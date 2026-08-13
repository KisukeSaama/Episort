import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

version = "0.2.0"

// Java 25 and JavaFX 25 are the current long-term-support pair. JavaFX carries
// the reason for the jump: CSS transitions arrived in 23 and interpolation of
// backgrounds, borders and insets in 24, which is what lets the interface state
// changes animate from the stylesheet instead of not at all.
val javaLanguageVersion = 25
val javafxVersion = "25.0.4"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaLanguageVersion)
    }
}

// The About screen shows the version it was built with rather than a constant
// kept in Java, which drifts from the artifact the moment someone forgets it.
val generateBuildInfo = tasks.register("generateBuildInfo") {
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
    version = javafxVersion
    modules = listOf("javafx.controls")
}

application {
    // Use a non-Application launcher so the JavaFX runtime check accepts the
    // classpath layout produced by the Application plugin.
    mainClass = "com.episort.Launcher"
    // JavaFX and JNA both load native libraries. Since Java 24 that is a
    // restricted operation which warns now and is refused later, so the grant
    // is explicit. Everything ships on the classpath here, hence ALL-UNNAMED.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// `./gradlew run -Depisort.debug.fps=true` has to reach the application, not the
// Gradle daemon, or the diagnostics the app exposes are unreachable from the
// command line. `javafx.` goes through for the same reason: the pulse rate
// picked by RenderTuning has to be overridable while measuring it.
tasks.named<JavaExec>("run") {
    systemProperties(providers.systemPropertiesPrefixedBy("episort.").get())
    systemProperties(providers.systemPropertiesPrefixedBy("javafx.").get())
    // Both grants are spelled out because setting jvmArgs here replaces the
    // list the Application plugin would have carried over from
    // applicationDefaultJvmArgs. Unlike the packaged application, the JavaFX
    // plugin runs the app with JavaFX on the module path, where the grant has
    // to name the module rather than the unnamed one.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--enable-native-access=javafx.graphics")
}

tasks.test {
    useJUnitPlatform()
    // CI runners are headless. JavaFX tests skip when no toolkit is available;
    // this property keeps a misbehaving toolkit or leaked test thread from
    // holding a release job indefinitely.
    systemProperty("java.awt.headless", "true")
    timeout.set(Duration.ofMinutes(2))
    // Same native-access grant as the application: tests touch JNA too.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
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
    implementation("net.java.dev.jna:jna-platform:5.18.1")
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
val portablePayloads = portableRoot.map { it.dir("payloads") }
val portableWindowsArchive = portablePayloads.map { it.file("$portableBaseName.zip") }
val portableLinuxArchive = portablePayloads.map { it.file("$portableBaseName.tar.gz") }
val portableLinuxPackages = portableRoot.map { it.dir("distributions") }
val portableLinuxPackageWork = portableRoot.map { it.dir("package-work") }
val singleFilePortable = portableDistributions.map {
    it.file("$portableBaseName${if (portableOs == "windows") ".exe" else ""}")
}
val portableReadme = layout.projectDirectory.file("docs/portable-readme.txt")
val portableLauncherSource = layout.projectDirectory.dir("tools/portable-launcher")
val portableLauncherSources = fileTree(portableLauncherSource) {
    include("*.go", "go.mod", "payload.bin")
}
val portableWindowsIcon = layout.projectDirectory.file("src/main/resources/assets/episort-logo.ico")
val portableLauncherWork = portableRoot.map { it.dir("launcher-work") }
val portableSmokeData = portableRoot.map { it.dir("smoke-data") }
val goExecutable = providers.gradleProperty("goExecutable").orElse("go")
val packagingLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(javaLanguageVersion)
}

// Running another process or touching files from inside a task action goes
// through these services since Gradle 9; `project.exec`, `project.copy` and
// `project.delete` are no longer reachable at execution time.
val execOperations = serviceOf<ExecOperations>()
val fileOperations = serviceOf<FileSystemOperations>()

// Everything the packaging tasks need to know about the project is read here,
// while the build is being configured. Reaching for `project` from inside a task
// action is deprecated in Gradle 9 and fails in Gradle 10.
val applicationVersion = project.version.toString()
val applicationMainClass = application.mainClass
val applicationJarName = tasks.named<Jar>("jar").flatMap { it.archiveFileName }
val applicationLibDirectory = layout.buildDirectory.dir("install/${project.name}/lib")
val portableIcon = layout.projectDirectory.file(
    if (portableOs == "windows") {
        "src/main/resources/assets/episort-logo.ico"
    } else {
        "src/main/resources/assets/episort-logo.png"
    }
)

val portableApp = tasks.register<Exec>("portableApp") {
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
        fileOperations.delete { delete(imageRoot) }
        imageRoot.mkdirs()

        val jpackageExecutable = packagingLauncher.get().metadata.installationPath
            .file("bin/${if (portableOs == "windows") "jpackage.exe" else "jpackage"}")
            .asFile
        if (!jpackageExecutable.isFile) {
            throw GradleException("jpackage was not found in the Java 25 toolchain: $jpackageExecutable")
        }

        commandLine(
            jpackageExecutable.absolutePath,
            "--type", "app-image",
            "--name", "Episort",
            "--app-version", applicationVersion,
            "--vendor", "Episort",
            "--description", "Safely organize TV series and movies with a reviewed file-operation plan.",
            "--input", applicationLibDirectory.get().asFile.absolutePath,
            "--main-jar", applicationJarName.get(),
            "--main-class", applicationMainClass.get(),
            "--icon", portableIcon.asFile.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--dest", imageRoot.absolutePath
        )
    }

    doLast {
        fileOperations.copy {
            from(portableReadme)
            into(portableImage)
            rename { "README.txt" }
        }
    }
}

