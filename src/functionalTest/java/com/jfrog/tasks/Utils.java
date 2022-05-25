package com.jfrog.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfrog.GradleDependencyTree;
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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.TEST_DIR;
import static java.lang.System.lineSeparator;
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
    static void generateDepTrees(String gradleVersion, Path... projectNames) throws IOException {
        for (Path projectName : projectNames) {
            File projectDir = TEST_DIR.toPath().resolve(projectName).toFile();

            // Run generateDepTrees and assert success
            BuildResult result = runGenerateDepTrees(gradleVersion, projectDir);
            assertSuccess(result);
            assertOutput(result);

            // Run generateDepTrees and make sure the task was cached
            result = runGenerateDepTrees(gradleVersion, projectDir);
            assertUpToDate(result);
            assertOutput(result);

            // Make a change in build.gradle file and make sure the cache was invalidated after running generateDepTrees
            Files.write(projectDir.toPath().resolve("build.gradle"), "\n".getBytes(), StandardOpenOption.APPEND);
            result = runGenerateDepTrees(gradleVersion, projectDir);
            assertSuccess(result);
            assertOutput(result);
        }
    }

    /**
     * Assert a child in the given dependency tree.
     *
     * @param dependencyTree - The dependency tree
     * @param childName      - The child name
     * @param configuration  - A configuration name to check
     */
    static void assertChild(GradleDependencyTree dependencyTree, String childName, String configuration, boolean unresolved) {
        GradleDependencyTree child = dependencyTree.getChildren().get(childName);
        assertNotNull(child);
        assertEquals(child.isUnresolved(), unresolved);
        assertTrue(child.getConfigurations().contains(configuration));
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
     * @param result - The build results
     * @throws IOException in case of any I/O error.
     */
    private static void assertOutput(BuildResult result) throws IOException {
        // Collect the paths printed in the end of generateDepTrees task
        Set<Path> expected = Arrays.stream(result.getOutput().split(lineSeparator()))
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
     * @return the build results.
     */
    private static BuildResult runGenerateDepTrees(String gradleVersion, File projectDir) {
        return GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withDebug(true)
                .withArguments("generateDepTrees", "-q")
                .build();
    }
}
