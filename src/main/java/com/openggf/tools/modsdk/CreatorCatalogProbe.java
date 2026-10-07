package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.StockMusicDomains;
import com.openggf.io.ModInputLimits;
import com.openggf.mods.*;
import com.openggf.mods.code.*;
import com.openggf.version.AppVersion;
import javax.tools.ToolProvider;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Opt-in catalog/owned-asset scalability probe through the production pipeline.
 * Inputs: new absolute work directory, comma-separated counts and bytes per asset.
 * Creates original fixtures only; never reads ROMs or installed settings. Reports
 * observed elapsed/heap values, not latency guarantees. Origin: readiness ID18,
 * 2026-10-07. Large shapes run explicitly, never as hidden ordinary-suite stress.
 */
public final class CreatorCatalogProbe {
    private CreatorCatalogProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: CreatorCatalogProbe <new-absolute-work-dir> <counts,comma,separated> <asset-bytes>");
        Path work = Path.of(args[0]);
        if (!work.isAbsolute() || Files.exists(work)) throw new IllegalArgumentException("Probe work directory must be new and absolute");
        long bytes = Long.parseLong(args[2]);
        List<Integer> counts = Arrays.stream(args[1].split(",")).map(Integer::parseInt).toList();
        Files.createDirectories(work.getParent());
        Files.createDirectory(work);
        try {
            Path compiled = compileFixture(work.resolve("source"), bytes);
            List<Map<String, Object>> cases = new ArrayList<>();
            for (int count : counts) cases.add(runCase(work.resolve("catalog-" + count), compiled, count, bytes));
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("formatVersion", 1);
            report.put("build", AppVersion.identity().toString());
            report.put("java", System.getProperty("java.runtime.version"));
            report.put("javaVendor", System.getProperty("java.vendor"));
            report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            report.put("processors", Runtime.getRuntime().availableProcessors());
            report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
            report.put("heapPoolPeakBytes", heapPeak());
            report.put("peakResidentBytes", peakResidentBytes());
            report.put("cases", cases);
            report.put("rejections", rejectionCases(work.resolve("malicious")));
            report.put("acceptance", "All effective owners register once without rejection/fault; only the existing 128-window cap blocks excess enabled owners; diagnostics/order repeat identically; production repository/asset limits reject excess before activation. Timings/heap are observations for this JVM, not product guarantees.");
            System.out.println(new ObjectMapper().writeValueAsString(report));
        } finally { deleteTree(work); }
    }

    static Path compileFixture(Path root, long assetBytes) throws Exception {
        if (assetBytes < 1 || assetBytes > ModInputLimits.production().maxAssetBytes()) throw new IllegalArgumentException("Asset bytes must be within production maxAssetBytes");
        Files.createDirectories(root);
        Path source = root.resolve("ProbeMod.java");
        Files.writeString(source, """
                package creatorprobe;
                import com.openggf.mods.code.*;
                import com.openggf.mods.scene.*;
                public final class ProbeMod implements GgfMod {
                  public void register(ModContext context) {
                    try {
                      byte[] payload = context.modAssets().readBounded("asset/payload.dat", context.modAssets().limits().maxAssetBytes());
                      if (payload.length != %dL) throw new IllegalStateException("Payload length changed");
                    } catch (java.io.IOException error) { throw new IllegalStateException(error); }
                    context.registerStartupScene(ProbeScene::new);
                  }
                  public static final class ProbeScene implements ModScene {
                    public void enter(SceneContext context) { }
                    public void update(SceneContext context) { }
                    public void draw(SceneContext context, SceneCanvas canvas) { canvas.clear(0xFF204060); }
                  }
                }
                """.formatted(assetBytes));
        Path classes = root.resolve("classes"); Files.createDirectories(classes);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || compiler.run(null, null, null, "--release", "21", "-cp", System.getProperty("java.class.path"), "-d", classes.toString(), source.toString()) != 0)
            throw new IllegalStateException("Probe fixture compilation requires the matching Java 21 JDK/engine classpath");
        return classes;
    }

    static Map<String, Object> runCase(Path root, Path compiled, int count, long assetBytes) throws Exception {
        if (count < 1 || count > ModInputLimits.production().maxModJars()) throw new IllegalArgumentException("Catalog count exceeds production budget");
        Files.createDirectories(root);
        for (int index = 0; index < count; index++) writeFixture(root.resolve(String.format("%04d.jar", index)), "probe-" + index, compiled, assetBytes);
        long started = System.nanoTime();
        var scanner = new DefaultModRepositoryScanner();
        List<ModCatalogEntry> scanned = scanner.scan(root.toAbsolutePath().normalize());
        long scannedAt = System.nanoTime();
        var validated = new ModCatalogValidator(root.toAbsolutePath().normalize(), ModInputLimits.production(), StockMusicDomains::containsSupported).validate(scanned).entries();
        List<ModState.Entry> state = new ArrayList<>();
        Set<String> trusted = new LinkedHashSet<>();
        for (ModCatalogEntry entry : validated) {
            if (!(entry instanceof ModDescriptor descriptor) || descriptor.hasErrors()) throw new IllegalStateException("Admitted fixture rejected: " + entry.findings());
            state.add(new ModState.Entry(descriptor.manifest().id(), true, state.size(), true, descriptor.sha256()));
            trusted.add(descriptor.manifest().id());
        }
        var startup = new ModState(1, state);
        var catalog = new EffectiveCatalogBuilder().build(validated, startup);
        // Existing production allocation is 128 windows, default one per enabled owner.
        // Repository discovery's 1,024-jar budget does not promise 1,024 active owners.
        int expectedEffective = Math.min(count, 128);
        var blockedReasons = new TreeMap<String, Integer>();
        for (var eligibility : catalog.eligibility().values()) {
            if (eligibility.status() == ModEligibility.Status.EFFECTIVE) continue;
            if (eligibility.status() != ModEligibility.Status.BLOCKED || eligibility.reasons().size() != 1
                    || !eligibility.reasons().getFirst().code().equals("PATTERN_WINDOW_BUDGET_EXCEEDED"))
                throw new IllegalStateException("Unexpected catalog eligibility: " + eligibility);
            blockedReasons.merge(eligibility.reasons().getFirst().code(), 1, Integer::sum);
        }
        if (catalog.effective().orderedEnabled().size() != expectedEffective
                || blockedReasons.values().stream().mapToInt(Integer::intValue).sum() != count - expectedEffective)
            throw new IllegalStateException("Production pattern-window boundary changed: " + blockedReasons);
        long validatedAt = System.nanoTime();
        int registrations;
        try (ModRuntime runtime = new ModClassLoaderFactory(CreatorCatalogProbe.class.getClassLoader()).create(catalog.effective(), trusted)) {
            runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(), owners -> new ModStateSaveResult.Saved(), owners -> { }));
            runtime.installStorageRoot(root.resolve("saves"));
            registrations = runtime.newRegistrationPlan().registrations().size();
            if (registrations != expectedEffective || !runtime.registrationFailures().isEmpty() || !runtime.rejectedOwners().isEmpty())
                throw new IllegalStateException("Registration/loader failure (expected " + expectedEffective + ", actual " + registrations + "): " + runtime.registrationFailures() + " / " + runtime.rejectedOwners());
        }
        long completed = System.nanoTime();
        if (!diagnostics(scanned).equals(diagnostics(scanner.scan(root.toAbsolutePath().normalize())))) throw new IllegalStateException("Unstable scanner diagnostics");
        var repeatedCatalog = new EffectiveCatalogBuilder().build(validated, startup);
        if (!catalog.eligibility().equals(repeatedCatalog.eligibility())
                || !catalog.effective().orderedEnabled().equals(repeatedCatalog.effective().orderedEnabled()))
            throw new IllegalStateException("Unstable effective catalog/order/eligibility");
        return Map.of("catalogOwners", count, "effectiveOwners", expectedEffective,
                "blockedOwnerReasons", blockedReasons, "assetBytesPerOwner", assetBytes, "registrations", registrations,
                "scanMillis", (scannedAt - started) / 1_000_000.0, "validateMillis", (validatedAt - scannedAt) / 1_000_000.0,
                "loadRegisterCloseMillis", (completed - validatedAt) / 1_000_000.0,
                "heapPoolPeakBytes", heapPeak());
    }

    static Map<String, List<String>> rejectionCases(Path root) throws Exception {
        Files.createDirectories(root);
        Path tooMany = root.resolve("too-many"); Files.createDirectories(tooMany);
        for (int index = 0; index <= ModInputLimits.production().maxModJars(); index++) Files.createFile(tooMany.resolve(index + ".jar"));
        List<String> countCodes = stableRejected(tooMany);
        if (!countCodes.equals(List.of("REPOSITORY_JAR_LIMIT_EXCEEDED"))) throw new IllegalStateException("Jar count guard failed: " + countCodes);
        Path oversized = root.resolve("oversized"); Files.createDirectories(oversized);
        writeFixture(oversized.resolve("oversized.jar"), "oversized", null, ModInputLimits.production().maxAssetBytes() + 1);
        List<String> assetCodes = stableRejected(oversized);
        if (!assetCodes.equals(List.of("MOD_JAR_INVALID"))) throw new IllegalStateException("Asset guard failed: " + assetCodes);
        return Map.of("catalogCount", countCodes, "assetBytes", assetCodes);
    }

    private static List<String> stableRejected(Path root) {
        var scanner = new DefaultModRepositoryScanner();
        var first = scanner.scan(root.toAbsolutePath().normalize());
        var second = scanner.scan(root.toAbsolutePath().normalize());
        if (first.stream().anyMatch(ModDescriptor.class::isInstance) || !diagnostics(first).equals(diagnostics(second))) throw new IllegalStateException("Invalid input admitted or diagnostics changed");
        return first.stream().flatMap(entry -> entry.findings().stream()).map(ModFinding::code).toList();
    }

    private static List<String> diagnostics(List<ModCatalogEntry> entries) {
        return entries.stream().flatMap(entry -> entry.findings().stream()).map(Object::toString).toList();
    }

    private static void writeFixture(Path jar, String id, Path compiled, long assetBytes) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.setLevel(1);
            String manifest = "formatVersion: 1\nid: " + id + "\nname: Catalog Probe\nversion: 1.0.0\nauthors: [OpenGGF]\ndescription: Original bounded scalability fixture.\nengineApiRange: \">=0.7.0 <0.8.0\"\ntype: patch\nbaseGame: s2\ndependencies: []\naudioOverrides: {}\nartOverrides: {}\n" + (compiled == null ? "" : "entrypoint: creatorprobe.ProbeMod\n");
            entry(out, "META-INF/openggf-mod.yaml"); out.write(manifest.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            if (compiled != null) try (var paths = Files.walk(compiled)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    entry(out, compiled.relativize(file).toString().replace('\\', '/')); Files.copy(file, out); out.closeEntry();
                }
            }
            entry(out, "asset/payload.dat");
            byte[] buffer = new byte[8192];
            for (long left = assetBytes; left > 0; left -= Math.min(left, buffer.length)) out.write(buffer, 0, (int) Math.min(left, buffer.length));
            out.closeEntry();
        }
    }

    private static void entry(JarOutputStream out, String name) throws Exception { var entry = new JarEntry(name); entry.setTime(JarPackager.ENTRY_TIMESTAMP); out.putNextEntry(entry); }
    private static long heapPeak() { return ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP).mapToLong(pool -> pool.getPeakUsage().getUsed()).sum(); }
    private static long peakResidentBytes() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status")))
                if (line.startsWith("VmHWM:")) return Long.parseLong(line.split("\\s+")[1]) * 1024;
        } catch (Exception unavailable) { /* Linux-only observation; no equivalent is inferred elsewhere. */ }
        return -1;
    }
    private static void deleteTree(Path root) throws Exception { try (var files = Files.walk(root)) { for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); } }
}
