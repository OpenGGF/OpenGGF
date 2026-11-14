package com.openggf.game.soniccd.zones.ccz;

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
 * Collision Chaos palette cycling, all ten stages, through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a, {@code src/Level/Collision Chaos/}). Every stage includes
 * {@code Palette Cycle (Present).asm}, which includes {@code Palette Cycle (Act 1 Variant).asm} for
 * {@code CC_VARIANT <> 0}; the entries set the variant (R3 Legacy Act 1 B/C/D Shared.asm:52,
 * R3 Legacy Act 2 B/C/D Shared.asm:52, R3 Shared.asm:52-54):
 * <ul>
 *   <li>variant 0 (Present: R31A, R32A): four {@code CycleColor} scripts, Palette Cycle (Present).asm:11-28, data :36-103;</li>
 *   <li>variant 1 (Past: R31B, R32B): {@code PaletteCycle} is a bare {@code rts}, Act 1 Variant:7-11, so there is
 *       no manager;</li>
 *   <li>variant 2 (Good Future: R31C, R32C, R33C): two scripts, Act 1 Variant:15-23 and data :36-103;</li>
 *   <li>variant 3 (Bad Future: R31D, R32D, R33D): three scripts, Act 1 Variant:15-31 and data :36-131.</li>
 * </ul>
 * {@code CycleColor} is Level/Palette Cycle Script.asm:15-47. The scripts, their lengths and colours are read
 * from the stage file at the offsets {@code tools/soniccd/ccz_cycle_catalogue.py} found; nothing is copied
 * from the disassembly.
 *
 * <p>Timer semantics as shipped ({@code FixBugs = 0}): timers and steps start at 0 (cleared with the
 * variables, Level/Main.asm:196-204), so every cycle fires on the first frame and shows its frame 1 first;
 * {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1 frames. The palette timers are not
 * rewind-registered yet (the CD frame owner carries no RewindSnapshottable palette managers).
 */
final class CczPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.ccz.paletteCycle";
    private static final int PRIORITY = 100;

    /** A CycleColor script: palette colour index, frame lengths and colour-table indices, colours. */
    private record Script(int colorIndex, int[] lengths, int[] picks, int[] colors) { }

    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final List<Script> scripts = new ArrayList<>();    // slot = position in call order
    private final byte[] timers = new byte[7];                 // palCycleTimers (_Definitions.inc:320)
    private final byte[] steps = new byte[7];                  // palCycleSteps (_Definitions.inc:319)

    CczPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        for (CczDiscTables.Row row : CczDiscTables.rows(payload, "script")) {
            if (row.slot() != scripts.size()) throw new IllegalStateException(payload + ": script slots out of order");
            scripts.add(readScript(image, row.a(), row.b()));
        }
    }

    static boolean hasCycle(CdStagePayload payload) {
        return !CczDiscTables.rows(payload, "script").isEmpty();
    }

    /** Script layout (Palette Cycle (Present).asm:36-39; read by CycleColor): index, count, then count {length, colour} pairs. */
    private static Script readScript(CdStageImage image, int script, int colors) {
        int colorIndex = image.u8(script);
        int count = image.u8(script + 1);
        int[] lengths = new int[count];
        int[] picks = new int[count];
        int highest = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = image.u8(script + 2 + 2 * i);
            picks[i] = (byte) image.u8(script + 3 + 2 * i);            // ext.w d0 (Script.asm:40)
            highest = Math.max(highest, picks[i]);
        }
        int[] words = new int[highest + 1];
        for (int i = 0; i < words.length; i++) words[i] = image.u16(colors + 2 * i);
        return new Script(colorIndex, lengths, picks, words);
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
        int colorIndex = script.colorIndex();
        PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                colorIndex >> 4, colorIndex & 15, script.colors()[script.picks()[step]]);
    }
}
