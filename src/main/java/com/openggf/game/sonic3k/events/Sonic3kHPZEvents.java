package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.objects.HPZPaletteControlObjectInstance;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Hidden Palace ({@code $1601}) screen and background events:
 * {@code HPZ_ScreenInit}, {@code HPZ_ScreenEvent} and the Knuckles layout patch in
 * {@code HPZ_BackgroundInit} (sonic3k.asm:119993-120188).
 *
 * <p>The screen shake that {@code HPZ_ScreenEvent} adds to {@code Camera_Y_pos_copy}
 * and the background parallax are shared with the sanctuary and owned by
 * {@link com.openggf.game.sonic3k.Sonic3kLevelEventManager} and {@code SwScrlHpz}.
 * Persistent event words live in {@link HpzZoneRuntimeState} so rewind captures them.
 */
public class Sonic3kHPZEvents extends Sonic3kZoneEvents {
    /** {@code HPZ_ScreenInit}: Knuckles' right limit and the Sonic/Tails upper-route left limit. */
    static final int KNUCKLES_MAX_X = 0xAA0;
    static final int SONIC_TAILS_MIN_X = 0xAA0;
    static final int SONIC_TAILS_MIN_X_BELOW_Y = 0x480;
    /** {@code HPZ_ScreenEvent}: {@code movea.w $1C(a3),a1} is foreground row 7. */
    static final int COLLAPSE_ROW = 7;
    static final int COLLAPSE_FIRST_COLUMN = 0x30;
    static final int COLLAPSE_CHUNK = 0x61;
    /** {@code HPZ_BackgroundInit}: {@code movea.w 4(a3),a1} with {@code a3 = Level_layout_main+2} is background row 1. */
    static final int KNUCKLES_BG_ROW = 1;
    static final int KNUCKLES_BG_FIRST_COLUMN = 3;

    private boolean screenInitApplied;

    @Override
    public void init(int act) {
        super.init(act);
        screenInitApplied = false;
    }

    @Override
    public void update(int act, int frameCounter) {
        HpzZoneRuntimeState state = hasRuntime()
                ? S3kRuntimeStates.currentHpz(zoneRuntimeRegistry()).orElse(null)
                : null;
        if (state == null) {
            return;
        }
        if (!screenInitApplied) {
            screenInitApplied = true;
            applyScreenInit(state.playerCharacter());
        }
        // tst.w (Events_bg+$00) / tst.w (Palette_fade_timer): allocate once after the fade.
        if (!state.paletteControlAllocated() && !paletteFadeActive()) {
            spawnObject(() -> new HPZPaletteControlObjectInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
            // st (Events_bg+$00) runs whether or not AllocateObject succeeded.
            state.markPaletteControlAllocated();
        }
        if (state.consumeForegroundCollapse()) {
            writeChunkPair(0, COLLAPSE_ROW, COLLAPSE_FIRST_COLUMN, COLLAPSE_CHUNK, COLLAPSE_CHUNK);
        }
    }

    private void applyScreenInit(PlayerCharacter playerCharacter) {
        // cmpi.w #3,(Player_mode).w
        if (playerCharacter == PlayerCharacter.KNUCKLES) {
            camera().setMaxX((short) KNUCKLES_MAX_X);
            // HPZ_BackgroundInit: move.b #$8E,3(a1) / move.b #$8F,4(a1)
            writeChunkPair(1, KNUCKLES_BG_ROW, KNUCKLES_BG_FIRST_COLUMN, 0x8E, 0x8F);
            return;
        }
        var player = spriteManager().getMainPlayable();
        if (player != null && (player.getCentreY() & 0xFFFF) < SONIC_TAILS_MIN_X_BELOW_Y) {
            camera().setMinX((short) SONIC_TAILS_MIN_X);
        }
    }

    private void writeChunkPair(int layer, int row, int firstColumn, int first, int second) {
        var pipeline = zoneLayoutMutationPipelineOrNull();
        if (pipeline == null) {
            return;
        }
        pipeline.queue(context -> {
            context.surface().setBlockInMap(layer, firstColumn, row, first);
            context.surface().setBlockInMap(layer, firstColumn + 1, row, second);
            return MutationEffects.redrawAllTilemaps();
        });
    }
}
