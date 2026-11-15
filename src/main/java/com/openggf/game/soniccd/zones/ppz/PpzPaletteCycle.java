package com.openggf.game.soniccd.zones.ppz;

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
 * Palmtree Panic palette cycling, acts 1 to 3 in every period, through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a, {@code src/Level/Palmtree Panic/}): {@code Palette Cycle
 * (Present).asm} and {@code (Past).asm}: {@code PaletteCycle} branches over the v0.02 prototype code
 * (:12) to {@code PalCycle_Do} (:86-111), six {@code CycleColor} calls ({@code Level/Palette Cycle
 * Script.asm:15-47}). {@code (Good Future).asm:11-61}: only the prototype routine, two data sets, no
 * scripts. {@code (Bad Future).asm:11-69}: the prototype routine, then a third script on timer slot 2
 * ({@code palCycleTimers+2}, :61-65). The scripts, colours and data sets are read from the stage file
 * at the offsets {@code tools/soniccd/ppz_cycle_catalogue.py} found; nothing is copied from the
 * disassembly. Acts 2 and 3 include the same four Palette Cycle files as act 1 (line 12 of the Act 2 Present stage file,
 * line 15 of the other five), so one class serves every stage and only the table offsets differ.
 *
 * <p>Timer semantics as shipped ({@code FixBugs = 0}): timers and steps start at 0 (cleared with the
 * variables, Level/Main.asm:196-204), so every cycle fires on the first frame and shows its frame 1 first;
 * {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1 frames. Palette animations in
 * this stage do not touch water. Rewind of the timers is not registered yet: no CD frame-owner carries
 * RewindSnapshottable palette managers.
 */
final class PpzPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.ppz.paletteCycle";
    private static final int PRIORITY = 100;
    // Prototype routine destinations: palette+$6A and palette+$58 are colour indices $35 and $2C
    // (Good Future:31,56). Row stride is 4 words (:30,:55).
    private static final int PROTO_COLOR_1 = 0x6A / 2;
    private static final int PROTO_COLOR_2 = 0x58 / 2;
    private static final int PROTO_RELOAD_1 = 7;     // Good Future:15
    private static final int PROTO_RELOAD_2 = 5;     // Good Future:39

    /** A CycleColor script: palette colour index, frame lengths and colour-table indices, colours. */
    private record Script(int colorIndex, int[] lengths, int[] picks, int[] colors) { }

    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final List<int[]> slots = new ArrayList<>();      // {slot} per script, in call order
    private final List<Script> scripts = new ArrayList<>();
    private int[][] protoData;                                 // {data1 words, data2 words}
    private final byte[] timers = new byte[7];                 // palCycleTimers (_Definitions.inc:320)
    private final byte[] steps = new byte[7];                  // palCycleSteps (_Definitions.inc:319)

    PpzPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        for (PpzCycleCatalogue.Row row : PpzCycleCatalogue.rows(payload)) {
            if (row.kind().equals("script")) {
                slots.add(new int[] {row.slot()});
                scripts.add(readScript(image, row.a(), row.b()));
            } else {
                protoData = new int[][] {readWords(image, row.a(), 12), readWords(image, row.b(), 12)};
            }
        }
    }

    static boolean hasCycle(CdStagePayload payload) {
        return !PpzCycleCatalogue.rows(payload).isEmpty();
    }

    /** Script layout (Palette Cycle (Present).asm:120-126): index, count, then count {length, colour} pairs. */
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
        return new Script(colorIndex, lengths, picks, readWords(image, colors, highest + 1));
    }

    private static int[] readWords(CdStageImage image, int offset, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) words[i] = image.u16(offset + 2 * i);
        return words;
    }

    @Override
    public void update() {
        if (scripts.isEmpty() && protoData == null) return;
        if (protoData != null) prototype();                    // Good Future, Bad Future
        for (int i = 0; i < scripts.size(); i++) cycleColor(slots.get(i)[0], scripts.get(i));
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
        write(script.colorIndex(), script.colors()[script.picks()[step]]);
    }

    /** PaletteCycle prototype routine (Good Future:11-61, Bad Future:11-59). */
    private void prototype() {
        timers[0]--;
        if (timers[0] < 0) {                                   // subq.b #1 ; bpl.s .SkipCycle1
            timers[0] = PROTO_RELOAD_1;
            steps[0] = (byte) (steps[0] == 2 ? 0 : steps[0] + 1);   // cmpi.b #2 ; wrap (:19-25)
            for (int i = 0; i < 4; i++) write(PROTO_COLOR_1 + i, protoData[0][steps[0] * 4 + i]);   // :30-33
        }
        timers[1]--;
        if (timers[1] < 0) {                                   // :37-38
            timers[1] = PROTO_RELOAD_2;
            steps[1] = (byte) (steps[1] == 2 ? 0 : steps[1] + 1);   // :43-49
            int row = steps[1] & 3;                             // andi.w #3,d0 (:54)
            for (int i = 0; i < 4; i++) write(PROTO_COLOR_2 + i, protoData[1][row * 4 + i]);        // :55-58
        }
    }

    private void write(int colorIndex, int segaWord) {
        PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                colorIndex >> 4, colorIndex & 15, segaWord);
    }
}