fun registerLinuxPackage(packageType: String) = tasks.register<Exec>("portable${packageType.replaceFirstChar { it.uppercase() }}") {
    group = "distribution"
    description = "Builds the native Linux .$packageType package."
    dependsOn(tasks.installDist)
    onlyIf { portableOs == "linux" }
    inputs.dir(applicationLibDirectory)
    outputs.dir(portableLinuxPackageWork.map { it.dir(packageType) })

    doFirst {
        val packageDirectory = portableLinuxPackageWork.get().asFile.resolve(packageType)
        fileOperations.delete { delete(packageDirectory) }
        packageDirectory.mkdirs()

        val jpackageExecutable = packagingLauncher.get().metadata.installationPath
            .file("bin/jpackage")
            .asFile
        if (!jpackageExecutable.isFile) {
            throw GradleException("jpackage was not found in the Java 25 toolchain: $jpackageExecutable")
        }

        val packageOptions = when (packageType) {
            "deb" -> listOf("--linux-deb-maintainer", "maintainer@episort.app")
            "rpm" -> listOf("--linux-rpm-license-type", "GPL-3.0-or-later")
            else -> error("Unsupported Linux package type: $packageType")
        }
        commandLine(
            jpackageExecutable.absolutePath,
            "--type", packageType,
            "--name", "Episort",
            "--app-version", applicationVersion,
            "--vendor", "Episort",
            "--description", "Safely organize TV series and movies with a reviewed file-operation plan.",
            "--input", applicationLibDirectory.get().asFile.absolutePath,
            "--main-jar", applicationJarName.get(),
            "--main-class", applicationMainClass.get(),
            "--icon", portableIcon.asFile.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--linux-package-name", "episort",
            "--linux-app-release", "1",
            "--linux-menu-group", "Utility",
            "--linux-shortcut",
            *packageOptions.toTypedArray(),
            "--dest", packageDirectory.absolutePath
        )
    }
}

val portableDeb = registerLinuxPackage("deb")
val portableRpm = registerLinuxPackage("rpm")

tasks.register("linuxPackages") {
    group = "distribution"
    description = "Builds the native Linux .deb and .rpm packages."
    dependsOn(portableDeb, portableRpm)
    onlyIf { portableOs == "linux" }

    doLast {
        val packageDirectory = portableLinuxPackages.get().asFile
        fileOperations.delete { delete(packageDirectory) }
        packageDirectory.mkdirs()
        fileOperations.copy {
            from(portableLinuxPackageWork.map { it.dir("deb") })
            into(packageDirectory)
            include("*.deb")
        }
        fileOperations.copy {
            from(portableLinuxPackageWork.map { it.dir("rpm") })
            into(packageDirectory)
            include("*.rpm")
        }
    }
}

