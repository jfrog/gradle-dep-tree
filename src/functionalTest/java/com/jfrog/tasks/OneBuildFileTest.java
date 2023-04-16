package com.jfrog.tasks;

import com.jfrog.GradleDependencyNode;
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

import static com.jfrog.tasks.Consts.ONE_FILE;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.*;
import static org.testng.Assert.*;

/**
 * Functional tests for the project under resources/oneBuildFile/
 * This project contain subprojects in a single build.gradle files.
 *
 * @author yahavi
 **/
public class OneBuildFileTest extends FunctionalTestBase {

    @BeforeMethod
    public void setup() throws IOException {
        setup(ONE_FILE);
    }

    @Test(dataProvider = "gradleVersions")
    public void testOneBuildFile(String gradleVersion) throws IOException {
        generateDepTrees(gradleVersion, false, Paths.get("."));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");
        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> actualProjects = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(actualProjects.size(), 5);
            for (String actualProject : actualProjects) {
                GradleDependencyNode dependencyTree = objectMapper.readValue(outputDir.resolve(actualProject).toFile(), GradleDependencyNode.class);
                String projectName = new String(Base64.getDecoder().decode(actualProject), StandardCharsets.UTF_8);
                switch (projectName) {
                    case "shared":
                        assertEquals(dependencyTree.getChildren().size(), 1);
                        assertChild(dependencyTree, "junit:junit:4.7", "testImplementation", false);
                        break;
                    case "api":
                    case "webservice":
                        assertTrue(dependencyTree.getChildren().size() > 3);
                        assertChild(dependencyTree, "junit:junit:4.7", "testImplementation", false);
                        assertChild(dependencyTree, "commons-lang:commons-lang:2.4", "implementation", false);
                        assertChild(dependencyTree, "org.jfrog.test.gradle.publish:shared:1.0-SNAPSHOT", "implementation", false);
                        break;
                    case "services":
                    case "functional-test-project":
                        assertTrue(dependencyTree.getChildren().isEmpty());
                        break;
                    default:
                        fail("Unexpected project " + projectName);
                }
            }
        }
    }
}
