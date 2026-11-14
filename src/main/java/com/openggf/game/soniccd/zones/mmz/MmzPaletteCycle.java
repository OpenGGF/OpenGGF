package com.openggf.game.soniccd.zones.mmz;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Metallic Madness palette cycling, all ten stages, through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a). Every stage assembles one of {@code r8/palette_cycle_a.asm} (R81A, R82A),
 * {@code _b} (R81B, R82B), {@code _c} (R81C, R82C, R83C) or {@code _d} (R81D, R82D, R83D) through
 * {@code Level/Metallic Madness/R8 Shared.asm:11-23}. {@code CyclePalette} (palette_cycle_a.asm:3-30) makes three
 * unconditional {@code CycleColor} calls on {@code palette_cycle_timers}/{@code _steps} slots 0-2, then more calls
 * chosen by {@code act}: {@code a} and {@code b} test {@code cmpi.b #1,act} (act 2 takes the second set, act 1 takes
 * the first; a: three calls each, b: two each), {@code c} tests {@code tst.b act} (act 1 has a fourth call, acts 2 and
 * 3 stop after three, palette_cycle_c.asm:15-24), {@code d} branches three ways (act 1, act 2, act 3: three calls
 * each, palette_cycle_d.asm:15-54). A call that is not reached leaves its slot unused: {@code CycleColor} advances
 * {@code a4}/{@code a5} by one on every call, so a stage's calls use slots 0..n-1 in order. {@code CycleColor} is the
 * same body in all four files (palette_cycle_a.asm:41-69 and the others, normalised comparison) and in Wacky
 * Workbench's. The generator ({@code tools/soniccd/mmz_presentation_catalogue.py}) walks each stage file's own
 * {@code CyclePalette} machine code with the stage's act and records the (script, colours) pair of every
 * {@code CycleColor} it reaches, so the scripts and colour tables come from the stage file, not from the source.
 *
 * <p>Timer semantics as shipped ({@code FixBugs = 0}; there is no fixed-bug branch): timers and steps start at 0
 * (cleared with the variables), so every cycle fires on the first frame and shows its frame 1 first;
 * {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1 frames (palette_cycle_a.asm:42, 59).
 * Rewind of the timers is not registered yet, matching the other Sonic CD palette cyclers.
 */
final class MmzPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.mmz.paletteCycle";
    private static final int PRIORITY = 100;

    /** A CycleColor script: palette colour index, frame lengths and colour-table indices, colours. */
    private record Script(int colorIndex, int[] lengths, int[] picks, int[] colors) { }

    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final List<Script> scripts = new ArrayList<>();
    private final byte[] timers = new byte[8];                 // palCycleTimers (Level/_Definitions.inc)
    private final byte[] steps = new byte[8];                  // palCycleSteps (Level/_Definitions.inc)

    MmzPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        for (MmzCatalogue.Row row : MmzCatalogue.rows(payload, "script")) {
            scripts.add(readScript(image, row.a(), row.b()));
        }
    }

    static boolean hasCycle(CdStagePayload payload) {
        return !MmzCatalogue.rows(payload, "script").isEmpty();
    }

    /** Script layout (palette_cycle_a.asm:CycleColor and the script tables): index, count, then count {length, colour} pairs. */
    private static Script readScript(CdStageImage image, int script, int colors) {
        int colorIndex = image.u8(script);
        int count = image.u8(script + 1);
        int[] lengths = new int[count];
        int[] picks = new int[count];
        int highest = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = image.u8(script + 2 + 2 * i);
            picks[i] = (byte) image.u8(script + 3 + 2 * i);            // ext.w d0 (palette_cycle_a.asm:62)
            highest = Math.max(highest, picks[i]);
        }
        return new Script(colorIndex, lengths, picks, readWords(image, colors, highest + 1));
    }

    private static int[] readWords(CdStageImage image, int offset, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) words[i] = image.u16(offset + 2 * i);
        return words;
    }

    @Override
    public void update() {
        if (scripts.isEmpty()) return;
        for (int slot = 0; slot < scripts.size(); slot++) cycleColor(slot, scripts.get(slot));
        Palette[] palettes = new Palette[level.getPaletteCount()];
        for (int i = 0; i < palettes.length; i++) palettes[i] = level.getPalette(i);
        registry.resolveInto(palettes, null, GameServices.graphics(), null);
    }

    /** CycleColor (r8/palette_cycle_a.asm:41-69). */
    private void cycleColor(int slot, Script script) {
        timers[slot]--;                                        // subq.b #1,(a5)
        if (timers[slot] >= 0) return;                         // bpl.s .End
        int step = (steps[slot] & 0xFF) + 1;                   // addq.b #1,d0
        if (step >= script.lengths().length) step = 0;         // cmp.b d1,d0 ; bcs.s .NoReset
        steps[slot] = (byte) step;
        timers[slot] = (byte) script.lengths()[step];          // move.b (a1,d0.w),(a5)
        int[] colors = script.colors();
        int pick = script.picks()[step];
        PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                script.colorIndex() >> 4, script.colorIndex() & 15, colors[pick]);
    }
}
