package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class GradleDepTreeSettings implements Plugin<Gradle> {

    private static final Logger logger = LoggerFactory.getLogger(GradleDepTreeSettings.class);

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
            CurationUtils.updateRepositoryUrls(repos);
        } catch (Exception e) {
            logger.warn("Failed to rewrite pluginManagement repository URLs for curation audit: {}", e.getMessage());
        }
    }

    private void rewriteDependencyResolutionManagementRepos(Settings settings) {
        try {
            // The reflection-based access to `getDependencyResolutionManagement()` is intentional for backward compatibility with Gradle < 6.8
            Object depResMgmt = settings.getClass()
                    .getMethod("getDependencyResolutionManagement")
                    .invoke(settings);
            if (depResMgmt != null) {
                RepositoryHandler repos = (RepositoryHandler) depResMgmt.getClass()
                        .getMethod("getRepositories")
                        .invoke(depResMgmt);
                CurationUtils.updateRepositoryUrls(repos);
            }
        } catch (Exception e) {
            logger.warn("Failed to rewrite dependencyResolutionManagement repository URLs for curation audit: {}", e.getMessage());
        }
    }
}
