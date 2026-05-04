package com.jfrog;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.jfrog.GradleDependencyTreeUtils.addChild;
import static com.jfrog.GradleDependencyTreeUtils.addConfiguration;
import static com.jfrog.GradleDependencyTreeUtils.resolveNodeId;
import static com.jfrog.GradleDependencyTreeUtils.synthesizeProjectNodeId;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

    /**
     * External dep with a non-null {@link ModuleVersionIdentifier} → preserve the original
     * "group:name:version" id format. The Project lookup path is not exercised here because
     * the synthesizer is only called when {@code getModuleVersion()} is null.
     */
    @Test
    public void testResolveNodeId_externalModule_returnsModuleVersionString() {
        ModuleVersionIdentifier mvId = mock(ModuleVersionIdentifier.class);
        when(mvId.toString()).thenReturn("com.itextpdf:kernel:7.2.5");

        ResolvedComponentResult component = mock(ResolvedComponentResult.class);
        when(component.getModuleVersion()).thenReturn(mvId);

        assertEquals(resolveNodeId(null, component), "com.itextpdf:kernel:7.2.5");
    }

    /**
     * Project dep without {@code group}/{@code version} configured → {@code getModuleVersion()}
     * is {@code null}. With a {@code null} ownerProject the synthesizer falls back to a
     * path-based id; with a real ownerProject it would mirror {@code getProjectModuleId} via
     * {@link Project#findProject(String)} (covered by
     * {@link #testSynthesizeProjectNodeId_resolvableSubproject_matchesGetProjectModuleId}).
     */
    @Test
    public void testResolveNodeId_projectDepWithNullModuleVersion_synthesizesId() {
        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":DummyService");

        ResolvedComponentResult component = mock(ResolvedComponentResult.class);
        when(component.getModuleVersion()).thenReturn(null);
        when(component.getId()).thenReturn(id);

        assertEquals(resolveNodeId(null, component), "unspecified:DummyService:unspecified");
    }

    /**
     * Genuine "not in any repository" case: external module that failed to resolve.
     * Must continue to return null (preserves original drop-the-subtree behaviour), since
     * we have nothing to identify it by.
     */
    @Test
    public void testResolveNodeId_externalModuleWithNullModuleVersion_returnsNull() {
        ModuleComponentIdentifier id = mock(ModuleComponentIdentifier.class);

        ResolvedComponentResult component = mock(ResolvedComponentResult.class);
        when(component.getModuleVersion()).thenReturn(null);
        when(component.getId()).thenReturn(id);

        assertNull(resolveNodeId(null, component));
    }

    @Test
    public void testSynthesizeProjectNodeId_simplePath_nullOwner_usesFallback() {
        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":lib");
        assertEquals(synthesizeProjectNodeId(null, id), "unspecified:lib:unspecified");
    }

    @Test
    public void testSynthesizeProjectNodeId_nestedPath_usesLastSegment() {
        // Mirrors GenerateDepTrees#getProjectModuleId, which uses Project.getName().
        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":services:webservice");
        assertEquals(synthesizeProjectNodeId(null, id), "unspecified:webservice:unspecified");
    }

    @Test
    public void testSynthesizeProjectNodeId_rootPath_fallsBackToUnspecifiedName() {
        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":");
        assertEquals(synthesizeProjectNodeId(null, id), "unspecified:unspecified:unspecified");
    }

    @Test
    public void testSynthesizeProjectNodeId_nullPath_fallsBackToAllUnspecified() {
        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(null);
        assertEquals(synthesizeProjectNodeId(null, id), "unspecified:unspecified:unspecified");
    }

    /**
     * Subproject lookup miss (e.g. cross-build / included-build references where
     * {@link Project#findProject(String)} returns {@code null}) must fall through to the
     * path-based fallback rather than NPE.
     */
    @Test
    public void testSynthesizeProjectNodeId_ownerCannotResolvePath_usesFallback() {
        Project ownerProject = mock(Project.class);
        when(ownerProject.findProject(":externalBuild:lib")).thenReturn(null);

        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":externalBuild:lib");

        assertEquals(synthesizeProjectNodeId(ownerProject, id), "unspecified:lib:unspecified");
    }

    /**
     * THE invariant test — the one that should have caught the original bug.
     * <p>
     * When the ownerProject can resolve the project path (the common case for project deps
     * in the same build), the synthesized id MUST match what {@code GenerateDepTrees#getProjectModuleId}
     * would produce for the same subproject. Otherwise the parent's tree references project
     * deps under one key and the child's own tree file is rooted under a different key —
     * downstream merging breaks and consumers like {@code jf curation-audit} report only a
     * fraction of the real blocked-package signal (this is exactly what the customer who
     * surfaced the original bug saw: 33 blocked packages → 3, all 30 missing deps reachable
     * only via project-dep chains where the merge silently failed).
     * <p>
     * The mock subproject mirrors stock Gradle's default group-derivation for a top-level
     * subproject of root project {@code skyscraper}: group={@code "skyscraper"} (root project's
     * name), version={@code "unspecified"} ({@code Project.DEFAULT_VERSION}).
     */
    @Test
    public void testSynthesizeProjectNodeId_resolvableSubproject_matchesGetProjectModuleId() {
        Project subproject = mock(Project.class);
        // Project.getGroup() / getVersion() return Object (Mockito accepts a String here).
        doReturn("skyscraper").when(subproject).getGroup();
        when(subproject.getName()).thenReturn("DummyService");
        doReturn("unspecified").when(subproject).getVersion();

        Project ownerProject = mock(Project.class);
        when(ownerProject.findProject(":DummyService")).thenReturn(subproject);

        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":DummyService");

        String synthesized = synthesizeProjectNodeId(ownerProject, id);

        // Hard assertion against the literal expected id...
        assertEquals(synthesized, "skyscraper:DummyService:unspecified",
                "Synthesized id must match GenerateDepTrees#getProjectModuleId for the same subproject "
                        + "— without this, per-subproject tree files don't merge downstream and "
                        + "consumers see only a fraction of blocked packages.");
        // ...and the centralisation guarantee: must equal what buildModuleId with the same
        // inputs would produce. If a future change re-inlines String.join or "unspecified"
        // at either id-producing site, this assertion will fail.
        assertEquals(synthesized, com.jfrog.Utils.buildModuleId("skyscraper", "DummyService", "unspecified"));
    }

    /**
     * End-to-end regression for the project-dep null-moduleVersion bug — fallback path
     * (ownerProject {@code null}, so the synthesized id uses path-based placeholders).
     * <p>
     * Configuration shaped as {@code root → :middle (project, no module version) →
     * com.itextpdf:kernel:7.2.5}. Pre-fix, {@code populateTree} returned early at
     * {@code :middle} because its {@link ModuleVersionIdentifier} was {@code null},
     * dropping {@code itextpdf:kernel} from the tree entirely. With the fix, the project
     * dep gets a synthesized id and the transitive {@code itextpdf:kernel} appears in the
     * resulting nodes map. The companion test
     * {@link #testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_resolvableOwner_matchesGetProjectModuleId}
     * exercises the same chain with a real ownerProject and verifies the synthesized id
     * matches {@code getProjectModuleId} (the centralisation invariant).
     */
    @Test
    public void testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_preservesTransitives() {
        // Leaf: external Maven dep com.itextpdf:kernel:7.2.5 (well-formed module version).
        ModuleVersionIdentifier itextMv = mock(ModuleVersionIdentifier.class);
        when(itextMv.toString()).thenReturn("com.itextpdf:kernel:7.2.5");
        ResolvedComponentResult itextComponent = mock(ResolvedComponentResult.class);
        when(itextComponent.getModuleVersion()).thenReturn(itextMv);
        doReturn(Collections.emptySet()).when(itextComponent).getDependencies();

        ResolvedDependencyResult itextDep = mock(ResolvedDependencyResult.class);
        when(itextDep.getSelected()).thenReturn(itextComponent);

        // Middle: project dep :middle, no module version, transitively pulls in itextpdf:kernel.
        ProjectComponentIdentifier middleId = mock(ProjectComponentIdentifier.class);
        when(middleId.getProjectPath()).thenReturn(":middle");

        ResolvedComponentResult middleComponent = mock(ResolvedComponentResult.class);
        when(middleComponent.getModuleVersion()).thenReturn(null);
        when(middleComponent.getId()).thenReturn(middleId);
        doReturn(setOf(itextDep)).when(middleComponent).getDependencies();

        ResolvedDependencyResult middleDep = mock(ResolvedDependencyResult.class);
        when(middleDep.getSelected()).thenReturn(middleComponent);

        // Root: configuration root component with one direct dep (the :middle project).
        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(middleDep)).when(rootComponent).getDependencies();

        ResolutionResult resolutionResult = mock(ResolutionResult.class);
        when(resolutionResult.getRoot()).thenReturn(rootComponent);

        ResolvableDependencies incoming = mock(ResolvableDependencies.class);
        when(incoming.getResolutionResult()).thenReturn(resolutionResult);

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getName()).thenReturn("implementation");
        when(configuration.getIncoming()).thenReturn(incoming);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("root", root);

        // Fallback path: no ownerProject for the synthesizer to resolve through.
        addConfiguration(null, root, configuration, nodes);

        // The chain must survive: both :middle (synthesized) and com.itextpdf:kernel must appear.
        assertTrue(nodes.containsKey("unspecified:middle:unspecified"),
                "Synthesized project node id is missing — populateTree dropped the project dep.");
        assertTrue(nodes.containsKey("com.itextpdf:kernel:7.2.5"),
                "Transitive external dep is missing — chain was broken at the project dep "
                        + "(this is the original bug — null moduleVersion on the project dep "
                        + "caused early-return that dropped the entire subtree below it).");

        // Topology: root → :middle → kernel.
        assertTrue(root.getChildren().contains("unspecified:middle:unspecified"));
        assertTrue(nodes.get("unspecified:middle:unspecified").getChildren()
                .contains("com.itextpdf:kernel:7.2.5"));
    }

    /**
     * End-to-end regression for the SECOND bug (the one that left the customer at 3-of-33
     * blocked packages even after the first patch shipped).
     * <p>
     * Same chain as
     * {@link #testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_preservesTransitives},
     * but with a real ownerProject whose {@link Project#findProject(String)} resolves
     * {@code :middle} to a subproject mock that mirrors stock Gradle's default group/version
     * derivation (group=root project name {@code "skyscraper"}, version={@code "unspecified"}).
     * <p>
     * Crucially, the synthesized middle-node id MUST be {@code "skyscraper:middle:unspecified"}
     * — the same string {@code GenerateDepTrees#getProjectModuleId} would produce when generating
     * {@code :middle}'s own dependency-tree file. If the id is anything else (most notably the
     * fallback {@code "unspecified:middle:unspecified"}), per-subproject tree files don't merge
     * downstream and consumers like {@code jf curation-audit} report a fraction of the real
     * blocked-package signal.
     */
    @Test
    public void testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_resolvableOwner_matchesGetProjectModuleId() {
        // Leaf: external Maven dep com.itextpdf:kernel:7.2.5 (well-formed module version).
        ModuleVersionIdentifier itextMv = mock(ModuleVersionIdentifier.class);
        when(itextMv.toString()).thenReturn("com.itextpdf:kernel:7.2.5");
        ResolvedComponentResult itextComponent = mock(ResolvedComponentResult.class);
        when(itextComponent.getModuleVersion()).thenReturn(itextMv);
        doReturn(Collections.emptySet()).when(itextComponent).getDependencies();

        ResolvedDependencyResult itextDep = mock(ResolvedDependencyResult.class);
        when(itextDep.getSelected()).thenReturn(itextComponent);

        // Middle: project dep :middle, no module version, transitively pulls in itextpdf:kernel.
        ProjectComponentIdentifier middleId = mock(ProjectComponentIdentifier.class);
        when(middleId.getProjectPath()).thenReturn(":middle");

        ResolvedComponentResult middleComponent = mock(ResolvedComponentResult.class);
        when(middleComponent.getModuleVersion()).thenReturn(null);
        when(middleComponent.getId()).thenReturn(middleId);
        doReturn(setOf(itextDep)).when(middleComponent).getDependencies();

        ResolvedDependencyResult middleDep = mock(ResolvedDependencyResult.class);
        when(middleDep.getSelected()).thenReturn(middleComponent);

        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(middleDep)).when(rootComponent).getDependencies();

        ResolutionResult resolutionResult = mock(ResolutionResult.class);
        when(resolutionResult.getRoot()).thenReturn(rootComponent);

        ResolvableDependencies incoming = mock(ResolvableDependencies.class);
        when(incoming.getResolutionResult()).thenReturn(resolutionResult);

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getName()).thenReturn("implementation");
        when(configuration.getIncoming()).thenReturn(incoming);

        // ownerProject — the project whose configuration we're walking. findProject(":middle")
        // returns a subproject mock with stock Gradle default group/version derivation.
        Project middleSubproject = mock(Project.class);
        doReturn("skyscraper").when(middleSubproject).getGroup();
        when(middleSubproject.getName()).thenReturn("middle");
        doReturn("unspecified").when(middleSubproject).getVersion();

        Project ownerProject = mock(Project.class);
        when(ownerProject.findProject(":middle")).thenReturn(middleSubproject);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("root", root);

        addConfiguration(ownerProject, root, configuration, nodes);

        // Centralisation invariant: synthesized id == GenerateDepTrees#getProjectModuleId.
        assertTrue(nodes.containsKey("skyscraper:middle:unspecified"),
                "Synthesized middle id must equal getProjectModuleId's output ('skyscraper:middle:unspecified'); "
                        + "found nodes: " + nodes.keySet() + ". This is the cross-site centralisation invariant — "
                        + "without it, per-subproject tree files don't merge downstream.");
        assertFalse(nodes.containsKey("unspecified:middle:unspecified"),
                "Found fallback id 'unspecified:middle:unspecified' even though ownerProject could resolve "
                        + "the subproject — synthesizer is not consulting Project.findProject(path).");

        // Topology: root → :middle (canonical id) → kernel.
        assertTrue(root.getChildren().contains("skyscraper:middle:unspecified"));
        assertTrue(nodes.get("skyscraper:middle:unspecified").getChildren()
                .contains("com.itextpdf:kernel:7.2.5"));
    }

    /**
     * If a node truly has no usable identity (external dep with null module version and a
     * non-project component identifier), it must still be skipped — same as the original
     * "not in any repository" behaviour. Anything below it is unreachable anyway.
     */
    @Test
    public void testAddConfiguration_unresolvedExternalDepWithoutModuleVersion_isSkipped() {
        ComponentIdentifier nonProjectId = mock(ModuleComponentIdentifier.class);

        ResolvedComponentResult brokenComponent = mock(ResolvedComponentResult.class);
        when(brokenComponent.getModuleVersion()).thenReturn(null);
        when(brokenComponent.getId()).thenReturn(nonProjectId);
        doReturn(Collections.emptySet()).when(brokenComponent).getDependencies();

        ResolvedDependencyResult brokenDep = mock(ResolvedDependencyResult.class);
        when(brokenDep.getSelected()).thenReturn(brokenComponent);

        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(brokenDep)).when(rootComponent).getDependencies();

        ResolutionResult resolutionResult = mock(ResolutionResult.class);
        when(resolutionResult.getRoot()).thenReturn(rootComponent);

        ResolvableDependencies incoming = mock(ResolvableDependencies.class);
        when(incoming.getResolutionResult()).thenReturn(resolutionResult);

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(true);
        when(configuration.getName()).thenReturn("implementation");
        when(configuration.getIncoming()).thenReturn(incoming);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("root", root);

        addConfiguration(null, root, configuration, nodes);

        // Root learns the configuration name but no orphaned child gets added.
        assertTrue(root.getChildren().isEmpty());
        assertEquals(nodes.size(), 1);
    }

    /**
     * Lock-in for the centralisation invariant: {@code addUnresolvedConfiguration} must route
     * its id assembly through {@link com.jfrog.Utils#buildModuleId} so a {@code null} group on
     * a declared dependency does not produce a literal {@code "null:foo:1.0"} node id (which
     * the previous {@code String.join} code path produced).
     * <p>
     * This is a regression guard for the third id-producing site in the codebase — the other
     * two ({@code GenerateDepTrees#getProjectModuleId} and {@code synthesizeProjectNodeId})
     * already share the helper.
     */
    @Test
    public void testAddConfiguration_unresolvedDepWithNullGroup_usesUnspecifiedPlaceholder() {
        Dependency dep = mock(Dependency.class);
        when(dep.getGroup()).thenReturn(null);
        when(dep.getName()).thenReturn("foo");
        when(dep.getVersion()).thenReturn("1.0");

        DependencySet depSet = mock(DependencySet.class);
        // DependencySet extends Iterable<Dependency> — the for-each in addUnresolvedConfiguration
        // calls iterator() on it. A fresh iterator is needed per call.
        when(depSet.iterator()).thenAnswer(invocation -> Collections.singletonList(dep).iterator());

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);
        when(configuration.getName()).thenReturn("api");
        when(configuration.getDependencies()).thenReturn(depSet);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        // ownerProject is irrelevant for unresolved-config flow (no project deps, no
        // synthesizer invocation), so passing null is correct here.
        addConfiguration(null, root, configuration, nodes);

        assertTrue(nodes.containsKey("unspecified:foo:1.0"),
                "Expected centralised 'unspecified:foo:1.0' node id, but found: " + nodes.keySet()
                        + " — the addUnresolvedConfiguration site must use Utils.buildModuleId, "
                        + "otherwise a null group falls back to the literal string 'null'.");
        assertFalse(nodes.containsKey("null:foo:1.0"),
                "Found legacy 'null:foo:1.0' key — the raw String.join regressed.");
        assertTrue(root.getChildren().contains("unspecified:foo:1.0"));
    }

    /**
     * Belt-and-braces: when the unresolved dependency has fully-populated coordinates, the
     * id must be unchanged (i.e. centralisation via {@code buildModuleId} is a no-op for the
     * common case).
     */
    @Test
    public void testAddConfiguration_unresolvedDepWithAllCoordinates_preservesId() {
        Dependency dep = mock(Dependency.class);
        when(dep.getGroup()).thenReturn("com.example");
        when(dep.getName()).thenReturn("foo");
        when(dep.getVersion()).thenReturn("1.0");

        DependencySet depSet = mock(DependencySet.class);
        when(depSet.iterator()).thenAnswer(invocation -> Collections.singletonList(dep).iterator());

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);
        when(configuration.getName()).thenReturn("api");
        when(configuration.getDependencies()).thenReturn(depSet);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, configuration, nodes);

        assertTrue(nodes.containsKey("com.example:foo:1.0"),
                "Common-case id assembly must be unchanged. Nodes: " + nodes.keySet());
        assertTrue(nodes.get("com.example:foo:1.0").isUnresolved(),
                "Unresolved-config children must be flagged unresolved.");
    }

    /**
     * Pre-existing behaviour preserved: dependencies with a {@code null} version — typically
     * {@code implementation gradleApi()} or similar — are skipped entirely (have no usable id).
     */
    @Test
    public void testAddConfiguration_unresolvedDepWithNullVersion_isSkipped() {
        Dependency dep = mock(Dependency.class);
        when(dep.getGroup()).thenReturn("foo");
        when(dep.getName()).thenReturn("bar");
        when(dep.getVersion()).thenReturn(null);

        DependencySet depSet = mock(DependencySet.class);
        when(depSet.iterator()).thenAnswer(invocation -> Collections.singletonList(dep).iterator());

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);
        when(configuration.getName()).thenReturn("api");
        when(configuration.getDependencies()).thenReturn(depSet);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, configuration, nodes);

        assertTrue(root.getChildren().isEmpty(),
                "Dependencies without a version must be skipped (gradleApi()-style).");
        assertTrue(nodes.isEmpty());
    }

    private static <T extends DependencyResult> Set<DependencyResult> setOf(T item) {
        Set<DependencyResult> set = new LinkedHashSet<>();
        set.add(item);
        return set;
    }
}
