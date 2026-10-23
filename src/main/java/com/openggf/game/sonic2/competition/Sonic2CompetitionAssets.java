package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * ROM-owned art and plane maps for the S2 native competition menus.
 * Loading needs neither a gameplay world nor graphics/global ROM services.
 * Maps are immutable; pattern and palette accessors return independent copies.
 */
public final class Sonic2CompetitionAssets {
    public enum ResultScreen { ACT, ZONE, GAME, SPECIAL_ACT, SPECIAL_ZONE }

    public enum Text {
        EMERALD_HILL(0x874A), MYSTIC_CAVE(0x8757), CASINO_NIGHT(0x8764),
        SPECIAL_STAGE(0x8771), SPECIAL(0x877F), ZONE(0x878C), STAGE(0x8792),
        GAME_OVER(0x8798), TIME_OVER(0x87A2), NO_GAME(0x87AC), TIED(0x87B4),
        ONE_PLAYER(0x87B9), TWO_PLAYER(0x87BD), BLANK(0x87C1);

        private final int address;

        Text(int address) {
            this.address = address;
        }
    }

    /** Row-major Mega Drive name-table words, including palette/flip/priority bits. */
    public record TileMap(int width, int height, List<Integer> words) {
        public TileMap {
            if (width <= 0 || height <= 0 || (long) width * height != words.size()) {
                throw new IllegalArgumentException("Plane-map dimensions do not match word count");
            }
            words = List.copyOf(words);
        }

        public int wordAt(int x, int y) {
            Objects.checkIndex(x, width);
            Objects.checkIndex(y, height);
            return words.get(y * width + x);
        }
    }

    private static final int SELECT_MAP = 0x9A60;
    private static final int RESULTS_POINTERS = 0x87DC;
    private static final int ICON_DESCRIPTORS = 0x8F7E;
    private static final int WINS_ART = 0x7C9AE;
    private static final int PREVIEW_RAM_BASE = 0xFF0330;
    private static final int COMPLETED_PREVIEW = 13;
    private static final int MENU_BOX_TILE = 0x70;
    private static final int PREVIEW_TILE = 0x90;
    private static final int FONT_TILE = 0x10;
    private static final int HIDDEN_PREVIEW = 0x207B;

    private final Pattern[] fontPatterns;
    private final Pattern[] menuBoxPatterns;
    private final Pattern[] previewPatterns;
    private final Pattern[] winsPatterns;
    private final Pattern[] backgroundPatterns;
    private final Pattern[] selectPatterns;
    private final Pattern[] resultsPatterns;
    private final Palette[] menuPalettes;
    private final Palette[] iconPalettes;
    private final TileMap panel;
    private final TileMap dimmedPanel;
    private final TileMap previewMap;
    private final TileMap backgroundMap;
    private final EnumMap<ResultScreen, TileMap> resultMaps;
    private final EnumMap<Text, List<Integer>> texts;
    private final List<Card> cards;

    private record Card(int x, int y, List<Integer> title, List<Integer> subtitle, int preview) { }

