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
     * Placeholder for any missing component of a Gradle module id (group / name / version).
     * All id-producing sites must agree on this value or per-subproject tree files won't
     * merge correctly downstream.
     */
    public static final String UNSPECIFIED_ID_PART = "unspecified";

    /**
     * Build a {@code group:name:version} module id, substituting {@link #UNSPECIFIED_ID_PART}
     * for any null/empty component. Single source of truth for the placeholder format.
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
