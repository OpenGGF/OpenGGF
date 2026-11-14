package com.openggf.game.soniccd.zones.mmz;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Metallic Madness animated tiles on the shared animated-pattern owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a). Every stage assembles {@code r8/stage_gfx_animate_<stage>.asm}
 * ({@code Level/Metallic Madness/R8 Shared.asm}). {@code AnimateStageGfx} (stage_gfx_animate_1a.asm:3-71) runs
 * {@code LoadPowerupArt} first and then up to five calls, each {@code AnimateTilesSimple}
 * (stage_gfx_animate_1a.asm:144-170: a reload timer, a frame count and frame art pointers) or
 * {@code AnimateStageGfxScript} (:73-110: per-frame lengths and art indices); a call that updates copies the frame
 * into the work-RAM art buffer and DMAs it to VRAM at a destination fixed by that stage
 * ({@code move.w #dest,(a5)} plus the {@code dma_stack} word, :17-24). Calls use timer/frame slots 0, 1, 2, ... in
 * order ({@code adda.w #1,a2/a4} at the end of each call). Tables, lengths and destinations are read from the stage
 * file at the offsets {@code tools/soniccd/mmz_presentation_catalogue.py} found; the frame art lives in the stage
 * file as plain tiles.
 *
 * <p>Calls per stage: R81A 5 (the fourth is the scripted one), R81B none (its {@code AnimateStageGfx} is a bare
 * {@code jmp LoadPowerupArt}, stage_gfx_animate_1b.asm:3-4), R81C 3, R81D 4, R82A 5, R82B 1, R82C 4, R82D 5, R83C 4,
 * R83D 5. In R83C and R83D the last call is gated: {@code tst.b boss_door_flag ; beq run ; tst.b (a4) ; beq rts}
 * (stage_gfx_animate_3c.asm:46-51, stage_gfx_animate_3d.asm:59-64): with the flag set the animator keeps running only
 * until its frame counter reaches 0. No boss lane exists yet, so the flag comes from a {@link BooleanSupplier} that
 * defaults to clear.
 *
 * <p>Clock: {@code AnimateStageGfx} runs once at the end of {@code InitStageResetPlayerState} (common/main.asm:291-309) and once in every V-blank
 * that ends a main-loop iteration ({@code VBlankStage}, common/main.asm:854-892), so the animators advance once per level
 * frame plus one initial tick; a lag V-blank ({@code VBlankLag}, common/main.asm:771-815) does not run it. The shared engine reports
 * no lag frames, so {@link #update()} runs once per level frame; the initial tick and the V-blank calls before the
 * engine's first frame are the level load's ({@code CdFirstMainLoopIteration}).
 *
 * <p>{@code LoadPowerupArt}, called first by every stage, belongs to the powerup object and is not modelled here.
 * Shipped {@code FixBugs = 0}; the routines have no fixed-bug branch: timers and frames start at 0, so every animator
 * fires on the first frame and shows its frame 1; {@code subq.b #1,timer; bpl} makes a reload value of N a period of
 * N+1 frames.
 */
final class MmzAnimatedTiles implements AnimatedPatternManager {
    private static final int TILE_BYTES = Pattern.PATTERN_SIZE_IN_ROM;

    private record Animator(boolean script, int table, int longs, int destTile, byte[][] frames, int[] lengths,
                            int[] picks, int reload) { }

    private final Level level;
    private final List<Animator> animators = new ArrayList<>();
    private final byte[] timers = new byte[8];                 // stage_anim_timers (Level/_Definitions.inc)
    private final byte[] frames = new byte[8];                 // stage_anim_frames
    private int gatedSlot = -1;                                // anim_gate row: the call behind boss_door_flag
    private final BooleanSupplier bossDoor;                    // boss_door_flag

    MmzAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image) {
        this(payload, level, image, () -> false);
    }

    MmzAnimatedTiles(CdStagePayload payload, Level level, CdStageImage image, BooleanSupplier bossDoor) {
        this.level = level;
        this.bossDoor = bossDoor;
        int highest = 0;
        for (MmzCatalogue.Row row : MmzCatalogue.rows(payload)) {
            switch (row.kind()) {
                case "anim_simple", "anim_script" -> {
                    Animator animator = read(image, row, row.kind().equals("anim_script"));
                    animators.add(animator);
                    highest = Math.max(highest, animator.destTile() + animator.longs() * 4 / TILE_BYTES);
                }
                case "anim_gate" -> gatedSlot = row.slot();
                default -> { }
            }
        }
        level.ensurePatternCapacity(highest);
    }

    static boolean hasAnimation(CdStagePayload payload) {
        return !MmzCatalogue.rows(payload, "anim_simple").isEmpty() || !MmzCatalogue.rows(payload, "anim_script").isEmpty();
    }

    private static Animator read(CdStageImage image, MmzCatalogue.Row row, boolean script) {
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

    @Override
    public void update() {
        for (int slot = 0; slot < animators.size(); slot++) {
            // tst.b boss_door_flag ; beq run ; tst.b (a4) ; beq rts (stage_gfx_animate_3c.asm:46-51)
            if (slot == gatedSlot && bossDoor.getAsBoolean() && frames[slot] == 0) return;
            animate(slot, slot);
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
            if (frame >= a.lengths().length) frame = 0;         // cmp.b (a1),d0 ; bcs (stage_gfx_animate_1a.asm:81-83)
            timers[slot] = (byte) a.lengths()[frame];           // move.b 2(a1,d0.w),(a2) (:89)
            shown[animatorIndex] = a.frames()[a.picks()[frame]];
        } else {
            timers[slot] = (byte) a.reload();                   // move.b (a1),(a2) (stage_gfx_animate_1a.asm:147)
            if (frame >= a.frames().length) frame = 0;          // cmp.b 1(a1),d0 ; bcs (:151-153)
            shown[animatorIndex] = a.frames()[frame];
        }
        frames[slot] = (byte) frame;
        return true;
    }

    /** The DMA: copy the art buffer to VRAM at the stage's destination (stage_gfx_animate_1a.asm:17-24). */
    private void upload(Animator a, byte[] art) {
        GraphicsManager graphics = GameServices.graphics();
        boolean canUpdateTextures = graphics != null && graphics.isGlInitialized();
        for (int i = 0; i < art.length / TILE_BYTES; i++) {
            int index = a.destTile() + i;
            if (index >= level.getPatternCount()) continue;
            Pattern pattern = level.getPattern(index);
            byte[] tile = new byte[TILE_BYTES];
            System.arraycopy(art, i * TILE_BYTES, tile, 0, TILE_BYTES);
            pattern.fromSegaFormat(tile);
            if (canUpdateTextures) graphics.updatePatternTexture(pattern, index);
        }
    }
}
