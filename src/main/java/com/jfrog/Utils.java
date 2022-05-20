package com.jfrog;

import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;

/**
 * @author yahavi
 **/
public class Utils {

    /**
     * Create the output JSON.
     *
     * @param dependencyTree - The dependency tree
     * @return output JSON.
     */
    public static String toJsonString(GradleDependencyTree dependencyTree) {
        return toJsonString(dependencyTree, "");
    }

    private static String toJsonString(GradleDependencyTree dependencyTree, String baseIndentation) {
        StringBuilder results = new StringBuilder("{").append(lineSeparator());
        String keyIndentation = baseIndentation + "  ";
        results.append(keyIndentation).append(quotedKey("unresolved")).append(dependencyTree.isUnresolved()).append(",").append(lineSeparator());
        results.append(keyIndentation).append(quotedKey("configurations")).append(configurationsToJson(dependencyTree)).append(",").append(lineSeparator());
        results.append(keyIndentation).append(quotedKey("children")).append("{");

        if (dependencyTree.getChildren().isEmpty()) {
            results.append("}");
        } else {
            String childIndentation = keyIndentation + "  ";
            for (Map.Entry<String, GradleDependencyTree> child : dependencyTree.getChildren().entrySet()) {
                results.append(lineSeparator())
                        .append(childIndentation)
                        .append(quotedKey(child.getKey()))
                        .append(toJsonString(child.getValue(), childIndentation))
                        .append(",");
            }
            results.deleteCharAt(results.length() - 1).append(lineSeparator()).append(keyIndentation).append("}");
        }
        return results + lineSeparator() + baseIndentation + "}";
    }

    private static String quotedKey(String str) {
        return "\"" + str + "\": ";
    }

    private static String configurationsToJson(GradleDependencyTree dependencyTree) {
        return dependencyTree.getConfigurations().stream()
                .sorted()
                .map(configuration -> "\"" + configuration + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
