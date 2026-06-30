package com.jfrog.tasks;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.ROOT_CONTAINER;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.GenerateDepTrees.INCLUDE_ALL_BUILD_FILES;
import static com.jfrog.tasks.GenerateDepTrees.OUTPUT_FILE_PROPERTY;
import static com.jfrog.tasks.Utils.assertSuccess;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Regression test for root container projects where rootProject.name differs from the
 * subproject that carries dependencies (custom-applications + CALINDI).
 */
public class RootContainerProjectTest extends FunctionalTestBase {

    @BeforeMethod
    public void setup() throws IOException {
        setup(ROOT_CONTAINER);
    }

    @Test(dataProvider = "gradleVersions")
    public void testCleanGenerateDepTreesFromRoot_withoutIncludeAllBuildFiles(String gradleVersion) throws IOException {
        Path outputFile = Files.createTempFile("gradle-deps-tree-root-container-default", "");
        try {
            BuildResult result = GradleRunner.create()
                    .withGradleVersion(gradleVersion)
                    .withProjectDir(TEST_DIR)
                    .withPluginClasspath()
                    .withArguments(
                            "clean", "generateDepTrees", "-q",
                            "-D" + OUTPUT_FILE_PROPERTY + "=" + outputFile.toAbsolutePath())
                    .build();
            assertSuccess(result);

            List<String> summaryPaths = Files.readAllLines(outputFile);
            Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");

            for (String path : summaryPaths) {
                File f = new File(path.trim());
                assertTrue(f.exists(), "Summary lists missing file: " + path);
            }

            Set<String> expectedNames = Set.of(
                    Base64.getEncoder().encodeToString("custom-applications".getBytes(StandardCharsets.UTF_8))
            );
            try (Stream<Path> files = Files.list(outputDir)) {
                Set<String> actualNames = files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
                assertEquals(actualNames, expectedNames);
            }
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    @Test(dataProvider = "gradleVersions")
    public void testCleanGenerateDepTreesFromRoot_withIncludeAllBuildFiles(String gradleVersion) throws IOException {
        Path outputFile = Files.createTempFile("gradle-deps-tree-root-container", "");
        try {
            BuildResult result = GradleRunner.create()
                    .withGradleVersion(gradleVersion)
                    .withProjectDir(TEST_DIR)
                    .withPluginClasspath()
                    .withArguments(
                            "clean", "generateDepTrees", "-q",
                            "-D" + INCLUDE_ALL_BUILD_FILES + "=true",
                            "-D" + OUTPUT_FILE_PROPERTY + "=" + outputFile.toAbsolutePath())
                    .build();
            assertSuccess(result);

            List<String> summaryPaths = Files.readAllLines(outputFile);
            Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");

            for (String path : summaryPaths) {
                File f = new File(path.trim());
                assertTrue(f.exists(), "Summary lists missing file: " + path);
            }

            Set<String> expectedNames = Set.of(
                    Base64.getEncoder().encodeToString("custom-applications".getBytes(StandardCharsets.UTF_8)),
                    Base64.getEncoder().encodeToString("CALINDI".getBytes(StandardCharsets.UTF_8))
            );
            try (Stream<Path> files = Files.list(outputDir)) {
                Set<String> actualNames = files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
                assertEquals(actualNames, expectedNames);
            }
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }
}
