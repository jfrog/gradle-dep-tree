package com.jfrog.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfrog.GradleDepTreeResults;
import com.jfrog.GradleDependencyNode;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.GenerateDepTrees.INCLUDE_ALL_BUILD_FILES;
import static com.jfrog.tasks.GenerateDepTrees.OUTPUT_FILE_PROPERTY;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.testng.Assert.*;

/**
 * @author yahavi
 */
public class Utils {
    static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Copy the test project from sourceDir to TEST_DIR.
     *
     * @param sourceDir - The Gradle project directory
     * @throws IOException - In case of any IO error
     */
    static void createTestDir(Path sourceDir) throws IOException {
        FileUtils.copyDirectory(sourceDir.toFile(), TEST_DIR);
    }

    /**
     * Delete the tests directories
     *
     * @throws IOException - In case of any IO error
     */
    static void deleteTestDir() throws IOException {
        FileUtils.deleteDirectory(TEST_DIR);
    }

    /**
     * Run and assert generateDepTrees task.
     *
     * @param gradleVersion - The Gradle version to use
     * @param projectNames  - The project names to use
     * @throws IOException in case of any I/O error.
     */
    static void generateDepTrees(String gradleVersion, boolean includeAllBuildFiles, Path... projectNames) throws IOException {
        Path outputFile = Files.createTempFile("gradle-deps-tree-test", "");
        try {
            for (Path projectName : projectNames) {
                File projectDir = TEST_DIR.toPath().resolve(projectName).toFile();

                // Run generateDepTrees and assert success
                BuildResult result = runGenerateDepTrees(gradleVersion, projectDir, outputFile, includeAllBuildFiles);
                assertSuccess(result);
                assertOutput(outputFile);

                // Run generateDepTrees and make sure the task was cached
                result = runGenerateDepTrees(gradleVersion, projectDir, outputFile, includeAllBuildFiles);
                assertUpToDate(result);
                assertOutput(outputFile);

                // Make a change in build.gradle file and make sure the cache was invalidated after running generateDepTrees
                Files.write(projectDir.toPath().resolve("build.gradle"), "\n".getBytes(), StandardOpenOption.APPEND);
                result = runGenerateDepTrees(gradleVersion, projectDir, outputFile, includeAllBuildFiles);
                assertSuccess(result);
                assertOutput(outputFile);
            }
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    /**
     * Assert a direct child of the root in the results.
     *
     * @param results       - The tree results object
     * @param childName     - The child name
     * @param configuration - A configuration name to check
     * @param unresolved    - True if the child is expected to be unresolved, false otherwise
     */
    static void assertDirectChild(GradleDepTreeResults results, String childName, String configuration, boolean unresolved) {
        assertTrue(results.getNodes().get(results.getRoot()).getChildren().contains(childName));
        GradleDependencyNode child = results.getNodes().get(childName);
        assertNotNull(child);
        assertEquals(child.isUnresolved(), unresolved);
        assertTrue(child.getConfigurations().contains(configuration));
    }

    static void assertRootChildrenCount(GradleDepTreeResults results, int expectedChildrenCount) {
        assertEquals(results.getNodes().get(results.getRoot()).getChildren().size(), expectedChildrenCount);
    }

    /**
     * Assert build success for task.
     *
     * @param buildResult - The build results
     */
    static void assertSuccess(BuildResult buildResult) {
        for (BuildTask buildTask : buildResult.getTasks()) {
            if (buildTask.getPath().contains("clean")) {
                continue;
            }
            assertEquals(buildTask.getOutcome(), SUCCESS);
        }
    }

    /**
     * Assert task cached successfully.
     *
     * @param buildResult - The build results
     */
    static void assertUpToDate(BuildResult buildResult) {
        for (BuildTask buildTask : buildResult.getTasks()) {
            if (buildTask.getPath().contains("clean")) {
                continue;
            }
            assertEquals(buildTask.getOutcome(), UP_TO_DATE);
        }
    }

    /**
     * Assert the task output.
     *
     * @param outputFile - The build results
     * @throws IOException in case of any I/O error.
     */
    private static void assertOutput(Path outputFile) throws IOException {
        // Collect the paths printed in the end of generateDepTrees task
        List<String> output = Files.readAllLines(outputFile);
        Set<Path> expected = output.stream()
                .map(Paths::get)
                .map(Path::getFileName)
                .collect(Collectors.toSet());
        assertFalse(expected.isEmpty());

        // Collect the files under "${buildDir}/gradle-dep-tree/" directory and compare
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");
        try (Stream<Path> fileStream = Files.list(outputDir)) {
            Set<Path> actual = fileStream.map(Path::getFileName).collect(Collectors.toSet());
            assertEquals(actual, expected);
        }
    }

    /**
     * Run Gradle process with the GradleRunner.
     *
     * @param gradleVersion - The Gradle version to use
     * @param projectDir    - The project directory
     * @param outputFile    - The output file
     * @return the build results.
     */
    private static BuildResult runGenerateDepTrees(String gradleVersion, File projectDir, Path outputFile, boolean includeAllBuildFiles) {
        return GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withEnvironment()
                .withArguments("generateDepTrees", "-q", "-D" + INCLUDE_ALL_BUILD_FILES + "=" + includeAllBuildFiles, "-D" + OUTPUT_FILE_PROPERTY + "=" + outputFile.toAbsolutePath())
                .build();
    }
}
