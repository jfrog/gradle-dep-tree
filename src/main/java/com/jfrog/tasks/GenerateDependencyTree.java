package com.jfrog.tasks;

import com.jfrog.GradleDependencyTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.UnresolvableConfigurationResult;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.jfrog.Utils.toJsonString;

/**
 * Represents the generateDependencyTree Gradle task.
 *
 * @author yahavi
 **/
@CacheableTask
public class GenerateDependencyTree extends DefaultTask {
    public static final String TASK_NAME = "generateDependencyTree";

    private final Path pluginOutputDir = Paths.get(getProject().getRootProject().getBuildDir().getPath(), "gradle-deps-tree");

    public GenerateDependencyTree() {
        // Disables executing this task on subprojects
        setImpliesSubProjects(true);
    }

    @Internal
    @Override
    @Nonnull
    public String getName() {
        return TASK_NAME;
    }

    /**
     * This method used by Gradle, to decide whether this task is up-to-date or should be running.
     * If the build.gradle of the project or a build.gradle file of a parent was changed, the cache should be invalidated.
     *
     * @return a list of the build.gradle files of the project and its parents.
     */
    @InputFiles
    @Classpath
    public List<File> getInputFile() {
        List<File> inputFiles = new ArrayList<>();
        for (Project project = getProject(); project != null; project = project.getParent()) {
            inputFiles.add(project.getBuildFile());
        }
        return inputFiles;
    }

    /**
     * This method used by Gradle, to decide whether this task is up-to-date or should be running.
     * If an output file is missing, the task should be running.
     *
     * @return a list of the output files of the task.
     */
    @OutputFiles
    public List<File> getOutputFiles() {
        List<File> outputFiles = new ArrayList<>();
        for (Project project : getRelatedProjects()) {
            outputFiles.add(getProjectOutputFile(project));
        }
        return outputFiles;
    }

    @TaskAction
    void generateDependencyTree() throws IOException {
        createOutputDir();
        for (Project project : getRelatedProjects()) {
            GradleDependencyTree dependencyTree = createProjectDependencyTree(project);
            // Write output to file
            Files.write(getProjectOutputFile(project).toPath(), toJsonString(dependencyTree).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Write the summary to the stdout after the task finished.
     *
     * @return task dependency.
     */
    @Internal
    @Override
    @Nonnull
    public TaskDependency getFinalizedBy() {
        for (File file : getOutputFiles()) {
            System.out.println(file.getAbsolutePath());
        }
        return super.getFinalizedBy();
    }

    /**
     * Related projects:
     * - The current running project.
     * - Subprojects that doesn't contain build.gradle file - this is needed to allow running this task concurrently on build.gradle files.
     *
     * @return list of related projects.
     */
    private List<Project> getRelatedProjects() {
        List<Project> relatedProjects = new ArrayList<>();
        relatedProjects.add(getProject());

        for (Project project : (Set<Project>) getProject().getSubprojects()) {
            if (!project.getBuildFile().exists()) {
                relatedProjects.add(project);
            }
        }
        return relatedProjects;
    }

    /**
     * Get the output file of the project.
     *
     * @param project - The current Gradle project
     * @return the output file of the project.
     */
    private File getProjectOutputFile(Project project) {
        return project.file(pluginOutputDir.resolve(Base64.getEncoder().encodeToString(project.getName().getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Create "${buildDir}/gradle-deps-tree" directory.
     *
     * @throws IOException in case of any I/O error.
     */
    private void createOutputDir() throws IOException {
        Files.createDirectories(pluginOutputDir);
    }

    /**
     * Generate the dependency tree for all project's configurations.
     *
     * @param project - The Gradle project
     * @return dependency tree for all project's configurations.
     */
    private GradleDependencyTree createProjectDependencyTree(Project project) {
        GradleDependencyTree results = new GradleDependencyTree();
        for (Configuration configuration : project.getConfigurations()) {
            RenderableDependency root = configuration.isCanBeResolved() ?
                    new RenderableModuleResult(configuration.getIncoming().getResolutionResult().getRoot()) :
                    new UnresolvableConfigurationResult(configuration);
            populateDependencyTree(root, results, configuration.getName(), new HashSet<>());
        }
        return results;
    }

    /**
     * Populate the dependency tree with the given Gradle dependencies tree of a configuration.
     *
     * @param gradleDependencyNode - Input - The root node of Gradle dependency tree
     * @param node                 - Output - The root dependency tree
     * @param configuration        - The configuration name
     * @param added                - Set of added dependencies, used to prevent adding a loop in the dependency tree
     */
    @SuppressWarnings("unchecked")
    private void populateDependencyTree(RenderableDependency gradleDependencyNode, GradleDependencyTree node, String configuration, Set<String> added) {
        for (RenderableDependency gradleDependencyChild : (Set<RenderableDependency>) gradleDependencyNode.getChildren()) {
            GradleDependencyTree child = new GradleDependencyTree(configuration);
            String childName;
            if (gradleDependencyChild.getId() instanceof DefaultProjectDependency) {
                // Project dependency
                DefaultProjectDependency projectDependency = (DefaultProjectDependency) gradleDependencyChild.getId();
                childName = projectDependency.getGroup() + ":" + projectDependency.getName() + ":" + projectDependency.getVersion();
            } else {
                // If needed, remove the " -> " from the dependency version
                childName = gradleDependencyChild.getName().replace(" -> ", ":");
                boolean unresolved = gradleDependencyChild.getResolutionState() == RenderableDependency.ResolutionState.UNRESOLVED ||
                        gradleDependencyChild.getResolutionState() == RenderableDependency.ResolutionState.FAILED;
                child.setUnresolved(unresolved);
            }
            node.addChild(childName, child);

            // If the dependency exist in the set, don't populate its children.
            if (!added.add(childName)) {
                continue;
            }
            populateDependencyTree(gradleDependencyChild, child, configuration, new HashSet<>(added));
        }
    }
}
