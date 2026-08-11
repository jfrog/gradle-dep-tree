package com.jfrog.tasks;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.jfrog.tasks.GenerateDepTrees.shouldExcludeConfiguration;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class GenerateDepTreesTest {

    @DataProvider
    public Object[][] configurationNames() {
        return new Object[][]{
                // pattern (?i)test — case-insensitive substring
                {"(?i)test", "testImplementation", true},
                {"(?i)test", "testCompileClasspath", true},
                {"(?i)test", "androidTestImplementation", true},
                {"(?i)test", "debugAndroidTestCompileClasspath", true},
                {"(?i)test", "testFixturesApi", true},
                {"(?i)test", "latestReleaseApi", true},
                {"(?i)test", "compileClasspath", false},
                {"(?i)test", "implementation", false},
                {"(?i)test", "debugCompileClasspath", false},
                {"(?i)test", null, false},
                // blank / null pattern = never exclude
                {"", "testImplementation", false},
                {null, "testImplementation", false},
                {"   ", "testImplementation", false},
                // custom pattern
                {"^runtime", "runtimeClasspath", true},
                {"^runtime", "compileClasspath", false},
        };
    }

    @Test(dataProvider = "configurationNames")
    public void testShouldExcludeConfiguration(String pattern, String configurationName, boolean expected) {
        assertEquals(shouldExcludeConfiguration(configurationName, pattern), expected);
    }

    @Test
    public void testInvalidPatternThrows() {
        expectThrows(IllegalArgumentException.class,
                () -> shouldExcludeConfiguration("implementation", "[invalid"));
    }
}
