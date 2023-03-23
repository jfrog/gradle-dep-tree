package com.jfrog.tasks;

import com.jfrog.GradleDependencyTree;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.*;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static com.jfrog.GradleDependencyTreeUtils.addConfiguration;
import static com.jfrog.Utils.toJsonString;

/**
 * Represents the generateDepTrees Gradle task.
 *
 * @author yahavi
 **/
@CacheableTask
public class GenerateDepTrees extends DefaultTask {
    public static final String OUTPUT_FILE_PROPERTY = "com.jfrog.depsTreeOutputFile";
    public static final String TASK_NAME = "generateDepTrees";
    public static final String INCLUDE_ALL_BUILD_FILES = "com.jfrog.includeAllBuildFiles";

    private final Path pluginOutputDir = Paths.get(getProject().getRootProject().getBuildDir().getPath(), "gradle-dep-tree");

    public GenerateDepTrees() {
        // Disables executing this task on subprojects
        setImpliesSubProjects(true);
        setOnlyIf(element -> {
            if (System.getProperty(OUTPUT_FILE_PROPERTY) == null) {
                throw new GradleException("'" + OUTPUT_FILE_PROPERTY + "' system property is mandatory");
            }
            return true;
        });
    }

    @Internal
    @Override
    @Nonnull
    public String getName() {
        return TASK_NAME;
    }

    /**
     * This method is used by Gradle, to decide whether this task is up-to-date or should be running.
     * If the build.gradle of the project or a build.gradle file of a parent project was changed, the cache should be invalidated.
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
     * This method is used by Gradle, to decide whether this task is up-to-date or should be running.
     * If an output file is missing, the task will be executed.
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
    void generateDepTrees() throws IOException {
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
        String outputFileName = System.getProperty(OUTPUT_FILE_PROPERTY);
        String projectPath = getProject().getProjectDir().getAbsolutePath();
        File outputFile = new File(projectPath, outputFileName);
        try (FileWriter writer = new FileWriter(outputFile, false)) {
            for (File file : getOutputFiles()) {
                writer.append(file.getAbsolutePath()).append(System.lineSeparator());
            }
            writer.flush();
        } catch (IOException e) {
            throw new GradleException("File '" + outputFile + "' is not writable", e);
        }
        return super.getFinalizedBy();
    }

    /**
     * Related projects:
     * - The current running project.
     * - Subprojects that don't contain build.gradle file - this is needed to allow running this task concurrently on
     * build.gradle files. The user should be allowed to run "gradle generateDepTrees" on each one of the
     * build.gradle files in his/here project.
     *
     * @return list of related projects.
     */
    private List<Project> getRelatedProjects() {
        List<Project> relatedProjects = new ArrayList<>();
        relatedProjects.add(getProject());
        boolean includeAllBuildFiles = Boolean.parseBoolean(System.getProperty(INCLUDE_ALL_BUILD_FILES));

        for (Project project : (Set<Project>) getProject().getSubprojects()) {
            if (includeAllBuildFiles || !project.getBuildFile().exists()) {
                relatedProjects.add(project);
            }
        }
        return relatedProjects;
    }

    /**
     * Get the output file of the project. The output files are list of files under ${buildDir}/gradle-dep-tree
     * directory. The files are generated in the end of the "generateDepTrees" task, for each one of the related
     * projects. To support special characters, the name of the output file is a base64 encoding of the project name.
     *
     * @param project - The current Gradle project
     * @return the output file of the project.
     */
    private File getProjectOutputFile(Project project) {
        return project.file(pluginOutputDir.resolve(Base64.getEncoder().encodeToString(project.getName().getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Create "${buildDir}/gradle-dep-tree" directory.
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
            addConfiguration(results, configuration);
        }
        return results;
    }
}
