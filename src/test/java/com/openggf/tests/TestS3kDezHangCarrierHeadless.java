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
import com.openggf.game.sonic3k.objects.S3kDezHangCarrierObjectInstance;
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
class TestS3kDezHangCarrierHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();SessionManager.clear(); }

    @ParameterizedTest
    @ValueSource(ints={320,800})
    void placedCarrierGrabsRisesToRealCeilingTravelsAndReplaysRelease(int width) {
        var fixture=boot(width);fixture.sprite().setRingCount(7);
        fixture.stepIdleFrames(3);var carrier=carrier();
        assertTrue(carrier.grabbedForTest(0),"actual placed handle captures Sonic");
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var snapshot=registry.capture();
        var first=ride(fixture,carrier,400);
        assertEquals(2,carrier.routineForTest(),"native ceiling collision selects horizontal travel");
        assertEquals(0,carrier.remainingTravelForTest());
        assertEquals(0x801-0x25*8,carrier.getX());assertTrue(carrier.getY()<0x86C);
        assertTrue(carrier.grabbedForTest(0));
        fixture.stepFrame(false,false,false,true,true);
        assertFalse(carrier.grabbedForTest(0));assertFalse(fixture.sprite().isObjectControlled());
        assertEquals(60,carrier.cooldownForTest(0));
        var released=playerRow(fixture);
        carrier.setDestroyed(true);GameServices.level().getObjectManager().removeDynamicObject(carrier);
        registry.restore(snapshot);assertNotSame(carrier,carrier());fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0,0,0,false,""));
        assertEquals(first,ride(fixture,carrier(),400));
        fixture.stepFrame(false,false,false,true,true);assertEquals(released,playerRow(fixture));
    }

    @Test
    void fourPieceRomHandleMappingUsesItsOwnBankAndBucket() throws Exception {
        boot(320);
        var frames=S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(),0x4717E,1);
        assertEquals(4,frames.getFirst().pieces().size());
        var a=frames.getFirst().pieces().getFirst();
        assertEquals(-24,a.xOffset());assertEquals(-20,a.yOffset());assertEquals(3,a.widthTiles());assertEquals(4,a.heightTiles());
        assertEquals(0xC,frames.getFirst().pieces().get(2).tileIndex());
        for(int act=0;act<2;act++) {
            var e=Sonic3kPlcArtRegistry.getPlan(11,act).levelArt().stream()
                    .filter(x->x.key().equals(Sonic3kObjectArtKeys.DEZ_HANG_CARRIER)).findFirst().orElseThrow();
            assertEquals(0x35D,e.artTileBase());assertEquals(1,e.palette());assertEquals(1,e.mappingFrameCount());
        }
        assertEquals(1,carrier().getPriorityBucket());
    }

    private List<String> ride(HeadlessTestFixture fixture,S3kDezHangCarrierObjectInstance carrier,int count) {
        var rows=new ArrayList<String>();
        for(int i=0;i<count;i++) {
            fixture.stepIdleFrames(1);assertFalse(fixture.sprite().getDead());
            rows.add(playerRow(fixture)+","+carrier.getX()+","+carrier.getY()+","+carrier.yFractionForTest()
                    +","+carrier.remainingTravelForTest()+","+carrier.grabbedForTest(0));
        }return rows;
    }
    private String playerRow(HeadlessTestFixture fixture) {
        var p=fixture.sprite();return p.getCentreX()+","+p.getCentreY()+","+p.getXSpeed()+","+p.getYSpeed()+","+p.isObjectControlled();
    }
    private S3kDezHangCarrierObjectInstance carrier() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o->o instanceof S3kDezHangCarrierObjectInstance)
                .map(o->(S3kDezHangCarrierObjectInstance)o).findFirst().orElseThrow();
    }
    private HeadlessTestFixture boot(int width) {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width==800?WidescreenAspect.SUPER_32_9:WidescreenAspect.NATIVE_4_3).name());
        config.resolveDisplayAspect();config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ,0)
                .startPosition((short)0x801,(short)0x894).startPositionIsCentre().build();
        assertEquals(width,GameServices.camera().getWidth());return fixture;
    }
}
