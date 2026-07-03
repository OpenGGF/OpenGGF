package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips the LBZ end boss (Obj 0xCB) graph children across a mid-fight rewind.
 *
 * <p>The boss's routine-spawned children (bobbing platform chain, Robotnik runner, rising
 * tube segments, spike balls) are not captured directly: the boss holds them in
 * {@code final} identity collections ({@code ownedChildren}/{@code platformChildren}) which
 * are structural rewind state, and the children themselves carry structural constructor
 * args (chain subtype/sibling link, tube offsets) that are re-derived from spawn order.
 * Previously these children returned {@code null} from {@code recreateForRewind}, so a
 * mid-fight rewind dropped them and the boss lost its child links. Each graph child now
 * reconstructs itself through the parent's reconstruction path (mirroring the CPZ boss pipe
 * segment re-register pattern), rebuilding the boss's collections.
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
    void midFightRewindRebuildsPlatformChainAndRunnerLinks() throws Exception {
        ObjectManager objectManager = createManagerWithBoss();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        LbzEndBossInstance boss = only(objectManager, LbzEndBossInstance.class);

        // Advance into the fight: startIntro spawns the four bobbing platforms + runner.
        invokePrivate(boss, "startIntro");
        assertEquals(4, boss.getPlatformChildrenForTests().size(),
                "precondition: intro spawns four platform children");
        assertTrue(boss.hasRunnerForTests(), "precondition: intro spawns the Robotnik runner");
        assertEquals(7, boss.getOwnedChildrenForTests().size(),
                "precondition: owned children = cockpit + tower + 4 platforms + runner");
        ObjectRefId bossId = objectId(objectManager, boss);

        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewindRegistry.capture();

        // Diverge: remove a platform before restore.
        objectManager.removeDynamicObject((ObjectInstance) boss.getPlatformChildrenForTests().get(0));
        assertEquals(3, liveOfSimpleName(objectManager, "LbzEndBossPlatformChild"),
                "diverge step drops one platform before restore");

        rewindRegistry.restore(snapshot);

        LbzEndBossInstance restored = objectById(objectManager, LbzEndBossInstance.class, bossId);
        assertNotSame(boss, restored, "restore recreates the boss fresh");

        assertEquals(4, liveOfSimpleName(objectManager, "LbzEndBossPlatformChild"),
                "all four platform objects must be recreated on restore");
        assertEquals(4, restored.getPlatformChildrenForTests().size(),
                "restored boss must rebuild its platform child list");
        assertTrue(restored.hasRunnerForTests(),
                "restored boss must rebuild its runner link");
        assertEquals(7, restored.getOwnedChildrenForTests().size(),
                "restored boss must rebuild its full owned-child list "
                        + "(cockpit + tower + 4 platforms + runner)");

        // Every rebuilt platform link must point at a live, freshly recreated instance.
        List<?> platforms = restored.getPlatformChildrenForTests();
        for (Object platform : platforms) {
            assertSame(platform, objectManager.getActiveObjects().stream()
                            .filter(o -> o == platform && !o.isDestroyed())
                            .findFirst().orElse(null),
                    "platform link must reference a live restored instance");
            assertNotSame(boss, platform, "restored platform must not be a pre-rewind instance");
        }
    }

    @Test
    void midFightRewindRebuildsRisingTubesAndLaunchedSpikeBall() throws Exception {
        ObjectManager objectManager = createManagerWithBoss();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        LbzEndBossInstance boss = only(objectManager, LbzEndBossInstance.class);
        invokePrivate(boss, "startIntro");
        invokePrivate(boss, "startRising");   // spawns the three rising tube segments
        invokePrivate(boss, "launchSpikeBall"); // spawns one spike ball projectile
        assertEquals(3, liveOfSimpleName(objectManager, "LbzEndBossTubeSegmentChild"),
                "precondition: three tube segments spawned");
        assertEquals(1, liveOfSimpleName(objectManager, "LbzEndBossSpikeBallChild"),
                "precondition: one spike ball launched");
        int ownedBefore = boss.getOwnedChildrenForTests().size();
        ObjectRefId bossId = objectId(objectManager, boss);

        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewindRegistry.capture();

        // Diverge: drop a tube and the spike ball.
        objectManager.removeDynamicObject(firstOfSimpleName(objectManager, "LbzEndBossTubeSegmentChild"));
        objectManager.removeDynamicObject(firstOfSimpleName(objectManager, "LbzEndBossSpikeBallChild"));

        rewindRegistry.restore(snapshot);

        LbzEndBossInstance restored = objectById(objectManager, LbzEndBossInstance.class, bossId);
        assertEquals(3, liveOfSimpleName(objectManager, "LbzEndBossTubeSegmentChild"),
                "all three tube segments must be recreated on restore");
        assertEquals(1, liveOfSimpleName(objectManager, "LbzEndBossSpikeBallChild"),
                "the launched spike ball must be recreated on restore");
        assertEquals(ownedBefore, restored.getOwnedChildrenForTests().size(),
                "restored boss must rebuild its full owned-child list including tubes and spike ball");
    }

    @Test
    void midDefeatRewindRebuildsDebrisSmokeAndExtender() throws Exception {
        ObjectManager objectManager = createManagerWithBoss();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);

        LbzEndBossInstance boss = only(objectManager, LbzEndBossInstance.class);
        invokePrivate(boss, "startIntro");
        invokePrivate(boss, "launchSpikeBall");
        // The spike-ball explosion sprays eight debris pieces and four smoke puffs.
        invokePrivate(firstOfSimpleName(objectManager, "LbzEndBossSpikeBallChild"), "explode");
        // startDefeat spawns the gradual-max-X extender (and the DEFERRED explosion controller).
        invokePrivate(boss, "startDefeat");

        assertEquals(8, liveOfSimpleName(objectManager, "LbzEndBossDebrisChild"),
                "precondition: eight debris pieces from the spike-ball spray");
        assertEquals(4, liveOfSimpleName(objectManager, "LbzEndBossSmokePuffChild"),
                "precondition: four smoke puffs from the spike-ball spray");
        assertEquals(1, liveOfSimpleName(objectManager, "LbzEndBossGradualMaxXExtenderChild"),
                "precondition: one gradual-max-X extender from startDefeat");
        List<Integer> debrisFramesBefore = intFieldOf(objectManager, "LbzEndBossDebrisChild", "frame");
        List<Integer> debrisXVelBefore = intFieldOf(objectManager, "LbzEndBossDebrisChild", "xVel");
        ObjectRefId bossId = objectId(objectManager, boss);

        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        CompositeSnapshot snapshot = rewindRegistry.capture();

        // Diverge: drop a debris piece, a smoke puff, and the extender.
        objectManager.removeDynamicObject(firstOfSimpleName(objectManager, "LbzEndBossDebrisChild"));
        objectManager.removeDynamicObject(firstOfSimpleName(objectManager, "LbzEndBossSmokePuffChild"));
        objectManager.removeDynamicObject(firstOfSimpleName(objectManager, "LbzEndBossGradualMaxXExtenderChild"));

        rewindRegistry.restore(snapshot);

        LbzEndBossInstance restored = objectById(objectManager, LbzEndBossInstance.class, bossId);
        assertEquals(8, liveOfSimpleName(objectManager, "LbzEndBossDebrisChild"),
                "all eight debris pieces must be recreated on restore");
        assertEquals(4, liveOfSimpleName(objectManager, "LbzEndBossSmokePuffChild"),
                "all four smoke puffs must be recreated on restore");
        assertEquals(1, liveOfSimpleName(objectManager, "LbzEndBossGradualMaxXExtenderChild"),
                "the gradual-max-X extender must be recreated on restore");
        assertTrue(restored.usesLocalGradualMaxXExtenderForTests(),
                "the boss's extender-active flag must restore");

        // The debris cosmetics (frame) and motion (xVel) — newly captured scalars — must
        // round-trip: the restored multiset matches the captured one.
        assertEquals(debrisFramesBefore, intFieldOf(objectManager, "LbzEndBossDebrisChild", "frame"),
                "debris render frames must round-trip across the rewind");
        assertEquals(debrisXVelBefore, intFieldOf(objectManager, "LbzEndBossDebrisChild", "xVel"),
                "debris x velocities must round-trip across the rewind");
    }

    private static List<Integer> intFieldOf(ObjectManager objectManager, String simpleName, String field) {
        return objectManager.getActiveObjects().stream()
                .filter(o -> o.getClass().getSimpleName().equals(simpleName))
                .filter(o -> !o.isDestroyed())
                .map(o -> readInt(o, field))
                .sorted()
                .toList();
    }

    private static int readInt(Object target, String field) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectManager createManagerWithBoss() {
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
        return objectManager;
    }

    private static void invokePrivate(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        return objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance).map(type::cast)
                .filter(o -> !o.isDestroyed())
                .filter(o -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable().idFor(o)))
                .findFirst().orElseThrow(() -> new AssertionError("missing restored " + type.getSimpleName()));
    }

    private static ObjectInstance firstOfSimpleName(ObjectManager objectManager, String simpleName) {
        return objectManager.getActiveObjects().stream()
                .filter(o -> o.getClass().getSimpleName().equals(simpleName))
                .filter(o -> !((ObjectInstance) o).isDestroyed())
                .findFirst().orElseThrow(() -> new AssertionError("no live " + simpleName));
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
