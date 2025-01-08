plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

group = "org.logicboost"
version = "0.1.2.1"

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

val ktorVersion = "2.3.13"
val serializationVersion = "1.7.3"

dependencies {
    implementation("org.json:json:20240303")
    // Using BOM for kotlinx.serialization
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:$serializationVersion"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")

    // Markdown processing
    implementation("org.jetbrains:markdown:0.7.3")

    implementation("com.openai:openai-java:0.9.1")

    // IntelliJ's SLF4J implementation
    compileOnly("org.slf4j:slf4j-api:1.7.36")

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

    implementation("org.jetbrains.intellij.deps:log4j:1.2.17.3")
    implementation("org.jetbrains:annotations:26.0.1")

    // Testing dependencies
    //    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    //    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    //    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")

    // Test implementations
    testImplementation("junit:junit:4.13.2")  // JUnit 4 for light tests
    testImplementation("io.mockk:mockk:1.13.14")  // Mocking framework

}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                when (requested.name) {
                    "kotlin-stdlib", "kotlin-stdlib-common", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8", "kotlin-reflect" -> {
                        useVersion("2.0.21")
                        because("Ensure consistent Kotlin version across all dependencies")
                    }
                }
            }
            if (requested.group == "org.jetbrains.kotlinx") {
                when {
                    requested.name.startsWith("kotlinx-coroutines") -> {
                        useVersion("1.9.0")
                        because("Use compatible coroutines version")
                    }

                    requested.name.startsWith("kotlinx-serialization") -> {
                        useVersion("1.7.3")
                        because("Use compatible serialization version")
                    }
                }
            }
        }

        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.0.21")
        force("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set("2024.1.7")
    type.set("IC")
    plugins.set(
        listOf(
        "com.intellij.java"
        )
    )
    // Support for multiple IDE products
    instrumentCode.set(true)
    // Specify supported IDEs
    updateSinceUntilBuild.set(true)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }


    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("243.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    test {
        useJUnit()

        testLogging {
            events("started", "passed", "skipped", "failed")
            showStandardStreams = true
            showExceptions = true
            showCauses = true
            showStackTraces = true

            // Force test execution even if nothing has changed
            outputs.upToDateWhen { false }
        }

        systemProperty("idea.force.use.core.classloader", "true")
        systemProperty("idea.test.default.test.data.path", "${project.projectDir}/src/test/testData")
    }
}

