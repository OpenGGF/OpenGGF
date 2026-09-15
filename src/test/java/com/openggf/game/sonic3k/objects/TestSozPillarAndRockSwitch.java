package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.game.OscillationManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.solid.*;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.level.objects.*;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSozPillarAndRockSwitch {
    @AfterEach void reset() { AbstractObjectInstance.resetCameraBoundsForTests(); OscillationManager.reset(); com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.reset(); }
    @Test void allMovementModesReadTheirNativeOscillatorAndRestore() throws Exception {
        var rom=mock(Rom.class);
        when(rom.readBytes(0x4116A,4)).thenReturn(new byte[]{32,64,0,0});
        var services=new StubObjectServices() { @Override public Rom rom() { return rom; } };
        OscillationManager.reset();
        for(int i=0;i<37;i++) OscillationManager.update(i);
        for(int mode=0;mode<=6;mode++) for(int flip=0;flip<=1;flip++) {
            var spawn=new ObjectSpawn(1000,1000,0x42,mode,flip,false,0);
            var pillar=new SozFloatingPillarObjectInstance(spawn);pillar.setServices(services);pillar.update(0,null);
            int delta=switch(mode) {
                case 1,4 -> OscillationManager.getByte(8)-32;
                case 2,5 -> OscillationManager.getByte(0x1C)-64;
                case 3,6 -> OscillationManager.getByte(0x38)*2-128;
                default -> 0;
            };
            if(flip==1)delta=-delta;
            assertEquals(1000+(mode<=3?delta:0),pillar.getX());
            assertEquals(1000+(mode>=4?delta:0),pillar.getY());
            assertEquals(new SolidObjectParams(43,64,65),pillar.getSolidParams());
            var restored=new SozFloatingPillarObjectInstance(spawn);restored.setServices(services);
            restored.restoreRewindState(pillar.captureRewindState());
            OscillationManager.update(100+mode*2+flip);
            pillar.update(1,null);restored.update(1,null);
            assertEquals(pillar.getX(),restored.getX());assertEquals(pillar.getY(),restored.getY());
        }
    }
    @Test void spikedFacesHurtOnlyTheirNewContactAndRespectInvulnerability() throws Exception {
        for(int frame:new int[]{1,2}) for(ContactKind kind:new ContactKind[]{ContactKind.TOP,ContactKind.BOTTOM,ContactKind.SIDE}) {
            var rom=mock(Rom.class);
            when(rom.readBytes(0x4116A+frame*4L,4)).thenReturn(new byte[]{32,80,(byte)frame,(byte)(frame==1?0x30:0xC)});
            var p=spy(new TestablePlayableSprite("sonic",(short)0,(short)0));p.setCentreX((short)1000);p.setCentreY((short)1000);
            p.setInvulnerableFrames(0);
            var context=mock(ObjectSolidExecutionContext.class);
            var result=new PlayerSolidContactResult(kind,kind==ContactKind.TOP,false,false,false,null,null,0);
            when(context.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(null,Map.of(p,result)));
            var pillar=new SozFloatingPillarObjectInstance(new ObjectSpawn(1000,1000,0x42,frame<<4,0,false,0));
            pillar.setServices(new StubObjectServices() {
                @Override public Rom rom() { return rom; }
                @Override public ObjectSolidExecutionContext solidExecution() { return context; }
            }.withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of)));
            p.setInvulnerableFrames(120);pillar.update(0,p);assertFalse(p.getDead());
            p.setInvulnerableFrames(0);pillar.update(1,p);
            boolean damaging=frame==1?kind==ContactKind.TOP:kind==ContactKind.BOTTOM;
            assertEquals(damaging,p.getDead(),"frame="+frame+" contact="+kind);
            if(damaging)verify(p).applyHurtOrDeath(1000,com.openggf.game.DamageCause.NORMAL,false);
        }
    }
    @Test void earlierSolidCannotConsumePillarsAirborneStandingBit() throws Exception {
        var rom=mock(Rom.class);when(rom.readBytes(0x4116E,4)).thenReturn(new byte[]{32,80,1,0x30});
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        p.setWidth(20);p.setHeight(38);p.setCentreX((short)1000);p.setCentreY((short)905);
        var services=new StubObjectServices() { @Override public Rom rom() { return rom; } }
                .withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of)).withIsolatedObjectManager();
        var manager=services.objectManager();
        var pillar=new SozFloatingPillarObjectInstance(new ObjectSpawn(1000,1000,0x42,0x10,0,false,0));
        var earlier=new SozSolidSpritesObjectInstance(new ObjectSpawn(500,1000,0x49,0,0,false,0));
        manager.addDynamicObject(earlier);manager.addDynamicObject(pillar);pillar.update(0,p);
        AbstractObjectInstance.updateCameraBounds(800,800,1120,1024,0);
        pillar.snapshotPreUpdatePosition();
        manager.forceRidingObjectForBootstrap(p,pillar);
        p.setAir(false);p.setOnObject(true);manager.processImmediateInlineSolidCheckpoint(pillar,p,List.of());
        p.setAir(true);p.setOnObject(false);p.setYSpeed((short)0x38);
        manager.processImmediateInlineSolidCheckpoint(earlier,p,List.of());
        assertTrue(manager.hasObjectStandingBit(p,pillar),"earlier SST must preserve pillar-owned stale bit");
        manager.processImmediateInlineSolidCheckpoint(pillar,p,List.of());
        assertTrue(p.getAir());assertFalse(p.isOnObject());assertFalse(manager.isRidingObject(p));
        assertEquals(0x38,p.getYSpeed());assertFalse(p.getDead());
    }
    @Test void linkedSlotMovesRockAndInvalidReuseClearsWithoutSearchingForAnother() throws Exception {
        var rom=mock(Rom.class);when(rom.read32BitAddr(anyLong())).thenReturn(0x100);
        when(rom.read16BitAddr(anyLong())).thenReturn(1000);
        var state=new SozZoneRuntimeState(1,PlayerCharacter.SONIC_AND_TAILS);
        var manager=mock(ObjectManager.class);
        var p=new TestablePlayableSprite("sonic",(short)0,(short)0);
        var services=new StubObjectServices() {
            @Override public Rom rom() { return rom; }
            @Override public ObjectManager objectManager() { return manager; }
            @Override public SozZoneRuntimeState zoneRuntimeState() { return state; }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> p,List::of));
        var rock=new SozPushableRockObjectInstance(new ObjectSpawn(980,1000,0x3E,0x87,0,false,0));
        rock.setServices(services);rock.setSlotIndex(42);rock.update(0,p);assertEquals(42,state.pushableRockSlot());
        when(manager.getActiveObjects()).thenReturn(List.of(rock));
        var button=new SozPushSwitchObjectInstance(new ObjectSpawn(1000,1000,0x45,0x91,0,false,0));button.setServices(services);
        button.update(0,p);assertEquals(4,SozZoneRuntimeState.trigger(1));assertEquals(973,rock.getX());
        button.update(1,p);assertEquals(4,SozZoneRuntimeState.trigger(1),"stationary rock offsets the fast decay");
        rock.setSlotIndex(43);button.update(2,p);assertEquals(-1,state.pushableRockSlot());
        assertEquals(3,SozZoneRuntimeState.trigger(1));
        rock.setSlotIndex(42);state.publishPushableRockSlot(42);rock.setDestroyed(true);
        button.update(3,p);assertEquals(-1,state.pushableRockSlot());
    }
}
