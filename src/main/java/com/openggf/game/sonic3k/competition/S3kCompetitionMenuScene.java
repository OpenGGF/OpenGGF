package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.openggf.game.sonic3k.competition.S3kCompetitionAssets.MenuPhase;
import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode;

/**
 * CPU-only composition of the native competition menus. The copy lists, object
 * records, mappings, glyphs, art and palettes all come from the supplied ROM.
 * No text rasterizer or ordinary gameplay renderer participates in this scene.
 */
public final class S3kCompetitionMenuScene {
    public static final int PLANE_WIDTH = 64;
    public static final int PLANE_HEIGHT = 64;
    public static final int SCREEN_WIDTH = 320;
    public static final int SCREEN_HEIGHT = 224;

    /**
     * Native SAT order is front to back (bucket, then object slot). Origins are
     * screen coordinates: native SAT position minus $80. Mapping pieces retain
     * column-major tiles and the fully added native tile-word flags.
     */
    public record SpriteGroup(int nativeSlot, int drawPriority, int x, int y,
            String mappingKey, int mappingFrame, int nativeArtWord,
            List<SpriteMappingPiece> pieces) {
        public SpriteGroup { pieces = List.copyOf(pieces); }
        public int nativePriorityWord() { return drawPriority; }
        public int priorityBucket() { return RenderPriority.fromS3kWord(drawPriority); }
    }

    /** A detached immutable frame; mutable engine asset types are copied on access. */
    public static final class Scene {
        private final MenuPhase phase;
        private final int frame, horizontalScrollA, verticalScrollA;
        private final int[] planeA, planeB;
        private final PatternBank patterns;
        private final Palette[] palettes;
        private final List<SpriteGroup> sprites;

        private Scene(MenuPhase phase, int frame, int[] planeA, int[] planeB,
                PatternBank patterns, Palette[] palettes, List<SpriteGroup> sprites,
                int horizontalScrollA, int verticalScrollA) {
            this.phase = phase; this.frame = frame;
            this.planeA = planeA.clone(); this.planeB = planeB.clone();
            this.patterns = patterns; this.palettes = copyPalettes(palettes);
            this.sprites = List.copyOf(sprites);
            this.horizontalScrollA = horizontalScrollA; this.verticalScrollA = verticalScrollA;
        }
        public MenuPhase phase() { return phase; }
        public int frame() { return frame; }
        public int planeWidth() { return PLANE_WIDTH; }
        public int planeHeight() { return PLANE_HEIGHT; }
        public int[] planeA() { return planeA.clone(); }
        public int[] planeB() { return planeB.clone(); }
        public int planeATile(int x, int y) { return planeA[tileOffset(x, y)]; }
        public int planeBTile(int x, int y) { return planeB[tileOffset(x, y)]; }
        /** Cache uploads by phase: each phase owns one immutable native VRAM bank. */
        public Pattern[] patterns() { return patterns.copy(); }
        public Palette[] palettes() { return copyPalettes(palettes); }
        public List<SpriteGroup> sprites() { return sprites; }
        public int horizontalScrollA() { return horizontalScrollA; }
        public int verticalScrollA() { return verticalScrollA; }
        public int horizontalScrollB() { return 0; }
        public int verticalScrollB() { return 0; }
        public int backdropColorIndex() { return phase == MenuPhase.MODE ? 0x30 : 0; }
        public boolean shadowHighlight() { return phase != MenuPhase.MODE; }
    }

    private record ObjectData(int routine, int x, int y, int artWord, int frame, int parameter) { }
    private record SheetKey(MenuPhase phase, String key, int artWord) { }
    private record PatternBank(Pattern[] data) {
        PatternBank { data = copyPatterns(data); }
        Pattern[] copy() { return copyPatterns(data); }
    }

    // Locked-on owning PC-relative LEAs: AC86->B0F4, AD1C->B1F0,
    // AD26->B282, B400->B6C8, B80C->BBAC, B8AE->BB4A, BCAE->BF96.
    private static final int MODE_OBJECTS = 0xAB48;
    private static final int ARENA_COPIES = 0xB0F4;
    private static final int MATCH_OBJECTS = 0xB1F0;
    private static final int TIME_ATTACK_OBJECTS = 0xB282;
    private static final int PLAYER_OBJECTS = 0xB6C8;
    private static final int RESULTS_OBJECTS = 0xBB4A;
    private static final int RESULTS_COPIES = 0xBBAC;
    private static final int RECORD_OBJECTS = 0xBF58;
    private static final int RECORD_COPIES = 0xBF96;
    private static final int RECORD_LEVEL_NAMES = 0xBFF2;
    private static final int SINE = 0x1D64;
    private static final int NUMBERS = 0x20A130;
    private static final int STATIC = 0xB4F6, SELECT_P1 = 0xB52C, SELECT_P2 = 0xB508;
    private static final int WHEEL_P1 = 0xB656, WHEEL_P2 = 0xB64E, PRESS_START = 0xB6A6;
    private static final int ZONE_SELECT = 0xAF24, RESULT_PLAYER = 0xB9A6;
    private static final int RESULT_WINNER = 0xB9CA, RECORD_CHARACTER = 0xBEF8;

