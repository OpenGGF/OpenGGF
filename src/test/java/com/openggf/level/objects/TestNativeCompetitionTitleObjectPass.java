package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.competition.Sonic2CompetitionObjectStreaming;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeCompetitionTitleObjectPass {
    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    private static class Probe extends AbstractObjectInstance implements RewindRecreatable, TouchResponseProvider {
        int ticks;
        boolean fail;
        Runnable firstDispatch;
        Probe(int id) { this(new ObjectSpawn(0x100, 0x100, id, 0, 0, false, 0x100)); }
        Probe(ObjectSpawn spawn) { super(spawn, "title-scan-test"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            ticks++;
            if (fail) throw new IllegalStateException("owned failure");
            if (firstDispatch != null) {
                var action = firstDispatch;
                firstDispatch = null;
                action.run();
            }
            NativeSpriteSubmissionAccess.submit(services().objectManager(), getSlotIndex(), 3);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public int getCollisionFlags() { return 0x40; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public boolean isPersistent() { return true; }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new Probe(context.spawn().objectId());
        }
    }

    private static final class Fixture {
        final Sonic sonic = new Sonic("sonic", (short) 0x100, (short) 0x100);
        final Tails tails = new Tails("tails", (short) 0x100, (short) 0x100);
        final Camera camera = new Camera();
        final NativeCompetitionRoster roster = mock(NativeCompetitionRoster.class);
        final ObjectManager manager;
        final Sonic2CompetitionObjectStreaming streaming = spy(new Sonic2CompetitionObjectStreaming());
        Fixture(boolean nativeRoster) {
            when(roster.player(0)).thenReturn(sonic);
            when(roster.player(1)).thenReturn(tails);
            when(roster.camera(0)).thenReturn(camera);
            when(roster.camera(1)).thenReturn(new Camera());
            var policy = mock(WorldSessionPolicyState.class);
            if (nativeRoster) when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
            var module = mock(GameModule.class);
            when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
            var world = new WorldSession(module);
            camera.setFocusedSprite(sonic);
            var holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public Camera camera() { return camera; }
                @Override public WorldSession worldSession() { return world; }
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public List<PlayableEntity> sidekicks() { return List.of(tails); }
            };
            var registry = new ObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) { return new Probe(spawn.objectId()); }
                @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
                @Override public String getPrimaryName(int id) { return "title-scan-test"; }
                @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
            };
            manager = new ObjectManager(List.of(), registry, -1, null, null, null, camera, services);
            holder[0] = manager;
            manager.enableExecThenLoadPlacement();
            if (nativeRoster) ObjectManagerInternalAccess.enableNativeCompetitionStreaming(manager, streaming);
            manager.reset(0);
            clearInvocations(streaming); // Initial ObjectsManager is distinct from the locked RunObjects walks.
            manager.initVblaCounter(0x1234);
        }
        Probe at(int slot, int id) {
            var actor = new Probe(id);
            manager.addDynamicObjectAtSlot(actor, slot);
            return actor;
        }
        void scan() {
            manager.snapshotTouchResponseState(true);
            ObjectManagerInternalAccess.runNativeCompetitionTitleObjectPass(manager);
        }
    }

    @Test void dispatchesFutureChildrenNowAndEarlierChildrenNextWithoutPlacementOrVblank() {
        var f = new Fixture(true);
        var parent = f.at(20, 1);
        var lower = new Probe(2);
        var upper = new Probe(3);
        parent.firstDispatch = () -> {
            f.manager.addDynamicObject(lower);
            f.manager.addDynamicObjectAtSlot(upper, 21);
        };
        f.scan();
        assertEquals(16, lower.getSlotIndex());
        assertEquals(0, lower.ticks, "a lower SST already passed waits for the next walk");
        assertEquals(1, upper.ticks, "a later SST is dispatched by this same native walk");
        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(20, 3),
                new NativeSpriteSubmissionAccess.Admission(21, 3)), NativeSpriteSubmissionAccess.admissions(f.manager));
        f.scan();
        assertEquals(1, lower.ticks);
        assertEquals(2, parent.ticks);
        assertEquals(2, upper.ticks);
        assertEquals(2, f.manager.getFrameCounter());
        assertEquals(0x1234, f.manager.getVblaCounter(), "only the actual WaitForVint advances this clock");
        verify(f.streaming, never()).update(anyInt(), anyInt(), any());
    }

    @Test void fullRamDoesNotConstructAnUnallocatedChildOrChangeTheWalk() {
        var f = new Fixture(true);
        var actors = new ArrayList<Probe>();
        for (int slot = 16; slot < 128; slot++) actors.add(f.at(slot, slot));
        var constructions = new AtomicInteger();
        actors.getFirst().firstDispatch = () -> assertNull(f.manager.createDynamicObjectWithReservedSlot(() -> {
            constructions.incrementAndGet();
            return new Probe(0xFF);
        }));
        f.scan();
        assertEquals(0, constructions.get());
        assertEquals(112, f.manager.getActiveObjectSlotCount());
        assertTrue(actors.stream().allMatch(actor -> actor.ticks == 1));
        assertEquals(1, f.manager.getFrameCounter());
        assertEquals(0x1234, f.manager.getVblaCounter());
    }

    @Test void failureUnwindsExecutionAndNextScanStillUsesTheCanonicalOwners() {
        var f = new Fixture(true);
        var first = f.at(16, 1);
        var later = f.at(17, 2);
        first.fail = true;
        assertThrows(IllegalStateException.class, f::scan);
        assertEquals(0, later.ticks);
        first.fail = false;
        first.firstDispatch = () -> assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.runNativeCompetitionTitleObjectPass(f.manager));
        f.scan();
        assertEquals(1, later.ticks);
        assertEquals(2, f.manager.getFrameCounter());
        assertEquals(0x1234, f.manager.getVblaCounter());
    }

    @Test void destructiveRestoreRecreatesObjectsAndTheirNativeQueueBeforeForwardScan() {
        var f = new Fixture(true);
        f.at(16, 1);
        f.manager.setRewindInPlaceRestoreEnabledForTest(false);
        var snapshots = new RewindRegistry();
        snapshots.register(f.manager.rewindSnapshottable());
        NativeSpriteSubmissionAccess.registerRewind(f.manager, snapshots);
        f.scan();
        var before = snapshots.capture();
        var retired = f.manager.getActiveObjects().iterator().next();
        f.scan();
        var expected = snapshots.capture();
        snapshots.restore(before);
        assertNotSame(retired, f.manager.getActiveObjects().iterator().next());
        assertTrue(RewindSnapshotDiff.keyEquals(NativeSpriteSubmissionAccess.REWIND_KEY,
                before.get(NativeSpriteSubmissionAccess.REWIND_KEY),
                snapshots.capture().get(NativeSpriteSubmissionAccess.REWIND_KEY)));
        f.scan();
        assertTrue(RewindSnapshotDiff.keyEquals("object-manager", expected.get("object-manager"),
                snapshots.capture().get("object-manager")));
        assertEquals(2, f.manager.getFrameCounter());
        assertEquals(0x1234, f.manager.getVblaCounter());
    }

    @Test void titleGuardReadsUpdatingAfterRosterCallbacks() throws Exception {
        var f = new Fixture(true);
        var probe = f.at(16, 1);
        var updating = ObjectManager.class.getDeclaredField("updating");
        updating.setAccessible(true);
        doAnswer(ignored -> {
            updating.setBoolean(f.manager, true);
            return f.sonic;
        }).when(f.roster).player(0);
        try {
            assertThrows(IllegalStateException.class,
                    () -> ObjectManagerInternalAccess.runNativeCompetitionTitleObjectPass(f.manager));
            assertTrue(updating.getBoolean(f.manager), "the roster callback ran before the later guard");
            assertEquals(0, probe.ticks);
            assertEquals(0, f.manager.getFrameCounter(), "the rejected pass cannot begin solid execution");
            assertEquals(0x1234, f.manager.getVblaCounter());
            verify(f.streaming, never()).update(anyInt(), anyInt(), any());
        } finally {
            updating.setBoolean(f.manager, false);
        }
    }

    @Test void ordinaryDispatchRetainsItsCounterAndNativeHookRejectsMissingAuthority() {
        var f = new Fixture(false);
        var probe = f.at(16, 1);
        assertThrows(IllegalStateException.class,
                () -> ObjectManagerInternalAccess.runNativeCompetitionTitleObjectPass(f.manager));
        assertEquals(0, f.manager.getFrameCounter());
        f.manager.update(0, f.sonic, List.of(f.tails), 0, false, true, true);
        assertEquals(1, probe.ticks);
        assertEquals(1, f.manager.getFrameCounter());
        assertEquals(0x1235, f.manager.getVblaCounter());
    }
}
