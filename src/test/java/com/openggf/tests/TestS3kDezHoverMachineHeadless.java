package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezHoverMachineObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezHoverRotorObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezHoverMachineHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides(); SessionManager.clear(); }

    @Test
    void parentAllocatesIndependentLaterRotorAndBothRecreateAcrossPriorityBoundary() {
        boot();
        var manager=GameServices.level().getObjectManager();
        var parent=new S3kDezHoverMachineObjectInstance(new ObjectSpawn(0x100,0x400,0x5E,0,0,false,0));
        manager.addDynamicObject(parent);parent.update(0,null);
        assertEquals(1,parent.mappingFrameForTest());
        var rotor=manager.getActiveObjects().stream().filter(o->o instanceof S3kDezHoverRotorObjectInstance)
                .map(o->(S3kDezHoverRotorObjectInstance)o).findFirst().orElseThrow();
        assertTrue(rotor.getSlotIndex()>parent.getSlotIndex());assertEquals(0x120,rotor.getX());
        for(int i=0;i<60;i++)rotor.update(i,null);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var snapshot=registry.capture();
        var expected=orbit(parent,rotor,150);
        parent.setDestroyed(true);rotor.setDestroyed(true);manager.removeDynamicObject(parent);manager.removeDynamicObject(rotor);
        registry.restore(snapshot);
        var restoredParent=manager.getActiveObjects().stream().filter(o->o instanceof S3kDezHoverMachineObjectInstance)
                .map(o->(S3kDezHoverMachineObjectInstance)o).findFirst().orElseThrow();
        var restoredRotor=manager.getActiveObjects().stream().filter(o->o instanceof S3kDezHoverRotorObjectInstance)
                .map(o->(S3kDezHoverRotorObjectInstance)o).findFirst().orElseThrow();
        assertNotSame(parent,restoredParent);assertNotSame(rotor,restoredRotor);
        assertEquals(expected,orbit(restoredParent,restoredRotor,150));
        restoredParent.setDestroyed(true);manager.removeDynamicObject(restoredParent);
        restoredRotor.update(0,null);assertFalse(restoredRotor.isDestroyed(),"housing deletion does not own the sibling");
    }

    @Test
    void fullNativeSlotPoolStillAnimatesHousingAndDoesNotRetryAllocation() {
        boot();var manager=GameServices.level().getObjectManager();
        var parent=new S3kDezHoverMachineObjectInstance(new ObjectSpawn(0x100,0x400,0x5E,0,0,false,0));
        manager.addDynamicObject(parent);var fillers=new ArrayList<S3kDezConveyorBeltObjectInstance>();
        boolean full=false;
        for(int i=0;i<160;i++) {
            var f=new S3kDezConveyorBeltObjectInstance(new ObjectSpawn(0,0,0x50,0,0,false,0));
            manager.addDynamicObject(f);if(f.isDestroyed()){full=true;break;}fillers.add(f);
        }
        assertTrue(full);parent.update(0,null);assertEquals(1,parent.mappingFrameForTest());
        manager.removeDynamicObject(fillers.getLast());parent.update(1,null);assertEquals(0,parent.mappingFrameForTest());
        assertFalse(manager.getActiveObjects().stream().anyMatch(o->o instanceof S3kDezHoverRotorObjectInstance));
    }

    @Test
    void romMappingKeepsHousingPiecePalettesAndRotorBank() throws Exception {
        boot();var frames=S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(),0x495D8,3);
        assertArrayEquals(new int[]{2,2,1},frames.stream().mapToInt(f->f.pieces().size()).toArray());
        assertEquals(0,frames.get(0).pieces().get(0).tileIndex());
        assertEquals(0xC,frames.get(0).pieces().get(1).tileIndex());
        assertEquals(0x1E,frames.get(1).pieces().get(1).tileIndex());
        assertEquals(0xE,frames.get(2).pieces().getFirst().tileIndex());
        for(int act=0;act<2;act++) {
            var entry=Sonic3kPlcArtRegistry.getPlan(11,act).levelArt().stream()
                    .filter(e->e.key().equals(Sonic3kObjectArtKeys.DEZ_HOVER_MACHINE)).findFirst().orElseThrow();
            assertEquals(0x30D,entry.artTileBase());assertEquals(1,entry.palette());assertEquals(3,entry.mappingFrameCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints={320,800})
    void actualPlacedRotorLiftsSonicAndReplaysAtBothWidths(int width) {
        var config=SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width==800?WidescreenAspect.SUPER_32_9:WidescreenAspect.NATIVE_4_3).name());
        config.resolveDisplayAspect();config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        SessionManager.clear();TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ,0)
                .startPosition((short)0x5B8,(short)0x890).startPositionIsCentre().build();
        assertEquals(width,GameServices.camera().getWidth());fixture.sprite().setAir(true);fixture.sprite().setRingCount(7);
        int startY=fixture.sprite().getCentreY();fixture.stepIdleFrames(20);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(o->o instanceof S3kDezHoverRotorObjectInstance));
        assertTrue(fixture.sprite().getCentreY()<startY,"placed rotor supplies lift");
        assertFalse(fixture.sprite().getDead());
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var snapshot=registry.capture();
        var first=ride(fixture,180);registry.restore(snapshot);assertEquals(first,ride(fixture,180));
    }
    private List<String> ride(HeadlessTestFixture fixture,int count) {
        var rows=new ArrayList<String>();for(int i=0;i<count;i++) {
            fixture.stepIdleFrames(1);var p=fixture.sprite();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getYSubpixelRaw()+","+p.getYSpeed()+","+p.getFlipAngle()+","+p.getFlipsRemaining());
            assertFalse(p.getDead());
        }return rows;
    }

    private List<String> orbit(S3kDezHoverMachineObjectInstance parent,S3kDezHoverRotorObjectInstance rotor,int count) {
        var rows=new ArrayList<String>();for(int i=0;i<count;i++) {
            parent.update(i,null);rotor.update(i,null);
            rows.add(parent.mappingFrameForTest()+","+rotor.getX()+","+rotor.angleForTest()+","+rotor.getPriorityBucket());
        }return rows;
    }
    private HeadlessTestFixture boot() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ,0).build();
    }
}
