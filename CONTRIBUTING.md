# 📖 Guidelines

- For an easy applying of the plugin's jar, this plugin shouldn't contain dependencies.
- If the existing tests do not already cover your changes, please add tests.
- Pull requests should be created on the _main_ branch.

# ⚒️ Building and Testing the Sources

## Build Gradle Deps Tree

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
