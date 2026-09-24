package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.sonic3k.Sonic3kAct3Carry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.S3kDezFinalBossInstance;
import com.openggf.game.sonic3k.objects.S3kDezFinalArenaControllerInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;

/**
 * Sonic 3 &amp; Knuckles Death Egg ({@code $B00}, {@code $B01}) screen and background events:
 * {@code DEZ1_ScreenEvent}, {@code DEZ2_ScreenInit} and {@code DEZ2_ScreenEvent}
 * (sonic3k.asm:118623-118735). <b>Not</b> Sonic 2's Death Egg.
 *
 * <p>{@code ScreenEvents} (:102233) enters the foreground handler with
 * {@code a3 = Level_layout_main}, whose first {@code $40} words are line pointers with the
 * foreground and background rows interleaved (constants.asm:288): foreground row {@code n} is at
 * offset {@code 4n}, background row {@code n} at {@code 4n + 2}. That is how the three offsets
 * below resolve to rows.
 *
 * <p>Persistent event words live in {@link S3kDezZoneRuntimeState} so rewind captures them.
 * The seamless {@code $B00} → {@code $B01} change in {@code DEZ1_BackgroundEvent}
 * {@code loc_593EC} and the miniboss transport chain are not here yet.
 */
public class Sonic3kDEZEvents extends Sonic3kZoneEvents {
    private boolean act3CarryConsumed;
    private boolean act3Initialised;
    private boolean act3BossSpawned;

    /** {@code DEZ1_ScreenEvent}: {@code movea.w $14(a3),a1} is foreground row 5. */
    static final int ACT1_CHUNK_ROW = 5;
    /** {@code move.b #$BD,$6E(a1)}. */
    static final int ACT1_CHUNK_COLUMN = 0x6E;
    static final int ACT1_CHUNK = 0xBD;

    /** {@code loc_594B4}: {@code movea.w $38(a3),a1} is foreground row 14. */
    static final int ACT2_STAGE0_ROW = 14;
    /** {@code addq.w #1,a1} then three bytes: columns 1, 2 and 3. */
    static final int ACT2_STAGE0_FIRST_COLUMN = 1;
    static final int ACT2_STAGE0_LEFT = 0xD7;
    static final int ACT2_STAGE0_MIDDLE = 0xDC;
    static final int ACT2_STAGE0_RIGHT = 0xD7;

    /** {@code loc_594DA}: {@code movea.w $18(a3),a1} is foreground row 6. */
    static final int ACT2_STAGE1_ROW = 6;
    /** {@code move.b #$BC,$6B(a1)}. */
    static final int ACT2_STAGE1_COLUMN = 0x6B;
    static final int ACT2_STAGE1_CHUNK = 0xBC;

    private static final int FOREGROUND_LAYER = 0;

    @Override
    public void update(int act, int frameCounter) {
        S3kDezZoneRuntimeState state = hasRuntime()
                ? S3kRuntimeStates.currentDez(zoneRuntimeRegistry()).orElse(null)
                : null;
        if (state == null) {
            return;
        }
        if (state.zoneIndex() == Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA) {
            updateAct3ScreenEvent(state);
            updateAct3BackgroundEvent(state);
        } else if (act == 0) {
            updateAct1ScreenEvent(state);
            updateAct1BackgroundEvent(state);
        } else {
            updateAct2ScreenEvent(state);
            updateAct2BackgroundEvent(state);
        }
    }

    /** Nine-entry {@code DEZ3_BackgroundEvent_Index} progression. */
    private void updateAct3BackgroundEvent(S3kDezZoneRuntimeState state) {
        switch (state.backgroundRoutine()) {
            case 0 -> {
                if (state.consumeEventsFg5()) {
                    writeChunks(2, 0x0D, 0x1A);
                    state.advanceBackgroundRoutine();
                }
            }
            case 4 -> {
                if (gameState().isScreenShakeActive()) {
                    state.setAct3BackgroundWord(0x16, 0x2C0);
                    state.advanceBackgroundRoutine();
                }
            }
            case 8 -> {
                if (state.act3BackgroundWord(0x00) != 0x6C0) {
                    prepareAct3BottomUpDraw(state);
                    state.advanceBackgroundRoutine();
                }
            }
            case 12 -> finishAct3BottomUpDraw(state);
            case 16 -> {
                if (state.act3BackgroundWord(0x00) != 0x2C0) {
                    prepareAct3BottomUpDraw(state);
                    state.setAct3BackgroundWord(0x16,
                            (camera().getXCopy() & 0xFFFF) & 0xFFE0);
                    state.advanceBackgroundRoutine();
                }
            }
            case 20 -> finishAct3BottomUpDraw(state);
            case 24 -> {
                if (state.act3BackgroundWord(0x00) == 0) {
                    prepareAct3BottomUpDraw(state);
                    state.advanceBackgroundRoutine();
                } else if (state.consumeEventsFg5()) {
                    int phase = state.act3BackgroundWord(0x0A);
                    int chunk = switch (phase) { case 2 -> 0x09; case 6 -> 0x08; default -> 0x06; };
                    writeChunks(3, 0x0E, chunk);
                    state.setAct3BackgroundWord(0x0A, phase == 6 ? -2 : phase + 2);
                }
            }
            case 28 -> finishAct3BottomUpDraw(state);
            default -> { /* stage $20: DrawBGAsYouMove only */ }
        }
    }

