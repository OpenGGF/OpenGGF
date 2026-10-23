package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.Sonic2CompetitionObjectStreaming;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCompetitionObjectStreaming {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    private static final class Actor extends AbstractObjectInstance implements RewindRecreatable {
        int ticks;
        Actor(ObjectSpawn spawn) { super(spawn, "native-stream-test"); }
        @Override public void update(int frame, PlayableEntity player) { ticks++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Actor(context.spawn());
        }
    }

    private static final class Fixture {
        final Camera[] cameras = {new Camera(SonicConfigurationService.getInstance()),
                new Camera(SonicConfigurationService.getInstance())};
        final NativeCompetitionRoster roster = mock(NativeCompetitionRoster.class);
        final ObjectManager manager;
        Fixture(List<ObjectSpawn> spawns) {
            this(spawns, true);
        }
        Fixture(List<ObjectSpawn> spawns, boolean enableStreaming) {
            when(roster.camera(0)).thenReturn(cameras[0]);
            when(roster.camera(1)).thenReturn(cameras[1]);
            GameModule module = mock(GameModule.class);
            WorldSessionPolicyState policies = mock(WorldSessionPolicyState.class);
            when(policies.getService(NativeCompetitionRoster.class)).thenReturn(roster);
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policies);
            WorldSession world = new WorldSession(module);
            ObjectRegistry registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return new Actor(spawn); }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "native-test"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
            };
            manager = new ObjectManager(spawns, registry, -1, null, null, null, cameras[0],
                    new StubObjectServices() {
                        @Override public Camera camera() { return cameras[0]; }
                        @Override public WorldSession worldSession() { return world; }
                    });
            if (enableStreaming) ObjectManagerInternalAccess.enableNativeCompetitionStreaming(manager, new Sonic2CompetitionObjectStreaming());
            manager.reset(0);
        }
        void at(int firstBlock, int secondBlock) {
            atPixels(firstBlock << 8, secondBlock << 8);
        }
        void atPixels(int firstX, int secondX) {
            cameras[0].setX((short) firstX); cameras[1].setX((short) secondX);
            manager.update(firstX, null, List.of(), 0, false);
        }
        List<Integer> xs() {
            return manager.getActiveObjects().stream().map(ObjectInstance::getSpawn).map(ObjectSpawn::x).sorted().toList();
        }
        List<String> graph() {
            return manager.getActiveObjects().stream().map(o -> (Actor) o)
                    .sorted(Comparator.comparingInt(Actor::getSlotIndex))
                    .map(o -> o.getSlotIndex() + ":" + o.getSpawn().layoutIndex() + ":" + o.getRespawnStateIndex() + ":" + o.ticks)
                    .toList();
        }
    }

    @Test void plainAllocationStopsBeforePlacementBanksButAfterCurrentRetainsNativeRange() {
        var f = new Fixture(List.of());
        assertTrue(f.manager.hasFreeDynamicSlotForDeferredAllocation(39));
        assertFalse(f.manager.hasFreeDynamicSlotForDeferredAllocation(40));
        for (int slot = 16; slot < 56; slot++) assertEquals(slot, f.manager.allocateDynamicSlot());
        assertEquals(-1, f.manager.allocateDynamicSlot());
        assertEquals(-1, f.manager.firstFreeDynamicSlot());
        assertFalse(f.manager.hasFreeDynamicSlot());
        assertFalse(f.manager.hasFreeDynamicSlotForDeferredAllocation(0));
        assertEquals(56, f.manager.allocateSlotAfter(55), "The ROM's after-current scan is not shortened");
        assertTrue(f.manager.reserveDynamicSlot(100), "Explicit placement bank ownership remains legal");
        f.manager.releaseDynamicSlot(30);
        assertEquals(30, f.manager.allocateDynamicSlot());
        assertEquals(-1, f.manager.allocateDynamicSlot());
    }

    @Test void absentNativeStreamingPolicyKeepsTheEntireOrdinaryDynamicPool() {
        var f = new Fixture(List.of(), false);
        for (int slot = 16; slot < 128; slot++) assertEquals(slot, f.manager.allocateDynamicSlot());
        assertEquals(-1, f.manager.allocateDynamicSlot());
    }

    private static ObjectSpawn spawn(int x, int index, boolean tracked) {
        return new ObjectSpawn(x, 0x100, 1, 0, 0, tracked, tracked ? 0x8100 : 0x100, index);
    }
    private static List<ObjectSpawn> columns(int count) {
        List<ObjectSpawn> spawns = new ArrayList<>();
        for (int i = 0; i < count; i++) spawns.add(spawn(i * 0x100 + 0x20, i, true));
        return spawns;
    }

    @Test void initSharesColumnsButUsesNativeBanksAndOneLiveSet() {
        Fixture f = new Fixture(columns(8));
        assertEquals(56, ObjectManagerInternalAccess.nativeCompetitionGeneralAllocationSlotLimitExclusive(f.manager));
        assertEquals(List.of(0x20, 0x120, 0x220), f.xs());
        assertEquals(List.of(56, 68, 80), f.manager.getActiveObjects().stream()
                .map(o -> ((Actor) o).getSlotIndex()).sorted().toList());
        f.at(1, 0);
        assertEquals(List.of(0x20, 0x120, 0x220, 0x320), f.xs(), "P2 still owns outgoing column zero");
        f.at(1, 1);
        assertEquals(List.of(0x120, 0x220, 0x320), f.xs(), "Only the last interest release unloads it");
        assertEquals(3, f.manager.getActiveObjects().size(), "Overlapping cameras never duplicate objects");
    }

    @Test void bankAllocationPreservesRomRecordOrderAcrossTheOrdinary128PixelSubcolumns() {
        ObjectSpawn first = spawn(0x1e0, 0, true);
        ObjectSpawn second = spawn(0x120, 1, true);
        Fixture f = new Fixture(List.of(first, second));
        var actors = f.manager.getActiveObjects().stream().map(o -> (Actor) o)
                .sorted(Comparator.comparingInt(Actor::getSlotIndex)).toList();
        assertSame(first, actors.get(0).getSpawn());
        assertSame(second, actors.get(1).getSpawn());
        assertEquals(68, actors.get(0).getSlotIndex());
        assertEquals(69, actors.get(1).getSlotIndex());
        assertEquals(1, actors.get(0).getRespawnStateIndex());
        assertEquals(2, actors.get(1).getRespawnStateIndex());
    }

    @Test void separatedViewsDoNotLoadTheGapAndProcessP1BeforeP2() {
        Fixture f = new Fixture(columns(12));
        for (int b = 1; b <= 8; b++) f.at(0, b);
        assertEquals(List.of(0x20, 0x120, 0x220, 0x820, 0x920, 0xa20), f.xs());
        assertEquals(6, f.manager.getActiveObjects().size());
        // Simultaneous expansion from identical windows reserves the P1 bank first.
        Fixture order = new Fixture(columns(6));
        order.at(1, 1);
        Actor third = (Actor) order.manager.getActiveObjects().stream()
                .filter(o -> o.getSpawn().x() == 0x320).findFirst().orElseThrow();
        assertEquals(92, third.getSlotIndex());
        assertEquals(4, third.getRespawnStateIndex());
    }

    @Test void opposedMovementAndCrossingKeepIndependentCursors() {
        Fixture f = new Fixture(columns(12));
        for (int b = 1; b <= 7; b++) f.at(0, b);
        for (int b = 1; b <= 7; b++) {
            f.at(b, 7 - b);
            List<Integer> expected = new ArrayList<>();
            for (int c = 0; c < 12; c++) {
                if (c >= b && c <= b + 2 || c >= 7 - b && c <= 9 - b) expected.add(c * 0x100 + 0x20);
            }
            assertEquals(expected, f.xs(), "crossing step " + b);
        }
    }

    @Test void exact256PixelEdgeAndStationaryCameraDoNotRunAnotherPass() {
        Fixture f = new Fixture(List.of(spawn(0x2ff, 0, true), spawn(0x300, 1, true)));
        assertEquals(List.of(0x2ff), f.xs());
        f.atPixels(0xff, 0xff);
        assertEquals(List.of(0x2ff), f.xs());
        f.atPixels(0x100, 0x100);
        assertEquals(List.of(0x2ff, 0x300), f.xs());
        assertEquals(2, f.manager.getActiveObjects().size());
        f.manager.postCameraPlacementUpdate(0x500);
        assertEquals(2, f.manager.getActiveObjects().size(), "No extra ordinary-camera gap pass");
    }

    @Test void fullBankLatchesBeforeAllocationAndOnlyLiveUnloadsClearTheirBits() {
        List<ObjectSpawn> spawns = new ArrayList<>();
        for (int i = 0; i < 13; i++) spawns.add(spawn(0x20 + i, i, true));
        Fixture f = new Fixture(spawns);
        assertEquals(12, f.manager.getActiveObjects().size());
        assertEquals(0x80, f.manager.rewindSnapshottable().capture().placement().objState()[13] & 0xff,
                "The failed thirteenth entry keeps ChkLoadObj_2P's bset");
        for (int b = 1; b <= 3; b++) f.at(b, b);
        assertEquals(0, f.manager.getActiveObjects().size());
        byte[] respawn = f.manager.rewindSnapshottable().capture().placement().objState();
        for (int i = 1; i <= 12; i++) assertEquals(0, respawn[i] & 0x80);
        assertEquals(0x80, respawn[13] & 0x80, "No live slot exists to clear the failed allocation latch");
        for (int b = 2; b >= 0; b--) f.at(b, b);
        assertEquals(12, f.manager.getActiveObjects().size());
        assertFalse(f.xs().contains(0x2c), "Tracked failed entry remains skipped on return");
    }

    @Test void fullGeneralRamParksUntrackedEntryUntilARealReentry() {
        ObjectSpawn general = new ObjectSpawn(0x300, 0x100, 1, 0, 0, false, 0x1100, 0);
        Fixture f = new Fixture(List.of(general));
        List<Actor> fillers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Actor actor = new Actor(spawn(0x100, 100 + i, false));
            f.manager.addDynamicObject(actor);
            fillers.add(actor);
        }
        f.at(1, 1);
        assertFalse(f.xs().contains(0x300));
        f.manager.removeDynamicObject(fillers.get(0));
        f.at(1, 1);
        assertFalse(f.xs().contains(0x300), "A newly free slot does not trigger a stationary retry");
        f.at(0, 0);
        f.at(1, 1);
        assertTrue(f.xs().contains(0x300), "Untracked entry retries when the cursor re-enters the block");
    }

    @Test void backwardFullBankRetainsLatchAndDoesNotRetryWhenOneSlotBecomesFree() {
        Fixture f = new Fixture(columns(5));
        f.at(1, 1); // Retire block zero and advance both back pointers/counters.
        List<Actor> fillers = new ArrayList<>();
        for (int slot = 56; slot < 68; slot++) {
            Actor actor = new Actor(spawn(0x100, 100 + slot, false));
            actor.setSlotIndex(slot);
            f.manager.addDynamicObject(actor);
            fillers.add(actor);
        }
        f.at(0, 1); // P1 tries loading block zero backward into that occupied bank.
        assertFalse(f.xs().contains(0x20));
        assertEquals(0x80, f.manager.rewindSnapshottable().capture().placement().objState()[1] & 0x80);
        f.manager.removeDynamicObject(fillers.getFirst());
        f.at(0, 1);
        assertFalse(f.xs().contains(0x20), "The rolled-back pointer does not authorize a stationary retry");
        f.at(0, 0);
        assertFalse(f.xs().contains(0x20), "P2's already-loaded-block scan skips the latched entry");
    }

    @Test void backwardUntrackedFullBankLoadsInReverseOrderWithoutPersistentLatch() {
        List<ObjectSpawn> spawns = new ArrayList<>();
        for (int i = 0; i < 13; i++) spawns.add(spawn(0x20 + i, i, false));
        Fixture f = new Fixture(spawns);
        assertEquals(12, f.manager.getActiveObjects().size());
        assertFalse(f.xs().contains(0x2c));
        for (int b = 1; b <= 3; b++) f.at(b, b);
        for (int b = 2; b >= 0; b--) f.at(b, b);
        assertEquals(12, f.manager.getActiveObjects().size());
        assertTrue(f.xs().contains(0x2c));
        assertFalse(f.xs().contains(0x20), "Reverse scan exhausts the bank at the other end of the column");
        Actor lastRecord = (Actor) f.manager.getActiveObjects().stream()
                .filter(o -> o.getSpawn().x() == 0x2c).findFirst().orElseThrow();
        assertEquals(56, lastRecord.getSlotIndex());
    }

    @Test void emptyBlockSentinelParticipatesInNativeByteComparisons() {
        Fixture f = new Fixture(List.of(spawn(0xff20, 0, true)));
        f.atPixels(0xfd00, 0xfd00);
        assertTrue(f.xs().isEmpty(), "Requested column FF compares equal to the native empty FF bank byte");
    }

    @Test void bit12PlacementUsesGeneralPoolAndSurvivesBankRetirement() {
        ObjectSpawn general = new ObjectSpawn(0x20, 0x100, 1, 0, 0, true, 0x9100, 0);
        Fixture f = new Fixture(List.of(general));
        assertEquals(16, ((Actor) f.manager.getActiveObjects().iterator().next()).getSlotIndex());
        for (int b = 1; b <= 5; b++) f.at(b, b);
        assertEquals(List.of(0x20), f.xs(), "Native MarkObjGone is bypassed; only bank slots are retired");
    }

    @Test void untrackedGeneralPlacementCanHaveMultipleNativeOwnersAcrossReentryAndRewind() {
        ObjectSpawn general = new ObjectSpawn(0x20, 0x100, 1, 0, 0, false, 0x1100, 0);
        Fixture f = new Fixture(List.of(general));
        for (int b = 1; b <= 3; b++) f.at(b, b);
        for (int b = 2; b >= 0; b--) f.at(b, b);
        assertEquals(List.of(0x20, 0x20), f.xs(), "No respawn latch suppresses the second general-pool owner");
        var objects = f.manager.rewindSnapshottable();
        var cursors = ObjectManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(f.manager);
        var snapshot = objects.capture();
        Object cursorSnapshot = cursors.capture();
        for (int b = 1; b <= 3; b++) f.at(b, b);
        for (int b = 2; b >= 0; b--) f.at(b, b);
        List<String> expected = f.graph();
        assertEquals(3, expected.size());
        objects.restore(snapshot); cursors.restore(cursorSnapshot);
        for (int b = 1; b <= 3; b++) f.at(b, b);
        for (int b = 2; b >= 0; b--) f.at(b, b);
        assertEquals(expected, f.graph(), "Both independently live SST owners restore before the next reentry");
    }

    @Test void independentSnapshotsAndTeleportSwapReplayTheSameNativeGraph() {
        Fixture f = new Fixture(columns(14));
        for (int b = 1; b <= 7; b++) f.at(0, b);
        var objects = f.manager.rewindSnapshottable();
        var cursors = ObjectManagerInternalAccess.nativeCompetitionStreamingRewindAdapter(f.manager);
        var objectSnapshot = objects.capture();
        Object cursorSnapshot = cursors.capture();
        assertTrue(RewindSnapshotDiff.keyEquals(cursors.key(), cursorSnapshot, cursors.capture()),
                "Private adapter payloads must compare by content in the real rewind comparator");
        f.at(1, 6); f.at(2, 5); f.at(3, 4);
        List<String> expected = f.graph();
        Object expectedCursors = cursors.capture();
        assertFalse(RewindSnapshotDiff.keyEquals(cursors.key(), cursorSnapshot, expectedCursors));
        objects.restore(objectSnapshot); cursors.restore(cursorSnapshot);
        f.at(1, 6); f.at(2, 5); f.at(3, 4);
        assertEquals(expected, f.graph());
        assertTrue(RewindSnapshotDiff.keyEquals(cursors.key(), expectedCursors, cursors.capture()));
        // Restore again, swap manager state together with the external camera swap.
        objects.restore(objectSnapshot); cursors.restore(cursorSnapshot);
        ObjectManagerInternalAccess.swapNativeCompetitionStreamingSlots(f.manager);
        f.at(7, 0);
        assertEquals(List.of(0x20, 0x120, 0x220, 0x720, 0x820, 0x920), f.xs());
        f.at(6, 1);
        assertEquals(List.of(0x120, 0x220, 0x320, 0x620, 0x720, 0x820), f.xs());
        // Reusing the immutable checkpoint after later swaps must remain safe.
        objects.restore(objectSnapshot); cursors.restore(cursorSnapshot);
        f.at(1, 6); f.at(2, 5); f.at(3, 4);
        assertEquals(expected, f.graph());
    }

    @Test void absentExplicitRosterCannotEnableNativeStreaming() {
        ObjectManager manager = new ObjectManager(List.of(), null, -1, null, null,
                null, null, new StubObjectServices());
        assertEquals(-1, ObjectManagerInternalAccess.nativeCompetitionGeneralAllocationSlotLimitExclusive(manager));
        assertThrows(IllegalStateException.class, () -> ObjectManagerInternalAccess.enableNativeCompetitionStreaming(
                manager, new Sonic2CompetitionObjectStreaming()));
    }
}
