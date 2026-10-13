package com.openggf.mods.code;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TerrainSurface;
import com.openggf.level.objects.TouchResponseProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production creator enemy source: committed attacks, native touch, bounded children and rewind. */
class TestSecondStrikeEnemies {
    @TempDir static Path temporary;
    static URLClassLoader loader;

    @BeforeAll static void compileCreatorSource() throws Exception {
        var output = Files.createDirectory(temporary.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "25", "-cp", System.getProperty("java.class.path"),
                "-d", output.toString()));
        try (var sources = Files.walk(Path.of("examples/second-strike/src/main/java/secondstrike/enemies"))) {
            sources.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, TestSecondStrikeEnemies.class.getClassLoader());
    }

    @AfterAll static void closeCreatorLoader() throws Exception {
        if (loader != null) loader.close();
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test void guardCommitsDirectionThenExposesAnHonestPunishWindow() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 320;
            guard.update(1, fixture.player);
            assertEquals(0x8b, touch(guard).getCollisionFlags(), "visible armed guard is a native HURT object");
            fixture.playerX = 500; // Dodge past it during its tell.
            for (int i = 0; i < 36; i++) guard.update(i, fixture.player);
            guard.update(40, fixture.player);
            assertTrue(guard.getX() < 400, "the committed dash must not retarget the dodge");
            for (int i = 0; i < 24; i++) guard.update(i, fixture.player);
            assertEquals(0x0b, touch(guard).getCollisionFlags(), "braking restores ordinary attackable enemy collision");
            int brakeX = guard.getX();
            for (int i = 0; i < 60; i++) guard.update(i, fixture.player);
            assertEquals(brakeX, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags(), "punish window remains open while moving through");
        }
    }

    @Test void guardStopsAtNativeLedgeInsteadOfCrossingTheGap() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 320;
            guard.update(0, fixture.player);
            for (int i = 0; i < 36; i++) guard.update(i, fixture.player);
            fixture.floorPresent = false;
            guard.update(40, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
        }
    }

    @Test void callerEnablesCooperationAndDestroyingItRemovesTheSignal() throws Exception {
        try (var fixture = new Fixture()) {
            var caller = fixture.enemy("SpotterBadnik", "spotter", 540, 200);
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 700; // Outside the gunner's own detection but visible to its caller.
            caller.update(0, fixture.player);
            gunner.update(0, fixture.player);
            for (int i = 0; i < 36; i++) gunner.update(i, fixture.player);
            assertEquals(1, fixture.shots().size(), "live nearby caller must enable a committed supporting volley");

            var other = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            caller.setDestroyed(true);
            for (int i = 0; i < 48; i++) other.update(i, fixture.player);
            assertEquals(1, fixture.shots().size(), "destroyed caller cannot keep a hidden alert alive");
            assertEquals(0, touch(caller).getCollisionFlags());
        }
    }

    @Test void gunnerLocksBothShotsBeforeTellAndCapsItsLiveChildren() throws Exception {
        try (var fixture = new Fixture()) {
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 320;
            gunner.update(0, fixture.player);
            fixture.playerX = 500;
            fixture.playerY = 100;
            for (int i = 0; i < 36; i++) gunner.update(i, fixture.player);
            for (int i = 0; i < 12; i++) gunner.update(i, fixture.player);
            assertEquals(2, fixture.shots().size());
            int firstX = velocity(fixture.shots().getFirst(), "horizontalVelocity");
            int firstY = velocity(fixture.shots().getFirst(), "verticalVelocity");
            assertTrue(firstX < 0, "volley stays aimed left after the player dodges right");
            assertEquals(firstX, velocity(fixture.shots().getLast(), "horizontalVelocity"));
            assertEquals(firstY, velocity(fixture.shots().getLast(), "verticalVelocity"));
            for (int i = 0; i < 200; i++) gunner.update(i, fixture.player);
            assertEquals(2, fixture.shots().size(), "unexpired shots cannot accumulate behind the player");
            assertEquals(0x23, touch(gunner).getCollisionFlags(), "the gunner's body remains punishable throughout");
        }
    }

    @Test void shieldDeflectionRemovesHurtAuthorityAndShotLifetimeIsBounded() throws Exception {
        try (var fixture = new Fixture()) {
            var shot = fixture.enemy("CommittedShot", "shot", 400, 200);
            assertEquals(0x98, touch(shot).getCollisionFlags());
            assertEquals(8, touch(shot).getShieldReactionFlags());
            assertTrue(touch(shot).onShieldDeflect(fixture.player));
            assertEquals(0, touch(shot).getCollisionFlags());
            assertFalse(touch(shot).onShieldDeflect(fixture.player));
            for (int i = 0; i < 120; i++) shot.update(i, fixture.player);
            assertTrue(shot.isDestroyed());
        }
    }

    @Test void downwardShotExpiresOnNativeFloorAndOffscreenShotsCannotPersist() throws Exception {
        try (var fixture = new Fixture()) {
            var type = loader.loadClass("secondstrike.enemies.CommittedShot");
            var shot = (AbstractObjectInstance) type.getConstructor(ObjectSpawn.class, int.class,
                    int.class, int.class, int.class).newInstance(spawn("shot", 400, 200), 640, 128, 400, 216);
            shot.setServices(fixture.services);
            for (int i = 0; i < 5; i++) shot.update(i, fixture.player);
            assertTrue(shot.isDestroyed(), "native floor contact removes low projectile hurt authority");
            assertEquals(0, touch(shot).getCollisionFlags());

            var outside = fixture.enemy("CommittedShot", "shot", 1000, 200);
            outside.update(0, fixture.player);
            assertTrue(outside.isDestroyed(), "offscreen projectiles must clean up without a 120-frame wait");
        }
    }

    @Test void attackTellAndProjectileFractionRecreateAndReplayExactly() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.playerX = 320;
            for (String[] definition : List.of(new String[]{"SpotterBadnik", "spotter"},
                    new String[]{"GuardBadnik", "guard"}, new String[]{"ArtilleryBadnik", "artillery"})) {
                var original = fixture.enemy(definition[0], definition[1], 400, 200);
                original.update(0, fixture.player);
                for (int i = 0; i < 12; i++) original.update(i, fixture.player);
                assertReplay(original, fixture, 8);
            }
            var shotType = loader.loadClass("secondstrike.enemies.CommittedShot");
            var shot = (AbstractObjectInstance) shotType.getConstructor(ObjectSpawn.class, int.class,
                    int.class, int.class, int.class).newInstance(spawn("shot", 400, 200), -640, -123, 400, 216);
            shot.setServices(fixture.services);
            for (int i = 0; i < 3; i++) shot.update(i, fixture.player);
            assertReplay(shot, fixture, 16);
        }
    }

    private static void assertReplay(AbstractObjectInstance original, Fixture fixture, int steps) {
        var snapshot = original.captureRewindState();
        var recreated = ((ModRewindRecreatable) original).recreateForRewind(
                new RewindRecreateContext(original.getSpawn(), snapshot, fixture.services));
        recreated.setServices(fixture.services);
        recreated.restoreRewindState(snapshot);
        assertEquals(snapshot, recreated.captureRewindState(), "recreate must restore all captured phase/target/fraction state");
        for (int i = 0; i < steps; i++) {
            original.update(i, fixture.player);
            recreated.update(i, fixture.player);
            assertEquals(original.captureRewindState(), recreated.captureRewindState(),
                    "restored state must produce the same forward frames");
        }
    }

    private static TouchResponseProvider touch(AbstractObjectInstance instance) { return (TouchResponseProvider) instance; }
    private static int velocity(AbstractObjectInstance instance, String method) throws Exception {
        return (int) instance.getClass().getMethod(method).invoke(instance);
    }
    private static ObjectSpawn spawn(String key, int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, "second-strike", "second-strike:" + key);
    }

    private static final class Fixture implements AutoCloseable {
        int playerX = 320, playerY = 200;
        boolean floorPresent = true;
        final PlayableEntity player = (PlayableEntity) Proxy.newProxyInstance(
                TestSecondStrikeEnemies.class.getClassLoader(), new Class<?>[]{PlayableEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCentreX" -> (short) playerX;
                    case "getCentreY" -> (short) playerY;
                    case "getDead" -> false;
                    default -> throw new AssertionError("Unexpected player access: " + method.getName());
                });
        final StubObjectServices services = new StubObjectServices() {
            @Override public TerrainSurface floorSurface(int x, int y, TerrainSurface.Layer layer, TerrainSurface.Path path) {
                return new TerrainSurface(floorPresent, y, 0, 0);
            }
        }.withIsolatedObjectManager().withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));

        Fixture() { AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0); }

        AbstractObjectInstance enemy(String type, String key, int x, int y) throws Exception {
            var instance = (AbstractObjectInstance) loader.loadClass("secondstrike.enemies." + type)
                    .getConstructor(ObjectSpawn.class).newInstance(spawn(key, x, y));
            services.objectManager().addDynamicObject(instance);
            instance.setServices(services);
            return instance;
        }

        List<AbstractObjectInstance> shots() {
            return services.objectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("CommittedShot"))
                    .map(AbstractObjectInstance.class::cast).toList();
        }

        @Override public void close() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    }
}