    private Sonic2CompetitionAssets(Rom rom) throws IOException {
        var reader = RomByteReader.fromRom(rom);
        fontPatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_FONT_STUFF_ADDR);
        menuBoxPatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_MENU_BOX_ADDR);
        previewPatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_LEVEL_SELECT_PICS_ADDR);
        winsPatterns = PatternDecompressor.nemesis(rom, WINS_ART);
        backgroundPatterns = PatternDecompressor.fromBytes(rom.readBytes(
                Sonic2Constants.ART_UNC_MENU_BACK_ADDR, Sonic2Constants.ART_UNC_MENU_BACK_SIZE));
        // MenuScreen / TwoPlayerResults use these same menu-background assets and animation.
        // MenuBackgroundAnimator consumes backgroundPatterns and writes VRAM tiles 1..10.
        backgroundMap = decodeMap(rom, Sonic2Constants.MAP_ENI_MENU_BACK_ADDR, 0x6000, 40, 28);
        // MenuScreen (s2.asm:11764-11781): two 17x12 copies, dimmed border on palette 1.
        panel = decodeMap(rom, SELECT_MAP, MENU_BOX_TILE, 17, 12);
        dimmedPanel = decodeMap(rom, SELECT_MAP, 0x2000 | MENU_BOX_TILE, 17, 12);
        previewMap = decodeMap(rom, Sonic2Constants.MAP_ENI_LEVEL_SELECT_ICON_ADDR, PREVIEW_TILE, 4, 45);
        resultMaps = new EnumMap<>(ResultScreen.class);
        // TwoPlayerResultsPointers (s2.asm:11681-11706): five map/setup pairs.
        for (var screen : ResultScreen.values()) {
            int address = reader.readU32BE(RESULTS_POINTERS + screen.ordinal() * 8);
            resultMaps.put(screen, decodeMap(rom, address, 0, 40, 28));
        }
        menuPalettes = new Palette[4];
        for (int i = 0; i < menuPalettes.length; i++) {
            menuPalettes[i] = readPalette(rom, Sonic2Constants.PAL_MENU_ADDR + i * 32);
        }
        iconPalettes = new Palette[4];
        var loadedCards = new ArrayList<Card>(4);
        // LevSel2PIconData (s2.asm:12016-12031): text pointers, VDP command, palette/RAM pointer.
        for (int i = 0; i < 4; i++) {
            int address = ICON_DESCRIPTORS + i * 16;
            int command = reader.readU32BE(address + 8);
            int vram = ((command >>> 16) & 0x3FFF) | ((command & 3) << 14);
            int cell = (vram - 0xC000) / 2;
            int descriptor = reader.readU32BE(address + 12);
            int preview = ((descriptor & 0xFFFFFF) - PREVIEW_RAM_BASE) / 24;
            loadedCards.add(new Card(cell % 64, cell / 64,
                    readText(reader, reader.readU32BE(address)),
                    readText(reader, reader.readU32BE(address + 4)), preview));
            iconPalettes[i] = readPalette(rom,
                    Sonic2Constants.PAL_LEVEL_ICONS_ADDR + (descriptor >>> 24) * 32);
        }
        cards = List.copyOf(loadedCards);
        texts = new EnumMap<>(Text.class);
        for (var text : Text.values()) texts.put(text, readText(reader, text.address));
        selectPatterns = bank(PREVIEW_TILE + previewPatterns.length);
        System.arraycopy(menuBoxPatterns, 0, selectPatterns, MENU_BOX_TILE, menuBoxPatterns.length);
        System.arraycopy(previewPatterns, 0, selectPatterns, PREVIEW_TILE, previewPatterns.length);
        resultsPatterns = bank(MENU_BOX_TILE + winsPatterns.length);
        System.arraycopy(winsPatterns, 0, resultsPatterns, MENU_BOX_TILE, winsPatterns.length);
    }

    public static Sonic2CompetitionAssets load(Rom rom) throws IOException {
        return new Sonic2CompetitionAssets(Objects.requireNonNull(rom, "rom"));
    }

    private Pattern[] bank(int length) {
        Pattern[] bank = new Pattern[length];
        Arrays.setAll(bank, i -> new Pattern());
        System.arraycopy(fontPatterns, 0, bank, FONT_TILE, fontPatterns.length);
        System.arraycopy(backgroundPatterns, 0, bank, 1, 10);
        return bank;
    }

    /** Native 2x2 selection layout; completed entries show the ROM cross and cannot hide it. */
    public TileMap selectMap(int selection, boolean[] completed) {
        Objects.checkIndex(selection, 4);
        if (completed.length != 4) throw new IllegalArgumentException("Four completion flags required");
        var destination = new ArrayList<Integer>(java.util.Collections.nCopies(40 * 28, 0));
        for (int i = 0; i < 4; i++) {
            var card = cards.get(i);
            boolean selected = i == selection;
            var cells = new ArrayList<>((selected ? panel : dimmedPanel).words());
            // Update2PLevSelSelection / ClearOld2PLevSelSelection (s2.asm:11911-12012).
            putText(cells, 0x48 / 2, card.title(), selected ? 0x6000 : 0);
            putText(cells, 0x94 / 2, card.subtitle(), selected ? 0x6000 : 0);
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 4; x++) {
                    int preview = completed[i] ? COMPLETED_PREVIEW : card.preview();
                    int word = selected || completed[i]
                            ? previewMap.words().get(preview * 12 + y * 4 + x) : HIDDEN_PREVIEW;
                    cells.set(0xD8 / 2 + y * 17 + x, word);
                }
            }
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 17; x++) {
                    destination.set((card.y() + y) * 40 + card.x() + x, cells.get(y * 17 + x));
                }
            }
        }
        return new TileMap(40, 28, destination);
    }

    /** Native three-digit counter; leading cells remain unchanged, zero emits the final digit. */
    public TileMap withCount(TileMap map, int byteOffset, int value, int paletteBits) {
        return withNumber(map, byteOffset, value, 3, paletteBits, true);
    }

    /** Native six-digit score; zero leaves the template's cells unchanged. */
    public TileMap withScore(TileMap map, int byteOffset, int value, int paletteBits) {
        return withNumber(map, byteOffset, value, 6, paletteBits, false);
    }

    private static TileMap withNumber(TileMap map, int byteOffset, int value, int digits,
                                      int paletteBits, boolean finalZero) {
        if (value < 0 || value >= (digits == 3 ? 1000 : 1_000_000)) {
            throw new IllegalArgumentException("Value exceeds native result field");
        }
        int start = wordOffset(map, byteOffset, digits);
        var cells = new ArrayList<>(map.words());
        int divisor = digits == 3 ? 100 : 100_000;
        boolean visible = false;
        // sub_86B0 / sub_86F6 (s2.asm:11558-11627): leading positions are not overwritten.
        for (int i = 0; i < digits; i++, divisor /= 10) {
            int digit = value / divisor;
            value %= divisor;
            visible |= digit != 0;
            if (visible || finalZero && i == digits - 1) {
                cells.set(start + i, (paletteBits & 0xF800) | (0x10 + digit));
            }
        }
        return new TileMap(map.width(), map.height(), cells);
    }

    public TileMap withText(TileMap map, int byteOffset, Text text, int paletteBits) {
        var words = texts.get(Objects.requireNonNull(text, "text"));
        int start = wordOffset(map, byteOffset, words.size());
        var cells = new ArrayList<>(map.words());
        putText(cells, start, words, paletteBits);
        return new TileMap(map.width(), map.height(), cells);
    }

    private static int wordOffset(TileMap map, int byteOffset, int count) {
        if ((byteOffset & 1) != 0) throw new IllegalArgumentException("Unaligned plane-map offset");
        int start = byteOffset / 2;
        Objects.checkFromIndexSize(start, count, map.words().size());
        return start;
    }

    private static void putText(List<Integer> cells, int start, List<Integer> text, int paletteBits) {
        for (int i = 0; i < text.size(); i++) cells.set(start + i, (paletteBits & 0xFF00) | text.get(i));
    }

    private static List<Integer> readText(RomByteReader reader, int address) {
        int count = reader.readU8(address) + 1;
        var text = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) text.add(reader.readU8(address + i + 1));
        return List.copyOf(text);
    }

    private static TileMap decodeMap(Rom rom, int address, int base, int width, int height) throws IOException {
        byte[] bytes;
        try (var channel = RomChannel.at(rom, address)) {
            bytes = EnigmaReader.decompress(channel, base);
        }
        if (bytes.length != width * height * 2) {
            throw new IOException("Unexpected competition plane-map dimensions at 0x"
                    + Integer.toHexString(address));
        }
        var words = new ArrayList<Integer>(bytes.length / 2);
        for (int i = 0; i < bytes.length; i += 2) words.add((bytes[i] & 0xFF) << 8 | bytes[i + 1] & 0xFF);
        return new TileMap(width, height, words);
    }

    private static Palette readPalette(Rom rom, int address) throws IOException {
        var palette = new Palette();
        palette.fromSegaFormat(rom.readBytes(address, 32));
        return palette;
    }

    private static Pattern[] copyPatterns(Pattern[] source) {
        Pattern[] copy = new Pattern[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = new Pattern();
            copy[i].copyFrom(source[i]);
        }
        return copy;
    }

    public Pattern[] fontPatterns() { return copyPatterns(fontPatterns); }
    public Pattern[] menuBoxPatterns() { return copyPatterns(menuBoxPatterns); }
    public Pattern[] previewPatterns() { return copyPatterns(previewPatterns); }
    public Pattern[] winsPatterns() { return copyPatterns(winsPatterns); }
    public Pattern[] backgroundPatterns() { return copyPatterns(backgroundPatterns); }
    public Pattern[] selectPatterns() { return copyPatterns(selectPatterns); }
    public Pattern[] resultsPatterns() { return copyPatterns(resultsPatterns); }
    public TileMap selectPanel(boolean dimmed) { return dimmed ? dimmedPanel : panel; }
    public TileMap backgroundMap() { return backgroundMap; }
    public TileMap resultMap(ResultScreen screen) { return resultMaps.get(Objects.requireNonNull(screen)); }

    public Palette[] menuPalettes() {
        return Arrays.stream(menuPalettes).map(Palette::deepCopy).toArray(Palette[]::new);
    }

    public Palette iconPalette(int selection) {
        return iconPalettes[Objects.checkIndex(selection, 4)].deepCopy();
    }

    /** Preview art uses the third CRAM line; highlight text retains the fourth menu line. */
    public Palette[] selectionPalettes(int selection) {
        var palettes = menuPalettes();
        palettes[2] = iconPalette(selection);
        return palettes;
    }
}
