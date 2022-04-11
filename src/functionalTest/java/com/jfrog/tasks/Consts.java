package com.jfrog.tasks;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author yahavi
 */
public class Consts {
    // The test directory
    static final File TEST_DIR = new File(System.getProperty("java.io.tmpdir"), "functional-test-project");

    // Root directories
    private static final Path PROJECTS_ROOT = Paths.get("src", "functionalTest", "resources");

    // Projects
    static final Path ONE_FILE = PROJECTS_ROOT.resolve("oneBuildFile");
    static final Path MULTI = PROJECTS_ROOT.resolve("multi");
    static final Path BASIC = PROJECTS_ROOT.resolve("basic");
    static final Path EMPTY = PROJECTS_ROOT.resolve("empty");
}
