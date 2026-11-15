package com.openggf.game.soniccd.title;

import com.openggf.data.compression.KosinskiReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.soniccd.program.CdProgramData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

/**
 * Title-screen data from TITLEM.MMD and TITLES.BIN on the user's disc, decoded once. Every blob is
 * located through {@code program-assets.tsv}; the labels are those of {@code Title Screen/Main.asm}
 * and {@code Title Screen/Sub.asm}.
 */
public final class CdTitleAssets {
    /** One sprite piece: {@code dc.b y, size, tile.w, x} (Main.asm DrawObject, 1556-1601). */
    public record Piece(int y, int size, int tile, int x) {
        public int widthTiles() { return ((size >> 2) & 3) + 1; }
        public int heightTiles() { return (size & 3) + 1; }
    }

    final int[] palTitle;
    final int[] palInit;
    final byte[] mapWater;
    final byte[] mapMountains;
    final int[] mapEmblem;
    final byte[] artWater, artMountains, artEmblem, artSolidColor, artBanner, artPlanet, artSonic;
    final byte[] artPressStart, artMenuArrow, artCopyright, artTm;
    /** Menu text art by option ID ({@code ObjMenu_SetOption .Text}, Menu/Main.asm:603-611). */
    final byte[][] menuText;
    final List<List<Piece>> sprSonic, sprBanner, sprPlanet, sprMenu, sprMenuArrow, sprCopyright, sprTm;
    final int[] waterPalCycle;
    final byte[] planetOffsets;
    final byte[] armFrames;
    final byte[] cloudStamps;
    final byte[] cloudStampMap;
    final int[] sineTable;

    public CdTitleAssets(CdProgramData data) {
        palTitle = words(data.bytes("palTitle"));
        palInit = words(data.bytes("palInit"));
        mapWater = data.bytes("mapWater");
        mapMountains = data.bytes("mapMountains");
        mapEmblem = words(data.bytes("mapEmblem"));
        artWater = nemesis(data, "artWater");
        artMountains = nemesis(data, "artMountains");
        artEmblem = nemesis(data, "artEmblem");
        artSolidColor = nemesis(data, "artSolidColor");
        artBanner = nemesis(data, "artBanner");
        artPlanet = nemesis(data, "artPlanet");
        artSonic = nemesis(data, "artSonic");
        artPressStart = nemesis(data, "artPressStart");
        artMenuArrow = nemesis(data, "artMenuArrow");
        artCopyright = nemesis(data, "artCopyright");
        artTm = nemesis(data, "artTm");
        // .Text: press start (0, 1), new game, continue, time attack, RAM data, D.A. Garden, visual mode.
        menuText = new byte[][] {artPressStart, artPressStart, nemesis(data, "artNewGame"), nemesis(data, "artContinue"),
                nemesis(data, "artTimeAttack"), nemesis(data, "artRamData"), nemesis(data, "artDaGarden"),
                nemesis(data, "artVisualMode")};
        sprSonic = mappings(data.bytes("mapSprSonic"));
        sprBanner = mappings(data.bytes("mapSprBanner"));
        sprPlanet = mappings(data.bytes("mapSprPlanet"));
        sprMenu = mappings(data.bytes("mapSprMenu"));
        sprMenuArrow = mappings(data.bytes("mapSprMenuArrow"));
        sprCopyright = mappings(data.bytes("mapSprCopyright"));
        sprTm = mappings(data.bytes("mapSprTm"));
        waterPalCycle = words(data.bytes("waterPalCycle"));
        planetOffsets = data.bytes("planetOffsets");
        armFrames = data.bytes("armFrames");
        cloudStamps = kosinski(data, "cloudStamps");
        cloudStampMap = kosinski(data, "cloudStampMap");
        sineTable = words(data.bytes("sineTable"));
    }

    static int[] words(byte[] bytes) {
        int[] out = new int[bytes.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = ((bytes[2 * i] & 0xFF) << 8) | (bytes[2 * i + 1] & 0xFF);
        return out;
    }

    private static byte[] nemesis(CdProgramData data, String name) {
        try {
            return NemesisReader.decompress(Channels.newChannel(new ByteArrayInputStream(data.from(name))));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot decode " + name, failure);
        }
    }

    private static byte[] kosinski(CdProgramData data, String name) {
        try {
            return KosinskiReader.decompress(Channels.newChannel(new ByteArrayInputStream(data.bytes(name))));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot decode " + name, failure);
        }
    }

    /** Sprite mappings: a word offset per frame, then a piece count and five bytes per piece. */
    static List<List<Piece>> mappings(byte[] map) {
        int frames = (((map[0] & 0xFF) << 8) | (map[1] & 0xFF)) / 2;
        List<List<Piece>> out = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            int at = ((map[2 * f] & 0xFF) << 8) | (map[2 * f + 1] & 0xFF);
            int count = map[at++] & 0xFF;
            List<Piece> pieces = new ArrayList<>();
            for (int p = 0; p < count; p++, at += 5) {
                pieces.add(new Piece(map[at], map[at + 1] & 0xFF,
                        ((map[at + 2] & 0xFF) << 8) | (map[at + 3] & 0xFF), map[at + 4]));
            }
            out.add(List.copyOf(pieces));
        }
        return List.copyOf(out);
    }
}
