package com.openggf.game.sonic3k.competition;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the actual policy-injected dispatch and manager queue, rather than native test ports. */
class TestS3kCompetitionSpriteSubmissions {
    @Test void canonicalSstDispatchQueuesOnlyRoutinesThatActuallyCallDrawSprite() {
        var fixture = new Fixture();
        var blade = fixture.add(4, new CompetitionArenaObjectInstance(spawn(0x5E),
                CompetitionArenaObjectInstance.Kind.BLADE));
        var spring = fixture.add(5, new CompetitionArenaObjectInstance(spawn(7),
                CompetitionArenaObjectInstance.Kind.SPRING));
        var path = fixture.add(6, new CompetitionArenaObjectInstance(spawn(2),
                CompetitionArenaObjectInstance.Kind.PATH_SWAP));
        var item = fixture.add(7, new CompetitionItemObjectInstance(spawn(0x64)));
        var goal = fixture.add(8, new CompetitionGoalObjectInstance(spawn(0x65)));
        var lap = fixture.add(9, new CompetitionHudObjectInstance(CompetitionHudObjectInstance.Kind.LAP, 0));
        var reserved = fixture.add(10, new CompetitionHudObjectInstance(CompetitionHudObjectInstance.Kind.RESERVED, 0));

        fixture.manager.update(0, null, List.of(), 1, false);

        assertEquals(List.of(new NativeSpriteSubmissionAccess.Admission(4, 5),
                new NativeSpriteSubmissionAccess.Admission(7, 5),
                new NativeSpriteSubmissionAccess.Admission(8, 5),
                new NativeSpriteSubmissionAccess.Admission(9, 0)),
                NativeSpriteSubmissionAccess.admissions(fixture.manager));
        assertTrue(blade.nativeDrawPublished());
        assertTrue(item.nativeDrawPublished());
        assertTrue(goal.nativeDrawPublished());
        assertTrue(lap.nativeDrawPublished());
        assertFalse(spring.nativeDrawPublished(), "Spring init exits without Draw_Sprite");
        assertFalse(path.nativeDrawPublished());
        assertFalse(reserved.nativeDrawPublished());
        for (var object : List.of(blade, spring, path, item, goal, lap, reserved))
            assertInstanceOf(NativeSpriteSubmissionSource.class, object);
        assertTrue(NativeSpriteSubmissionAccess.nativePassPresent(fixture.manager));
    }

    @Test void actualS3kCallsSpillAtCallTimeAndRejectedCallsReceiveNoAdmission() {
        var fixture = new Fixture();
        var item = fixture.add(4, new CompetitionItemObjectInstance(spawn(0x64)));
        for (int call = 0; call < 63; call++)
            assertTrue(NativeSpriteSubmissionAccess.submitSpilling(fixture.manager, 70, 5));
        item.update(1, null);
        assertEquals(new NativeSpriteSubmissionAccess.Admission(4, 6),
                NativeSpriteSubmissionAccess.admissions(fixture.manager).getLast());
        for (int call = 1; call < 63; call++)
            assertTrue(NativeSpriteSubmissionAccess.submitSpilling(fixture.manager, 70, 6));
        for (int call = 0; call < 63; call++)
            assertTrue(NativeSpriteSubmissionAccess.submitSpilling(fixture.manager, 70, 7));
        var before = NativeSpriteSubmissionAccess.admissions(fixture.manager);
        item.update(2, null);
        assertTrue(item.nativeDrawPublished(), "Draw_Sprite ran even when its input list was full");
        assertEquals(before, NativeSpriteSubmissionAccess.admissions(fixture.manager));
        assertTrue(((NativeSpriteSubmissionSource) item).nativeSpriteSubmissionsAuthoritative());
    }

    @Test void directNativeTestPortsDoNotInventCallsOrPublishAllocatedButUnexecutedObjects() {
        var fixture = new Fixture();
        var item = fixture.add(4, new CompetitionItemObjectInstance(spawn(0x64)));
        item.updateNative(fixture.nativeWorld.session);
        assertTrue(item.nativeDrawPublished());
        assertTrue(NativeSpriteSubmissionAccess.admissions(fixture.manager).isEmpty());
        assertFalse(NativeSpriteSubmissionAccess.nativePassPresent(fixture.manager));
    }

    private static ObjectSpawn spawn(int id) { return new ObjectSpawn(0x430, 0x194, id, 0, 0, false, 0); }

    private static final class Fixture {
        final CompetitionTestWorld nativeWorld = new CompetitionTestWorld(S3kCompetitionSession.Mode.MATCH_RACE);
        final ObjectManager manager;
        Fixture() {
            var module = mock(GameModule.class);
            when(module.getIdentifier()).thenReturn("native-submission-fixture");
            when(module.getGameService(WorldSessionPolicyProvider.class))
                    .thenReturn(ignored -> nativeWorld.session);
            var world = new WorldSession(module);
            var services = mock(ObjectServices.class);
            var camera = mock(Camera.class);
            when(camera.getWidth()).thenReturn((short) 320);
            when(camera.getHeight()).thenReturn((short) 224);
            var registry = mock(ObjectRegistry.class, CALLS_REAL_METHODS);
            when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_3K);
            when(services.worldSession()).thenReturn(world);
            when(services.camera()).thenReturn(camera);
            when(services.solidExecutionRegistry()).thenReturn(com.openggf.game.solid.SolidExecutionRegistry.inert());
            manager = new ObjectManager(List.of(), registry, 0, null, null,
                    mock(GraphicsManager.class), camera, services);
            when(services.objectManager()).thenReturn(manager);
        }
        <T extends AbstractObjectInstance> T add(int slot, T object) {
            manager.addDynamicObjectAtSlot(object, slot);
            assertEquals(slot, object.getSlotIndex());
            assertFalse(object.isDestroyed());
            return object;
        }
    }
}
