plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// The About screen shows the version it was built with rather than a constant
// kept in Java, which drifts from the artifact the moment someone forgets it.
val generateBuildInfo by tasks.registering {
    val outputDirectory = layout.buildDirectory.dir("generated/build-info")
    val appVersion = project.version.toString()
    inputs.property("version", appVersion)
    outputs.dir(outputDirectory)
    doLast {
        val file = outputDirectory.get().file("build-info.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$appVersion\n")
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo)
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls")
}

application {
    // Use a non-Application launcher so the JavaFX runtime check accepts the
    // classpath layout produced by the Application plugin.
    mainClass = "com.episort.Launcher"
}

tasks.test {
    useJUnitPlatform()
    // Deletions go straight through instead of to the recycle bin: a test run
    // must not depend on a desktop session, nor fill the developer's bin with
    // temporary fixtures.
    systemProperty("episort.delete.recycleBin", "false")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