    private final RomByteReader rom;
    private final S3kCompetitionAssets assets;
    private final S3kCompetitionResources resources;
    private final Map<MenuPhase, PatternBank> banks = new EnumMap<>(MenuPhase.class);
    private final Map<MenuPhase, int[]> backgrounds = new EnumMap<>(MenuPhase.class);
    private final Map<SheetKey, ObjectSpriteSheet> sheetCache = new HashMap<>();

    public S3kCompetitionMenuScene(Rom rom, S3kCompetitionAssets assets) throws IOException {
        this.rom = RomByteReader.fromRom(Objects.requireNonNull(rom));
        this.assets = Objects.requireNonNull(assets);
        resources = assets.resources();
        for (MenuPhase phase : MenuPhase.values()) {
            banks.put(phase, new PatternBank(assets.menuPatterns(phase)));
            backgrounds.put(phase, assets.menuBackground(phase));
        }
    }

    public Scene compose(S3kCompetitionScreen.MenuView view, S3kCompetitionRecords records) throws IOException {
        return compose(view, records, -1);
    }

    /** newRecordIndex is the native byte: -1/$FF means no newly inserted record. */
    public Scene compose(S3kCompetitionScreen.MenuView view, S3kCompetitionRecords records,
            int newRecordIndex) throws IOException {
        Objects.requireNonNull(view, "view");
        MenuPhase phase = switch (view.phase()) {
            case MODE -> MenuPhase.MODE;
            case PLAYER_SELECT -> MenuPhase.PLAYER_SELECT;
            case ARENA_SELECT -> MenuPhase.ARENA_SELECT;
            case RESULTS -> MenuPhase.RESULTS;
            case TIME_ATTACK_RESULTS -> MenuPhase.TIME_ATTACK_RESULTS;
            case RACE -> throw new IllegalArgumentException("Race frames belong to the native arena view");
        };
        int[] a = new int[PLANE_WIDTH * PLANE_HEIGHT], b = new int[a.length];
        // Clear_DisplayData zeros plane B; sub_C02A fills only plane A's $2000 bytes.
        Arrays.fill(a, 0x8000);
        copy(a, b, backgrounds.get(phase), 0, 0xE000, 40, 28);
        List<SpriteGroup> groups = new ArrayList<>();
        Arena paletteArena = null;
        switch (phase) {
            case MODE -> mode(view, groups);
            case PLAYER_SELECT -> {
                clear(a, 0x220, 9, 8); clear(a, 0x820, 9, 8);
                selection(view, resources.at(PLAYER_OBJECTS), groups);
            }
            case ARENA_SELECT -> {
                Objects.requireNonNull(records, "Arena selection requires the supplied native records");
                copyList(a, b, resources.at(ARENA_COPIES), phase);
                int window = (0x20 + view.verticalScroll()) * 16 + 4;
                if (view.mode() == Mode.TIME_ATTACK) clear(a, window + 0x300, 9, 8);
                else { clear(a, window, 9, 8); clear(a, window + 0x600, 9, 8); }
                for (int row = 0; row < 5; row++) putTime(a, 0x2D2 + row * 0x480,
                        records.entries(S3kCompetitionSession.GRAND_PRIX_ORDER.get(row)).getFirst().packedTime(), false);
                selection(view, view.mode() == Mode.TIME_ATTACK ? TIME_ATTACK_OBJECTS : MATCH_OBJECTS, groups);
                if (arenaCursorVisible(view)) {
                    paletteArena = S3kCompetitionSession.GRAND_PRIX_ORDER.get(view.arenaRow());
                    add(groups, phase, 12, 0, 0xB0, view.arenaRow() * 0x48 + 0xA8 - view.verticalScroll(),
                            "competition_menu_select", view.arenaRow() + 6, 0xC29F);
                }
            }
            case RESULTS -> grandPrix(view, a, b, groups);
            case TIME_ATTACK_RESULTS -> timeAttack(view, Objects.requireNonNull(records,
                    "Time Attack results require the supplied native records"), newRecordIndex, a, b, groups);
        }
        groups.sort(Comparator.comparingInt(SpriteGroup::drawPriority).thenComparingInt(SpriteGroup::nativeSlot));
        // TimeAttack_Records writes -$88 before its first frame, independently of menu scroll state.
        int hScroll = phase == MenuPhase.TIME_ATTACK_RESULTS ? -0x88 : view.horizontalScroll();
        int vScroll = phase == MenuPhase.ARENA_SELECT ? view.verticalScroll() : 0;
        return new Scene(phase, view.frame(), a, b, banks.get(phase), assets.menuPalette(phase, paletteArena),
                groups, hScroll, vScroll);
    }

