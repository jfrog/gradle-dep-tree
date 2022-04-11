package com.jfrog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author yahavi
 **/
public class GradleDependencyTree {
    private final Map<String, GradleDependencyTree> children = new HashMap<>();
    private final Set<String> configurations = new HashSet<>();
    private boolean unresolved;

    public GradleDependencyTree() {
    }

    public GradleDependencyTree(String configuration) {
        this.configurations.add(configuration);
    }

    public Map<String, GradleDependencyTree> getChildren() {
        return children;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }

    public boolean isUnresolved() {
        return unresolved;
    }

    public void setUnresolved(boolean unresolved) {
        this.unresolved = unresolved;
    }

    public void addChild(String id, GradleDependencyTree childToAdd) {
        // If the child already exist, add the Gradle configurations of the input child
        if (children.containsKey(id)) {
            GradleDependencyTree child = children.get(id);
            child.configurations.addAll(childToAdd.configurations);
            child.unresolved = child.unresolved && childToAdd.unresolved;
        } else {
            children.put(id, childToAdd);
        }
    }
}
