plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.5"
    modules = listOf("javafx.controls")
}

application {
    mainClass = "com.episort.EpisortApplication"
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.register("prepareLocalTvdbCredentials") {
    val generated = layout.projectDirectory.file("src/main/java/com/episort/config/BuildTvdbCredentials.java")
    val example = layout.projectDirectory.file("src/main/java/com/episort/config/BuildTvdbCredentials.java.example")
    outputs.file(generated)
    doLast {
        if (!generated.asFile.exists()) {
            generated.asFile.writeText(example.asFile.readText())
        }
    }
}

tasks.named("compileJava") {
    dependsOn("prepareLocalTvdbCredentials")
}

dependencies {
    implementation("net.java.dev.jna:jna:5.18.1")
    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
