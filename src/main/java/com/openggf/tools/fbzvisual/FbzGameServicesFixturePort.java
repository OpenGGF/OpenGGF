package com.openggf.tools.fbzvisual;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kLevelAnimationManager;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Locale;

/**
 * Validation-only FBZ fixture adapter backed by the active gameplay services.
 *
 * <p>The adapter deliberately exposes a small typed vocabulary. Unknown keys
 * are rejected rather than silently becoming inert recipe metadata, and every
 * write is subsequently checked by {@link FbzVisualFixture} through the public
 * read-only probe.</p>
 */
public final class FbzGameServicesFixturePort implements FbzVisualFixturePort {

    @Override
    public FbzVisualStateProbe.Snapshot snapshot() {
        return FbzVisualStateProbe.captureRuntime();
    }

    @Override
    public void write(String key, Object value) {
        switch (key) {
            case "player_x" -> NativePositionOps.writeXPosResetSubpixel(player(), number(key, value));
            case "player_y" -> NativePositionOps.writeYPosResetSubpixel(player(), number(key, value));
            case "camera_x" -> camera().setX(word(key, value));
            case "camera_y" -> camera().setY(word(key, value));
            case "camera_min_x" -> camera().setMinXCurrent(word(key, value));
            case "camera_max_x" -> camera().setMaxXCurrent(word(key, value));
            case "camera_min_y" -> camera().setMinYCurrent(word(key, value));
            case "camera_max_y" -> camera().setMaxYCurrent(word(key, value));
            case "camera_target_min_x" -> camera().setMinXTarget(word(key, value));
            case "camera_target_max_x" -> camera().setMaxXTarget(word(key, value));
            case "camera_target_min_y" -> camera().setMinYTarget(word(key, value));
            case "camera_target_max_y" -> camera().setMaxYTarget(word(key, value));
            case "events_routine_fg" -> events().setAct2ForegroundStage(number(key, value));
            case "events_routine_bg" -> writeBackgroundStage(number(key, value));
            case "foreground_region" -> events().setForegroundLayoutRegion(number(key, value));
            case "foreground_outdoor" -> events().setForegroundOutdoor(bool(key, value));
            case "background_outdoor" -> events().setBackgroundOutdoor(bool(key, value));
            case "plane_assignment" -> events().setPlaneAssignmentMode(plane(value));
            case "collision_mode" -> writeCollisionMode(value);
            case "screen_shake_active" -> events().setScreenShakeState(
                    bool(key, value), events().getScreenShakeOffset(), events().getScreenShakePhase());
            case "screen_shake_offset" -> events().setScreenShakeState(
                    events().isScreenShakeActive(), number(key, value), events().getScreenShakePhase());
            case "events_fg_5" -> events().setEventsFg5(bool(key, value));
            case "boss_active" -> eventManager().setBossActive(bool(key, value));
            case "boss_defeated" -> GameServices.gameState().setBossDefeatedFlag(bool(key, value));
            case "boss_load_position_adjustment_pending" ->
                    events().setBossLoadPositionAdjustmentPending(bool(key, value));
            case "boss_event_setup_attempted" -> writeBossEventSetup(bool(key, value));
            case "level_frame_counter" -> GameServices.level().setFrameCounter(number(key, value));
            default -> {
                if (key.startsWith("aniplc_timer_") || key.startsWith("aniplc_frame_")) {
                    writeAniPlcCounter(key, number(key, value));
                } else {
                    throw new IllegalArgumentException("Unsupported FBZ visual fixture key: " + key);
                }
            }
        }
    }

