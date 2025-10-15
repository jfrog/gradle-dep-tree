package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class GradleDepTreeTest {

    @AfterMethod
    public void tearDown() {
        System.clearProperty(GenerateDepTrees.CURATION_AUDIT_MODE);
    }

    @Test
    public void testCurationModeAddsApiPrefix() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://test.jfrog.io/artifactory/libs-release"));
        System.setProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "true");
        
        new GradleDepTree().apply(project);

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://test.jfrog.io/artifactory/api/curation/audit/libs-release");
    }

    @Test
    public void testNoCurationModeNoChange() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://test.jfrog.io/artifactory/libs-release"));
        
        new GradleDepTree().apply(project);

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://test.jfrog.io/artifactory/libs-release");
    }

    @Test
    public void testNonArtifactoryUrlUnchanged() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://repo1.maven.org/maven2/"));
        System.setProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "true");
        
        new GradleDepTree().apply(project);

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://repo1.maven.org/maven2/");
    }

    @Test
    public void testAlreadyModifiedUrlNotDuplicated() {
        Project project = ProjectBuilder.builder().build();
        project.getRepositories().maven(repo -> repo.setUrl("https://test.jfrog.io/artifactory/api/curation/audit/libs-release"));
        System.setProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "true");
        
        new GradleDepTree().apply(project);

        MavenArtifactRepository repo = (MavenArtifactRepository) project.getRepositories().iterator().next();
        assertEquals(repo.getUrl().toString(), "https://test.jfrog.io/artifactory/api/curation/audit/libs-release");
    }
}
