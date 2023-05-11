package com.jfrog;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;

import java.util.HashSet;
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
     * @param configuration - Resolve or unresolved Gradle configuration
     */
    public static void addConfiguration(GradleDependencyTree root, Configuration configuration) {
        if (configuration.isCanBeResolved()) {
            addResolvedConfiguration(root, configuration);
        } else {
            addUnresolvedConfiguration(root, configuration);
        }
    }

    /**
     * Add resolved configuration. A resolved configuration may contain transitive dependencies.
     *
     * @param root          - The root node
     * @param configuration - Resolved Gradle configuration
     */
    private static void addResolvedConfiguration(GradleDependencyTree root, Configuration configuration) {
        root.getConfigurations().add(configuration.getName());
        ResolvedComponentResult componentResult = configuration.getIncoming().getResolutionResult().getRoot();
        for (DependencyResult dependency : componentResult.getDependencies()) {
            populateTree(root, configuration.getName(), dependency, new HashSet<>());
        }
    }

    /**
     * Add unresolved configuration. An unresolved configuration can contain only direct dependencies.
     *
     * @param node          - The parent node
     * @param configuration - Unresolved Gradle configuration
     */
    private static void addUnresolvedConfiguration(GradleDependencyTree node, Configuration configuration) {
        for (Dependency dependency : configuration.getDependencies()) {
            GradleDependencyTree child = new GradleDependencyTree(configuration.getName());
            child.setUnresolved(true);
            if (dependency.getVersion() != null) {
                // If the version is null, the dependency does not contain an ID and we should not add it.
                // For example: "implementation gradleApi()"
                addChild(node, String.join(":", dependency.getGroup(), dependency.getName(), dependency.getVersion()), child);
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
     */
    private static void populateTree(GradleDependencyTree node, String configurationName, DependencyResult dependency, Set<String> addedChildren) {
        GradleDependencyTree child = new GradleDependencyTree(configurationName);
        if (dependency instanceof UnresolvedDependencyResult) {
            if (!addedChildren.add(dependency.getRequested().getDisplayName())) {
                return;
            }
            child.setUnresolved(true);
            addChild(node, dependency.getRequested().getDisplayName(), child);
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
        addChild(node, moduleVersion.toString(), child);
        for (DependencyResult dependencyResult : resolvedDependency.getSelected().getDependencies()) {
            populateTree(child, configurationName, dependencyResult, addedChildren);
        }
    }

    /**
     * Add a child to the dependency tree.
     *
     * @param parent     - The parent node
     * @param id         - The child ID
     * @param childToAdd - The child node
     */
    static void addChild(GradleDependencyTree parent, String id, GradleDependencyTree childToAdd) {
        // If the child already exists, add the Gradle configurations of the input child
        if (parent.getChildren().containsKey(id)) {
            GradleDependencyTree child = parent.getChildren().get(id);
            child.getConfigurations().addAll(childToAdd.getConfigurations());
            child.setUnresolved(child.isUnresolved() && childToAdd.isUnresolved());
        } else {
            parent.getChildren().put(id, childToAdd);
        }
    }
}
