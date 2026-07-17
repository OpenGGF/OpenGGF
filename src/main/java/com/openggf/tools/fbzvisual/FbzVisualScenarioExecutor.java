package com.openggf.tools.fbzvisual;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Fbz2SubbossInstance;
import com.openggf.game.sonic3k.objects.FbzEggPrisonInstance;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.game.sonic3k.objects.FbzEndEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.FbzExitDoorInstance;
import com.openggf.game.sonic3k.objects.FbzExitHallInstance;
import com.openggf.game.sonic3k.objects.FbzMinibossInstance;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes reviewed, deterministic FBZ visual recipe phases. */
public final class FbzVisualScenarioExecutor {

    private static final int MAX_REDRAW_PHASES = 40;

    private final HiddenGlCaptureSession session;
    private final FbzGameServicesFixturePort port = new FbzGameServicesFixturePort();

    public FbzVisualScenarioExecutor(HiddenGlCaptureSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public Execution execute(FbzVisualScenarioDriver.ScenarioPlan plan,
                             FbzVisualEvidenceAmendment amendment) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(amendment, "amendment");
        FbzVisualStateProbe.Snapshot pre = session.decorateSnapshot(new FbzVisualFixture(port)
                .applyVerified(plan.fixtureMutation()));
        List<Map<String, Object>> observations = new ArrayList<>();
        List<CadenceFrame> cadenceFrames = new ArrayList<>();
        switch (plan.strategy()) {
            case "native-start" -> executeNativeStart(amendment, observations);
            case "act1-aniplc" -> executeAniPlc(plan, amendment, observations, cadenceFrames);
            case "act1-bidirectional-boundary" -> executeAct1Boundary(plan, observations);
            case "seamless-transition" -> executeSeamlessTransition(observations);
            case "act2-bidirectional-boundary" -> executeAct2Boundary(observations);
            case "act2-plane-entry" -> executePlaneEntry(observations);
            case "act2-plane-steady" -> executePlaneSteady(observations);
            case "act1-miniboss-active" -> executeMiniboss(plan, observations);
            case "act2-subboss-active" -> executeSubboss(plan, observations);
            case "act2-end-boss-active" -> executeEndBoss(plan, observations);
            case "act2-exit-ready" -> executeExit(plan, observations);
            case "act2-final-capsule" -> executeCapsule(plan, observations);
            default -> throw new IllegalStateException("Unsupported FBZ scenario strategy: " + plan.strategy());
        }
        return new Execution(pre, session.captureState(), List.copyOf(observations),
                List.copyOf(cadenceFrames));
    }

    private void executeNativeStart(FbzVisualEvidenceAmendment amendment,
                                    List<Map<String, Object>> observations) {
        session.stepFrames(1);
        FbzVisualStateProbe.Snapshot firstTick = session.captureState();
        amendment.verifyFirstAnimationTick(firstTick.values());
        observations.add(observation("first-animation-tick", firstTick));

        int targetFrame = amendment.acceptedLevelFrameCounter();
        int currentFrame = GameServices.level().getFrameCounter();
        if (targetFrame < currentFrame) {
            throw new IllegalStateException("FBZ approved first-visible frame precedes the first "
                    + "production animation tick: target=" + targetFrame + ", current=" + currentFrame);
        }
        session.stepFrames(targetFrame - currentFrame);
        FbzVisualStateProbe.Snapshot accepted = session.captureState();
        amendment.verifyAcceptedVisibleFrame(accepted.values());
        FbzVisualVisibilityVerifier.verifyState(accepted.values());
        observations.add(observation("first-fully-visible-gameplay-frame", accepted));
    }

