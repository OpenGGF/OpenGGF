package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kKosModuleSnapshot;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.timing.*;

import java.io.BufferedWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Passive production Kos queue causality probe (2026-10-17 ending reunion follow-up).
 * Inputs: original S3&K ROM, new output directory, solo Sonic/Tails +2 NTSC product
 * and a bounded inclusive level-frame window. Reuses the sampler's frozen title-owner
 * ingress, FAST defaults and private preferences. Emits metadata only, never pictures,
 * prepared payloads/decoder snapshots, readiness writes or native comparison input.
 */
public final class S3kEndingQueueProbeTool {
    private S3kEndingQueueProbeTool() { }

    record Arguments(S3kEndingSamplerTool.Arguments sampler, int first, int last) {
        static Arguments parse(String[] args) {
            Set<String> required = Set.of("--rom", "--out-dir", "--product", "--region", "--progress", "--level-window");
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (!required.contains(args[i]) || i + 1 >= args.length || values.putIfAbsent(args[i], args[i + 1]) != null) {
                    throw new IllegalArgumentException("Expected unique --rom --out-dir --product --region --progress --level-window FIRST:LAST");
                }
            }
            if (!values.keySet().equals(required) || !Set.of("sonic-solo", "tails-solo").contains(values.get("--product"))
                    || !"ntsc".equals(values.get("--region")) || !"+2".equals(values.get("--progress"))) {
                throw new IllegalArgumentException("Probe supports only solo Sonic/Tails +2 NTSC");
            }
            String window = values.get("--level-window");
            if (!window.matches("[1-9][0-9]*:[1-9][0-9]*")) throw new IllegalArgumentException("Invalid level window");
            String[] bounds = window.split(":");
            int first = Integer.parseInt(bounds[0]), last = Integer.parseInt(bounds[1]);
            if (last < first || last >= S3kEndingSamplerTool.LIMIT || last - first >= 128) {
                throw new IllegalArgumentException("Inclusive level window must contain 1..128 frames below60000");
            }
            var sampler = new S3kEndingSamplerTool.Arguments(Path.of(values.get("--rom")), Path.of(values.get("--out-dir")),
                    null, S3kEndingSamplerTool.Product.parse(values.get("--product")), 2, "ntsc", false);
            return new Arguments(sampler, first, last);
        }
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        Probe probe = new Probe(arguments);
        S3kEndingSamplerTool.run(arguments.sampler(), probe);
        Path output = arguments.sampler().output();
        Properties provenance = new Properties();
        try (var reader = Files.newBufferedReader(output.resolve("provenance.properties"))) { provenance.load(reader); }
        provenance.setProperty("diagnostic.window.level_frames", arguments.first() + ":" + arguments.last());
        provenance.setProperty("diagnostic.rows", Integer.toString(probe.rows));
        provenance.setProperty("diagnostic.file.sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(output.resolve("queues.jsonl")))));
        provenance.setProperty("diagnostic.observer.restored", Boolean.toString(probe.restored));
        provenance.setProperty("diagnostic.policy", "LIVE; observation only; no recorded admission");
        try (var writer = Files.newBufferedWriter(output.resolve("provenance.properties"))) { provenance.store(writer, "Passive ending queue probe"); }
        System.out.println("Passive queue metadata completed: " + arguments.sampler().product().cli + "; rows=" + probe.rows
                + "; LF=" + arguments.first() + ".." + arguments.last() + "; observer restored=" + probe.restored);
    }

    record HandleMetadata(String kind, long ordinal, String submissionFingerprint) { }
    record ModuleMetadata(int completedModules, int activeModuleOffset, HandleMetadata activeChild, boolean prepared) { }
    record JobMetadata(HandleMetadata handle, int source, int compressedLength, int destination, int destinationLength,
                       String variant, int moduleCount, boolean ready, boolean claimed, boolean physicallyRetired,
                       boolean profileActive, int assignedServiceFrames, int remainingServiceFrames,
                       List<String> eligibleBoundaries, String decisionSource, String serviceModel, ModuleMetadata module) { }

    static HandleMetadata projectHandle(HardwareWorkHandle handle) {
        return handle == null ? null : new HandleMetadata(handle.kind().name(), handle.ordinal(), handle.submissionFingerprint());
    }

    static JobMetadata projectJob(HardwareTimingJob.Snapshot job) {
        // Explicit projection is intentional: Jackson must never see the rewind snapshot itself.
        ModuleMetadata module = job.preparationSnapshot() instanceof S3kKosModuleSnapshot parent
                ? new ModuleMetadata(parent.completedModules(), parent.activeModuleOffset(), projectHandle(parent.activeChild()), parent.prepared()) : null;
        return new JobMetadata(projectHandle(job.handle()), job.romSourceAddress(), job.compressedLength(), job.destinationAddress(),
                job.destinationLength(), job.compressionVariant(), job.moduleCount(), job.ready(), job.claimed(), job.physicallyRetired(),
                job.profileActive(), job.assignedServiceFrames(), job.remainingServiceFrames(),
                job.eligibleBoundaries().stream().map(Enum::name).sorted().toList(), job.decisionSource().name(), job.serviceModel(), module);
    }

    static void requireLive(HardwareTimingSnapshot snapshot) {
        if (snapshot.recordedAdmissionActive() || snapshot.admissionPolicies().values().stream()
                .anyMatch(policy -> policy != HardwareReadinessAdmissionPolicy.LIVE)) {
            throw new IllegalStateException("Passive queue probe requires every timing kind LIVE");
        }
    }

    private static boolean kos(HardwareWorkKind kind) {
        return kind == HardwareWorkKind.KOS_DECOMPRESSION_QUEUE || kind == HardwareWorkKind.KOS_MODULE_QUEUE;
    }