    private void mode(S3kCompetitionScreen.MenuView view, List<SpriteGroup> groups) throws IOException {
        int count = rom.readU16BE(resources.at(MODE_OBJECTS)) + 1;
        for (int slot = 0; slot < count; slot++) {
            int at = resources.at(MODE_OBJECTS) + 2 + slot * 10;
            int routine = resources.menuActor(readLong(at)), x = rom.readU16BE(at + 4), y = rom.readU16BE(at + 6);
            int frame = rom.readU8(at + 8), parameter = (byte) rom.readU8(at + 9), art = 0x34A;
            if (routine == 0xAB00) {
                if (view.modeRow() > 1 || (view.frame() & 8) == 0) continue;
                x = view.itemsEnabled() ? 0x15C : 0x17C;
            } else if (routine == 0xAADC) {
                if (parameter >= 0) art |= parameter == view.modeRow() ? 0x4000 : 0x2000;
            } else throw new IOException("Unexpected competition mode object routine " + Integer.toHexString(routine));
            add(groups, MenuPhase.MODE, slot, 0, x, y, "competition_menu_mode", frame, art);
        }
    }

    /** Obj_CompetitionPlayerSprite (sonic3k.asm:14643-14667) uses native sine words and arithmetic shifts. */
    private void selection(S3kCompetitionScreen.MenuView view, int table, List<SpriteGroup> groups) throws IOException {
        MenuPhase phase = view.phase() == S3kCompetitionScreen.Phase.PLAYER_SELECT
                ? MenuPhase.PLAYER_SELECT : MenuPhase.ARENA_SELECT;
        List<SpriteGroup> wheel0 = new ArrayList<>(), wheel1 = new ArrayList<>();
        int count = rom.readU16BE(table) + 1;
        for (int slot = 0; slot < count; slot++) {
            ObjectData o = object(table, slot);
            int nativeSlot = slot + (table == resources.at(PLAYER_OBJECTS) ? 1 : 0);
            int x = o.x() + view.horizontalScroll(), y = o.y(), frame = o.frame(), art = 0x829F + o.artWord();
            switch (o.routine()) {
                case STATIC -> { }
                case SELECT_P1, SELECT_P2 -> {
                    int player = o.routine() == SELECT_P1 ? 0 : 1;
                    frame = player == 1 && view.participants() == 1 ? 0
                            : view.confirmed().get(player) ? 1 : view.frame() >>> 3 & 1;
                }
                case WHEEL_P1, WHEEL_P2 -> {
                    int player = o.routine() == WHEEL_P1 ? 0 : 1;
                    if (player == 1 && view.participants() == 1) continue;
                    int angle = view.wheelAngles().get(player) + o.parameter() * 0x55 & 0xFF;
                    x += sine(angle + 0x40) >> 4; y += sine(angle) >> 5;
                    List<SpriteGroup> wheel = player == 0 ? wheel0 : wheel1;
                    add(wheel, phase, nativeSlot, 0, x, y, "competition_menu_player", frame, 0x25C9);
                    continue;
                }
                case PRESS_START -> {
                    if (view.participants() == 2 || (view.frame() & 0x20) == 0) continue;
                }
                case ZONE_SELECT -> {
                    if (!arenaCursorVisible(view) || (view.frame() & 8) == 0) continue;
                    // This object does not add H_scroll_buffer: its X remains the ObjDat value.
                    x = o.x(); y = view.arenaRow() * 0x48 + 0xAC - view.verticalScroll();
                }
                default -> throw new IOException("Unexpected competition selection routine " + Integer.toHexString(o.routine()));
            }
            add(groups, phase, nativeSlot, 0, x, y, "competition_menu_select", frame, art);
        }
        depthSortWheel(groups, wheel0, phase, view, 0); depthSortWheel(groups, wheel1, phase, view, 1);
    }

