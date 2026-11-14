package com.openggf.game.soniccd.zones.ssz;

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
 * Stardust Speedway palette cycling through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a). {@code StageMainLoopDraw} calls {@code CyclePalette} once per level
 * frame unless {@code time_stop} is set (common/main.asm:440-447). Each stage assembles one of four bodies by
 * period (Level/Stardust Speedway/R7 Shared.asm:13-25): <ul>
 * <li><b>Present</b> (R71A, R72A; r7/palette_cycle_a.asm:3-14): three {@code CycleColor} calls
 *     ({@code sub_2001B8}, :17-49) on {@code palette_cycle_timers}/{@code palette_cycle_steps} slots 0-2.</li>
 * <li><b>Past</b> (R71B, R72B; palette_cycle_b.asm:3-4) and <b>Good Future</b> (R71C, R72C, R73C;
 *     palette_cycle_c.asm:3-4): {@code CyclePalette} begins with {@code rts}, so the four calls behind it are dead
 *     code and these five stages never cycle. This is shipped behaviour ({@code FixBugs = 0}); no manager is made
 *     for them.</li>
 * <li><b>Bad Future</b> (R71D, R72D, R73D; palette_cycle_d.asm:3-27): six calls, the sixth choosing its script by
 *     {@code tst.b act} (:21-27): act 1 (act byte 0) reads {@code byte_2002E4}, acts 2 and 3 read
 *     {@code byte_2002C2}. The act byte is the 0-based act, STAGE_ACT in each stage file
 *     (Level/Stardust Speedway/Act 2 Bad Future.asm:10).</li>
 * </ul>
 * Scripts and colour tables are read from the stage file at the offsets
 * {@code tools/soniccd/ssz_presentation_catalogue.py} found; nothing is copied from the disassembly.
 *
 * <p>{@code CycleColor} differs from the Wacky Workbench one only by a second write: after the live-palette colour
 * it stores the colour {@code count} entries further into the table at {@code -$80(a3)}, the water palette buffer
 * ({@code waterPalette} sits $80 bytes before {@code palette}, Level/_Definitions.inc:338-340;
 * palette_cycle_a.asm:41-44). Neither r7/ nor Level/Stardust Speedway/ mentions water, and the engine has no water palette owner for
 * this game, so only the live write is modelled.
 *
 * <p>Timer semantics as shipped: timers and steps start at 0 (cleared with the variables), so every script fires on
 * the first frame and shows its step 1 first; {@code subq.b #1,timer; bpl} makes a reload value of N a period of N+1
 * frames (palette_cycle_a.asm:18-19, 36-37). Rewind of the timers is not registered, matching the other Sonic CD
 * palette cyclers.
 */
final class SszPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.ssz.paletteCycle";
    private static final int PRIORITY = 100;
    private static final int SLOTS = 7;                        // palCycleTimers rs.b 7 (Level/_Definitions.inc:319-320)

    /** A CycleColor script: palette colour index, frame lengths and colour-table indices, colours. */
    private record Script(int colorIndex, int[] lengths, int[] picks, int[] colors) { }

    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final List<Script> scripts = new ArrayList<>();
    private final byte[] timers = new byte[SLOTS];
    private final byte[] steps = new byte[SLOTS];

    SszPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        int act = payload.stage().actNumber() - 1;             // STAGE_ACT is 0-based
        for (SszCatalogue.Row row : SszCatalogue.rows(payload, "script")) {
            boolean chosen = row.c() == 0 || (row.c() == 1) == (act == 0);   // tst.b act ; bne.s (palette_cycle_d.asm:22-24)
            if (!chosen) continue;
            while (scripts.size() <= row.slot()) scripts.add(null);
            scripts.set(row.slot(), readScript(image, row.a(), row.b()));
        }
    }

    /** True when {@code CyclePalette} of this stage does anything (Present and Bad Future). */
    static boolean hasCycle(CdStagePayload payload) {
        return !SszCatalogue.rows(payload, "script").isEmpty();
    }

    /** Script layout (palette_cycle_a.asm:19-37): colour index, count, then count {length, colour pick} pairs. */
    private static Script readScript(CdStageImage image, int script, int colors) {
        int colorIndex = image.u8(script);
        int count = image.u8(script + 1);
        int[] lengths = new int[count];
        int[] picks = new int[count];
        int highest = 0;
        for (int i = 0; i < count; i++) {
            lengths[i] = image.u8(script + 2 + 2 * i);
            picks[i] = (byte) image.u8(script + 3 + 2 * i);            // ext.w d0 (palette_cycle_a.asm:39)
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

    /** CycleColor (r7/palette_cycle_a.asm:17-49). */
    private void cycleColor(int slot, Script script) {
        timers[slot]--;                                        // subq.b #1,(a5)
        if (timers[slot] >= 0) return;                         // bpl.s .End
        int step = (steps[slot] & 0xFF) + 1;                   // addq.b #1,d0
        if (step >= script.lengths().length) step = 0;         // cmp.b d1,d0 ; bcs.s .NoReset
        steps[slot] = (byte) step;
        timers[slot] = (byte) script.lengths()[step];          // move.b (a1,d0.w),(a5)
        int pick = script.picks()[step];
        PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                script.colorIndex() >> 4, script.colorIndex() & 15, script.colors()[pick]);
    }
}
