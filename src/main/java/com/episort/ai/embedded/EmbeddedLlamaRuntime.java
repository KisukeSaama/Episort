package com.episort.ai.embedded;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages the embedded llama-server child process: extracts the bundled
 * runtime zips from the install dir into a per-user cache on first launch,
 * spawns {@code llama-server.exe} bound to a random localhost port, polls
 * {@code /health} until the model is loaded, and kills the process on JVM
 * shutdown.
 *
 * <p>This is the only part of Episort that knows about llama.cpp; everything
 * else talks to it through {@link LlamaServerClient}. No third-party brand
 * names leak into user-visible UI — the runtime is presented as "Episort
 * local AI".
 */
public class EmbeddedLlamaRuntime implements AutoCloseable {
    public static final String LLAMA_SERVER_EXE = "llama-server.exe";
    public static final String EXTRACTION_MARKER = ".extracted";

    private final Path runtimeZipDir;
    private final Path extractionDir;
    private final Supplier<Path> modelPathSupplier;
    private final AtomicReference<Process> serverProcess = new AtomicReference<>();
    private final AtomicReference<URI> serverUri = new AtomicReference<>();
    private final AtomicReference<Path> loadedModelPath = new AtomicReference<>();
    private final Thread shutdownHook;

    public EmbeddedLlamaRuntime(Path runtimeZipDir, Path extractionDir, Path modelPath) {
        this(runtimeZipDir, extractionDir, () -> modelPath);
    }

    public EmbeddedLlamaRuntime(Path runtimeZipDir, Path extractionDir, Supplier<Path> modelPathSupplier) {
        this.runtimeZipDir = Objects.requireNonNull(runtimeZipDir, "runtimeZipDir");
        this.extractionDir = Objects.requireNonNull(extractionDir, "extractionDir");
        this.modelPathSupplier = Objects.requireNonNull(modelPathSupplier, "modelPathSupplier");
        this.shutdownHook = new Thread(this::stopQuietly, "episort-llama-shutdown");
    }

    private Path currentModelPath() {
        return Objects.requireNonNull(modelPathSupplier.get(), "model path supplier returned null");
    }

    public Optional<Path> activeModelPath() {
        return Optional.ofNullable(loadedModelPath.get());
    }

