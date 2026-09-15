package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.*;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.EnumMap;
import static org.junit.jupiter.api.Assertions.*;

/** Short placed-object obligations independent of long SOZ routes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSozSwingAndWireProduction {
    private final EnumMap<SonicConfiguration,Object> saved=new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;
    @BeforeEach void setup() {
        config=SonicConfigurationService.getInstance();
        for(var k:SonicConfiguration.values())if(config.hasSessionOverride(k))saved.put(k,config.getConfigValue(k));
        config.clearSessionOverrides();config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        CrossGameFeatureProvider.getInstance().resetState();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();config.clearSessionOverrides();saved.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();SessionManager.clear();TestEnvironment.activeGameplayMode();
    }
    @ParameterizedTest @CsvSource({"0,0x9F8,0xB96", "1,0x1C08,0x516"})
    void placedTriggeredPlatformLandsCarriesAndRestoresGraph(int act,int x,int y) {
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)x,(short)(y+0x20)).startPositionIsCentre().withFreshLevelStartLifecycle().build();
        for(int i=0;i<3;i++)fixture.stepFrame(false,false,false,false,false);
        var manager=GameServices.level().getObjectManager();
        var platform=manager.getActiveObjects().stream().filter(SozSwingingPlatformObjectInstance.class::isInstance)
                .map(SozSwingingPlatformObjectInstance.class::cast).filter(o->o.getSpawn().x()==x).findFirst().orElseThrow();
        var p=fixture.sprite();
        NativePositionOps.writeXPosPreserveSubpixel(p,platform.getX());
        NativePositionOps.writeYPosPreserveSubpixel(p,platform.getY()-0x11-p.getYRadius()-4);
        p.setAir(true);p.setXSpeed((short)0);p.setYSpeed((short)0x100);
        boolean landed=false,moved=false;int sx=0,sy=0;StringBuilder diagnostic=new StringBuilder();
        for(int i=0;i<100;i++) {
            var before=fixture.gameplayMode().getRewindRegistry().capture();
            fixture.stepFrame(false,false,false,false,false);
            platform=manager.getActiveObjects().stream().filter(SozSwingingPlatformObjectInstance.class::isInstance)
                    .map(SozSwingingPlatformObjectInstance.class::cast).filter(o->o.getSpawn().x()==x).findFirst().orElseThrow();
            if(manager.isRidingObject(p,platform)) {
                if(!landed){landed=true;sx=p.getCentreX();sy=p.getCentreY();replay(fixture,before,false,"platform landing");}
                else if(Math.abs(p.getCentreX()-sx)+Math.abs(p.getCentreY()-sy)>5){moved=true;replay(fixture,before,false,"platform carry");break;}
            }
            if(i%10==0)diagnostic.append(i+":"+p.getCentreX()+","+p.getCentreY()+" air="+p.getAir()+" platform="+platform.getX()+","+platform.getY()+" support="+manager.getRidingObject(p)+"; ");
            assertFalse(p.getDead());
        }
        assertTrue(landed && moved,"placed platform lands and carries rider landed="+landed+" moved="+moved+" "+diagnostic);
        assertNotNull(GameServices.level().getObjectRenderManager().getRenderer(Sonic3kObjectArtKeys.SOZ_SWINGING_PLATFORM));
    }
    @ParameterizedTest @CsvSource({"0,0x90C,0x940", "1,0x18F4,0x240"})
    void placedWireCapturesExtendsRatchetsAndRecreatesEveryLink(int act,int x,int y) {
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)x,(short)(y+0x40)).startPositionIsCentre().withFreshLevelStartLifecycle().build();
        for(int i=0;i<3;i++)fixture.stepFrame(false,false,false,false,false);
        var manager=GameServices.level().getObjectManager();
        var wire=manager.getActiveObjects().stream().filter(SozRapelWireObjectInstance.class::isInstance)
                .map(SozRapelWireObjectInstance.class::cast).filter(o->o.getSpawn().x()==x).findFirst().orElseThrow();
        var p=fixture.sprite();
        if(!wire.isPlayerHeld(p)) {
            NativePositionOps.writeXPosPreserveSubpixel(p,wire.handleX());
            NativePositionOps.writeYPosPreserveSubpixel(p,wire.handleY()+20);
            p.setAir(true);p.setXSpeed((short)0);p.setYSpeed((short)0);
        }
        fixture.stepFrame(false,false,false,false,false);
        assertTrue(wire.isPlayerHeld(p),"placed handle captures through production update");
        assertEquals(17,manager.getActiveObjects().stream().filter(SozRapelWireObjectInstance.Segment.class::isInstance).count());
        for(int i=0;i<76;i++) {
            var before=fixture.gameplayMode().getRewindRegistry().capture();fixture.stepFrame(false,false,false,false,false);
            if(i==12)replay(fixture,before,false,"wire extension graph");
        }
        wire=findWire(x);
        int initialX=wire.handleX(),initialY=wire.handleY();
        var before=fixture.gameplayMode().getRewindRegistry().capture();fixture.stepFrame(false,false,false,false,true);
        replay(fixture,before,true,"wire ratchet activation");
        for(int i=0;i<32;i++)fixture.stepFrame(false,false,false,false,false);
        wire=findWire(x);
        assertNotEquals(initialX+","+initialY,wire.handleX()+","+wire.handleY());
        before=fixture.gameplayMode().getRewindRegistry().capture();fixture.stepFrame(false,false,false,false,false);
        replay(fixture,before,false,"wire ratchet motion");
        wire=findWire(x);assertTrue(wire.isPlayerHeld(p));assertFalse(p.getDead());
        assertNotNull(GameServices.level().getObjectRenderManager().getRenderer(Sonic3kObjectArtKeys.SOZ_RAPEL_WIRE));
    }
    private static SozRapelWireObjectInstance findWire(int x) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(SozRapelWireObjectInstance.class::isInstance).map(SozRapelWireObjectInstance.class::cast)
                .filter(o->o.getSpawn().x()==x).findFirst().orElseThrow();
    }
    private static void replay(HeadlessTestFixture fixture,CompositeSnapshot before,boolean jump,String label) {
        var registry=fixture.gameplayMode().getRewindRegistry();var after=registry.capture();
        // Exercise identity relinking, not only the in-place object fast path.
        GameServices.level().getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        registry.restore(before);same(before,registry.capture(),label+" restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
        fixture.stepFrame(false,false,false,false,jump);same(after,registry.capture(),label+" forward replay");
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual,String label) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet(),label);
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),
                ()->label+" "+key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
}
