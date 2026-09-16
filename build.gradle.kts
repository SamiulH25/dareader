plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
}

// Root plugin versions only; library pins live in gradle/libs.versions.toml.
// Modules: :app is the Compose Multiplatform desktop shell
// (kotlin("multiplatform") + org.jetbrains.compose, mainClass dareader.MainKt);
// :extension-runtime is the JVM-only vendored Keiyoushi extension loader
// (kotlin("jvm"): dex2jar/apk-parser/OkHttp/Rhino) consumed by :app.