    /** True when the bundled zips can be located next to the JAR. */
    public boolean runtimeBinariesAvailable() {
        if (!Files.isDirectory(runtimeZipDir)) {
            return false;
        }
        try (var stream = Files.list(runtimeZipDir)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith(".zip"));
        } catch (IOException ex) {
            return false;
        }
    }

    public boolean modelAvailable() {
        Path path = currentModelPath();
        return Files.isRegularFile(path) && path.toFile().length() > 0;
    }

    public Optional<URI> baseUri() {
        return Optional.ofNullable(serverUri.get());
    }

    /**
     * Extracts the runtime zips (idempotent, marker-file based) and spawns
     * llama-server. Blocks until the server reports {@code /health == 200} or
     * the timeout fires. Returns the bound base URI.
     */
    public URI startBlocking(java.time.Duration readyTimeout) throws IOException, InterruptedException {
        if (serverProcess.get() != null) {
            return serverUri.get();
        }
        if (!runtimeBinariesAvailable()) {
            throw new IOException("Episort local AI runtime binaries are missing from " + runtimeZipDir);
        }
        Path modelPath = currentModelPath();
        if (!Files.isRegularFile(modelPath) || modelPath.toFile().length() == 0) {
            throw new IOException("Episort local AI model is missing at " + modelPath);
        }
        ensureExtracted();
        Path serverExe = locateServerExecutable();
        int port = pickFreePort();
        ProcessBuilder builder = new ProcessBuilder(serverArgs(serverExe, port, modelPath));
        builder.redirectErrorStream(true);
        // Capture the server's stdout+stderr to a rolling log file so launch
        // failures are diagnosable. Falls back to DISCARD if the file can't
        // be opened (e.g. read-only profile dir).
        Path logFile = extractionDir.resolve("llama-server.log");
        try {
            Files.createDirectories(extractionDir);
            builder.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
        } catch (IOException ignored) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        Process process = builder.start();
        serverProcess.set(process);
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        URI uri = URI.create("http://127.0.0.1:" + port);
        LlamaServerClient client = new LlamaServerClient(uri);
        long deadline = System.currentTimeMillis() + readyTimeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                int exit = process.exitValue();
                String tail = tailLog(logFile, 6);
                // Clear the dead process so a subsequent startBlocking/restart
                // call doesn't short-circuit on the stale reference.
                serverProcess.compareAndSet(process, null);
                throw new IOException("Local AI runtime exited before becoming ready (exit "
                        + exit + "). Last log lines: " + tail);
            }
            if (client.isHealthy()) {
                serverUri.set(uri);
                loadedModelPath.set(modelPath);
                return uri;
            }
            Thread.sleep(500);
        }
        stopQuietly();
        throw new IOException("Local AI runtime did not become ready within " + readyTimeout);
    }

    /**
     * Stops the running server (if any) and starts a new one using the path
     * currently returned by the model path supplier. Safe to call from any
     * thread; blocks the caller for the duration of the start sequence.
     */
    public URI restart(java.time.Duration readyTimeout) throws IOException, InterruptedException {
        stop();
        return startBlocking(readyTimeout);
    }

    public void stop() {
        Process process = serverProcess.getAndSet(null);
        serverUri.set(null);
        loadedModelPath.set(null);
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down.
        }
    }

    private static String tailLog(Path logFile, int lines) {
        try {
            if (!Files.isRegularFile(logFile)) return "(no log file)";
            List<String> all = Files.readAllLines(logFile, java.nio.charset.StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            return String.join(" | ", all.subList(from, all.size())).trim();
        } catch (IOException ex) {
            return "(unreadable log: " + ex.getMessage() + ")";
        }
    }

    private void stopQuietly() {
        try {
            stop();
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void close() {
        stop();
    }

    private List<String> serverArgs(Path serverExe, int port, Path modelPath) {
        List<String> args = new ArrayList<>();
        args.add(serverExe.toString());
        args.add("-m");
        args.add(modelPath.toString());
        args.add("--host");
        args.add("127.0.0.1");
        args.add("--port");
        args.add(String.valueOf(port));
        // GPU offload: all layers to VRAM. Qwen3 4B Q4_K_M (~2.5 GB) fits
        // comfortably on modest discrete GPUs.
        args.add("-ngl");
        args.add("999");
        // 32K context: Qwen3 4B Instruct 2507 supports 32,768 tokens and this keeps large
        // selected batches in-context without relying on reasoning mode.
        args.add("--ctx-size");
        args.add("32768");
        // Larger logical and physical batch sizes to saturate the GPU during
        // prompt processing (system prompt + filenames are batched).
        args.add("--batch-size");
        args.add("512");
        args.add("--ubatch-size");
        args.add("512");
        // CPU threads for the bits that aren't on the GPU. RTX 4070 Super
        // builds typically pair with 8+ physical cores; cap at 8 to leave
        // room for the JavaFX UI thread.
        args.add("--threads");
        args.add(String.valueOf(Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors() / 2))));
        // Single slot. Tried --parallel 4 to fan out the per-file pass, but on
        // a 4B Q4 model on one GPU, four concurrent decode streams are
        // bandwidth-bound and per-call latency 3-4x'd; aggregate throughput
        // also dropped because each slot keeps its own KV cache so the
        // system-prompt prefix is prefilled once per slot, not once total.
        // Concurrency on one GPU only pays off with long shared context or
        // long decode, neither of which we have. Batching wins here instead.
        args.add("--parallel");
        args.add("1");
        // Flash attention + KV quantization. Halves KV memory at 32K and
        // gives a ~2x prefill speedup on Ampere/Ada GPUs. If a future bundled
        // binary regresses on these flags, fall back to removing them.
        args.add("-fa");
        args.add("on");
        args.add("-ctk");
        args.add("q8_0");
        args.add("-ctv");
        args.add("q8_0");
        // Use the GGUF's embedded Jinja chat template so /v1/chat/completions
        // formats turns exactly the way Qwen3 expects (incl. tool-calls and
        // the optional /no_think switch) instead of relying on hand-rolled
        // ChatML in the client.
        args.add("--jinja");
        // Per-model overrides (currently unused — Qwen3 needs none — but the
        // hook stays wired so a new catalog entry with a broken chat template
        // can ship its workaround without touching this method).
        args.addAll(extraArgsFor(modelPath));
        return args;
    }

    private static List<String> extraArgsFor(Path modelPath) {
        String fileName = modelPath.getFileName() == null ? "" : modelPath.getFileName().toString();
        return com.episort.ai.AiModelCatalog.entries().stream()
                .filter(e -> e.fileName().equalsIgnoreCase(fileName))
                .findFirst()
                .map(com.episort.ai.AiModelEntry::extraServerArgs)
                .orElse(List.of());
    }

    private void ensureExtracted() throws IOException {
        Path marker = extractionDir.resolve(EXTRACTION_MARKER);
        if (Files.isRegularFile(marker)) {
            return;
        }
        Files.createDirectories(extractionDir);
        try (var stream = Files.list(runtimeZipDir)) {
            for (Path zip : stream.filter(p -> p.toString().endsWith(".zip")).toList()) {
                extractZip(zip, extractionDir);
            }
        }
        Files.writeString(marker, "ok");
    }

    private static void extractZip(Path zip, Path target) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path resolved = target.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(target)) {
                    throw new IOException("Refusing to extract entry outside target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(in, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path locateServerExecutable() throws IOException {
        try (var stream = Files.walk(extractionDir)) {
            return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(LLAMA_SERVER_EXE))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Local AI server executable not found under " + extractionDir));
        }
    }

    private static int pickFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Resolves the runtime zip directory relative to the running JAR. */
    public static Path defaultRuntimeZipDir() {
        Path jar = locateInstallRoot();
        Path primary = jar.resolve("runtime");
        if (hasZip(primary)) {
            return primary;
        }
        // Dev fallback: when running via `gradle run`, the install layout
        // doesn't exist; the fetched zips live under `build/llama-runtime/`.
        Path devCache = Path.of(System.getProperty("user.dir", "."), "build", "llama-runtime");
        if (hasZip(devCache)) {
            return devCache;
        }
        return primary;
    }

    private static boolean hasZip(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith(".zip"));
        } catch (IOException ex) {
            return false;
        }
    }

    /** Resolves the per-user extraction directory. */
    public static Path defaultExtractionDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = (localAppData != null && !localAppData.isBlank())
                ? Path.of(localAppData, "Episort", "runtime")
                : Path.of(System.getProperty("user.home", "."), ".episort", "runtime");
        return base;
    }

    private static Path locateInstallRoot() {
        try {
            Path codeSource = Path.of(EmbeddedLlamaRuntime.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            // Application plugin layout: <install>/lib/<jar>. Climb to <install>.
            Path parent = codeSource.getParent();
            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equals("lib")) {
                Path root = parent.getParent();
                if (root != null) {
                    return root;
                }
            }
            return parent != null ? parent : Path.of(".");
        } catch (Exception ex) {
            return Path.of(".");
        }
    }
}
