package com.jfrog;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.testng.annotations.Test;
import org.testng.collections.Sets;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.jfrog.GradleDependencyTreeUtils.ARTIFACT_TYPE_JAR;
import static com.jfrog.GradleDependencyTreeUtils.ARTIFACT_TYPE_POM;
import static com.jfrog.GradleDependencyTreeUtils.addChild;
import static com.jfrog.GradleDependencyTreeUtils.addConfiguration;
import static com.jfrog.GradleDependencyTreeUtils.resolveArtifactType;
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

    /** addChild must union types across repeat appearances, not overwrite them. */
    @Test
    public void testAddChild_unionsTypesAcrossRepeatedAppearances() {
        GradleDependencyNode dep = new GradleDependencyNode("configuration-1");
        Map<String, GradleDependencyNode> nodes = new HashMap<>();
        nodes.put("dep", dep);

        GradleDependencyNode firstAppearance = new GradleDependencyNode("implementation");
        firstAppearance.getTypes().add(ARTIFACT_TYPE_POM);
        addChild(dep, "child-1", firstAppearance, nodes);
        assertEquals(nodes.get("child-1").getTypes(), Sets.newHashSet(ARTIFACT_TYPE_POM));

        GradleDependencyNode secondAppearance = new GradleDependencyNode("testImplementation");
        secondAppearance.getTypes().add(ARTIFACT_TYPE_JAR);
        addChild(dep, "child-1", secondAppearance, nodes);
        assertEquals(nodes.get("child-1").getTypes(), Sets.newHashSet(ARTIFACT_TYPE_POM, ARTIFACT_TYPE_JAR));
    }

    /** No resolved variant — no category evidence, so no guessed default. */
    @Test
    public void testResolveArtifactType_nullResolvedVariant_returnsNull() {
        ResolvedDependencyResult dependency = mock(ResolvedDependencyResult.class);
        when(dependency.getResolvedVariant()).thenReturn(null);

        assertNull(resolveArtifactType(dependency));
    }

    /** No readable category attribute — same as no variant at all. */
    @Test
    public void testResolveArtifactType_variantWithNoCategoryAttribute_returnsNull() {
        ResolvedDependencyResult dependency = dependencyWithCategoryName(null);

        assertNull(resolveArtifactType(dependency));
    }

    @Test
    public void testResolveArtifactType_libraryCategory_returnsJar() {
        ResolvedDependencyResult dependency = dependencyWithCategoryName(Category.LIBRARY);

        assertEquals(resolveArtifactType(dependency), ARTIFACT_TYPE_JAR);
    }

    /** The exact shape reported for `implementation platform('org.springframework.boot:spring-boot-dependencies:...')`. */
    @Test
    public void testResolveArtifactType_regularPlatformCategory_returnsPom() {
        ResolvedDependencyResult dependency = dependencyWithCategoryName(Category.REGULAR_PLATFORM);

        assertEquals(resolveArtifactType(dependency), ARTIFACT_TYPE_POM);
    }

    @Test
    public void testResolveArtifactType_enforcedPlatformCategory_returnsPom() {
        ResolvedDependencyResult dependency = dependencyWithCategoryName(Category.ENFORCED_PLATFORM);

        assertEquals(resolveArtifactType(dependency), ARTIFACT_TYPE_POM);
    }

    /** Must type by this edge's resolved variant, not by every variant the shared component was ever selected as. */
    @Test
    public void testResolveArtifactType_ignoresUnrelatedVariantsOnSharedComponent() {
        // The component was ALSO selected as a platform via some other, unrelated edge.
        ResolvedVariantResult unrelatedPlatformVariant = variantWithCategoryName(Category.REGULAR_PLATFORM);
        ResolvedComponentResult sharedComponent = mock(ResolvedComponentResult.class);
        when(sharedComponent.getVariants()).thenReturn(Collections.singletonList(unrelatedPlatformVariant));

        // But THIS edge's own resolved variant is a regular library.
        ResolvedVariantResult thisEdgeVariant = variantWithCategoryName(Category.LIBRARY);
        ResolvedDependencyResult dependency = mock(ResolvedDependencyResult.class);
        when(dependency.getSelected()).thenReturn(sharedComponent);
        when(dependency.getResolvedVariant()).thenReturn(thisEdgeVariant);

        assertEquals(resolveArtifactType(dependency), ARTIFACT_TYPE_JAR,
                "Must type by this edge's own resolved variant, not by every variant ever "
                        + "selected for the shared component.");
    }

    /** End-to-end regression test: a BOM pulled in via `platform(...)` must be typed "pom" only. */
    @Test
    public void testAddConfiguration_platformDependency_typedAsPomNotJar() {
        ModuleVersionIdentifier bomMv = mock(ModuleVersionIdentifier.class);
        when(bomMv.toString()).thenReturn("org.springframework.boot:spring-boot-dependencies:4.1.0");

        ResolvedComponentResult bomComponent = mock(ResolvedComponentResult.class);
        when(bomComponent.getModuleVersion()).thenReturn(bomMv);
        doReturn(Collections.emptySet()).when(bomComponent).getDependencies();

        ResolvedVariantResult bomVariant = variantWithCategoryName(Category.REGULAR_PLATFORM);
        ResolvedDependencyResult bomDep = mock(ResolvedDependencyResult.class);
        when(bomDep.getSelected()).thenReturn(bomComponent);
        when(bomDep.getResolvedVariant()).thenReturn(bomVariant);

        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(bomDep)).when(rootComponent).getDependencies();

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

        GradleDependencyNode bomNode = nodes.get("org.springframework.boot:spring-boot-dependencies:4.1.0");
        assertNotNull(bomNode, "BOM node missing from tree. Nodes: " + nodes.keySet());
        assertEquals(bomNode.getTypes(), Sets.newHashSet(ARTIFACT_TYPE_POM),
                "A platform/BOM dependency must be typed \"pom\" only, or the CLI will still probe "
                        + "for a jar that was never published and fail with a spurious 404.");
    }

    /** A resolved dependency whose resolved variant has the given {@code org.gradle.category}, or none if null. */
    private static ResolvedDependencyResult dependencyWithCategoryName(String categoryName) {
        ResolvedVariantResult variant = variantWithCategoryName(categoryName);
        ResolvedDependencyResult dependency = mock(ResolvedDependencyResult.class);
        when(dependency.getResolvedVariant()).thenReturn(variant);
        return dependency;
    }

    /** A resolved variant with the given {@code org.gradle.category}, or no attribute if null. */
    private static ResolvedVariantResult variantWithCategoryName(String categoryName) {
        AttributeContainer attributes = mock(AttributeContainer.class);
        if (categoryName != null) {
            // Mocks the keySet()-based lookup resolveArtifactType actually uses, not a direct
            // getAttribute(Category.CATEGORY_ATTRIBUTE) stub.
            @SuppressWarnings("unchecked")
            Attribute<String> categoryAttribute = mock(Attribute.class);
            when(categoryAttribute.getName()).thenReturn("org.gradle.category");
            when(attributes.keySet()).thenReturn(Collections.singleton(categoryAttribute));
            when(attributes.getAttribute(categoryAttribute)).thenReturn(categoryName);
        } else {
            when(attributes.keySet()).thenReturn(Collections.emptySet());
        }

        ResolvedVariantResult variant = mock(ResolvedVariantResult.class);
        when(variant.getAttributes()).thenReturn(attributes);
        return variant;
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

    /**
     * `implementation` itself is never resolvable — a dependency declared only there (no resolved
     * edge anywhere in this test) must get no type guessed at all. Reading category attributes off
     * a raw declared Dependency was tried and found unreliable across Gradle versions (CI showed
     * it silently no-ops on 5.6.4–7.6), so this path deliberately assigns nothing and leaves typing
     * entirely to resolved edges elsewhere in the tree.
     */
    @Test
    public void testAddConfiguration_unresolvedConfigDeclaresDependency_assignsNoType() {
        Dependency dep = mock(Dependency.class);
        when(dep.getGroup()).thenReturn("org.springframework.boot");
        when(dep.getName()).thenReturn("spring-boot-dependencies");
        when(dep.getVersion()).thenReturn("4.1.0");

        DependencySet depSet = mock(DependencySet.class);
        when(depSet.iterator()).thenAnswer(invocation -> Collections.singletonList(dep).iterator());

        Configuration configuration = mock(Configuration.class);
        when(configuration.isCanBeResolved()).thenReturn(false);
        when(configuration.getName()).thenReturn("implementation");
        when(configuration.getDependencies()).thenReturn(depSet);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, configuration, nodes);

        GradleDependencyNode node = nodes.get("org.springframework.boot:spring-boot-dependencies:4.1.0");
        assertNotNull(node, "Node missing from tree. Nodes: " + nodes.keySet());
        assertTrue(node.getTypes().isEmpty(),
                "A dependency declared only on a never-resolvable configuration must have no type "
                        + "guessed — the real type comes from a resolved edge elsewhere.");
    }

    /**
     * Regression test (jfrog-cli-security#876): a dependency Gradle fails to resolve within an
     * otherwise-resolvable configuration must still default to "jar" - curation-audit needs to
     * attempt a check even when resolution itself fails (that's the whole point of curation-audit
     * surviving a broken/partial install). Unlike the never-resolvable-configuration case above,
     * there is no other, correctly-resolved edge anywhere that could supply the real type instead.
     */
    @Test
    public void testAddConfiguration_unresolvedDependencyResultWithinResolvedConfig_defaultsToJar() {
        ComponentSelector requested = mock(ComponentSelector.class);
        when(requested.getDisplayName()).thenReturn("some.group:some-artifact:1.0.0");

        UnresolvedDependencyResult unresolvedDep = mock(UnresolvedDependencyResult.class);
        when(unresolvedDep.getRequested()).thenReturn(requested);

        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(unresolvedDep)).when(rootComponent).getDependencies();

        ResolutionResult resolutionResult = mock(ResolutionResult.class);
        when(resolutionResult.getRoot()).thenReturn(rootComponent);

        ResolvableDependencies incoming = mock(ResolvableDependencies.class);
        when(incoming.getResolutionResult()).thenReturn(resolutionResult);

        Configuration compileClasspath = mock(Configuration.class);
        when(compileClasspath.isCanBeResolved()).thenReturn(true);
        when(compileClasspath.getName()).thenReturn("compileClasspath");
        when(compileClasspath.getIncoming()).thenReturn(incoming);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, compileClasspath, nodes);

        GradleDependencyNode node = nodes.get("some.group:some-artifact:1.0.0");
        assertNotNull(node, "Node missing from tree. Nodes: " + nodes.keySet());
        assertTrue(node.isUnresolved());
        assertEquals(node.getTypes(), Sets.newHashSet(ARTIFACT_TYPE_JAR),
                "An unresolved dependency must default to \"jar\" so curation-audit still attempts "
                        + "a check - leaving it type-less silently skips the check entirely.");
    }

    /**
     * End-to-end regression test for the actual bug: the SAME BOM is declared on
     * {@code implementation} (never-resolvable, contributes no type) and resolved via
     * {@code compileClasspath} (contributes "pom"). The union in addChild must land on exactly
     * {"pom"}, not {"jar", "pom"} — the latter is what shipped and broke curation-audit.
     */
    @Test
    public void testAddConfiguration_platformDepOnBothUnresolvedAndResolvedConfig_unionsToPomOnly() {
        // "implementation": never resolvable, declares the BOM with no type info available.
        Dependency declaredDep = mock(Dependency.class);
        when(declaredDep.getGroup()).thenReturn("org.springframework.boot");
        when(declaredDep.getName()).thenReturn("spring-boot-dependencies");
        when(declaredDep.getVersion()).thenReturn("4.1.0");

        DependencySet depSet = mock(DependencySet.class);
        when(depSet.iterator()).thenAnswer(invocation -> Collections.singletonList(declaredDep).iterator());

        Configuration implementation = mock(Configuration.class);
        when(implementation.isCanBeResolved()).thenReturn(false);
        when(implementation.getName()).thenReturn("implementation");
        when(implementation.getDependencies()).thenReturn(depSet);

        // "compileClasspath": resolvable, resolves the same BOM as a platform.
        ModuleVersionIdentifier bomMv = mock(ModuleVersionIdentifier.class);
        when(bomMv.toString()).thenReturn("org.springframework.boot:spring-boot-dependencies:4.1.0");

        ResolvedComponentResult bomComponent = mock(ResolvedComponentResult.class);
        when(bomComponent.getModuleVersion()).thenReturn(bomMv);
        doReturn(Collections.emptySet()).when(bomComponent).getDependencies();

        ResolvedVariantResult bomVariant = variantWithCategoryName(Category.REGULAR_PLATFORM);
        ResolvedDependencyResult bomDep = mock(ResolvedDependencyResult.class);
        when(bomDep.getSelected()).thenReturn(bomComponent);
        when(bomDep.getResolvedVariant()).thenReturn(bomVariant);

        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(bomDep)).when(rootComponent).getDependencies();

        ResolutionResult resolutionResult = mock(ResolutionResult.class);
        when(resolutionResult.getRoot()).thenReturn(rootComponent);

        ResolvableDependencies incoming = mock(ResolvableDependencies.class);
        when(incoming.getResolutionResult()).thenReturn(resolutionResult);

        Configuration compileClasspath = mock(Configuration.class);
        when(compileClasspath.isCanBeResolved()).thenReturn(true);
        when(compileClasspath.getName()).thenReturn("compileClasspath");
        when(compileClasspath.getIncoming()).thenReturn(incoming);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, implementation, nodes);
        addConfiguration(null, root, compileClasspath, nodes);

        GradleDependencyNode bomNode = nodes.get("org.springframework.boot:spring-boot-dependencies:4.1.0");
        assertNotNull(bomNode, "BOM node missing from tree. Nodes: " + nodes.keySet());
        assertEquals(bomNode.getTypes(), Sets.newHashSet(ARTIFACT_TYPE_POM),
                "Must union to exactly {\"pom\"} — {\"jar\", \"pom\"} is the actual bug that shipped "
                        + "and broke curation-audit for every BOM dependency.");
    }

    /** No category evidence at all — must contribute no type, not a guessed "jar" default. */
    @Test
    public void testAddConfiguration_resolvedDepWithNoCategoryEvidence_contributesNoType() {
        ModuleVersionIdentifier depMv = mock(ModuleVersionIdentifier.class);
        when(depMv.toString()).thenReturn("some.group:some-artifact:1.0.0");

        ResolvedComponentResult depComponent = mock(ResolvedComponentResult.class);
        when(depComponent.getModuleVersion()).thenReturn(depMv);
        doReturn(Collections.emptySet()).when(depComponent).getDependencies();

        ResolvedDependencyResult resolvedDep = mock(ResolvedDependencyResult.class);
        when(resolvedDep.getSelected()).thenReturn(depComponent);
        when(resolvedDep.getResolvedVariant()).thenReturn(null);

        ResolvedComponentResult rootComponent = mock(ResolvedComponentResult.class);
        doReturn(setOf(resolvedDep)).when(rootComponent).getDependencies();

        ResolutionResult resolutionResult = mock(ResolutionResult.class);
        when(resolutionResult.getRoot()).thenReturn(rootComponent);

        ResolvableDependencies incoming = mock(ResolvableDependencies.class);
        when(incoming.getResolutionResult()).thenReturn(resolutionResult);

        Configuration compileClasspath = mock(Configuration.class);
        when(compileClasspath.isCanBeResolved()).thenReturn(true);
        when(compileClasspath.getName()).thenReturn("compileClasspath");
        when(compileClasspath.getIncoming()).thenReturn(incoming);

        GradleDependencyNode root = new GradleDependencyNode();
        Map<String, GradleDependencyNode> nodes = new HashMap<>();

        addConfiguration(null, root, compileClasspath, nodes);

        GradleDependencyNode depNode = nodes.get("some.group:some-artifact:1.0.0");
        assertNotNull(depNode, "Node missing from tree. Nodes: " + nodes.keySet());
        assertTrue(depNode.getTypes().isEmpty(), "No category evidence — types must stay empty, not guess \"jar\".");
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
