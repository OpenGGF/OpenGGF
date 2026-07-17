package com.openggf.tools.fbzvisual;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.objects.Fbz2SubbossInstance;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.game.sonic3k.objects.FbzMinibossInstance;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed, read-only capture of the FBZ runtime fields required by receipts. */
public final class FbzVisualStateProbe {

    private FbzVisualStateProbe() {
    }

    public static Snapshot captureRuntime() {
        return capture(new GameServicesRuntimeView());
    }

    public static Snapshot capture(RuntimeView runtime) {
        Objects.requireNonNull(runtime, "runtime");
        PlayerState player = Objects.requireNonNull(runtime.player(), "player");
        CameraState camera = Objects.requireNonNull(runtime.camera(), "camera");
        EventState events = Objects.requireNonNull(runtime.events(), "events");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("zone", runtime.zone());
        values.put("act", runtime.oneBasedAct());
        values.put("player_x", player.x());
        values.put("player_y", player.y());
        values.put("camera_x", camera.x());
        values.put("camera_y", camera.y());
        values.put("camera_x_copy", camera.xCopy());
        values.put("camera_y_copy", camera.yCopy());
        values.put("camera_min_x", camera.minX());
        values.put("camera_max_x", camera.maxX());
        values.put("camera_min_y", camera.minY());
        values.put("camera_max_y", camera.maxY());
        values.put("camera_target_min_x", camera.targetMinX());
        values.put("camera_target_max_x", camera.targetMaxX());
        values.put("camera_target_min_y", camera.targetMinY());
        values.put("camera_target_max_y", camera.targetMaxY());
        values.put("events_routine_fg", events.foregroundStage());
        values.put("events_routine_bg", events.backgroundStage());
        values.put("foreground_region", events.foregroundRegion());
        values.put("foreground_outdoor", events.foregroundOutdoor());
        values.put("background_outdoor", events.backgroundOutdoor());
        values.put("plane_assignment", events.planeAssignment());
        values.put("collision_mode", events.collisionMode());
        values.put("screen_shake_active", events.screenShakeActive());
        values.put("screen_shake_offset", events.screenShakeOffset());
        values.put("boss_active", runtime.bossActive());
        values.put("boss_defeated", runtime.bossDefeated());
        values.put("level_frame_counter", runtime.levelFrameCounter());
        values.put("rng_seed", runtime.rngSeed());
        values.put("rng_state", String.format("0x%08X:%s",
                runtime.rngSeed() & 0xFFFFFFFFL, runtime.rngFlavour()));
        addAnimationState(values);
        addVisibilityState(values);
        addRuntimeDetails(values);
        return new Snapshot(values);
    }

    private static void addAnimationState(Map<String, Object> values) {
        try {
            if (GameServices.level().getAnimatedPatternManager()
                    instanceof Sonic3kLevelAnimationManager animation) {
                PatternAnimatorSnapshot.ScriptCounter[] counters = animation.capture().scriptCounters();
                List<Integer> rawCounters = new ArrayList<>(Collections.nCopies(16, 0));
                for (int i = 0; i < counters.length; i++) {
                    values.put("aniplc_timer_" + i, counters[i].timer());
                    values.put("aniplc_frame_" + i, counters[i].frameIndex());
                    if (i < 8) {
                        rawCounters.set(i * 2, counters[i].timer() & 0xFF);
                        rawCounters.set(i * 2 + 1, counters[i].frameIndex() & 0xFF);
                    }
                }
                values.put("raw_anim_counters", List.copyOf(rawCounters));
            }
        } catch (RuntimeException ignored) {
            // Synthetic RuntimeView contracts intentionally have no gameplay service context.
        }
    }

