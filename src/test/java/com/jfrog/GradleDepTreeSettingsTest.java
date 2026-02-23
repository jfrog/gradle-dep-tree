package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class GradleDepTreeSettingsTest {

    @AfterMethod
    public void tearDown() {
        System.clearProperty(GenerateDepTrees.CURATION_AUDIT_MODE);
    }

    @Test
    public void testUpdateRepositoryUrlsRewritesArtifactoryUrl() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://myserver.jfrog.io/artifactory/gradle-plugins"));

        GradleDepTreeSettings.updateRepositoryUrls(project.getRepositories());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://myserver.jfrog.io/artifactory/api/curation/audit/gradle-plugins");
    }

    @Test
    public void testUpdateRepositoryUrlsSkipsNonArtifactoryUrl() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://plugins.gradle.org/m2/"));

        GradleDepTreeSettings.updateRepositoryUrls(project.getRepositories());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://plugins.gradle.org/m2/");
    }

    @Test
    public void testUpdateRepositoryUrlsSkipsAlreadyRewritten() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo ->
                repo.setUrl("https://myserver.jfrog.io/artifactory/api/curation/audit/gradle-plugins"));

        GradleDepTreeSettings.updateRepositoryUrls(project.getRepositories());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://myserver.jfrog.io/artifactory/api/curation/audit/gradle-plugins");
    }

    @Test
    public void testUpdateRepositoryUrlsHandlesMultipleRepos() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://myserver.jfrog.io/artifactory/repo1"));
        project.getRepositories().maven(repo -> repo.setUrl("https://plugins.gradle.org/m2/"));
        project.getRepositories().maven(repo -> repo.setUrl("https://myserver.jfrog.io/artifactory/repo2"));

        GradleDepTreeSettings.updateRepositoryUrls(project.getRepositories());

        Object[] repos = project.getRepositories().toArray();
        assertEquals(((MavenArtifactRepository) repos[0]).getUrl().toString(),
                "https://myserver.jfrog.io/artifactory/api/curation/audit/repo1");
        assertEquals(((MavenArtifactRepository) repos[1]).getUrl().toString(),
                "https://plugins.gradle.org/m2/");
        assertEquals(((MavenArtifactRepository) repos[2]).getUrl().toString(),
                "https://myserver.jfrog.io/artifactory/api/curation/audit/repo2");
    }

    @Test
    public void testApplyDoesNothingWhenCurationModeOff() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://myserver.jfrog.io/artifactory/libs-release"));

        new GradleDepTreeSettings().apply(project.getGradle());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://myserver.jfrog.io/artifactory/libs-release");
    }

    @Test
    public void testApplyRegistersCallbackWhenCurationModeOn() {
        System.setProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "true");
        Project project = ProjectBuilder.builder().build();

        new GradleDepTreeSettings().apply(project.getGradle());

    }
}