    /** sub_C0B8 puts the lowest wheel actor in bucket 1/high plane priority, then buckets 2 and 3. */
    private void depthSortWheel(List<SpriteGroup> groups, List<SpriteGroup> wheel, MenuPhase phase,
            S3kCompetitionScreen.MenuView view, int player) throws IOException {
        // The native two-pass three-element sort swaps equal-Y actors too (loc_C0F6).
        List<SpriteGroup> sorted = new ArrayList<>(wheel);
        if (sorted.size() != 3) { groups.addAll(sorted); return; }
        int a = 0, b = 1, c = 2;
        for (int pass = 0; pass < 2; pass++) {
            if (sorted.get(a).y() <= sorted.get(b).y()) { int t = a; a = b; b = t; }
            if (sorted.get(b).y() <= sorted.get(c).y()) { int t = b; b = c; c = t; }
        }
        int[] order = {a, b, c};
        for (int rank = 0; rank < order.length; rank++) {
            SpriteGroup g = sorted.get(order[rank]);
            int parameter = g.nativeSlot() - (player == 0 ? 3 : 8);
            int queuedPriority = view.wheelQueuePriorityWords().isEmpty() ? (rank + 1) * 0x80
                    : view.wheelQueuePriorityWords().get(player * 3 + parameter);
            add(groups, phase, g.nativeSlot(), queuedPriority, g.x() + 0x80, g.y() + 0x80,
                    g.mappingKey(), g.mappingFrame(), rank == 0 ? 0xA5C9 : 0x25C9);
        }
    }

    private void grandPrix(S3kCompetitionScreen.MenuView view, int[] a, int[] b,
            List<SpriteGroup> groups) throws IOException {
        var result = Objects.requireNonNull(view.result(), "Grand Prix results require the actual session result");
        clear(a, 0x1AA, 9, 8); clear(a, 0x82A, 9, 8);
        copyList(a, b, resources.at(RESULTS_COPIES), MenuPhase.RESULTS);
        long[] totals = new long[2]; int[] wins = new int[2];
        for (int slot = 0; slot < 2; slot++) {
            List<Integer> times = result.grandPrixTimes().get(slot);
            for (int row = 0; row < 5; row++) putTime(a, 0x15A + slot * 0x680 + row * 0x100,
                    S3kCompetitionRecords.packCentiseconds(times.get(row)), false);
            totals[slot] = sumNativeTimes(times);
        }
        int fastest = totals[0] == totals[1] ? -1 : totals[0] < totals[1] ? 0 : 1;
        for (int slot = 0; slot < 2; slot++) putTime(a, 0x65A + slot * 0x680, totals[slot],
                // sub_BA04 initially writes normal glyphs. loc_B922 starts
                // the palette alternation only after the first loop V-sync.
                view.mainLoopStarted() && slot == fastest && (view.frame() & 8) == 0);
        for (int row = 0; row < 5; row++) {
            int first = result.grandPrixTimes().get(0).get(row), second = result.grandPrixTimes().get(1).get(row);
            if (first < second) wins[0]++; else if (second < first) wins[1]++;
            copyRaw(a, b, first == second ? resources.at(0x20AF18) : first < second ? resources.at(0x20AF08) : resources.at(0x20AF10),
                    0xC148 + row * 0x100, 2, 2);
            copyRaw(a, b, first == second ? resources.at(0x20AF18) : first < second ? resources.at(0x20AF10) : resources.at(0x20AF08),
                    0xC7C8 + row * 0x100, 2, 2);
        }
        resultObjects(view, resources.at(RESULTS_OBJECTS), groups, wins, null, -1);
    }

