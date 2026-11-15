package com.openggf.game.soniccd.zones.ccz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;

/**
 * Collision Chaos act 2 animated tiles through the shared pattern owner (R32A Present, R32C Good Future,
 * R32D Bad Future; R32B has none).
 *
 * <p>Source (docs/scddisasm pin 1ecc5f3a): {@code src/Level/Collision Chaos/Animated Tiles Update (Act 2).asm}.
 * Acts 1 and 3 and the Past stage assemble the one-instruction {@code UpdateAnimTiles: jmp
 * CCAnimTilesLoadArt} ((Act 1 Present).asm:17-18; (Act 2).asm:49-50 for {@code CC_ANIM_TILES_VARIANT=1}), so
 * they have no animated terrain tiles: their {@code LoadPowerupArt} call is object art, not this lane's.
 *
 * <p>{@code UpdateAnimTiles} (:48-85): {@code jsr CCAnimTilesLoadArt}; {@code subq.b #1,aniArtTimers; bpl}
 * so the timer reloads with 10 and the frames change every 11 calls; {@code aniArtFrames+1} wraps at 2, so
 * the frame sequence is 1, 0, 1, 0 (timer and frame start at 0, cleared with the variables, so the first
 * call shows frame 1). The frame's primary window is DMAd to the primary VRAM address and its secondary
 * window to the secondary one; each DMA reads only the first {@code words} of its 768-byte window
 * ({@code move.l #$940093A0} primary: $A0 words; secondary: $60 words in the Present stage, $80 in the
 * futures, :70 and :79; the variants' values :22-45). The window pointers, the lengths and the VRAM command words are read from the stage
 * file at the offsets {@code tools/soniccd/ccz_cycle_catalogue.py} found. A VRAM command word's low 14
 * bits are the address (the second command word is the constant {@code $80}, address bits 15-14 zero), so
 * the destination tile is {@code (word & $3FFF) / 32}.
 *
 * <p>Shipped {@code FixBugs = 0}: the original calls {@code UpdateAnimTiles} once before the main loop
 * (Level/Main.asm:312) and then from the level V-INT, not from the main loop, and the DMA lands immediately;
 * the V-INT is {@code VInt_Level} (Level/Main.asm:821-847). The calls that precede the engine's first frame are
 * the level load's ({@code CdFirstMainLoopIteration}).
 */
final class CczAnimatedTiles implements AnimatedPatternManager {
    private static final int TIMER_RELOAD = 10;                // move.b #10,aniArtTimers (:55)
    private static final int FRAMES = 2;                       // cmpi.b #2,d0 (:59)

    private record Window(Pattern[] tiles, int firstTile) { }

    private final Level level;
    private final Window[][] windows = new Window[FRAMES][2];  // [frame][primary, secondary]
    private int timer;                                         // aniArtTimers
    private int frame;                                         // aniArtFrames

    CczAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        int[] words = new int[2];
        int[] firstTiles = new int[2];
        for (CczDiscTables.Row row : CczDiscTables.rows(payload, "dma")) {
            if ((row.b() >>> 14) != 1) throw new IllegalStateException(payload + ": DMA is not a VRAM write");
            words[row.slot()] = row.a();
            firstTiles[row.slot()] = (row.b() & 0x3FFF) / 32;
        }
        int required = 0;
        for (CczDiscTables.Row row : CczDiscTables.rows(payload, "frame")) {
            int[] sources = {row.a(), row.b()};
            for (int window = 0; window < 2; window++) {
                int tiles = words[window] * 2 / 32;
                Pattern[] patterns = new Pattern[tiles];
                for (int t = 0; t < tiles; t++) {
                    patterns[t] = new Pattern();
                    patterns[t].fromSegaFormat(image.slice(sources[window] + 32 * t, 32));
                }
                windows[row.slot()][window] = new Window(patterns, firstTiles[window]);
                required = Math.max(required, firstTiles[window] + tiles);
            }
        }
        level.ensurePatternCapacity(required);
        // The level start's call (Level/Main.asm:312) and the V-INT calls before the engine's first frame are the level
        // load's (CdFirstMainLoopIteration), through update().
    }

    static boolean hasAnimation(CdStagePayload payload) {
        return !CczDiscTables.rows(payload, "frame").isEmpty();
    }

    @Override
    public void update() {
        call();
    }

    /** One UpdateAnimTiles call. */
    private void call() {
        if (--timer >= 0) return;                              // subq.b #1,aniArtTimers ; bpl.w (:53-54)
        timer = TIMER_RELOAD;                                  // move.b #10 (:55)
        frame = frame + 1 >= FRAMES ? 0 : frame + 1;           // addq.b #1 ; cmpi.b #2 ; bcs ; moveq #0 (:56-64)
        GraphicsManager graphics = GameServices.graphics();
        boolean textures = graphics.isGlInitialized();
        for (Window window : windows[frame]) {
            for (int t = 0; t < window.tiles().length; t++) {
                int index = window.firstTile() + t;
                if (index >= level.getPatternCount()) continue;
                Pattern dest = level.getPattern(index);
                dest.copyFrom(window.tiles()[t]);
                if (textures) graphics.updatePatternTexture(dest, index);
            }
        }
    }
}
