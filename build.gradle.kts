import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import java.security.MessageDigest
import java.util.Locale

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

// jpackage must run on the target operating system: its app-image launcher is
// native. The GitHub Actions matrix below invokes these same tasks on Windows
// and Linux, while developers can build the image for their current platform.
val hostOs = System.getProperty("os.name").lowercase(Locale.ROOT)
val hostArchitecture = System.getProperty("os.arch").lowercase(Locale.ROOT)
val portableOs = when {
    hostOs.contains("win") -> "windows"
    hostOs.contains("linux") -> "linux"
    else -> "unsupported"
}
val portableArchitecture = when (hostArchitecture) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    else -> hostArchitecture.replace(Regex("[^a-z0-9._-]"), "-")
}
val portableBaseName = "Episort-${project.version}-$portableOs-$portableArchitecture"
val portableRoot = layout.buildDirectory.dir("portable")
val portableImageRoot = portableRoot.map { it.dir("image") }
val portableImage = portableImageRoot.map { it.dir("Episort") }
val portableDistributions = portableRoot.map { it.dir("distributions") }
val portableWindowsArchive = portableDistributions.map { it.file("$portableBaseName.zip") }
val portableLinuxArchive = portableDistributions.map { it.file("$portableBaseName.tar.gz") }
val portableReadme = layout.projectDirectory.file("docs/portable-readme.txt")
val packagingLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}

val portableApp by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds a self-contained native app image for the current Windows or Linux host."
    dependsOn(tasks.installDist)
    inputs.file(portableReadme)
    outputs.dir(portableImage)

    doFirst {
        if (portableOs == "unsupported") {
            throw GradleException("Portable bundles are supported on Windows and Linux hosts only.")
        }

        val imageRoot = portableImageRoot.get().asFile
        delete(imageRoot)
        imageRoot.mkdirs()

        val jpackageExecutable = packagingLauncher.get().metadata.installationPath
            .file("bin/${if (portableOs == "windows") "jpackage.exe" else "jpackage"}")
            .asFile
        if (!jpackageExecutable.isFile) {
            throw GradleException("jpackage was not found in the Java 21 toolchain: $jpackageExecutable")
        }

        val applicationJar = tasks.named<Jar>("jar").get().archiveFileName.get()
        val applicationLib = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
        val icon = layout.projectDirectory.file(
            if (portableOs == "windows") {
                "src/main/resources/assets/episort-logo.ico"
            } else {
                "src/main/resources/assets/episort-logo.png"
            }
        ).asFile

        commandLine(
            jpackageExecutable.absolutePath,
            "--type", "app-image",
            "--name", "Episort",
            "--app-version", project.version.toString(),
            "--vendor", "Episort",
            "--description", "Safely organize TV series and movies with a reviewed file-operation plan.",
            "--input", applicationLib.absolutePath,
            "--main-jar", applicationJar,
            "--main-class", application.mainClass.get(),
            "--icon", icon.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--dest", imageRoot.absolutePath
        )
    }

    doLast {
        copy {
            from(portableReadme)
            into(portableImage)
            rename { "README.txt" }
        }
    }
}

val verifyPortableApp by tasks.registering {
    group = "verification"
    description = "Verifies the native launcher and bundled Java runtime in the portable app image."
    dependsOn(portableApp)

    doLast {
        val image = portableImage.get().asFile
        val launcher = if (portableOs == "windows") {
            image.resolve("Episort.exe")
        } else {
            image.resolve("bin/Episort")
        }
        val runtimeMarker = if (portableOs == "windows") {
            image.resolve("runtime/release")
        } else {
            image.resolve("lib/runtime/release")
        }

        if (!launcher.isFile) {
            throw GradleException("Portable launcher is missing: $launcher")
        }
        if (!runtimeMarker.isFile) {
            throw GradleException("Bundled Java runtime is missing: $runtimeMarker")
        }
        if (portableOs == "linux" && !launcher.canExecute()) {
            throw GradleException("Linux launcher is not executable: $launcher")
        }
    }
}

val portableZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Archives the Windows portable app image."
    dependsOn(verifyPortableApp)
    onlyIf { portableOs == "windows" }
    archiveFileName.set(portableWindowsArchive.map { it.asFile.name })
    destinationDirectory.set(portableDistributions)
    from(portableImage) {
        into("Episort")
    }
}

val portableTar by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Archives the Linux portable app image while preserving executable permissions."
    dependsOn(verifyPortableApp)
    onlyIf { portableOs == "linux" }
    outputs.file(portableLinuxArchive)

    doFirst {
        val archive = portableLinuxArchive.get().asFile
        archive.parentFile.mkdirs()
        delete(archive)
        commandLine(
            "tar",
            "-C", portableImageRoot.get().asFile.absolutePath,
            "-czf", archive.absolutePath,
            "Episort"
        )
    }
}

val portablePackagingTask = if (portableOs == "windows") portableZip else portableTar
val portableArchiveFile = if (portableOs == "windows") portableWindowsArchive else portableLinuxArchive
val portableChecksum by tasks.registering {
    group = "distribution"
    description = "Writes the SHA-256 checksum for the current portable archive."
    dependsOn(portablePackagingTask)
    val checksumFile = portableArchiveFile.map { archive ->
        archive.asFile.resolveSibling(archive.asFile.name + ".sha256")
    }
    inputs.file(portableArchiveFile)
    outputs.file(checksumFile)

    doLast {
        val archive = portableArchiveFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                if (count > 0) {
                    digest.update(buffer, 0, count)
                }
                count = input.read(buffer)
            }
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        checksumFile.get().writeText("$hash  ${archive.name}\n")
    }
}

tasks.register("portableArchive") {
    group = "distribution"
    description = "Builds and archives the portable app for the current Windows or Linux host."
    dependsOn(portableChecksum)
}
