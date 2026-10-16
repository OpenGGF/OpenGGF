package com.openggf.game.sonic3k.titlescreen;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectDataLoader;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native frontend uploads are CPU resource authority, independent of virtual GL caching. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFrontendPatternResidency {
    private Rom rom;
    @BeforeEach void boot() throws Exception {
        rom=new Rom();assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        TestEnvironment.configureRomFixture(rom);
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED,false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        GameServices.graphics().initHeadless();
        GameServices.level().loadZoneAndActForFreshRuntime(13,1);
    }
    @AfterEach void cleanup(){TestEnvironment.resetAll();if(rom!=null)rom.close();}
    private Sonic3kTitleScreenManager actualTitleWithoutRendering(){
        var title=assertInstanceOf(Sonic3kTitleScreenManager.class,GameServices.module().getTitleScreenProvider());
        title.initialize();var input=new InputHandler();input.setLogicalOverride(LogicalInputSnapshot.neutral());
        int steps=0;while(title.getState()!=TitleScreenProvider.State.ACTIVE&&steps++<3000)title.update(input);
        assertEquals(TitleScreenProvider.State.ACTIVE,title.getState(),"actual title publication, no draw/GL cache authority");
        return title;
    }
    @Test void actualTitleOwnerRetainsBannerBytesAcrossResetAndDeclaredD01LoadWithoutDrawing() throws Exception {
        var title=actualTitleWithoutRendering();
        var data=new Sonic3kTitleScreenDataLoader();assertTrue(data.loadData());
        byte[] expected=pixels(data.getSpritePatterns()[0x52E-0x400]);
        assertTrue(nonzero(expected),"independently decoded actual ROM banner control");
        title.reset(); // Ordinary title reset retires its code owner, not physical VRAM.
        GameServices.level().loadZoneAndActForFreshRuntime(13,1);
        assertTrue(GameServices.level().hasPendingInitialProcessSpritesPass());
        assertArrayEquals(expected,pixels(GameServices.level().getCurrentLevel().getPattern(0x52E)),
                "unwritten native pattern bytes survive before any new ending actor dispatch");
    }
    @Test void admittedDataSelectUploadsReplaceEarlierTitleBytesBeforeLevelOverlays() throws Exception {
        actualTitleWithoutRendering();GameServices.module().getDataSelectProvider().initialize();
        var data=new S3kDataSelectDataLoader(RomByteReader.fromRom(rom));data.loadData();
        byte[] expected=pixels(data.getTextPatterns()[0x20]);
        assertTrue(nonzero(expected),"independent ROM SaveScreen digit-zero font control");
        GameServices.level().loadZoneAndActForFreshRuntime(13,1);
        assertArrayEquals(expected,pixels(GameServices.level().getCurrentLevel().getPattern(0x582)),
                "SaveScreen's admitted tile562 font upload supersedes the older title banner");
    }
    @Test void freshGameplayContextDoesNotBorrowThePreviousContextsFrontendBytes() throws Exception {
        actualTitleWithoutRendering();
        TestEnvironment.configureRomFixture(rom);GameServices.graphics().initHeadless();
        GameServices.level().loadZoneAndActForFreshRuntime(13,1);
        assertArrayEquals(new byte[64],pixels(GameServices.level().getCurrentLevel().getPattern(0x52E)));
    }
    @Test void preparedBuildCannotPublishAnEarlierFrontendGeneration() throws Exception {
        actualTitleWithoutRendering();
        var game=assertInstanceOf(com.openggf.game.sonic3k.Sonic3k.class,GameServices.level().getGame());
        var prepared=game.prepareLevelBuildTask(27,null).call();
        GameServices.module().getDataSelectProvider().initialize();
        var oldLevel=GameServices.level().getCurrentLevel();
        assertThrows(IllegalStateException.class,()->game.installPreparedLevel(prepared));
        assertSame(oldLevel,GameServices.level().getCurrentLevel());
    }
    @Test void actualFrontendResourceWorldsRestoreBothDirectionsAndReplayTwice() throws Exception {
        actualTitleWithoutRendering();var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before=registry.capture();var menu=GameServices.module().getDataSelectProvider();menu.initialize();
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++){
            registry.restore(before);same(before,registry.capture());menu.initialize();same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot a,com.openggf.game.rewind.CompositeSnapshot b){
        assertEquals(a.entries().keySet(),b.entries().keySet());
        for(String key:a.entries().keySet())assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                key,a.get(key),b.get(key)).isEmpty(),key+": "+com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)));
    }
    private static byte[] pixels(Pattern p){byte[] b=new byte[64];p.copyInto(b,0);return b;}
    private static boolean nonzero(byte[] bytes){for(byte value:bytes)if(value!=0)return true;return false;}
}