    private void timeAttack(S3kCompetitionScreen.MenuView view, S3kCompetitionRecords records,
            int newRecordIndex, int[] a, int[] b, List<SpriteGroup> groups) throws IOException {
        var result = Objects.requireNonNull(view.result(), "Time Attack results require the actual session result");
        clear(a, 0x1AA, 9, 8);
        copyList(a, b, resources.at(RECORD_COPIES), MenuPhase.TIME_ATTACK_RESULTS);
        copyRaw(a, b, readLong(resources.at(RECORD_LEVEL_NAMES) + (result.arena().zoneId() - 0xE) * 4), 0xC826, 13, 2);
        var player = result.players().getFirst();
        for (int lap = 0; lap < 5; lap++) putTime(a, 0x15A + lap * 0x100,
                S3kCompetitionRecords.packCentiseconds(player.lapCentiseconds().get(lap)), false);
        putTime(a, 0x65A, S3kCompetitionRecords.packCentiseconds(player.totalCentiseconds()), false);
        List<S3kCompetitionRecords.Entry> entries = records.entries(result.arena());
        for (int rank = 0; rank < 3; rank++) putTime(a, 0x95A + rank * 0x180, entries.get(rank).packedTime(), false);
        resultObjects(view, resources.at(RECORD_OBJECTS), groups, null, entries, newRecordIndex);
    }

    private void resultObjects(S3kCompetitionScreen.MenuView view, int table, List<SpriteGroup> groups,
            int[] wins, List<S3kCompetitionRecords.Entry> records, int newRecordIndex) throws IOException {
        MenuPhase phase = records == null ? MenuPhase.RESULTS : MenuPhase.TIME_ATTACK_RESULTS;
        int count = rom.readU16BE(table) + 1;
        for (int slot = 0; slot < count; slot++) {
            ObjectData o = object(table, slot); int frame = o.frame(), art = 0x829F + o.artWord();
            String key = "competition_menu_select";
            int scroll = records == null ? view.horizontalScroll() : -0x88;
            switch (o.routine()) {
                case STATIC -> { }
                case RESULT_PLAYER -> { key = "competition_menu_player"; frame = view.characters().get(o.parameter()); art = 0xA5C9; }
                case RESULT_WINNER -> {
                    int my = wins[o.parameter()], other = wins[o.parameter() ^ 1];
                    frame = my == other ? 0xD : my > other ? 0xB : 0xC; art = 0xA34A;
                    if (frame == 0xB && (view.frame() & 8) == 0) continue;
                }
                case RECORD_CHARACTER -> {
                    if ((newRecordIndex & 0xFF) == o.parameter() && (view.frame() & 8) == 0) continue;
                    int character = records.get(o.parameter()).characterId();
                    key = "competition_results_character"; frame = character + 0x29;
                    art = character == 2 ? 0xBFD5 : 0x9FD5;
                    // loc_BF3E goes directly to Draw_Sprite: record icon positions do not scroll.
                    scroll = 0;
                }
                default -> throw new IOException("Unexpected competition result routine " + Integer.toHexString(o.routine()));
            }
            add(groups, phase, slot, 0, o.x() + scroll, o.y(), key, frame, art);
        }
    }

    private boolean arenaCursorVisible(S3kCompetitionScreen.MenuView view) {
        return view.horizontalScroll() == -0x58
                && view.verticalScroll() == Math.clamp(view.arenaRow() - 1, 0, 2) * 0x48;
    }

    private ObjectData object(int table, int slot) {
        int at = table + 2 + slot * 12;
        return new ObjectData(resources.menuActor(readLong(at)), rom.readU16BE(at + 4), rom.readU16BE(at + 6),
                rom.readU16BE(at + 8), rom.readU8(at + 10), rom.readU8(at + 11));
    }

    private void add(List<SpriteGroup> groups, MenuPhase phase, int slot, int priority,
            int nativeX, int nativeY, String key, int frame, int artWord) throws IOException {
        SheetKey cacheKey = new SheetKey(phase, key, artWord);
        ObjectSpriteSheet sheet = sheetCache.computeIfAbsent(cacheKey,
                k -> assets.menuSheet(k.phase(), k.key(), k.artWord()));
        if (frame < 0 || frame >= sheet.getFrameCount())
            throw new IOException("Native menu mapping frame is outside the supplied ROM table: " + key + ":" + frame);
        groups.add(new SpriteGroup(slot, priority, nativeX - 0x80, nativeY - 0x80,
                key, frame, artWord, sheet.getFrame(frame).pieces()));
    }

    private void copyList(int[] a, int[] b, int table, MenuPhase phase) throws IOException {
        int count = rom.readU16BE(table) + 1;
        for (int i = 0; i < count; i++) {
            int at = table + 2 + i * 10, source = readLong(at) & 0xFFFFFF;
            int destination = rom.readU16BE(at + 4), width = rom.readU16BE(at + 6) + 1,
                    height = rom.readU16BE(at + 8) + 1;
            if (source >= 0xFF0000) {
                int[] data; int offset;
                if (source < 0xFF1000) {
                    data = phase == MenuPhase.ARENA_SELECT ? assets.arenaBorders() : backgrounds.get(phase);
                    offset = (source - 0xFF0000) / 2;
                } else if (source < 0xFF2000) {
                    data = phase == MenuPhase.ARENA_SELECT ? assets.arenaImages() : assets.resultsLed();
                    offset = (source - 0xFF1000) / 2;
                } else { data = assets.arenaNames(); offset = (source - 0xFF2000) / 2; }
                copy(a, b, data, offset, destination, width, height);
            } else copyRaw(a, b, source, destination, width, height);
        }
    }