    private void executeMiniboss(FbzVisualScenarioDriver.ScenarioPlan plan,
                                 List<Map<String, Object>> observations) {
        session.stepFrames(1); // native placement window + setup-only object callback
        FbzMinibossInstance boss = findLive(FbzMinibossInstance.class);
        if (boss == null) {
            boss = requireCreated(objects().createDynamicObject(() -> new FbzMinibossInstance(
                    new ObjectSpawn(0x2F00, 0x5E0, Sonic3kObjectIds.FBZ_MINIBOSS,
                            0, 0, true, 0x5E0))), "FBZ miniboss");
            session.stepFrames(1);
        }
        observations.add(observation("miniboss-native-graph-initialized", port.snapshot()));

        SolidContact standing = new SolidContact(true, false, false, true, false);
        for (int i = 0; i < 260; i++) {
            FbzVisualStateProbe.Snapshot state = port.snapshot();
            if ("ACTIVE".equals(state.values().get("miniboss_phase"))) {
                requireEquals(state, "miniboss_remaining_hits", 6);
                observations.add(observation("miniboss-first-active-frame", state));
                session.stepFrames(1);
                restoreCapturePosition(plan);
                FbzVisualStateProbe.Snapshot stable = port.snapshot();
                requireEquals(stable, "miniboss_phase", "ACTIVE");
                requireEquals(stable, "camera_min_x", 0x2E20);
                requireEquals(stable, "camera_max_x", 0x2EA0);
                requireEquals(stable, "camera_target_max_y", 0x540);
                requireEquals(stable, "miniboss_art_ready", true);
                return;
            }
            ObjectInstance plunger = objects().getActiveObjects().stream()
                    .filter(object -> "FBZMinibossPlunger".equals(object.getName()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "FBZ miniboss did not allocate its native plunger child"));
            if (!(plunger instanceof SolidObjectListener listener)) {
                throw new IllegalStateException("FBZ miniboss plunger is not a solid-contact listener");
            }
            listener.onSolidContact(GameServices.camera().getFocusedSprite(), standing,
                    GameServices.level().getFrameCounter());
            session.stepFrames(1);
        }
        throw new IllegalStateException("FBZ miniboss did not reach ACTIVE within 260 production frames");
    }

    private void executeSubboss(FbzVisualScenarioDriver.ScenarioPlan plan,
                                List<Map<String, Object>> observations) {
        session.stepFrames(1);
        Fbz2SubbossInstance boss = findLive(Fbz2SubbossInstance.class);
        if (boss == null) {
            boss = requireCreated(objects().createDynamicObject(() -> new Fbz2SubbossInstance(
                    new ObjectSpawn(0x2B40, 0x5F0, Sonic3kObjectIds.FBZ2_SUBBOSS,
                            0, 0, true, 0x5F0))), "FBZ2 subboss");
            session.stepFrames(1);
        }
        requireEquals(port.snapshot(), "subboss_collision_property", 0x7F);
        port.write("player_x", 0x2B40);
        for (int i = 0; i < 180; i++) {
            FbzVisualStateProbe.Snapshot state = port.snapshot();
            if ("ACTIVE".equals(state.values().get("subboss_phase"))) {
                observations.add(observation("subboss-first-active-frame", state));
                session.stepFrames(1);
                restoreCapturePosition(plan);
                FbzVisualStateProbe.Snapshot stable = port.snapshot();
                requireEquals(stable, "subboss_phase", "ACTIVE");
                requireEquals(stable, "subboss_collision_property", 0x7F);
                requireEquals(stable, "camera_min_x", 0x2900);
                requireEquals(stable, "camera_target_max_y", 0x5E0);
                requireEquals(stable, "subboss_art_ready", true);
                return;
            }
            session.stepFrames(1);
        }
        throw new IllegalStateException("FBZ2 subboss did not reach ACTIVE within 180 production frames");
    }