    private void prepareAct3BottomUpDraw(S3kDezZoneRuntimeState state) {
        state.setAct3BackgroundWord(0x0C, 0x0F);
        var manager = levelManager();
        if (manager != null) manager.resetTileOffsetPositionEffectiveForFullRefresh();
    }

    private void finishAct3BottomUpDraw(S3kDezZoneRuntimeState state) {
        state.setAct3BackgroundWord(0x0C, 0);
        state.advanceBackgroundRoutine();
        var manager = levelManager();
        if (manager != null) manager.refreshFullTilemapPlanesFromCurrentLayout(0);
    }

    /** {@code DEZ3_ScreenEvent} stage 0: restore the level-load carry exactly once. */
    private void updateAct3ScreenEvent(S3kDezZoneRuntimeState state) {
        initializeAct3(state);
        if (act3CarryConsumed) return;
        act3CarryConsumed = true;
        Sonic3kAct3Carry carry = module().getGameService(Sonic3kAct3Carry.class);
        Sonic3kAct3Carry.Snapshot saved = carry == null ? null : carry.consume();
        if (saved == null) return; // Direct level-select load: normal fresh-level values.
        levelManager().getLevelGamestate().setRings(saved.rings());
        levelManager().getLevelGamestate().setTimerFrames(saved.timerFrames());
        var player = spriteManager().getMainPlayable();
        if (player != null && saved.shield() != null) player.giveShield(saved.shield());
        state.setForegroundRoutine(4);
    }

    /** {@code DEZ3_ScreenInit}, excluding the boss graph installed by the next slice. */
    private void initializeAct3(S3kDezZoneRuntimeState state) {
        if (act3Initialised) return;
        act3Initialised = true;
        state.setAct3BackgroundWord(0x00, 0x6C0);
        state.setAct3BackgroundWord(0x02, 0x3C0);
        state.setAct3BackgroundWord(0x04, 0x0F8);
        state.setAct3BackgroundWord(0x10, 0);
        state.setAct3BackgroundWord(0x12, 0xFFFF);
        state.setAct3BackgroundWord(0x16, 0x80);
        camera().setX((short) 0x80);
        camera().setXCopy((short) 0x80);
        camera().setYCopy((short) 0x20);
        camera().setMinX((short) 0x80);
        camera().setMaxX((short) 0x80);
        spawnObject(() -> new S3kDezFinalArenaControllerInstance(new ObjectSpawn(
                0x130, 0x0F0, Sonic3kObjectIds.DEZ_END_BOSS,
                0, 0, false, -1)));
        S3kDezFinalBossInstance boss = spawnObject(() -> new S3kDezFinalBossInstance(
                new ObjectSpawn(0x3C0, 0x0F8, Sonic3kObjectIds.DEZ_END_BOSS,
                        0, 0, false, -1)));
        act3BossSpawned = boss != null;
    }

    /**
     * {@code DEZ1_ScreenEvent} has no routine index. Every frame it tests {@code Events_fg_4} and,
     * when set, clears it and writes one chunk, so a second raise writes the chunk again rather
     * than advancing a stage. Its production trigger is the miniboss reaching 8 hits.
     */
    private void updateAct1ScreenEvent(S3kDezZoneRuntimeState state) {
        if (!state.consumeEventsFg4()) {
            return;
        }
        writeChunks(ACT1_CHUNK_ROW, ACT1_CHUNK_COLUMN, ACT1_CHUNK);
    }

    /** {@code DEZ1_BackgroundEvent}: results signal, resource wait, then {@code loc_593EC}. */
    private void updateAct1BackgroundEvent(S3kDezZoneRuntimeState state) {
        switch (state.backgroundRoutine()) {
            case 0 -> {
                if (state.consumeEventsFg5()) {
                    // The target level's normal ROM pipeline owns the same PLC $38 and DEZ2
                    // secondary resources. Advancing here preserves the native wait boundary.
                    state.advanceBackgroundRoutine();
                }
            }
            case 4 -> requestAct2Transition();
            default -> { }
        }
    }

