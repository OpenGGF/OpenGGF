package com.openggf.game.sonic2.kis2;

import com.openggf.game.sonic2.credits.Sonic2EndingArt;
import com.openggf.game.sonic2.credits.Sonic2EndingCutsceneManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.data.compression.NemesisReader;
import com.openggf.util.PatternDecompressor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.List;

/** KiS2 EndingSequence and ObjCF presentation, sourced exclusively from lock-on ROM windows. */
public final class Kis2EndingPresentation implements Sonic2EndingCutsceneManager.Presentation {
    // Verified against the byte-identical chip build: EndingSequence LEA and ObjCC mapping pointer.
    public static final int CHARACTER_ART = 0x0DEA00;
    public static final int PALETTES = 0x3090BC;
    public static final int OBJECT_MAP = 0x3091E0;
    private final LockOnAddressSpace rom;
    private final Kis2PlayerArt player;

    public Kis2EndingPresentation(LockOnAddressSpace rom, Kis2PlayerArt player) {
        this.rom = java.util.Objects.requireNonNull(rom);
        this.player = java.util.Objects.requireNonNull(player);
    }

    @Override public SpriteArtSet playerArt() throws IOException { return player.loadKnuckles(); }

    @Override public void applyArt(Sonic2EndingArt art) throws IOException {
        var source = rom.require(CHARACTER_ART);
        Pattern[] patterns;
        try (var channel = Channels.newChannel(new ByteArrayInputStream(
                source.reader().slice(source.localAddress(), 8192)))) {
            patterns = PatternDecompressor.fromBytes(NemesisReader.decompress(channel));
        }
        art.replaceCharacterArt(patterns, playerArt());
        art.replacePalettes(palettes());
    }

    @Override public Sonic2EndingArt.EndingRoutine endingRoutine() {
        var state = com.openggf.game.GameServices.gameStateOrNull();
        return state != null && state.hasAllEmeralds() ? Sonic2EndingArt.EndingRoutine.SUPER_SONIC
                : Sonic2EndingArt.EndingRoutine.SONIC;
    }

    @Override public Palette[] palettes() {
        var paletteSource = rom.require(PALETTES);
        Palette[] palettes = new Palette[4];
        for (int line = 0; line < 4; line++) {
            palettes[line] = new Palette();
            palettes[line].fromSegaFormat(paletteSource.reader().slice(paletteSource.localAddress() + line * 32, 32));
        }
        // EndingSequence copies all four lines from Pal_AC7E, including with all emeralds.
        return palettes;
    }

    @Override public List<SpriteMappingFrame> objectFrames() throws IOException {
        var source = rom.require(OBJECT_MAP);
        return Kis2SpriteMappings.load(source.reader(), source.localAddress(), 0);
    }
    // KnucklesAni_Float2 / KnucklesAni_Walk; the ending owns animation selection.
    @Override public int[] floatingFrames() { return new int[]{0xC0,0xC1,0xC2,0xC3,0xC4,0xC5,0xC6,0xC7,0xC8,0xC9}; }
    @Override public int floatingFrameDuration() { return 6; }
    @Override public int[] walkingFrames() { return new int[]{7,8,1,2,3,4,5,6}; }
    @Override public int waitingFrame() { return 0x56; }
    @Override public String playerCode() { return "knuckles"; }
    @Override public com.openggf.game.sonic2.credits.Sonic2LogoFlashManager.Presentation logo() {
        return new com.openggf.game.sonic2.credits.Sonic2LogoFlashManager.Presentation() {
            @Override public Pattern[] patterns(Pattern[] stock) throws IOException {
                Pattern[] result = java.util.Arrays.copyOf(stock, 0x500);
                for (int i = stock.length; i < result.length; i++) result[i] = new Pattern();
                copyArt(result, 0x33E78A, 0x3DD);
                copyArt(result, 0x33E93E, 0x3FE);
                copyArt(result, 0x33EF7A, 0x488);
                return result;
            }
            @Override public List<SpriteMappingFrame> frames(SpriteMappingFrame stock) throws IOException {
                var shifted = new SpriteMappingFrame(stock.pieces().stream().map(p ->
                        new com.openggf.level.render.SpriteMappingPiece(p.xOffset(), p.yOffset() + 32,
                                p.widthTiles(), p.heightTiles(), p.tileIndex(), p.hFlip(), p.vFlip(),
                                p.paletteIndex(), p.priority())).toList());
                var banner = rom.require(0x310B76);
                var text = rom.require(0x310C34);
                var trademark = Kis2SpriteMappings.load(text.reader(), text.localAddress(), 0).get(2);
                var relocated = new SpriteMappingFrame(trademark.pieces().stream().map(p ->
                        new com.openggf.level.render.SpriteMappingPiece(p.xOffset(), p.yOffset(),
                                p.widthTiles(), p.heightTiles(), p.tileIndex() + 0x3DD,
                                p.hFlip(), p.vFlip(), 3, true)).toList());
                return List.of(shifted, Kis2SpriteMappings.load(banner.reader(), banner.localAddress(), 0).getFirst(), relocated);
            }
            @Override public Palette bannerPalette() {
                var source = rom.require(0x3085F6);
                var palette = new Palette();
                palette.fromSegaFormat(source.reader().slice(source.localAddress(), 32));
                return palette;
            }
        };
    }

    private void copyArt(Pattern[] destination, int address, int tile) throws IOException {
        var source = rom.require(address);
        int length = Math.min(8192, source.reader().size() - source.localAddress());
        try (var channel = Channels.newChannel(new ByteArrayInputStream(source.reader().slice(source.localAddress(), length)))) {
            Pattern[] patterns = PatternDecompressor.fromBytes(NemesisReader.decompress(channel));
            System.arraycopy(patterns, 0, destination, tile, patterns.length);
        }
    }
}