    private static final class Probe implements S3kEndingSamplerTool.FrameObserver {
        private final Arguments arguments;
        private final ObjectMapper mapper = new ObjectMapper();
        private final Set<Integer> observedLevelFrames = new HashSet<>();
        private BufferedWriter writer;
        private GameplayModeContext gameplay;
        private HardwareTimingBoundaryObserver previous;
        private HardwareTimingBoundaryObserver installed;
        private int outer = -1, beforeLevel = -1, rows;
        private boolean finished, restored;

        private Probe(Arguments arguments) { this.arguments = arguments; }

        @Override public void started(GameplayCaptureSession session) throws Exception {
            if (!"FAST".equals(GameServices.configuration().getString(SonicConfiguration.LOAD_TIME_SIMULATION))) {
                throw new IllegalStateException("Declared diagnostic requires unmodified FAST configuration");
            }
            requireLive(GameServices.hardwareTiming().capture());
            gameplay = SessionManager.getCurrentGameplayMode();
            previous = gameplay.hardwareTimingBoundaryObserver();
            if (previous != HardwareTimingBoundaryObserver.NO_OP) throw new IllegalStateException("Refuse to replace another observer");
            writer = Files.newBufferedWriter(arguments.sampler().output().resolve("queues.jsonl"));
            installed = boundary -> {
                try { observe(boundary == HardwareServiceBoundary.PRE_MAIN_LOOP
                        ? "after_service_before_art_after" : "after_service_after_art_after", boundary); }
                catch (java.io.IOException error) { throw new UncheckedIOException(error); }
            };
            gameplay.setHardwareTimingBoundaryObserver(installed);
        }

        @Override public void beforeStep(int outer) {
            this.outer = outer;
            beforeLevel = GameServices.level().getFrameCounter();
        }

        @Override public void afterStep(int outer) throws Exception {
            observe("after_step", null);
            int frame = GameServices.level().getFrameCounter();
            if (frame >= arguments.last()) {
                for (int expected = arguments.first(); expected <= arguments.last(); expected++) {
                    if (!observedLevelFrames.contains(expected)) throw new IllegalStateException("Missing observed LF " + expected);
                }
                finished = true;
            }
        }

        private void observe(String phase, HardwareServiceBoundary callback) throws java.io.IOException {
            int frame = GameServices.level().getFrameCounter();
            if (frame < arguments.first() || frame > arguments.last()) return;
            var timing = GameServices.hardwareTiming().capture();
            requireLive(timing);
            var zone = (EndingZoneRuntimeState) GameServices.zoneRuntimeState();
            var art = S3kRuntimeArtCoordinator.from(GameServices.runtimeArtCoordinator());
            var diagnostics = art.captureQueueDiagnostics();
            for (var queue : diagnostics) if (!queue.serviceObservations().isEmpty()) {
                throw new IllegalStateException("Service observations must remain empty");
            }
            var direct = art.directQueue().capture();
            var physical = GameServices.level().getObjectManager().getObjectServices().kosinskiModuleQueue();
            if (physical == null) throw new IllegalStateException("Missing physical module queue");
            var jobs = timing.jobs().stream().filter(job -> kos(job.kind())).map(S3kEndingQueueProbeTool::projectJob).toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phase", phase); row.put("callbackBoundary", callback); row.put("outer", outer);
            row.put("levelBeforeStep", beforeLevel); row.put("levelAtObservation", frame);
            row.put("objectOrdinal", GameServices.level().getObjectManager().getFrameCounter());
            row.put("vint", GameServices.level().getObjectManager().getVblaCounter());
            row.put("fg", zone.screen().foregroundRoutine); row.put("bg", zone.screen().backgroundRoutine);
            row.put("progress", zone.ending().progress()); row.put("cloudDriftFixed", zone.screen().cloudDriftFixed);
            row.put("cameraX", GameServices.camera().getX()); row.put("cameraY", GameServices.camera().getY());
            row.put("cameraCopyY", GameServices.camera().getYCopy());
            row.put("lastServicedBoundary", timing.lastServicedBoundary());
            row.put("admissionPolicies", timing.admissionPolicies());
            row.put("diagnosticQueues", diagnostics); // Diagnostic queuedFingerprints are NOT timing submission fingerprints.
            row.put("directEntries", direct.entries().stream().map(entry -> Map.of("handle", projectHandle(entry.handle()),
                    "descriptor", entry.descriptor(), "physical", entry.physical(), "prepared", entry.prepared())).toList());
            row.put("directDeferNewHeadPreparationVisibility", direct.deferNewHeadPreparationVisibility());
            row.put("logicalModulesLeft", art.moduleQueue().modulesLeft());
            row.put("physicalPhase", physical.phase()); row.put("physicalModulesLeftRaw", physical.modulesLeftRaw());
            row.put("physicalActiveSource", physical.activeSourceAddress());
            row.put("physicalActiveDestination", physical.activeDestinationVramBytes());
            row.put("physicalArchives", physical.queuedArchives()); // Immutable address/count metadata, no byte snapshots.
            row.put("jobs", jobs);
            writer.write(mapper.writeValueAsString(row)); writer.newLine(); rows++;
            if (phase.equals("after_step")) observedLevelFrames.add(frame);
        }

        @Override public boolean finished() { return finished; }

        @Override public void close() throws Exception {
            try {
                if (gameplay != null && installed != null) {
                    if (gameplay.hardwareTimingBoundaryObserver() != installed) throw new IllegalStateException("Observer ownership changed");
                    gameplay.setHardwareTimingBoundaryObserver(previous);
                    restored = gameplay.hardwareTimingBoundaryObserver() == previous;
                }
            } finally { if (writer != null) writer.close(); }
        }
    }
}
