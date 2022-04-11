package com.jfrog;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.jfrog.Utils.toJsonString;
import static org.testng.Assert.assertEquals;

/**
 * @author yahavi
 **/
public class UtilsTest {
    private static final Path RESOURCES_DIR = Paths.get("src", "test", "resources");

    @Test
    public void testToJsonString() throws IOException {
        String expectedOutput = FileUtils.readFileToString(RESOURCES_DIR.resolve("expectedDepsTree.json").toFile(), StandardCharsets.UTF_8);
        GradleDependencyTree dependencyTree = new GradleDependencyTree("configuration-1");
        GradleDependencyTree child1Conf1 = new GradleDependencyTree("configuration-1");
        GradleDependencyTree child1Conf2 = new GradleDependencyTree("configuration-2");
        GradleDependencyTree child2 = new GradleDependencyTree("configuration-2");
        dependencyTree.addChild("child-1", child1Conf1);
        dependencyTree.addChild("child-1", child1Conf2);
        dependencyTree.addChild("child-2", child2);

        GradleDependencyTree child3 = new GradleDependencyTree("configuration-1");
        child3.setUnresolved(true);
        child1Conf1.addChild("child-3", child3);
        assertEquals(toJsonString(dependencyTree), expectedOutput.trim());
    }
}
