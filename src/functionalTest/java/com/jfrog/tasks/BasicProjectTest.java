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

import static com.jfrog.tasks.Consts.BASIC;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.*;
import static org.testng.Assert.assertEquals;

/**
 * Functional tests for the project under resources/basic/
 * This project contain no subprojects.
 *
 * @author yahavi
 **/
public class BasicProjectTest extends FunctionalTestBase {

    @BeforeMethod
    public void setup() throws IOException {
        setup(BASIC);
    }

    @Test(dataProvider = "gradleVersions")
    public void testBasicProject(String gradleVersion) throws IOException {
        generateDependencyTree(gradleVersion, Paths.get("."));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-deps-tree");
        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> actualProjects = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(1, actualProjects.size());
            for (String actualProject : actualProjects) {
                GradleDependencyTree dependencyTree = objectMapper.readValue(outputDir.resolve(actualProject).toFile(), GradleDependencyTree.class);
                assertChild(dependencyTree, "junit:junit:4.12", "testImplementation", false);
                assertChild(dependencyTree, "joda-time:joda-time:2.2", "implementation", false);
                assertChild(dependencyTree, "missing:dependency:404", "testImplementation", true);
            }
        }
    }
}
