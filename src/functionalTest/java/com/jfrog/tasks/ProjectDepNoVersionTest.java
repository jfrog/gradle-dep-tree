package com.jfrog.tasks;

import com.jfrog.GradleDepTreeResults;
import com.jfrog.GradleDependencyNode;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.tasks.Consts.PROJECT_DEP_NO_VERSION;
import static com.jfrog.tasks.Consts.TEST_DIR;
import static com.jfrog.tasks.Utils.generateDepTrees;
import static com.jfrog.tasks.Utils.objectMapper;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Happy-path smoke test for the project under {@code resources/projectDepNoVersion/}.
 * <p>
 * Shaped to mirror the customer scenario that surfaced the {@code populateTree} bug:
 * a 3-module chain where no subproject declares {@code group} or {@code version}:
 * <pre>
 *   :app -> :middle -> :lib -> commons-io:commons-io:2.14.0
 *                  \-> commons-lang:commons-lang:2.4
 * </pre>
 * <p>
 * <b>Important:</b> stock Gradle (5.6.4 — 8.14.2) always assigns project deps a synthetic
 * non-null {@link org.gradle.api.artifacts.ModuleVersionIdentifier} of the form
 * {@code <rootProjectName>:<subproject>:unspecified}, so this fixture does <em>not</em>
 * trigger the null-moduleVersion early-return that the bug fix targets. Strict regression
 * coverage for the null-moduleVersion path lives in the Mockito-driven unit test
 * {@code GradleDependencyTreeUtilsTest#testAddConfiguration_chainOfProjectDepsWithoutModuleVersion_preservesTransitives}.
 * <p>
 * What this test <em>does</em> guarantee: the fix introduces no regression on a normal
 * happy-path multi-module project across all five supported Gradle versions — the chain
 * still resolves end-to-end and both transitive externals appear in {@code :app}'s tree.
 *
 * @author jfrog
 **/
public class ProjectDepNoVersionTest extends FunctionalTestBase {

    private static final String COMMONS_IO_ID = "commons-io:commons-io:2.14.0";
    private static final String COMMONS_LANG_ID = "commons-lang:commons-lang:2.4";

    @BeforeMethod
    public void setup() throws IOException {
        setup(PROJECT_DEP_NO_VERSION);
    }

    @Test(dataProvider = "gradleVersions")
    public void testMultiModuleChainWithoutGroupAndVersion_remainsHappyPath(String gradleVersion) throws IOException {
        // Run from the :app sub-project. :app has its own build.gradle, so with
        // includeAllBuildFiles=false getRelatedProjects() returns only :app -- that's
        // exactly what we want: a single tree file containing the entire chain.
        // The plugin always writes its output to the *root project's* build dir, so the
        // output lives at <root>/build/gradle-dep-tree/, not <root>/app/build/...
        generateDepTrees(gradleVersion, false, Paths.get("app"));
        Path outputDir = TEST_DIR.toPath().resolve("build").resolve("gradle-dep-tree");

        try (Stream<Path> files = Files.list(outputDir)) {
            Set<String> outputFiles = files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet());
            assertEquals(outputFiles.size(), 1, "Expected a single tree file for the :app project");

            String onlyFile = outputFiles.iterator().next();
            GradleDepTreeResults results = objectMapper.readValue(outputDir.resolve(onlyFile).toFile(), GradleDepTreeResults.class);
            Map<String, GradleDependencyNode> nodes = results.getNodes();
            GradleDependencyNode root = nodes.get(results.getRoot());
            assertNotNull(root, "Root node missing from tree");

            // Both transitive externals must survive in :app's tree across all supported
            // Gradle versions. This proves the post-fix code path stays well-behaved on
            // the happy path; the null-moduleVersion regression itself is covered by the
            // Mockito-driven unit test (see class javadoc).
            assertNodePresentAndReachable(nodes, root, COMMONS_LANG_ID,
                    "chain :app -> :middle -> commons-lang");
            assertNodePresentAndReachable(nodes, root, COMMONS_IO_ID,
                    "chain :app -> :middle -> :lib -> commons-io");

            // Sanity: each surfaced external must carry at least one configuration label
            // (the resolvable configuration through which it was reached, e.g.
            // 'compileClasspath'). The exact configuration set varies across Gradle
            // versions, so we don't assert the specific name.
            assertTrue(!nodes.get(COMMONS_IO_ID).getConfigurations().isEmpty(),
                    COMMONS_IO_ID + " must carry at least one configuration");
            assertTrue(!nodes.get(COMMONS_LANG_ID).getConfigurations().isEmpty(),
                    COMMONS_LANG_ID + " must carry at least one configuration");
        }
    }

    private static void assertNodePresentAndReachable(Map<String, GradleDependencyNode> nodes,
                                                      GradleDependencyNode root,
                                                      String targetId,
                                                      String chainDescription) {
        assertNotNull(nodes.get(targetId),
                "Transitive dep '" + targetId + "' is missing from the tree -- "
                        + chainDescription + " was dropped. "
                        + "Tree nodes: " + nodes.keySet());
        assertTrue(reachableFrom(root, targetId, nodes),
                "'" + targetId + "' is in nodes but not reachable from the root via children "
                        + "(" + chainDescription + " is broken).");
    }

    /**
     * BFS from {@code from} over the {@code children} relation, returning {@code true} if
     * {@code targetId} is reached. {@code visited} guards against cycles, though the
     * produced tree should not contain any for this fixture.
     */
    private static boolean reachableFrom(GradleDependencyNode from, String targetId, Map<String, GradleDependencyNode> nodes) {
        Deque<String> stack = new ArrayDeque<>(from.getChildren());
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            String id = stack.pop();
            if (!visited.add(id)) {
                continue;
            }
            if (targetId.equals(id)) {
                return true;
            }
            GradleDependencyNode child = nodes.get(id);
            if (child != null) {
                stack.addAll(child.getChildren());
            }
        }
        return false;
    }
}
