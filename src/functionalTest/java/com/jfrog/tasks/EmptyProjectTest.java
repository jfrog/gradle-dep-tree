package com.jfrog.tasks;

import com.jfrog.GradleDependencyTree;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.EMPTY;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.generateDepTrees;
import static com.jfrog.tasks.Utils.objectMapper;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Functional tests for the project under resources/empty/
 * This project contain no dependencies and no subproject.
 *
 * @author yahavi
 **/
public class EmptyProjectTest extends FunctionalTestBase {

    @BeforeMethod
    public void setup() throws IOException {
        setup(EMPTY);
    }

    @Test(dataProvider = "gradleVersions")
    public void testNoDependenciesProject(String gradleVersion) throws IOException {
        generateDepTrees(gradleVersion, Paths.get("."));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-deps-tree");
        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> actualProjects = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(1, actualProjects.size());
            for (String actualProject : actualProjects) {
                GradleDependencyTree dependencyTree = objectMapper.readValue(outputDir.resolve(actualProject).toFile(), GradleDependencyTree.class);
                assertTrue(dependencyTree.getChildren().isEmpty());
            }
        }
    }
}
