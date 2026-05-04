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
     * @param ownerProject  the Gradle project that owns the configuration being walked. Used by
     *                      {@link #synthesizeProjectNodeId(Project, ProjectComponentIdentifier)}
     *                      to look up sibling subprojects when a project dependency's
     *                      {@code ResolvedComponentResult} returns a {@code null}
     *                      {@link ModuleVersionIdentifier}; may be {@code null} (in which case
     *                      the synthesizer falls back to a path-based id with placeholder
     *                      group/version, the pre-fix behaviour).
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
     *
     * @param ownerProject  see {@link #addConfiguration(Project, GradleDependencyNode, Configuration, Map)}
     * @param root          the root node
     * @param configuration resolved Gradle configuration
     * @param nodes         a map of all nodes mapped by their module ID (group:name:version)
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
                // If the version is null, the dependency does not contain an ID and we should not add it.
                // For example: "implementation gradleApi()"
                // Route through the central Utils.buildModuleId helper so a null group does not
                // produce a literal "null:foo:1.0" key — see Utils.UNSPECIFIED_ID_PART javadoc
                // for why the placeholder must stay consistent across all id-producing sites.
                addChild(root, Utils.buildModuleId(dependency.getGroup(), dependency.getName(), dependency.getVersion()), child, nodes);
            }
        }
    }

    /**
     * Recursively populate the dependency tree.
     *
     * @param ownerProject      the project that owns the configuration being walked — passed
     *                          unchanged through recursion so {@link #synthesizeProjectNodeId}
     *                          can look up sibling subprojects by their absolute path
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
            // The component has no usable identity — typically an external dependency that
            // was not found in any repository, or a less common ComponentIdentifier subtype
            // (e.g. LibraryBinaryIdentifier for native/JVM library variants) for which we
            // have no synthesizer. Anything below it is unreachable, so dropping it is safe.
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
     * For external module dependencies, the id is the {@link ModuleVersionIdentifier#toString()}
     * (group:name:version) — same behaviour as before.
     * <p>
     * For local project dependencies (e.g. {@code project(":foo")}),
     * {@link ResolvedComponentResult#getModuleVersion()} is annotated {@code @Nullable} and can
     * return {@code null} when the referenced subproject has no {@code group}/{@code version}
     * configured AND the runtime/plugin combination doesn't synthesize the usual
     * {@code rootProjectName:subproject:unspecified} fallback. Stock Gradle 5.6.4 — 8.14.2 does
     * synthesize that fallback, so the null path is exercised in production by recent JDKs +
     * Gradle 8.14+ in combination with plugins that rewrite resolution metadata
     * (notably {@code io.spring.dependency-management} on a Kotlin DSL build, observed at the
     * customer that surfaced this bug). Returning early in that case (the previous behaviour)
     * silently dropped the entire transitive subtree below the project dependency.
     * <p>
     * Instead of dropping the subtree, we look up the actual subproject via
     * {@link Project#findProject(String)} on the {@code ownerProject} and synthesize an id from
     * its real {@code group}/{@code name}/{@code version} — the exact same string
     * {@code GenerateDepTrees#getProjectModuleId} would produce when generating that
     * subproject's own dependency-tree file. This keeps the cross-site centralisation invariant
     * (see {@link Utils#UNSPECIFIED_ID_PART}) actually true, so per-subproject tree files merge
     * correctly downstream — without it, downstream consumers like {@code jf curation-audit}
     * see only a fraction of the real blocked-package signal because parent trees reference
     * project deps by ids that don't match the child trees' root ids.
     *
     * @param ownerProject the project owning the configuration being walked; may be
     *                     {@code null} (in which case the synthesizer falls back to the
     *                     path-based id with placeholder group/version)
     * @param selected     the resolved component
     * @return the node id, or {@code null} if the component truly has no usable identity
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
     * Build a node id for a project component dependency that has no
     * {@link ModuleVersionIdentifier}. The result MUST equal what
     * {@code GenerateDepTrees#getProjectModuleId} produces for the same subproject —
     * otherwise the per-subproject tree files won't merge correctly downstream
     * (the parent's reference and the child's own root use different keys).
     * <p>
     * Strategy:
     * <ol>
     *   <li>If {@code ownerProject} is non-null and {@link Project#findProject(String)}
     *       resolves the path, use the subproject's actual {@code group}/{@code name}/
     *       {@code version} via {@link Utils#buildModuleId(String, String, String)}.
     *       This is the path that satisfies the centralisation invariant.</li>
     *   <li>Otherwise, fall back to a path-based id with placeholder group/version.
     *       This covers cross-build references (composite/included builds) and any
     *       case where the lookup fails. The fallback id won't merge cleanly with the
     *       child tree's root id — same drawback the pre-fix behaviour had — but it's
     *       still better than dropping the subtree entirely.</li>
     * </ol>
     * <p>
     * Note on collisions: in the lookup path we use the resolved subproject's own
     * {@code Project.getName()}, so this matches {@code getProjectModuleId} exactly,
     * including the pre-existing collision behaviour where sibling subprojects with
     * identical leaf names (e.g. {@code :foo:lib} and {@code :bar:lib}) collapse in the
     * nodes map. Do not "fix" that here without also changing {@code getProjectModuleId}.
     *
     * @param ownerProject the project owning the configuration being walked; may be {@code null}
     * @param id           the project component identifier
     */
    static String synthesizeProjectNodeId(Project ownerProject, ProjectComponentIdentifier id) {
        String path = id.getProjectPath();
        if (path == null) {
            return Utils.buildModuleId(null, null, null);
        }
        if (ownerProject != null) {
            Project subproject = ownerProject.findProject(path);
            if (subproject != null) {
                // Mirror GenerateDepTrees#getProjectModuleId exactly — same helper, same inputs.
                return Utils.buildModuleId(
                        subproject.getGroup().toString(),
                        subproject.getName(),
                        subproject.getVersion().toString());
            }
        }
        // Fallback: cross-build references or unresolvable paths. Less ideal (won't merge
        // with the child tree's root id) but preserves visibility rather than dropping the
        // chain. Documented in the javadoc above.
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
