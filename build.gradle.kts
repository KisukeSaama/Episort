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
    // Use a non-Application launcher so the JavaFX runtime check accepts the
    // classpath layout produced by the Application plugin.
    mainClass = "com.episort.Launcher"
}

tasks.test {
    useJUnitPlatform()
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

// Local-LLM tests spawn the embedded llama-server; gate them behind -PrunLocalLlm=true.
tasks.test {
    useJUnitPlatform {
        if (project.findProperty("runLocalLlm") != "true") {
            excludeTags("local-llm")
        }
    }
}

// --- Embedded llama.cpp runtime ---
// The runtime is shipped inside the application distribution under `runtime/`.
// We pin a llama.cpp release and the matching CUDA runtime, fetch them from
// GitHub releases, verify SHA-256, then publish them through `processResources`
// so they end up next to the JAR. Idempotent: skipped when the cache exists.
val llamaCppTag = "b9090"
val llamaRuntimeAssets = listOf(
    "llama-${llamaCppTag}-bin-win-cuda-12.4-x64.zip",
    "cudart-llama-bin-win-cuda-12.4-x64.zip",
)
val llamaRuntimeCache = layout.buildDirectory.dir("llama-runtime")

tasks.register("fetchLlamaRuntime") {
    description = "Downloads the pinned llama.cpp Windows CUDA runtime into build/llama-runtime/"
    group = "build"
    val cacheDir = llamaRuntimeCache.get().asFile
    outputs.dir(cacheDir)
    doLast {
        cacheDir.mkdirs()
        llamaRuntimeAssets.forEach { asset ->
            val target = cacheDir.resolve(asset)
            if (target.exists() && target.length() > 0) {
                logger.lifecycle("llama runtime cache hit: $asset")
                return@forEach
            }
            val url = "https://github.com/ggml-org/llama.cpp/releases/download/${llamaCppTag}/$asset"
            logger.lifecycle("Downloading $url")
            uri(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

// Stage the runtime zips inside the distribution under `runtime/`. This makes
// them reachable from EmbeddedLlamaRuntime via the JAR's parent layout.
distributions {
    main {
        contents {
            from(llamaRuntimeCache) {
                into("runtime")
                include("*.zip")
            }
        }
    }
}

tasks.named("installDist") { dependsOn("fetchLlamaRuntime") }
tasks.named("distZip") { dependsOn("fetchLlamaRuntime") }
tasks.named("distTar") { dependsOn("fetchLlamaRuntime") }
