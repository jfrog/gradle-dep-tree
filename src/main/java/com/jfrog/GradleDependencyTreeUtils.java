package com.jfrog;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utils for building the GradleDependencyTree object. The reason for these utils is to make the GradleDependencyTree
 * class clean from Gradle internal dependencies that may be unresolved during Jackson unmarshal.
 *
 * @author yahavi
 **/
public class GradleDependencyTreeUtils {

    /**
     * Add Gradle configuration including its all dependencies.
     *
     * @param root          - The root node
     * @param configuration - Resolved or unresolved Gradle configuration
     * @param nodes         - A map of all nodes mapped by their module ID (group:name:version)
     */
    public static void addConfiguration(GradleDependencyNode root, Configuration configuration, Map<String, GradleDependencyNode> nodes) {
        if (configuration.isCanBeResolved()) {
            addResolvedConfiguration(root, configuration, nodes);
        } else {
            addUnresolvedConfiguration(root, configuration, nodes);
        }
    }

    /**
     * Add resolved configuration. A resolved configuration may contain transitive dependencies.
     *
     * @param root          - The root node
     * @param configuration - Resolved Gradle configuration
     * @param nodes         - A map of all nodes mapped by their module ID (group:name:version)
     */
    private static void addResolvedConfiguration(GradleDependencyNode root, Configuration configuration, Map<String, GradleDependencyNode> nodes) {
        root.getConfigurations().add(configuration.getName());
        ResolvedComponentResult componentResult = configuration.getIncoming().getResolutionResult().getRoot();
        for (DependencyResult dependency : componentResult.getDependencies()) {
            populateTree(root, configuration.getName(), dependency, new HashSet<>(), nodes);
        }
    }

    /**
     * Add unresolved configuration. An unresolved configuration can contain only direct dependencies.
     *
     * @param root          - The parent node
     * @param configuration - Unresolved Gradle configuration
     * @param nodes         - A map of all nodes mapped by their module ID (group:name:version)
     */
    private static void addUnresolvedConfiguration(GradleDependencyNode root, Configuration configuration, Map<String, GradleDependencyNode> nodes) {
        for (Dependency dependency : configuration.getDependencies()) {
            GradleDependencyNode child = new GradleDependencyNode(configuration.getName());
            child.setUnresolved(true);
            if (dependency.getVersion() != null) {
                // If the version is null, the dependency does not contain an ID and we should not add it.
                // For example: "implementation gradleApi()"
                addChild(root, String.join(":", dependency.getGroup(), dependency.getName(), dependency.getVersion()), child, nodes);
            }
        }
    }

    /**
     * Recursively populate the dependency tree.
     *
     * @param node              - The parent node
     * @param configurationName - The configuration name
     * @param dependency        - Resolved or unresolved dependency
     * @param addedChildren     - Set used to remove duplications to make sure there is no loop in the tree
     * @param nodes             - A map of all nodes mapped by their module ID (group:name:version)
     */
    private static void populateTree(GradleDependencyNode node, String configurationName, DependencyResult dependency, Set<String> addedChildren, Map<String, GradleDependencyNode> nodes) {
        GradleDependencyNode child = new GradleDependencyNode(configurationName);
        if (dependency instanceof UnresolvedDependencyResult) {
            child.setUnresolved(true);
            addChild(node, dependency.getRequested().getDisplayName(), child, nodes);
            return;
        }
        ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
        ModuleVersionIdentifier moduleVersion = resolvedDependency.getSelected().getModuleVersion();
        if (moduleVersion == null) {
            // If there is no module version, then the dependency was not found in any repository
            return;
        }
        if (!addedChildren.add(moduleVersion.toString())) {
            return;
        }
        addChild(node, moduleVersion.toString(), child, nodes);
        for (DependencyResult dependencyResult : resolvedDependency.getSelected().getDependencies()) {
            populateTree(child, configurationName, dependencyResult, new HashSet<>(addedChildren), nodes);
        }
    }

    /**
     * Add a child to the dependency tree.
     *
     * @param parent     - The parent node
     * @param childId    - The child ID
     * @param childToAdd - The child node
     * @param nodes      - A map of all nodes mapped by their module ID (group:name:version)
     */
    static void addChild(GradleDependencyNode parent, String childId, GradleDependencyNode childToAdd, Map<String, GradleDependencyNode> nodes) {
        GradleDependencyNode child = nodes.get(childId);
        if (child == null) {
            nodes.put(childId, childToAdd);
        } else {
            // If the child already exists, add the Gradle configurations of the input child
            child.getConfigurations().addAll(childToAdd.getConfigurations());
            child.setUnresolved(child.isUnresolved() && childToAdd.isUnresolved());
        }
        parent.getChildren().add(childId);
    }
}
