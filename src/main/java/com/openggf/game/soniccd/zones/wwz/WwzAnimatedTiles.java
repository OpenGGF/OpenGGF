package com.openggf.game.soniccd.zones.wwz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Wacky Workbench animated tiles, all ten stages, on the shared animated-pattern owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a). Act 1 Present assembles {@code Level/Wacky Workbench/Animated Tiles
 * Update (Act 1 Present).asm:7-34} ({@code UpdateAnimTiles}); the other nine stages assemble {@code
 * r6/stage_gfx_animate_{1b,1c,1d,2a,2b,2c,2d,3c,3d}.asm} ({@code AnimateStageGfx}, :3-47), the same routines in the
 * other dialect. Each call is {@code AnimateTilesSimple} ({@code Level/Animate Tiles (Simple).asm:21-57}: a reload
 * timer, a frame count and frame art pointers) or {@code AnimateStageGfxScript} ({@code Level/Animate Tiles
 * (Scripted).asm:21-65}: per-frame lengths and art indices); a call that updates copies the frame into the work-RAM
 * art buffer and DMAs it to VRAM at a destination fixed by that stage ({@code move.w #dest,(a5)} plus the
 * {@code dma_stack} word, stage_gfx_animate_1b.asm:11-17). Tables, lengths and destinations are read from the stage
 * file at the offsets {@code tools/soniccd/wwz_presentation_catalogue.py} found; the frame art lives in the stage file
 * as plain tiles.
 *
 * <p>Three call shapes: <b>plain</b> (R61A, R61B, R61D, R62A, R62B, R62D, R63D): the animators in order, timer/frame
 * slots 0, 1, 2. <b>blink</b> (R61C, R62C; stage_gfx_animate_1c.asm:19-77): the first animator, then a hand-run
 * blinker on slot 2 that alternates a 90-frame period and a 30-frame period; in the second phase the next animator
 * runs, in the first phase the art buffer is cleared and zeros are uploaded to its destination every frame.
 * <b>bossgate</b> (R63C, R63D; stage_gfx_animate_3c.asm:10-51): the first animator uploads to its ordinary
 * destination unless {@code boss_flags} bit 6 is set (a request: {@code bclr #6}, {@code bset #7} and a one-off VRAM
 * clear of that destination) or bit 7 is set (then it uploads to the boss region, the electricity's tiles at $470). The
 * flags are the boss RAM's, read and written through an {@link IntSupplier}/{@link IntConsumer} pair (cleared RAM
 * before the boss binds it).
 *
 * <p>{@code LoadShieldArt}/{@code LoadPowerupArt}, called first by every stage, belongs to the powerup object and is
 * not modelled here. Shipped {@code FixBugs = 0}: timers and frames start at 0, so every animator fires on the first
 * frame and shows its frame 1; {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1 frames.
 */
final class WwzAnimatedTiles implements AnimatedPatternManager {
    private static final int TILE_BYTES = Pattern.PATTERN_SIZE_IN_ROM;

    private enum Shape { PLAIN, BLINK, BOSSGATE }

    private record Animator(boolean script, int table, int longs, int destTile, byte[][] frames, int[] lengths,
                            int[] picks, int reload) { }

    private final Level level;
    private final Shape shape;
    private final List<Animator> animators = new ArrayList<>();
    private final byte[] timers = new byte[3];                 // stage_anim_timers (Level/_Definitions.inc)
    private final byte[] frames = new byte[3];                 // stage_anim_frames
    private final int[] blinkDurations = new int[2];
    private final IntSupplier bossFlags;                       // boss_flags (read)
    private final IntConsumer setBossFlags;                    // boss_flags (bclr #6 / bset #7)

    /** VRAM $8E00 ({@code move.w #$4E00,(a5)} with {@code dma_stack} $82, stage_gfx_animate_3c.asm:19-21): tile $470. */
    private static final int BOSS_REGION_TILE = 0x470;

    WwzAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        this(payload, level, image, () -> 0, value -> { });
    }

    WwzAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image, IntSupplier bossFlags,
                     IntConsumer setBossFlags) {
        this.level = level;
        this.bossFlags = bossFlags;
        this.setBossFlags = setBossFlags;
        this.shape = switch (payload) {
            case R61C, R62C -> Shape.BLINK;
            case R63C, R63D -> Shape.BOSSGATE;
            default -> Shape.PLAIN;
        };
        int highest = 0;
        for (WwzCatalogue.Row row : WwzCatalogue.rows(payload)) {
            switch (row.kind()) {
                case "anim_simple", "anim_script" -> {
                    Animator animator = read(image, row, row.kind().equals("anim_script"));
                    animators.add(animator);
                    highest = Math.max(highest, animator.destTile() + animator.longs() * 4 / TILE_BYTES);
                }
                case "blink" -> {
                    blinkDurations[0] = image.u8(row.a());     // R6StageGfxSpecialFrameDurations (scddisasm/src/r6/stage_gfx_animate_1c.asm:40-42)
                    blinkDurations[1] = image.u8(row.a() + 1);
                }
                default -> { }
            }
        }
        if (shape == Shape.BOSSGATE && !animators.isEmpty()) {
            highest = Math.max(highest, BOSS_REGION_TILE + animators.getFirst().longs() * 4 / TILE_BYTES);
        }
        level.ensurePatternCapacity(highest);
    }

    static boolean hasAnimation(CdStagePayload payload) {
        return !WwzCatalogue.rows(payload, "anim_simple").isEmpty() || !WwzCatalogue.rows(payload, "anim_script").isEmpty();
    }

    private static Animator read(CdStageImage image, WwzCatalogue.Row row, boolean script) {
        int table = row.a();
        int longs = row.b() + 1;                               // move.w #n-1,d6 ; dbf copies n longwords
        int count = script ? image.u8(table) : image.u8(table + 1);
        int pointers = script ? table + 2 + 2 * count : table + 2;
        byte[][] art = new byte[count][];
        int[] lengths = new int[count];
        int[] picks = new int[count];
        for (int i = 0; i < count; i++) {
            if (script) {
                lengths[i] = image.u8(table + 2 + 2 * i);      // move.b 2(a1,d0.w),(a2)
                picks[i] = (byte) image.u8(table + 3 + 2 * i); // move.b 3(a1,d0.w),d0 ; ext.w d0
            }
        }
        int frameCount = script ? 0 : count;
        if (script) {
            for (int pick : picks) frameCount = Math.max(frameCount, pick + 1);
        }
        art = new byte[frameCount][];
        for (int i = 0; i < frameCount; i++) {
            art[i] = image.slice(image.offsetOf(image.u32(pointers + 4 * i)), longs * 4);
        }
        return new Animator(script, table, longs, row.c() / TILE_BYTES, art, lengths, picks,
                script ? 0 : image.u8(table));
    }

    /** {@code aniArtFrames+slot}: the frame of one animator (the spark tiles are slot 2). */
    int frame(int slot) {
        return frames[slot] & 0xFF;
    }

    @Override
    public void update() {
        switch (shape) {
            case PLAIN -> {
                for (int slot = 0; slot < animators.size(); slot++) animate(slot, slot);
            }
            case BLINK -> {
                animate(0, 0);                                  // stage_gfx_animate_1c.asm:7-17
                timers[2]--;                                    // subq.b #1,stage_anim_timers+2 ; bmi.s (:20-21)
                if (timers[2] < 0) {
                    frames[2] ^= 1;                             // eori.b #1,stage_anim_frames+2 (:27)
                    timers[2] = (byte) blinkDurations[frames[2] & 1];   // (:28-30)
                }
                if (frames[2] != 0) {
                    animate(1, 1);                              // R6StageGfxAfterSpecialFrame (:46-57), timer/frame slot 1
                } else {
                    Animator target = animators.get(1);        // R6StageGfxClearSpecialTiles (:64-78)
                    upload(target, new byte[target.longs() * 4]);
                }
            }
            case BOSSGATE -> {
                if (update(0, 0)) {                             // bsr AnimateTilesSimple ; bne (stage_gfx_animate_3c.asm:9-10)
                    Animator first = animators.get(0);
                    int flags = bossFlags.getAsInt();
                    if ((flags & 0x40) != 0) {                  // bclr #6,boss_flags ; bne (:11-12)
                        setBossFlags.accept((flags & ~0x40) | 0x80);    // bset #7,boss_flags (:27)
                        // VRAM fill: $8F01 (increment 1), length $FF, $9780 (fill), address $4600, data 0 (:28-33);
                        // the fill covers the ordinary destination's $100 bytes (the animator's 8 tiles).
                        upload(first, new byte[first.longs() * 4], first.destTile());
                    } else if ((flags & 0x80) != 0) {           // btst #7,boss_flags ; beq (:13-14)
                        upload(first, current(0), BOSS_REGION_TILE);    // DMA to $8E00 (:15-21)
                    } else {
                        upload(first, current(0), first.destTile());    // R6StageGfxBossPaletteUpload: $4600 (:44-51)
                    }
                }
                for (int slot = 1; slot < animators.size(); slot++) animate(slot, slot);
            }
        }
    }

    /** One AnimateTilesSimple / AnimateStageGfxScript call plus its DMA when it updated. */
    private void animate(int animatorIndex, int slot) {
        if (update(animatorIndex, slot)) upload(animators.get(animatorIndex), current(animatorIndex));
    }

    private final byte[][] shown = new byte[8][];

    private byte[] current(int animatorIndex) {
        return shown[animatorIndex];
    }

    /** Runs the timer/frame logic; returns true when the animator updated (the routines return d0 = 0). */
    private boolean update(int animatorIndex, int slot) {
        Animator a = animators.get(animatorIndex);
        timers[slot]--;                                         // subq.b #1,(a2)
        if (timers[slot] >= 0) return false;                    // bpl: No update
        int frame = (frames[slot] & 0xFF) + 1;                  // addq.b #1,d0
        if (a.script()) {
            if (frame >= a.lengths().length) frame = 0;         // cmp.b (a1),d0 ; bcs (Scripted.asm:28-30)
            timers[slot] = (byte) a.lengths()[frame];           // move.b 2(a1,d0.w),(a2) (:36)
            shown[animatorIndex] = a.frames()[a.picks()[frame]];
        } else {
            timers[slot] = (byte) a.reload();                   // move.b (a1),(a2) (Simple.asm:24)
            if (frame >= a.frames().length) frame = 0;          // cmp.b 1(a1),d0 ; bcs (:29-31)
            shown[animatorIndex] = a.frames()[frame];
        }
        frames[slot] = (byte) frame;
        return true;
    }

    /** The DMA: copy the art buffer to VRAM at the stage's destination (stage_gfx_animate_1b.asm:11-17). */
    private void upload(Animator a, byte[] art) {
        upload(a, art, a.destTile());
    }

    private void upload(Animator a, byte[] art, int destTile) {
        GraphicsManager graphics = GameServices.graphics();
        boolean canUpdateTextures = graphics != null && graphics.isGlInitialized();
        for (int i = 0; i < art.length / TILE_BYTES; i++) {
            int index = destTile + i;
            if (index >= level.getPatternCount()) continue;
            Pattern pattern = level.getPattern(index);
            byte[] tile = new byte[TILE_BYTES];
            System.arraycopy(art, i * TILE_BYTES, tile, 0, TILE_BYTES);
            pattern.fromSegaFormat(tile);
            if (canUpdateTextures) graphics.updatePatternTexture(pattern, index);
        }
    }
}
