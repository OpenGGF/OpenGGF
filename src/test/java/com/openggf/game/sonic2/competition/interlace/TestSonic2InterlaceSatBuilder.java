package com.openggf.game.sonic2.competition.interlace;

import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.sonic2.competition.interlace.Sonic2InterlaceWorldInputs.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent raw-ROM/CPU-WRAM oracle for BuildSprites_2P, not the native mapper
 * or renderer. Synthetic live-owner sequencing never supplies replacement assets.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2InterlaceSatBuilder {
    private static final int SPRING = 0x1901C, SONIC = 0x6FBE0, TAILS = 0x739E2, BRIDGE = 0xFC70;
    private static final int LABELS = 0x40BEA, DIGITS = 0x40C82, RINGS = 0x1736A;
    private static final Camera ORIGIN = new Camera(0, 0);
    private static final RingWindow NO_RINGS = new RingWindow(List.of());
    private static final Hud STOPPED = new Hud(false, 0,
            new PlayerHud(0, 0, 0, 0), new PlayerHud(0, 0, 0, 0), 0);

    @Test void realSpringSonicTailsAndBridgePiecesUseNativeWordsAndAllFourFlipBranches() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        assertArrayEquals(new byte[]{(byte) 0xF0, 0x0D, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xF0},
                rom.slice(frameAddress(rom, SPRING, 0) + 2, 8), "REV01 spring anchor, including the separate 2P word");
        int[][] mappings = {{SPRING, 0, 2}, {SONIC, 1, 4}, {TAILS, 1, 2}, {BRIDGE, 0, 1}};
        for (var source : mappings) for (int flip = 0; flip < 4; flip++) {
            assertEquals(source[2], rom.readU16BE(frameAddress(rom, source[0], source[1])));
            var object = object(1, 4 | flip, 170, 100, source[0], source[1], 0xE3FC);
            var input = input(STOPPED, List.of(new Admission(7, 0, 0, object)));
            var first = builder.buildPlayer1(seed(), input);
            var expected = masks();
            addFrame(expected, rom, source[0], source[1], 298, 356, 0xE3FC, flip);
            assertPage(first, expected);
            var second = builder.buildPlayer2(first, input, false);
            var expected2 = new ArrayList<Entry>();
            addFrame(expected2, rom, source[0], source[1], 298, 580, 0xE3FC, flip);
            assertPage(second, expected2);
            assertEquals(List.of(new Visibility(7, true)), second.visibility());
        }
    }

    @Test void masksHudAndPerPlayerRingsPrecedeBucketFifoObjectsIncludingDuplicateAdmissions() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        var bridge = object(1, 4, 80, 60, BRIDGE, 0, 0x4100);
        var spring = object(2, 4, 100, 70, SPRING, 0, 0x4200);
        var cleared = object(0, 4, 120, 80, SONIC, 1, 0x4300);
        var admissions = List.of(new Admission(9, 7, 0, spring), new Admission(3, 1, 4, bridge),
                new Admission(4, 0, 0, null), new Admission(5, 0, 1, cleared),
                new Admission(3, 1, 1, bridge));
        var hud = new Hud(true, 8, new PlayerHud(5, 1, 2, 1), new PlayerHud(12, 3, 45, 1), 0);
        var input = new Build(ORIGIN, new Camera(40, 20), hud,
                new RingWindow(List.of(new Ring(0, 0, 30, 40))),
                new RingWindow(List.of(new Ring(8, 1, 60, 80))), 2, admissions, false);
        var first = builder.buildPlayer1(seed(), input);
        var expected = masks();
        addHud(expected, rom, 0, 0, 1, 2, new int[]{5}, null);
        addRing(expected, rom, 2, 30, 40, 0);
        addFrame(expected, rom, BRIDGE, 0, 208, 316, 0x4100, 0);
        addFrame(expected, rom, BRIDGE, 0, 208, 316, 0x4100, 0);
        addFrame(expected, rom, SPRING, 0, 228, 326, 0x4200, 0);
        assertPage(first, expected);
        assertFalse(first.consumeDisplayLists());
        assertThrows(IllegalStateException.class, () -> builder.buildPlayer2(first, input, true));
        var second = builder.buildPlayer2(first, input, false);
        var expected2 = new ArrayList<Entry>();
        addHud(expected2, rom, 1, 0, 3, 45, new int[]{1, 2}, null);
        addRing(expected2, rom, 1, 20, 60, 1);
        addFrame(expected2, rom, BRIDGE, 0, 168, 520, 0x4100, 0);
        addFrame(expected2, rom, BRIDGE, 0, 168, 520, 0x4100, 0);
        addFrame(expected2, rom, SPRING, 0, 188, 530, 0x4200, 0);
        assertPage(second, expected2);
        assertArrayEquals(first.ram().range(0xF800, 0x300), second.ram().range(0xF800, 0x300));
        assertEquals(List.of(new Visibility(3, true), new Visibility(9, true)), second.visibility());
        assertTrue(second.consumeDisplayLists());
        var teleport = new Build(input.firstCamera(), input.secondCamera(), hud, input.firstRings(),
                input.secondRings(), 2, admissions, true);
        assertFalse(builder.buildPlayer2(first, teleport, false).consumeDisplayLists());
    }

    @Test void staticPieceIgnoresFrameAndScreenSpaceCoordinatesRebaseAndAvoidHardwareXZero() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        int pieceAddress = frameAddress(rom, BRIDGE, 0) + 2;
        var direct = object(1, 0x20, 8, 0xFFF8, pieceAddress, 255, 0x7FFF);
        var input = new Build(new Camera(200, 300), new Camera(900, 800), STOPPED,
                NO_RINGS, NO_RINGS, 0, List.of(new Admission(0, 0, 0, direct)), false);
        var expected = masks();
        addPiece(expected, rom, pieceAddress, 8, 0x10078, 0x7FFF, 0);
        var first = builder.buildPlayer1(seed(), input);
        assertPage(first, expected);
        assertEquals(1, first.ram().readWord(0xF800 + 2 * 8 + 6));
        var expected2 = new ArrayList<Entry>();
        addPiece(expected2, rom, pieceAddress, 8, 0x10158, 0x7FFF, 0);
        assertPage(builder.buildPlayer2(first, input, false), expected2);
    }

    @Test void cullingUsesSignedStoredWordsExplicitRadiusAndApproximateYBoundsAndP2OrsVisibility() throws Exception {
        var builder = new Sonic2InterlaceSatBuilder(rom());
        // Native signed subtraction wraps across zero before the signed culling comparisons.
        int[][] cases = {{-8, 0, 4, 1}, {-9, 0, 4, 0}, {327, 0, 4, 1}, {328, 0, 4, 0},
                {0, -32, 4, 1}, {0, -33, 4, 0}, {0, 255, 4, 1}, {0, 256, 4, 0},
                {0, -8, 0x14, 1}, {0, -9, 0x14, 0}, {0, 231, 0x14, 1}, {0, 232, 0x14, 0},
                {0x7FFF, 0, 4, 0}, {-32768, 0, 4, 0}};
        for (var c : cases) {
            var object = object(1, c[2], (0xFFF0 + c[0]) & 65535, (0xFFF0 + c[1]) & 65535,
                    BRIDGE, 0, 0);
            var input = new Build(new Camera(0xFFF0, 0xFFF0), ORIGIN, STOPPED, NO_RINGS, NO_RINGS, 0,
                    List.of(new Admission(3, 0, 0, object)), false);
            var pass = builder.buildPlayer1(seed(), input);
            assertEquals(2 + c[3], pass.spriteCount(), Arrays.toString(c));
            assertEquals(List.of(new Visibility(3, c[3] != 0)), pass.visibility(), Arrays.toString(c));
        }
        var p1Only = object(1, 4, 40, 40, BRIDGE, 0, 0);
        var p2Only = object(1, 4, 1040, 1040, BRIDGE, 0, 0);
        var input = new Build(ORIGIN, new Camera(1000, 1000), STOPPED, NO_RINGS, NO_RINGS, 0,
                List.of(new Admission(1, 0, 0, p1Only), new Admission(2, 0, 1, p2Only)), false);
        var first = builder.buildPlayer1(seed(), input);
        assertEquals(List.of(new Visibility(1, true), new Visibility(2, false)), first.visibility());
        assertEquals(List.of(new Visibility(1, true), new Visibility(2, true)),
                builder.buildPlayer2(first, input, false).visibility());
        // An empty real mapping still gets the object's on_screen flag after successful culling.
        var empty = input(STOPPED, List.of(new Admission(7, 0, 0, object(1, 4, 10, 10, SONIC, 0, 0))));
        var emptyPass = builder.buildPlayer1(seed(), empty);
        assertEquals(2, emptyPass.spriteCount());
        assertEquals(List.of(new Visibility(7, true)), emptyPass.visibility());
    }

    @Test void shippedMultispriteBranchBypassesBothFlipsAndKeepsMainThenRawChildOrder() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        var children = new ArrayList<>(List.of(new Child(160, 90, 2), new Child(80, 120, 1), new Child(0xFFF0, 50, 0)));
        var multi = new MultiSprite(1, 32, 32, children);
        children.clear();
        assertEquals(3, multi.children().size());
        for (int flips = 0; flips < 4; flips++) {
            var object = new ObjectSlot(1, 0x54 | flips, 100, 100, 8, 8, new Mapping(SONIC, 255), 0xF300, multi);
            var input = input(STOPPED, List.of(new Admission(1, 0, 0, object)));
            var expected = masks();
            addFrame(expected, rom, SONIC, 1, 228, 356, 0xF300, 0);
            addFrame(expected, rom, SONIC, 2, 288, 346, 0xF300, 0);
            addFrame(expected, rom, SONIC, 1, 208, 376, 0xF300, 0);
            assertPage(builder.buildPlayer1(seed(), input), expected);
        }
        var noMain = new MultiSprite(0, 32, 32, List.of(new Child(80, 120, 1)));
        var object = new ObjectSlot(1, 0x57, 100, 100, 8, 8, new Mapping(TAILS, 255), 0xF300, noMain);
        var expected = masks(); addFrame(expected, rom, TAILS, 1, 208, 376, 0xF300, 0);
        assertPage(builder.buildPlayer1(seed(), input(STOPPED, List.of(new Admission(1, 0, 0, object)))), expected);
    }

    @Test void nativeCountlessRingMappingsUseStatusOrGlobalFrameAndPreserveShippedOffTopBug() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        var rows = List.of(new Ring(1, 0, 0, 99), new Ring(2, 0, 100, 100),
                new Ring(3, 0x0103, 120, 101), new Ring(4, 0x8001, 130, 110),
                new Ring(5, 2, 140, 331), new Ring(6, 1, 150, 332));
        var hud = new Hud(true, 8, new PlayerHud(1, 0, 0, 0), new PlayerHud(1, 0, 0, 0), 0);
        var input = new Build(new Camera(100, 100), ORIGIN, hud, new RingWindow(rows), NO_RINGS, 1, List.of(), false);
        var expected = masks(); addHud(expected, rom, 0, 0, 0, 0, new int[]{1}, null);
        addRing(expected, rom, 1, 0, 0, 0);
        addRing(expected, rom, 3, 20, 1, 0);
        addRing(expected, rom, 2, 40, 231, 0);
        assertPage(builder.buildPlayer1(seed(), input), expected);
        assertEquals(3, expected.size() - 2 - 10, "only relative Y 0,1,231 survive; negative one disappears in FixBugs=0");
        // Rings have no X masking/zero substitution, unlike ordinary object pieces.
        var unmasked = new Build(ORIGIN, ORIGIN, hud,
                new RingWindow(List.of(new Ring(0, 1, 0xFFF0, 0), new Ring(1, 1, 0xFF88, 0))),
                NO_RINGS, 0, List.of(), false);
        var pass = builder.buildPlayer1(seed(), unmasked);
        int firstRing = 12;
        assertEquals(0x10068 & 65535, pass.ram().readWord(0xF800 + firstRing * 8 + 6));
        assertEquals(0, pass.ram().readWord(0xF800 + (firstRing + 1) * 8 + 6));
    }

    @Test void hudUsesLfFlashTimerBytesLeadingZeroSuppressionLoserHighByteAndPlayerSpecificArt() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        // Explicit expected frames/digits: no call to a production formatter, mapper or decoder.
        for (int lf : new int[]{0, 8}) {
            var hud = new Hud(true, lf, new PlayerHud(0, 9, 7, 1), new PlayerHud(105, 3, 45, 0), 0x1E7F);
            var input = input(hud, List.of());
            var expected = masks(); addHud(expected, rom, 0, lf == 0 ? 3 : 0, 9, 7, new int[]{0}, 30);
            var first = builder.buildPlayer1(seed(), input); assertPage(first, expected);
            var expected2 = new ArrayList<Entry>(); addHud(expected2, rom, 1, 0, 3, 45, new int[]{1, 0, 5}, null);
            assertPage(builder.buildPlayer2(first, input, false), expected2);
            assertEquals(0x83EA, first.ram().readWord(0xF800 + (expected.size() - 2) * 8 + 4));
            assertEquals(0x834E, builder.buildPlayer2(first, input, false).ram()
                    .readWord(0xDD00 + (expected2.size() - 2) * 8 + 4));
        }
        var hud = new Hud(true, 0, new PlayerHud(12, 9, 59, 0), new PlayerHud(0, 1, 2, 1), 0x0900);
        var input = input(hud, List.of());
        var expected = masks(); addHud(expected, rom, 0, 2, 9, 59, new int[]{1, 2}, null);
        var first = builder.buildPlayer1(seed(), input); assertPage(first, expected);
        var expected2 = new ArrayList<Entry>(); addHud(expected2, rom, 1, 1, 1, 2, new int[]{0}, 9);
        assertPage(builder.buildPlayer2(first, input, false), expected2);
    }

    @Test void cpuPieceLoopSpillsPastEightyButHardwareUploadStays320WordsAndTerminatorsDiffer() throws Exception {
        var rom = rom(); var builder = new Sonic2InterlaceSatBuilder(rom);
        for (int bridges : new int[]{76, 78}) {
            var admissions = new ArrayList<Admission>();
            for (int i = 0; i < bridges; i++) admissions.add(new Admission(i, 0, i, object(1, 4, 100, 100, BRIDGE, 0, 0x4000)));
            admissions.add(new Admission(100, 0, bridges, object(1, 4, 120, 100, SONIC, 1, 0x4300)));
            var input = input(STOPPED, admissions);
            var first = builder.buildPlayer1(seed(), input); var second = builder.buildPlayer2(first, input, false);
            var expected = masks(); var expected2 = new ArrayList<Entry>();
            for (int i = 0; i < bridges; i++) {
                addFrame(expected, rom, BRIDGE, 0, 228, 356, 0x4000, 0);
                addFrame(expected2, rom, BRIDGE, 0, 228, 580, 0x4000, 0);
            }
            if (bridges == 76) addFrame(expected, rom, SONIC, 1, 248, 356, 0x4300, 0);
            addFrame(expected2, rom, SONIC, 1, 248, 580, 0x4300, 0);
            assertPage(first, expected); assertPage(second, expected2);
            assertEquals(bridges == 76 ? 82 : 80, first.spriteCount());
            assertEquals(bridges == 76 ? 80 : 82, second.spriteCount());
            assertEquals(320, first.hardwareWords().size()); assertEquals(320, second.hardwareWords().size());
            if (bridges == 76) {
                assertNotEquals(0xA5A5, first.ram().readWord(0xFA80), "81st CPU entry exists beyond uploaded P1 SAT");
                assertEquals(0, first.ram().readByte(0xF800 + 81 * 8 + 3));
                assertEquals(0xA5A5, first.ram().readWord(0xF800 + 82 * 8), "P1 >=80 clears last link without appending a terminator");
            } else {
                assertNotEquals(0xA5A5, second.ram().readWord(0xDF80), "81st CPU entry exists beyond uploaded P2 SAT");
                assertEquals(82, second.ram().readByte(0xDD00 + 81 * 8 + 3));
                assertEquals(0, second.ram().readWord(0xDD00 + 82 * 8));
                assertEquals(0, second.ram().readWord(0xDD00 + 82 * 8 + 2));
            }
        }
    }

    @Test void immutableInputsAndRetainedRamSurviveDestructiveConsumersAndReplay() throws Exception {
        var builder = new Sonic2InterlaceSatBuilder(rom());
        var admissions = new ArrayList<>(List.of(new Admission(1, 0, 0, object(1, 4, 10, 10, BRIDGE, 0, 0))));
        var ringRows = new ArrayList<>(List.of(new Ring(0, 0, 10, 10)));
        var window = new RingWindow(ringRows); ringRows.clear();
        var input = new Build(ORIGIN, ORIGIN, STOPPED, window, NO_RINGS, 0, admissions, false); admissions.clear();
        byte[] mutable = new byte[65536]; Arrays.fill(mutable, (byte) 0xA5);
        var ram = new Sonic2InterlaceWorkRam(mutable); Arrays.fill(mutable, (byte) 0);
        var first = builder.buildPlayer1(ram, input); var second = builder.buildPlayer2(first, input, false);
        var replay = builder.buildPlayer2(builder.buildPlayer1(ram, input), input, false);
        assertEquals(second, replay);
        byte[] leaked = first.ram().bytes(); Arrays.fill(leaked, (byte) 0);
        leaked = first.ram().range(0xF800, 0x300); Arrays.fill(leaked, (byte) 0);
        builder.buildPlayer1(new Sonic2InterlaceWorkRam(new byte[65536]), input(STOPPED, List.of()));
        assertEquals(second, builder.buildPlayer2(first, input, false));
        assertEquals(0xA5A5, ram.readWord(0xF800));
        assertEquals(1, input.admissions().size()); assertEquals(1, input.firstRings().rows().size());
        assertThrows(UnsupportedOperationException.class, () -> first.hardwareWords().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.visibility().clear());
        assertThrows(UnsupportedOperationException.class, () -> input.admissions().clear());
        assertThrows(UnsupportedOperationException.class, () -> window.rows().clear());
        assertThrows(IllegalArgumentException.class, () -> builder.buildPlayer2(second, input, false));
        assertThrows(IllegalArgumentException.class, () -> new Sonic2InterlaceWorkRam(new byte[65535]));
        assertThrows(IllegalArgumentException.class, () -> new Mapping(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RingWindow(List.of(new Ring(1, 0, 0, 0), new Ring(1, 0, 0, 0))));
        assertThrows(IllegalArgumentException.class, () -> new RingWindow(List.of(new Ring(1, 0, 0, 0), new Ring(3, 0, 0, 0))));
    }

    private static RomByteReader rom() throws Exception { return RomByteReader.fromRom(TestEnvironment.currentRom()); }
    private static Sonic2InterlaceWorkRam seed() {
        byte[] bytes = new byte[65536]; Arrays.fill(bytes, (byte) 0xA5); return new Sonic2InterlaceWorkRam(bytes);
    }
    private static ObjectSlot object(int id, int flags, int x, int y, int mapping, int frame, int art) {
        return new ObjectSlot(id, flags, x, y, 8, 8, new Mapping(mapping, frame), art, null);
    }
    private static Build input(Hud hud, List<Admission> admissions) {
        return new Build(ORIGIN, ORIGIN, hud, NO_RINGS, NO_RINGS, 0, admissions, false);
    }

    private record Entry(int y, int size, int tile, int x) { }
    private static ArrayList<Entry> masks() {
        return new ArrayList<>(List.of(new Entry(0x1D8, 15, 0, 1), new Entry(0x1D8, 15, 0, 0)));
    }
    private static int frameAddress(RomByteReader rom, int base, int frame) {
        return base + (short) rom.readU16BE(base + frame * 2);
    }
    private static void addFrame(List<Entry> out, RomByteReader rom, int base, int frame,
            int x, int y, int art, int flip) {
        int address = frameAddress(rom, base, frame), count = rom.readU16BE(address);
        assertTrue(count < 16, "oracle is deliberately bounded to real small mapping frames");
        for (int i = 0; i < count; i++) addPiece(out, rom, address + 2 + i * 8, x, y, art, flip);
    }
    private static void addPiece(List<Entry> out, RomByteReader rom, int at, int x, int y, int art, int flip) {
        // Decode the eight ROM bytes independently: ordinary art word at +2 is deliberately ignored.
        byte[] bytes = rom.slice(at, 8);
        int shape = bytes[1] & 255, dx = (short) (((bytes[6] & 255) << 8) | (bytes[7] & 255));
        int width = (shape / 4 + 1) * 8, height = (shape % 4 + 1) * 8;
        int nativeShape = (width / 8 - 1) * 4 + (height / 8 - 1) / 2;
        int px = Math.floorMod(x + ((flip % 2 == 0) ? dx : -dx - width), 512);
        int py = y + ((flip < 2) ? bytes[0] : -bytes[0] - height);
        int nativeWord = ((bytes[4] & 255) << 8) | (bytes[5] & 255);
        out.add(new Entry(py & 65535, nativeShape, ((art + nativeWord) & 65535) ^ (flip * 0x800), px == 0 ? 1 : px));
    }
    private static void addRing(List<Entry> out, RomByteReader rom, int frame, int relativeX, int relativeY, int player) {
        int at = frameAddress(rom, RINGS, frame);
        byte[] piece = rom.slice(at, 8);
        int shape = piece[1] & 255;
        out.add(new Entry((relativeY + 256 + player * 224 + piece[0]) & 65535,
                (shape / 4) * 4 + (shape % 4) / 2,
                (0x235E + rom.readU16BE(at + 4)) & 65535,
                (relativeX + 128 + (short) rom.readU16BE(at + 6)) & 65535));
    }
    private static void addHud(List<Entry> out, RomByteReader rom, int player, int labelsFrame,
            int minute, int second, int[] ringDigits, Integer loserSeconds) {
        int y = 264 + player * 224;
        addFrame(out, rom, LABELS, labelsFrame, 144, 392 + player * 224, 0x8365, 0);
        int[] time = {minute, 10, second / 10, second % 10};
        for (int i = 0; i < time.length; i++) addFrame(out, rom, DIGITS, time[i], 184 + i * 8, y, 0x8371, 0);
        for (int i = 0; i < ringDigits.length; i++)
            addFrame(out, rom, DIGITS, ringDigits[i], 216 - ringDigits.length * 8 + i * 8, y + 16, 0x8371, 0);
        if (loserSeconds != null) {
            addFrame(out, rom, DIGITS, loserSeconds / 10, 272, 440 + player * 224, 0x8371, 0);
            addFrame(out, rom, DIGITS, loserSeconds % 10, 280, 440 + player * 224, 0x8371, 0);
        }
        addFrame(out, rom, LABELS, player + 4, 144, 392 + player * 224, player == 0 ? 0x8365 : 0x8340, 0);
    }
    private static void assertPage(Sonic2InterlaceSatBuilder.Pass pass, List<Entry> entries) {
        int base = pass.player() == 0 ? 0xF800 : 0xDD00;
        assertEquals(entries.size(), pass.spriteCount());
        boolean lastLinkZero = pass.player() == 0 ? entries.size() >= 80 : entries.size() == 80;
        byte[] raw = new byte[entries.size() * 8];
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i); int at = i * 8;
            put(raw, at, entry.y()); put(raw, at + 2, entry.size() * 256 + (lastLinkZero && i + 1 == entries.size() ? 0 : i + 1));
            put(raw, at + 4, entry.tile()); put(raw, at + 6, entry.x());
        }
        assertArrayEquals(raw, pass.ram().range(base, raw.length), "all eight CPU SAT bytes on page " + pass.player());
        if (!lastLinkZero) assertArrayEquals(new byte[4], pass.ram().range(base + raw.length, 4));
        assertEquals(320, pass.hardwareWords().size());
        for (int i = 0; i < 320; i++) assertEquals(pass.ram().readWord(base + i * 2), pass.hardwareWords().get(i));
    }
    private static void put(byte[] bytes, int at, int word) { bytes[at] = (byte) (word >>> 8); bytes[at + 1] = (byte) word; }
}
