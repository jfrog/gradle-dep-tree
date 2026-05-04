package com.jfrog;

import org.gradle.api.GradleException;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;

/**
 * @author yahavi
 **/
public class Utils {
    private static final String indentationSpace = "  ";

    /**
     * Placeholder used in place of any missing component of a Gradle module id
     * (group / name / version) when the underlying value is null or empty.
     * Centralised so that all sites producing module ids stay in sync: both
     * project root ids (see {@code GenerateDepTrees#getProjectModuleId}) and
     * synthesized project-dep ids (see
     * {@code GradleDependencyTreeUtils#synthesizeProjectNodeId}) must agree on
     * this value, otherwise the dependency-tree files for sibling sub-projects
     * will not merge correctly downstream.
     */
    public static final String UNSPECIFIED_ID_PART = "unspecified";

    /**
     * Build a Gradle module id of the form {@code group:name:version}, replacing
     * any null/empty component with {@link #UNSPECIFIED_ID_PART}. Use this from
     * any code that produces a module id so the placeholder stays consistent.
     *
     * @param group   the group component (may be null/empty)
     * @param name    the name component (may be null/empty)
     * @param version the version component (may be null/empty)
     * @return the joined id, never null
     */
    public static String buildModuleId(String group, String name, String version) {
        return String.join(":", orUnspecified(group), orUnspecified(name), orUnspecified(version));
    }

    private static String orUnspecified(String value) {
        return value == null || value.isEmpty() ? UNSPECIFIED_ID_PART : value;
    }

    public static void saveToFileAsJson(File outputFile, GradleDepTreeResults results) {
        try (FileWriter fileWriter = new FileWriter(outputFile);
             Writer writer = new BufferedWriter(fileWriter)) {
            writer.append("{").append(lineSeparator());
            writer.append(indentationSpace).append(quotedKey("root")).append("\"").append(results.getRoot()).append("\",").append(lineSeparator());
            writer.append(indentationSpace).append(quotedKey("nodes"));
            appendMap(writer, results.getNodes(), indentationSpace);
            writer.append(lineSeparator()).append("}");
            writer.flush();
        } catch (IOException e) {
            throw new GradleException("File '" + outputFile + "' is not writable", e);
        }
    }

    private static void appendToFileAsJson(Writer writer, GradleDependencyNode dependencyTree, String baseIndentation) throws IOException {
        writer.append("{").append(lineSeparator());
        String keyIndentation = baseIndentation + indentationSpace;
        writer.append(keyIndentation).append(quotedKey("unresolved")).append(String.valueOf(dependencyTree.isUnresolved())).append(",").append(lineSeparator());
        writer.append(keyIndentation).append(quotedKey("configurations")).append(stringSetToJson(dependencyTree.getConfigurations())).append(",").append(lineSeparator());
        writer.append(keyIndentation).append(quotedKey("children")).append(stringSetToJson(dependencyTree.getChildren()));
        writer.append(lineSeparator()).append(baseIndentation).append("}");
    }

    private static String quotedKey(String str) {
        return "\"" + str + "\": ";
    }

    private static String stringSetToJson(Set<String> strSet) {
        return strSet.stream()
                .sorted()
                .map(str -> "\"" + str + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    @SuppressWarnings("SameParameterValue")
    private static void appendMap(Writer writer, Map<String, GradleDependencyNode> map, String baseIndentation) throws IOException {
        String keyIndentation = baseIndentation + indentationSpace;
        boolean firstItem = true;
        writer.append("{").append(lineSeparator());
        for (Map.Entry<String, GradleDependencyNode> entry : map.entrySet()) {
            if (firstItem) {
                firstItem = false;
            } else {
                writer.append(",").append(lineSeparator());
            }
            writer.append(keyIndentation).append(quotedKey(entry.getKey()));
            appendToFileAsJson(writer, entry.getValue(), keyIndentation);
        }
        writer.append(lineSeparator()).append(baseIndentation).append("}");
    }
}
