package com.jfrog;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class CurationUtilsTest {

    @Test
    public void testRewritesArtifactoryUrl() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://test.jfrog.io/artifactory/libs-release"));

        CurationUtils.updateRepositoryUrls(project.getRepositories());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://test.jfrog.io/artifactory/api/curation/audit/libs-release");
    }

    @Test
    public void testSkipsNonArtifactoryUrl() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://plugins.gradle.org/m2/"));

        CurationUtils.updateRepositoryUrls(project.getRepositories());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://plugins.gradle.org/m2/");
    }

    @Test
    public void testSkipsAlreadyRewrittenUrl() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo ->
                repo.setUrl("https://test.jfrog.io/artifactory/api/curation/audit/libs-release"));

        CurationUtils.updateRepositoryUrls(project.getRepositories());

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://test.jfrog.io/artifactory/api/curation/audit/libs-release");
    }

    @Test
    public void testHandlesMultipleRepos() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://test.jfrog.io/artifactory/repo1"));
        project.getRepositories().maven(repo -> repo.setUrl("https://plugins.gradle.org/m2/"));
        project.getRepositories().maven(repo -> repo.setUrl("https://test.jfrog.io/artifactory/repo2"));

        CurationUtils.updateRepositoryUrls(project.getRepositories());

        Object[] repos = project.getRepositories().toArray();
        assertEquals(((MavenArtifactRepository) repos[0]).getUrl().toString(),
                "https://test.jfrog.io/artifactory/api/curation/audit/repo1");
        assertEquals(((MavenArtifactRepository) repos[1]).getUrl().toString(),
                "https://plugins.gradle.org/m2/");
        assertEquals(((MavenArtifactRepository) repos[2]).getUrl().toString(),
                "https://test.jfrog.io/artifactory/api/curation/audit/repo2");
    }
}
