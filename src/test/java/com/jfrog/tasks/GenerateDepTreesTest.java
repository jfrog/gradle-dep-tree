package com.jfrog.tasks;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.jfrog.tasks.GenerateDepTrees.isTestConfiguration;
import static org.testng.Assert.assertEquals;

public class GenerateDepTreesTest {

    @DataProvider
    public Object[][] configurationNames() {
        return new Object[][]{
                {"testImplementation", true},
                {"testCompileClasspath", true},
                {"testRuntimeClasspath", true},
                {"androidTestImplementation", true},
                {"debugAndroidTestCompileClasspath", true},
                {"compileClasspath", false},
                {"runtimeClasspath", false},
                {"implementation", false},
                {"debugImplementation", false},
                {"debugCompileClasspath", false},
                {"releaseCompileClasspath", false},
                {"A1ReleaseCompileClasspath", false},
                {null, false},
        };
    }

    @Test(dataProvider = "configurationNames")
    public void testIsTestConfiguration(String configurationName, boolean expected) {
        assertEquals(isTestConfiguration(configurationName), expected);
    }
}
