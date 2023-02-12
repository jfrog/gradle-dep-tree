package com.jfrog;

import org.testng.annotations.Test;
import org.testng.collections.Sets;

import static com.jfrog.GradleDependencyTreeUtils.addChild;
import static org.testng.Assert.*;

/**
 * @author yahavi
 **/
public class GradleDependencyTreeTest {
    @Test
    public void testNoChildren() {
        GradleDependencyTree dependencyTree = new GradleDependencyTree("compile");
        assertEquals(Sets.newHashSet("compile"), dependencyTree.getConfigurations());
        assertTrue(dependencyTree.getChildren().isEmpty());
    }

    @Test
    public void testOneChild() {
        GradleDependencyTree dependencyTree = new GradleDependencyTree("compile");
        addChild(dependencyTree, "child-1", new GradleDependencyTree("compile"));
        addChild(dependencyTree, "child-1", new GradleDependencyTree("compile"));
        addChild(dependencyTree, "child-1", new GradleDependencyTree("runtime"));

        assertEquals(1, dependencyTree.getChildren().size());
        GradleDependencyTree actualChild = dependencyTree.getChildren().get("child-1");
        assertEquals(Sets.newHashSet("compile", "runtime"), actualChild.getConfigurations());
    }

    @Test
    public void testTwoChildren() {
        GradleDependencyTree dependencyTree = new GradleDependencyTree("compile");
        GradleDependencyTree child1 = new GradleDependencyTree("compile");
        child1.setUnresolved(true);
        addChild(dependencyTree, "child-1", child1);
        addChild(dependencyTree, "child-2", new GradleDependencyTree("compile"));
        addChild(dependencyTree, "child-2", new GradleDependencyTree("runtime"));

        assertEquals(2, dependencyTree.getChildren().size());
        GradleDependencyTree actualChild1 = dependencyTree.getChildren().get("child-1");
        assertEquals(Sets.newHashSet("compile"), actualChild1.getConfigurations());
        assertTrue(actualChild1.isUnresolved());
        GradleDependencyTree actualChild2 = dependencyTree.getChildren().get("child-2");
        assertEquals(Sets.newHashSet("compile", "runtime"), actualChild2.getConfigurations());
        assertFalse(actualChild2.isUnresolved());
    }
}
