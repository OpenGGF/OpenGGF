package com.openggf.game.sonic3k.competition;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.openggf.game.sonic3k.competition.S3kCompetitionDamage.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Independent HurtCharacter/Obj_Bouncing_Ring/GiveRing cases, with actual ROM sparkle bytes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionDamage {
    private RomByteReader reader;
    @BeforeEach void openRom() throws Exception { reader=RomByteReader.fromRom(RomManager.getInstance().getRom()); }

    @Test void zeroRingBladeContactKillsEitherPlayerButAShieldConsumesOnlyItsNativeBits() {
        for (int slot=0;slot<2;slot++) {
            var world=world(); var player=world.players.get(slot); player.setFractions(0x1234,0x5678);
            assertEquals(KILLED,S3kCompetitionDamage.contact(world.session,world,player,player.centreX()));
            assertEquals(6,player.routine()); assertEquals(-0x700,player.yVelocity());
            assertTrue(player.highPriority()); assertEquals(0x18,player.animation());
            assertEquals(0x1234,player.xFraction()); assertEquals(0x5678,player.yFraction());
            assertTrue(world.objects.isEmpty()); assertEquals(List.of(Sonic3kSfx.DEATH),world.sounds);
            world=world(); player=world.players.get(slot); player.setStatusSecondary(0xF5);
            assertEquals(HURT,S3kCompetitionDamage.contact(world.session,world,player,player.centreX()+8));
            assertEquals(4,player.routine()); assertEquals(0x84,player.statusSecondary());
            assertEquals(-0x200,player.xVelocity()); assertEquals(-0x400,player.yVelocity());
            assertTrue(world.objects.isEmpty());
        }
    }

    @Test void projectileGatesBeforeAnyFractionWriteAndUndoesTheFullSignedYIntegration() {
        for (int gate=0;gate<3;gate++) {
            var world=world(); var player=world.players.get(1);
            player.setFractions(0x1357,0x2468); player.setYVelocity(0x281);
            if (gate==0) player.setInvulnerabilityTimer(1);
            if (gate==1) player.setStatusSecondary(2);
            if (gate==2) player.setRoutine(4);
            var before=player.capture();
            assertEquals(IGNORED,S3kCompetitionDamage.projectile(world.session,world,player,player.centreX()));
            assertEquals(before,player.capture()); assertTrue(world.sounds.isEmpty()); assertTrue(world.objects.isEmpty());
        }
        var world=world(); var player=world.players.get(1);
        player.setStatusSecondary(1); player.setCentre(0x600,200); player.setFractions(0x1357,0x2468);
        player.setYVelocity(0x281);
        assertEquals(HURT,S3kCompetitionDamage.projectile(world.session,world,player,0x5F0));
        assertEquals(197,player.centreY()); assertEquals(0xA368,player.yFraction());
        assertEquals(0x1357,player.xFraction()); assertEquals(0x200,player.xVelocity());
    }

    @Test void fullRamFailurePreservesBothBanksAndStillAppliesNativeKnockback() {
        for (int slot=0;slot<2;slot++) {
            var world=world(); Arrays.fill(world.occupied,true);
            world.session.setNativeRings(0,12); world.session.setNativeRings(1,7);
            var player=world.players.get(slot); player.setStatus(0x40);
            assertEquals(HURT,S3kCompetitionDamage.contact(world.session,world,player,player.centreX()+1));
            assertEquals(12,world.session.rings(0)); assertEquals(7,world.session.rings(1));
            assertTrue(world.objects.isEmpty()); assertEquals(-0x100,player.xVelocity());
            assertEquals(-0x200,player.yVelocity()); assertEquals(120,player.invulnerabilityTimer());
            assertEquals(List.of(Sonic3kSfx.DEATH),world.sounds);
        }
    }

    @Test void spillInitializationReadsEitherOwnerCaps32AndPreservesTheNativeP2BankBug() {
        for (int slot=0;slot<2;slot++) {
            var world=world(); world.session.setNativeRings(0,40); world.session.setNativeRings(1,7);
            var player=world.players.get(slot);
            S3kCompetitionDamage.contact(world.session,world,player,player.centreX());
            assertEquals(1,world.objects.size()); assertEquals(40,world.session.rings(0));
            var owner=ring(world,0); owner.updateNative(world.session,world,0);
            assertEquals(slot==0?32:7,world.objects.size());
            assertEquals(0,world.session.rings(0)); assertEquals(7,world.session.rings(1));
            assertEquals(0xFF,world.spillAnimation.counter());
            assertEquals(List.of(Sonic3kSfx.DEATH,Sonic3kSfx.RING_LOSS),world.sounds);
            assertEquals(-196,owner.nativeXVelocity()); assertEquals(-980,owner.nativeYVelocity());
            assertEquals(196,ring(world,1).nativeXVelocity()); assertEquals(-1004,ring(world,1).nativeYVelocity());
            assertEquals(-568,ring(world,2).nativeXVelocity()); assertEquals(-848,ring(world,2).nativeYVelocity());
            assertEquals(0x3C00,owner.xFraction()); assertEquals(0x1400,owner.yFraction());
            assertEquals(8,owner.getOnScreenHalfWidth()); assertEquals(0,owner.getOnScreenHalfHeight());
            assertEquals(3,owner.getPriorityBucket()); assertEquals(0xA6BC,owner.nativeArtWord());
            assertEquals(0x47,owner.getCollisionFlags()); assertTrue(owner.publishesTouchResponseListEntryThisFrame());
        }
    }

    @Test void partialRamStopsSpawningAndAZeroedDeferredCountStillRunsTheNativeDbf() {
        for (boolean zeroBeforeInit:List.of(false,true)) {
            var world=world(); Arrays.fill(world.occupied,true);
            world.occupied[4]=world.occupied[5]=world.occupied[6]=false;
            world.session.setNativeRings(0,9); var player=world.players.getFirst();
            S3kCompetitionDamage.contact(world.session,world,player,player.centreX());
            if (zeroBeforeInit) world.session.setNativeRings(0,0);
            ring(world,0).updateNative(world.session,world,0);
            assertEquals(3,world.objects.size()); assertEquals(List.of(4,5,6),world.objects.stream().map(AbstractObjectInstance::getSlotIndex).toList());
            assertEquals(0,world.session.rings(0)); assertEquals(0xFF,world.spillAnimation.counter());
        }
    }

    @Test void eitherHumanCanConsumeTheSameRingPointerButNativeGiveRingAlwaysAwardsP1() {
        var world=world(); world.session.setNativeRings(0,1);
        S3kCompetitionDamage.contact(world.session,world,world.players.getFirst(),0);
        var ring=ring(world,0); ring.updateNative(world.session,world,0);
        for (var player:world.players) { player.setRoutine(2); player.setCentre(ring.getX(),ring.getY()); player.setInvulnerabilityTimer(90); }
        assertTrue(ring.touchNativePlayer(world.players.getFirst())); assertEquals(2,ring.routine());
        world.players.get(1).setInvulnerabilityTimer(89);
        assertTrue(ring.touchNativePlayer(world.players.get(1))); assertEquals(4,ring.routine());
        assertEquals(3,ring.getPriorityBucket(),"Touch_ChkValue writes only routine, not priority or flags");
        world.players.getFirst().setInvulnerabilityTimer(89);
        assertTrue(ring.touchNativePlayer(world.players.getFirst())); assertEquals(0x47,ring.getCollisionFlags());
        ring.updateNative(world.session,world,0);
        assertEquals(1,world.session.rings(0)); assertEquals(0,world.session.rings(1));
        assertEquals(0,ring.getCollisionFlags()); assertEquals(1,ring.getPriorityBucket());
        assertEquals(4,ring.mappingFrame()); assertFalse(ring.publishesTouchResponseListEntryThisFrame());
        for (int tick=0;tick<24;tick++) ring.updateNative(world.session,world,0);
        assertEquals(8,ring.routine()); assertEquals(7,ring.mappingFrame());
        assertFalse(ring.isDestroyed()); assertTrue(ring.nativeDrawPublished(),"FC still falls through Draw_Sprite this dispatch");
        ring.updateNative(world.session,world,0); assertTrue(ring.isDestroyed()); assertFalse(ring.nativeDrawPublished());
    }

    @Test void nativeGiveRingCapsAndThresholdFlagsArePerPlayerIncludingAlreadyCappedP2() {
        var world=world();
        for (int slot=0;slot<2;slot++) {
            for (int count=0;count<1001;count++) world.session.giveRing(slot);
            assertEquals(999,world.session.rings(slot)); assertEquals(999,world.session.damage().totalRings(slot));
            assertEquals(6,world.session.damage().extraLifeFlags(slot)); assertEquals(2,world.lifeAwards[slot]);
        }
        assertEquals(4,world.extraLifeMusicCalls);
        world=world(); world.session.setNativeRings(0,999); world.session.setNativeRings(1,999);
        world.session.giveRing(0); assertEquals(0,world.lifeAwards[0]);
        world.session.giveRing(1); world.session.giveRing(1);
        assertEquals(2,world.lifeAwards[1]); assertEquals(6,world.session.damage().extraLifeFlags(1));
        assertEquals(999,world.session.rings(1));
    }

    @Test void thresholdBanksRestoreAndReplayBothHumansAndResetAtTheArenaBoundary() {
        var world=world(); world.session.setNativeRings(0,99); world.session.setNativeRings(1,199);
        world.session.damage().setExtraLifeFlags(1,2);
        var before=world.session.capture();
        world.session.giveRing(0); world.session.giveRing(1);
        var forward=world.session.capture();
        assertEquals(100,world.session.rings(0)); assertEquals(200,world.session.rings(1));
        assertEquals(new S3kCompetitionDamage.State(1,1,2,6),world.session.damage().capture());
        assertArrayEquals(new int[]{1,1},world.lifeAwards); assertEquals(2,world.extraLifeMusicCalls);
        world.session.restore(before); Arrays.fill(world.lifeAwards,0); world.extraLifeMusicCalls=0;
        world.session.giveRing(0); world.session.giveRing(1);
        assertEquals(forward,world.session.capture());
        assertArrayEquals(new int[]{1,1},world.lifeAwards); assertEquals(2,world.extraLifeMusicCalls);
        world.session.enterArena(S3kCompetitionSession.Arena.BALLOON_PARK);
        assertEquals(new S3kCompetitionDamage.State(0,0,0,6),world.session.damage().capture());
        assertEquals(0,world.session.rings(0)); assertEquals(0,world.session.rings(1));
        assertArrayEquals(new int[]{1,1},world.lifeAwards,"level reset retains the external life bank");
    }

    @Test void grandPrixLoadPreservesTheFullP2FlagByteAcrossRecreationAndSeventeenForwardPasses() {
        var world=new CompetitionTestWorld(S3kCompetitionSession.Mode.GRAND_PRIX);
        world.session.damage().restore(new S3kCompetitionDamage.State(321,654,0xA6,0xE6));
        world.session.setNativeRings(0,123); world.session.setNativeRings(1,234);
        world.session.enterArena(S3kCompetitionSession.Arena.BALLOON_PARK);
        assertEquals(new S3kCompetitionDamage.State(0,0,0,0xE6),world.session.damage().capture());
        assertEquals(0,world.session.rings(0)); assertEquals(0,world.session.rings(1));
        world.session.setNativeRings(0,90); world.session.setNativeRings(1,190);
        var before=world.session.capture();
        forwardRingFrames(world,17);
        var forward=world.session.capture();
        assertArrayEquals(new int[]{1,0},world.lifeAwards,"P2 thresholds remain claimed in the next GP arena");
        assertEquals(new S3kCompetitionDamage.State(17,17,2,0xE6),world.session.damage().capture());
        var recreated=new CompetitionTestWorld(S3kCompetitionSession.Mode.GRAND_PRIX);
        recreated.session.restore(before);
        forwardRingFrames(recreated,17);
        assertEquals(forward,recreated.session.capture());
        assertArrayEquals(world.lifeAwards,recreated.lifeAwards);
        assertEquals(world.extraLifeMusicCalls,recreated.extraLifeMusicCalls);
    }

    @Test void actualLevelLoadOperandsClearP1FlagsAndBothTotalsWhileExcludingP2Flags() {
        // loc_63B4 (no starpost), loc_6404, and GiveRing_Tails's two threshold writes.
        assertEquals(0x7000,reader.readU16BE(0x63B4));
        assertEquals(0x11C0,reader.readU16BE(0x63C4)); assertEquals(0xFE1B,reader.readU16BE(0x63C6));
        assertEquals(0x31C0,reader.readU16BE(0x6418)); assertEquals(0xFEC8,reader.readU16BE(0x641A));
        assertEquals(0x31C0,reader.readU16BE(0x641C)); assertEquals(0xFECA,reader.readU16BE(0x641E));
        for (int address:new int[]{0x1A620,0x1A630}) {
            assertEquals(0x08F8,reader.readU16BE(address));
            assertEquals(address==0x1A620?1:2,reader.readU16BE(address+2));
            assertEquals(0xFEBF,reader.readU16BE(address+4));
        }
        int[][] clears={{0x60DE,0xAC00,0xFF},{0x60EE,0xB000,0x7FF},
                {0x60FE,0xF628,0x15},{0x610E,0xF700,0x3F},
                {0x611E,0xFE6E,0x12},{0x612E,0xFA80,0x1F}};
        for (int[] clear:clears) {
            int address=clear[0],start=clear[1],longsMinusOne=clear[2];
            assertEquals(0x43F8,reader.readU16BE(address)); assertEquals(start,reader.readU16BE(address+2));
            assertEquals(0x7000,reader.readU16BE(address+4)); assertEquals(0x323C,reader.readU16BE(address+6));
            assertEquals(longsMinusOne,reader.readU16BE(address+8));
            assertEquals(0x22C0,reader.readU16BE(address+10)); assertEquals(0x51C9,reader.readU16BE(address+12));
            assertEquals(0xFFFC,reader.readU16BE(address+14));
            assertFalse(0xFEBF>=start && 0xFEBF<start+4*(longsMinusOne+1),"native clear span contains P2 flags");
        }
    }

    @Test void spillLifetimeDeletionSharesTheDescendingFloorCadenceAndCarriedVisibility() {
        var world=world(); world.floorY=10000; world.session.setNativeRings(0,1);
        S3kCompetitionDamage.contact(world.session,world,world.players.getFirst(),0);
        var ring=ring(world,0); int gate=(ring.getSlotIndex()-109)&7;
        ring.updateNative(world.session,world,gate);
        for (int tick=0;tick<60 && ring.nativeYVelocity()<0;tick++) ring.updateNative(world.session,world,gate);
        assertTrue(ring.nativeYVelocity()>=0);
        world.floorY=ring.getY()+6; ring.refreshNativeVisibility(false);
        ring.updateNative(world.session,world,gate); assertTrue(ring.nativeYVelocity()>0);
        ring.refreshNativeVisibility(true); world.floorY=ring.getY()+6;
        ring.updateNative(world.session,world,gate); assertTrue(ring.nativeYVelocity()<0);
        world.floorY=10000;
        for (int tick=0;tick<60 && ring.nativeYVelocity()<0;tick++) ring.updateNative(world.session,world,gate);
        world.spillAnimation.restore(new int[]{0,0,0});
        ring.updateNative(world.session,world,gate+1); assertFalse(ring.isDestroyed());
        ring.updateNative(world.session,world,gate); assertTrue(ring.isDestroyed()); assertFalse(ring.nativeDrawPublished());
    }

    @Test void damageBanksAndRingSstRecreationReplayBothPlayersWithSharedAnimation() {
        var world=world(); world.session.setNativeRings(0,3); world.session.setNativeRings(1,2);
        for (var player:world.players) S3kCompetitionDamage.contact(world.session,world,player,player.centreX());
        step(world,1);
        var before=world.session.capture(); var spinBefore=world.spillAnimation.snapshot();
        var objectsBefore=world.objects.stream().map(AbstractObjectInstance::captureRewindState).toList();
        var spawns=world.objects.stream().map(AbstractObjectInstance::getSpawn).toList();
        step(world,17); var forward=world.objects.stream().map(AbstractObjectInstance::captureRewindState).toList();
        var sessionForward=world.session.capture(); var spinForward=world.spillAnimation.snapshot();
        world.session.restore(before); world.spillAnimation.restore(spinBefore); world.objects.clear();
        for (int i=0;i<objectsBefore.size();i++) {
            var state=objectsBefore.get(i);
            var restored=(S3kCompetitionRingLoss)ObjectRewindDynamicCodecs.genericRecreate(new DynamicObjectEntry(
                    S3kCompetitionRingLoss.class.getName(),spawns.get(i),state.slotIndex(),state),
                    new DynamicObjectRecreateContext(mock(ObjectManager.class)));
            assertNotNull(restored); assertNotNull(state.compactGenericState());
            restored.restoreRewindState(state); world.objects.add(restored);
        }
        step(world,17);
        assertEquals(forward,world.objects.stream().map(AbstractObjectInstance::captureRewindState).toList());
        assertEquals(sessionForward,world.session.capture()); assertArrayEquals(spinForward,world.spillAnimation.snapshot());
    }

    private CompetitionTestWorld world() {
        var world=new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.animationSets=Map.of(S3kCompetitionRingLoss.ART_KEY,S3kSpriteDataLoader.loadAnimationSet(reader,0x1A992,1));
        return world;
    }
    private static S3kCompetitionRingLoss ring(CompetitionTestWorld world,int index) {
        return assertInstanceOf(S3kCompetitionRingLoss.class,world.objects.get(index));
    }
    private static void forwardRingFrames(CompetitionTestWorld world,int ticks) {
        for (int tick=0;tick<ticks;tick++) {
            world.session.beforeFrame(tick);
            for (var player:world.players) world.session.giveRing(player.slot());
            world.session.afterFrame();
        }
    }
    private static void step(CompetitionTestWorld world,int ticks) {
        for (int tick=0;tick<ticks;tick++) {
            for (int slot=4;slot<=93;slot++) for (var object:List.copyOf(world.objects))
                if (object.getSlotIndex()==slot && object instanceof S3kCompetitionRingLoss ring)
                    ring.updateNative(world.session,world,tick);
            world.spillAnimation.tick();
        }
    }
}
