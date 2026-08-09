[![Scanned by Frogbot](https://raw.github.com/jfrog/frogbot/master/images/frogbot-badge.svg)](https://github.com/jfrog/frogbot#readme)
[![Test](https://github.com/jfrog/gradle-dep-tree/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/jfrog/gradle-dep-tree/actions/workflows/test.yml)

# 🐘 Gradle Dependency Tree

This Gradle plugin reads the Gradle dependencies of a given Gradle project, and generates a dependency tree. This
package was developed by JFrog, and is used by the [JFrog IntelliJ IDEA Plugin](https://plugins.jetbrains.com/plugin/9834-jfrog)
to generate the dependency tree for projects using Gradle dependencies. You may find this plugin useful for other
purposes and applications as well, by applying it in your build.gradle file.

## 🖥️ Usage

Inject the plugin using the [init.gradle](./init.gradle) initialization script, and run *generateDepTrees* in a
directory containing a build.gradle file. The plugin will generate a dependency tree for each subproject that does not
contain a build.gradle file. To generate a dependency tree for each subproject that contains a Gradle build file, set the `-Dcom.jfrog.includeAllBuildFiles` flag to `true`.

To skip Gradle test configurations (`testImplementation`, `testCompileClasspath`, `androidTest*`, etc.), set
`-Dcom.jfrog.excludeTestConfigurations=true`. Debug/release configurations that do not contain `test` in the name are
still included.

The command:

```bash
gradle clean generateDepTrees -I <path/to/init.gradle> -q -Dcom.jfrog.depsTreeOutputFile=<path/to/output/file>
```

Exclude test configurations:

```bash
gradle clean generateDepTrees -I <path/to/init.gradle> -q \
  -Dcom.jfrog.depsTreeOutputFile=<path/to/output/file> \
  -Dcom.jfrog.excludeTestConfigurations=true
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
  "root": "org.jfrog.example.gradle:shared:1.0-SNAPSHOT",
  "nodes": {
    "junit:junit:4.7": {
      "unresolved": false,
      "configurations": ["testCompileClasspath", "testImplementation", "testRuntimeClasspath"],
      "children": []
    },
    "org.jfrog.example.gradle:shared:1.0-SNAPSHOT": {
      "unresolved": false,
      "configurations": ["compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath"],
      "children": ["junit:junit:4.7"]
    }
  }
}
```

## 💻 Contributions

We welcome pull requests from the community. To help us improve this project, please read
our [contribution](./CONTRIBUTING.md#-guidelines) guide.