    private void executeEndBoss(FbzVisualScenarioDriver.ScenarioPlan plan,
                                List<Map<String, Object>> observations) {
        session.stepFrames(1);
        FbzEndBossInstance boss = findLive(FbzEndBossInstance.class);
        if (boss == null) {
            boss = requireCreated(objects().createDynamicObject(() -> new FbzEndBossInstance(
                    new ObjectSpawn(0x31C0, 0x690, Sonic3kObjectIds.FBZ_END_BOSS,
                            0, 0, false, 0x690))), "FBZ end boss");
        }
        for (int i = 0; i < 180; i++) {
            FbzVisualStateProbe.Snapshot state = port.snapshot();
            if ("DESCEND".equals(state.values().get("end_boss_phase"))) {
                requireEquals(state, "end_boss_remaining_hits", 8);
                requireEquals(state, "end_boss_arm_count", 2);
                requireEquals(state, "end_boss_chain_count", 8);
                observations.add(observation("end-boss-first-active-frame", state));
                session.stepFrames(1);
                restoreCapturePosition(plan);
                restoreEndBossCaptureState();
                FbzVisualStateProbe.Snapshot stable = port.snapshot();
                requireEquals(stable, "end_boss_phase", "DESCEND");
                requireEquals(stable, "screen_shake_active", false);
                requireEquals(stable, "camera_max_x", 0x32B8);
                requireEquals(stable, "boss_load_position_adjustment_pending", true);
                requireEquals(stable, "end_boss_art_ready", true);
                return;
            }
            session.stepFrames(1);
        }
        throw new IllegalStateException("FBZ end boss did not reach DESCEND within 180 production frames");
    }

