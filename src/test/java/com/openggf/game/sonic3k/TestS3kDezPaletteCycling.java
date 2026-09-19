package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AnPal_DEZ1} / {@code AnPal_DEZ2} (sonic3k.asm:3661-3718), the Sonic 3 &amp; Knuckles
 * Death Egg palette cycles. Act 1 enters at {@code AnPal_DEZ1} and falls through into
 * {@code AnPal_DEZ2}; act 2 enters at the {@code AnPal_DEZ2} label, so act 1 runs three
 * channels and act 2 runs two.
 *
 * <table>
 *   <caption>Channels, read from the routine</caption>
 *   <tr><th>Channel</th><th>Timer</th><th>Reload</th><th>Index</th><th>Step</th><th>Limit</th>
 *       <th>Table</th><th>Destination</th></tr>
 *   <tr><td>A (act 1 only)</td><td>{@code Palette_cycle_counters+$0A}</td><td>{@code $F}</td>
 *       <td>{@code +$04}</td><td>8</td><td>{@code $30}</td><td>{@code AnPal_PalDEZ1}</td>
 *       <td>{@code Normal_palette_line_4+$18} and {@code +$1C} — 8 contiguous bytes, so
 *           palette index 3 colours 12-15</td></tr>
 *   <tr><td>B</td><td>{@code Palette_cycle_counter1}</td><td>4</td><td>{@code counter0}</td>
 *       <td>4</td><td>{@code $30}</td><td>{@code AnPal_PalDEZ12_1}</td>
 *       <td>{@code Normal_palette_line_3+$1A} — palette index 2 colours 13-14</td></tr>
 *   <tr><td>C</td><td>{@code Palette_cycle_counters+$08}</td><td>{@code $13}</td>
 *       <td>{@code +$02}</td><td>{@code $A}</td><td>{@code $28}</td>
 *       <td>{@code AnPal_PalDEZ12_2}</td>
 *       <td>{@code Normal_palette_line_3+$10} — palette index 2 colours 8-12</td></tr>
 * </table>
 *
 * <p>A {@code subq.w #1} / {@code bpl} timer reloaded with {@code n} advances every
 * {@code n + 1} passes, so the periods are 16, 5 and 20 frames. Expectations here come from the
 * ROM tables read through the ROM pipeline, never from the cycler under test.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezPaletteCycling {

    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;

    @Test
    void actOneChannelACyclesLineFourColoursTwelveToFifteenEverySixteenFrames() throws IOException {
        byte[] table = romBytes(Sonic3kConstants.ANPAL_DEZ1_ADDR, Sonic3kConstants.ANPAL_DEZ1_SIZE);
        assertCycle(ACT_1, 3, 12, 4, table, 8, 0x30, 16, "AnPal_DEZ1 channel A");
    }

    @Test
    void bothActsCycleLineThreeColoursThirteenAndFourteenEveryFiveFrames() throws IOException {
        byte[] table = romBytes(Sonic3kConstants.ANPAL_DEZ12_1_ADDR, Sonic3kConstants.ANPAL_DEZ12_1_SIZE);
        assertCycle(ACT_1, 2, 13, 2, table, 4, 0x30, 5, "AnPal_PalDEZ12_1 in act 1");
        assertCycle(ACT_2, 2, 13, 2, table, 4, 0x30, 5, "AnPal_PalDEZ12_1 in act 2");
    }

    @Test
    void bothActsCycleLineThreeColoursEightToTwelveEveryTwentyFrames() throws IOException {
        byte[] table = romBytes(Sonic3kConstants.ANPAL_DEZ12_2_ADDR, Sonic3kConstants.ANPAL_DEZ12_2_SIZE);
        assertCycle(ACT_1, 2, 8, 5, table, 0xA, 0x28, 20, "AnPal_PalDEZ12_2 in act 1");
        assertCycle(ACT_2, 2, 8, 5, table, 0xA, 0x28, 20, "AnPal_PalDEZ12_2 in act 2");
    }

    /**
     * Act 2's dispatch entry is the {@code AnPal_DEZ2} label itself, below channel A's
     * {@code subq.w #1,(Palette_cycle_counters+$0A).w}, so line 4 colours 12-15 are never touched.
     */
    @Test
    void actTwoNeverRunsChannelA() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, ACT_2)
                .build();
        Level level = GameServices.level().getCurrentLevel();
        List<Integer> initial = colourWords(level, 3, 12, 4);

        for (int frame = 0; frame < 200; frame++) {
            fixture.stepIdleFrames(1);
            assertEquals(initial, colourWords(level, 3, 12, 4),
                    "act 2 must not write line 4 colours 12-15 (frame " + frame + ")");
        }
    }

    /**
     * Steps the act for long enough to see several full passes of the table, collects the
     * destination colours every frame, and checks the observed sequence against the ROM table:
     * each table frame in order, each held for exactly {@code period} passes, wrapping after
     * {@code limit / step} frames.
     *
     * <p>{@code Animate_Palette} spends the first {@code $16} frames of a fresh level on the
     * fade and advances no cycle, and the loaded level palette can itself equal a table frame, so
     * the alignment is taken from the first colour <em>change</em> after a lead-in rather than
     * from the first matching value. That makes the first asserted run a whole one.
     */
    private void assertCycle(int actIndex, int paletteIndex, int firstColour, int colourCount,
                             byte[] table, int step, int limit, int period, String label) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, actIndex)
                .build();
        Level level = GameServices.level().getCurrentLevel();

        int tableFrames = limit / step;
        int leadIn = 0x40;
        int frames = leadIn + period * tableFrames * 3;
        List<List<Integer>> observed = new ArrayList<>(frames);
        for (int frame = 0; frame < frames; frame++) {
            fixture.stepIdleFrames(1);
            observed.add(colourWords(level, paletteIndex, firstColour, colourCount));
        }

        List<List<Integer>> expectedFrames = new ArrayList<>(tableFrames);
        for (int index = 0; index < limit; index += step) {
            List<Integer> colours = new ArrayList<>(colourCount);
            for (int colour = 0; colour < colourCount; colour++) {
                int at = index + colour * 2;
                colours.add(((table[at] & 0xFF) << 8) | (table[at + 1] & 0xFF));
            }
            expectedFrames.add(colours);
        }

        int start = -1;
        for (int frame = leadIn; frame < observed.size() && start < 0; frame++) {
            if (!observed.get(frame).equals(observed.get(frame - 1))
                    && expectedFrames.contains(observed.get(frame))) {
                start = frame;
            }
        }
        assertTrue(start >= 0,
                label + ": no colour change into a table frame after " + leadIn + " passes");

        // Some DEZ tables repeat a frame: AnPal_PalDEZ12_2 frames 1 and 3 are byte-identical, and
        // so are AnPal_PalDEZ1 frames 1/5 and 2/4. The observed colours therefore cannot say which
        // index the run is at, so every index that matches is tried and at least one must carry the
        // whole observed sequence at the right period and wrap.
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < tableFrames; index++) {
            if (expectedFrames.get(index).equals(observed.get(start))) {
                candidates.add(index);
            }
        }
        assertTrue(!candidates.isEmpty(), label + ": the aligned pass matched no table frame");

        String firstFailure = null;
        int bestRuns = 0;
        for (int candidate : candidates) {
            int tableIndex = candidate;
            int checkedRuns = 0;
            String failure = null;
            for (int frame = start; frame + period <= observed.size() && failure == null; frame += period) {
                List<Integer> expected = expectedFrames.get(tableIndex);
                for (int held = 0; held < period && failure == null; held++) {
                    if (!expected.equals(observed.get(frame + held))) {
                        failure = label + ": starting from table frame " + candidate + ", frame "
                                + tableIndex + " must be held for " + period + " passes; pass "
                                + (frame + held) + " was " + observed.get(frame + held)
                                + " instead of " + expected;
                    }
                }
                tableIndex = (tableIndex + 1) % tableFrames;
                checkedRuns++;
            }
            if (failure == null) {
                assertTrue(checkedRuns >= tableFrames,
                        label + ": expected at least one full table cycle, checked " + checkedRuns);
                return;
            }
            if (firstFailure == null || checkedRuns > bestRuns) {
                firstFailure = failure;
                bestRuns = checkedRuns;
            }
        }
        org.junit.jupiter.api.Assertions.fail(firstFailure);
    }

    private static List<Integer> colourWords(Level level, int paletteIndex, int firstColour, int count) {
        Palette palette = level.getPalette(paletteIndex);
        List<Integer> words = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            words.add(colourWord(palette.getColor(firstColour + i)));
        }
        return words;
    }

    /** Inverse of {@link Palette.Color#fromSegaFormat(int)} over the 12-bit Genesis colour space. */
    private static int colourWord(Palette.Color color) {
        byte[] data = new byte[2];
        for (int word = 0; word <= 0x0EEE; word += 2) {
            data[0] = (byte) (word >>> 8);
            data[1] = (byte) word;
            Palette.Color candidate = new Palette.Color();
            candidate.fromSegaFormat(data, 0);
            if (candidate.r == color.r && candidate.g == color.g && candidate.b == color.b) {
                return word;
            }
        }
        return -1;
    }

    private static byte[] romBytes(int address, int length) throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        if (romFile == null) {
            throw new IOException("Configured S3K ROM is unavailable");
        }
        try (Rom rom = new Rom()) {
            if (!rom.open(romFile.getAbsolutePath())) {
                throw new IOException("Failed to open configured S3K ROM");
            }
            return rom.readBytes(address, length);
        }
    }
}
