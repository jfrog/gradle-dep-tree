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
 * Happy-path smoke test for a 3-module chain ({@code :app -> :middle -> :lib -> commons-io},
 * also {@code :middle -> commons-lang}) where no subproject declares {@code group}/{@code version}.
 * <p>
 * Stock Gradle (5.6.4 — 8.14.2) synthesises a non-null {@code ModuleVersionIdentifier} for
 * project deps, so this fixture does <em>not</em> trigger the null-moduleVersion path —
 * strict coverage of that path lives in {@code GradleDependencyTreeUtilsTest}. This test
 * just asserts the fix doesn't regress the happy path across all supported Gradle versions.
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
        // Run from :app — with includeAllBuildFiles=false, getRelatedProjects() returns only
        // :app, so we get a single tree file with the whole chain. Output goes to the root
        // project's build dir (<root>/build/gradle-dep-tree/), not <root>/app/build/.
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

            assertNodePresentAndReachable(nodes, root, COMMONS_LANG_ID,
                    "chain :app -> :middle -> commons-lang");
            assertNodePresentAndReachable(nodes, root, COMMONS_IO_ID,
                    "chain :app -> :middle -> :lib -> commons-io");

            // Each surfaced external must carry at least one config label; exact name varies by Gradle version.
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

    /** BFS over children; visited guards against cycles. */
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
