plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
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
    testImplementation("commons-io:commons-io:2.14.0")
    testImplementation("org.testng:testng:7.7.1")
    "functionalTestImplementation"("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    "functionalTestImplementation"("commons-io:commons-io:2.14.0")
    "functionalTestImplementation"("org.testng:testng:7.7.1")
    "functionalTestImplementation"(project)
}

pluginBundle {
    website = "https://github.com/jfrog/gradle-dep-tree"
    vcsUrl = "https://github.com/jfrog/gradle-dep-tree"
    tags = listOf("gradle", "dependencies", "dependency-tree")
}

gradlePlugin {
    isAutomatedPublishing = false
    plugins {
        create("gradleDepTree") {
            id = "com.jfrog.gradle-dep-tree"
            displayName = "Gradle Dependency Tree"
            description = "A plugin that generates a Gradle dependency tree in Json format"
            implementationClass = "com.jfrog.GradleDepTree"
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

java {
    withJavadocJar()
    withSourcesJar()
}

nexusPublishing {
    repositories {
         sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("gradle-dep-tree")
                description.set("JFrog gradle-dep-tree")
                url.set("https://github.com/jfrog/gradle-dep-tree")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("JFrog")
                        email.set("eco-system@jfrog.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/jfrog/gradle-dep-tree.git")
                    developerConnection.set("scm:git:git@github.com/jfrog/gradle-dep-tree.git")
                    url.set("https://github.com/jfrog/gradle-dep-tree")
                }
            }

            from(components["java"])
        }
    }
}

signing {
    if (project.hasProperty("sign")) {
        val signingKey: String? = findProperty("signingKey")?.toString()
        val signingPassword: String? = findProperty("signingPassword")?.toString()
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