    private void requestAct2Transition() {
        var handoff = seamlessTransitionResourceHandoffs().register(
                new DezActTransitionHandoff(this));
        SessionSaveRequests.requestCurrentSessionSave(SaveReason.PROGRESSION_SAVE);
        var cam = camera();
        int minX = offsetWord(cam.getMinX(), -0x3600);
        int maxX = offsetWord(cam.getMaxX(), -0x3600);
        int minY = offsetWord(cam.getMinY(), 0x0400);
        int maxY = offsetWord(cam.getMaxY(), 0x0400);
        int maxYTarget = offsetWord(cam.getMaxYTarget(), 0x0400);
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .deactivateLevelNow(false)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .preserveEndOfLevelState(true)
                .objectSurvivalPolicy(
                        SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.PERSISTENT_EXACT_SST)
                .showInLevelTitleCard(false)
                .resetLevelGamestateAtInLevelTitleCardDisplay(true)
                .preserveOffsetCameraPosition(true)
                .postTransitionMinX(minX)
                .postTransitionMaxX(maxX)
                .postTransitionMinY(minY)
                .postTransitionMaxY(maxY)
                .postTransitionMaxYTarget(maxYTarget)
                .playerOffset(-0x3600, 0x0400)
                .cameraOffset(-0x3600, 0x0400)
                .resourceHandoff(handoff)
                .build();
        try {
            // loc_593EC calls Load_Level and rebases the world inside this event dispatch.
            levelManager().executeActTransition(request);
            levelManager().markSynchronousSeamlessTransitionBoundary();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Failed to apply DEZ act transition", failure);
        }
    }

    private static int offsetWord(short value, int offset) {
        return ((value & 0xFFFF) + offset) & 0xFFFF;
    }

    /**
     * {@code DEZ2_ScreenEvent} indexes {@code Events_routine_fg} into three {@code bra.w}
     * entries. Stage 0 ({@code loc_594B4}) is the post-act-change cutscene's three chunks and is
     * reachable only through the seamless change, because {@code DEZ2_ScreenInit} starts a direct
     * load at 4. Stage 1 ({@code loc_594DA}) is the end boss's chunk. Stage 2
     * ({@code loc_594F8}) is plain {@code DrawTilesAsYouMove}: it neither consumes
     * {@code Events_fg_4} nor advances.
     */
    private void updateAct2ScreenEvent(S3kDezZoneRuntimeState state) {
        switch (state.foregroundRoutine()) {
            case 0 -> {
                if (state.consumeEventsFg4()) {
                    writeChunks(ACT2_STAGE0_ROW, ACT2_STAGE0_FIRST_COLUMN,
                            ACT2_STAGE0_LEFT, ACT2_STAGE0_MIDDLE, ACT2_STAGE0_RIGHT);
                    state.advanceForegroundRoutine();
                }
            }
            case 4 -> {
                if (state.consumeEventsFg4()) {
                    writeChunks(ACT2_STAGE1_ROW, ACT2_STAGE1_COLUMN, ACT2_STAGE1_CHUNK);
                    state.advanceForegroundRoutine();
                }
            }
            default -> {
                // loc_594F8: jmp (DrawTilesAsYouMove).l
            }
        }
    }

    /**
     * {@code DEZ2_BackgroundEvent} stages 0-2 ({@code loc_59532..loc_59566}). The seamless
     * reload leaves the routine at zero; a direct act-2 load starts at eight and bypasses this
     * redraw. Native {@code Draw_PlaneVertBottomUp} consumes two rows per dispatch from the
     * initial {@code $0F} row counter, then returns negative and advances to plain deformation.
     */
    private void updateAct2BackgroundEvent(S3kDezZoneRuntimeState state) {
        switch (state.backgroundRoutine()) {
            case 0 -> {
                var manager = levelManager();
                if (manager != null) {
                    manager.resetTileOffsetPositionEffectiveForFullRefresh();
                }
                state.setBackgroundDrawRowsRemaining(0x0F);
                state.advanceBackgroundRoutine();
            }
            case 4 -> {
                int remaining = state.backgroundDrawRowsRemaining() - 2;
                state.setBackgroundDrawRowsRemaining(remaining);
                if (remaining < 0) {
                    var manager = levelManager();
                    if (manager != null) {
                        manager.refreshFullTilemapPlanesFromCurrentLayout(0);
                    }
                    state.advanceBackgroundRoutine();
                }
            }
            default -> { /* stage 8: PlainDeformation */ }
        }
    }

    /** Consecutive foreground chunk bytes starting at {@code firstColumn} on {@code row}. */
    private void writeChunks(int row, int firstColumn, int... chunks) {
        var pipeline = zoneLayoutMutationPipelineOrNull();
        if (pipeline == null) {
            return;
        }
        pipeline.queue(context -> {
            for (int index = 0; index < chunks.length; index++) {
                context.surface().setBlockInMap(FOREGROUND_LAYER, firstColumn + index, row, chunks[index]);
            }
            // Refresh_PlaneScreenDirect redraws the visible screen after the write.
            return MutationEffects.redrawAllTilemaps();
        });
    }
}
