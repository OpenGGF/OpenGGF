package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageConstants;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageDataLoader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** ROM-owned assets and decoded streams for the two-human halfpipe. */
public final class CompetitiveHalfpipeRomData {
    private static final int STAGE_COUNT = 7;
    private static final int PALETTE_POINTERS = 0x2782;
    private static final int STAGE_PALETTE_IDS = 0x778E;

    public record Item(boolean bomb, int distance, int angle) {
        public Item {
            if (distance < 0 || distance > 0x3F || angle < 0 || angle > 0xFF) {
                throw new IllegalArgumentException("Invalid native halfpipe item");
            }
        }
    }

    /** One track segment and its corresponding native object-stream group. */
    public record Segment(int geometry, boolean flipped, int marker, List<Item> items) {
        public Segment {
            if (geometry < 0 || geometry > 4 || marker < 0xFC || marker > 0xFF) {
                throw new IllegalArgumentException("Invalid native halfpipe segment");
            }
            items = List.copyOf(items);
        }
    }

    private final RomByteReader rom;
    private final Sonic2SpecialStageDataLoader loader;
    private final List<List<Segment>> stages;
    private final Palette[][] palettes;
    private final byte[] backgroundMappings;
    private final byte[] skydomeScroll;
    private final byte[] perspective;
    private final byte[][] trackFrames;
    private final Pattern[] backgroundPatterns;
    private final Pattern[] trackPatterns;
    private final Pattern[] playerPatterns;
    private final Pattern[] ringPatterns;
    private final Pattern[] bombPatterns;
    private final Pattern[] shadowHorizPatterns;
    private final Pattern[] shadowDiagPatterns;
    private final Pattern[] shadowVertPatterns;
    private final Pattern[] hudPatterns;
    private final Pattern[] tailsTextPatterns;
    private final Pattern[] startPatterns;
    private final Pattern[] messagesPatterns;
    private final Pattern[] starsPatterns;
    private final Pattern[] explosionPatterns;
    private final Pattern[] playerVsPlayerPatterns;

    public CompetitiveHalfpipeRomData(Rom rom) throws IOException {
        Objects.requireNonNull(rom, "rom");
        this.rom = RomByteReader.fromRom(rom);
        loader = new Sonic2SpecialStageDataLoader(rom);
        stages = decodeStages(loader.getLevelLayouts(), loader.getObjectLocations());
        palettes = loadCompetitivePalettes(this.rom);
        backgroundMappings = combineBackgroundMappings(
                loader.getBackgroundLowerMappings(), loader.getBackgroundMainMappings());
        skydomeScroll = loader.getSkydomeScrollTable().clone();
        perspective = loader.getPerspectiveData().clone();
        byte[][] decodedFrames = loader.getTrackFrames();
        trackFrames = new byte[decodedFrames.length][];
        for (int i = 0; i < trackFrames.length; i++) trackFrames[i] = decodedFrames[i].clone();
        backgroundPatterns = copyPatterns(loader.getBackgroundArtPatterns());
        trackPatterns = copyPatterns(loader.getTrackArtPatterns());
        playerPatterns = copyPatterns(loader.getPlayerArtPatterns());
        ringPatterns = copyPatterns(loader.getRingArtPatterns());
        bombPatterns = copyPatterns(loader.getBombArtPatterns());
        shadowHorizPatterns = copyPatterns(loader.getShadowHorizPatterns());
        shadowDiagPatterns = copyPatterns(loader.getShadowDiagPatterns());
        shadowVertPatterns = copyPatterns(loader.getShadowVertPatterns());
        hudPatterns = copyPatterns(loader.getHudArtPatterns());
        tailsTextPatterns = copyPatterns(loader.getTailsTextArtPatterns());
        startPatterns = copyPatterns(loader.getStartArtPatterns());
        messagesPatterns = copyPatterns(loader.getMessagesArtPatterns());
        starsPatterns = copyPatterns(loader.getStarsArtPatterns());
        explosionPatterns = copyPatterns(loader.getExplosionArtPatterns());
        // PlrList_SpecialStage (s2.asm:89965-89979) also loads the native
        // Player VS Player caption, outside ordinary SpecialMessages art.
        playerVsPlayerPatterns = loadPlayerVsPlayerPatterns(this.rom);
    }

