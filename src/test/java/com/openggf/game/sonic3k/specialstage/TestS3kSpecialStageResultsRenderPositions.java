package com.openggf.game.sonic3k.specialstage;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.PatternDesc;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Independent ROM geometry oracle for arrived result rows (044fa46 ObjDat2_2E834,
 * LevResults_DisplayScore and Map_Results). No image offsets or fitted glyph positions.
 * Bonus values are native word counters: seven child BCD digits plus the parent's
 * permanent zero, not seven digits of a multiplied value. Widths check the engine's
 * documented centering extension; these component inlets do not certify cold routes.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageResultsRenderPositions {
    private static final int ROW_TABLE = 0x2E834;
    private static final Comparator<Tile> ORDER = Comparator.comparingInt(Tile::x)
            .thenComparingInt(Tile::y).thenComparingInt(Tile::pattern);

    @AfterEach
    void reset() { TestEnvironment.resetAll(); }

    @Test
    void oracleReadsTheShippedRowAndMappingShapes() throws Exception {
        Rom rom = GameServices.rom().getRom();
        assertEquals(0x120, rom.read16BitAddr(ROW_TABLE + 4));
        assertEquals(0x178, rom.read16BitAddr(ROW_TABLE + 2 * 12 + 4));
        assertEquals(0x17, rom.readByte(ROW_TABLE + 10) & 255);
        assertEquals(0, frame(rom, 0).size());
        assertEquals(List.of(new Piece(0, 0, 1, 0xA520)), frame(rom, 1));
        var score = frame(rom, 0x17);
        assertEquals(5, score.size());
        assertEquals(0x28, score.get(2).x());
        assertEquals(0x6E4, score.get(2).tile() & 0x7FF);
        assertEquals(0x40, score.get(3).x());
        assertEquals(0x6EA, score.get(3).tile() & 0x7FF);
    }

    static Stream<Arguments> scoreProducts() {
        return Stream.of(320, 352, 400, 528, 800).flatMap(width -> Stream.of(
                Arguments.of(width, PlayerCharacter.TAILS_ALONE, false),
                Arguments.of(width, PlayerCharacter.TAILS_ALONE, true),
                Arguments.of(width, PlayerCharacter.KNUCKLES, true)));
    }

    @ParameterizedTest
    @MethodSource("scoreProducts")
    void scoreRowUsesTheUnadjustedRomPiecePositions(int width, PlayerCharacter character, boolean superStage) throws Exception {
        var screen = screen(width, character, superStage);
        var elem = arrivedElement(screen, 0);
        Rom rom = GameServices.rom().getRom();
        var expected = new ArrayList<Tile>();
        // loc_2EA1E: the locked-on Super stage selects the palette-line twins,
        // with the further +5 for Knuckles. The native piece origins remain authoritative.
        int mapping = (rom.readByte(ROW_TABLE + 10) & 255)
                + (superStage ? 0x1A + (character == PlayerCharacter.KNUCKLES ? 5 : 0) : 0);
        addFrame(expected, rom, mapping,
                rom.read16BitAddr(ROW_TABLE + 4) - 0x80 + (width - 320) / 2,
                rom.read16BitAddr(ROW_TABLE + 8) - 0x80, 0);
        assertEquals(expected.stream().sorted(ORDER).toList(), commands(screen, elem, "renderScoreRow", null));
    }

    static Stream<Arguments> bonusProducts() {
        return Stream.of(320, 352, 400, 528, 800).flatMap(width ->
                Stream.of(0, 1, 9, 10, 125, 5000, 65535).flatMap(value ->
                        Stream.of(2, 4).map(row -> Arguments.of(width, value, row))));
    }

    @ParameterizedTest
    @MethodSource("bonusProducts")
    void bonusRowsRenderTheNativeParentAndSevenChildSlots(int width, int value, int row) throws Exception {
        var screen = screen(width);
        var elem = arrivedElement(screen, row);
        Rom rom = GameServices.rom().getRom();
        int data = ROW_TABLE + row * 12;
        int x = rom.read16BitAddr(data + 4) - 0x80 + (width - 320) / 2;
        int y = rom.read16BitAddr(data + 8) - 0x80;
        var expected = new ArrayList<Tile>();
        addFrame(expected, rom, rom.readByte(data + 10) & 255, x, y, 0x98);
        String decimal = String.format(java.util.Locale.ROOT, "%07d", value & 0xFFFF);
        boolean visible = false;
        for (int i = 0; i < decimal.length(); i++) {
            int digit = decimal.charAt(i) - '0';
            visible |= digit != 0;
            if (visible) addFrame(expected, rom, digit + 1, x - 0x38 + i * 8, y, 0x98);
        }
        assertEquals(expected.stream().sorted(ORDER).toList(), commands(screen, elem, "renderBonusDigits", value));
    }

    static Stream<Arguments> clearedProducts() {
        return Stream.of(320, 352, 400, 528, 800).flatMap(width ->
                Stream.of(2, 4).map(row -> Arguments.of(width, row)));
    }

    @ParameterizedTest
    @MethodSource("clearedProducts")
    void clearingTheChildCountRetainsTheParentsZero(int width, int row) throws Exception {
        var screen = screen(width);
        var elem = arrivedElement(screen, row);
        Field cleared = elem.getClass().getDeclaredField("digitsCleared");
        cleared.setAccessible(true);
        cleared.setBoolean(elem, true);
        Rom rom = GameServices.rom().getRom();
        int data = ROW_TABLE + row * 12;
        var expected = new ArrayList<Tile>();
        // loc_2E6C8 clears $16 only. Render_Sprites loc_1AEE4 still draws mapping_frame
        // before loc_1AF1C tests the now-zero child count; loc_2EC44 keeps drawing the row.
        addFrame(expected, rom, rom.readByte(data + 10) & 255,
                rom.read16BitAddr(data + 4) - 0x80 + (width - 320) / 2,
                rom.read16BitAddr(data + 8) - 0x80, 0x98);
        assertEquals(expected.stream().sorted(ORDER).toList(), commands(screen, elem, "renderElement", null));
    }

    static Stream<Arguments> priorityProducts() {
        return Stream.of(320, 800).flatMap(width ->
                Stream.of(false, true).map(superStage -> Arguments.of(width, superStage)));
    }

    @ParameterizedTest
    @MethodSource("priorityProducts")
    void nativeFirstOpaqueSpriteWinsAcrossPiecesAndRows(int width, boolean superStage) throws Exception {
        var screen = screen(width, PlayerCharacter.TAILS_ALONE, superStage);
        Field elements = S3kSpecialStageResultsScreen.class.getDeclaredField("phase1Elements");
        elements.setAccessible(true);
        var rows = (List<?>) elements.get(screen);
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            Field visible = row.getClass().getDeclaredField("visible");
            visible.setAccessible(true); visible.setBoolean(row, i < 5);
            if (i < 5) arrivedElement(screen, i);
        }
        invoke(screen, "updateDynamicScorePatterns");
        invoke(screen, "ensureArtCached");
        Field art = S3kSpecialStageResultsScreen.class.getDeclaredField("combinedPatterns");
        art.setAccessible(true);
        Pattern[] patterns = (Pattern[]) art.get(screen);
        Rom rom = GameServices.rom().getRom();
        int[][] palette = new int[4][16];
        for (int line = 0; line < 4; line++) for (int c = 0; c < 16; c++)
            palette[line][c] = rom.read16BitAddr(Sonic3kConstants.PAL_RESULTS_ADDR + line * 32 + c * 2);
        if (superStage) {
            // SpecialStage_Results loc_2E12C and sub_2E2C0, before the later sanctuary fade.
            palette[1] = palette[0].clone(); palette[1][5] = 0x00EE;
            java.util.Arrays.fill(palette[2], 0x0CCC); java.util.Arrays.fill(palette[3], 0x0CCC);
        }
        var nativeOrder = new ArrayList<Ink>();
        for (int row = 0; row < 5; row++) {
            int data = ROW_TABLE + row * 12;
            boolean bonus = row == 2 || row == 4;
            int map = (rom.readByte(data + 10) & 255) + (superStage && !bonus ? 0x1A : 0);
            int artTile = bonus ? 0x98 : 0; // Initial tally labels already encode their palette.
            addNativeInk(nativeOrder, rom, map,
                    rom.read16BitAddr(data + 4) - 0x80 + (width - 320) / 2,
                    rom.read16BitAddr(data + 8) - 0x80, artTile);
        }
        // Render_Sprites emits ascending SST/piece order; the VDP keeps the first
        // nontransparent sprite pixel. GL alpha draws instead replace the last pixel.
        int[] expected = raster(nativeOrder, patterns, palette, width, true);
        var submitted = captureInk(() -> screen.appendRenderCommands(new ArrayList<>()));
        int[] actual = raster(submitted, patterns, palette, width, false);
        int differences = 0, first = -1;
        for (int i = 0; i < expected.length; i++) if (expected[i] != actual[i]) {
            differences++; if (first < 0) first = i;
        }
        assertEquals(0, differences, "native opaque-sprite overlap: " + differences
                + " different pixels; first " + first % width + "," + first / width);
    }

    static Stream<Boolean> iconReplacementProducts() { return Stream.of(false, true); }

    @ParameterizedTest
    @MethodSource("iconReplacementProducts")
    void bothRevealTablesRetainTheContinueInItsEarlierFreedSlot(boolean skSideOrigin) {
        GameServices.graphics().initHeadless();
        GameServices.gameState().resetSession();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(1, 1, 1, 1, 1, 1, 1), false);
        var screen = new S3kSpecialStageResultsScreen(50, true, 1, 0, 7,
                PlayerCharacter.SONIC_AND_TAILS, false, skSideOrigin);
        boolean seenContinue = false;
        int frame = 0;
        while (screen.revealFramesForTest().isEmpty() && !screen.isComplete() && frame < 2000) {
            screen.update(++frame, null);
            seenContinue |= screen.continueIconShownForTest();
        }
        assertTrue(seenContinue, "the admitted ring product must actually create its continue icon");
        assertEquals(skSideOrigin ? 3 : 6, screen.revealFramesForTest().size());
        // loc_2EAC8 deleted the successful path's failure-message slot43 long before
        // loc_2E4EA allocates after control29. The seven Chaos indicators36..42 remain.
        // Thus the icon is in43, before either replacement table (44..49 or44..46).
        assertTrue(screen.continueIconShownForTest(),
                "both reveals retain the icon in the earlier freed failure-message slot");
    }

    private static void invoke(S3kSpecialStageResultsScreen screen, String name) throws Exception {
        Method method = S3kSpecialStageResultsScreen.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(screen);
    }

    private static void addNativeInk(List<Ink> target, Rom rom, int index, int x, int y, int artTile) throws Exception {
        for (var p : frame(rom, index)) {
            int word = (p.tile() + artTile) & 0xFFFF;
            int width = ((p.size() >>> 2) & 3) + 1, height = (p.size() & 3) + 1;
            for (int col = 0; col < width; col++) for (int row = 0; row < height; row++) {
                int dx = (word & 0x800) == 0 ? col : width - 1 - col;
                int dy = (word & 0x1000) == 0 ? row : height - 1 - row;
                int id = PatternAtlasRange.SPECIAL_STAGE_RESULTS.base()
                        + (word & 0x7FF) - Sonic3kConstants.VRAM_SS_RESULTS_BASE + col * height + row;
                target.add(new Ink(id, x + p.x() + dx * 8, y + p.y() + dy * 8, word));
            }
        }
    }

    private static int[] raster(List<Ink> commands, Pattern[] patterns, int[][] palette, int width, boolean firstWins) {
        int[] pixels = new int[width * 224]; java.util.Arrays.fill(pixels, 0x0CCC);
        boolean[] opaque = new boolean[pixels.length];
        for (var tile : commands) {
            Pattern pattern = patterns[tile.pattern() - PatternAtlasRange.SPECIAL_STAGE_RESULTS.base()];
            for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) {
                int x = tile.x() + px, y = tile.y() + py;
                if (x < 0 || x >= width || y < 0 || y >= 224) continue;
                int sx = (tile.flags() & 0x800) == 0 ? px : 7 - px;
                int sy = (tile.flags() & 0x1000) == 0 ? py : 7 - py;
                int colour = pattern.getPixel(sx, sy) & 15;
                if (colour == 0) continue;
                int offset = y * width + x;
                if (!firstWins || !opaque[offset]) pixels[offset] = palette[(tile.flags() >>> 13) & 3][colour];
                opaque[offset] = true;
            }
        }
        return pixels;
    }

    private static S3kSpecialStageResultsScreen screen(int width) {
        return screen(width, PlayerCharacter.TAILS_ALONE, false);
    }

    private static S3kSpecialStageResultsScreen screen(int width, PlayerCharacter character, boolean superStage) {
        GameServices.graphics().initHeadless();
        GameServices.gameState().resetSession();
        var screen = new S3kSpecialStageResultsScreen(0, false, 1, 0, 0, character, superStage, false);
        screen.setViewportWidth(width);
        return screen;
    }

    private static Object arrivedElement(S3kSpecialStageResultsScreen screen, int row) throws Exception {
        Field elements = S3kSpecialStageResultsScreen.class.getDeclaredField("phase1Elements");
        elements.setAccessible(true);
        Object elem = ((List<?>) elements.get(screen)).get(row);
        Field target = elem.getClass().getDeclaredField("targetX");
        Field current = elem.getClass().getDeclaredField("currentX");
        target.setAccessible(true); current.setAccessible(true);
        current.setInt(elem, target.getInt(elem));
        return elem;
    }

    private static List<Tile> commands(S3kSpecialStageResultsScreen screen, Object elem, String method, Integer value) throws Exception {
        return captureInk(() -> {
            Method draw = value == null
                    ? S3kSpecialStageResultsScreen.class.getDeclaredMethod(method, elem.getClass())
                    : S3kSpecialStageResultsScreen.class.getDeclaredMethod(method, elem.getClass(), int.class);
            draw.setAccessible(true);
            if (value == null) draw.invoke(screen, elem);
            else draw.invoke(screen, elem, value.intValue());
        }).stream().map(i -> new Tile(i.pattern(), i.x(), i.y())).sorted(ORDER).toList();
    }

    private static List<Ink> captureInk(CheckedDraw draw) throws Exception {
        var result = new ArrayList<Ink>();
        var graphics = mock(GraphicsManager.class);
        var state = GameServices.gameState();
        doAnswer(call -> {
            PatternDesc descriptor = call.getArgument(1);
            result.add(new Ink(call.getArgument(0), call.getArgument(2), call.getArgument(3), descriptor.get()));
            return null;
        }).when(graphics).renderPatternWithId(anyInt(), any(PatternDesc.class), anyInt(), anyInt());
        try (var services = mockStatic(GameServices.class, call -> {
            return switch (call.getMethod().getName()) {
                case "graphics" -> graphics;
                case "gameState" -> state;
                default -> throw new AssertionError("Pure results draw touched " + call.getMethod().getName());
            };
        })) { draw.run(); }
        return result;
    }

    private static List<Piece> frame(Rom rom, int index) throws Exception {
        int base = Sonic3kConstants.MAP_RESULTS_ADDR;
        int address = base + (short) rom.read16BitAddr(base + index * 2);
        int count = rom.read16BitAddr(address);
        assertTrue(count <= 32, "bounded native mapping frame");
        var result = new ArrayList<Piece>();
        for (int i = 0; i < count; i++) {
            int p = address + 2 + i * 6;
            result.add(new Piece((short) rom.read16BitAddr(p + 4), rom.readByte(p),
                    rom.readByte(p + 1) & 255, rom.read16BitAddr(p + 2)));
        }
        return result;
    }

    private static void addFrame(List<Tile> target, Rom rom, int index, int x, int y, int artTile) throws Exception {
        for (var p : frame(rom, index)) {
            int width = ((p.size() >>> 2) & 3) + 1;
            int height = (p.size() & 3) + 1;
            for (int col = 0; col < width; col++) for (int row = 0; row < height; row++) {
                int dx = (p.tile() & 0x800) == 0 ? col : width - 1 - col;
                int dy = (p.tile() & 0x1000) == 0 ? row : height - 1 - row;
                int pattern = PatternAtlasRange.SPECIAL_STAGE_RESULTS.base()
                        + (p.tile() & 0x7FF) - Sonic3kConstants.VRAM_SS_RESULTS_BASE + artTile + col * height + row;
                target.add(new Tile(pattern, x + p.x() + dx * 8, y + p.y() + dy * 8));
            }
        }
    }

    @FunctionalInterface
    private interface CheckedDraw { void run() throws Exception; }
    private record Ink(int pattern, int x, int y, int flags) { }
    private record Piece(int x, int y, int size, int tile) { }
    private record Tile(int pattern, int x, int y) { }
}
