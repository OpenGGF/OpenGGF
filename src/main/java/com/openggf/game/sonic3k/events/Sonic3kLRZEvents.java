package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.render.LrzRockSpriteRenderer;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;

/**
 * Lava Reef screen and background events for {@code $900}, {@code $901} and the boss act
 * {@code $1600}.
 *
 * <p>{@code LRZ1_ScreenEvent} (sonic3k.asm:115199-115214) and {@code LRZ2_ScreenEvent}
 * (115670-115673) both start by adding {@code Screen_shake_offset} to {@code Camera_Y_pos_copy},
 * and every background-event exit tail-calls {@code ShakeScreen_Setup} (115318, 115364, 115388,
 * 115682) to produce the next frame's offset. The engine runs that setup once at the head of its
 * screen-event pass, exactly as it does for Hidden Palace, so the word this frame's event and
 * {@code SwScrlLrz} read is the one the previous setup left in
 * {@link LrzZoneRuntimeState#appliedScreenShakeOffset()}.
 *
 * <p>Act 1 also answers {@code Events_bg+$0C}, the rock crusher's chunk-edit request: a negative
 * word selects the {@code $44/$00/$4A} and {@code $3E/$00/$4B} edits at {@code loc_56B2C} and a
 * positive one the single {@code $9C} write, and either way {@code loc_56B54} clears the word and
 * redraws the screen directly. The crusher and those edits are slice 3d; the request word already
 * lives in the runtime state so rewind captures it before the writer exists.
 *
 * <p>Still to arrive, by slice: the {@code LRZ1_BackgroundEvent} stage machine and the seamless
 * {@code $901} handover ({@code loc_56CAA}), the dome regions ({@code sub_56DCA}), and the whole
 * {@code $1600} branch (screen stages, flash sequence, autoscroll, end boss).
 */
public class Sonic3kLRZEvents extends Sonic3kZoneEvents {

    /**
     * {@code ShakeScreen_Setup} followed by {@code LRZ1_ScreenEvent}/{@code LRZ2_ScreenEvent}'s
     * {@code move.w (Screen_shake_offset).w,d0 / add.w d0,(Camera_Y_pos_copy).w}. The copy is the
     * sprite and foreground vertical scroll source, so this is the only place the Lava Reef shake
     * enters the camera.
     *
     * @param frameCounter ROM {@code Level_frame_counter} as {@code ShakeScreen_Setup} reads it
     * @param playerDeadOrRestarting ROM {@code Player_1+routine >= 6}
     */
    public void advanceScreenShakeAndApplyToCameraCopy(int frameCounter,
                                                       boolean playerDeadOrRestarting) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null) {
            return;
        }
        lrz.advanceScreenShake(frameCounter, playerDeadOrRestarting);
        applyScreenEventShake(camera(), lrz.appliedScreenShakeOffset());
    }

    /** {@code add.w d0,(Camera_Y_pos_copy).w}. */
    static void applyScreenEventShake(Camera camera, int screenShakeOffset) {
        camera.setYCopy((short) (camera.getYCopy() + screenShakeOffset));
    }

    @Override
    public void update(int act, int frameCounter) {
        // loc_56B5E / LRZ2_ScreenEvent with no pending chunk edit: DrawTilesAsYouMove only, which
        // the engine's own tile streaming already does. The stage machines arrive with their slices.
        advanceRockSpriteWindow();
    }

    /**
     * {@code LevelLoop} calls {@code Draw_LRZ_Special_Rock_Sprites} once a frame, right after
     * {@code ScreenEvents} and {@code Load_Rings} and only while {@code Current_zone} is 9
     * (sonic3k.asm:7899-7903). It reads {@code Camera_X_pos}, not the copy the renderer later
     * subtracts, and leaves the two placement pointers in the runtime state for
     * {@code sub_1CB68} to walk during {@code Render_Sprites}.
     */
    private void advanceRockSpriteWindow() {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ) {
            return;
        }
        LrzRockSpriteRenderer renderer = rockSpriteRenderer();
        if (renderer == null) {
            return;
        }
        renderer.advanceWindow(lrz, camera().getX());
    }

    private LrzRockSpriteRenderer rockSpriteRenderer() {
        return levelManager() != null
                && levelManager().getZoneFeatureProvider()
                        instanceof Sonic3kZoneFeatureProvider provider
                ? provider.lrzRockSpriteRenderer()
                : null;
    }

    private LrzZoneRuntimeState state() {
        return hasRuntime()
                ? S3kRuntimeStates.currentLrz(zoneRuntimeRegistry()).orElse(null)
                : null;
    }
}
