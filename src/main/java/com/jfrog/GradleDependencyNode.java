package com.jfrog;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yahavi
 **/
public class GradleDependencyNode {
    private final Set<String> children = new HashSet<>();
    // The Gradle configuration such as compileJava, implementation, testImplementation, etc.
    private final Set<String> configurations = new HashSet<>();
    // The artifact type(s) this dependency was resolved as, e.g. "jar", "pom".
    private final Set<String> types = new HashSet<>();
    private boolean unresolved;

    public GradleDependencyNode() {
    }

    public GradleDependencyNode(String configuration) {
        this.configurations.add(configuration);
    }

    public Set<String> getChildren() {
        return children;
    }

    public Set<String> getConfigurations() {
        return configurations;
    }

    public Set<String> getTypes() {
        return types;
    }

    public boolean isUnresolved() {
        return unresolved;
    }

    public void setUnresolved(boolean unresolved) {
        this.unresolved = unresolved;
    }
}
