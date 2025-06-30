package com.jfrog.tasks;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.nio.file.Path;

import static com.jfrog.tasks.Utils.createTestDir;
import static com.jfrog.tasks.Utils.deleteTestDir;

/**
 * Base test for all functional tests.
 *
 * @author yahavi
 **/
public class FunctionalTestBase {
    public void setup(Path projectPath) throws IOException {
        deleteTestDir();
        createTestDir(projectPath);
    }

    @AfterMethod
    public void tearDown() throws IOException {
        deleteTestDir();
    }

    @DataProvider
    public Object[][] gradleVersions() {
        return new Object[][]{{"5.6.4"}, {"6.9"}, {"7.4.2"}, {"7.6"}, {"8.14.2"}};
    }
}
