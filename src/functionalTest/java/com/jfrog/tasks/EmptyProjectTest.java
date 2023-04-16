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

import static com.jfrog.tasks.Consts.EMPTY;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.*;
import static org.testng.Assert.assertEquals;

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
        generateDepTrees(gradleVersion, false, Paths.get("."));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");
        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> actualProjects = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(1, actualProjects.size());
            for (String actualProject : actualProjects) {
                GradleDepTreeResults results = objectMapper.readValue(outputDir.resolve(actualProject).toFile(), GradleDepTreeResults.class);
                assertRootChildrenCount(results, 0);
            }
        }
    }
}
