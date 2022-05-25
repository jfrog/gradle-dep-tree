plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.+"
}

group = "com.jfrog"

repositories {
    mavenCentral()
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

val functionalTest by sourceSets.creating

dependencies {
    testImplementation("commons-io:commons-io:2.11.0")
    testImplementation("org.testng:testng:7.5")
    "functionalTestImplementation"("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
    "functionalTestImplementation"("commons-io:commons-io:2.11.0")
    "functionalTestImplementation"("org.testng:testng:7.5")
    "functionalTestImplementation"(project)
}

pluginBundle {
    website = "https://github.com/jfrog/gradle-dep-tree"
    vcsUrl = "https://github.com/jfrog/gradle-dep-tree"
    tags = listOf("gradle", "dependencies", "dependency-tree")
}

gradlePlugin {
    plugins {
        create("gradleDepsTree") {
            id = "com.jfrog.gradle-dep-tree"
            displayName = "Gradle Dependency Tree"
            description = "A plugin that generates a Gradle dependency tree in Json format"
            implementationClass = "com.jfrog.GradleDepsTree"
        }
    }
    testSourceSets(functionalTest)
}

tasks.withType<Test>().configureEach {
    useTestNG {
        useDefaultListeners(true)
    }
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("started", "passed", "skipped", "failed", "standardOut", "standardError")
        minGranularity = 0
    }
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs the functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    mustRunAfter(tasks.test)
}

tasks.check {
    dependsOn(functionalTestTask)
}
