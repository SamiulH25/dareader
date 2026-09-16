plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // tachiyomi source interfaces are vendored (see dareader.ext) rather than
    // taken from the tachiyomix stub artifact, mirroring Suwayomi-Server.
    api(libs.okhttp.core)
    api(libs.okhttp.brotli)
    api(libs.okhttp.zstd)
    api(libs.jsoup)
    api(libs.coroutines.core)
    api(libs.serialization.json)
    api(libs.serialization.protobuf)
    implementation(libs.dex.translator)
    implementation(libs.dex.tools)
    implementation(libs.asm)
    implementation(libs.apksig)
    implementation(libs.apk.parser)
    implementation(libs.rxjava)
    implementation(libs.kotlin.logging)
    implementation(libs.logging.interceptor)
    implementation(libs.okio)
    implementation(libs.asm.tree)
    implementation(libs.serialization.json.okio)
    implementation(libs.natsort)
    implementation(libs.slf4j.api)
    implementation(libs.rhino)
    runtimeOnly(libs.logback)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

tasks.test {
    useJUnitPlatform()
}
