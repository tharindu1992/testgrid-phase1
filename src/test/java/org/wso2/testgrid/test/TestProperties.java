package org.wso2.testgrid.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestProperties {

    public static String email;
    public static String emailPassword;
    public static String jenkinsUser;
    public static String jenkinsToken;
    private String propFileName = System.getenv("TEST_PROPS");

    public TestProperties() {

        getPropValues();
    }

    public void getPropValues() {

        try (InputStream inputStream = new FileInputStream(new File(propFileName))) {
            Properties prop = new Properties();

            if (inputStream != null) {
                prop.load(inputStream);
            } else {
                throw new FileNotFoundException("property file '" + propFileName + "' not found.");
            }

            email = prop.getProperty("email");
            emailPassword = prop.getProperty("emailPassword");
            jenkinsToken = prop.getProperty("jenkinsToken");
            jenkinsUser = prop.getProperty("jenkinsUser");

        } catch (IOException e) {
            System.out.println("Exception: " + e);
        }
    }
}


