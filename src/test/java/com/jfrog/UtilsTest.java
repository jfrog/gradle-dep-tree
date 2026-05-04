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

import static com.jfrog.Utils.UNSPECIFIED_ID_PART;
import static com.jfrog.Utils.buildModuleId;
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

    /**
     * {@link Utils#buildModuleId} is the single source of truth for the
     * {@code group:name:version} placeholder format used by both
     * {@code GenerateDepTrees#getProjectModuleId} and
     * {@code GradleDependencyTreeUtils#synthesizeProjectNodeId}. If these
     * assertions ever change, both call sites need to be re-examined to keep
     * downstream tree-merge logic consistent.
     */
    @Test
    public void testBuildModuleId_allComponentsPresent() {
        assertEquals(buildModuleId("com.itextpdf", "kernel", "7.2.5"), "com.itextpdf:kernel:7.2.5");
    }

    @Test
    public void testBuildModuleId_nullGroup_replacedWithUnspecified() {
        assertEquals(buildModuleId(null, "lib", "1.0"), UNSPECIFIED_ID_PART + ":lib:1.0");
    }

    @Test
    public void testBuildModuleId_emptyGroup_replacedWithUnspecified() {
        assertEquals(buildModuleId("", "lib", "1.0"), UNSPECIFIED_ID_PART + ":lib:1.0");
    }

    @Test
    public void testBuildModuleId_nullName_replacedWithUnspecified() {
        assertEquals(buildModuleId("com.example", null, "1.0"), "com.example:" + UNSPECIFIED_ID_PART + ":1.0");
    }

    @Test
    public void testBuildModuleId_nullVersion_replacedWithUnspecified() {
        assertEquals(buildModuleId("com.example", "lib", null), "com.example:lib:" + UNSPECIFIED_ID_PART);
    }

    @Test
    public void testBuildModuleId_emptyVersion_replacedWithUnspecified() {
        assertEquals(buildModuleId("com.example", "lib", ""), "com.example:lib:" + UNSPECIFIED_ID_PART);
    }

    @Test
    public void testBuildModuleId_allNull_yieldsThreeUnspecifiedParts() {
        // This is the value synthesizeProjectNodeId returns when the project path is null.
        assertEquals(buildModuleId(null, null, null),
                String.join(":", UNSPECIFIED_ID_PART, UNSPECIFIED_ID_PART, UNSPECIFIED_ID_PART));
    }

    @Test
    public void testBuildModuleId_allEmpty_yieldsThreeUnspecifiedParts() {
        assertEquals(buildModuleId("", "", ""),
                String.join(":", UNSPECIFIED_ID_PART, UNSPECIFIED_ID_PART, UNSPECIFIED_ID_PART));
    }

    /**
     * Idempotence: passing {@code null} or {@code ""} for any part must produce the same id.
     * If this ever drifts, sibling subprojects' tree files won't merge correctly downstream
     * (the same subproject would be keyed differently depending on which API gave us the
     * null-vs-empty value). The cross-site invariant proper — that the three id-producing
     * call sites all agree — is locked down in {@code GradleDependencyTreeUtilsTest}.
     */
    @Test
    public void testBuildModuleId_nullAndEmptyParts_areInterchangeable() {
        assertEquals(buildModuleId(null, "DummyService", null),
                buildModuleId("", "DummyService", ""));
    }
}