    /** Explicit interoperability with the existing injected-ROM decoder. */
    public Sonic2SpecialStageDataLoader loader() { return loader; }
    /** Immutable, bounds-checked source for native sprite mapping tables. */
    public RomByteReader rom() { return rom; }
    public List<Segment> segments(int stage) { return stages.get(checkStage(stage)); }
    public Palette[] palettes(int stage) {
        Palette[] source = palettes[checkStage(stage)];
        Palette[] result = new Palette[source.length];
        for (int i = 0; i < result.length; i++) result[i] = source[i].deepCopy();
        return result;
    }
    public byte[] backgroundMappings() { return backgroundMappings.clone(); }
    public byte[] skydomeScroll() { return skydomeScroll.clone(); }
    public byte[] perspective() { return perspective.clone(); }
    public byte[] trackFrame(int frame) { return trackFrames[frame].clone(); }
    public Pattern[] backgroundPatterns() { return copyPatterns(backgroundPatterns); }
    public Pattern[] trackPatterns() { return copyPatterns(trackPatterns); }
    public Pattern[] playerPatterns() { return copyPatterns(playerPatterns); }
    public Pattern[] ringPatterns() { return copyPatterns(ringPatterns); }
    public Pattern[] bombPatterns() { return copyPatterns(bombPatterns); }
    public Pattern[] shadowHorizPatterns() { return copyPatterns(shadowHorizPatterns); }
    public Pattern[] shadowDiagPatterns() { return copyPatterns(shadowDiagPatterns); }
    public Pattern[] shadowVertPatterns() { return copyPatterns(shadowVertPatterns); }
    public Pattern[] hudPatterns() { return copyPatterns(hudPatterns); }
    public Pattern[] tailsTextPatterns() { return copyPatterns(tailsTextPatterns); }
    public Pattern[] startPatterns() { return copyPatterns(startPatterns); }
    public Pattern[] messagesPatterns() { return copyPatterns(messagesPatterns); }
    public Pattern[] starsPatterns() { return copyPatterns(starsPatterns); }
    public Pattern[] explosionPatterns() { return copyPatterns(explosionPatterns); }
    public Pattern[] playerVsPlayerPatterns() { return copyPatterns(playerVsPlayerPatterns); }

