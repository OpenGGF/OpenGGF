package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.MutationEffects;
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
 * The results signal queues secondary resources before the synchronous act change;
 * the retained background is then replaced by the native eight-pass bottom-up redraw.
 */
public class Sonic3kDEZEvents extends Sonic3kZoneEvents {

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
        if (act == 0) {
            updateAct1ScreenEvent(state);
            updateAct1Background(state);
        } else {
            updateAct2ScreenEvent(state);
            updateAct2Background(state);
        }
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


    private void updateAct1Background(S3kDezZoneRuntimeState state) {
        if (state.backgroundRoutine() == 0) {
            if (!state.consumeEventsFg5()) return;
            try {
                state.blockJobOrdinal(directKosQueue().queueStandardKos(rom(), 0x1D7E3A,
                        com.openggf.game.sonic3k.resources.S3kKosRamDestinations.blockTableOffset(0x15E0)).ordinal());
                state.artJobOrdinal(moduleKosQueue().queue(rom(), 0x1D7FCA, 0x292).ordinal());
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException("DEZ2 secondary resources", failure);
            }
            applyPlc(0x38);
            state.setBackgroundRoutine(4);
            return; // loc_593A8 returns through PlainDeformation, without entering stage 4.
        }
        if (state.backgroundRoutine() != 4) return;
        var blocks = hardwareTiming().pendingHandle(
                com.openggf.game.timing.HardwareWorkKind.KOS_DECOMPRESSION_QUEUE, state.blockJobOrdinal()).orElseThrow();
        var art = hardwareTiming().pendingHandle(
                com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE, state.artJobOrdinal()).orElseThrow();
        if (!directKosQueue().isReady(blocks) || !moduleKosQueue().isReady(art)
                || moduleKosQueue().hasPendingPhysicalModules()) return;
        byte[] blockBytes = directKosQueue().claim(blocks);
        byte[] artBytes = moduleKosQueue().claim(art);
        state.blockJobOrdinal(-1); state.artJobOrdinal(-1);
        requestAct2Reload(state, blockBytes, artBytes);
    }

    private void requestAct2Reload(S3kDezZoneRuntimeState state, byte[] blocks, byte[] art) {
        state.transitionPlane().retain(levelManager()::getBackgroundTileDescriptorAtWorld);
        byte[][] palettes = new byte[2][32];
        var level = levelManager().getCurrentLevel();
        for (int line = 0; line < 2; line++) for (int color = 0; color < 16; color++) {
            int word = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(level.getPalette(line).getColor(color));
            palettes[line][color * 2] = (byte) (word >>> 8);
            palettes[line][color * 2 + 1] = (byte) word;
        }
        var handoff = seamlessTransitionResourceHandoffs().register(
                new DezActTransitionHandoff(this, state, palettes, blocks, art));
        var camera = camera();
        var request = com.openggf.level.SeamlessLevelTransitionRequest.builder(
                com.openggf.level.SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(11, 1).deactivateLevelNow(false).preserveMusic(true)
                .preserveLevelGamestate(true).preserveEndOfLevelState(true)
                .showInLevelTitleCard(false)
                .runtimeArtAdmissionPolicy(com.openggf.game.RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .objectSurvivalPolicy(com.openggf.level.SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST)
                .playerOffset(-0x3600, 0x400).romWorldObjectOffsetRange(4, 94)
                .preserveOffsetCameraPosition(true).cameraOffset(-0x3600, 0x400)
                .postTransitionMinX(camera.getMinX() - 0x3600).postTransitionMaxX(camera.getMaxX() - 0x3600)
                .postTransitionMinXTarget(camera.getMinXTarget() - 0x3600)
                .postTransitionMaxXTarget(camera.getMaxXTarget() - 0x3600)
                .postTransitionMinY(camera.getMinY() + 0x400).postTransitionMaxY(camera.getMaxY() + 0x400)
                .postTransitionMinYTarget(camera.getMinYTarget() + 0x400)
                .postTransitionMaxYTarget(camera.getMaxY() + 0x400)
                .resourceHandoff(handoff).build();
        com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.reset();
        levelManager().applySynchronousScreenEventTransition(request);
        levelManager().markSynchronousSeamlessTransitionBoundary();
    }

    private void updateAct2Background(S3kDezZoneRuntimeState state) {
        if (state.backgroundRoutine() == 0) state.setBackgroundRoutine(4);
        if (state.backgroundRoutine() != 4) return;
        state.transitionPlane().advance(levelManager()::getBackgroundTileDescriptorAtWorld);
        if (state.transitionPlane().remaining() < 0) state.setBackgroundRoutine(8);
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
