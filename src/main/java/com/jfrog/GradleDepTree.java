package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

import javax.annotation.Nonnull;
import java.util.Set;

@SuppressWarnings("unused")
public class GradleDepTree implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        // Check if we're in curation audit mode
        boolean isCurationMode = Boolean.parseBoolean(System.getProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "false"));
        
        if (isCurationMode) {
            // Override repositories for all projects when in curation mode
            for (Project proj : (Set<Project>) project.getAllprojects()) {
                configureCurationRepositories(proj);
            }
        }
        
        for (Project proj : (Set<Project>) project.getAllprojects()) {
            proj.getTasks().maybeCreate(GenerateDepTrees.TASK_NAME, GenerateDepTrees.class);
        }
    }
    
    private void configureCurationRepositories(Project project) {
        // Update existing repository URLs to use pass-through
        updateRepositoryUrls(project.getRepositories());
        
        // Also update buildscript repositories if they exist
        if (project.getBuildscript().getRepositories() != null) {
            updateRepositoryUrls(project.getBuildscript().getRepositories());
        }
    }
    
    private void updateRepositoryUrls(RepositoryHandler repositories) {
        repositories.all(repo -> {
            if (repo instanceof org.gradle.api.artifacts.repositories.MavenArtifactRepository) {
                org.gradle.api.artifacts.repositories.MavenArtifactRepository mavenRepo = 
                    (org.gradle.api.artifacts.repositories.MavenArtifactRepository) repo;
                String currentUrl = mavenRepo.getUrl().toString();
                
                // Check if URL contains '/artifactory/something' and update it
                if (currentUrl.contains("/artifactory/") && !currentUrl.contains("/api/curation/audit")) {
                    String updatedUrl = currentUrl.replace("/artifactory/", "/artifactory/api/curation/audit/");
                    mavenRepo.setUrl(updatedUrl);
                }
            }
        });
    }
}