    private void executeExit(FbzVisualScenarioDriver.ScenarioPlan plan,
                             List<Map<String, Object>> observations) {
        drainKosmQueue("pre-existing FBZ queue", 600);
        var queue = SessionManager.getCurrentGameplayMode().getKosinskiModuleQueue();
        try {
            var rom = GameServices.rom().getRom();
            for (var entry : Sonic3kPlcLoader.fbzEndBossExitKosmEntries()) {
                if (!queue.enqueue(rom, entry.sourceAddress(), entry.destinationVramBytes())) {
                    throw new IllegalStateException("FBZ exit KosM queue rejected a native entry");
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not queue FBZ exit art", failure);
        }
        observations.add(observation("exit-art-native-queue-enqueued", port.snapshot()));
        drainKosmQueue("FBZ exit art", 600);
        publishExitArt();
        ensureExitPlacementGraph();
        session.stepFrames(1);
        restoreCapturePosition(plan);
        restoreExitCaptureState();
        FbzVisualStateProbe.Snapshot state = port.snapshot();
        requireEquals(state, "boss_active", false);
        requireEquals(state, "boss_defeated", true);
        requireEquals(state, "events_routine_fg", 12);
        requireEquals(state, "events_routine_bg", 16);
        requireEquals(state, "plane_assignment", "FG=B,BG=A");
        requireEquals(state, "collision_mode", "FOREGROUND_ONLY");
        requireEquals(state, "kosm_queue_idle", true);
        requireEquals(state, "kosm_queue_archive_count", 0);
        requireEquals(state, "exit_door_art_ready", true);
        requireEquals(state, "exit_hall_art_ready", true);
        requireObjectCountAtLeast(state, "FbzExitDoorInstance", 1);
        requireObjectCountAtLeast(state, "FbzExitHallInstance", 1);
        observations.add(observation("exit-art-and-placement-graph-ready", state));
    }

    private void executeCapsule(FbzVisualScenarioDriver.ScenarioPlan plan,
                                List<Map<String, Object>> observations) {
        FbzEndEggCapsuleInstance capsule = findLive(FbzEndEggCapsuleInstance.class);
        if (capsule == null) {
            requireCreated(objects().createDynamicObject(() -> new FbzEndEggCapsuleInstance(
                    0x307C, 0x660)), "FBZ final EggCapsule");
        }
        session.stepFrames(1);
        restoreCapturePosition(plan);
        FbzVisualStateProbe.Snapshot state = port.snapshot();
        requireEquals(state, "camera_y", 0x720);
        requireEquals(state, "boss_active", false);
        requireObjectCountAtLeast(state, "FbzEndEggCapsuleInstance", 1);
        requireObjectCountAtLeast(state, "FbzEndEggCapsuleButtonInstance", 1);
        requireEquals(state, "egg_capsule_art_ready", true);
        int placedPrisons = objectCount(state, FbzEggPrisonInstance.class.getSimpleName());
        if (placedPrisons != 0) {
            throw new IllegalStateException("FBZ final capsule fixture retained " + placedPrisons
                    + " placed $CF egg-prison objects");
        }
        observations.add(observation("generic-final-capsule-before-transition-gate", state));
    }

    private void restoreCapturePosition(FbzVisualScenarioDriver.ScenarioPlan plan) {
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("player_x", plan.recipe().centreX());
        writes.put("player_y", plan.recipe().centreY());
        var camera = plan.recipe().setup().path("camera");
        if (camera.isObject() && camera.path("x").isNumber()) {
            writes.put("camera_x", camera.path("x").asInt());
        }
        if (camera.isObject() && camera.path("y").isNumber()) {
            writes.put("camera_y", camera.path("y").asInt());
        }
        new FbzVisualFixture(port).applyVerified(
                new FbzVisualFixture.Mutation(Map.of(), writes));
    }

    private void restoreEndBossCaptureState() {
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("events_routine_fg", 12);
        writes.put("events_routine_bg", 16);
        writes.put("plane_assignment", "FG=B,BG=A");
        writes.put("collision_mode", "FOREGROUND_AND_BACKGROUND");
        writes.put("screen_shake_active", false);
        writes.put("boss_load_position_adjustment_pending", true);
        writes.put("camera_max_x", 0x32B8);
        writes.put("camera_target_max_x", 0x32B8);
        writes.put("boss_active", true);
        new FbzVisualFixture(port).applyVerified(
                new FbzVisualFixture.Mutation(Map.of(), writes));
    }

    private void restoreExitCaptureState() {
        Map<String, Object> writes = new LinkedHashMap<>();
        writes.put("events_routine_fg", 12);
        writes.put("events_routine_bg", 16);
        writes.put("plane_assignment", "FG=B,BG=A");
        writes.put("collision_mode", "FOREGROUND_ONLY");
        writes.put("screen_shake_active", false);
        writes.put("boss_active", false);
        writes.put("boss_defeated", true);
        new FbzVisualFixture(port).applyVerified(
                new FbzVisualFixture.Mutation(Map.of(), writes));
    }

    private void drainKosmQueue(String label, int maxFrames) {
        var mode = SessionManager.getCurrentGameplayMode();
        if (mode == null) throw new IllegalStateException("No gameplay mode for " + label);
        var queue = mode.getKosinskiModuleQueue();
        for (int i = 0; i <= maxFrames; i++) {
            if (queue.isIdle()) return;
            session.stepFrames(1);
        }
        throw new IllegalStateException(label + " did not drain within " + maxFrames + " frames");
    }

    private void publishExitArt() {
        var render = GameServices.level().getObjectRenderManager();
        if (render == null || !(render.getArtProvider() instanceof Sonic3kObjectArtProvider provider)) {
            throw new IllegalStateException("FBZ exit art requires Sonic3kObjectArtProvider");
        }
        try {
            provider.registerFbzExitArtSheets(GameServices.level().getCurrentLevel(),
                    GameServices.rom().getRom());
            render.ensurePatternsCached(GameServices.graphics(), PatternAtlasRange.OBJECTS.base());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not publish FBZ exit art consumers", failure);
        }
    }

    private void ensureExitPlacementGraph() {
        int[][] hallRecords = {
                {0x3408, 0x660, 0}, {0x3448, 0x658, 4}, {0x34A8, 0x658, 4},
                {0x34D8, 0x658, 4}, {0x3528, 0x658, 4}, {0x3558, 0x658, 4},
                {0x35A8, 0x658, 4}, {0x35D8, 0x658, 4}, {0x3638, 0x658, 4},
                {0x3558, 0x658, 4}, {0x3678, 0x660, 0}
        };
        Map<String, Integer> requiredOccurrences = new LinkedHashMap<>();
        for (int[] record : hallRecords) {
            String identity = record[0] + ":" + record[1] + ":" + record[2];
            int required = requiredOccurrences.merge(identity, 1, Integer::sum);
            long existing = objects().getActiveObjects().stream()
                    .filter(object -> object instanceof FbzExitHallInstance
                            && object.getX() == record[0] && object.getY() == record[1]
                            && object.getSpawn().subtype() == record[2])
                    .count();
            if (existing < required) {
                requireCreated(objects().createDynamicObject(() -> new FbzExitHallInstance(
                        new ObjectSpawn(record[0], record[1], Sonic3kObjectIds.FBZ_EXIT_HALL,
                                record[2], 0, false, record[1]))), "FBZ exit hall record");
            }
        }
        boolean doorExists = objects().getActiveObjects().stream()
                .anyMatch(object -> object instanceof FbzExitDoorInstance
                        && object.getX() == 0x3680 && object.getY() == 0x660);
        if (!doorExists) {
            requireCreated(objects().createDynamicObject(() -> new FbzExitDoorInstance(
                    new ObjectSpawn(0x3680, 0x660, Sonic3kObjectIds.FBZ_EXIT_DOOR,
                            0, 0, false, 0x660))), "FBZ exit door record");
        }
    }

    private ObjectManager objects() {
        ObjectManager objects = GameServices.level().getObjectManager();
        if (objects == null) throw new IllegalStateException("FBZ visual scenario has no object manager");
        return objects;
    }

    private <T> T findLive(Class<T> type) {
        return objects().getActiveObjects().stream().filter(type::isInstance).map(type::cast)
                .findFirst().orElse(null);
    }

    private static <T> T requireCreated(T value, String label) {
        if (value == null) throw new IllegalStateException(label + " could not allocate an SST slot");
        return value;
    }

    @SuppressWarnings("unchecked")
    private static int objectCount(FbzVisualStateProbe.Snapshot state, String simpleName) {
        Object types = state.values().get("active_object_types");
        if (!(types instanceof Map<?, ?> counts)) {
            throw new IllegalStateException("FBZ state probe did not publish active_object_types");
        }
        Object count = counts.get(simpleName);
        return count instanceof Number number ? number.intValue() : 0;
    }

    private static void requireObjectCountAtLeast(FbzVisualStateProbe.Snapshot state,
                                                   String simpleName, int minimum) {
        int actual = objectCount(state, simpleName);
        if (actual < minimum) {
            throw new IllegalStateException("FBZ object graph requires at least " + minimum + " "
                    + simpleName + " objects, got " + actual);
        }
    }

    private void executeSeamlessTransition(List<Map<String, Object>> observations) {
        Sonic3kFBZEvents oldEvents = events();
        var player = GameServices.camera().getFocusedSprite();
        oldEvents.updateAct1BackgroundEvent(player.getCentreX(), player.getCentreY(), false);
        FbzVisualStateProbe.Snapshot reloaded = port.snapshot();
        requireEquals(reloaded, "zone", 4);
        requireEquals(reloaded, "act", 2);
        requireEquals(reloaded, "player_x", 0x60);
        observations.add(observation("synchronous-act2-reload", reloaded));
        session.stepFrames(1);
        observations.add(observation("first-complete-act2-frame", port.snapshot()));
    }

    private void executeAct2Boundary(List<Map<String, Object>> observations) {
        Sonic3kFBZEvents events = events();
        var player = GameServices.camera().getFocusedSprite();
        events.updateAct2ScreenEvent(player.getCentreX(), player.getCentreY(), false,
                GameServices.camera().getX() & 0xFFFF);
        requireEquals(port.snapshot(), "foreground_region", 4);

        port.write("player_y", 0xA40);
        advanceAct2Event();
        requireTransientStage("act2-forward", 4, observations);
        drainAct2Redraw("act2-forward", observations);

        new FbzVisualFixture(port).applyVerified(new FbzVisualFixture.Mutation(
                Map.of("events_routine_bg", 4),
                Map.of("foreground_outdoor", true, "background_outdoor", true)));
        port.write("player_y", 0xA40);
        advanceAct2Event();
        requireTransientStage("act2-reverse", 4, observations);
        drainAct2Redraw("act2-reverse", observations);

        port.write("player_y", 0xA3F);
        session.stepFrames(1);
        FbzVisualStateProbe.Snapshot settled = port.snapshot();
        requireEquals(settled, "events_routine_bg", 4);
        requireEquals(settled, "background_outdoor", false);
        observations.add(observation("act2-settled", settled));
    }

    private void executePlaneEntry(List<Map<String, Object>> observations) {
        FbzVisualStateProbe.Snapshot allocated = port.snapshot();
        requireEquals(allocated, "boss_event_setup_attempted", true);
        requireEquals(allocated, "cloud_identity_count", 10);
        observations.add(observation("boss-event-graph-allocated", allocated));
        session.stepFrames(1);
        FbzVisualStateProbe.Snapshot transitioned = port.snapshot();
        requireEquals(transitioned, "events_routine_fg", 4);
        requireEquals(transitioned, "events_routine_bg", 16);
        observations.add(observation("plane-entry-complete", transitioned));
    }

    private void executePlaneSteady(List<Map<String, Object>> observations) {
        session.stepFrames(1);
        FbzVisualStateProbe.Snapshot state = port.snapshot();
        requireEquals(state, "events_routine_fg", 4);
        requireEquals(state, "events_routine_bg", 16);
        requireEquals(state, "plane_assignment", "FG=B,BG=A");
        requireEquals(state, "collision_mode", "FOREGROUND_AND_BACKGROUND");
        requireEquals(state, "cloud_identity_count", 10);
        observations.add(observation("plane-reversal-steady", state));
    }

    private void executeAniPlc(FbzVisualScenarioDriver.ScenarioPlan plan,
                               FbzVisualEvidenceAmendment amendment,
                               List<Map<String, Object>> observations,
                               List<CadenceFrame> cadenceFrames) {
        FbzVisualEvidenceAmendment.VisibleRegion region =
                amendment.requireApprovedCadenceSeries(plan.checkpointId());
        FbzVisualCadenceCapture.Spec spec = FbzVisualCadenceCapture.spec(plan.checkpointId());
        int channel = plan.recipe().setup().path("timers").path("ani_channel").asInt(-1);
        if (channel != spec.channel()) {
            throw new IllegalStateException("FBZ cadence channel disagrees with reviewed destination");
        }
        FbzVisualStateProbe.Snapshot zeroStep = session.captureState();
        FbzVisualVisibilityVerifier.verifyState(zeroStep.values());
        observations.add(cadenceObservation("zero-step", channel, zeroStep, zeroStep));
        HiddenGlCaptureSession.CapturedImages previousImages = session.renderAndCapture();
        cadenceFrames.add(new CadenceFrame(0, "zero-step", zeroStep, zeroStep, previousImages,
                FbzVisualCadenceCapture.hashDestinationPatterns(spec,
                        GameServices.level().getCurrentLevel()), false));

        boolean naturalExpiry = false;
        int captured = 1;
        for (int i = 0; i < spec.resetTimer() + 4; i++) {
            FbzVisualStateProbe.Snapshot before = session.captureState();
            session.stepFrames(1);
            FbzVisualStateProbe.Snapshot after = session.captureState();
            FbzVisualVisibilityVerifier.verifyState(after.values());
            observations.add(cadenceObservation("one-step", channel, before, after));
            captured++;
            HiddenGlCaptureSession.CapturedImages images = session.renderAndCapture();
            boolean regionChanged = FbzVisualCadenceCapture.reviewedRegionChanged(
                    previousImages.nativeCrop(), images.nativeCrop(), region);
            cadenceFrames.add(new CadenceFrame(captured - 1, "one-step", before, after, images,
                    FbzVisualCadenceCapture.hashDestinationPatterns(spec,
                            GameServices.level().getCurrentLevel()), regionChanged));
            previousImages = images;
            int timerBefore = stateInt(before, "aniplc_timer_" + channel);
            int frameBefore = stateInt(before, "aniplc_frame_" + channel);
            int frameAfter = stateInt(after, "aniplc_frame_" + channel);
            if (timerBefore == 0 && frameAfter != frameBefore) {
                naturalExpiry = true;
            }
            if (naturalExpiry && captured >= 5) return;
        }
        throw new IllegalStateException("FBZ AniPLC channel " + channel
                + " did not provide five natural-cadence frames spanning an expiry");
    }

    private static Map<String, Object> cadenceObservation(String control, int channel,
                                                           FbzVisualStateProbe.Snapshot before,
                                                           FbzVisualStateProbe.Snapshot after) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("phase", "aniplc-cadence");
        observation.put("control", control);
        observation.put("channel", channel);
        observation.put("timer_before", stateInt(before, "aniplc_timer_" + channel));
        observation.put("frame_before", stateInt(before, "aniplc_frame_" + channel));
        observation.put("timer_after", stateInt(after, "aniplc_timer_" + channel));
        observation.put("frame_after", stateInt(after, "aniplc_frame_" + channel));
        observation.put("state", after.values());
        return Map.copyOf(observation);
    }

    private static int stateInt(FbzVisualStateProbe.Snapshot state, String key) {
        Object value = state.values().get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("FBZ cadence state lacks numeric " + key);
        }
        return number.intValue();
    }

