# 📖 Guidelines

- For an easy applying of the plugin's jar, this plugin shouldn't contain dependencies.
- If the existing tests do not already cover your changes, please add tests.
- Pull requests should be created on the _main_ branch.

# ⚒️ Building and Testing the Sources

## Build Gradle Dependency Tree

Clone the sources and CD to the root directory of the project:

```
git clone https://github.com/jfrog/gradle-dep-tree.git
cd gradle-dep-tree
```

Build the sources as follows:

On Unix based systems run:

```
./gradlew clean build
```

On Windows run:

```
gradlew.bat clean build
```

Once completed, you'll find the gradle-dep-tree-<version>.jar at the build/libs/ directory.

## Tests

To run the tests, run the following command:

On Unix based systems run:

```
./gradlew clean check
```

On Windows run:

```
gradlew.bat clean check
```

# 🐞 Debug

To debug the gradle-dep-tree process, follow these steps:
1) Create a new debug configuration in your IDE. If you are using IntelliJ IDEA Choose "Remote JVM Debug" and ensure the following fields are correctly configured:
   * Debugger Mode: Attach to remote JVM
   * Host: localhost
   * Post: 5005
   * Use module classpath: <no module>
2) Run the Gradle command with following flags: `-Dorg.gradle.debug=true --no-daemon`
3) After executing the command with the flags, the process will pause. At this juncture, you can set a breakpoint and initiate the debug configuration in "Debug" mode.