val verifyPortableApp = tasks.register("verifyPortableApp") {
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

val portableZip = tasks.register<Zip>("portableZip") {
    group = "distribution"
    description = "Archives the Windows portable app image."
    dependsOn(verifyPortableApp)
    onlyIf { portableOs == "windows" }
    archiveFileName.set(portableWindowsArchive.map { it.asFile.name })
    destinationDirectory.set(portablePayloads)
    from(portableImage) {
        into("Episort")
    }
}

val portableTar = tasks.register<Exec>("portableTar") {
    group = "distribution"
    description = "Archives the Linux portable app image while preserving executable permissions."
    dependsOn(verifyPortableApp)
    onlyIf { portableOs == "linux" }
    outputs.file(portableLinuxArchive)

    doFirst {
        val archive = portableLinuxArchive.get().asFile
        archive.parentFile.mkdirs()
        fileOperations.delete { delete(archive) }
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
val testPortableLauncher = tasks.register<Exec>("testPortableLauncher") {
    group = "verification"
    description = "Runs the native single-file launcher tests."
    inputs.files(portableLauncherSources)
    workingDir(portableLauncherSource)
    commandLine(goExecutable.get(), "test", ".")
}

val buildSingleFilePortable = tasks.register<Exec>("buildSingleFilePortable") {
    group = "distribution"
    description = "Embeds the complete application image in one native executable."
    dependsOn(portablePackagingTask, testPortableLauncher)
    inputs.files(portableLauncherSources)
    inputs.file(portableArchiveFile)
    if (portableOs == "windows") {
        inputs.file(portableWindowsIcon)
    }
    outputs.file(singleFilePortable)

    doFirst {
        if (portableOs == "unsupported") {
            throw GradleException("Single-file bundles are supported on Windows and Linux hosts only.")
        }
        val archive = portableArchiveFile.get().asFile
        val workDirectory = portableLauncherWork.get().asFile
        val output = singleFilePortable.get().asFile
        fileOperations.delete { delete(workDirectory) }
        workDirectory.mkdirs()
        output.parentFile.mkdirs()
        fileOperations.delete { delete(output) }
        fileOperations.copy {
            from(portableLauncherSource)
            include("*.go", "go.mod", "payload.bin")
            into(workDirectory)
        }
        fileOperations.copy {
            from(archive)
            into(workDirectory)
            rename { "payload.bin" }
        }
        if (portableOs == "windows") {
            // jpackage embeds the icon in the launcher inside the payload. The
            // outer, single-file Go launcher is a separate executable and needs
            // its own Windows resources or Explorer displays the generic icon.
            execOperations.exec {
                workingDir(workDirectory)
                commandLine(
                    goExecutable.get(),
                    "run", "github.com/tc-hib/go-winres@v0.3.3",
                    "simply",
                    "--arch", "${if (portableArchitecture == "x64") "amd64" else "arm64"}",
                    "--out", "episort-resource",
                    "--manifest", "gui",
                    "--icon", portableWindowsIcon.asFile.absolutePath,
                    "--file-version", applicationVersion,
                    "--product-version", applicationVersion,
                    "--file-description", "Episort portable launcher",
                    "--product-name", "Episort",
                    "--original-filename", output.name
                )
            }
        }
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
        val linkerFlags = buildList {
            add("-s")
            add("-w")
            if (portableOs == "windows") add("-H=windowsgui")
            add("-X")
            add("main.version=$applicationVersion")
            add("-X")
            add("main.payloadFormat=${if (portableOs == "windows") "zip" else "tar.gz"}")
            add("-X")
            add("main.payloadDigest=$hash")
        }.joinToString(" ")
        workingDir(workDirectory)
        environment("CGO_ENABLED", "0")
        commandLine(
            goExecutable.get(),
            "build",
            "-trimpath",
            "-ldflags", linkerFlags,
            "-o", output.absolutePath,
            "."
        )
    }
}

val verifySingleFilePortable = tasks.register<Exec>("verifySingleFilePortable") {
    group = "verification"
    description = "Verifies that the one-file launcher safely extracts into user application data."
    dependsOn(buildSingleFilePortable)
    inputs.file(singleFilePortable)
    outputs.dir(portableSmokeData)

    doFirst {
        val executable = singleFilePortable.get().asFile
        val smokeData = portableSmokeData.get().asFile
        if (!executable.isFile || executable.length() <= portableArchiveFile.get().asFile.length()) {
            throw GradleException("Single-file portable executable is missing or incomplete: $executable")
        }
        val signature = executable.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
        val signatureIsValid = if (portableOs == "windows") {
            signature[0] == 'M'.code.toByte() && signature[1] == 'Z'.code.toByte()
        } else {
            signature.contentEquals(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        }
        if (!signatureIsValid) {
            throw GradleException("Single-file portable executable has an invalid native signature")
        }
        fileOperations.delete { delete(smokeData) }
        smokeData.mkdirs()
        if (portableOs == "windows") {
            environment("LOCALAPPDATA", smokeData.absolutePath)
        } else {
            environment("XDG_DATA_HOME", smokeData.absolutePath)
        }
        commandLine(executable.absolutePath, "--episort-portable-extract-only")
    }

    doLast {
        val expectedLauncher = if (portableOs == "windows") "Episort.exe" else "Episort"
        val extractedLaunchers = portableSmokeData.get().asFile.walkTopDown()
            .filter { it.isFile && it.name == expectedLauncher }
            .toList()
        if (extractedLaunchers.none()) {
            throw GradleException("Single-file launcher did not extract Episort into application data")
        }
    }
}

val singleFileChecksum = tasks.register("singleFileChecksum") {
    group = "distribution"
    description = "Writes the SHA-256 checksum for the single-file portable executable."
    dependsOn(verifySingleFilePortable)
    val checksumFile = singleFilePortable.map { executable ->
        executable.asFile.resolveSibling(executable.asFile.name + ".sha256")
    }
    inputs.file(singleFilePortable)
    outputs.file(checksumFile)

    doLast {
        val executable = singleFilePortable.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        executable.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        checksumFile.get().writeText("$hash  ${executable.name}\n")
    }
}

tasks.register("portableArchive") {
    group = "distribution"
    description = "Builds the Windows portable executable or native Linux packages for the current host."
    dependsOn(if (portableOs == "linux") tasks.named("linuxPackages") else singleFileChecksum)
}
