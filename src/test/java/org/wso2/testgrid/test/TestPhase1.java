package org.wso2.testgrid.test;

import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class TestPhase1 {

    private JenkinsJob previousBuild;
    private JenkinsJob currentBuild;
    TestProperties testProperties;

    @BeforeTest

    public void init() {
       testProperties =  new TestProperties();
        HostnameVerifier allHostsValid = new HostValidator();
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

    }

	@Test
	public void buildTriggerTest() {

        HttpsURLConnection connection = null;

        try {
            String user = TestProperties.jenkinsUser; // username
            //String pass = "ba4ac7dc47c0d2f68c063dc44425d964"; // password or API token
            String pass = TestProperties.jenkinsToken;
            URL buildTriggerUrl = new URL("https://" + user + ":" + pass +
                    "@testgrid-live-dev.private.wso2.com/admin/job/Phase-1/build?token=test");
            URL buildStatusUrl =
                    new URL("https://testgrid-live-dev.private.wso2.com/admin/job/Phase-1/lastBuild/api/json");

            JenkinsJob jenkinsJob = getLastJob(buildStatusUrl);

            previousBuild = jenkinsJob;
            System.out.println(jenkinsJob.id + " " + jenkinsJob.status);

            connection = (HttpsURLConnection) buildTriggerUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);

            SSLSocketFactory sslSocketFactory = createSslSocketFactory();
            connection.setSSLSocketFactory(sslSocketFactory);


            int response = connection.getResponseCode();
            if (response == 201) {
                System.out.println("build Triggered");
            } else {
                Assert.fail("Phase 1 build couldn't be triggered. Response code : " + response);
            }

            jenkinsJob=getLastJob(buildStatusUrl);

            while (jenkinsJob.building) {
                jenkinsJob=getLastJob(buildStatusUrl);
                System.out.println("Phase 1  #("+jenkinsJob.id + ") building ");
            }

            currentBuild = getLastJob(buildStatusUrl);

            Assert.assertEquals(currentBuild.status,"SUCCESS");


            EmailUtils emailUtils = connectToEmail();
            testTextContained(emailUtils,jenkinsJob.id);

        } catch(Exception e) {
           System.out.println(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
	}

    @Test(dependsOnMethods={"buildTriggerTest"})
    public void logTest() {

        try {
            validateLog(getTestplanID(currentBuild.id));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }

    @Test(dependsOnMethods={"buildTriggerTest"})
    public void summaryTest() {

        try {
            testSummaryValidate(getTestplanID(currentBuild.id));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }


	public void validateLog(String testplan) throws Exception {

        String webPage = "https://testgrid-live-dev.private.wso2.com/api/test-plans/log/" + testplan;

        URL url = new URL(webPage);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "test");
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        connection.setSSLSocketFactory(sslSocketFactory);

        try ( BufferedReader in =new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            if (response.toString().contains(testplan)) {
                System.out.println("Correct log is found");
            } else {
                Assert.fail("Correct log is not found");
            }
        }
    }

    public String getTestplanID(String buildNo) throws Exception {
	    URL url = new URL("https://testgrid-live-dev.private.wso2.com/admin/job/Phase-1/" + buildNo +
                "/consoleText");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        connection.setSSLSocketFactory(sslSocketFactory);
        int responseCode = connection.getResponseCode();
        String testplan;
        try ( BufferedReader in =new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuffer response = new StringBuffer();

            String patternString = ".*Preparing workspace for testplan.*";

            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher;
            String rowTestPlanID = "";
            while ((inputLine = in.readLine()) != null) {
                matcher = pattern.matcher(inputLine);
                if (matcher.find()) {
                    rowTestPlanID = inputLine;
                    break;
                }
            }
            testplan = rowTestPlanID.split(":")[5].replaceAll("\\s","");
        }
        connection.disconnect();
        return testplan;
    }

    public void testTextContained(EmailUtils emailUtils, String buildNo)  {
        try{
            Message email = emailUtils.getMessagesBySubject("'Phase-1' Test Results! #(" + buildNo + ")",
                    false, 5)[0];
            Assert.assertTrue(emailUtils.isTextInMessage(email, "Phase-1 integration test Results!"),
                    "Phase-1 integration test Results!");
            System.out.println("Email received on " + email.getReceivedDate());
        } catch (ArrayIndexOutOfBoundsException e) {
            Assert.fail("Email not recieved for the build");
        }  catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static EmailUtils connectToEmail() {
        try {
            //gmail need to alow less secure apps
            EmailUtils emailUtils = new EmailUtils(TestProperties.email, TestProperties.emailPassword,
                    "smtp.gmail.com", EmailUtils.EmailFolder.INBOX);
            return emailUtils;
        } catch (MessagingException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
            return null;
        }
    }

    private void testSummaryValidate(String testplan) throws Exception {

        URL url = new URL("https://testgrid-live-dev.private.wso2.com/api/test-plans/test-summary/" + testplan);

        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "test");
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        con.setSSLSocketFactory(sslSocketFactory);
        StringBuffer response;
        try ( BufferedReader in =new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }

        JSONObject myresponse = new JSONObject(response.toString());
        myresponse = myresponse.getJSONArray("scenarioSummaries").getJSONObject(0);

        Assert.assertEquals(myresponse.getInt("totalSuccess"),541);
        Assert.assertEquals(myresponse.getInt("totalFail"),0);
        Assert.assertEquals(myresponse.getString("scenarioDescription"),"Test-Phase-1");
        System.out.println("Summary verified");

        con.disconnect();

    }

	private JenkinsJob getLastJob(URL url) throws Exception {

        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setDoOutput(true);
        SSLSocketFactory sslSocketFactory = createSslSocketFactory();
        con.setSSLSocketFactory(sslSocketFactory);
        int responseCode = con.getResponseCode();

        StringBuffer response;
        try ( BufferedReader in =new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String inputLine;
            response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        JSONObject myresponse = new JSONObject(response.toString());
        JenkinsJob jenkinsJob;
        if (myresponse.getBoolean("building")) {
            jenkinsJob = new JenkinsJob(myresponse.getString("id"), myresponse.getBoolean("building"));
        } else {
            jenkinsJob = new JenkinsJob(myresponse.getString("result"), myresponse.getString("id"),
                    myresponse.getBoolean("building"));
        }

        con.disconnect();
        return jenkinsJob;
    }

	public static class JenkinsJob {
	    public String status;
        public String id;
        public boolean building;

        public JenkinsJob(String status, String id, Boolean building) {
            this.status = status;
            this.id = id;
            this.building = building;
        }
        public JenkinsJob(String id, Boolean building) {
            this(null,id,building);
        }
    }

    /**
     * This method is to bypass SSL verification
     * @return SSL socket factory that by will bypass SSL verification
     * @throws Exception java.security exception is thrown in an issue with SSLContext
     */
    private static SSLSocketFactory createSslSocketFactory() throws Exception {
        TrustManager[] byPassTrustManagers = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
        } };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, byPassTrustManagers, new SecureRandom());
        return sslContext.getSocketFactory();
    }
}
