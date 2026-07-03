package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.bosses.LbzEndBossInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the LBZ end boss (Obj 0xCB) child-link rewind gap and pins the reason
 * its {@code ownedChildren}/{@code platformChildren} identity collections are NOT given
 * a {@code CAPTURED} rewind policy.
 *
 * <p>An audit flagged those two collections as schema-UNSUPPORTED (they are non-final
 * identity collections, so the whole class falls onto the generic scalar path and the
 * lists are dropped on rewind). A naive fix would add {@code CAPTURED} policies for them.
 * That fix is invalid for THIS boss: its gameplay-spawned children — the bobbing
 * platforms, the Robotnik runner, the rising tube segments and the launched spike balls —
 * are NOT rewind-recreatable. They implement {@code LbzEndBossGraphChild}, whose
 * {@code recreateForRewind} returns {@code null}, and the parent's construction
 * ({@code initializeBossState}) only re-spawns the cockpit and tower, not the routine
 * children. So on a mid-fight rewind restore the child OBJECTS themselves are dropped
 * (documented in {@code docs/rewind/real-gaps.md}); capturing the boss's references to
 * them then resolves to missing objects and makes restore throw a
 * "Missing required object reference" error.
 *
 * <p>This test proves the current (lossy-but-safe) behaviour: the boss survives restore
 * while its routine-spawned child links are lost, exactly because those children cannot
 * be reconstructed. When graph-child rewind recreate is implemented for this boss (the
 * proper fix, out of scope for the child-link-collection work), the drop assertions
 * below should be flipped to assert restoration and the collections given CAPTURED
 * policies.
 */
class TestS3kLbzEndBossChildLinksRewind {

    private static final ObjectSpawn SPAWN =
            new ObjectSpawn(160, 240, Sonic3kObjectIds.LBZ_END_BOSS, 0, 0, false, 0);

    /** Forces the S3KL/LBZ zone context so the registry materialises the real boss. */
    private static final class LbzZoneRegistry extends Sonic3kObjectRegistry {
        @Override
        protected int currentRomZoneId() {
            return Sonic3kZoneIds.ZONE_LBZ;
        }
    }

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void midFightRewindDropsRoutineSpawnedChildLinksBecauseChildrenAreNotRecreatable()
            throws Exception {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mockCamera();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        ObjectManager objectManager = new ObjectManager(
                List.of(SPAWN), new LbzZoneRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = objectManager;
        objectManager.reset(0);
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        LbzEndBossInstance boss = only(objectManager, LbzEndBossInstance.class);

        // Advance into the fight: startIntro spawns the four bobbing platforms + runner.
        invokePrivate(boss, "startIntro");
        assertEquals(4, boss.getPlatformChildrenForTests().size(),
                "precondition: intro spawns four platform children");
        assertTrue(boss.hasRunnerForTests(), "precondition: intro spawns the Robotnik runner");
        assertEquals(7, boss.getOwnedChildrenForTests().size(),
                "precondition: owned children = cockpit + tower + 4 platforms + runner");
        long platformsBefore = liveOfSimpleName(objectManager, "LbzEndBossPlatformChild");
        assertEquals(4, platformsBefore, "precondition: four live platform objects in the manager");

        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewindRegistry.capture();

        // Restore does not throw: because the collections are deliberately not captured,
        // no unresolved child reference is written into the keyframe.
        rewindRegistry.restore(snapshot);

        LbzEndBossInstance restored = only(objectManager, LbzEndBossInstance.class);
        assertNotNull(restored, "the boss itself round-trips (it is spawn-recreatable)");
        assertNotSame(boss, restored, "restore recreates the boss fresh");

        // The gameplay children are gone: they are not rewind-recreatable, so the boss's
        // links to them are lost. Only the construction-spawned cockpit + tower are
        // re-adopted. This is the known gap that a graph-child recreate fix must close.
        assertEquals(0, liveOfSimpleName(objectManager, "LbzEndBossPlatformChild"),
                "routine-spawned platform children are dropped on restore (non-recreatable)");
        assertEquals(0, restored.getPlatformChildrenForTests().size(),
                "restored boss has no platform child links after a mid-fight rewind");
        assertEquals(2, restored.getOwnedChildrenForTests().size(),
                "restored boss re-adopts only its construction children (cockpit + tower)");
    }

    private static void invokePrivate(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static long liveOfSimpleName(ObjectManager objectManager, String simpleName) {
        return objectManager.getActiveObjects().stream()
                .filter(o -> o.getClass().getSimpleName().equals(simpleName))
                .filter(o -> !((ObjectInstance) o).isDestroyed())
                .count();
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(object -> type.isInstance(object) && !object.isDestroyed())
                .map(type::cast)
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
