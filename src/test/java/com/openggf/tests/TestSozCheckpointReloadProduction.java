package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Physical SOZ starpost activation followed by the production death/reload loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozCheckpointReloadProduction {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}
    static Stream<Arguments> scenarios() {
        var rows = new java.util.ArrayList<Arguments>();
        for (int act : new int[]{0, 1}) for (String character : new String[]{"sonic", "tails", "knuckles"})
            for (int width : new int[]{320, 400, 512, 640, 800}) for (String donor : new String[]{"off", "s1", "s2"})
                rows.add(Arguments.of(act, act == 0 ? 1 : 2, act == 0 ? 0x1A30 : 0x860,
                        act == 0 ? 0x428 : 0x5C8, character, width, donor));
        return rows.stream();
    }
    @ParameterizedTest @MethodSource("scenarios")
    void touchCheckpointThenDeathReloadsItsNativePosition(int act,int index,int x,int y,String character,
            int width,String donor){
        var config=SonicConfigurationService.getInstance();config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,!donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE,donor);
        if (!donor.equals("off")) {
            var rom = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable();
            assertNotNull(rom,"required donor ROM");
            config.setSessionOverride(donor.equals("s1") ? SonicConfiguration.SONIC_1_ROM : SonicConfiguration.SONIC_2_ROM,rom.getAbsolutePath());
        }
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var builder=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)(x-24),(short)(y+4)).startPositionIsCentre().withFreshLevelStartLifecycle();
        if(!donor.equals("off"))builder.withCrossGameDonation(donor);
        var f=builder.build();
        assertEquals(width,f.camera().getWidth()&65535);
        assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
        if(!donor.equals("off"))assertEquals(donor,CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"),f.sprite().getGameRules().playerCapability().spindashEnabled());
        var manager=GameServices.level().getObjectManager();var p=f.sprite();
        assertFalse(GameServices.level().getCheckpointState().isActive());
        for(int i=0;i<60&&!GameServices.level().getCheckpointState().isActive();i++) {
            var registry=f.gameplayMode().getRewindRegistry();var before=registry.capture();
            f.stepFrame(false,false,false,true,false);
            if(GameServices.level().getCheckpointState().isActive()) {
                var after=registry.capture();manager.setRewindInPlaceRestoreEnabledForTest(false);
                registry.restore(before);same(before,registry.capture());
                f.stepFrame(false,false,false,true,false);same(after,registry.capture());
            }
        }
        assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex(),
                "walking into the real placed post must activate it");
        assertFalse(p.getDead());
        assertTrue(p.applyPitDeath());
        var loop=new GameLoop(new InputHandler());loop.setGameplayMode(f.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        try {
            boolean reloaded=false;
            for(int i=0;i<1200;i++){
                f.gameplayMode().getFadeManager().update();loop.step();
                if(GameServices.level().getObjectManager()!=manager){
                    reloaded=true;assertEquals(8,GameServices.level().getCurrentZone());assertEquals(act,GameServices.level().getCurrentAct());
                    assertEquals(index,GameServices.level().getCheckpointState().getLastCheckpointIndex());
                    assertEquals(x,GameServices.camera().getFocusedSprite().getCentreX()&65535);
                    assertEquals(y,GameServices.camera().getFocusedSprite().getCentreY()&65535);
                    assertFalse(GameServices.camera().getFocusedSprite().getDead());
                    assertEquals(width,GameServices.camera().getWidth()&65535);
                    assertEquals(!donor.equals("off"),CrossGameFeatureProvider.isActive());
                    break;
                }
            }
            assertTrue(reloaded,"native death countdown must reach GameLoop reload");
        }finally{loop.closePresence();}
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual) {
        for(var key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),key+": "+diff);
        }
    }
}
