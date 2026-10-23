package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionSession {
    @Test void timeAttackWritesItsRecordAtTheFinishBoundaryBeforeTheResultWait() {
        var world = new CompetitionTestWorld(S3kCompetitionSession.Mode.TIME_ATTACK);
        world.session.signalGo(); world.cross(0, true);
        for (int lap = 0; lap < 5; lap++) world.completeLap(0, 60);
        assertTrue(world.savedTimeAttackRecords.isEmpty());
        world.step(1);
        assertEquals(1, world.savedTimeAttackRecords.size());
        assertEquals(500, world.savedTimeAttackRecords.getFirst().totalCentiseconds());
        assertTrue(world.results.isEmpty(), "native120tickresultwait has not elapsed");
        world.step(20); assertEquals(1, world.savedTimeAttackRecords.size());
    }
    @Test void nativeModesAndGrandPrixOrderAreNotOrdinalDerived() {
        assertEquals(0, S3kCompetitionSession.Mode.GRAND_PRIX.nativeValue());
        assertEquals(3, S3kCompetitionSession.Mode.MATCH_RACE.nativeValue());
        assertEquals(-1, S3kCompetitionSession.Mode.TIME_ATTACK.nativeValue());
        assertEquals(S3kCompetitionSession.Mode.TIME_ATTACK, S3kCompetitionSession.Mode.fromNative(0xFF));
        assertEquals(List.of(0xE,0xF,0x11,0x10,0x12), S3kCompetitionSession.GRAND_PRIX_ORDER.stream()
                .map(S3kCompetitionSession.Arena::zoneId).toList());
    }

    @Test void nativeSideLatchChangesBeforeGeometryAndLayerChecks() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.signalGo();
        world.players.get(0).setCentre(1001, 220); // Bottom is exclusive.
        world.session.observeGoal(1000,200,8);
        world.players.get(0).setCentre(1001,200);
        world.session.observeGoal(1000,200,8);
        assertEquals(0, world.session.lap(0), "moving vertically into the gate does not create a second crossing");
        world.cross(0,false); // Saturating reverse crossing.
        assertEquals(0, world.session.lap(0));
        world.cross(0,true);
        assertEquals(1, world.session.lap(0));
        assertEquals(0, world.session.lap(1), "P2 has a separate side latch and lap byte");
    }

    @Test void falseStartRestoresBothSlotsAfterExactly120TicksAndRetainsShoes() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.applyImmediateItem(1,0);
        world.players.get(1).setCentre(1100,210);
        world.cross(0,true);
        assertTrue(world.session.controlsLocked());
        assertEquals(0, world.session.falseStarter());
        world.step(119);
        assertTrue(world.session.controlsLocked());
        assertEquals(1001, world.players.get(0).centreX());
        world.step(1);
        assertFalse(world.session.controlsLocked());
        assertEquals(984, world.players.get(0).centreX());
        assertEquals(980, world.players.get(1).centreX());
        assertEquals(0xC00, world.players.get(1).speeds().maxSpeed());
        assertEquals(0, world.session.lap(0));
        assertFalse(world.session.started());
        assertEquals(0, world.session.timeCentiseconds(1));
    }

    @Test void goalRequiresNativeLayerAndStrict32PixelHorizontalLimit() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.signalGo();
        world.players.get(0).setTopSolidBit(0xE);
        world.cross(0,true);
        assertEquals(0,world.session.lap(0));
        world.players.get(0).setTopSolidBit(0xC);
        world.session.observeGoal(1000,200,8);
        assertEquals(0,world.session.lap(0),"changing plane without crossing does not bypass the side latch");
        world.cross(0,false);
        world.players.get(0).setCentre(1032,200);
        world.session.observeGoal(1000,200,8);
        assertEquals(0,world.session.lap(0),"abs(dx)==32 is excluded");
        world.cross(0,false); world.cross(0,true);
        assertEquals(1,world.session.lap(0));
    }

    @Test void fiveLapsFreezeEachClockAndMatchWaitsForBothPlayers() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        world.session.signalGo(); world.cross(0,true); world.cross(1,true);
        for (int lap = 0; lap < 5; lap++) world.completeLap(0,60);
        world.session.afterFrame();
        assertTrue(world.session.finished(0));
        assertFalse(world.session.finished(1));
        assertEquals(500, world.session.timeCentiseconds(0));
        world.step(200);
        assertTrue(world.results.isEmpty(), "Match Race must wait for P2's finish, not a first-finisher timeout");
        for (int lap = 0; lap < 5; lap++) world.completeLap(1,60);
        world.session.afterFrame();
        world.step(119);
        assertTrue(world.results.isEmpty());
        world.step(1);
        assertEquals(1, world.results.size());
        assertEquals(0, world.results.getFirst().winner());
        assertEquals(List.of(100,100,100,100,100), world.results.getFirst().players().getFirst().lapCentiseconds());
    }

    @Test void backwardCrossingAndRespawnSaturateAndDoNotReplenishOldHighestLap() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        CompetitionItemObjectInstance wheel = new CompetitionItemObjectInstance(new ObjectSpawn(0x500,200,0x64,0,0,false,200));
        wheel.setSlotIndex(10); world.objects.add(wheel);
        world.session.signalGo(); world.cross(0,true); world.completeLap(0,60);
        assertEquals(0,wheel.effect());
        world.cross(0,false);
        assertEquals(1,world.session.lap(0));
        wheel.collectNative(world.session,0);
        assertEquals(7,wheel.effect());
        world.cross(0,true);
        assertEquals(7,wheel.effect(), "crossing the same highest lap does not refill twice");
        world.session.notifyRespawn(0); world.session.observeGoal(1000,200,8);
        assertEquals(1,world.session.lap(0));
        world.cross(0,false); world.session.notifyRespawn(0); world.session.observeGoal(1000,200,8);
        assertEquals(0,world.session.lap(0));
    }

    @Test void replenishmentUsesRomSequenceAndNativeSlotOrderAcrossBothPlayers() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        CompetitionItemObjectInstance late = wheel(12), early = wheel(5), middle = wheel(9);
        world.objects.addAll(List.of(late,early,middle));
        world.session.signalGo(); world.cross(0,true); world.cross(1,true);
        world.completeLap(0,1);
        assertEquals(List.of(0,1,2),List.of(early.effect(),middle.effect(),late.effect()));
        world.completeLap(1,1);
        assertEquals(List.of(2,3,4),List.of(early.effect(),middle.effect(),late.effect()));
    }

    @Test void worldSnapshotRestoresBothParticipantsAndDeterministicForwardOutcome() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.GRAND_PRIX);
        world.session.signalGo(); world.cross(0,true); world.cross(1,true);
        world.session.applyImmediateItem(0,0); world.session.applyImmediateItem(1,1);
        world.session.setTrigger(3,0x102);
        S3kCompetitionSession.State before = world.session.capture();
        advanceBoth(world);
        S3kCompetitionSession.State expected = world.session.capture();
        world.session.restore(before);
        assertEquals(before,world.session.capture());
        advanceBoth(world);
        assertEquals(expected,world.session.capture());
        CompetitionTestWorld isolated = new CompetitionTestWorld(S3kCompetitionSession.Mode.GRAND_PRIX);
        assertEquals(0,isolated.session.trigger(3));
        assertFalse(isolated.session.started());
    }

    @Test void grandPrixAdvancesAllFiveArenasAndChoosesArenaWinsOverTotalTime() {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.GRAND_PRIX);
        for (S3kCompetitionSession.Arena arena : S3kCompetitionSession.GRAND_PRIX_ORDER) {
            world.players.get(0).setCentre(984,200); world.players.get(1).setCentre(980,200);
            world.session.enterArena(arena); world.session.signalGo();
            world.cross(0,true); world.cross(1,true);
            for (int lap=0;lap<5;lap++) { world.completeLap(0,1); world.completeLap(1,1); }
            world.session.afterFrame(); world.step(122);
        }
        assertEquals(S3kCompetitionSession.GRAND_PRIX_ORDER.subList(1,5),world.arenaRequests);
        assertEquals(1,world.results.size());
        var players=world.results.getFirst().players();
        var result=new S3kCompetitionSession.Result(S3kCompetitionSession.Mode.GRAND_PRIX,
                S3kCompetitionSession.Arena.ENDLESS_MINE,players,
                List.of(List.of(100,100,100,59000,59000),List.of(101,101,101,100,100)));
        assertTrue(result.grandPrixTotalCentiseconds(0)>result.grandPrixTotalCentiseconds(1));
        assertEquals(3,result.arenaWins(0)); assertEquals(0,result.winner());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void runtimeSequenceAndAnimationPointersReadSuppliedRom() throws IOException {
        CompetitionTestWorld world = new CompetitionTestWorld(S3kCompetitionSession.Mode.TIME_ATTACK);
        Rom rom = GameServices.rom().getRom();
        S3kCompetitionSession session = new S3kCompetitionSession(rom,
                world.session.config(),world);
        assertArrayEquals(CompetitionTestWorld.SEQUENCE,rom.readBytes(0x36FDA,24));
        assertEquals(0x18,session.itemAnimationByte(1));
        assertEquals(0xFC,session.itemAnimationByte(56));
        assertEquals(2,session.goalAnimationByte(1));
        assertEquals(0xFF,session.goalAnimationByte(7));
    }

    private static CompetitionItemObjectInstance wheel(int slot) {
        var wheel = new CompetitionItemObjectInstance(new ObjectSpawn(0x500,200,0x64,0,0,false,200));
        wheel.setSlotIndex(slot); return wheel;
    }
    private static void advanceBoth(CompetitionTestWorld world) {
        world.completeLap(0,37); world.completeLap(1,19);
        world.session.applyImmediateItem(1,2); world.session.giveRing(0);
        world.session.setTrigger(3,world.session.trigger(3) | 4);
        world.step(64);
    }
}
