package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Command-line entry point for fail-closed FBZ engine visual captures. */
public final class FbzVisualCaptureTool {

    public static final String REVIEWED_MANIFEST_SHA256 =
            "D13D037BAF52BBD65D28096A71A54ACACB4229B8C4C560C76DCB921E90DC40DD";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FbzVisualCaptureTool() {
    }

    public static void main(String[] args) throws Exception {
        int status = run(Arguments.parse(args));
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(Arguments arguments) throws Exception {
        Objects.requireNonNull(arguments, "arguments");
        FbzVisualCaptureMode mode = FbzVisualCaptureMode.resolve(
                arguments.required("mode-key"),
                arguments.requiredInt("framebuffer-width"),
                arguments.requiredInt("framebuffer-height"),
                arguments.requiredInt("native-crop-x"));
        String checkpoint = arguments.required("checkpoint");
        Path manifestPath = arguments.requiredPath("manifest");
        String manifestHash = arguments.value("manifest-sha256", REVIEWED_MANIFEST_SHA256);
        FbzVisualManifest manifest = FbzVisualManifest.load(manifestPath, manifestHash);
        FbzVisualEvidenceAmendment amendment = FbzVisualEvidenceAmendment.load(
                arguments.requiredPath("evidence-amendment"),
                arguments.required("evidence-amendment-sha256"));
        FbzVisualScenarioDriver.ScenarioPlan plan =
                new FbzVisualScenarioDriver(manifest).plan(checkpoint);

        Path workspace = arguments.path("workspace", Path.of("."));
        Path outputRoot = arguments.requiredPath("output-root");
        FbzVisualCapturePaths paths = mode.paths(outputRoot, checkpoint, plan.recipe().output());
        FbzVisualCapturePublisher publisher = new FbzVisualCapturePublisher(MAPPER);
        long rngSeed = arguments.longValue("rng-seed", 0L);

        FbzVisualPrebootVerifier.Inputs prebootInputs = new FbzVisualPrebootVerifier.Inputs(
                workspace,
                arguments.requiredPath("rom"),
                arguments.requiredPath("artifact"),
                arguments.required("artifact-sha256"),
                manifest,
                mode,
                arguments.required("input-schedule-source"),
                arguments.required("input-schedule-sha256"),
                arguments.required("savestate-source"),
                arguments.required("savestate-sha256"),
                rngSeed);
        FbzVisualPrebootVerifier.Result preboot = FbzVisualPrebootVerifier.verify(prebootInputs);
        if (!preboot.verified()) {
            publisher.publishRejected(paths, FbzVisualCaptureReceipt.rejected(
                    checkpoint, mode.key(), "preboot verification failed: " + preboot.rejectionReason(),
                    preboot.provenance()));
            return 2;
        }
        if (!plan.captureSupported()) {
            publisher.publishRejected(paths, FbzVisualCaptureReceipt.rejected(
                    checkpoint, mode.key(), plan.blocker(), preboot.provenance()));
            return 3;
        }
        if (!mode.nativeMode()) {
            publisher.publishRejected(paths, FbzVisualCaptureReceipt.rejected(
                    checkpoint, mode.key(),
                    "compatibility mode configuration/evidence is not implemented yet",
                    preboot.provenance()));
            return 3;
        }
        String sourceRejection = exactStartSourceRejection(arguments, plan);
        if (sourceRejection != null) {
            publisher.publishRejected(paths, FbzVisualCaptureReceipt.rejected(
                    checkpoint, mode.key(), sourceRejection, preboot.provenance()));
            return 4;
        }

        Map<String, Object> provenance = new LinkedHashMap<>(preboot.provenance());
        provenance.putAll(amendment.provenance());
        provenance.put("recipe_version", 1);
        provenance.put("executed_phases", java.util.List.of(
                "complete level load",
                "fixture precondition/readback",
                "reviewed production gameplay progression",
                "production scene/HUD/fade render",
                "full framebuffer and native crop readback"));

        try (HiddenGlCaptureSession session = new HiddenGlCaptureSession(mode)) {
            session.boot(prebootInputs.rom(), plan.zeroBasedAct(), rngSeed);
            FbzVisualScenarioExecutor.Execution execution =
                    new FbzVisualScenarioExecutor(session).execute(plan, amendment);
            FbzVisualStateProbe.Snapshot pre = execution.pre();
            provenance.put("rng_seed", pre.values().get("rng_seed"));
            provenance.put("rng_state", pre.values().get("rng_state"));
            provenance.put("observed_transients", execution.observations());
            FbzVisualStateProbe.Snapshot post = execution.post();
            FbzVisualGameplayAdvanceVerifier.verify(plan, pre, post, amendment);
            FbzVisualVisibilityVerifier.verifyState(post.values());
            HiddenGlCaptureSession.CapturedImages images = session.renderAndCapture();
            EncodedImages encoded = encode(images, outputRoot, mode.nativeMode());

            FbzVisualCaptureReceipt receipt = FbzVisualCaptureReceipt.accepted(
                    checkpoint,
                    mode.key(),
                    provenance,
                    pre.values(),
                    post.values(),
                    FbzVisualPrebootVerifier.sha256(encoded.fullPng()),
                    FbzVisualPrebootVerifier.sha256(encoded.nativeCropPng()));
            publisher.publishAccepted(paths, encoded.fullPng(), encoded.nativeCropPng(), receipt);
            publishCadenceIfPresent(execution, plan, amendment, outputRoot, publisher, provenance);
            return 0;
        } catch (Exception failure) {
            provenance.put("capture_failure_type", failure.getClass().getName());
            publisher.publishRejected(paths, FbzVisualCaptureReceipt.rejected(
                    checkpoint, mode.key(), "capture rejected: " + message(failure), provenance));
            return 5;
        }
    }

    private static void publishCadenceIfPresent(FbzVisualScenarioExecutor.Execution execution,
                                                FbzVisualScenarioDriver.ScenarioPlan plan,
                                                FbzVisualEvidenceAmendment amendment,
                                                Path outputRoot,
                                                FbzVisualCapturePublisher publisher,
                                                Map<String, Object> commonProvenance) throws IOException {
        if (execution.cadenceFrames().isEmpty()) return;
        FbzVisualCadenceCapture.Spec spec = FbzVisualCadenceCapture.spec(plan.checkpointId());
        String reviewedSeries = amendment.cadenceSeriesName(plan.checkpointId());
        if (!spec.series().equals(reviewedSeries)) {
            throw new IllegalStateException("FBZ cadence series name disagrees with reviewed amendment");
        }

        List<FbzVisualCadenceVerifier.FrameEvidence> evidence = new ArrayList<>();
        List<FbzVisualCadenceCapture.FramePublication> publications = new ArrayList<>();
        for (FbzVisualScenarioExecutor.CadenceFrame frame : execution.cadenceFrames()) {
            EncodedImages encoded = encode(frame.images(), outputRoot, true);
            String cropHash = FbzVisualPrebootVerifier.sha256(encoded.nativeCropPng());
            int timerBefore = requiredStateInt(frame.before(), "aniplc_timer_" + spec.channel());
            int frameBefore = requiredStateInt(frame.before(), "aniplc_frame_" + spec.channel());
            int timerAfter = requiredStateInt(frame.after(), "aniplc_timer_" + spec.channel());
            int frameAfter = requiredStateInt(frame.after(), "aniplc_frame_" + spec.channel());
            evidence.add(new FbzVisualCadenceVerifier.FrameEvidence(frame.index(), frame.control(),
                    timerBefore, frameBefore, timerAfter, frameAfter,
                    frame.vramSha256(), cropHash, true,
                    frame.reviewedVisibleRegionChanged()));

            FbzVisualCadenceCapture.FramePaths paths = FbzVisualCadenceCapture.paths(
                    outputRoot, reviewedSeries, frame.index(), frame.control());
            Map<String, Object> receipt = new LinkedHashMap<>(commonProvenance);
            receipt.put("schema_version", 2);
            receipt.put("kind", "openggf-natural-aniplc-cadence");
            receipt.put("series", reviewedSeries);
            receipt.put("checkpoint", plan.checkpointId());
            receipt.put("capture_index", frame.index());
            receipt.put("control", frame.control());
            receipt.put("overlay_free", true);
            receipt.put("natural_expiry_observed", timerBefore == 0 && frameAfter != frameBefore);
            receipt.put("channel", spec.channel());
            receipt.put("destination_tile", spec.destinationTile());
            receipt.put("destination_byte_length", spec.tileCount() * PatternBytes.BYTES_PER_TILE);
            receipt.put("reset_duration", spec.resetTimer());
            receipt.put("level_frame_counter",
                    requiredStateInt(frame.after(), "level_frame_counter"));
            receipt.put("timer_before", timerBefore);
            receipt.put("frame_before", frameBefore);
            receipt.put("timer_after", timerAfter);
            receipt.put("frame_after", frameAfter);
            receipt.put("vram_sha256", frame.vramSha256());
            receipt.put("crop_sha256", cropHash);
            receipt.put("reviewed_visible_region_changed", frame.reviewedVisibleRegionChanged());
            receipt.put("state", frame.after().values());
            publications.add(new FbzVisualCadenceCapture.FramePublication(
                    paths.png(), paths.receipt(), encoded.nativeCropPng(), receipt));
        }
        FbzVisualCadenceVerifier.verify(evidence);
        publisher.publishCadenceSeries(publications);
    }

    private static int requiredStateInt(FbzVisualStateProbe.Snapshot state, String key) {
        Object value = state.values().get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("FBZ cadence receipt lacks numeric " + key);
        }
        return number.intValue();
    }

