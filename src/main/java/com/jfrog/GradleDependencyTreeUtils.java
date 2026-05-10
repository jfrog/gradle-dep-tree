package com.jfrog;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;

import java.util.HashMap;
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
    // The maximum number of times a single dependency is populated, during the run of each configuration.
    private static final int MAX_DEP_POPULATIONS_IN_CONFIG = 10;

    /**
     * Add Gradle configuration including its all dependencies.
     *
     * @param ownerProject  the project owning {@code configuration}, used to look up sibling
     *                      subprojects when a project dep has no {@link ModuleVersionIdentifier};
     *                      may be {@code null}
     * @param root          the root node
     * @param configuration resolved or unresolved Gradle configuration
     * @param nodes         a map of all nodes mapped by their module ID (group:name:version)
     */
    public static void addConfiguration(Project ownerProject, GradleDependencyNode root, Configuration configuration, Map<String, GradleDependencyNode> nodes) {
        if (configuration.isCanBeResolved()) {
            addResolvedConfiguration(ownerProject, root, configuration, nodes);
        } else {
            addUnresolvedConfiguration(root, configuration, nodes);
        }
    }

    /**
     * Add resolved configuration. A resolved configuration may contain transitive dependencies.
     */
    private static void addResolvedConfiguration(Project ownerProject, GradleDependencyNode root, Configuration configuration, Map<String, GradleDependencyNode> nodes) {
        root.getConfigurations().add(configuration.getName());
        ResolvedComponentResult componentResult = configuration.getIncoming().getResolutionResult().getRoot();
        Map<String, Integer> depPopulations = new HashMap<>();
        for (DependencyResult dependency : componentResult.getDependencies()) {
            populateTree(ownerProject, root, configuration.getName(), dependency, new HashSet<>(), nodes, depPopulations);
        }
    }

    /**
     * Add unresolved configuration. An unresolved configuration can contain only direct dependencies.
     *
     * @param root          the parent node
     * @param configuration unresolved Gradle configuration
     * @param nodes         a map of all nodes mapped by their module ID (group:name:version)
     */
    private static void addUnresolvedConfiguration(GradleDependencyNode root, Configuration configuration, Map<String, GradleDependencyNode> nodes) {
        for (Dependency dependency : configuration.getDependencies()) {
            GradleDependencyNode child = new GradleDependencyNode(configuration.getName());
            child.setUnresolved(true);
            if (dependency.getVersion() != null) {
                // Skip deps with no version (e.g. "implementation gradleApi()").
                // Use buildModuleId so a null group becomes "unspecified" instead of the literal "null".
                addChild(root, Utils.buildModuleId(dependency.getGroup(), dependency.getName(), dependency.getVersion()), child, nodes);
            }
        }
    }

    /**
     * Recursively populate the dependency tree.
     *
     * @param ownerProject      see {@link #addConfiguration}; passed unchanged through recursion
     *                          so the project-dep synthesizer can look up sibling subprojects
     * @param node              the parent node
     * @param configurationName the configuration name
     * @param dependency        resolved or unresolved dependency
     * @param addedChildren     a set used to remove duplications to make sure there is no loop in the tree
     * @param nodes             a map of all nodes mapped by their module ID (group:name:version)
     * @param depPopulations    a map of all node population counters mapped by their module ID (group:name:version)
     */
    private static void populateTree(Project ownerProject, GradleDependencyNode node, String configurationName, DependencyResult dependency, Set<String> addedChildren, Map<String, GradleDependencyNode> nodes, Map<String, Integer> depPopulations) {
        GradleDependencyNode child = new GradleDependencyNode(configurationName);
        if (dependency instanceof UnresolvedDependencyResult) {
            child.setUnresolved(true);
            addChild(node, dependency.getRequested().getDisplayName(), child, nodes);
            return;
        }
        ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
        ResolvedComponentResult selected = resolvedDependency.getSelected();
        String nodeId = resolveNodeId(ownerProject, selected);
        if (nodeId == null) {
            // No usable identity (external dep not in any repo, or unsupported ComponentIdentifier subtype).
            return;
        }
        int populations = depPopulations.getOrDefault(nodeId, 0);
        if (!addedChildren.add(nodeId) || populations >= MAX_DEP_POPULATIONS_IN_CONFIG) {
            return;
        }
        depPopulations.put(nodeId, populations + 1);
        for (DependencyResult dependencyResult : selected.getDependencies()) {
            populateTree(ownerProject, child, configurationName, dependencyResult, new HashSet<>(addedChildren), nodes, depPopulations);
        }
        addChild(node, nodeId, child, nodes);
    }

    /**
     * Resolve a stable node id for a resolved component.
     * <p>
     * External deps return {@link ModuleVersionIdentifier#toString()}. Local project deps
     * whose {@code getModuleVersion()} is {@code null} (observed with recent JDKs + Gradle
     * 8.14+ + plugins that rewrite resolution metadata, e.g. {@code io.spring.dependency-management})
     * fall through to {@link #synthesizeProjectNodeId} instead of being dropped.
     *
     * @return the node id, or {@code null} if the component has no usable identity
     */
    static String resolveNodeId(Project ownerProject, ResolvedComponentResult selected) {
        ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
        if (moduleVersion != null) {
            return moduleVersion.toString();
        }
        ComponentIdentifier id = selected.getId();
        if (id instanceof ProjectComponentIdentifier) {
            return synthesizeProjectNodeId(ownerProject, (ProjectComponentIdentifier) id);
        }
        return null;
    }

    /**
     * Build a node id for a project component with no {@link ModuleVersionIdentifier}.
     * The result MUST equal {@code GenerateDepTrees#getProjectModuleId}'s output for the
     * same subproject, otherwise per-subproject tree files won't merge downstream.
     * <p>
     * If {@link Project#findProject(String)} resolves the path, use the subproject's actual
     * {@code group}/{@code name}/{@code version}. Otherwise (cross-build refs, lookup miss)
     * fall back to a path-based id with {@link Utils#UNSPECIFIED_ID_PART} placeholders;
     * the chain survives, but the id won't merge cleanly with the child tree's root.
     */
    static String synthesizeProjectNodeId(Project ownerProject, ProjectComponentIdentifier id) {
        String path = id.getProjectPath();
        if (path == null) {
            return Utils.buildModuleId(null, null, null);
        }
        if (ownerProject != null) {
            Project subproject = ownerProject.findProject(path);
            if (subproject != null) {
                return Utils.buildModuleId(
                        subproject.getGroup().toString(),
                        subproject.getName(),
                        subproject.getVersion().toString());
            }
        }
        int lastColon = path.lastIndexOf(':');
        String name = lastColon >= 0 ? path.substring(lastColon + 1) : path;
        return Utils.buildModuleId(null, name, null);
    }

    /**
     * Add a child to the dependency tree.
     *
     * @param parent     the parent node
     * @param childId    the child ID
     * @param childToAdd the child node
     * @param nodes      a map of all nodes mapped by their module ID (group:name:version)
     */
    static void addChild(GradleDependencyNode parent, String childId, GradleDependencyNode childToAdd, Map<String, GradleDependencyNode> nodes) {
        GradleDependencyNode child = nodes.get(childId);
        if (child == null) {
            nodes.put(childId, childToAdd);
        } else {
            // If the child already exists, add the Gradle configurations of the input child
            child.getConfigurations().addAll(childToAdd.getConfigurations());
            child.setUnresolved(child.isUnresolved() && childToAdd.isUnresolved());
            child.getChildren().addAll(childToAdd.getChildren());
        }
        parent.getChildren().add(childId);
    }
}
