package com.openggf.game.soniccd.zones.qqz;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.util.List;

/**
 * Quartz Quadrant palette cycling, all ten stages, through the shared palette owner.
 *
 * <p>Sources (docs/scddisasm pin 1ecc5f3a, {@code src/r5/}): {@code CyclePalette} in
 * {@code palette_cycle_a.asm:3-58} (R51A, R52A), {@code _b.asm:3-57} (R51B, R52B), {@code _c.asm:3-55}
 * (R51C, R52C, R53C) and {@code _d.asm:3-72} (R51D, R52D, R53D). All four end in the same table
 * interpreter (a:20-58): {@code subq.b #1,timer; bpl} skips; otherwise the timer reloads from byte 3 of
 * the table header, the step advances and wraps at byte 1, and byte 2 colours of frame {@code step}
 * are copied to palette colour index byte 0. The call structure differs only in which slots run and
 * when: a runs slot 0 always and slot 1 with the table for the current {@code r5_bg_change} parity
 * (a:7-16); b runs slot 0, then slot 1 only while {@code r5_bg_change} is even (b:7-15); c returns while
 * {@code boss_flags} is set (c:4-6); d skips slot 0 under {@code boss_flags} (d:6-15) and runs slots 1 and
 * 2 while {@code r5_bg_change} is odd (d:18-28). While {@code r5_conveyor_reverse} is set slot 0 reads
 * its reverse table (a:21-24). The table addresses come from {@code tools/soniccd/qqz_cycle_catalogue.py};
 * nothing is copied from the disassembly.
 *
 * <p>Timer semantics as shipped ({@code FixBugs = 0}): timers and steps start at 0, so every cycle fires
 * on its first frame and shows frame 1 first; a reload of N is a period of N+1 frames. The cycle writes
 * the working palette only, never water. A background swap reloads the palette from the disc table
 * (background_swap_1a.asm:40-45) and is not modelled here (see QqzScrollHandler). Rewind of the timers is
 * not registered: no CD frame owner carries RewindSnapshottable palette managers yet.
 */
final class QqzPaletteCycle implements AnimatedPaletteManager {
    private static final String OWNER = "scd.qqz.paletteCycle";
    private static final int PRIORITY = 100;
    private static final int SLOTS = 3;

    private final Level level;
    private final CdStageImage image;
    private final int zoneIndex;
    private final PaletteOwnershipRegistry registry;
    private final List<QqzCycleCatalogue.Row> rows;
    private final byte[] timers = new byte[SLOTS];             // palette_cycle_timers
    private final byte[] steps = new byte[SLOTS];              // palette_cycle_steps

    QqzPaletteCycle(CdStagePayload payload, Level level, CdStageImage image) {
        this.level = level;
        this.image = image;
        this.zoneIndex = SonicCdZoneRegistry.zoneIndex(payload);
        // The gameplay session owns the registry and begins its frame (GameplayModeContext).
        this.registry = GameServices.paletteOwnershipRegistry();
        this.rows = QqzCycleCatalogue.rows(payload);
    }

    static boolean hasCycle(CdStagePayload payload) {
        return !QqzCycleCatalogue.rows(payload).isEmpty();
    }

    /** The handler that owns r5_bg_change, r5_conveyor_reverse and boss_flags, or {@code null}. */
    private QqzScrollHandler state() {
        var parallax = GameServices.parallax();
        return parallax != null && parallax.getHandler(zoneIndex) instanceof QqzScrollHandler h ? h : null;
    }

    @Override
    public void update() {
        if (rows.isEmpty()) return;
        QqzScrollHandler state = state();
        int bgChange = state == null ? 0 : state.backgroundChange();
        boolean boss = state != null && state.bossFlags();
        boolean reverse = state != null && state.conveyorReverse();
        for (QqzCycleCatalogue.Row row : rows) {
            boolean runs = switch (row.gate()) {
                case ALWAYS -> true;
                case NOBOSS -> !boss;
                case BG_EVEN -> (bgChange & 1) == 0;
                case BG_ODD -> (bgChange & 1) != 0;
            };
            if (!runs) continue;
            cycle(row.slot(), reverse && row.reverse() >= 0 ? row.reverse() : row.table());
        }
        Palette[] palettes = new Palette[level.getPaletteCount()];
        for (int i = 0; i < palettes.length; i++) palettes[i] = level.getPalette(i);
        registry.resolveInto(palettes, null, GameServices.graphics(), null);
    }

    /** The table interpreter, palette_cycle_a.asm:27-53. Header: colour index, frames, colours per frame, reload. */
    private void cycle(int slot, int table) {
        timers[slot]--;                                        // subq.b #1,(a4)
        if (timers[slot] >= 0) return;                         // bpl.s
        int colorIndex = image.u8(table);
        int count = image.u8(table + 1);
        int width = image.u8(table + 2);
        timers[slot] = (byte) image.u8(table + 3);             // move.b 3(a1),(a4)
        int step = (steps[slot] & 0xFF) + 1;                   // addq.b #1,d0
        if (step >= count) step = 0;                           // cmp.b 1(a1),d0 ; bcs.s
        steps[slot] = (byte) step;
        int data = table + 4 + 2 * step * width;               // lea 4(a1,d0.w),a1 after mulu.w width,d0 ; add.w d0,d0
        for (int i = 0; i < width; i++) {
            int colour = colorIndex + i;
            PaletteWriteSupport.applyColor(registry, level, GameServices.graphics(), OWNER, PRIORITY,
                    colour >> 4, colour & 15, image.u16(data + 2 * i));
        }
    }
}