    private static final class PatternBytes {
        private static final int BYTES_PER_TILE = 32;
        private PatternBytes() { }
    }

    private static String exactStartSourceRejection(Arguments arguments,
                                                    FbzVisualScenarioDriver.ScenarioPlan plan) {
        if (!"fbz1-start-outdoor".equals(plan.checkpointId())) return null;
        String schedule = arguments.required("input-schedule-source");
        String savestate = arguments.required("savestate-source");
        if (!"none:idle-one-frame".equals(schedule)) {
            return "FBZ native-start recipe requires --input-schedule-source "
                    + "none:idle-one-frame; supplied source is not consumed";
        }
        if (!"none:native-load".equals(savestate)) {
            return "FBZ native-start recipe requires --savestate-source "
                    + "none:native-load; route savestates are forbidden here";
        }
        return null;
    }

    private static EncodedImages encode(HiddenGlCaptureSession.CapturedImages images,
                                        Path outputRoot, boolean nativeMode) throws IOException {
        Files.createDirectories(outputRoot.toAbsolutePath().normalize());
        Path staging = Files.createTempDirectory(outputRoot.toAbsolutePath().normalize(), "fbz-png-");
        Path fullPath = staging.resolve("full.png");
        Path cropPath = staging.resolve("crop.png");
        try {
            ScreenshotCapture.savePNG(images.full(), fullPath);
            byte[] full = Files.readAllBytes(fullPath);
            byte[] crop;
            if (nativeMode) {
                crop = full;
            } else {
                ScreenshotCapture.savePNG(images.nativeCrop(), cropPath);
                crop = Files.readAllBytes(cropPath);
            }
            return new EncodedImages(full, crop);
        } finally {
            Files.deleteIfExists(fullPath);
            Files.deleteIfExists(cropPath);
            Files.deleteIfExists(staging);
        }
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record EncodedImages(byte[] fullPng, byte[] nativeCropPng) {
    }

    /** Minimal strict `--key value` argument parser. */
    static final class Arguments {
        private final Map<String, String> values;

        private Arguments(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        static Arguments parse(String[] args) {
            Objects.requireNonNull(args, "args");
            Map<String, String> parsed = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                String key = args[i];
                if (!key.startsWith("--") || key.length() == 2 || i + 1 >= args.length) {
                    throw new IllegalArgumentException("FBZ capture arguments must be --key value pairs");
                }
                key = key.substring(2);
                if (parsed.putIfAbsent(key, args[i + 1]) != null) {
                    throw new IllegalArgumentException("Duplicate FBZ capture argument: --" + key);
                }
            }
            return new Arguments(parsed);
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing FBZ capture argument --" + key);
            }
            return value;
        }

        String value(String key, String fallback) {
            String value = values.get(key);
            return value == null ? fallback : value;
        }

        Path requiredPath(String key) {
            return Path.of(required(key)).toAbsolutePath().normalize();
        }

        Path path(String key, Path fallback) {
            return Path.of(value(key, fallback.toString())).toAbsolutePath().normalize();
        }

        int requiredInt(String key) {
            try {
                return Integer.decode(required(key));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid integer for --" + key + ": " + required(key), invalid);
            }
        }

        long longValue(String key, long fallback) {
            String value = values.get(key);
            if (value == null) return fallback;
            try {
                return Long.decode(value);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid long for --" + key + ": " + value, invalid);
            }
        }
    }
}