    private void executeAct1Boundary(FbzVisualScenarioDriver.ScenarioPlan plan,
                                     List<Map<String, Object>> observations) {
        Boundary boundary = Boundary.forCheckpoint(plan.checkpointId());
        GameServices.camera().updatePosition(true);

        writeCrossing(boundary, boundary.forwardCoordinate());
        advanceAct1Event();
        requireTransient("forward", observations);
        drainRedraw("forward", observations);

        new FbzVisualFixture(port).applyVerified(new FbzVisualFixture.Mutation(
                Map.of("events_routine_bg", 0),
                Map.of("foreground_outdoor", true, "background_outdoor", true)));
        writeCrossing(boundary, boundary.reverseCoordinate());
        advanceAct1Event();
        requireTransient("reverse", observations);
        drainRedraw("reverse", observations);

        writeCrossing(boundary, boundary.safeIndoorCoordinate());
        session.stepFrames(1);
        FbzVisualStateProbe.Snapshot finalState = port.snapshot();
        requireEquals(finalState, "events_routine_bg", 0);
        requireEquals(finalState, "background_outdoor", false);
        observations.add(observation("settled", finalState));
    }

    private void writeCrossing(Boundary boundary, int coordinate) {
        String key = boundary.axis() == Axis.X ? "player_x" : "player_y";
        port.write(key, coordinate);
        Object actual = port.snapshot().values().get(key);
        if (!Integer.valueOf(coordinate).equals(actual)) {
            throw new IllegalStateException("FBZ boundary coordinate readback mismatch for " + key
                    + ": expected " + coordinate + ", got " + actual);
        }
    }

