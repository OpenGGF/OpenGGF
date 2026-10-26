package com.openggf.game.sonic2.competition.interlace;

import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.game.sonic2.competition.Sonic2CompetitionNativeArt;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlacePlaneWriter.*;
import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceVramService.CpuWordWrite;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2InterlacePlaneWriter {
    @Test void nativePairUsesBothChunkFlipsAndRetainsAllAttributesAndSpillBytes() {
        byte[] ram = new byte[65536];
        ram[0x8000] = 1;
        // Block $3FF lives beyond Block_Table, in the genuine native RAM spill.
        put(ram, 0xAFF8, 0xE155); put(ram, 0xAFFA, 0x4266);
        put(ram, 0xAFFC, 0x1234); put(ram, 0xAFFE, 0x5678);
        for (int flip = 0; flip < 4; flip++) {
            put(ram, 128, 0xF3FF | (flip << 10));
            var block = new Sonic2InterlacePlaneWriter(ram).getBlock(Layout.FOREGROUND, 0, 0);
            int toggle = ((flip & 1) != 0 ? 0x800 : 0) | ((flip & 2) != 0 ? 0x1000 : 0);
            assertEquals((flip & 1) == 0 ? List.of(0xE155 ^ toggle, 0x4266 ^ toggle)
                    : List.of(0x4266 ^ toggle, 0xE155 ^ toggle), block.words());
            assertEquals(128, block.chunkAddress()); assertEquals(0xAFF8, block.blockAddress());
            assertThrows(UnsupportedOperationException.class, () -> block.words().clear());
            assertFalse(block.words().contains(0x1234), "V-flip must not select ordinary lower-row data");
        }
    }

    @Test void exactRamSeedAndRetainedPlansAreImmutableAndCoordinateWordsWrap() {
        byte[] ram = patternedRam();
        var writer = new Sonic2InterlacePlaneWriter(ram);
        var camera = new CameraCopy(-1, 0x10201);
        assertEquals(0xFFFF, camera.x()); assertEquals(0x201, camera.y());
        var original = writer.initialForeground(camera, new CameraCopy(0, 0));
        Arrays.fill(ram, (byte) 0);
        assertEquals(original, writer.initialForeground(camera, new CameraCopy(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> original.clear());
        assertThrows(UnsupportedOperationException.class, () -> original.getFirst().words().clear());
        assertThrows(IllegalArgumentException.class, () -> new Sonic2InterlacePlaneWriter(new byte[0xA800]));
        assertThrows(NullPointerException.class, () -> new Sonic2InterlacePlaneWriter(null));
        assertThrows(IllegalArgumentException.class, () -> nameTableAddress(0xC800, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> writer.foregroundUpdates(2, camera, 0, false));
        assertThrows(IllegalArgumentException.class, () -> writer.foregroundUpdates(1, camera, 0, true));
        assertEquals(0xCFFC, nameTableAddress(0xC000, -1, -1));
        assertEquals(0xC000, nameTableAddress(0xC000, 512, 512));
        assertEquals(writer.getBlock(Layout.FOREGROUND, -1, -1), writer.getBlock(Layout.FOREGROUND, 65535, 65535));
        assertTrue(writer.titleCardForegroundRows(camera, camera, 0).isEmpty());
        assertTrue(writer.titleCardForegroundRows(camera, camera, 65536).isEmpty());
        assertTrue(writer.foregroundUpdates(0, camera, 0xF0, false).isEmpty());
        assertTrue(writer.backgroundUpdates(camera, 0, camera, 0, camera, 0).isEmpty());
    }

    @Test void partialWritesStayOnTheirNativeRowColumnAndKeepActualCallOrder() {
        byte[] ram = patternedRam();
        var writer = new Sonic2InterlacePlaneWriter(ram);
        var p1 = new CameraCopy(505, 507); var p2 = new CameraCopy(0x3FF8, 0x7F8);
        var initial = writer.initialForeground(p1, p2);
        assertEquals(1024, initial.size());
        assertEquals(blockAddress(0xA000, p2.x() - 16, p2.y() + 208), initial.getFirst().address());
        assertEquals(blockAddress(0xC000, p1.x() - 16, p1.y() + 208), initial.get(64).address());

        byte[] expected = new byte[65536], actual = new byte[65536];
        Arrays.fill(expected, (byte) 0x5A); Arrays.fill(actual, (byte) 0x5A);
        oracleForeground(expected, ram, p1, p2);
        apply(actual, initial);
        assertArrayEquals(expected, actual);
        byte[] before = actual.clone();
        var next = new CameraCopy(p1.x() + 16, p1.y());
        var right = writer.foregroundUpdates(0, next, 8, false);
        assertEquals(16, right.size());
        oracleColumn(expected, ram, 0xC000, false, next, 320, -16, 16);
        apply(actual, right);
        assertArrayEquals(expected, actual);
        for (int address = 0; address < 65536; address++) {
            int comparedAddress = address;
            if (right.stream().noneMatch(write -> comparedAddress >= write.address() && comparedAddress < write.address() + 4))
                assertEquals(before[address], actual[address], "unwritten byte " + address);
        }
        var allEdges = writer.foregroundUpdates(1, p2, 15, false);
        assertEquals(76, allEdges.size(), "two 22-block rows and two 16-block columns, preserving overlap/order");
        var redraw = writer.foregroundUpdates(0, p1, 15, true);
        assertEquals(352, redraw.size(), "Screen_redraw_flag replaces, rather than appends, the FG flag work");
        assertEquals(writer.foregroundUpdates(0, p1, 0, true), redraw);
        // Stateless replay: destructive use of another RAM snapshot cannot change an earlier producer or its plans.
        var destroyed = new Sonic2InterlacePlaneWriter(new byte[65536]);
        assertNotEquals(initial, destroyed.initialForeground(p1, p2));
        assertEquals(initial, writer.initialForeground(p1, p2));
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest(name = "ROM zone {0} act {1}")
    @CsvSource({"0,0", "0,1", "11,0", "11,1", "12,0", "12,1"})
    void actualRomInitialAndPartialPlanesMatchIndependentLayoutOracle(int zone, int act) throws Exception {
        var profile = Sonic2CompetitionLevelProfile.load(TestEnvironment.currentRom(), zone, act);
        var art = Sonic2CompetitionNativeArt.load(TestEnvironment.currentRom(), profile);
        byte[] ram = art.workRamBytes();
        var writer = new Sonic2InterlacePlaneWriter(ram);
        var p1 = new CameraCopy(profile.sonicStart().x() - 160, profile.sonicStart().y() - 96);
        var p2 = new CameraCopy(profile.tailsStart().x() - 160 + 496, profile.tailsStart().y() + 403);
        var bg = new CameraCopy(29, 19); // Explicit copied words, deliberately neither aligned nor inferred from FG.
        byte[] expected = new byte[65536], actual = new byte[65536];
        Arrays.fill(expected, (byte) 0x6D); Arrays.fill(actual, (byte) 0x6D);
        oracleForeground(expected, ram, p1, p2);
        oracleInitialBackground(expected, ram, zone, bg);
        var foreground = writer.initialForeground(p1, p2);
        var background = writer.initialBackground(zone, bg);
        assertEquals(zone == 11 ? 1024 : 512, background.size());
        apply(actual, foreground); apply(actual, background);
        assertArrayEquals(expected, actual, "all initial name words including untouched rows/other VRAM");
        for (boolean back : new boolean[]{false, true}) for (int x : new int[]{-17, 0, 127, 128, 511, 0x3FFF})
            for (int y : new int[]{-17, 0, 127, 128, 511, 0x7FF})
                assertEquals(oraclePair(ram, back, x, y), writer.getBlock(back ? Layout.BACKGROUND : Layout.FOREGROUND, x, y).words());

        var moved = new CameraCopy(p2.x() + 16, p2.y() - 16);
        oracleRow(expected, ram, 0xA000, false, moved, -16, -16, 22, false, false);
        oracleColumn(expected, ram, 0xA000, false, moved, 320, -16, 16);
        apply(actual, writer.foregroundUpdates(1, moved, 9, false));
        assertArrayEquals(expected, actual, "a diagonal refresh writes only its actual leading edges");

        var bg2 = new CameraCopy(0x1FE, 0x1FE); var bg3 = new CameraCopy(0xFFF1, 0x7F9);
        oracleBackgroundFlags(expected, ram, bg, 0xFF, bg2, 3, bg3, 3);
        var updates = writer.backgroundUpdates(bg, 0xFF, bg2, 3, bg3, 3);
        assertEquals(216, updates.size());
        assertTrue(updates.stream().allMatch(write -> write.address() >= 0xE000 && write.address() < 0xF000));
        apply(actual, updates);
        assertArrayEquals(expected, actual, "BG1 then BG2 then BG3 overlap is a shared-plane write order");
        if (zone == 11) assertEquals(writer.initialBackground(zone, new CameraCopy(0, 0)), background,
                "MCZ initial whole-plane fill does not consume BG camera words");
        else assertNotEquals(writer.initialBackground(zone, new CameraCopy(0, 0)), background,
                    "EHZ/CNZ retain their shipped camera-relative initial fill");
    }

    private static void oracleForeground(byte[] out, byte[] ram, CameraCopy p1, CameraCopy p2) {
        // Completed leave coverage: 32 source blocks in each of 16 rows per foreground.
        for (int slot = 0; slot < 2; slot++) {
            CameraCopy camera = slot == 0 ? p1 : p2;
            for (int y = -16; y <= 224; y += 16)
                oracleRow(out, ram, slot == 0 ? 0xC000 : 0xA000, false, camera, -16, y, 32, false, false);
        }
    }

    private static void oracleInitialBackground(byte[] out, byte[] ram, int zone, CameraCopy bg) {
        int first = zone == 0 ? -16 : 0, rows = zone == 11 ? 32 : 16;
        for (int i = 0; i < rows; i++)
            oracleRow(out, ram, 0xE000, true, bg, 0, first + i * 16, 32, zone == 11, zone == 11);
    }

    private static void oracleBackgroundFlags(byte[] out, byte[] ram, CameraCopy a, int flags,
            CameraCopy b, int flags2, CameraCopy c, int flags3) {
        if ((flags & 1) != 0) oracleRow(out, ram, 0xE000, true, a, -16, -16, 22, false, false);
        if ((flags & 2) != 0) oracleRow(out, ram, 0xE000, true, a, -16, 224, 22, false, false);
        if ((flags & 4) != 0) oracleColumn(out, ram, 0xE000, true, a, -16, -16, 16);
        if ((flags & 8) != 0) oracleColumn(out, ram, 0xE000, true, a, 320, -16, 16);
        if ((flags & 16) != 0) oracleRow(out, ram, 0xE000, true, a, 0, -16, 32, true, false);
        if ((flags & 32) != 0) oracleRow(out, ram, 0xE000, true, a, 0, 224, 32, true, false);
        if ((flags & 64) != 0) oracleRow(out, ram, 0xE000, true, a, -16, -16, 32, false, false);
        if ((flags & 128) != 0) oracleRow(out, ram, 0xE000, true, a, -16, 224, 32, false, false);
        if ((flags2 & 1) != 0) oracleColumn(out, ram, 0xE000, true, b, -16, 112, 3);
        if ((flags2 & 2) != 0) oracleColumn(out, ram, 0xE000, true, b, 320, 112, 3);
        if ((flags3 & 1) != 0) oracleColumn(out, ram, 0xE000, true, c, -16, 64, 3);
        if ((flags3 & 2) != 0) oracleColumn(out, ram, 0xE000, true, c, 320, 64, 3);
    }

    private static void oracleRow(byte[] out, byte[] ram, int base, boolean bg, CameraCopy camera,
            int x, int y, int count, boolean absoluteX, boolean absoluteY) {
        int sourceY = y + (absoluteY ? 0 : camera.y());
        for (int column = 0; column < count; column++)
            oracleBlock(out, ram, base, bg, x + column * 16 + (absoluteX ? 0 : camera.x()), sourceY);
    }

    private static void oracleColumn(byte[] out, byte[] ram, int base, boolean bg, CameraCopy camera,
            int x, int y, int count) {
        for (int row = 0; row < count; row++) oracleBlock(out, ram, base, bg, x + camera.x(), y + row * 16 + camera.y());
    }

    private static void oracleBlock(byte[] out, byte[] ram, int base, boolean bg, int x, int y) {
        int destination = blockAddress(base, x, y);
        var words = oraclePair(ram, bg, x, y);
        put(out, destination, words.getFirst()); put(out, destination + 2, words.getLast());
    }

    private static List<Integer> oraclePair(byte[] ram, boolean bg, int x, int y) {
        x = Math.floorMod(x, 65536); y = Math.floorMod(y, 65536);
        int chunkId = ram[0x8000 + (bg ? 128 : 0) + (y / 128 % 16) * 256 + x / 128 % 128] & 255;
        int block = read(ram, chunkId * 128 + (y / 16 % 8) * 16 + (x / 16 % 8) * 2);
        int blockOffset = 0x9000 + (block % 1024) * 8;
        int[] pair = {read(ram, blockOffset), read(ram, blockOffset + 2)};
        boolean flipX = (block / 1024 % 2) != 0, flipY = (block / 2048 % 2) != 0;
        int first = flipX ? 1 : 0, toggle = (flipX ? 2048 : 0) + (flipY ? 4096 : 0);
        return List.of(pair[first] ^ toggle, pair[1 - first] ^ toggle);
    }

    private static int blockAddress(int base, int x, int y) {
        return base + Math.floorMod(y, 512) / 16 * 128 + Math.floorMod(x, 512) / 16 * 4;
    }

    private static void apply(byte[] out, List<CpuWordWrite> writes) {
        for (var write : writes) {
            int address = write.address();
            for (int word : write.words()) {
                out[address & 65535] = (byte) (word >>> 8); out[(address + 1) & 65535] = (byte) word;
                address = (address + write.autoIncrement()) & 65535;
            }
        }
    }

    private static byte[] patternedRam() {
        byte[] ram = new byte[65536];
        for (int chunk = 0; chunk < 256; chunk++) for (int block = 0; block < 64; block++)
            put(ram, chunk * 128 + block * 2, (chunk * 13 + block) % 1024 | ((block % 4) << 10));
        for (int i = 0; i < 4096; i++) ram[0x8000 + i] = (byte) (i * 7 + i / 128);
        for (int block = 0; block < 1024; block++) for (int tile = 0; tile < 4; tile++)
            put(ram, 0x9000 + block * 8 + tile * 2, ((block * 3 + tile) & 1023) | ((tile % 4) << 13));
        return ram;
    }

    private static int read(byte[] bytes, int at) { return (bytes[at] & 255) * 256 + (bytes[at + 1] & 255); }
    private static void put(byte[] bytes, int at, int word) { bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word; }
}
