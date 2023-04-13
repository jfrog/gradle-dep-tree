package com.jfrog;

import java.util.Map;

public class GradleDepTreeResults {
    private String root;
    private Map<String, GradleDependencyNode> nodes;

    public GradleDepTreeResults() {
    }

    public GradleDepTreeResults(String root, Map<String, GradleDependencyNode> nodes) {
        this.root = root;
        this.nodes = nodes;
    }

    public String getRoot() {
        return root;
    }

    public Map<String, GradleDependencyNode> getNodes() {
        return nodes;
    }
}
