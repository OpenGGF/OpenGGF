package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.objects.LrzDomeLavaPlatformObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.render.LrzRockSpriteRenderer;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.game.mutation.MutationEffects;

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

    /** {@code move.w #$C,(Events_routine_bg)} at {@code loc_56BD2} (sonic3k.asm:115292). */
    public static final int BG_STAGE_ACT_CHANGE = 0x0C;
    /** {@code ArtKosM_LRZ2_Secondary} (sonic3k.lst: ROM {@code $1B97D6}). */
    private static final int ACT2_SECONDARY_ART_SOURCE = 0x1B97D6;
    /** {@code move.w #tiles_to_bytes($090),d2} at {@code loc_56BD2} (sonic3k.asm:115286). */
    private static final int ACT2_SECONDARY_ART_TILE = 0x090;
    /** {@code moveq #$30,d0 / jsr (Load_PLC)} at {@code loc_56BD2} (sonic3k.asm:115288-115289). */
    private static final int ACT2_PLC = 0x30;
    /** {@code move.w #$2C00,d0} at {@code loc_56CAA} (sonic3k.asm:115361). */
    private static final int ACT2_REBASE_X = 0x2C00;
    /** {@code Dynamic_object_RAM+object_size}, the first slot {@code Offset_ObjectsDuringTransition} walks. */
    private static final int FIRST_ROM_WORLD_OFFSET_SLOT = 4;
    /** {@code Breathing_bubbles}: the {@code dbf} count ends exclusive here. */
    private static final int LAST_ROM_WORLD_OFFSET_SLOT_EXCLUSIVE = 94;

    /** {@code PalLoad_Line1} writes {@code Normal_palette_line_2}: engine line 1. */
    private static final int MINIBOSS_PALETTE_FIRST_LINE = 1;
    /** One Genesis palette line: sixteen words. */
    private static final int PALETTE_LINE_BYTES = 0x20;

    private boolean act1BackgroundInitialised;
    /**
     * {@code LRZ2_BackgroundInit} (sonic3k.asm:115655-115668) runs on a direct {@code $901} load
     * and leaves {@code Events_routine_bg} on 8. The seamless change does not run it at all --
     * {@code loc_56CAA} calls {@code Load_Level}, not the background initializer, and ends on
     * {@code clr.w (Events_routine_bg)} -- so {@link #requestAct2Reload} sets this before the
     * reload and act 2 starts on {@code LRZ2_BackgroundEvent}'s stage 0 instead.
     */
    private boolean act2BackgroundInitialised;
    private boolean act3Initialised;

    @Override
    public void update(int act, int frameCounter) {
        LrzZoneRuntimeState lrz = state();
        if (lrz != null && lrz.zoneIndex() == Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ) {
            updateAct3ScreenAndBackground(lrz);
            return;
        }
        // LRZ1_ScreenEvent reads Events_bg+$0C before anything else draws (:115201-115204); with
        // no pending edit it is loc_56B5E, DrawTilesAsYouMove only, which the engine's own tile
        // streaming already does. The stage machines arrive with their slices.
        applyBackgroundInit(act);
        applyPendingChunkEdit(act);
        if (advanceSeamlessActChange(act)) {
            // loc_56BD2 and loc_56CAA both leave through loc_56D16, so the frame still draws;
            // what they do not do is run sub_56DCA, the dome-region test.
            advanceRockSpriteWindow();
            return;
        }
        advanceDomeRegions(act);
        advanceAct2Background(act);
        advanceRockSpriteWindow();
    }

    /** {@code LRZ3_ScreenInit}, screen stages and the camera halves of the BG stages. */
    private void updateAct3ScreenAndBackground(LrzZoneRuntimeState lrz) {
        if (!act3Initialised) {
            act3Initialised = true;
            AbstractPlayableSprite player = spriteManager().getMainPlayable();
            if (player != null && (player.getCentreX() & 0xFFFF) >= 0x480) {
                Camera camera = camera();
                setCameraPositionAndBounds(camera, 0x920, 0x2F0);
                lrz.setLrz3SpecialEventsRoutine(0x14);
                lrz.setLrz3AutoscrollStage(0x10);
                lrz.setLrz3AutoscrollDelay(0x2D);
                lrz.setLrz3ScreenRoutine(0x0C);
                lrz.setLrz3CameraFixed(0x920 << 16, 0x2F0 << 16);
            } else {
                lrz.setLrz3ScreenRoutine(4);
                lrz.setLrz3CameraFixed(camera().getX() << 16, camera().getY() << 16);
            }
            resetActualTileOffsetsAndRefresh();
        }

        // loc_59B46: once camera Y reaches its current maximum, pin the minimum to it.
        Camera camera = camera();
        if (lrz.lrz3ScreenRoutine() == 4 && camera.getY() == camera.getMaxY()) {
            camera.setMinY(camera.getY());
            camera.setMinYTarget(camera.getY());
        }
        if (lrz.lrz3TerrainRequest() != 0) {
            lrz.setLrz3TerrainRequest(0);
            lrz.setLrz3ScreenRoutine(Math.min(0x0C, lrz.lrz3ScreenRoutine() + 4));
        }

        int y = camera.getY() & 0xFFFF;
        if (lrz.backgroundRoutine() == 0 && y >= 0x500) {
            lrz.setBackgroundRoutine(4);
        } else if (lrz.backgroundRoutine() == 4 && y < 0x500) {
            lrz.publishDeformationWords(camera.getX() & 0xFFFF, y, 0, 0);
            lrz.setDelayedRowcount(0x0F);
            lrz.setBackgroundRoutine(8);
        } else if (lrz.backgroundRoutine() == 8) {
            int rows = lrz.delayedRowcount() - 1;
            lrz.setDelayedRowcount(rows);
            if (rows < 0) {
                lrz.clearSavedBackgroundCamera();
                lrz.setBackgroundRoutine(0);
            }
        }
        if (y < 0x500) {
            lrz.publishDeformationWords((camera.getX() & 0xFFFF) >> 4,
                    (y >> 4) + 0x10, 0, 0);
        } else {
            lrz.publishDeformationWords((camera.getX() & 0xFFFF) - 0x700,
                    y - 0x500, 0, 0);
        }
    }

    /** ROM {@code Special_events_routine=$14}, {@code loc_59E46..loc_59FC2}. */
    public void updateSpecialEvents(int act) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ
                || lrz.lrz3SpecialEventsRoutine() != 0x14) {
            return;
        }
        if (lrz.lrz3AutoscrollDelay() != 0) {
            lrz.setLrz3AutoscrollDelay(lrz.lrz3AutoscrollDelay() - 1);
            return;
        }
        Camera camera = camera();
        int x = camera.getX() & 0xFFFF;
        int y = camera.getY() & 0xFFFF;
        int stage = lrz.lrz3AutoscrollStage();
        int dx = 0;
        int dy = 0;
        boolean advance = false;
        switch (stage) {
            case 0 -> { dx = 0x20000; advance = x >= 0x410; }
            case 4 -> { dx = 0x16A00; dy = -0x16A00; advance = y <= 0x330; }
            case 8 -> { dx = 0x20000; advance = x >= 0x650; }
            case 0x0C -> { dx = 0x16A00; dy = -0x16A00; advance = y <= 0x2F0; }
            case 0x10 -> { dx = 0x20000; advance = x >= 0x910; }
            case 0x14 -> { dx = 0x1D900; dy = 0xC400; advance = y >= 0x320; }
            case 0x18 -> {
                dx = 0x20000;
                AbstractPlayableSprite player = spriteManager().getMainPlayable();
                if (x >= 0xBBF && player != null && (player.getCentreX() & 0xFFFF) >= 0xC50) {
                    camera.setMinX((short) 0xA00);
                    camera.setMinXTarget((short) 0xA00);
                    camera.setMaxX((short) 0xBC0);
                    camera.setMaxXTarget((short) 0xBC0);
                    camera.setMaxY((short) 0x560);
                    camera.setMaxYTarget((short) 0x560);
                    lrz.setLrz3SpecialEventsRoutine(0);
                    return;
                }
            }
            default -> throw new IllegalStateException("Invalid LRZ3 autoscroll stage " + stage);
        }
        if (advance) {
            lrz.setLrz3AutoscrollStage(stage + 4);
        }
        int fixedX = lrz.lrz3CameraXFixed() + dx;
        int fixedY = lrz.lrz3CameraYFixed() + dy;
        lrz.setLrz3CameraFixed(fixedX, fixedY);
        setCameraPositionAndBounds(camera, fixedX >> 16, fixedY >> 16);
        confineAct3Players((fixedX >> 16) + 0x10, (fixedX >> 16) + 0x120, dx >> 8);
    }

    private void confineAct3Players(int left, int right, int groundVelocity) {
        java.util.List<AbstractPlayableSprite> players = new java.util.ArrayList<>();
        players.add(spriteManager().getMainPlayable());
        players.addAll(spriteManager().getSidekicks());
        for (AbstractPlayableSprite player : players) {
            if (player == null) continue;
            int x = player.getCentreX() & 0xFFFF;
            if (x < left) {
                NativePositionOps.writeXPosPreserveSubpixel(player, left);
                player.setGSpeed((short) groundVelocity);
            } else if (x >= right) {
                NativePositionOps.writeXPosPreserveSubpixel(player, right);
            }
        }
    }

    private static void setCameraPositionAndBounds(Camera camera, int x, int y) {
        camera.setX((short) x);
        camera.setXCopy((short) x);
        camera.setMinX((short) x);
        camera.setMinXTarget((short) x);
        camera.setMaxX((short) x);
        camera.setMaxXTarget((short) x);
        camera.setY((short) y);
        camera.setYCopy((short) y);
        camera.setMinY((short) y);
        camera.setMinYTarget((short) y);
        camera.setMaxY((short) y);
        camera.setMaxYTarget((short) y);
    }

    private void resetActualTileOffsetsAndRefresh() {
        LevelManager manager = levelManager();
        if (manager != null) {
            manager.resetTileOffsetPositionActualForFullRefresh();
            manager.refreshFullTilemapPlanesFromCurrentLayout(0);
        }
    }

    /**
     * {@code LRZ2_BackgroundInit} (sonic3k.asm:115655-115668) and then
     * {@code LRZ2_BackgroundEvent} (:115676-115738), which act 2 dispatches every frame in place
     * of act 1's.
     *
     * <p>The initializer is skipped for the seamless change on purpose: {@code loc_56CAA} runs
     * {@code Load_Level}, which does not re-enter the background initializer, and leaves the
     * routine word clear, so the act-2 event's stage 0 is what refills plane B after the change.
     */
    private void advanceAct2Background(int act) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ || act != 1) {
            return;
        }
        if (!act2BackgroundInitialised) {
            act2BackgroundInitialised = true;
            // loc_56FD2: Events_routine_bg = 8, sub_57082, Reset_TileOffsetPositionEff,
            // Refresh_PlaneFull, ApplyDeformation. The deformation is SwScrlLrz's already.
            lrz.setBackgroundRoutine(Lrz2BackgroundStageMachine.BG_STAGE_STEADY);
            resetEffectiveTileOffsets();
            refreshPlaneFull();
            return;
        }
        Lrz2BackgroundStageMachine.advance(lrz, this::resetEffectiveTileOffsets);
    }

    /** ROM {@code Reset_TileOffsetPositionEff}. */
    private void resetEffectiveTileOffsets() {
        LevelManager manager = levelManager();
        if (manager != null) {
            manager.resetTileOffsetPositionEffectiveForFullRefresh();
        }
    }

    /** ROM {@code Refresh_PlaneFull}, from {@code LRZ2_BackgroundInit}'s {@code moveq #0,d1}. */
    private void refreshPlaneFull() {
        LevelManager manager = levelManager();
        if (manager != null) {
            manager.refreshFullTilemapPlanesFromCurrentLayout(0);
        }
    }

    /**
     * {@code LRZ1_BackgroundEvent} stage 0's {@code Events_fg_5} branch ({@code loc_56BD2},
     * sonic3k.asm:115274-115293) and stage {@code $C}, the act change itself ({@code loc_56CAA},
     * sonic3k.asm:115347-115374).
     *
     * <p>The two halves exist together on purpose: stage 0 advancing {@code Events_routine_bg} to
     * {@code $C} with no stage {@code $C} behind it would leave the background event pointing at a
     * stage that never draws again.
     *
     * @return true when this frame belonged to the change, so the dome-region test does not run
     */
    private boolean advanceSeamlessActChange(int act) {
        LrzZoneRuntimeState lrz = state();
        if (lrz == null || lrz.zoneIndex() != Sonic3kZoneIds.ZONE_LRZ || act != 0) {
            return false;
        }
        if (lrz.backgroundRoutine() == BG_STAGE_ACT_CHANGE) {
            applyActChangeWhenArtIsReady(lrz);
            return true;
        }
        // Only stage 0 (loc_56BD2) reads Events_fg_5: stages 4 (loc_56C6E) and 8 (loc_56C88)
        // never look at the word (LRZ1_BackgroundEvent_Index, sonic3k.asm:115264-115271).
        if (lrz.backgroundRoutine() != LrzBackgroundStageMachine.BG_STAGE_NORMAL) {
            return false;
        }
        // tst.w (Events_fg_5) / beq loc_56C28: with the word clear this is the ordinary stage 0.
        if (lrz.eventsFg5() == 0) {
            return false;
        }
        armActChange(lrz);
        return true;
    }

    /**
     * {@code loc_56BD2}: clear {@code Events_fg_5}, queue the act-2 secondary resources and PLC
     * {@code $30}, and step the background routine to {@code $C}. The ROM queues three Kos jobs;
     * the two table jobs ({@code LRZ2_128x128_Secondary_Kos} into {@code Chunk_table+$180},
     * {@code LRZ2_16x16_Secondary_Kos} into {@code Block_table+$128}) are the act-2 chunk and
     * block data, which the engine's own target {@code Load_Level} brings with the reload. What
     * it does not bring is the art module, so that one is queued here, exactly as the ROM does,
     * and it is also what stage {@code $C} waits on.
     */
    private void armActChange(LrzZoneRuntimeState lrz) {
        lrz.setEventsFg5(0);
        try {
            lrz.setAct2ArtJobOrdinal(moduleKosQueue()
                    .queue(rom(), ACT2_SECONDARY_ART_SOURCE, ACT2_SECONDARY_ART_TILE).ordinal());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot queue the Lava Reef act 2 secondary art", failure);
        }
        applyPlc(ACT2_PLC);
        lrz.setBackgroundRoutine(BG_STAGE_ACT_CHANGE);
    }

    /**
     * {@code loc_56CAA}: {@code tst.b (Kos_modules_left) / bne} holds the change until the queued
     * art has landed, and then the whole swap happens on one frame.
     */
    private void applyActChangeWhenArtIsReady(LrzZoneRuntimeState lrz) {
        if (lrz.act2ArtJobOrdinal() < 0) {
            // Stage $C is only ever entered from armActChange, which always leaves an ordinal.
            // Changing the act from here would skip tst.b (Kos_modules_left) and leave act 2
            // running on act 1's patterns at tile $090, silently and only visually.
            throw new IllegalStateException(
                    "Lava Reef stage $C reached with no queued act 2 art job");
        }
        var handle = hardwareTiming().pendingHandle(
                com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE,
                lrz.act2ArtJobOrdinal()).orElseThrow();
        if (!moduleKosQueue().isReady(handle)) {
            return;
        }
        byte[] art = moduleKosQueue().claim(handle);
        lrz.setAct2ArtJobOrdinal(-1);
        requestAct2Reload(art);
    }

    /**
     * The reload half of {@code loc_56CAA}. It is applied <b>synchronously</b> because the ROM
     * runs {@code Load_Level}, {@code LoadSolids} and the {@code -$2C00} rebase inside this
     * background-event dispatch; deferring it to the next loop iteration would leave one frame
     * drawn from the old act. {@code SozAct1Events.requestAct2Reload} is the precedent.
     */
    private void requestAct2Reload(byte[] act2SecondaryArt) {
        var handoff = seamlessTransitionResourceHandoffs().register(
                new LrzActTransitionHandoff(act2SecondaryArt, ACT2_SECONDARY_ART_TILE, this));
        Camera camera = camera();
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_LRZ, 1)
                .deactivateLevelNow(false)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                // loc_56CAA allocates no Obj_TitleCard: the Lava Reef change is seamless, which
                // is why it is the only act change in the zone with no card. SOZ and FBZ pass
                // TITLE_OWNER because their own paths do allocate one; with no title owner here
                // a TITLE_OWNER lease would never be consumed, so the art is admitted directly.
                .showInLevelTitleCard(false)
                .objectSurvivalPolicy(
                        SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST)
                .preserveOffsetCameraPosition(true)
                // sub.w d0,(Player_1+x_pos) / (Player_2+x_pos) (sonic3k.asm:115363-115364) and
                // jsr (Offset_ObjectsDuringTransition) (:115365), which walks
                // Dynamic_object_RAM+object_size up to Breathing_bubbles -- ROM SST slots 4 to 94
                // exclusive (sonic3k.constants.asm:303-311) -- and subtracts d0/d1 from every
                // entry whose render_flags bit 2 says it is in world coordinates.
                .playerOffset(-ACT2_REBASE_X, 0)
                .romWorldObjectOffsetRange(FIRST_ROM_WORLD_OFFSET_SLOT,
                        LAST_ROM_WORLD_OFFSET_SLOT_EXCLUSIVE)
                // sub.w d0,(Camera_X_pos) / (Camera_X_pos_copy) (:115366-115367).
                .cameraOffset(-ACT2_REBASE_X, 0)
                // sub.w d0,(Camera_min_X_pos) / (Camera_max_X_pos) (:115368-115369). loc_56CAA
                // touches no Y word at all and Load_Level writes no camera word, so the Y bounds
                // the arena left behind have to be carried across unchanged rather than taking
                // act 2's own -- the two camera releases that follow are what widen them.
                .postTransitionMinX((int) camera.getMinX() - ACT2_REBASE_X)
                .postTransitionMaxX((int) camera.getMaxX() - ACT2_REBASE_X)
                .postTransitionMinXTarget((int) camera.getMinXTarget() - ACT2_REBASE_X)
                .postTransitionMaxXTarget((int) camera.getMaxXTarget() - ACT2_REBASE_X)
                .postTransitionMinY((int) camera.getMinY())
                .postTransitionMaxY((int) camera.getMaxY())
                .postTransitionMinYTarget((int) camera.getMinYTarget())
                .postTransitionMaxYTarget((int) camera.getMaxYTarget())
                .resourceHandoff(handoff)
                .build();
        // jsr (Clear_Switches) at :115355 runs BEFORE jsr (Load_Level) at :115359, so a trigger
        // bit an act-2 initializer sets during the reload must survive. FBZ does the same.
        com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.reset();
        // loc_56CAA never calls LRZ2_BackgroundInit: the act 2 background arrives through
        // LRZ2_BackgroundEvent's stage 0 (loc_5700C), which the cleared routine word selects.
        act2BackgroundInitialised = true;
        levelManager().applySynchronousScreenEventTransition(request);
        restoreFightPaletteAcrossTheChange();
        // The synchronous reload has finished: this is the first legal post-change rewind state.
        if (hasRuntime()) {
            levelManager().markSynchronousSeamlessTransitionBoundary();
        }
    }


    /**
     * {@code Load_Level} (sonic3k.asm:38747-38761) copies the level layout and nothing else: no
     * palette. So the ROM carries the fight's own lines -- {@code Pal_LRZMiniboss1} on
     * {@code Normal_palette_line_2} and {@code Pal_LRZMiniboss2} on {@code line_3} and the line
     * after it -- straight through the act change, and only {@code loc_78B08} replaces them, once
     * the act 2 camera has reached {@code $2C0}.
     *
     * <p>The engine's reload installs the target level's palette, which the ROM's does not, so the
     * three lines the fight owns are written back here. Measured on a native BizHawk capture of
     * the recorded movie ({@code ~/Videos/OGGF/lrz-bring-up/native-lrz2-bg/run1}, movie frame
     * 416433 = trace row 26450): 893 frames after its own change the ROM still draws act 2's
     * blocks gold, in the miniboss palette, not blue.
     */
    private void restoreFightPaletteAcrossTheChange() {
        loadPalette(MINIBOSS_PALETTE_FIRST_LINE, Sonic3kConstants.PAL_LRZ_MINIBOSS_1_ADDR);
        loadPalette(MINIBOSS_PALETTE_FIRST_LINE + 1, Sonic3kConstants.PAL_LRZ_MINIBOSS_2_ADDR);
        loadPalette(MINIBOSS_PALETTE_FIRST_LINE + 2,
                Sonic3kConstants.PAL_LRZ_MINIBOSS_2_ADDR + PALETTE_LINE_BYTES);
    }

    /** {@code Obj_Results}' {@code st (Events_fg_5)} for Lava Reef (sonic3k.asm:62615-62622). */
    public void setEventsFg5(boolean flag) {
        LrzZoneRuntimeState lrz = state();
        // Obj_Results' own gate is zone and act, not handler: tst.b (Apparent_act) / bne and the
        // Angel Island / Ice Cap exclusions (sonic3k.asm:62615-62622). The Lava Reef runtime
        // state is shared with the boss act, so without this the word latches there and is
        // carried in rewind with nothing to consume it.
        if (lrz != null && lrz.zoneIndex() == Sonic3kZoneIds.ZONE_LRZ && lrz.actIndex() == 0) {
            lrz.setEventsFg5(flag ? 0xFFFF : 0);
        }
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
