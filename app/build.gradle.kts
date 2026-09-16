plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose") version "1.9.3"
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.components.resources)
                implementation(compose.material3)
                implementation(project(":extension-runtime"))
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter:5.10.3")
                runtimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
            }
        }
    }
}

tasks.named<Test>("desktopTest") {
    useJUnitPlatform()
    // Isolate java.util.prefs (the SharedPreferences stub backing) from the
    // real user tree so preference-touching tests are deterministic.
    systemProperty(
        "java.util.prefs.userRoot",
        layout.buildDirectory.dir("test-prefs").get().asFile.absolutePath,
    )
}
compose.desktop {
    application {
        mainClass = "dareader.MainKt"
        // Prefer the invoking JVM's home (Gradle runs on the pinned JDK 21).
        javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
        nativeDistributions {
            modules("java.base", "java.desktop", "java.logging", "jdk.crypto.ec", "jdk.zipfs")
        }
    }
}
