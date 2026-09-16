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
    }
}
compose.desktop {
    application {
        mainClass = "dareader.MainKt"
        javaHome = System.getenv("JAVA_HOME") ?: "/usr/lib/jvm/java-21-openjdk"
        nativeDistributions {
            modules("java.base", "java.desktop", "java.logging", "jdk.crypto.ec", "jdk.zipfs")
        }
    }
}
