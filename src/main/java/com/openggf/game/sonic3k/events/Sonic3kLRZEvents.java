package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.objects.LrzDomeLavaPlatformObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.render.LrzRockSpriteRenderer;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;

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

    /**
     * {@code LRZ1_ScreenEvent}'s chunk edits (sonic3k.asm:115201-115224). {@code a3} is
     * {@code Level_layout_main} (the {@code ScreenEvents} preamble at :102237 loads it), whose
     * entries are longs -- {@code Layout_row_index_mask} is {@code $7C} (:102207) -- so the
     * {@code movea.w} at {@code $38(a3)}, {@code $3C(a3)} and {@code $40(a3)} reads the FOREGROUND
     * row pointer of layout rows 14, 15 and 16, and {@code lea $1D(a1)} indexes column 29 of that
     * row. The rock crusher's own bridge positions confirm the reading independently: subtype 0
     * drops slabs at {@code ($F00,$760)} and {@code ($F80,$760)}, which are columns 30 and 31 of
     * row 14, and the other subtype at {@code ($540,$860)}, column 10 of row 16.
     *
     * <p>The engine calls the ROM's {@code $80 x $80} unit a <em>block</em>, so each write is one
     * {@code setBlockInMap(0, column, row, id)} on the foreground layer.
     */
    private static final int CRUSHER_EDIT_FIRST_COLUMN = 29;
    private static final int CRUSHER_EDIT_ROW_A = 14;
    private static final int CRUSHER_EDIT_ROW_B = 15;
    private static final int CRUSHER_EDIT_ROW_C = 16;
    /** {@code move.b #$44 / #0 / #$4A} at {@code loc_56B2C} (:115211-115214). */
    private static final int[] CRUSHER_EDIT_ROW_A_IDS = {0x44, 0x00, 0x4A};
    /** {@code move.b #$3E / #0 / #$4B} at {@code loc_56B2C} (:115216-115219). */
    private static final int[] CRUSHER_EDIT_ROW_B_IDS = {0x3E, 0x00, 0x4B};
    /** {@code move.b #$9C,$A(a1)} on the positive branch (:115207). */
    private static final int CRUSHER_EDIT_POSITIVE_COLUMN = 10;
    private static final int CRUSHER_EDIT_POSITIVE_ID = 0x9C;

    /**
     * {@code LRZ1_BackgroundInit} (sonic3k.asm:115239-115242): with {@code Player_mode} 3
     * (Knuckles) it takes the background layout's row 1 pointer and writes {@code $F6}
     * ({@code move.b #-$A,4(a1)}) into its column 4 -- one chunk, once, before the first
     * deformation. Everything else in that routine is the row-0 repeat the engine's plane period
     * already gives and the first {@code LRZ1_Deform}.
     */
    private static final int KNUCKLES_BG_CHUNK_LAYER = 1;
    private static final int KNUCKLES_BG_CHUNK_ROW = 1;
    private static final int KNUCKLES_BG_CHUNK_COLUMN = 4;
    private static final int KNUCKLES_BG_CHUNK_ID = 0xF6;

    private boolean act1BackgroundInitialised;

    @Override
    public void update(int act, int frameCounter) {
        // LRZ1_ScreenEvent reads Events_bg+$0C before anything else draws (:115201-115204); with
        // no pending edit it is loc_56B5E, DrawTilesAsYouMove only, which the engine's own tile
        // streaming already does. The stage machines arrive with their slices.
        applyBackgroundInit(act);
        applyPendingChunkEdit(act);
        advanceDomeRegions(act);
        advanceRockSpriteWindow();
    }

    /**
     * {@code sub_56DCA} (sonic3k.asm:115457-115497), which {@code LRZ1_BackgroundEvent} runs from
     * both its stage 0 ({@code loc_56C28}) and its stage 4 ({@code loc_56C6E}).
     *
     * <p>What lands here is the state half: {@code Events_bg+$00} and {@code Obj_56EA0}. The
     * background half of {@code loc_56E40}/{@code loc_56E66} -- {@code Events_routine_bg}
     * stepping to 4 and then 8, {@code Reset_TileOffsetPositionEff}, the saved
     * {@code Events_bg+$02/$04} camera copies and the {@code Draw_delayed_rowcount $F} bottom-up
     * refresh on the way out -- is not wired yet and is recorded as owed in the plan.
     */
    private void advanceDomeRegions(int act) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ || act != 0) {
            return;
        }
        AbstractPlayableSprite player1 = spriteManager() != null
                ? spriteManager().getMainPlayable() : null;
        if (player1 == null) {
            return;
        }
        LrzBackgroundStageMachine.advance(lrz, player1.getCentreX(), player1.getCentreY(),
                this::spawnDomeLavaPlatform);
    }

    /** {@code LRZ1_BackgroundInit}'s Knuckles-only background chunk, run once per act 1 load. */
    private void applyBackgroundInit(int act) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ || act != 0) {
            return;
        }
        if (act1BackgroundInitialised) {
            return;
        }
        act1BackgroundInitialised = true;
        if (lrz.playerCharacter() != PlayerCharacter.KNUCKLES) {
            return;
        }
        applyLayoutWrite(KNUCKLES_BG_CHUNK_LAYER, KNUCKLES_BG_CHUNK_ROW,
                KNUCKLES_BG_CHUNK_COLUMN, KNUCKLES_BG_CHUNK_ID);
    }

    private void spawnDomeLavaPlatform() {
        ObjectManager objects = levelManager() != null ? levelManager().getObjectManager() : null;
        if (objects == null) {
            return;
        }
        boolean alreadyLive = objects.getActiveObjects().stream()
                .anyMatch(o -> o instanceof LrzDomeLavaPlatformObjectInstance);
        if (alreadyLive) {
            return;
        }
        objects.createDynamicObject(() -> new LrzDomeLavaPlatformObjectInstance(
                new ObjectSpawn(LrzDomeLavaPlatformObjectInstance.FIXED_X,
                        LrzDomeLavaPlatformObjectInstance.SURFACE_BASE_Y,
                        0, 0, 0, false, 0)));
    }

    /**
     * {@code LRZ1_ScreenEvent} {@code loc_56B2C}/{@code loc_56B54} (sonic3k.asm:115201-115224).
     * {@code tst.w} on the request word: zero does nothing, negative takes the two-row opening,
     * and positive the single {@code $9C} write. Either way {@code loc_56B54} clears the word and
     * redraws the screen directly, which is what consuming the request and letting the mutation
     * pipeline publish a foreground redraw does here.
     */
    private void applyPendingChunkEdit(int act) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ || act != 0) {
            return;
        }
        short request = (short) lrz.chunkEditRequest();
        if (request == 0) {
            return;
        }
        lrz.consumeChunkEditRequest();
        if (request < 0) {
            applyLayoutWrites(CRUSHER_EDIT_ROW_A, CRUSHER_EDIT_ROW_A_IDS);
            applyLayoutWrites(CRUSHER_EDIT_ROW_B, CRUSHER_EDIT_ROW_B_IDS);
            return;
        }
        applyLayoutWrite(0, CRUSHER_EDIT_ROW_C, CRUSHER_EDIT_POSITIVE_COLUMN,
                CRUSHER_EDIT_POSITIVE_ID);
    }

    private void applyLayoutWrites(int row, int[] ids) {
        for (int i = 0; i < ids.length; i++) {
            applyLayoutWrite(0, row, CRUSHER_EDIT_FIRST_COLUMN + i, ids[i]);
        }
    }

    private void applyLayoutWrite(int layer, int row, int column, int blockId) {
        LevelManager manager = levelManager();
        Level level = manager != null ? manager.getCurrentLevel() : null;
        if (level == null) {
            return;
        }
        LayoutMutationContext context = new LayoutMutationContext(
                LevelMutationSurface.forLevel(level), manager::applyMutationEffects);
        zoneLayoutMutationPipeline().applyImmediately(
                ctx -> ctx.surface().setBlockInMap(layer, column, row, blockId), context);
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