    private static void addVisibilityState(Map<String, Object> values) {
        try {
            values.put("gameplay_context_active", SessionManager.getCurrentGameplayMode() != null);
            TitleCardProvider titleCard = GameServices.module().getTitleCardProvider();
            values.put("title_card_overlay_active", titleCard != null && titleCard.isOverlayActive());
            values.put("title_card_complete", titleCard == null || titleCard.isComplete());
            if (titleCard instanceof Sonic3kTitleCardManager manager) {
                values.put("title_card_state", manager.getStateName());
                values.put("title_card_timer", manager.getStateTimer());
            } else {
                values.put("title_card_state", titleCard == null ? "NONE" : titleCard.getClass().getSimpleName());
                values.put("title_card_timer", 0);
            }
            var fade = GameServices.fade();
            values.put("fade_active", fade.isActive());
            values.put("fade_state", fade.getState().name());
            values.put("fade_alpha", fade.getFadeAlpha());
            values.put("fade_frame_count", fade.getFrameCount());
            var config = GameServices.configuration();
            boolean overlaysDisabled = !config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED)
                    && !config.getBoolean(SonicConfiguration.DEBUG_COLLISION_VIEW_ENABLED)
                    && !config.getBoolean(SonicConfiguration.EDITOR_ENABLED)
                    && !config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)
                    && !config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED);
            values.put("overlays_disabled", overlaysDisabled);
        } catch (RuntimeException ignored) {
            // Synthetic RuntimeView contracts intentionally have no gameplay service context.
        }
    }

    private static void addRuntimeDetails(Map<String, Object> values) {
        try {
            if (!(GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager)
                    || manager.getFbzEvents() == null) return;
            Sonic3kFBZEvents events = manager.getFbzEvents();
            values.put("events_fg_5", events.isEventsFg5());
            values.put("boss_event_setup_attempted", events.isBossEventSetupAttempted());
            values.put("boss_load_position_adjustment_pending",
                    events.isBossLoadPositionAdjustmentPending());
            values.put("boss_background_x", events.getBossBackgroundOffsetX());
            values.put("boss_background_y", events.getBossBackgroundOffsetY());
            values.put("screen_shake_phase", events.getScreenShakePhase());
            values.put("collision_camera_diff_x", events.getCollisionCameraDiffX());
            values.put("collision_camera_diff_y", events.getCollisionCameraDiffY());
            long cloudCount = events.getCloudRewindIds().stream().filter(Objects::nonNull).count();
            values.put("cloud_identity_count", (int) cloudCount);

            var mode = SessionManager.getCurrentGameplayMode();
            if (mode != null) {
                var queue = mode.getKosinskiModuleQueue();
                values.put("kosm_queue_idle", queue.isIdle());
                values.put("kosm_queue_phase", queue.phase().name());
                values.put("kosm_queue_archive_count", queue.capture().archives().size());
            }

            var render = GameServices.level().getObjectRenderManager();
            if (render != null) {
                values.put("miniboss_art_ready", ready(render, Sonic3kObjectArtKeys.FBZ_MINIBOSS));
                values.put("subboss_art_ready", ready(render, Sonic3kObjectArtKeys.FBZ2_SUBBOSS));
                values.put("end_boss_art_ready", ready(render, Sonic3kObjectArtKeys.FBZ_END_BOSS));
                values.put("exit_door_art_ready", ready(render, Sonic3kObjectArtKeys.FBZ_EXIT_DOOR));
                values.put("exit_hall_art_ready", ready(render, Sonic3kObjectArtKeys.FBZ_EXIT_HALL));
                values.put("egg_capsule_art_ready", ready(render, Sonic3kObjectArtKeys.EGG_CAPSULE));
            }

            Map<String, Integer> objectTypes = new java.util.TreeMap<>();
            for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                objectTypes.merge(object.getClass().getSimpleName(), 1, Integer::sum);
                if (object instanceof FbzMinibossInstance miniboss) {
                    values.put("miniboss_phase", miniboss.phaseName());
                    values.put("miniboss_remaining_hits", miniboss.remainingHits());
                } else if (object instanceof Fbz2SubbossInstance subboss) {
                    values.put("subboss_phase", subboss.phaseName());
                    values.put("subboss_collision_property", subboss.getCollisionProperty());
                } else if (object instanceof FbzEndBossInstance boss) {
                    values.put("end_boss_phase", boss.phase().name());
                    values.put("end_boss_remaining_hits", boss.getCollisionProperty());
                    values.put("end_boss_arm_count", boss.arms().size());
                    values.put("end_boss_chain_count", boss.chainLinks().size());
                }
            }
            values.put("active_object_types", Collections.unmodifiableMap(objectTypes));
        } catch (RuntimeException ignored) {
            // Synthetic RuntimeView contracts intentionally have no gameplay service context.
        }
    }

    private static boolean ready(com.openggf.level.objects.ObjectRenderManager render, String key) {
        var renderer = render.getRenderer(key);
        return renderer != null && renderer.isReady();
    }

    public interface RuntimeView {
        int zone();
        int oneBasedAct();
        PlayerState player();
        CameraState camera();
        EventState events();
        boolean bossActive();
        default boolean bossDefeated() { return false; }
        int levelFrameCounter();
        long rngSeed();
        String rngFlavour();
    }

    public record PlayerState(int x, int y) {
    }

    public record CameraState(
            int x, int y, int xCopy, int yCopy,
            int minX, int maxX, int minY, int maxY,
            int targetMinX, int targetMaxX, int targetMinY, int targetMaxY) {
    }

    public record EventState(
            int foregroundStage,
            int backgroundStage,
            int foregroundRegion,
            boolean foregroundOutdoor,
            boolean backgroundOutdoor,
            String planeAssignment,
            String collisionMode,
            boolean screenShakeActive,
            int screenShakeOffset) {
    }

    public record Snapshot(Map<String, Object> values) {
        public Snapshot {
            Objects.requireNonNull(values, "values");
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private static final class GameServicesRuntimeView implements RuntimeView {
        private Sonic3kFBZEvents fbzEvents() {
            if (!(GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager)) {
                throw new IllegalStateException("FBZ visual probe requires Sonic3kLevelEventManager");
            }
            Sonic3kFBZEvents events = manager.getFbzEvents();
            if (events == null) {
                throw new IllegalStateException("FBZ visual probe used outside FBZ");
            }
            return events;
        }

        @Override public int zone() { return GameServices.level().getCurrentZone(); }
        @Override public int oneBasedAct() { return GameServices.level().getCurrentAct() + 1; }

        @Override
        public PlayerState player() {
            AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
            if (player == null) throw new IllegalStateException("FBZ visual probe has no focused player");
            return new PlayerState(player.getCentreX(), player.getCentreY());
        }

        @Override
        public CameraState camera() {
            Camera camera = GameServices.camera();
            return new CameraState(
                    camera.getX() & 0xFFFF, camera.getY() & 0xFFFF,
                    camera.getXCopy() & 0xFFFF, camera.getYCopy() & 0xFFFF,
                    camera.getMinX() & 0xFFFF, camera.getMaxX() & 0xFFFF,
                    camera.getMinY() & 0xFFFF, camera.getMaxY() & 0xFFFF,
                    camera.getMinXTarget() & 0xFFFF, camera.getMaxXTarget() & 0xFFFF,
                    camera.getMinYTarget() & 0xFFFF, camera.getMaxYTarget() & 0xFFFF);
        }

        @Override
        public EventState events() {
            Sonic3kFBZEvents events = fbzEvents();
            boolean act2 = events.getAct() == 1;
            return new EventState(
                    act2 ? events.getAct2ForegroundStage() : 0,
                    act2 ? events.getBossBackgroundStage() : events.getBackgroundRedrawStage(),
                    events.getForegroundLayoutRegion(),
                    events.isForegroundOutdoor(), events.isBackgroundOutdoor(),
                    events.getPlaneAssignmentMode() == Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED
                            ? "FG=B,BG=A" : "FG=A,BG=B",
                    events.getCollisionMode().name(),
                    events.isScreenShakeActive(), events.getScreenShakeOffset());
        }

        @Override public boolean bossActive() { return GameServices.gameState().isBossFightActive(); }
        @Override public boolean bossDefeated() { return GameServices.gameState().isBossDefeatedFlag(); }
        @Override public int levelFrameCounter() { return GameServices.level().getFrameCounter(); }
        @Override public long rngSeed() { return GameServices.rng().getSeed(); }
        @Override public String rngFlavour() {
            GameRng rng = GameServices.rng();
            return rng.flavour().name();
        }
    }
}
