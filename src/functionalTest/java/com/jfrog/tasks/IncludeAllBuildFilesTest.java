package com.jfrog.tasks;

import com.jfrog.GradleDependencyTree;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.MULTI;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.*;
import static org.testng.Assert.*;

/**
 * Functional tests for the project under resources/multi/
 * This project contain subprojects in a multiple build.gradle files.
 *
 * @author yahavi
 **/
public class IncludeAllBuildFilesTest extends FunctionalTestBase {

    @BeforeMethod
    public void setup() throws IOException {
        setup(MULTI);
    }

    @Test(dataProvider = "gradleVersions")
    public void testRootBuildFile(String gradleVersion) throws IOException {
        generateDepTrees(gradleVersion, true, Paths.get("."));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");
        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> actualProjects = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(actualProjects.size(), 5);
            for (String actualProject : actualProjects) {
                GradleDependencyTree dependencyTree = objectMapper.readValue(outputDir.resolve(actualProject).toFile(), GradleDependencyTree.class);
                String projectName = new String(Base64.getDecoder().decode(actualProject), StandardCharsets.UTF_8);
                switch (projectName) {
                    case "shared":
                        assertEquals(dependencyTree.getChildren().size(), 1);
                        assertChild(dependencyTree, "junit:junit:4.7", "testImplementation", false);
                        break;
                    case "services":
                    case "functional-test-project":
                        assertTrue(dependencyTree.getChildren().isEmpty());
                        break;
                    case "api":
                        assertEquals(dependencyTree.getChildren().size(), 4);
                        break;
                    case "webservice":
                        assertEquals(dependencyTree.getChildren().size(), 7);
                        break;
                    default:
                        fail("Unexpected project " + projectName);
                }
            }
        }
    }
}