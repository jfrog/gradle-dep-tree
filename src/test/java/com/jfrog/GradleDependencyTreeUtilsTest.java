package com.jfrog;

import org.testng.annotations.Test;
import org.testng.collections.Sets;

import java.util.HashMap;
import java.util.Map;

import static com.jfrog.GradleDependencyTreeUtils.addChild;
import static org.testng.Assert.*;

public class GradleDependencyTreeUtilsTest {
    @Test
    public void testAddChild() {
        GradleDependencyNode dep = new GradleDependencyNode("configuration-1");
        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("dep", dep);

        // Add "child-1" (a resolved dependency)
        GradleDependencyNode originalChild1 = new GradleDependencyNode("configuration-1");
        originalChild1.setUnresolved(true);
        addChild(dep, "child-1", originalChild1, nodes);
        assertEquals(nodes.size(), 2);
        assertEquals(dep.getChildren().size(), 1);
        assertEquals(nodes.get("child-1").getConfigurations(), Sets.newHashSet("configuration-1"));
        assertTrue(nodes.get("child-1").isUnresolved());

        // Add "child-1" (that already exists in the tree) with another configuration and resolved
        GradleDependencyNode newChild1 = new GradleDependencyNode("configuration-2");
        addChild(dep, "child-1", newChild1, nodes);
        assertEquals(nodes.size(), 2);
        assertEquals(dep.getChildren().size(), 1);
        assertEquals(nodes.get("child-1").getConfigurations(), Sets.newHashSet("configuration-1", "configuration-2"));
        assertFalse(nodes.get("child-1").isUnresolved());

        // Add "child-2" (a new child)
        GradleDependencyNode child2 = new GradleDependencyNode("configuration-1");
        addChild(dep, "child-2", child2, nodes);
        assertEquals(nodes.size(), 3);
        assertEquals(dep.getChildren().size(), 2);
        assertEquals(nodes.get("child-2").getConfigurations(), Sets.newHashSet("configuration-1"));
        assertFalse(nodes.get("child-2").isUnresolved());
    }
}
