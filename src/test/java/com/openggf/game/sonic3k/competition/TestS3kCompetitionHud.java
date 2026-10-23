package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode;
import com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance.Kind;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DynamicObjectRecreateContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Native SST order and literal expectations from Obj_2PGoalMarker / loc_3716C..loc_37D96. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionHud {
    private Rom rom;
    private SpriteAnimationSet neonScripts;

    @BeforeEach
    void readActualRomScripts() throws Exception {
        rom = RomManager.getInstance().getRom();
        var reader = RomByteReader.fromRom(rom);
        assertEquals(0x0010, reader.readU16BE(0x3750E));
        assertEquals(0x3B, reader.readU8(0x3751E));
        assertEquals(5, reader.readU8(0x37522));
        assertEquals(0x12, reader.readU8(0x3754E));
        neonScripts = S3kSpriteDataLoader.loadAnimationSet(reader, 0x3750E, 8);
    }

    @Test
    void goalAllocatesTheEightActualNativeRoutinesInExactSstOrder() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        goal(world).updateNative(world.session);
        assertEquals(List.of(Kind.ITEM_ICON, Kind.ITEM_ICON, Kind.LAP, Kind.LAP,
                Kind.NEON, Kind.TIMER, Kind.TIMER, Kind.RESERVED), hudKinds(world));
        assertEquals(List.of(0, 1, 0, 1, 0, 0, 1, 0), huds(world).stream()
                .map(CompetitionHudObjectInstance::playerSlot).toList());
        assertEquals(List.of(4, 5, 6, 7, 8, 9, 10, 11), huds(world).stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(3, world.session.capture().goalTimerMask());
        assertTrue(huds(world).stream().allMatch(h -> h.getPriorityBucket() == 0));
        assertTrue(huds(world).stream().filter(h -> h.kind() != Kind.RESERVED).allMatch(CompetitionHudObjectInstance::isHighPriority));
        assertFalse(hud(world, Kind.RESERVED, 0).isHighPriority(), "locret_3827A leaves fresh art_tile zero");
        assertEquals(5, goal(world).getPriorityBucket());
        assertFalse(goal(world).isHighPriority());
        step(world, 1);
        assertFalse(hud(world, Kind.RESERVED, 0).nativeDrawPublished());
        assertFalse(hud(world, Kind.RESERVED, 0).isDestroyed());
    }

    @Test
    void fullRamStopsOnEachAllocationFailureAndNeverReconstructsMissingTimersLater() throws Exception {
        var expected = List.of(Kind.ITEM_ICON, Kind.ITEM_ICON, Kind.LAP, Kind.LAP,
                Kind.NEON, Kind.TIMER, Kind.TIMER, Kind.RESERVED);
        for (int available = 0; available <= 8; available++) {
            var world = world(Mode.MATCH_RACE, available);
            step(world, 1);
            assertEquals(expected.subList(0, available), hudKinds(world), "free SST slots=" + available);
            assertEquals(available >= 7 ? 3 : available == 6 ? 1 : 0,
                    world.session.capture().goalTimerMask());
            // A newly free later slot cannot cause goal initialization to run again.
            world.occupied[50] = false;
            step(world, 300);
            assertEquals(available + 1, world.objects.size());
            assertEquals(available >= 5, world.session.started());
            assertEquals(available >= 6 ? 100 : 0, world.session.timeCentiseconds(0));
            assertEquals(available >= 7 ? 100 : 0, world.session.timeCentiseconds(1));
        }
    }

    @Test
    void trueRomNeonStartsClocksAtGoAndProducesExactlyThreeReadySounds() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        var soundFrames = new ArrayList<Integer>();
        for (int frame = 1; frame <= 301; frame++) {
            int before = world.sounds.size();
            step(world, 1);
            if (world.sounds.size() != before) soundFrames.add(frame);
            if (frame < 242) {
                assertFalse(world.session.started(), "pre-Go frame=" + frame);
                assertEquals(0, world.session.timeCentiseconds(0));
            }
            if (frame == 241) assertEquals(0xB, hud(world, Kind.NEON, 0).mappingFrame());
            if (frame == 242) {
                assertTrue(world.session.started());
                assertEquals(3, hud(world, Kind.NEON, 0).mappingFrame());
                assertEquals(1, hud(world, Kind.TIMER, 0).packedTime());
                assertEquals(1, hud(world, Kind.TIMER, 1).packedTime());
            }
        }
        assertEquals(List.of(62, 122, 182, 242), soundFrames);
        assertEquals(List.of(Sonic3kSfx.LAUNCH_READY, Sonic3kSfx.LAUNCH_READY,
                Sonic3kSfx.LAUNCH_READY, Sonic3kSfx.LAUNCH_GO), world.sounds);
        assertEquals(0x100, hud(world, Kind.TIMER, 0).packedTime());
        assertEquals(0x100, hud(world, Kind.TIMER, 1).packedTime());
    }

    @Test
    void falseStartLocksFor120GoalUpdatesHidesMainNeonAndResetsNativeWordsOnly() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 100);
        var p1 = world.players.getFirst();
        p1.setFractions(0x1234, 0x5678);
        p1.setStandingObject(0xCAFE);
        p1.setXVelocity(0x500); p1.setYVelocity(0x300); p1.setGroundVelocity(0x500);
        p1.setMoveLock(19);
        p1.setCentreNative(1001, 200);
        step(world, 1);
        assertEquals(120, world.session.falseStartTicks());
        assertEquals(0, world.session.falseStarter());
        var main = hud(world, Kind.NEON, 0);
        var error = hud(world, Kind.FALSE_START, 0);
        assertEquals(0xA, main.mappingFrame()); assertFalse(main.nativeDrawPublished());
        assertEquals(8, error.mappingFrame()); assertTrue(error.nativeDrawPublished());
        assertEquals(0, error.viewSlot());
        assertEquals(1, world.sounds.stream().filter(s -> s == Sonic3kSfx.ERROR).count());
        step(world, 119);
        assertTrue(world.session.controlsLocked()); assertEquals(1, world.session.falseStartTicks());
        assertFalse(main.nativeDrawPublished()); assertEquals(0, world.session.timeCentiseconds(0));
        step(world, 1);
        assertFalse(world.session.controlsLocked()); assertFalse(world.session.started());
        assertEquals(984, p1.centreX()); assertEquals(200, p1.centreY());
        assertEquals(0x1234, p1.xFraction()); assertEquals(0x5678, p1.yFraction());
        assertEquals(0xCAFE, p1.standingObject(), "native status reset keeps the raw interact word");
        assertEquals(2, p1.status()); assertEquals(0, p1.moveLock());
        assertEquals(0, p1.xVelocity()); assertEquals(0, p1.yVelocity()); assertEquals(0, p1.groundVelocity());
        assertEquals(0x12, main.mappingFrame()); assertTrue(main.nativeDrawPublished());
        assertTrue(error.isDestroyed());
        int readySounds = (int) world.sounds.stream().filter(s -> s == Sonic3kSfx.LAUNCH_READY).count();
        step(world, 60);
        assertEquals(readySounds, world.sounds.stream().filter(s -> s == Sonic3kSfx.LAUNCH_READY).count());
        step(world, 1);
        assertEquals(readySounds + 1, world.sounds.stream().filter(s -> s == Sonic3kSfx.LAUNCH_READY).count());
    }

    @Test
    void falseStartSoundAndErrorDisplayRequireAnActualFreeSstSlot() throws Exception {
        for (int slot = 0; slot < 2; slot++) {
            var world = world(Mode.MATCH_RACE, 8);
            step(world, 10);
            world.players.get(slot).setCentre(1001, 200);
            step(world, 1);
            assertTrue(world.session.controlsLocked());
            assertEquals(slot, hud(world, Kind.FALSE_START, slot).viewSlot());
            assertEquals(List.of(Sonic3kSfx.ERROR), world.sounds);
        }
        var full = world(Mode.MATCH_RACE, 8);
        step(full, 10);
        full.players.get(1).setCentre(1001, 200);
        Arrays.fill(full.occupied, true);
        step(full, 1);
        assertTrue(full.session.controlsLocked()); assertEquals(1, full.session.falseStarter());
        assertEquals(8, huds(full).size());
        assertFalse(hud(full, Kind.NEON, 0).nativeDrawPublished());
        assertTrue(full.sounds.isEmpty(), "loc_36EF2 is reached only after successful allocation");
    }

    @Test
    void itemLapAndTimerRecordsAddressBothNativeViewsAndTheActualShoePriority() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 242);
        for (int slot = 0; slot < 2; slot++) {
            assertEquals(slot, hud(world, Kind.ITEM_ICON, slot).viewSlot());
            assertEquals(slot, hud(world, Kind.LAP, slot).viewSlot());
            assertEquals(slot, hud(world, Kind.TIMER, slot).viewSlot());
            assertEquals(0, hud(world, Kind.ITEM_ICON, slot).mappingFrame());
        }
        world.session.giveRing(0); step(world, 1);
        assertEquals(3, hud(world, Kind.ITEM_ICON, 0).mappingFrame());
        world.session.applyImmediateItem(0, 0); // Native fast shoes outrank the ring icon.
        world.session.applyImmediateItem(1, 1);
        world.cross(0, true); world.cross(1, true);
        step(world, 1);
        assertEquals(1, hud(world, Kind.ITEM_ICON, 0).mappingFrame());
        assertEquals(2, hud(world, Kind.ITEM_ICON, 1).mappingFrame());
        assertEquals(1, hud(world, Kind.LAP, 0).mappingFrame());
        assertEquals(1, hud(world, Kind.LAP, 1).mappingFrame());
        for (int slot = 0; slot < 2; slot++) for (int lap = 0; lap < 5; lap++) world.completeLap(slot, 0);
        step(world, 1);
        assertEquals(5, hud(world, Kind.LAP, 0).mappingFrame());
        assertEquals(5, hud(world, Kind.LAP, 1).mappingFrame());
        assertTrue(world.session.finished(0)); assertTrue(world.session.finished(1));
        int time = hud(world, Kind.TIMER, 0).packedTime();
        step(world, 10);
        assertEquals(time, hud(world, Kind.TIMER, 0).packedTime());
    }

    @Test
    void timeAttackAllocatesGhostHudRecordsThenDeletesOnlyTheThreeP2Consumers() throws Exception {
        var world = world(Mode.TIME_ATTACK, 8);
        goal(world).updateNative(world.session);
        assertEquals(8, huds(world).size());
        step(world, 1);
        assertTrue(hud(world, Kind.ITEM_ICON, 1).isDestroyed());
        assertTrue(hud(world, Kind.LAP, 1).isDestroyed());
        assertTrue(hud(world, Kind.TIMER, 1).isDestroyed());
        assertFalse(hud(world, Kind.RESERVED, 0).isDestroyed());
        assertFalse(hud(world, Kind.NEON, 0).isDestroyed());
    }

    @Test
    void lateSecondFinisherUsesItsOwnLoserNeonWithoutReplacingTheWinnerView() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 242);
        world.cross(0, true);
        for (int lap = 0; lap < 5; lap++) world.completeLap(0, 0);
        step(world, 1);
        var winner = hud(world, Kind.NEON, 0);
        var loser = hud(world, Kind.LOSER_NEON, 1);
        assertTrue(world.session.finished(0)); assertFalse(world.session.finished(1));
        assertEquals(0, winner.viewSlot()); assertEquals(1, loser.viewSlot());
        assertEquals(0x12, loser.mappingFrame());
        step(world, 1);
        assertEquals(5, winner.mappingFrame());
        world.cross(1, true);
        for (int lap = 0; lap < 5; lap++) world.completeLap(1, 0);
        step(world, 1);
        assertTrue(world.session.finished(1));
        step(world, 1);
        assertEquals(6, loser.mappingFrame());
        assertEquals(0, winner.viewSlot()); assertEquals(1, loser.viewSlot());
        assertEquals(2, world.sounds.stream().filter(s -> s == Sonic3kSfx.GOAL).count());
        assertEquals(1, huds(world).stream().filter(h -> h.kind() == Kind.LOSER_NEON).count());
    }

    @Test
    void simultaneousInitialFinishUsesTheSharedTieDisplayAndOneNativeGoalSound() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 242);
        for (int slot = 0; slot < 2; slot++) {
            world.cross(slot, true);
            for (int lap = 0; lap < 5; lap++) world.completeLap(slot, 0);
        }
        step(world, 2);
        assertTrue(world.session.finished(0)); assertTrue(world.session.finished(1));
        var shared = hud(world, Kind.NEON, 0);
        assertEquals(-1, shared.viewSlot()); assertEquals(7, shared.mappingFrame());
        assertEquals(1, world.sounds.stream().filter(s -> s == Sonic3kSfx.GOAL).count());
        assertEquals(0, huds(world).stream().filter(h -> h.kind() == Kind.LOSER_NEON).count());
    }

    @Test
    void fullRamStillStopsTimerButCannotInventTheLateLoserDisplay() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 242);
        Arrays.fill(world.occupied, true);
        world.cross(0, true);
        for (int lap = 0; lap < 5; lap++) world.completeLap(0, 0);
        step(world, 1);
        assertTrue(world.session.finished(0));
        assertEquals(8, huds(world).size());
        assertEquals(0, hud(world, Kind.NEON, 0).viewSlot());
        assertEquals(0, huds(world).stream().filter(h -> h.kind() == Kind.LOSER_NEON).count());
        int stopped = world.session.timeCentiseconds(0);
        step(world, 20);
        assertEquals(stopped, world.session.timeCentiseconds(0));
        assertTrue(world.session.timeCentiseconds(1) > stopped);
    }

    @Test
    void compactGenericRecreationRestoresTheLockedDisplayAndForwardsThroughGoExactly() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 100);
        world.players.get(1).setCentre(1001, 200);
        step(world, 42);
        var locked = capture(world);
        assertEquals(79, world.session.falseStartTicks());
        assertTrue(locked.objects.stream().allMatch(e -> e.state().compactGenericState() != null));
        step(world, 330);
        var forward = capture(world);
        var sounds = List.copyOf(world.sounds);
        var originalNeon = hud(world, Kind.NEON, 0);
        restore(world, locked);
        assertNotSame(originalNeon, hud(world, Kind.NEON, 0));
        assertEquals(1, hud(world, Kind.FALSE_START, 1).playerSlot());
        assertFalse(hud(world, Kind.NEON, 0).nativeDrawPublished());
        step(world, 330);
        assertEquals(forward.session, world.session.capture());
        assertEquals(forward.objects.stream().map(DynamicObjectEntry::state).toList(),
                capture(world).objects.stream().map(DynamicObjectEntry::state).toList());
        assertEquals(sounds, world.sounds);
        assertTrue(world.session.started());
        assertEquals(world.session.packedTime(0), hud(world, Kind.TIMER, 0).packedTime());
        assertEquals(world.session.packedTime(1), hud(world, Kind.TIMER, 1).packedTime());
    }

    @Test
    void simultaneousFalseStartsProcessBothNativeSlotsEvenWhenOnlyOneDisplayFits() throws Exception {
        for (int free = 1; free <= 2; free++) {
            var world = world(Mode.MATCH_RACE, 8);
            step(world, 1);
            Arrays.fill(world.occupied, true);
            world.occupied[11] = false;
            if (free == 2) world.occupied[12] = false;
            world.players.forEach(player -> player.setCentre(1001, 200));
            step(world, 1);
            assertTrue(world.session.controlsLocked());
            assertEquals(120, world.session.falseStartTicks(), "same-pass P2 must not tick the shared hold");
            assertEquals(1, world.session.falseStarter(), "P2's call still executes after P1 rewrites the goal routine");
            assertEquals(0, world.session.lap(0)); assertEquals(0, world.session.lap(1));
            var errors = huds(world).stream().filter(h -> h.kind() == Kind.FALSE_START).toList();
            assertEquals(free, errors.size());
            assertEquals(free == 1 ? List.of(0) : List.of(0, 1),
                    errors.stream().map(CompetitionHudObjectInstance::viewSlot).toList());
            assertEquals(free == 1 ? List.of(Sonic3kSfx.ERROR) : List.of(Sonic3kSfx.ERROR, Sonic3kSfx.ERROR),
                    world.sounds);
            assertTrue(errors.stream().allMatch(h -> h.nativeDrawPublished() && h.mappingFrame() == 8));
            assertFalse(hud(world, Kind.NEON, 0).nativeDrawPublished());
            step(world, 119);
            assertEquals(1, world.session.falseStartTicks());
            step(world, 1);
            assertFalse(world.session.controlsLocked());
            assertTrue(errors.stream().allMatch(AbstractObjectInstance::isDestroyed));
            assertEquals(984, world.players.get(0).centreX()); assertEquals(980, world.players.get(1).centreX());
        }
    }

    @Test
    void maximumTimeStartsTheNativeWaitPhaseWithoutInventingAWinnerOrLoserNeon() throws Exception {
        var world = world(Mode.MATCH_RACE, 8);
        step(world, 242);
        var state = world.session.capture();
        var players = new ArrayList<>(state.players());
        var p1 = players.getFirst();
        // 35,999 elapsed 60-Hz frames = 9:59:98; next timer slot reaches the native cap.
        players.set(0, new S3kCompetitionSession.PlayerState(p1.body(), p1.lap(), p1.highestLap(),
                p1.rightOfGoal(), p1.respawnPending(), p1.timerRunning(), p1.finished(),
                35_999, p1.completedCentiseconds(), p1.lapTimes(), p1.rings()));
        world.session.restore(new S3kCompetitionSession.State(state.arena(), state.levelFrameCounter(),
                state.go(), state.falseStartTicks(), state.falseStartFrame(), state.falseStarter(),
                state.replenishmentOffset(), state.finishWait(), state.resultSent(), players, state.starts(),
                state.triggers(), state.grandPrixTimes(), state.goalTimerMask(), state.damage()));
        step(world, 1);
        assertTrue(world.session.finished(0)); assertFalse(world.session.finished(1));
        assertEquals(0x093B63, hud(world, Kind.TIMER, 0).packedTime());
        step(world, 1); // The earlier main neon now observes the previous timer pass's stopped flag.
        var atTimeout = capture(world);
        for (int frame = 0; frame < 70; frame++) {
            step(world, 1);
            int mapping = hud(world, Kind.NEON, 0).mappingFrame();
            assertFalse(List.of(4, 5, 6, 7, 0xD, 0xE, 0xF, 0x10).contains(mapping),
                    "time limit is not a lap finish: native neon frame=" + mapping);
        }
        assertEquals(0x12, hud(world, Kind.NEON, 0).mappingFrame());
        assertEquals(-1, hud(world, Kind.NEON, 0).viewSlot());
        assertEquals(8, huds(world).size());
        assertTrue(huds(world).stream().noneMatch(h -> h.kind() == Kind.LOSER_NEON));
        assertEquals(0, world.sounds.stream().filter(sound -> sound == Sonic3kSfx.GOAL).count());
        assertEquals(0x093B63, hud(world, Kind.TIMER, 0).packedTime());
        var forward = capture(world);
        restore(world, atTimeout); step(world, 70);
        assertEquals(forward.session, world.session.capture());
        assertEquals(forward.objects.stream().map(DynamicObjectEntry::state).toList(),
                capture(world).objects.stream().map(DynamicObjectEntry::state).toList());
    }

    @Test
    void hudDescriptorsExposeNativeScreenPositionsDimensionsAndOnlyTheTimerPiecesActuallyDrawn() throws Exception {
        var world=world(Mode.MATCH_RACE,8); step(world,301);
        for (int slot=0;slot<2;slot++) {
            var icon=hud(world,Kind.ITEM_ICON,slot);
            var lap=hud(world,Kind.LAP,slot);
            var timer=hud(world,Kind.TIMER,slot);
            assertEquals(0x120,icon.getX()); assertEquals(0xE0,icon.getY());
            assertEquals(0x10,icon.getOnScreenHalfWidth()); assertEquals(0x10,icon.getOnScreenHalfHeight());
            assertEquals(0x190,lap.getX()); assertEquals(0x98,lap.getY());
            assertEquals(8,lap.getOnScreenHalfWidth()); assertEquals(0xC,lap.getOnScreenHalfHeight());
            assertEquals(0xC8,timer.getX()); assertEquals(0x90,timer.getY());
            assertEquals(0x40,timer.getOnScreenHalfWidth()); assertEquals(0x10,timer.getOnScreenHalfHeight());
            assertEquals(List.of(new CompetitionHudObjectInstance.NativeDrawPiece("competition_item_icon",0,-1,0xA0,0x60)),
                    icon.nativeDrawPieces());
            assertEquals(List.of(new CompetitionHudObjectInstance.NativeDrawPiece("competition_lap",0,-1,0x110,0x18)),
                    lap.nativeDrawPieces());
            var pieces=timer.nativeDrawPieces();
            assertEquals(8,pieces.size());
            assertEquals(List.of(0,2,5,-1,-1,-1,-1,-1),pieces.stream()
                    .map(CompetitionHudObjectInstance.NativeDrawPiece::pieceIndex).toList());
            assertEquals(List.of(0x48,0x48,0x48,0x30,0x44,0x50,0x64,0x70),pieces.stream()
                    .map(CompetitionHudObjectInstance.NativeDrawPiece::screenX).toList());
            assertTrue(pieces.stream().allMatch(piece -> piece.screenY()==0x10));
            assertEquals(List.of(0,0,1,0,0),pieces.subList(3,8).stream()
                    .map(CompetitionHudObjectInstance.NativeDrawPiece::mappingFrame).toList());
            assertTrue(pieces.subList(3,8).stream().allMatch(piece -> piece.artKey().equals("competition_timer_digit")));
        }
        var neon=hud(world,Kind.NEON,0);
        assertEquals(0x80,neon.getOnScreenHalfWidth()); assertEquals(0x28,neon.getOnScreenHalfHeight());
        var state=capture(world); var expected=huds(world).stream().map(CompetitionHudObjectInstance::nativeDrawPieces).toList();
        step(world,8); restore(world,state);
        assertEquals(expected,huds(world).stream().map(CompetitionHudObjectInstance::nativeDrawPieces).toList());
        assertTrue(hud(world,Kind.RESERVED,0).nativeDrawPieces().isEmpty());
        assertTrue(huds(world).stream().noneMatch(AbstractObjectInstance::participatesInRomWorldTransitionOffset));
    }

    private CompetitionTestWorld world(Mode mode, int available) throws Exception {
        var world = new CompetitionTestWorld(mode);
        world.session = new S3kCompetitionSession(rom,
                new S3kCompetitionSession.Config(mode, Arena.AZURE_LAKE, true), world);
        world.animationSets = Map.of("competition_neon", neonScripts);
        Arrays.fill(world.occupied, true);
        for (int slot = 4; slot < (available == 8 ? 94 : 4 + available); slot++) world.occupied[slot] = false;
        var goal = new CompetitionGoalObjectInstance(new ObjectSpawn(1000, 200, 0x65, 8, 0, false, 0));
        goal.setSlotIndex(2); world.objects.add(goal);
        return world;
    }

    /** P1/P2 have already executed; new higher SST children execute in the same native pass. */
    private static void step(CompetitionTestWorld world, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            world.session.beforeFrame(world.session.levelFrameCounter() + 1);
            for (int i = 0; i < world.objects.size(); i++) {
                var object = world.objects.get(i);
                if (object.isDestroyed()) continue;
                if (object instanceof CompetitionGoalObjectInstance goal) goal.updateNative(world.session);
                if (object instanceof CompetitionHudObjectInstance hud) hud.updateNative(world.session);
            }
            world.session.afterFrame();
        }
    }

    private static CompetitionGoalObjectInstance goal(CompetitionTestWorld world) {
        return (CompetitionGoalObjectInstance) world.objects.getFirst();
    }
    private static List<CompetitionHudObjectInstance> huds(CompetitionTestWorld world) {
        return world.objects.stream().filter(CompetitionHudObjectInstance.class::isInstance)
                .map(CompetitionHudObjectInstance.class::cast).toList();
    }
    private static List<Kind> hudKinds(CompetitionTestWorld world) {
        return huds(world).stream().map(CompetitionHudObjectInstance::kind).toList();
    }
    private static CompetitionHudObjectInstance hud(CompetitionTestWorld world, Kind kind, int slot) {
        return huds(world).stream().filter(h -> h.kind() == kind && h.playerSlot() == slot)
                .findFirst().orElseThrow();
    }

    private record Snapshot(S3kCompetitionSession.State session, List<DynamicObjectEntry> objects,
                            boolean[] occupied, List<Sonic3kSfx> sounds) { }

    private static Snapshot capture(CompetitionTestWorld world) {
        var identities = identities(world.objects);
        var entries = new ArrayList<DynamicObjectEntry>();
        for (int i = 0; i < world.objects.size(); i++) {
            var object = world.objects.get(i);
            entries.add(new DynamicObjectEntry(object.getClass().getName(), object.getSpawn(),
                    object.getSlotIndex(), object.captureRewindState(identities)));
        }
        return new Snapshot(world.session.capture(), List.copyOf(entries), world.occupied.clone(),
                List.copyOf(world.sounds));
    }

    private static void restore(CompetitionTestWorld world, Snapshot state) {
        var recreate = new DynamicObjectRecreateContext(mock(ObjectManager.class));
        world.objects.clear();
        for (var entry : state.objects) {
            var object = (AbstractObjectInstance) ObjectRewindDynamicCodecs.genericRecreate(entry, recreate);
            assertNotNull(object, "Production genericRecreate must construct " + entry.className());
            object.setSlotIndex(entry.slotIndex()); world.objects.add(object);
        }
        var identities = identities(world.objects);
        for (int i = 0; i < world.objects.size(); i++)
            world.objects.get(i).restoreRewindState(state.objects.get(i).state(), identities);
        world.session.restore(state.session);
        System.arraycopy(state.occupied, 0, world.occupied, 0, world.occupied.length);
        world.sounds.clear(); world.sounds.addAll(state.sounds);
    }

    private static RewindCaptureContext identities(List<AbstractObjectInstance> objects) {
        var table = new RewindIdentityTable();
        for (int i = 0; i < objects.size(); i++) table.registerObject(objects.get(i),
                ObjectRefId.dynamic(objects.get(i).getSlotIndex(), 0, i));
        return RewindCaptureContext.withIdentityTable(table);
    }
}
