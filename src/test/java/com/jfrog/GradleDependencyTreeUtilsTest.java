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

    @Test
    public void testResolveNodeId_externalModule_returnsModuleVersionString() {
        ModuleVersionIdentifier mvId = mock(ModuleVersionIdentifier.class);
        when(mvId.toString()).thenReturn("com.itextpdf:kernel:7.2.5");

        ResolvedComponentResult component = mock(ResolvedComponentResult.class);
        when(component.getModuleVersion()).thenReturn(mvId);

        assertEquals(resolveNodeId(null, component), "com.itextpdf:kernel:7.2.5");
    }

    @Test
    public void testResolveNodeId_projectDepWithNullModuleVersion_synthesizesId() {
        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":DummyService");

        ResolvedComponentResult component = mock(ResolvedComponentResult.class);
        when(component.getModuleVersion()).thenReturn(null);
        when(component.getId()).thenReturn(id);

        // Null ownerProject → fallback path-based id.
        assertEquals(resolveNodeId(null, component), "unspecified:DummyService:unspecified");
    }

    @Test
    public void testResolveNodeId_externalModuleWithNullModuleVersion_returnsNull() {
        // External dep that failed to resolve: no ProjectComponentIdentifier, no fallback.
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

    @Test
    public void testSynthesizeProjectNodeId_ownerCannotResolvePath_usesFallback() {
        // Cross-build / included-build refs: findProject returns null → fallback, not NPE.
        Project ownerProject = mock(Project.class);
        when(ownerProject.findProject(":externalBuild:lib")).thenReturn(null);

        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":externalBuild:lib");

        assertEquals(synthesizeProjectNodeId(ownerProject, id), "unspecified:lib:unspecified");
    }

    /**
     * Centralisation invariant: when ownerProject can resolve the path, the synthesized id
     * MUST equal {@code GenerateDepTrees#getProjectModuleId}'s output for the same subproject.
     */
    @Test
    public void testSynthesizeProjectNodeId_resolvableSubproject_matchesGetProjectModuleId() {
        Project subproject = mock(Project.class);
        // getGroup()/getVersion() return Object — doReturn lets Mockito accept the String.
        doReturn("skyscraper").when(subproject).getGroup();
        when(subproject.getName()).thenReturn("DummyService");
        doReturn("unspecified").when(subproject).getVersion();

        Project ownerProject = mock(Project.class);
        when(ownerProject.findProject(":DummyService")).thenReturn(subproject);

        ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);
        when(id.getProjectPath()).thenReturn(":DummyService");

        String synthesized = synthesizeProjectNodeId(ownerProject, id);
        assertEquals(synthesized, "skyscraper:DummyService:unspecified",
                "Synthesized id must match GenerateDepTrees#getProjectModuleId for the same subproject "
                        + "— without this, per-subproject tree files don't merge downstream and "
                        + "consumers see only a fraction of blocked packages.");
        assertEquals(synthesized, com.jfrog.Utils.buildModuleId("skyscraper", "DummyService", "unspecified"));
    }

    /**
     * End-to-end: chain {@code root → :middle (no module version) → com.itextpdf:kernel}.
     * Pre-fix, populateTree returned early at :middle and dropped kernel. Fallback path here
     * (null ownerProject) — synthesized id uses placeholder group/version.
     */
    @Test
    public void testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_preservesTransitives() {
        ModuleVersionIdentifier itextMv = mock(ModuleVersionIdentifier.class);
        when(itextMv.toString()).thenReturn("com.itextpdf:kernel:7.2.5");
        ResolvedComponentResult itextComponent = mock(ResolvedComponentResult.class);
        when(itextComponent.getModuleVersion()).thenReturn(itextMv);
        doReturn(Collections.emptySet()).when(itextComponent).getDependencies();

        ResolvedDependencyResult itextDep = mock(ResolvedDependencyResult.class);
        when(itextDep.getSelected()).thenReturn(itextComponent);

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

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("root", root);

        addConfiguration(null, root, configuration, nodes);

        assertTrue(nodes.containsKey("unspecified:middle:unspecified"),
                "Synthesized project node id is missing — populateTree dropped the project dep.");
        assertTrue(nodes.containsKey("com.itextpdf:kernel:7.2.5"),
                "Transitive external dep is missing — chain was broken at the project dep "
                        + "(this is the original bug — null moduleVersion on the project dep "
                        + "caused early-return that dropped the entire subtree below it).");
        assertTrue(root.getChildren().contains("unspecified:middle:unspecified"));
        assertTrue(nodes.get("unspecified:middle:unspecified").getChildren()
                .contains("com.itextpdf:kernel:7.2.5"));
    }

    /**
     * Same chain as above but with a resolvable ownerProject — the synthesized middle id
     * must match getProjectModuleId ({@code "skyscraper:middle:unspecified"}), not the
     * fallback ({@code "unspecified:middle:unspecified"}).
     */
    @Test
    public void testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_resolvableOwner_matchesGetProjectModuleId() {
        ModuleVersionIdentifier itextMv = mock(ModuleVersionIdentifier.class);
        when(itextMv.toString()).thenReturn("com.itextpdf:kernel:7.2.5");
        ResolvedComponentResult itextComponent = mock(ResolvedComponentResult.class);
        when(itextComponent.getModuleVersion()).thenReturn(itextMv);
        doReturn(Collections.emptySet()).when(itextComponent).getDependencies();

        ResolvedDependencyResult itextDep = mock(ResolvedDependencyResult.class);
        when(itextDep.getSelected()).thenReturn(itextComponent);

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

        // Subproject mirrors stock Gradle default derivation: group = root project name.
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

        assertTrue(nodes.containsKey("skyscraper:middle:unspecified"),
                "Synthesized middle id must equal getProjectModuleId's output ('skyscraper:middle:unspecified'); "
                        + "found nodes: " + nodes.keySet() + ". This is the cross-site centralisation invariant — "
                        + "without it, per-subproject tree files don't merge downstream.");
        assertFalse(nodes.containsKey("unspecified:middle:unspecified"),
                "Found fallback id 'unspecified:middle:unspecified' even though ownerProject could resolve "
                        + "the subproject — synthesizer is not consulting Project.findProject(path).");
        assertTrue(root.getChildren().contains("skyscraper:middle:unspecified"));
        assertTrue(nodes.get("skyscraper:middle:unspecified").getChildren()
                .contains("com.itextpdf:kernel:7.2.5"));
    }

    /** External dep with no module version and non-project id → still skipped (unreachable). */
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
     * addUnresolvedConfiguration must route through Utils.buildModuleId so a null group
     * produces "unspecified:foo:1.0" rather than the literal "null:foo:1.0".
     */
    @Test
    public void testAddConfiguration_unresolvedDepWithNullGroup_usesUnspecifiedPlaceholder() {
        Dependency dep = mock(Dependency.class);
        when(dep.getGroup()).thenReturn(null);
        when(dep.getName()).thenReturn("foo");
        when(dep.getVersion()).thenReturn("1.0");

        DependencySet depSet = mock(DependencySet.class);
        // Fresh iterator per call — DependencySet's for-each in addUnresolvedConfiguration calls iterator().
        when(depSet.iterator()).thenAnswer(invocation -> Collections.singletonList(dep).iterator());

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);
        when(configuration.getName()).thenReturn("api");
        when(configuration.getDependencies()).thenReturn(depSet);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, configuration, nodes);

        assertTrue(nodes.containsKey("unspecified:foo:1.0"),
                "Expected centralised 'unspecified:foo:1.0' node id, but found: " + nodes.keySet()
                        + " — the addUnresolvedConfiguration site must use Utils.buildModuleId, "
                        + "otherwise a null group falls back to the literal string 'null'.");
        assertFalse(nodes.containsKey("null:foo:1.0"),
                "Found legacy 'null:foo:1.0' key — the raw String.join regressed.");
        assertTrue(root.getChildren().contains("unspecified:foo:1.0"));
    }

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

    /** Pre-existing: deps with no version (e.g. gradleApi()) are skipped. */
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