    private void advanceAct1Event() {
        Sonic3kFBZEvents events = events();
        int frame = GameServices.level().getFrameCounter();
        var player = GameServices.camera().getFocusedSprite();
        events.updateAct1Frame(player.getCentreX(), player.getCentreY(), false, frame);
        GameServices.level().setFrameCounter(frame + 1);
    }

    private void advanceAct2Event() {
        Sonic3kFBZEvents events = events();
        int frame = GameServices.level().getFrameCounter();
        var player = GameServices.camera().getFocusedSprite();
        events.updateAct2ScreenEvent(player.getCentreX(), player.getCentreY(), false,
                GameServices.camera().getX() & 0xFFFF);
        events.updateAct2BackgroundEvent(player.getCentreX(), player.getCentreY(), false);
        GameServices.level().setFrameCounter(frame + 1);
    }

    private void requireTransient(String phase, List<Map<String, Object>> observations) {
        FbzVisualStateProbe.Snapshot state = port.snapshot();
        int stage = ((Number) state.values().get("events_routine_bg")).intValue();
        if (stage == 0) {
            throw new IllegalStateException("FBZ " + phase + " crossing did not enter a redraw stage");
        }
        observations.add(observation(phase + "-transient", state));
    }

    private void drainRedraw(String phase, List<Map<String, Object>> observations) {
        for (int i = 0; i < MAX_REDRAW_PHASES; i++) {
            FbzVisualStateProbe.Snapshot state = port.snapshot();
            if (((Number) state.values().get("events_routine_bg")).intValue() == 0) {
                observations.add(observation(phase + "-complete", state));
                return;
            }
            advanceAct1Event();
        }
        throw new IllegalStateException("FBZ " + phase + " redraw did not complete within "
                + MAX_REDRAW_PHASES + " event phases");
    }

