package com.jfrog.tasks;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class GenerateDepTreesTest {

    @Test
    public void testCurationAuditModeConstant() {
        // Test that the curation audit mode constant is properly defined
        assertEquals(GenerateDepTrees.CURATION_AUDIT_MODE, "com.jfrog.curationAuditMode");
    }

    @Test
    public void testCurationAuditModeProperty() {
        // Test that the curation audit mode property can be set and read
        System.setProperty(GenerateDepTrees.CURATION_AUDIT_MODE, "true");
        
        try {
            String retrievedValue = System.getProperty(GenerateDepTrees.CURATION_AUDIT_MODE);
            assertEquals(retrievedValue, "true");
        } finally {
            System.clearProperty(GenerateDepTrees.CURATION_AUDIT_MODE);
        }
    }
}
