package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezCurvedEnergyBridgeObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezCurvedEnergyBridgeHeadless {
    @AfterEach void reset(){SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear();}
    @ParameterizedTest
    @ValueSource(ints={320,800})
    void actualCurveSelectsTerrainThenDropsThePlayerAndReplaysAfterRecreation(int width) {
        var fixture=boot(width);fixture.sprite().setRingCount(7);fixture.sprite().setAir(true);
        fixture.stepIdleFrames(3);var bridge=bridge();
        assertTrue(bridge.admittedForTest(0));assertEquals(14,fixture.sprite().getTopSolidBit());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var snapshot=registry.capture();
        var first=ride(fixture,300);
        assertTrue(first.stream().anyMatch(s->s.contains("path=12,air=true")),"expiry releases to primary terrain in air");
        bridge.setDestroyed(true);GameServices.level().getObjectManager().removeDynamicObject(bridge);
        registry.restore(snapshot);assertNotSame(bridge,bridge());assertEquals(first,ride(fixture,300));
    }
    @ParameterizedTest
    @ValueSource(ints={320,800})
    void controllerApproachTraversesTheLitCurveAndFeedsTheHangingCarrier(int width) {
        var fixture=boot(width,0x700,0x910);fixture.sprite().setRingCount(7);
        fixture.stepIdleFrames(224);
        for(int i=0;i<65;i++)fixture.stepFrame(false,false,false,true,false);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var before=registry.capture();
        var first=approach(fixture);
        assertTrue(first.stream().anyMatch(s->s.contains("path=14")),"route crosses the lit curve's secondary terrain");
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o->o instanceof com.openggf.game.sonic3k.objects.S3kDezHangCarrierObjectInstance c&&c.grabbedForTest(0)),
                "the curve returns Sonic to the actual hanging carrier");
        registry.restore(before);
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,8,0,false,""));
        assertEquals(first,approach(fixture));
    }
    private List<String> approach(HeadlessTestFixture fixture) {
        var rows=new ArrayList<String>();
        for(int i=0;i<230;i++) {
            fixture.stepFrame(false,false,false,i<130,false);var p=fixture.sprite();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getXSpeed()+","+p.getYSpeed()+",path="+p.getTopSolidBit()
                    +",control="+p.isObjectControlled());
            assertFalse(p.getDead());
        }return rows;
    }
    @Test
    void romMappingIsFourPhasesOfCurvedEnergyPoints() throws Exception {
        boot(320);var frames=S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(),0x48038,4);
        assertArrayEquals(new int[]{3,3,3,2},frames.stream().mapToInt(f->f.pieces().size()).toArray());
        for(var f:frames)for(var p:f.pieces()) {assertEquals(8,p.tileIndex());assertEquals(1,p.widthTiles());assertEquals(1,p.heightTiles());}
        assertEquals(-60,frames.getFirst().pieces().getFirst().xOffset());assertEquals(32,frames.getFirst().pieces().getFirst().yOffset());
        for(int act=0;act<2;act++) {
            var e=Sonic3kPlcArtRegistry.getPlan(11,act).levelArt().stream().filter(a->a.key().equals(Sonic3kObjectArtKeys.DEZ_CURVED_ENERGY_BRIDGE)).findFirst().orElseThrow();
            assertEquals(0x3FF,e.artTileBase());assertEquals(1,e.palette());assertEquals(4,e.mappingFrameCount());
        }
    }
    private List<String> ride(HeadlessTestFixture fixture,int count) {
        var rows=new ArrayList<String>();for(int i=0;i<count;i++) {
            fixture.stepIdleFrames(1);var p=fixture.sprite();var b=bridge();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getYSpeed()+",path="+p.getTopSolidBit()+",air="+p.getAir()
                    +",remaining="+b.remainingForTest()+",draw="+b.drawPublishedForTest());
            assertFalse(p.getDead());
        }return rows;
    }
    private S3kDezCurvedEnergyBridgeObjectInstance bridge() {
        return GameServices.level().getObjectManager().getActiveObjects().stream().filter(o->o instanceof S3kDezCurvedEnergyBridgeObjectInstance)
                .map(o->(S3kDezCurvedEnergyBridgeObjectInstance)o).findFirst().orElseThrow();
    }
    private HeadlessTestFixture boot(int width) { return boot(width,0x840,0x910); }
    private HeadlessTestFixture boot(int width,int x,int y) {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,(width==800?WidescreenAspect.SUPER_32_9:WidescreenAspect.NATIVE_4_3).name());
        config.resolveDisplayAspect();config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ,0)
                .startPosition((short)x,(short)y).startPositionIsCentre().build();
        assertEquals(width,GameServices.camera().getWidth());return f;
    }
}
