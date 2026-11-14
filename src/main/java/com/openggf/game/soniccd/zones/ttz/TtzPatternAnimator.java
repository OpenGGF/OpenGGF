package com.openggf.game.soniccd.zones.ttz;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Tidal Tempest Act 1 Present animated tiles: {@code AnimateStageGfx} (docs/scddisasm pin 1ecc5f3a,
 * {@code src/r4/stage_gfx_animate_1a.asm:3-95}). R41A is the only stage with tile animation: the other
 * nine stages' {@code AnimateStageGfx} is {@code jmp LoadPowerupArt} ({@code stage_gfx_animate_1b.asm:
 * 3-4}, the same file for 1C to 3D), power-up art for the object lane, not tile animation.
 *
 * <p>Per call (natively once per V-blank, {@code r4/main.asm:1069-1070}, and once during
 * {@code InitStage}), unless the camera is at X {@code $280} or beyond or Y {@code $160} or beyond
 * (:7-10): three {@code AnimateStageGfxScript} runs (:56-95) against timers {@code stage_anim_timers}
 * and frames {@code stage_anim_frames}. A script is a count byte, a pad byte, {@code count}
 * {delay, frame} byte pairs and eight pointers to 512-byte frames; when the timer runs out the step
 * advances (wrapping at the count), the delay reloads and 128 longs ({@code move.w #$7F,d6}) are copied
 * to {@code stage_anim_gfx}, which the DMA of :17-23, :30-36 and :43-49 uploads: length
 * {@code $0100} words, to VRAM {@code $4500}, {@code $4700} and {@code $4900}, i.e. 16 tiles at
 * pattern indices {@code $228}, {@code $238} and {@code $248}. The scripts, their destinations and the
 * frame sources are read from the stage file at the offsets {@code tools/soniccd/ttz_catalogue.py} found.
 *
 * <p>Shipped {@code FixBugs = 0}: timers and steps start at 0, so each script fires on its first call and
 * shows its step 1 first. The native DMA is queued and lands in VRAM the same V-blank; here the pattern
 * changes at the call. Rewind of the timers is not registered yet.
 */
final class TtzPatternAnimator implements AnimatedPatternManager {
    private static final int LIMIT_X = 0x280;            // cmpi.w #$280,scroll_fg_x (:7)
    private static final int LIMIT_Y = 0x160;            // cmpi.w #$160,scroll_fg_y (:9)
    private static final int FRAME_TILES = 16;           // 128 longs = 512 bytes (:14, :18 length $0100 words)
    private static final int FRAME_BYTES = FRAME_TILES * Pattern.PATTERN_SIZE_IN_ROM;

    private record Script(int[] delays, int[] frames, int[] sources, int destinationTile) { }

    private final Level level;
    private final CdStageImage image;
    private final List<Script> scripts = new ArrayList<>();
    private final byte[] timers = new byte[3];
    private final byte[] steps = new byte[3];

    TtzPatternAnimator(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        this.image = image;
        for (TtzCatalogue.Row row : TtzCatalogue.rows(payload, "anim")) {
            scripts.add(readScript(image, row.a(), row.b() / Pattern.PATTERN_SIZE_IN_ROM));
        }
        int required = 0;
        for (Script script : scripts) required = Math.max(required, script.destinationTile() + FRAME_TILES);
        if (required > 0) level.ensurePatternCapacity(required);
    }

    static boolean hasAnimation(CdStagePayload payload) {
        return !TtzCatalogue.rows(payload, "anim").isEmpty();
    }

    /** Script (:99-157): count, pad, count x {delay, frame}, then eight frame pointers (:78 movea.l 2(a1,d0.w)). */
    private static Script readScript(CdStageImage image, int script, int destinationTile) {
        int count = image.u8(script);
        int[] delays = new int[count];
        int[] frames = new int[count];
        for (int i = 0; i < count; i++) {
            delays[i] = image.u8(script + 2 + 2 * i);
            frames[i] = (byte) image.u8(script + 3 + 2 * i);       // ext.w d0 (:71)
        }
        int highest = 0;
        for (int frame : frames) highest = Math.max(highest, frame);
        int[] sources = new int[highest + 1];
        for (int i = 0; i < sources.length; i++) {
            sources[i] = image.offsetOf(image.u32(script + 2 + count * 2 + 4 * i));
        }
        return new Script(delays, frames, sources, destinationTile);
    }

    @Override
    public void update() {
        Camera camera = GameServices.camera();
        int x = camera == null ? 0 : camera.getX() & 0xFFFF;
        int y = camera == null ? 0 : camera.getY() & 0xFFFF;
        if (x >= LIMIT_X || y >= LIMIT_Y) return;                  // :7-10 bcc.w locret
        GraphicsManager graphics = GameServices.graphics();
        for (int slot = 0; slot < scripts.size(); slot++) {
            Script script = scripts.get(slot);
            timers[slot]--;                                        // subq.b #1,(a2)
            if (timers[slot] >= 0) continue;                       // bpl.s loc_20FD76
            int step = (steps[slot] & 0xFF) + 1;                   // addq.b #1,d0
            if (step >= script.delays().length) step = 0;          // cmp.b (a1),d0 ; bcs.s
            steps[slot] = (byte) step;
            timers[slot] = (byte) script.delays()[step];           // move.b 2(a1,d0.w),(a2)
            copyFrame(graphics, script, script.sources()[script.frames()[step]]);
        }
    }

    private void copyFrame(GraphicsManager graphics, Script script, int source) {
        byte[] data = image.slice(source, FRAME_BYTES);
        boolean upload = graphics != null && graphics.isGlInitialized();
        for (int tile = 0; tile < FRAME_TILES; tile++) {
            int index = script.destinationTile() + tile;
            if (index >= level.getPatternCount()) break;
            Pattern pattern = level.getPattern(index);
            pattern.fromSegaFormat(java.util.Arrays.copyOfRange(data, tile * Pattern.PATTERN_SIZE_IN_ROM,
                    (tile + 1) * Pattern.PATTERN_SIZE_IN_ROM));
            if (upload) graphics.updatePatternTexture(pattern, index);
        }
    }
}
