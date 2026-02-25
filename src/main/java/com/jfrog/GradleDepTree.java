package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import java.util.Set;

@SuppressWarnings("unused")
public class GradleDepTree implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        boolean isCurationMode = Boolean.parseBoolean(System.getProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "false"));

        if (isCurationMode) {
            for (Project proj : (Set<Project>) project.getAllprojects()) {
                configureCurationRepositories(proj);
            }
        }

        for (Project proj : (Set<Project>) project.getAllprojects()) {
            proj.getTasks().maybeCreate(GenerateDepTrees.TASK_NAME, GenerateDepTrees.class);
        }
    }

    private void configureCurationRepositories(Project project) {
        CurationUtils.updateRepositoryUrls(project.getRepositories());
        if (project.getBuildscript().getRepositories() != null) {
            CurationUtils.updateRepositoryUrls(project.getBuildscript().getRepositories());
        }
    }
}
