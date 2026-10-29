package com.openggf.game.sonic1;

import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.levelselect.Sonic1LevelSelectDataLoader;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageDataLoader;
import com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenDataLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Run with each original image through sonic1.rom.path; no alternate asset source. */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1RetailRomLoading {
    @ParameterizedTest
    @ValueSource(ints = {0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88,
            0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F, 0x90, 0x91, 0x92, 0x95, 0x96})
    void originalImageLoadsEveryActAndBothEndingsFromItsOwnTables(int index) throws Exception {
        Rom rom = TestEnvironment.currentRom();
        assertTrue(RetailRomRevision.detect(rom).belongsTo(com.openggf.data.RomIdentity.S1));
        var level = new Sonic1(rom).buildDetachedLevel(index);
        assertTrue(level.getPatternCount() > 0, "PLC art");
        assertTrue(level.getChunkCount() > 0, "native LevelHeaders chunk pointer");
        assertTrue(level.getBlockCount() > 0, "native LevelHeaders block pointer");
        assertTrue(level.getMaxX() >= level.getMinX(), "LevelSizeArray");
        assertNotNull(level.getRingSpriteSheet());
        assertEquals(4, level.getRingSpriteSheet().getSpinFrameCount());
    }

    @Test
    void playerRingsObjectArtAndFrontEndsUseTheLoadedRevision() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var reader = RomByteReader.fromRom(rom);
        var player = new Sonic1PlayerArt(reader).loadSonic();
        assertEquals(88, player.mappingFrames().size());
        assertEquals(88, player.dplcFrames().size());
        assertTrue(player.artTiles().length > 1200);
        var rings = new Sonic1RingArt(rom).load();
        assertEquals(Sonic1RomProfile.isRev00(rom) ? 8 : 9, rings.getFrameCount());
        var objectArt = new Sonic1ObjectArt(rom, reader);
        assertEquals(24, objectArt.loadMappingFrames(Sonic1Constants.MAP_CATERKILLER_ADDR).size());
        assertEquals(13, objectArt.loadMappingFrames(Sonic1Constants.MAP_EGGMAN_ADDR).size());
        assertNotNull(objectArt.loadGameOverSheet());
        assertTrue(new Sonic1TitleScreenDataLoader().loadData(), "SEGA/title/GHZ front-end art");
        assertTrue(new Sonic1LevelSelectDataLoader().loadData(), "level select font and palette");
    }

    @Test
    void everyZoneBuildsItsObjectAndBossArtWithoutRelocatingNativePointersTwice() throws Exception {
        for (int zone = 0; zone <= 6; zone++) {
            var provider = new Sonic1ObjectArtProvider();
            provider.loadArtForZone(zone);
            assertNotNull(provider.getSheet(com.openggf.level.objects.ObjectArtKeys.CHECKPOINT), "zone " + zone);
            if (zone < 6) assertNotNull(provider.getSheet(com.openggf.level.objects.ObjectArtKeys.BOSS_EXPLOSION), "zone " + zone);
            else assertNotNull(provider.getSheet(com.openggf.level.objects.ObjectArtKeys.END_STH), "ending logo");
        }
    }

    @Test
    void everyNativeAudioTableAndPcmRemainReadable() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var module = new Sonic1GameModule();
        module.bindRetailRom(rom);
        var profile = module.getAudioProfile();
        var loader = profile.createSmpsLoader(rom);
        for (int music = 0x81; music <= 0x93; music++) assertNotNull(loader.loadMusic(music), "music " + music);
        for (int sound = 0xA0; sound <= 0xD0; sound++) assertNotNull(loader.loadSfx(sound), "sound " + sound);
        assertNotNull(loader.loadDacData());
        assertEquals(0x6978, profile.loadSegaPcm(rom).length);
        assertEquals(Sonic1RomProfile.isRev00(rom) ? Sonic1Music.LZ.id : Sonic1Music.BOSS.id,
                profile.resolveAirResetMusic(Sonic1Music.LZ.id, true, false, true));
    }

    @Test
    void allSpecialStageLayoutsAndArtUseNativePointerTables() throws Exception {
        var loader = new Sonic1SpecialStageDataLoader(TestEnvironment.currentRom());
        for (int stage = 0; stage < 6; stage++) {
            assertEquals(0x4000, loader.getStageLayout(stage).length);
            int[] start = loader.getStartPosition(stage);
            assertTrue(start[0] > 0 && start[1] > 0);
        }
        assertTrue(loader.getWallPatterns().length > 0);
        assertTrue(loader.getBgCloudPatterns().length > 0);
        assertTrue(loader.getBgFishPatterns().length > 0);
    }

    @Test
    void moduleBindingIsIdempotentAndCannotChangeAnotherSessionsRevision() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var module = new Sonic1GameModule();
        module.bindRetailRom(rom);
        var audio = module.getAudioProfile();
        var special = module.getSpecialStageProvider();
        module.createGame(rom);
        assertSame(audio, module.getAudioProfile());
        assertSame(special, module.getSpecialStageProvider());
        assertEquals(RetailRomRevision.detect(rom), module.getRetailRomRevision());
        try (Rom unrelated = Rom.fromReader(RomByteReader.fromBytes(new byte[512]), "unknown image")) {
            assertThrows(IllegalStateException.class, () -> module.bindRetailRom(unrelated));
        }
        assertEquals(RetailRomRevision.detect(rom), module.getRetailRomRevision());
    }
}