    private void copyRaw(int[] a, int[] b, int source, int destination, int width, int height) throws IOException {
        int[] data = new int[width * height];
        for (int i = 0; i < data.length; i++) data[i] = rom.readU16BE(source + i * 2);
        copy(a, b, data, 0, destination, width, height);
    }

    private static void copy(int[] a, int[] b, int[] source, int sourceOffset,
            int destination, int width, int height) throws IOException {
        int[] plane = destination >= 0xE000 ? b : a;
        int start = ((destination & 0x1FFF) / 2);
        if (destination < 0xC000 || sourceOffset < 0 || sourceOffset + width * height > source.length
                || (start % PLANE_WIDTH) + width > PLANE_WIDTH || start + (height - 1) * PLANE_WIDTH + width > plane.length)
            throw new IOException("Native competition map exceeds its VRAM plane or decoded source");
        for (int row = 0; row < height; row++) System.arraycopy(source, sourceOffset + row * width,
                plane, start + row * PLANE_WIDTH, width);
    }

    private static void clear(int[] a, int byteOffset, int width, int height) {
        int at = byteOffset / 2;
        for (int row = 0; row < height; row++) Arrays.fill(a, at + row * PLANE_WIDTH, at + row * PLANE_WIDTH + width, 0);
    }

    /** sub_B066's eight 8x16 ROM glyphs, including the suppressed first minute zero. */
    private void putTime(int[] a, int destination, long packed, boolean palette3) {
        int minutes = (int) (packed >>> 16) & 0xFF, seconds = (int) (packed >>> 8) & 0xFF,
                centis = (int) packed & 0xFF;
        int first = minutes / 10 % 10;
        int[] glyphs = {first == 0 ? 12 : first, minutes % 10, 10, seconds / 10 % 10,
                seconds % 10, 11, centis / 10 % 10, centis % 10};
        int at = destination / 2;
        for (int i = 0; i < glyphs.length; i++) for (int row = 0; row < 2; row++) {
            int word = rom.readU16BE(resources.at(NUMBERS) + glyphs[i] * 4 + row * 2);
            a[at + row * PLANE_WIDTH + i] = palette3 ? (word & 0x9FFF) | 0x6000 : word;
        }
    }

    /** sub_BB04 caps the minute byte at 99 while preserving its carried seconds/centiseconds. */
    private static long sumNativeTimes(List<Integer> times) {
        int minutes = 0, seconds = 0, centis = 0;
        for (int time : times) {
            centis += time % 100;
            if (centis >= 100) { centis -= 100; seconds++; }
            seconds += time / 100 % 60;
            if (seconds >= 60) { seconds -= 60; minutes++; }
            minutes = Math.min(99, minutes + time / 6000);
        }
        return S3kCompetitionRecords.packTime(minutes, seconds, centis);
    }

    private int sine(int angle) { return (short) rom.readU16BE(resources.at(SINE) + (angle & 0xFF) * 2); }
    private int readLong(int at) { return rom.readU16BE(at) << 16 | rom.readU16BE(at + 2); }
    private static int tileOffset(int x, int y) {
        if (x < 0 || x >= PLANE_WIDTH || y < 0 || y >= PLANE_HEIGHT) throw new IndexOutOfBoundsException();
        return y * PLANE_WIDTH + x;
    }
    private static Pattern[] copyPatterns(Pattern[] source) {
        Pattern[] copy = new Pattern[source.length];
        for (int i = 0; i < source.length; i++) { copy[i] = new Pattern(); copy[i].copyFrom(source[i]); }
        return copy;
    }
    private static Palette[] copyPalettes(Palette[] source) {
        Palette[] copy = new Palette[source.length];
        for (int line = 0; line < source.length; line++) {
            copy[line] = new Palette();
            for (int color = 0; color < 16; color++) {
                var from = source[line].colors[color]; var to = copy[line].colors[color];
                to.r = from.r; to.g = from.g; to.b = from.b;
            }
        }
        return copy;
    }
}
