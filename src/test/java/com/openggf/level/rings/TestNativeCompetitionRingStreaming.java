package com.openggf.level.rings;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.LevelState;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.Sonic2CompetitionRingStreaming;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCompetitionRingStreaming {
    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    private static final class Fixture {
        final Camera[] cameras = {new Camera(SonicConfigurationService.getInstance()),
                new Camera(SonicConfigurationService.getInstance())};
        final AbstractPlayableSprite[] players = {mock(AbstractPlayableSprite.class), mock(AbstractPlayableSprite.class)};
        final AtomicInteger[] xs = {new AtomicInteger(100), new AtomicInteger(100)};
        final AtomicInteger[] rings = {new AtomicInteger(), new AtomicInteger()};
        final NativeCompetitionRoster roster = mock(NativeCompetitionRoster.class);
        final AudioManager audio = mock(AudioManager.class);
        final RingManager manager;
        Fixture(List<RingSpawn> spawns) {
            var rules = new Sonic2GameModule().getRules();
            for (int slot = 0; slot < 2; slot++) {
                final int s = slot;
                when(roster.camera(slot)).thenReturn(cameras[slot]);
                when(roster.player(slot)).thenReturn(players[slot]);
                when(roster.slotOf(players[slot])).thenReturn(slot);
                when(players[slot].getCentreX()).thenAnswer(call -> (short) xs[s].get());
                when(players[slot].getCentreY()).thenReturn((short) 256);
                when(players[slot].getYRadius()).thenReturn((short) 19);
                when(players[slot].getGameRules()).thenReturn(rules);
                LevelState state = mock(LevelState.class);
                when(roster.levelState(slot)).thenReturn(state);
                when(state.getRings()).thenAnswer(call -> rings[s].get());
                doAnswer(call -> { rings[s].addAndGet(call.getArgument(0)); return null; }).when(state).addRings(anyInt());
            }
            GameModule module = mock(GameModule.class);
            WorldSessionPolicyState policies = mock(WorldSessionPolicyState.class);
            when(policies.getService(NativeCompetitionRoster.class)).thenReturn(roster);
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policies);
            WorldSession world = new WorldSession(module);
            LevelManager level = mock(LevelManager.class);
            ObjectManager objects = mock(ObjectManager.class);
            when(level.getObjectManager()).thenReturn(objects);
            when(objects.getObjectServices()).thenReturn(new StubObjectServices() {
                @Override public WorldSession worldSession() { return world; }
            });
            manager = new RingManager(spawns, null, level, null, audio);
            RingManagerInternalAccess.enableNativeCompetitionStreaming(manager, new Sonic2CompetitionRingStreaming());
            manager.reset(0);
        }
        void at(int firstX, int secondX) {
            cameras[0].setX((short) firstX); cameras[1].setX((short) secondX);
            manager.update(firstX, players[0], 0, false);
        }
        List<Integer> active() { return manager.getActiveSpawns().stream().map(RingSpawn::x).toList(); }
    }

    private static List<RingSpawn> rings(int... xs) {
        List<RingSpawn> result = new ArrayList<>();
        for (int x : xs) result.add(new RingSpawn(x, 256));
        return result;
    }

    @Test void exactEdgesAndClampedLeftMatchNativeHalfOpenPointers() {
        Fixture f = new Fixture(rings(0, 1, 92, 92, 337, 427, 428, 428));
        f.at(100, 100);
        assertEquals(List.of(92, 92, 337, 427), f.active());
        for (int x : new int[]{0, 7, 8, 9}) {
            f.at(x, x);
            assertEquals(List.of(1, 92, 92), f.active(), "native low-X clamp at " + x);
        }
    }

    @Test void initSeedsBothPairsFromP1ThenMainSeparatesWithoutFillingGap() {
        Fixture f = new Fixture(rings(100, 600, 1000));
        f.cameras[1].setX((short) 1000);
        f.manager.reset(0);
        assertEquals(List.of(100), f.active(), "RingsManager_Init mirrors P1 into P2");
        f.at(0, 1000);
        assertEquals(List.of(100, 1000), f.active());
        assertFalse(f.active().contains(600));
    }

    @Test void opposedMovementCrossingAndTeleportPreserveIndependentRanges() {
        Fixture f = new Fixture(rings(100, 200, 600, 900, 1000, 1200));
        for (int[] cameras : new int[][]{{0, 1000}, {100, 900}, {500, 500}, {900, 100}, {1000, 0}}) {
            f.at(cameras[0], cameras[1]);
            List<Integer> expected = new ArrayList<>();
            for (int slot = 0; slot < 2; slot++) {
                int left = Math.max(1, cameras[slot] - 8);
                for (int x : new int[]{100, 200, 600, 900, 1000, 1200}) {
                    if (x >= left && x < left + 336 && !expected.contains(x)) expected.add(x);
                }
            }
            assertEquals(expected, f.active());
        }
        RingManagerInternalAccess.swapNativeCompetitionStreamingSlots(f.manager);
        assertEquals(List.of(100, 200, 1000, 1200), f.active());
        f.at(0, 1000);
        assertEquals(List.of(100, 200, 1000, 1200), f.active());
    }

    @Test void sharedOverlapCollectsOnceInP1SlotAndP2ReceivesItsOwnPickup() {
        Fixture f = new Fixture(rings(100, 1000));
        f.at(0, 0);
        f.manager.update(0, f.players[0], 1, true);
        assertEquals(1, f.rings[0].get());
        assertEquals(0, f.rings[1].get());
        f.xs[1].set(1000);
        f.at(0, 1000);
        f.manager.collectStageRings(f.players[1], 2);
        f.manager.collectStageRings(f.players[1], 3);
        assertEquals(1, f.rings[0].get());
        assertEquals(1, f.rings[1].get());
        assertEquals(2, java.util.BitSet.valueOf(f.manager.captureRingStatusTable()).cardinality());
        verify(f.players[0], never()).addRings(anyInt());
        verify(f.players[1], never()).addRings(anyInt());
    }

    @Test void playerCannotBorrowOtherSlotsInterestWindow() {
        Fixture f = new Fixture(rings(1000));
        f.at(0, 1000);
        f.xs[0].set(1000); f.xs[1].set(1000);
        f.manager.collectStageRings(f.players[0], 1);
        assertEquals(0, f.rings[0].get());
        f.manager.collectStageRings(f.players[1], 1);
        assertEquals(1, f.rings[1].get());
    }

    @Test void deadOrInvulnerableP1DoesNotPreventEligibleP2Pickup() {
        Fixture f = new Fixture(rings(100));
        when(f.players[0].getInvulnerableFrames()).thenReturn(90);
        f.manager.update(0, f.players[0], 1, true);
        assertEquals(0, f.rings[0].get());
        assertEquals(1, f.rings[1].get());
    }

    @Test void cursorSnapshotIsIndependentOfSharedStatusAndSupportsForwardReplay() {
        Fixture f = new Fixture(rings(100, 600, 1000, 1300));
        f.at(0, 1000);
        var shared = f.manager.capture();
        var adapter = RingManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(f.manager);
        Object cursors = adapter.capture();
        f.at(500, 1200);
        f.xs[1].set(1300);
        f.manager.collectStageRings(f.players[1], 1);
        var expectedActive = f.active();
        var expectedCollected = f.manager.captureRingStatusTable();
        f.manager.restore(shared); adapter.restore(cursors);
        f.at(500, 1200);
        f.manager.collectStageRings(f.players[1], 1);
        assertEquals(expectedActive, f.active());
        assertArrayEquals(expectedCollected, f.manager.captureRingStatusTable());
        RingManagerInternalAccess.swapNativeCompetitionStreamingSlots(f.manager);
        assertArrayEquals(expectedCollected, f.manager.captureRingStatusTable(), "Teleport never exchanges ring statuses");
        adapter.restore(cursors);
        assertEquals(List.of(100, 1000, 1300), f.active());
        assertArrayEquals(expectedCollected, f.manager.captureRingStatusTable(), "Cursor adapter does not overwrite collection");
    }

    @Test void ordinaryManagerRejectsNativeEnableWithoutExplicitRoster() {
        RingManager manager = new RingManager(rings(100), null, null, null, null);
        assertThrows(IllegalStateException.class, () -> RingManagerInternalAccess.enableNativeCompetitionStreaming(
                manager, new Sonic2CompetitionRingStreaming()));
        manager.reset(0);
        assertEquals(List.of(100), manager.getActiveSpawns().stream().map(RingSpawn::x).toList());
    }
}
