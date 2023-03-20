[![Test](https://github.com/jfrog/gradle-dep-tree/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/jfrog/gradle-dep-tree/actions/workflows/test.yml)

# 🐘 Gradle Dependency Tree

This Gradle plugin reads the Gradle dependencies of a given Gradle project, and generates a dependency tree. This
package was developed by JFrog, and is used by the [JFrog IDEA plugin](https://plugins.jetbrains.com/plugin/9834-jfrog)
to generate the dependency tree for projects using Gradle dependencies. You may find this plugin useful for other
purposes and applications as well, by applying it in your build.gradle file.

## 🖥️ Usage

Inject the plugin using the [init.gradle](./init.gradle) initialization script, and run *generateDepTrees* in a
directory containing a build.gradle file. The plugin will generate a dependency tree for each subproject that does not
contain a build.gradle file. To generate a dependency tree for each subproject that contains a Gradle build file, set the `-Dcom.jfrog.includeAllBuildFiles` flag to `true`.

The command:

```bash
gradle clean generateDepTrees -I <path/to/init.gradle> -q -Dcom.jfrog.depsTreeOutputFile=<path/to/output/file>
```

Output:

```
"<path/to/dependency/tree1>"
"<path/to/dependency/tree2>"
...
```

## 🌲 Output Tree Structure

```json
{
  "unresolved": false,
  "configurations": ["implementation", "runtimeImplementation"],
  "children": {
    "child-1": {
      "unresolved": false,
      "configurations": ["implementation"],
      "children": { }
    },
    "child-2": {
      "unresolved": true,
      "configurations": ["implementation"],
      "children": {}
    }
  }
}
```

## 💻 Contributions

We welcome pull requests from the community. To help us improve this project, please read
our [contribution](./CONTRIBUTING.md#-guidelines) guide.
