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
 * The seamless {@code $B00} → {@code $B01} change in {@code DEZ1_BackgroundEvent}
 * {@code loc_593EC}, the miniboss transport chain and the background bottom-up redraw are not
 * here yet; they are slice 7 of the bring-up plan.
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
        } else {
            updateAct2ScreenEvent(state);
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
