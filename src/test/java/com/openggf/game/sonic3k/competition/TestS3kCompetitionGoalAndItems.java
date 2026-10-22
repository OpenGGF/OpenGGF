package com.openggf.game.sonic3k.competition;

import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionGoalAndItems {
    @Test void cardinalWheelOrbitUses56PixelsWrapAndNativeFacingDirection() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        int[][] cases = {{0,0x428,200},{0x40,0x7F0,256},{0x80,0x7B8,200},{0xC0,0x7F0,144}};
        for (int[] c : cases) {
            var wheel = new CompetitionItemObjectInstance(new ObjectSpawn(0x7F0,200,0x64,c[0],0,false,200));
            wheel.updateNative(world.session);
            assertEquals(c[1],wheel.getX()); assertEquals(c[2],wheel.getY());
            assertEquals((c[0]+1)&0xFF,wheel.angle());
        }
        var reverse = new CompetitionItemObjectInstance(new ObjectSpawn(0x500,200,0x64,0,1,false,200));
        reverse.updateNative(world.session);
        assertEquals(255,reverse.angle());
        assertEquals(7,reverse.effect(), "native init is an empty wheel until a completed lap replenishes it");
        assertTrue(reverse.isPersistent(), "the native routine has no camera-based deletion");
    }

    @Test void everyRealEffectConsumesAtFullRamAndImmediateEffectsStillApply() {
        for (int effect = 0; effect <= 6; effect++) {
            CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
            Arrays.fill(world.occupied,true);
            var wheel = wheel(); wheel.replenish(effect);
            wheel.collectNative(world.session,1);
            assertEquals(7,wheel.effect(),"effect "+effect);
            assertEquals(0,wheel.collisionFlags(),"effect "+effect);
            assertTrue(world.objects.isEmpty(),"no dynamic child exists with full RAM: effect "+effect);
            if (effect == 0) assertEquals(0xC00,world.players.get(1).speeds().maxSpeed());
            if (effect == 1) assertEquals(0x300,world.players.get(1).speeds().maxSpeed());
            if (effect == 3) assertEquals(1,world.session.rings(1));
            assertEquals(effect == 4,world.reservedProjectile != null,
                    "only projectile4 overwrites the reserved SST rather than using dynamic allocation");
        }
    }

    @Test void simultaneousCollectorsApplyOnlyToTheFirstNativePlayerBit() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var wheel = wheel(); wheel.replenish(3); wheel.updateNative(world.session);
        for (S3kCompetitionPlayer player : world.players) player.setCentre(wheel.getX(),wheel.getY());
        for (S3kCompetitionPlayer player : world.players) wheel.touchNativePlayer(player);
        wheel.updateNative(world.session);
        assertEquals(1,world.session.rings(0)); assertEquals(0,world.session.rings(1));
    }

    @Test void swapTransfersTheFullSecondaryByteAndOnlyShoesTransferTheirSpeedTuple() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var a = world.players.get(0); var b = world.players.get(1);
        a.setStatusSecondary(0x84); a.setSpeedShoesTimer(23); a.setSpeeds(0xC00,0x18,0x80);
        b.setStatusSecondary(0x12); b.setSpeedShoesTimer(7); b.setSpeeds(0x444,0x22,0x33);
        world.session.applyImmediateItem(1,2);
        assertEquals(0x12,a.statusSecondary()); assertEquals(0x84,b.statusSecondary());
        assertEquals(7,a.speedShoesTimer()); assertEquals(23,b.speedShoesTimer());
        assertEquals(new S3kCompetitionPlayer.Speeds(0x600,0x10,0x20),a.speeds(),
                "the slot without shoes uses its own character table, not the other character's normal speed");
        assertEquals(new S3kCompetitionPlayer.Speeds(0xC00,0x18,0x80),b.speeds());
    }

    @Test void shoeExpiryReadsTheLevelClockEveryEighthTickAndRestoresEachCharacter() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.applyImmediateItem(0,0); world.session.applyImmediateItem(1,1);
        world.session.beforeFrame(7);
        assertEquals(75,world.players.get(0).speedShoesTimer());
        for (int frame=8; frame<=600; frame+=8) world.session.beforeFrame(frame);
        assertEquals(0,world.players.get(0).speedShoesTimer());
        assertEquals(new S3kCompetitionPlayer.Speeds(0x600,0x10,0x20),world.players.get(0).speeds());
        assertEquals(new S3kCompetitionPlayer.Speeds(0x4C0,0x1C,0x70),world.players.get(1).speeds());
        assertEquals(0,world.players.get(1).statusSecondary() & 4);
    }

    @Test void droppedSpringChecksLayerAndAppliesNativeFlipToEitherParticipant() {
        for (int slot=0; slot<2; slot++) {
            CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
            var wheel = wheel(); wheel.replenish(6); wheel.collectNative(world.session,slot);
            var spring = (CompetitionItemObjectInstance)world.objects.getFirst();
            land(world,spring);
            var player = world.players.get(slot);
            player.setCentre(spring.getX(),spring.getY()); player.setFacingLeft(slot==1);
            spring.touchNativePlayer(player);
            spring.updateNative(world.session);
            assertEquals(-0xA00,player.yVelocity()); assertTrue(player.airborne());
            assertEquals(slot==1?-1:1,player.groundVelocity());
            assertEquals((slot==1?-1:1)&0xFF,player.flipAngle());
            assertEquals(4,player.flipSpeed()); assertEquals(10,spring.effect());
        }
    }

    @Test void droppedSlideBoostsThenDragsAndReleasesWithoutAnOrphanedLock() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var wheel = wheel(); wheel.replenish(5); wheel.collectNative(world.session,0);
        var slide = (CompetitionItemObjectInstance)world.objects.getFirst(); land(world,slide);
        var player = world.players.get(1); player.setCentre(slide.getX(),slide.getY());
        player.setGroundVelocity(0x100); player.setAirborne(false);
        slide.touchNativePlayer(player);
        slide.updateNative(world.session);
        assertEquals(0x5E0,player.groundVelocity(),"native +$200, double, then same-pass $20 drag");
        assertEquals(0x80,player.statusSecondary() & 0x80);
        for (int i=0;i<70 && !slide.isDestroyed();i++) slide.updateNative(world.session);
        assertEquals(0,player.groundVelocity()); assertEquals(0,player.statusSecondary() & 0x80);
        assertEquals(5,player.moveLock()); assertTrue(slide.isDestroyed());
    }

    @Test void projectileOverwritesReservedSlotAtFullRamAndUsesNativeTerrainProbes() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        Arrays.fill(world.occupied,true);
        var wheel = wheel(); wheel.replenish(4); wheel.collectNative(world.session,0);
        var projectile = (CompetitionItemObjectInstance)world.reservedProjectile;
        land(world,projectile);
        int beforeX=projectile.getX();
        world.leftWallDistance=-2;
        projectile.updateNative(world.session);
        assertEquals((beforeX-4+2)&0xFFFF,projectile.getX());
        assertEquals(CompetitionItemObjectInstance.Kind.EFFECT,projectile.kind());
        assertEquals(11,projectile.effect());
        wheel.replenish(4); wheel.collectNative(world.session,1);
        assertNotSame(projectile,world.reservedProjectile);
    }

    @Test void objectRecreationRestoresWheelAnimationAndForwardOutcomeWithBothPlayers() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        var wheel = wheel(); wheel.replenish(3);
        for (int i=0;i<11;i++) wheel.updateNative(world.session);
        var objectBefore=GenericFieldCapturer.captureObjectSubclassScalars(wheel);
        var worldBefore=world.session.capture();
        for (int i=0;i<40;i++) wheel.updateNative(world.session);
        var objectExpected=GenericFieldCapturer.captureObjectSubclassScalars(wheel);
        var worldExpected=world.session.capture();
        var restored=new CompetitionItemObjectInstance(wheel.getSpawn());
        GenericFieldCapturer.restore(restored,objectBefore); world.session.restore(worldBefore);
        for (int i=0;i<40;i++) restored.updateNative(world.session);
        assertEquals(objectExpected,GenericFieldCapturer.captureObjectSubclassScalars(restored));
        assertEquals(worldExpected,world.session.capture());
    }

    @Test void goalUsesTheRomAnimationCadenceAndRecreatesScalarState() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.signalGo();
        var goal = new CompetitionGoalObjectInstance(new ObjectSpawn(1000,200,0x65,8,0,false,200));
        for (int i=0;i<6;i++) goal.updateNative(world.session);
        assertEquals(0,goal.mappingFrame());
        var snapshot=GenericFieldCapturer.captureObjectSubclassScalars(goal);
        goal.updateNative(world.session); assertEquals(1,goal.mappingFrame());
        GenericFieldCapturer.restore(goal,snapshot);
        goal.updateNative(world.session); assertEquals(1,goal.mappingFrame());
        assertTrue(goal.isPersistent()); assertEquals(5,goal.getPriorityBucket());
    }

    private static CompetitionItemObjectInstance wheel() {
        var item=new CompetitionItemObjectInstance(new ObjectSpawn(0x500,200,0x64,0,0,false,200));
        item.setSlotIndex(10); return item;
    }
    private static void land(CompetitionTestWorld world,CompetitionItemObjectInstance item) {
        List<CompetitionItemObjectInstance.Kind> dropping=List.of(CompetitionItemObjectInstance.Kind.DROPPING_SLIDE,
                CompetitionItemObjectInstance.Kind.DROPPING_SPRING,CompetitionItemObjectInstance.Kind.DROPPING_PROJECTILE);
        for (int i=0;i<120 && dropping.contains(item.kind());i++) item.updateNative(world.session);
        assertFalse(dropping.contains(item.kind()),"bounded floor fixture must land the item");
        item.updateNative(world.session); // First grounded routine publishes the next touch list.
    }
}
