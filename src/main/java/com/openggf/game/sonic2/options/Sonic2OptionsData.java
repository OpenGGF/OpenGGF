package com.openggf.game.sonic2.options;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.Rom;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.Arrays;

/** Verified S2 REV01 MenuScreen assets. All pixels, map words and text come from the ROM. */
final class Sonic2OptionsData {
    static final int BOX_WIDTH = 22;
    static final int BOX_HEIGHT = 8;
    private static final int MAP_OPTIONS = 0x9AB2;
    private static final int[] HEADERS = {0x97CA, 0x982C, 0x985E};
    private static final int[] MILES_CHOICES = {0x97DC, 0x97FC, 0x980C};
    private static final int[] TAILS_CHOICES = {0x97EC, 0x97FC, 0x981C};
    private static final int[] ITEMS_CHOICES = {0x983E, 0x984E};

    final Pattern[] patterns;
    final Pattern[] backgroundArt;
    final Palette[] palettes;
    final int[] backgroundMap;
    final int[][] boxMaps;
    final int[][] headers;
    final int[][] teamChoices;
    final int[][] itemChoices;
    final int[] soundTemplate;
    private final int[] backgroundSources;
    private final int[] backgroundDurations;

    private Sonic2OptionsData(Rom rom, boolean overseas) throws IOException {
        Pattern[] font = PatternDecompressor.nemesis(rom, 0x7C43A);
        Pattern[] boxes = PatternDecompressor.nemesis(rom, 0x7D990);
        if (font.length != 88 || boxes.length != 21) {
            throw new IOException("Unexpected S2 options font/MenuBox art");
        }
        patterns = new Pattern[0x70 + boxes.length];
        Arrays.setAll(patterns, ignored -> new Pattern());
        System.arraycopy(font, 0, patterns, 0x10, font.length);
        System.arraycopy(boxes, 0, patterns, 0x70, boxes.length);
        backgroundArt = PatternDecompressor.fromBytes(rom.readBytes(0x7CD2C, 1280));
        backgroundMap = enigma(rom, 0x7CB80, 428, 0x6000);
        boxMaps = new int[][] {enigma(rom, MAP_OPTIONS, 44, 0x70),
                enigma(rom, MAP_OPTIONS, 44, 0x2070)};
        if (backgroundMap.length != 40 * 28 || boxMaps[0].length != BOX_WIDTH * BOX_HEIGHT
                || boxMaps[1].length != BOX_WIDTH * BOX_HEIGHT) {
            throw new IOException("Unexpected S2 options plane dimensions");
        }
        palettes = new Palette[4];
        for (int line = 0; line < palettes.length; line++) {
            palettes[line] = new Palette();
            palettes[line].fromSegaFormat(rom.readBytes(address(rom, 0x30E2) + line * 32, 32));
        }
        headers = textTable(rom, HEADERS);
        teamChoices = textTable(rom, overseas ? TAILS_CHOICES : MILES_CHOICES);
        itemChoices = textTable(rom, ITEMS_CHOICES);
        soundTemplate = text(rom, 0x9870);
        // Anim_SonicMilesBG (s2.asm:11667-11677). Duration bytes are DBF-like N+1 calls.
        byte[] animation = rom.readBytes(address(rom, 0x87D0), 12);
        backgroundSources = new int[6];
        backgroundDurations = new int[6];
        for (int i = 0; i < 6; i++) {
            backgroundSources[i] = Byte.toUnsignedInt(animation[i * 2]);
            backgroundDurations[i] = Byte.toUnsignedInt(animation[i * 2 + 1]) + 1;
            if (backgroundSources[i] + 10 > backgroundArt.length) {
                throw new IOException("Invalid S2 menu animation art span");
            }
        }
    }

    static Sonic2OptionsData load(Rom rom, boolean overseas) throws IOException {
        return new Sonic2OptionsData(rom, overseas);
    }

    int backgroundSource(int tick) {
        int period = Arrays.stream(backgroundDurations).sum();
        int phase = Math.floorMod(tick, period);
        for (int frame = 0; frame < backgroundSources.length; frame++) {
            if (phase < backgroundDurations[frame]) {
                return backgroundSources[frame];
            }
            phase -= backgroundDurations[frame];
        }
        throw new AssertionError("Unreachable animation phase");
    }

    /** OptionScreen_DrawSelected/DrawUnselected (s2.asm:12259-12340). */
    int[] box(int row, Sonic2OptionsState state) {
        boolean selected = state.selectedRow() == row;
        int palette = selected ? 0x6000 : 0;
        int[] words = boxMaps[selected ? 0 : 1].clone();
        stamp(words, 0x30 / 2, headers[row], palette);
        int[] choice = switch (row) {
            case 0 -> teamChoices[state.playerOption()];
            case 1 -> itemChoices[state.twoPlayerItems()];
            default -> soundTemplate;
        };
        stamp(words, 0xB6 / 2, choice, palette);
        if (row == 2) {
            words[0xC2 / 2] = palette | hexDigit(state.soundTestValue() >>> 4);
            words[0xC2 / 2 + 1] = palette | hexDigit(state.soundTestValue());
        }
        return words;
    }

    private static int hexDigit(int digit) {
        int value = digit & 0xF;
        return 0x10 + value + (value >= 10 ? 4 : 0);
    }

    private static void stamp(int[] destination, int offset, int[] text, int palette) {
        for (int i = 0; i < text.length; i++) {
            // MenuScreenTextToRAM replaces d0's LOW BYTE. Text already names absolute tiles.
            destination[offset + i] = palette | text[i];
        }
    }

    private static int[][] textTable(Rom rom, int[] addresses) throws IOException {
        int[][] result = new int[addresses.length][];
        for (int i = 0; i < result.length; i++) {
            result[i] = text(rom, addresses[i]);
        }
        return result;
    }

    private static int[] text(Rom rom, int address) throws IOException {
        address = address(rom, address);
        int length = Byte.toUnsignedInt(rom.readByte(address)) + 1;
        if (length < 1 || length > 22) {
            throw new IOException("Invalid options text length at " + Integer.toHexString(address));
        }
        byte[] bytes = rom.readBytes(address + 1, length);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = Byte.toUnsignedInt(bytes[i]);
        }
        return result;
    }

    private static int[] enigma(Rom rom, int address, int length, int base) throws IOException {
        address = address(rom, address);
        try (var channel = Channels.newChannel(new ByteArrayInputStream(rom.readBytes(address, length)))) {
            byte[] bytes = EnigmaReader.decompress(channel, base);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            int[] words = new int[bytes.length / 2];
            for (int i = 0; i < words.length; i++) {
                words[i] = Short.toUnsignedInt(buffer.getShort());
            }
            return words;
        }
    }
}
