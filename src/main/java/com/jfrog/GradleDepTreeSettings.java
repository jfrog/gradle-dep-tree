package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class GradleDepTreeSettings implements Plugin<Gradle> {

    private static final String ARTIFACTORY_PATH = "/artifactory/";
    private static final String CURATION_AUDIT_PATH = "/api/curation/audit";

    @Override
    public void apply(@Nonnull Gradle gradle) {
        boolean isCurationMode = Boolean.parseBoolean(
                System.getProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "false"));
        if (!isCurationMode) {
            return;
        }

        gradle.settingsEvaluated(settings -> {
            rewritePluginManagementRepos(settings);
            rewriteDependencyResolutionManagementRepos(settings);
        });
    }

    private void rewritePluginManagementRepos(Settings settings) {
        try {
            RepositoryHandler repos = settings.getPluginManagement().getRepositories();
            updateRepositoryUrls(repos);
        } catch (Exception ignored) {
        }
    }

    private void rewriteDependencyResolutionManagementRepos(Settings settings) {
        try {
            Object depResMgmt = settings.getClass()
                    .getMethod("getDependencyResolutionManagement")
                    .invoke(settings);
            if (depResMgmt != null) {
                RepositoryHandler repos = (RepositoryHandler) depResMgmt.getClass()
                        .getMethod("getRepositories")
                        .invoke(depResMgmt);
                updateRepositoryUrls(repos);
            }
        } catch (Exception ignored) {
        }
    }

    static void updateRepositoryUrls(RepositoryHandler repositories) {
        repositories.all(repo -> {
            if (repo instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) repo;
                String currentUrl = mavenRepo.getUrl().toString();
                if (currentUrl.contains(ARTIFACTORY_PATH) && !currentUrl.contains(CURATION_AUDIT_PATH)) {
                    mavenRepo.setUrl(currentUrl.replace(ARTIFACTORY_PATH, ARTIFACTORY_PATH + "api/curation/audit/"));
                }
            }
        });
    }
}
