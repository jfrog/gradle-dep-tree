package com.jfrog;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * @author yahavi
 **/
public class UtilsTest {
    private static final Path RESOURCES_DIR = Paths.get("src", "test", "resources");
    private Path tempDirPath;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDirPath = Files.createTempDirectory("testLoadBuildTree");
    }

    @AfterMethod
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(tempDirPath.toFile());
    }

    @Test
    public void testSaveToFileAsJson() throws IOException {
        String outputFilePath = tempDirPath.resolve("output.txt").toString();
        File outputFile = new File(outputFilePath);

        // Create a node called "dep" and its children "child-1" and "child-2". "dep" is also a child of "child-2". "child-1" is unresolved.
        GradleDependencyNode dep = new GradleDependencyNode("configuration-1");
        GradleDependencyNode child1 = new GradleDependencyNode("configuration-1");
        child1.getConfigurations().add("configuration-2");
        child1.setUnresolved(true);
        dep.getChildren().add("child-1");
        GradleDependencyNode child2 = new GradleDependencyNode("configuration-1");
        child2.getChildren().add("dep");
        dep.getChildren().add("child-2");

        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("dep", dep);
        nodes.put("child-1", child1);
        nodes.put("child-2", child2);

        GradleDepTreeResults results = new GradleDepTreeResults("dep", nodes);
        Utils.saveToFileAsJson(outputFile, results);

        String expectedOutput = FileUtils.readFileToString(RESOURCES_DIR.resolve("expectedDepTree.json").toFile(), StandardCharsets.UTF_8).trim();
        String actualOutput = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8).trim();
        assertEquals(actualOutput, expectedOutput);
    }
}
