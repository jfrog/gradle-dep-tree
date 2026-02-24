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
