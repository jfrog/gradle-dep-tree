package com.jfrog.tasks;

import com.jfrog.GradleDepTreeResults;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.PLATFORM_DEPENDENCY;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.*;
import static org.testng.Assert.assertEquals;

/**
 * Functional test for the project under resources/platformDependency/.
 * Regression test for XRAY-159118, against real Gradle's attribute-resolution machinery
 * (not mocks): a BOM pulled in via platform(...) or enforcedPlatform(...) must be typed "pom"
 * only, never "jar".
 *
 * @author yahavi
 **/
public class PlatformDependencyTest extends FunctionalTestBase {

    @BeforeMethod
    public void setup() throws IOException {
        setup(PLATFORM_DEPENDENCY);
    }

    @Test(dataProvider = "gradleVersions")
    public void testPlatformDependencyTypedAsPom(String gradleVersion) throws IOException {
        generateDepTrees(gradleVersion, false, Paths.get("."));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");
        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> actualProjects = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(1, actualProjects.size());
            for (String actualProject : actualProjects) {
                GradleDepTreeResults results = objectMapper.readValue(outputDir.resolve(actualProject).toFile(), GradleDepTreeResults.class);
                assertTypes(results, "com.fasterxml.jackson:jackson-bom:2.15.2", "pom");
                assertTypes(results, "org.junit:junit-bom:5.10.2", "pom");
                assertTypes(results, "joda-time:joda-time:2.2", "jar");
            }
        }
    }
}
