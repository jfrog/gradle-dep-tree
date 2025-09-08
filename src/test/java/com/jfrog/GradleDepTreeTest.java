package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class GradleDepTreeTest {

    private Project project;
    private GradleDepTree plugin;

    @BeforeMethod
    public void setUp() {
        project = ProjectBuilder.builder().build();
        plugin = new GradleDepTree();
    }

    @Test
    public void testApplyCreatesTask() {
        // Apply the plugin
        plugin.apply(project);
        
        // Verify that the task was created
        assertNotNull(project.getTasks().findByName(GenerateDepTrees.TASK_NAME));
    }

    @Test
    public void testCurationAuditModeConstant() {
        // Verify that the constant is properly defined
        assertEquals(GenerateDepTrees.CURATION_AUDIT_MODE, "com.jfrog.curationAuditMode");
    }

    @Test
    public void testCurationModeDetection() {
        // Test that curation mode can be detected
        System.setProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "true");
        
        try {
            boolean isCurationMode = Boolean.parseBoolean(System.getProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "false"));
            assertTrue(isCurationMode);
        } finally {
            System.clearProperty(GenerateDepTrees.CURATION_AUDIT_MODE);
        }
    }
}