    private static Pattern[] loadPlayerVsPlayerPatterns(RomByteReader rom) throws IOException {
        // ArtNem_SpecialPlayerVSPlayer is exactly 182 compressed ROM bytes;
        // the following bytes only satisfy Nemesis' bit-buffer lookahead.
        byte[] compressed = rom.slice(0xDD9C8, 182 + 16);
        byte[] decoded;
        try (var channel = Channels.newChannel(new ByteArrayInputStream(compressed))) {
            decoded = NemesisReader.decompress(channel);
        }
        if (decoded.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Invalid native competitive caption art");
        }
        Pattern[] result = new Pattern[decoded.length / Pattern.PATTERN_SIZE_IN_ROM];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Pattern();
            result[i].fromSegaFormat(Arrays.copyOfRange(decoded, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM));
        }
        return result;
    }

    private static Palette[][] loadCompetitivePalettes(RomByteReader rom) throws IOException {
        Palette[] main = new Palette[3];
        for (int line = 0; line < main.length; line++) {
            main[line] = readPalette(rom, (int) Sonic2SpecialStageConstants.PALETTE_MAIN_OFFSET + line * 32);
        }
        Palette[][] result = new Palette[STAGE_COUNT][4];
        for (int stage = 0; stage < STAGE_COUNT; stage++) {
            // SSInitPalAndData (s2.asm:10251-10266): the native two-player flag
            // adds three palette IDs from stage index two onwards. This reaches
            // SS6/SS7 and then all three competitive palettes; it is not the
            // ordinary seven-palette order. No global palette cache/fallback.
            int paletteIndex = stage + (stage >= 2 ? 3 : 0);
            int paletteId = rom.readU16BE(STAGE_PALETTE_IDS + paletteIndex * 2);
            int descriptor = PALETTE_POINTERS + paletteId * 8;
            if (rom.readU16BE(descriptor + 4) != 0xFB60
                    || rom.readU16BE(descriptor + 6) != 7) {
                throw new IOException("Invalid native halfpipe palette descriptor");
            }
            for (int line = 0; line < main.length; line++) result[stage][line] = main[line].deepCopy();
            result[stage][3] = readPalette(rom, rom.readU32BE(descriptor));
        }
        return result;
    }

    private static Palette readPalette(RomByteReader rom, int address) {
        Palette palette = new Palette();
        for (int color = 0; color < Palette.PALETTE_SIZE; color++) {
            palette.colors[color].fromSegaFormat(rom.readU16BE(address + color * 2));
        }
        return palette;
    }

    private static List<List<Segment>> decodeStages(byte[] layouts, byte[] objects) throws IOException {
        if (layouts.length < STAGE_COUNT * 2 || objects.length < STAGE_COUNT * 2) {
            throw new IOException("Missing native halfpipe stage tables");
        }
        List<List<Segment>> result = new ArrayList<>(STAGE_COUNT);
        for (int stage = 0; stage < STAGE_COUNT; stage++) {
            int layoutStart = readWord(layouts, stage * 2);
            int layoutEnd = stage + 1 < STAGE_COUNT ? readWord(layouts, (stage + 1) * 2) : layouts.length;
            int cursor = readWord(objects, stage * 2);
            int objectEnd = stage + 1 < STAGE_COUNT ? readWord(objects, (stage + 1) * 2) : objects.length;
            if (layoutStart < STAGE_COUNT * 2 || layoutEnd <= layoutStart || layoutEnd > layouts.length
                    || cursor < STAGE_COUNT * 2 || objectEnd <= cursor || objectEnd > objects.length) {
                throw new IOException("Invalid native halfpipe stage offsets: " + stage);
            }
            List<Segment> segments = new ArrayList<>(layoutEnd - layoutStart);
            boolean finalMessageSeen = false;
            for (int index = layoutStart; index < layoutEnd; index++) {
                int geometryByte = layouts[index] & 0xFF;
                int geometry = geometryByte & 0x7F;
                if (geometry > 4) throw new IOException("Invalid native halfpipe geometry: " + geometry);
                List<Item> items = new ArrayList<>();
                int marker = 0xFF;
                if (!finalMessageSeen) {
                    while (true) {
                        if (cursor >= objectEnd) throw new IOException("Unterminated native halfpipe sector");
                        int value = objects[cursor++] & 0xFF;
                        if ((value & 0x80) != 0) {
                            if (value < 0xFC) throw new IOException("Invalid native halfpipe marker");
                            marker = value;
                            finalMessageSeen = marker == 0xFD;
                            break;
                        }
                        if (cursor >= objectEnd) throw new IOException("Truncated native halfpipe item");
                        items.add(new Item((value & 0x40) != 0, value & 0x3F, objects[cursor++] & 0xFF));
                    }
                }
                // Track geometry continues after the final message while the
                // results handoff runs. Stream padding is not another sector.
                segments.add(new Segment(geometry, (geometryByte & 0x80) != 0, marker, items));
            }
            if (!finalMessageSeen) throw new IOException("Missing native halfpipe final message: " + stage);
            result.add(List.copyOf(segments));
        }
        return List.copyOf(result);
    }

    private static int readWord(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static byte[] combineBackgroundMappings(byte[] lower, byte[] main) throws IOException {
        if (lower.length != 1024 || main.length != 1024) {
            throw new IOException("Invalid native halfpipe background planes");
        }
        byte[] combined = new byte[2048];
        System.arraycopy(lower, 0, combined, 0, lower.length);
        System.arraycopy(main, 0, combined, lower.length, main.length);
        return combined;
    }

    private static Pattern[] copyPatterns(Pattern[] source) {
        Pattern[] result = new Pattern[source.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new Pattern();
            result[i].copyFrom(Objects.requireNonNull(source[i], "ROM pattern"));
        }
        return result;
    }

    private static int checkStage(int stage) {
        if (stage < 0 || stage >= STAGE_COUNT) throw new IllegalArgumentException("Invalid stage: " + stage);
        return stage;
    }
}
