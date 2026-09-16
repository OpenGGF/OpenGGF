package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.*;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozSandMechanismsProduction {
    @ParameterizedTest
    @CsvSource({"0,0xE74,0x4BA", "0,0x1CCC,0xA7A", "0,0x30F4,0xB5A", "0,0x3CB4,0x4FA", "1,0x574,0x3FA", "1,0x22F4,0x4FA"})
    void everyPlacedWallRisesAndReplays(int act,int x,int y) {
        TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,act)
                .startPosition((short)(x-48),(short)(y-100)).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        fixture.sprite().refreshPersistentInstaShieldRegistration();
        var registry=fixture.gameplayMode().getRewindRegistry();
        boolean found=false;
        for(int i=0;i<35;i++) {
            var before=registry.capture();fixture.stepFrame(false,false,false,false,false);
            var wall=GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(SozRisingSandWallObjectInstance.class::isInstance)
                    .filter(o->o.getSpawn().x()==x).findFirst();
            if(wall.isPresent() && wall.get().getY()==y-104) {
                replay(fixture,before,registry.capture()); found=true;break;
            }
        }
        assertTrue(found,"placed wall must finish rising");
    }
    @ParameterizedTest
    @CsvSource({"0x2130,0x50,0x18", "0x2130,0x550,0x0A", "0x4940,0x450,0x9C", "0x4B80,0x1D0,0x9E"})
    void everyCorkSubtypeBreaksThroughProductionTouchAndRewindsChildGraph(int x,int y,int subtype) {
        TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,1)
                .startPosition((short)x,(short)y).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        fixture.sprite().refreshPersistentInstaShieldRegistration();
        var player=fixture.sprite();var registry=fixture.gameplayMode().getRewindRegistry();
        boolean broken=false;
        for(int i=0;i<8;i++) {
            NativePositionOps.writeXPosResetSubpixel(player,x);NativePositionOps.writeYPosResetSubpixel(player,y);
            player.setAir(true);player.setRollingFlagPreserveRadii(true);player.applyCustomRadii(7,14);
            player.setAnimationId(2);player.setXSpeed((short)0);player.setYSpeed((short)0);
            var before=registry.capture();fixture.stepFrame(false,false,false,false,false);
            var corks=GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(SozSandCorkObjectInstance.class::isInstance).toList();
            if(corks.stream().anyMatch(o->((SozSandCorkObjectInstance)o).getCollisionFlags()==0)) {
                assertEquals((subtype&0x7F)/16+1,
                        corks.stream().filter(o->((SozSandCorkObjectInstance)o).getOnScreenHalfHeight()==0x80).count(),"native column count");
                replay(fixture,before,registry.capture());
                var next=registry.capture();fixture.stepFrame(false,false,false,false,false);
                replay(fixture,next,registry.capture());
                var manager=GameServices.level().getObjectManager();
                var placement=corks.getFirst().getSpawn();
                var owner=assertInstanceOf(SozSandCorkObjectInstance.class,manager.getActiveObjectForRewind(placement));
                assertEquals(0x80,owner.getOnScreenHalfHeight(),"last column owns native respawn_addr");
                int ownerSlot=owner.getSlotIndex();
                player.setObjectControlled(true);player.setObjectControlSuppressesMovement(true);
                for(int frame=0;frame<130;frame++) fixture.stepFrame(false,false,false,false,false);
                owner=assertInstanceOf(SozSandCorkObjectInstance.class,manager.getActiveObjectForRewind(placement));
                assertEquals(ownerSlot,owner.getSlotIndex(),"handoff never moves an SST slot");
                int distance=(subtype&0x7F)*16;
                int finalY=(y-0x90-(distance/256)*256+distance)&0xFFFF;
                assertEquals(finalY,owner.getY(),"column drains exact native distance then remains stationary");
                var settledBefore=registry.capture();fixture.stepFrame(false,false,false,false,false);
                replay(fixture,settledBefore,registry.capture());
                NativePositionOps.writeXPosResetSubpixel(player,x+0x700);
                fixture.camera().setX((short)(x+0x600));
                fixture.stepFrame(false,false,false,false,false);
                assertNull(manager.getActiveObjectForRewind(placement),"column unload releases placement");
                NativePositionOps.writeXPosResetSubpixel(player,x);
                fixture.camera().setX((short)(x-0xA0));
                fixture.stepFrame(false,false,false,false,false);
                fixture.stepFrame(false,false,false,false,false);
                var reloaded=assertInstanceOf(SozSandCorkObjectInstance.class,manager.getActiveObjectForRewind(placement));
                assertEquals(0x80,reloaded.getOnScreenHalfHeight(),"broken cork reloads stationary sand");
                assertEquals(finalY,reloaded.getY());
                var reloadedBefore=registry.capture();fixture.stepFrame(false,false,false,false,false);
                replay(fixture,reloadedBefore,registry.capture());
                broken=true;break;
            }
        }
        assertTrue(broken,"rolling overlap must reach the native cork touch latch");
    }
    @ParameterizedTest
    @CsvSource({"0x12FE,0x7B8,0x15", "0x1DF0,0x93A,0x12", "0x1F00,0x1B8,0x15"})
    void placedSpawnersAllocateNativeBlocksAndReplay(int x,int y,int subtype) {
        TestEnvironment.activeGameplayMode();
        var fixture=HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8,0)
                .startPosition((short)(x+subtype*8),(short)(y-48)).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        fixture.sprite().refreshPersistentInstaShieldRegistration();
        var registry=fixture.gameplayMode().getRewindRegistry();
        boolean found=false;
        for(int i=0;i<8;i++) {
            var before=registry.capture();fixture.stepFrame(false,false,false,false,false);
            long blocks=GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(SozSpawningSandBlocksObjectInstance.class::isInstance)
                    .filter(o->o.getSpawn().x()==x).count();
            if(blocks>=2) {replay(fixture,before,registry.capture());found=true;break;}
        }
        assertTrue(found,"spawner allocates first block immediately");
    }
    private static void replay(HeadlessTestFixture fixture,
            com.openggf.game.rewind.CompositeSnapshot before,com.openggf.game.rewind.CompositeSnapshot after) {
        var registry=fixture.gameplayMode().getRewindRegistry();
        registry.restore(before);
        for(var key:before.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(key,before.get(key),registry.capture().get(key)).isEmpty(),()->key+": "+RewindSnapshotDiff.diffKey(key,before.get(key),registry.capture().get(key)));
        fixture.stepFrame(false,false,false,false,false);
        var actual=registry.capture();
        for(var key:after.entries().keySet()) assertTrue(RewindSnapshotDiff.diffKey(key,after.get(key),actual.get(key)).isEmpty(),
                ()->key+": "+RewindSnapshotDiff.diffKey(key,after.get(key),actual.get(key)));
    }
}
