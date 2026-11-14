package com.openggf.game.soniccd.zones.wwz;

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
import java.util.function.BooleanSupplier;

/**
 * Wacky Workbench palette cycling, all ten stages, through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a). Act 1 Present assembles {@code Level/Wacky Workbench/Palette
 * Cycle (Present).asm}: {@code PaletteCycle} (:12-31) makes four {@code CycleColor} calls on
 * {@code palCycleTimers}/{@code palCycleSteps} slots 0-3 (the fourth falls through into
 * {@code Level/Palette Cycle Script.asm:15-47}). Every other stage assembles {@code r6/palette_cycle_b.asm}
 * (Past; {@code CyclePalette} :3-19), {@code _c} (Good Future), {@code _d} (Bad Future) or {@code _a}
 * (act 2 Present), which are the same four calls with different scripts and colours
 * ({@code R6PaletteCycleGroup1..4Timing}/{@code Colors}); {@code CycleColor} is identical in all of them
 * (palette_cycle_b.asm:20-48). Scripts, colours and the boss colour table are read from the stage file at
 * the offsets {@code tools/soniccd/wwz_presentation_catalogue.py} found; nothing is copied from the
 * disassembly.
 *
 * <p>Good and Bad Future (also act 1 Good Future, act 3) add one branch before the fourth call: with
 * {@code boss_flags} bit 5 set the fourth script reads {@code R6PaletteCycleBossColors} instead of its own
 * colours (palette_cycle_c.asm:17-19, palette_cycle_d.asm:17-19). The flag comes from a {@link BooleanSupplier}
 * (the boss RAM's bit on act 3, clear elsewhere). Act 3's boss also writes timers 0-5 ({@link #fillTimers}).
 *
 * <p>Timer semantics as shipped ({@code FixBugs = 0}): timers and steps start at 0 (cleared with the
 * variables), so every cycle fires on the first frame and shows its frame 1 first;
 * {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1 frames (palette_cycle_b.asm:21-22,
 * 39). The electric-floor flash is the fourth script (palette index $31, a $5B-frame or $3D-frame
 * pattern); it is driven by this routine, not by an object. Rewind of the timers is not registered yet,
 * matching the other Sonic CD palette cyclers.
 */
final class WwzPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.wwz.paletteCycle";
    private static final int PRIORITY = 100;
    private static final int BOSS_COLOR_SLOT = 3;

    /** A CycleColor script: palette colour index, frame lengths and colour-table indices, colours. */
    private record Script(int colorIndex, int[] lengths, int[] picks, int[] colors) { }

    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final BooleanSupplier bossPalette;
    private final List<Script> scripts = new ArrayList<>();
    private int[] bossColors;
    private final byte[] timers = new byte[7];                 // palCycleTimers (_Definitions.inc:320)
    private final byte[] steps = new byte[7];                  // palCycleSteps (_Definitions.inc:319)

    WwzPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this(payload, level, image, () -> false);
    }

    WwzPaletteCycle(CdStagePayload payload, Level level, CdStageImage image, BooleanSupplier bossPalette) {
        this.level = level;
        this.bossPalette = bossPalette;
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        for (WwzCatalogue.Row row : WwzCatalogue.rows(payload, "script")) {
            scripts.add(readScript(image, row.a(), row.b()));
        }
        for (WwzCatalogue.Row row : WwzCatalogue.rows(payload, "bosscolors")) {
            bossColors = readWords(image, row.a(), 4);        // palette_cycle_c.asm:106-110
        }
    }

    /** {@code palCycleSteps+slot}: the step of one CycleColor script (read by objects and the player). */
    int step(int slot) {
        return steps[slot] & 0xFF;
    }

    /**
     * {@code BossFillPaletteCycleTimers}: the boss writes {@code palette_cycle_timers} bytes 0-5 ({@code moveq #5,d0;
     * move.b d1,(a5)+; dbf}, objects/r6/boss.asm:2019-2027): $80 from {@code BossSetPaletteCycleSlow} (:2010-2013), 0
     * from {@code BossSetPaletteCycleClear} (:2016-2017).
     */
    void fillTimers(int value) {
        for (int i = 0; i < 6; i++) timers[i] = (byte) value;
    }

    /** {@code palCycleTimers+slot}. */
    int timer(int slot) {
        return timers[slot] & 0xFF;
    }

    static boolean hasCycle(CdStagePayload payload) {
        return !WwzCatalogue.rows(payload, "script").isEmpty();
    }

    /** Script layout (Palette Cycle (Present).asm:35-39): index, count, then count {length, colour} pairs. */
    private static Script readScript(CdStageImage image, int script, int colors) {
        int colorIndex = image.u8(script);
        int count = image.u8(script + 1);
        int[] lengths = new int[count];
        int[] picks = new int[count];
        int highest = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = image.u8(script + 2 + 2 * i);
            picks[i] = (byte) image.u8(script + 3 + 2 * i);            // ext.w d0 (Palette Cycle Script.asm:40)
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

    /** CycleColor (Level/Palette Cycle Script.asm:15-47). */
    private void cycleColor(int slot, Script script) {
        timers[slot]--;                                        // subq.b #1,(a5)
        if (timers[slot] >= 0) return;                         // bpl.s .End
        int step = (steps[slot] & 0xFF) + 1;                   // addq.b #1,d0
        if (step >= script.lengths().length) step = 0;         // cmp.b d1,d0 ; bcs.s .NoReset
        steps[slot] = (byte) step;
        timers[slot] = (byte) script.lengths()[step];          // move.b (a1,d0.w),(a5)
        int[] colors = script.colors();
        if (slot == BOSS_COLOR_SLOT && bossColors != null && bossPalette.getAsBoolean()) {
            colors = bossColors;                               // btst #5,boss_flags ; lea BossColors,a2
        }
        int pick = script.picks()[step];
        PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                script.colorIndex() >> 4, script.colorIndex() & 15, colors[pick]);
    }
}