    private void requireTransientStage(String phase, int settledStage,
                                       List<Map<String, Object>> observations) {
        FbzVisualStateProbe.Snapshot state = port.snapshot();
        int stage = ((Number) state.values().get("events_routine_bg")).intValue();
        if (stage == settledStage) {
            throw new IllegalStateException("FBZ " + phase + " crossing did not enter a redraw stage");
        }
        observations.add(observation(phase + "-transient", state));
    }

    private void drainAct2Redraw(String phase, List<Map<String, Object>> observations) {
        for (int i = 0; i < MAX_REDRAW_PHASES; i++) {
            FbzVisualStateProbe.Snapshot state = port.snapshot();
            if (((Number) state.values().get("events_routine_bg")).intValue() == 4) {
                observations.add(observation(phase + "-complete", state));
                return;
            }
            advanceAct2Event();
        }
        throw new IllegalStateException("FBZ " + phase + " redraw did not complete within "
                + MAX_REDRAW_PHASES + " event phases");
    }

    private static Map<String, Object> observation(String phase, FbzVisualStateProbe.Snapshot state) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("phase", phase);
        observation.put("state", state.values());
        return Map.copyOf(observation);
    }

    private static void requireEquals(FbzVisualStateProbe.Snapshot state, String key, Object expected) {
        Object actual = state.values().get(key);
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException("FBZ final state mismatch for " + key
                    + ": expected " + expected + ", got " + actual);
        }
    }

    private static Sonic3kFBZEvents events() {
        if (!(GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager)
                || manager.getFbzEvents() == null) {
            throw new IllegalStateException("FBZ scenario executor requires active FBZ events");
        }
        return manager.getFbzEvents();
    }

    public record Execution(FbzVisualStateProbe.Snapshot pre,
                            FbzVisualStateProbe.Snapshot post,
                            List<Map<String, Object>> observations,
                            List<CadenceFrame> cadenceFrames) {
    }

    public record CadenceFrame(int index, String control,
                               FbzVisualStateProbe.Snapshot before,
                               FbzVisualStateProbe.Snapshot after,
                               HiddenGlCaptureSession.CapturedImages images,
                               String vramSha256,
                               boolean reviewedVisibleRegionChanged) {
    }

    private enum Axis { X, Y }

    private record Boundary(Axis axis, int forwardCoordinate,
                            int reverseCoordinate, int safeIndoorCoordinate) {
        private static Boundary forCheckpoint(String checkpoint) {
            return switch (checkpoint) {
                case "fbz1-boundary-1-outdoor" -> new Boundary(Axis.Y, 0x9C0, 0x9C0, 0x9BF);
                case "fbz1-boundary-2-outdoor" -> new Boundary(Axis.Y, 0x2C0, 0x2C0, 0x2C1);
                case "fbz1-boundary-3-outdoor" -> new Boundary(Axis.Y, 0x9C0, 0x9C0, 0x9BF);
                case "fbz1-boundary-4-horizontal" -> new Boundary(Axis.X, 0x1B00, 0x1B00, 0x1AFF);
                case "fbz1-boundary-5-outdoor" -> new Boundary(Axis.Y, 0x240, 0x240, 0x241);
                case "fbz1-boundary-6-outdoor" -> new Boundary(Axis.Y, 0x640, 0x640, 0x63F);
                default -> throw new IllegalArgumentException("Not an FBZ Act 1 boundary: " + checkpoint);
            };
        }
    }
}
