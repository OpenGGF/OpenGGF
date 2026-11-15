package com.openggf.game.soniccd.zones.ttz;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Tidal Tempest palette cycling for all ten stage programs, through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a, {@code src/r4/}): the Present stages R41A and R42A assemble
 * {@code palette_cycle_a.asm}, the Past stages R41B and R42B {@code palette_cycle_b.asm}, the Good
 * Future stages {@code palette_cycle_c.asm} and the Bad Future stages {@code palette_cycle_d.asm}; all
 * four define {@code CyclePalette} and share the {@code CycleColor} body (a:57-85, b:26-54, c:26-54).
 * <ul>
 *   <li>{@code palette_cycle_a.asm:4-30}: act 1 below X $280 and Y $160 or more reloads palette entry 4
 *       once and then runs the standard path; the standard path is four {@code CycleColor} calls on one
 *       shared colour table (:16-30). Act 1 above Y $160 and left of X $280 runs the six-call
 *       upper-screen path instead (:34-53). Acts 2 and 3 always run the standard path.</li>
 *   <li>{@code palette_cycle_b/c/d.asm:4-22}: act 1 returns ({@code tst.b act; bne.s Active; rts}); acts
 *       2 and 3 run three {@code CycleColor} calls on one colour table.</li>
 * </ul>
 * Scripts and colours are read from the stage file at the offsets {@code tools/soniccd/ttz_catalogue.py}
 * found; nothing is copied from the disassembly. Palette animations here never touch the underwater
 * palette ({@code CycleColor} writes {@code palette} only, a:64), so the engine's underwater copy keeps
 * the colours {@code LoadWaterPalette} gave it.
 *
 * <p>Timer semantics as shipped ({@code FixBugs = 0}): timers and steps start at 0 (no R4 stage code
 * writes them before the first {@code CyclePalette}, and the variable RAM starts cleared, {@code
 * Level/Initialization.asm:56-70}; a stage entered from another stage would inherit that stage's bytes,
 * which this model does not carry), so every cycle fires on the first frame and shows its frame 1
 * first; {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1 frames. The slot
 * pointers advance whether or not a cycle fires ({@code adda.w #1,a4/a5}, a:83-84), so a slot's timer
 * and step byte are shared by the standard and upper-screen paths exactly as in the original. Rewind
 * of the timers is not registered yet: no CD frame-owner carries RewindSnapshottable palette managers.
 */
final class TtzPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.ttz.paletteCycle";
    private static final int PRIORITY = 100;
    private static final int STAGE_PALETTE_ID = 4;          // moveq #4,d0; bsr.w LoadPalette (a:13-14)
    private static final int UPPER_LEFT_LIMIT_X = 0x280;     // cmpi.w #$280,scroll_fg_x (a:7)
    private static final int UPPER_TOP_LIMIT_Y = 0x160;      // cmpi.w #$160,scroll_fg_y (a:9)

    /** A CycleColor script: palette colour index, frame lengths and colour-table indices, colours. */
    private record Script(int colorIndex, int[] lengths, int[] picks, int[] colors) { }

    private final CdStagePayload payload;
    private final CdStageImage image;
    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final int act;                                   // the native "act" byte: 0 for act 1
    private final boolean presentPalette;                    // palette_cycle_a.asm
    private final List<Script> standard = new ArrayList<>();
    private final List<Script> upper = new ArrayList<>();
    private final byte[] timers = new byte[7];                // palette_cycle_timers
    private final byte[] steps = new byte[7];                 // palette_cycle_steps

    TtzPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this.payload = payload;
        this.level = level;
        this.image = image;
        this.act = payload.stage().actNumber() - 1;
        this.presentPalette = payload.stage().period() == CdPeriod.PRESENT;
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        for (TtzCatalogue.Row row : TtzCatalogue.rows(payload, "cycle-std")) {
            standard.add(readScript(image, row.a(), row.b()));
        }
        for (TtzCatalogue.Row row : TtzCatalogue.rows(payload, "cycle-upper")) {
            upper.add(readScript(image, row.a(), row.b()));
        }
    }

    static boolean hasCycle(CdStagePayload payload) {
        return !TtzCatalogue.rows(payload, "cycle-std").isEmpty();
    }

    /** Timing record (a:89-91): palette colour index, entry count, then count {delay, colour index} pairs. */
    private static Script readScript(CdStageImage image, int script, int colors) {
        int colorIndex = image.u8(script);
        int count = image.u8(script + 1);
        int[] lengths = new int[count];
        int[] picks = new int[count];
        int highest = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = image.u8(script + 2 + 2 * i);
            picks[i] = (byte) image.u8(script + 3 + 2 * i);            // ext.w d0 (a:78)
            highest = Math.max(highest, picks[i]);
        }
        int[] words = new int[highest + 1];
        for (int i = 0; i < words.length; i++) words[i] = image.u16(colors + 2 * i);
        return new Script(colorIndex, lengths, picks, words);
    }

    @Override
    public void update() {
        Camera camera = GameServices.camera();
        int fgX = camera == null ? 0 : camera.getX() & 0xFFFF;
        int fgY = camera == null ? 0 : camera.getY() & 0xFFFF;
        if (presentPalette) {
            if (act == 0 && fgX < UPPER_LEFT_LIMIT_X) {                 // a:5-8
                if (fgY < UPPER_TOP_LIMIT_Y) {                          // a:9-10 bcs
                    run(upper);                                        // a:34-53
                    flush();
                    return;
                }
                boolean wasSet = (timers[4] & 0x80) != 0;               // a:11 bset #7,timers+4
                timers[4] |= (byte) 0x80;
                if (!wasSet) loadStagePalette();                       // a:12-14
            }
            run(standard);                                             // a:16-30
        } else {
            if (act == 0) return;                                      // b/c/d:5-7 tst.b act; bne.s; rts
            run(standard);                                             // b/c/d:11-22
        }
        flush();
    }

    private void run(List<Script> scripts) {
        for (int slot = 0; slot < scripts.size(); slot++) cycleColor(slot, scripts.get(slot));
    }

    /** CycleColor (a:57-85). */
    private void cycleColor(int slot, Script script) {
        timers[slot]--;                                        // subq.b #1,(a5)
        if (timers[slot] >= 0) return;                         // bpl.s R4PaletteCycleReturn
        int step = (steps[slot] & 0xFF) + 1;                   // addq.b #1,d0
        if (step >= script.lengths().length) step = 0;         // cmp.b d1,d0 ; bcs.s
        steps[slot] = (byte) step;
        timers[slot] = (byte) script.lengths()[step];          // move.b (a1,d0.w),(a5)
        write(script.colorIndex(), script.colors()[script.picks()[step]]);
    }

    /**
     * LoadPalette with d0 = 4 (Palette Load.asm:4-15 in this stage's copy): the table record at
     * PaletteTable+4*8 is source pointer, destination, count-1 longs; the destination is relative to the
     * table's first destination, which is the palette buffer.
     */
    private void loadStagePalette() {
        int table = CdStageCatalogue.entry(payload).paletteTable();
        int base = image.u16(table + 4);
        int entry = table + STAGE_PALETTE_ID * 8;
        int source = image.offsetOf(image.u32(entry));
        int firstColor = (image.u16(entry + 4) - base) / 2;
        int words = (image.u16(entry + 6) + 1) * 2;
        for (int i = 0; i < words; i++) write(firstColor + i, image.u16(source + 2 * i));
    }

    private void flush() {
        Palette[] palettes = new Palette[level.getPaletteCount()];
        for (int i = 0; i < palettes.length; i++) palettes[i] = level.getPalette(i);
        registry.resolveInto(palettes, null, GameServices.graphics(), null);
    }

    private void write(int colorIndex, int segaWord) {
        PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                colorIndex >> 4, colorIndex & 15, segaWord);
    }
}
