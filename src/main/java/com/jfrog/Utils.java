package com.jfrog;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;

/**
 * @author yahavi
 **/
public class Utils {
    public static void saveToFileAsJson(File outputFile, GradleDepTreeResults results) {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.append("{").append(lineSeparator());
            String baseIndentation = "  ";
            writer.append(baseIndentation).append(quotedKey("root")).append("\"").append(results.getRoot()).append("\",").append(lineSeparator());
            writer.append(baseIndentation).append(quotedKey("nodes"));
            appendMap(writer, results.getNodes(), baseIndentation);
            writer.append(lineSeparator()).append("}");
            writer.flush();
        } catch (IOException e) {
            throw new GradleException("File '" + outputFile + "' is not writable", e);
        }
    }

    private static void appendToFileAsJson(FileWriter writer, GradleDependencyNode dependencyTree, String baseIndentation) throws IOException {
        writer.append("{").append(lineSeparator());
        String keyIndentation = baseIndentation + "  ";
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

    private static void appendMap(FileWriter writer, Map<String, GradleDependencyNode> map, String baseIndentation) throws IOException {
        String keyIndentation = baseIndentation + "  ";
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
