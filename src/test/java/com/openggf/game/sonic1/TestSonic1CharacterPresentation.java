package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1CharacterPresentation {
    static Image image(int width, int height, int pixel) {
        byte[] pixels = new byte[width * height];
        java.util.Arrays.fill(pixels, (byte) pixel);
        return new Image(width, height, pixels);
    }
    static CharacterPresentation mica() {
        return new CharacterPresentation("MICA", null,
                Map.of(Host.S1, new Art(image(16,16,3), image(16,16,4), null, null)),
                Map.of(new Font(Host.S1, Style.HUD), Map.of('M',image(8,8,5),'A',image(7,8,6))));
    }
    static Rom rom() throws Exception { return com.openggf.tests.TestEnvironment.currentRom(); }
    static Pattern[] lives(Rom rom) throws Exception {
        return PatternDecompressor.nemesis(rom, Sonic1RomProfile.resolve(rom,
                com.openggf.game.sonic1.constants.Sonic1Constants.ART_NEM_LIFE_ICON_ADDR));
    }
    @Test void hudUsesMicaIconAndSmallNativeICWithOnlySupplementaryMA() throws Exception {
        Rom rom = rom();
        var nativeTiles = lives(rom);
        var result = Sonic1CharacterPresentation.hud(rom, nativeTiles, mica());
        var name = Sonic1CharacterPresentation.name(rom, mica(), Style.HUD);
        assertEquals(Set.of('I','C'), name.nativeGlyphs());
        assertEquals(Set.of('M','A'), name.supplementaryGlyphs());
        assertTrue(name.complete());
        assertArrayEquals(image(16,16,3).pixels(), CharacterNameComposer.extract(result,0,2,2,0,0,16,16).pixels());
        assertArrayEquals(name.image().pixels(), CharacterNameComposer.extract(result,4,4,2,0,0,name.image().width(),8).pixels());
        for (int i = 5; i < 12; i += 2) assertSame(nativeTiles[i], result[i], "Lower row stays native");
        assertSame(nativeTiles, Sonic1CharacterPresentation.hud(rom,nativeTiles,null));
    }
    @Test void missingHudGlyphKeepsNativeNameAndOptionalIconStillWorks() throws Exception {
        Rom rom = rom();
        var nativeTiles = lives(rom);
        var result = Sonic1CharacterPresentation.hud(rom,nativeTiles,new CharacterPresentation("MICA"));
        for (int i=0;i<nativeTiles.length;i++) assertSame(nativeTiles[i],result[i]);
    }

    @Test void oneUpFaceChangesOnlyDedicatedSonicTilesAndKeepsAllMappings() throws Exception {
        Rom rom = rom();
        var art = new Sonic1ObjectArt(rom, com.openggf.data.RomByteReader.fromRom(rom));
        var stock = art.buildArtSheetFromRom(
                com.openggf.game.sonic1.constants.Sonic1Constants.ART_NEM_MONITOR_ADDR,
                com.openggf.game.sonic1.constants.Sonic1Constants.MAP_MONITOR_ADDR, 0, 1);
        assertSame(stock, Sonic1CharacterPresentation.monitor(stock, null));
        assertSame(stock, Sonic1CharacterPresentation.monitor(stock, new CharacterPresentation("MICA")));
        var result = Sonic1CharacterPresentation.monitor(stock, mica());
        var facePiece = result.getFrame(4).pieces().getFirst();
        assertEquals(0x1C, facePiece.tileIndex());
        assertArrayEquals(image(16,16,4).pixels(), CharacterNameComposer.extract(result.getPatterns(),
                facePiece.tileIndex(),2,2,0,0,16,16).pixels());
        for (int i=0;i<stock.getPatterns().length;i++) {
            if (i < 0x1C || i >= 0x20) assertSame(stock.getPatterns()[i],result.getPatterns()[i]);
        }
        for (int i=0;i<stock.getFrameCount();i++) assertSame(stock.getFrame(i),result.getFrame(i));
        assertEquals(stock.getPaletteIndex(),result.getPaletteIndex());
        assertEquals(stock.getFrameDelay(),result.getFrameDelay());
    }

    @Test void actResultsReplaceOnlySonicWithNativeTitleLetters() throws Exception {
        assertResultsName(0);
    }
    @Test void specialStageResultsReplaceOnlySonicAndKeepGotThemAll() throws Exception {
        assertResultsName(12);
    }
    private void assertResultsName(int frameIndex) throws Exception {
        Rom rom = rom();
        var frames = Sonic1ResultsMappingLoader.load(com.openggf.data.RomByteReader.fromRom(rom));
        var patterns = lives(rom);
        var stock = Sonic1CharacterPresentation.results(rom,patterns,frames,null,frameIndex);
        assertSame(patterns,stock.getPatterns());
        for (int i=0;i<frames.size();i++) assertSame(frames.get(i),stock.getFrame(i));
        var result = Sonic1CharacterPresentation.results(rom,patterns,frames,mica(),frameIndex);
        var name = Sonic1CharacterPresentation.name(rom,mica(),Style.RESULTS);
        assertEquals(Set.of('M','I','C','A'),name.nativeGlyphs());
        assertTrue(name.supplementaryGlyphs().isEmpty());
        assertTrue(name.complete());
        var pieces = result.getFrame(frameIndex).pieces();
        int columns = (name.image().width()+7)/8;
        assertArrayEquals(name.image().pixels(), CharacterNameComposer.extract(result.getPatterns(),
                patterns.length,columns,2,0,0,name.image().width(),16).pixels());
        int generatedPieces = (columns+3)/4;
        assertEquals(frames.get(frameIndex).pieces().subList(5,frames.get(frameIndex).pieces().size()),
                pieces.subList(generatedPieces,pieces.size()));
        var lastNative = frames.get(frameIndex).pieces().get(4);
        assertEquals(lastNative.xOffset()+lastNative.widthTiles()*8-name.image().width(),pieces.getFirst().xOffset());
        for (int i=0;i<frames.size();i++) if(i!=frameIndex) assertSame(frames.get(i),result.getFrame(i));
        for (int i=0;i<patterns.length;i++) assertSame(patterns[i],result.getPatterns()[i]);
    }
}