    private static void writeAniPlcCounter(String key, int value) {
        if (!(GameServices.level().getAnimatedPatternManager()
                instanceof Sonic3kLevelAnimationManager animation)) {
            throw new IllegalStateException("FBZ visual fixture requires Sonic3kLevelAnimationManager");
        }
        PatternAnimatorSnapshot snapshot = animation.capture();
        PatternAnimatorSnapshot.ScriptCounter[] counters = snapshot.scriptCounters().clone();
        int separator = key.lastIndexOf('_');
        int index;
        try {
            index = Integer.parseInt(key.substring(separator + 1));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid FBZ AniPLC fixture key: " + key, invalid);
        }
        if (index < 0 || index >= counters.length) {
            throw new IllegalArgumentException("FBZ AniPLC channel out of range: " + index);
        }
        PatternAnimatorSnapshot.ScriptCounter old = counters[index];
        counters[index] = key.startsWith("aniplc_timer_")
                ? new PatternAnimatorSnapshot.ScriptCounter(value, old.frameIndex())
                : new PatternAnimatorSnapshot.ScriptCounter(old.timer(), value);
        animation.restore(new PatternAnimatorSnapshot(counters, snapshot.handlerCounters(), snapshot.extra()));
    }

    private static void writeBossEventSetup(boolean requested) {
        if (!requested) {
            throw new IllegalArgumentException("FBZ boss-event setup is forward-only");
        }
        Sonic3kFBZEvents events = events();
        int cameraX = camera().getX() & 0xFFFF;
        if (cameraX < 0x2B30) {
            cameraX = 0x2B30;
            camera().setX((short) cameraX);
        }
        AbstractPlayableSprite player = player();
        events.updateAct2ScreenEvent(player.getCentreX(), player.getCentreY(), false, cameraX);
        if (!events.isBossEventSetupAttempted()) {
            throw new IllegalStateException("FBZ boss-event setup threshold did not allocate the event graph");
        }
    }

    private static AbstractPlayableSprite player() {
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            throw new IllegalStateException("FBZ visual fixture has no focused player");
        }
        return player;
    }

    private static Camera camera() {
        return GameServices.camera();
    }

    private static Sonic3kFBZEvents events() {
        Sonic3kLevelEventManager manager = eventManager();
        Sonic3kFBZEvents events = manager.getFbzEvents();
        if (events == null) {
            throw new IllegalStateException("FBZ visual fixture used outside FBZ");
        }
        return events;
    }

    private static Sonic3kLevelEventManager eventManager() {
        if (!(GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager)) {
            throw new IllegalStateException("FBZ visual fixture requires Sonic3kLevelEventManager");
        }
        return manager;
    }

    private static void writeBackgroundStage(int stage) {
        Sonic3kFBZEvents events = events();
        if (events.getAct() == 0) {
            events.setBackgroundRedraw(stage, events.getBackgroundRedrawDirection());
        } else {
            events.setBossBackgroundState(stage,
                    events.getBossBackgroundOffsetX(), events.getBossBackgroundOffsetY());
        }
    }

    private static void writeCollisionMode(Object value) {
        Sonic3kFBZEvents events = events();
        Sonic3kFBZEvents.CollisionMode mode;
        try {
            mode = Sonic3kFBZEvents.CollisionMode.valueOf(text("collision_mode", value)
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid FBZ collision_mode: " + value, invalid);
        }
        int diffX = mode == Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY
                ? 0 : events.getCollisionCameraDiffX();
        int diffY = mode == Sonic3kFBZEvents.CollisionMode.FOREGROUND_ONLY
                ? 0 : events.getCollisionCameraDiffY();
        events.setCollisionMode(mode, diffX, diffY);
    }

    private static Sonic3kFBZEvents.PlaneAssignmentMode plane(Object value) {
        return switch (text("plane_assignment", value)) {
            case "FG=A,BG=B" -> Sonic3kFBZEvents.PlaneAssignmentMode.NORMAL;
            case "FG=B,BG=A" -> Sonic3kFBZEvents.PlaneAssignmentMode.REVERSED;
            default -> throw new IllegalArgumentException("Invalid FBZ plane_assignment: " + value);
        };
    }

    private static short word(String key, Object value) {
        return (short) number(key, value);
    }

    private static int number(String key, Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("FBZ fixture " + key + " requires a number, got " + value);
        }
        return number.intValue();
    }

    private static boolean bool(String key, Object value) {
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("FBZ fixture " + key + " requires a boolean, got " + value);
        }
        return bool;
    }

    private static String text(String key, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("FBZ fixture " + key + " requires text, got " + value);
        }
        return text;
    }
}
