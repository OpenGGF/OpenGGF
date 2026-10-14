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
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.game.GameStateManager;
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
import static org.mockito.Mockito.*;

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
            for (int i = 0; i < 56; i++) guard.update(i, fixture.player);
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

    @Test void reinforcedEnemiesRequireSeparatedHitsAndRewardOnlyTheFinalContact() throws Exception {
        for (String[] definition : List.of(new String[]{"SpotterBadnik", "spotter", "2"},
                new String[]{"ArtilleryBadnik", "artillery", "2"},
                new String[]{"GuardBadnik", "guard", "3"})) {
            try (var fixture = new Fixture()) {
                var enemy = fixture.enemy(definition[0], definition[1], 400, 200);
                int hits = Integer.parseInt(definition[2]);
                for (int hit = 0; hit < hits; hit++) {
                    assertEquals(hits - hit - 1, touch(enemy).getCollisionProperty(),
                            "native rebound on nonfatal hits, ordinary kill bounce on final hit");
                    attack(enemy, fixture.player);
                    if (hit < hits - 1) {
                        assertFalse(enemy.isDestroyed());
                        assertEquals(0, touch(enemy).getCollisionFlags(), "hit flash has no hurt authority");
                        attack(enemy, fixture.player); // Same-frame second player / rolling overlap.
                        assertEquals(hits - hit - 2, touch(enemy).getCollisionProperty());
                        verifyNoInteractions(fixture.score);
                        assertEquals(0, fixture.explosions());
                        fixture.playerX = 800;
                        for (int tick = 0; tick < 24; tick++) enemy.update(tick, fixture.player);
                        fixture.playerX = 320;
                    }
                }
                assertTrue(enemy.isDestroyed());
                assertEquals(1, fixture.explosions());
                verify(fixture.score, times(1)).addScore(100);
                attack(enemy, fixture.player);
                assertEquals(1, fixture.explosions(), "destroyed enemy cannot repeat its final rewards");
            }
        }
    }

    @Test void prolongedRollingOverlapCannotConsumeASecondHitAfterTheTimer() throws Exception {
        try (var fixture = new Fixture()) {
            var enemy = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 400;
            attack(enemy, fixture.player);
            for (int tick = 0; tick < 60; tick++) enemy.update(tick, fixture.player);
            attack(enemy, fixture.player);
            assertFalse(enemy.isDestroyed(), "a distinct separated contact is required");
            assertEquals(0, touch(enemy).getCollisionFlags());
            fixture.playerX = 800;
            enemy.update(61, fixture.player);
            assertEquals(0x23, touch(enemy).getCollisionFlags());
            attack(enemy, fixture.player);
            assertTrue(enemy.isDestroyed());
        }
    }

    @Test void poweredWindupUsesMultiHitHealthWhileKeepingNativeHurtCollision() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            assertEquals(0x8b, touch(guard).getCollisionFlags(), "the native armed tell still hurts on contact");
            ((PoweredScreenAttackable) guard).onPoweredScreenAttack(fixture.player);
            assertFalse(guard.isDestroyed());
            assertEquals(1, touch(guard).getCollisionProperty(), "only DASH is immune to health damage");
            assertEquals(0, fixture.explosions());
            verifyNoInteractions(fixture.score);
        }
    }

    @Test void fastCommittedDashRejectsTouchAndPoweredScreenAttacks() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 35; tick++) guard.update(tick, fixture.player);
            assertEquals(400, guard.getX(), "all 36 tell updates remain stationary");
            guard.update(36, fixture.player);
            guard.update(37, fixture.player);
            assertEquals(390, guard.getX(), "charge is 10 pixels/update instead of 4");
            attack(guard, fixture.player);
            ((PoweredScreenAttackable) guard).onPoweredScreenAttack(fixture.player);
            assertFalse(guard.isDestroyed());
            assertEquals(2, touch(guard).getCollisionProperty(), "invulnerable dash cannot lose HP");
            for (int tick = 0; tick < 12; tick++) guard.update(tick, fixture.player);
            assertTrue(Math.abs(guard.getX() - 400) <= 96);
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            assertEquals(0, fixture.explosions());
            verifyNoInteractions(fixture.score);
        }
    }

    @Test void fastDashChecksIntermediateFloorInsteadOfSkippingOverANarrowGap() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            fixture.floorGapX = 393;
            guard.update(37, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
        }
    }

    @Test void guardReturnsSlowlyAfterTheFullBrakeThenChargesTheSameDirectionAgain() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 260;
            finishGuardCharge(guard, fixture);
            assertEquals(310, guard.getX());
            for (int tick = 0; tick < 72; tick++) {
                guard.update(tick, fixture.player);
                assertEquals(310, guard.getX(), "all72 exposed brake updates remain stationary");
                assertEquals(0x0b, touch(guard).getCollisionFlags());
            }
            for (int tick = 0; tick < 45; tick++) {
                int previousX = guard.getX();
                guard.update(tick, fixture.player);
                assertEquals(2, guard.getX() - previousX, "return is a walk, not a teleport or charge");
                assertEquals(0x0b, touch(guard).getCollisionFlags(), "return stays vulnerable");
            }
            assertEquals(400, guard.getX());
            assertEquals(1, fixture.sounds.stream().filter(id -> id == 0x43).count());
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            guard.update(37, fixture.player);
            assertEquals(390, guard.getX(), "a second same-direction charge has real10px travel");
            assertEquals(2, fixture.sounds.stream().filter(id -> id == 0x43).count());
        }
    }

    @Test void returnRemainsAttackableAndItsMidWalkScalarStateReplays() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 260;
            finishGuardCharge(guard, fixture);
            for (int tick = 0; tick < 84; tick++) guard.update(tick, fixture.player);
            assertEquals(334, guard.getX());
            assertReplay(guard, fixture, 8);
            assertEquals(350, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            attack(guard, fixture.player);
            assertFalse(guard.isDestroyed());
            assertEquals(1, touch(guard).getCollisionProperty(), "return admits an ordinary nonfatal hit");
            assertEquals(0, fixture.explosions());
            verifyNoInteractions(fixture.score);
        }
    }

    @Test void guardReturnStopsAtAnIntermediateGapAndCannotFlashABlockedFlameDash() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 260;
            finishGuardCharge(guard, fixture);
            for (int tick = 0; tick < 72; tick++) guard.update(tick, fixture.player);
            fixture.floorGapX = 311; // One-pixel hole before a clear2px return endpoint.
            for (int tick = 0; tick < 70; tick++) {
                guard.update(tick, fixture.player);
                assertEquals(310, guard.getX(), "a rejected stride cannot teleport toward the post");
                assertEquals(0x0b, touch(guard).getCollisionFlags(), "blocked return must not re-arm");
            }
            assertEquals(1, fixture.sounds.stream().filter(id -> id == 0x43).count(),
                    "a range-blocked charge must not enter flame DASH");
        }
    }

    @Test void guardCancelsFlameEntryWhenFloorDisappearsDuringTheTell() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            fixture.floorPresent = false;
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            assertFalse(fixture.sounds.contains(0x43), "flame dash entry requires real native travel");
        }
    }

    private static void finishGuardCharge(AbstractObjectInstance guard, Fixture fixture) {
        guard.update(0, fixture.player);
        for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
        for (int tick = 0; tick < 10; tick++) guard.update(tick, fixture.player);
    }

    @Test void oneFloorValidatedCompanionIsNonrecursiveAndRetiresWithAnUnloadedParent() throws Exception {
        try (var fixture = new Fixture()) {
            AbstractObjectInstance.updateCameraBounds(7400, 400, 8000, 900, 0);
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            fixture.playerX = 0x1e00; fixture.playerY = 0x27c;
            gunner.update(0, fixture.player);
            assertEquals(1, fixture.guards().size());
            var ally = fixture.guards().getFirst();
            for (int tick = 0; tick < 20; tick++) {
                gunner.update(tick, fixture.player);
                ally.update(tick, fixture.player);
            }
            assertEquals(1, fixture.guards().size(), "companions cannot produce more companions");
            assertReplay(ally, fixture, 4);
            gunner.setDestroyed(true);
            assertEquals(0, touch(ally).getCollisionFlags(), "parent retirement removes same-pass hurt authority");
            ally.update(22, fixture.player);
            assertTrue(ally.isDestroyed());
            assertEquals(0, fixture.explosions(), "lifetime cleanup gives no extra reward");
        }
    }

    @Test void parentCombatDefeatPreservesAllyHealthAndRespawnCannotDuplicateIt() throws Exception {
        try (var fixture = new Fixture()) {
            AbstractObjectInstance.updateCameraBounds(7400, 400, 8000, 900, 0);
            var parent = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            fixture.playerX = 0x1e00; fixture.playerY = 0x27c;
            parent.update(0, fixture.player);
            var ally = fixture.guards().getFirst();
            attack(parent, fixture.player);
            fixture.playerX = 7900;
            for (int tick = 0; tick < 24; tick++) parent.update(tick, fixture.player);
            attack(parent, fixture.player);
            assertTrue(parent.isDestroyed());
            assertFalse(ally.isDestroyed());
            assertEquals(2, touch(ally).getCollisionProperty(), "combat does not give a free ally kill/hit");
            ally.update(25, fixture.player);
            assertFalse(ally.isDestroyed());
            assertReplay(ally, fixture, 4);
            var respawn = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            respawn.update(26, fixture.player);
            assertEquals(1, fixture.guards().size(), "one live ally per native parent anchor across respawn");
            AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0);
            ally.update(27, fixture.player);
            assertTrue(ally.isDestroyed(), "native range retirement is silent even after combat release");
            assertEquals(1, fixture.explosions(), "only defeated artillery received rewards");
            verify(fixture.score, times(1)).addScore(100);
        }
    }

    @Test void missingCompanionFloorDoesNotInventAnEnemyPlatform() throws Exception {
        try (var fixture = new Fixture()) {
            AbstractObjectInstance.updateCameraBounds(7400, 400, 8000, 900, 0);
            fixture.floorPresent = false;
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            for (int tick = 0; tick < 20; tick++) gunner.update(tick, fixture.player);
            assertTrue(fixture.guards().isEmpty());
        }
    }

    @Test void hurtHealthCooldownAndSeparationRecreateAndReplay() throws Exception {
        try (var fixture = new Fixture()) {
            for (String[] definition : List.of(new String[]{"SpotterBadnik", "spotter"},
                    new String[]{"GuardBadnik", "guard"}, new String[]{"ArtilleryBadnik", "artillery"})) {
                var enemy = fixture.enemy(definition[0], definition[1], 400, 200);
                attack(enemy, fixture.player);
                assertReplay(enemy, fixture, 12);
                assertFalse(enemy.isDestroyed());
            }
        }
    }

    @Test void spotterUsesOneNativeCoconutCueAtEachCallEntry() throws Exception {
        try (var fixture = new Fixture()) {
            var spotter = fixture.enemy("SpotterBadnik", "spotter", 400, 200);
            spotter.update(0, fixture.player);
            assertEquals(List.of(0x51), fixture.sounds);
            for (int tick = 0; tick < 168; tick++) spotter.update(tick, fixture.player);
            assertEquals(List.of(0x51), fixture.sounds, "call and rest cannot retrigger the cue");
            spotter.update(169, fixture.player);
            assertEquals(List.of(0x51, 0x51), fixture.sounds);
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
    private static void attack(AbstractObjectInstance enemy, PlayableEntity player) {
        ((TouchResponseAttackable) enemy).onPlayerAttack(player, null);
    }
    private static int velocity(AbstractObjectInstance instance, String method) throws Exception {
        return (int) instance.getClass().getMethod(method).invoke(instance);
    }
    private static ObjectSpawn spawn(String key, int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, "second-strike", "second-strike:" + key);
    }

    private static final class Fixture implements AutoCloseable {
        int playerX = 320, playerY = 200;
        boolean floorPresent = true;
        int floorGapX = Integer.MIN_VALUE;
        final GameStateManager score = mock(GameStateManager.class);
        final List<Integer> sounds = new ArrayList<>();
        final PlayableEntity player = (PlayableEntity) Proxy.newProxyInstance(
                TestSecondStrikeEnemies.class.getClassLoader(), new Class<?>[]{PlayableEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCentreX" -> (short) playerX;
                    case "getCentreY" -> (short) playerY;
                    case "getDead" -> false;
                    case "incrementBadnikChain" -> 100;
                    default -> throw new AssertionError("Unexpected player access: " + method.getName());
                });
        final StubObjectServices services = new StubObjectServices() {
            @Override public TerrainSurface floorSurface(int x, int y, TerrainSurface.Layer layer, TerrainSurface.Path path) {
                return new TerrainSurface(floorPresent && x != floorGapX, y, 0, 0);
            }
            @Override public GameStateManager gameState() { return score; }
            @Override public void playSfx(int soundId) { sounds.add(soundId); }
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
        List<AbstractObjectInstance> guards() {
            return services.objectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("GuardBadnik") && !o.isDestroyed())
                    .map(AbstractObjectInstance.class::cast).toList();
        }
        long explosions() {
            return services.objectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("ExplosionObjectInstance")).count();
        }

        @Override public void close() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    }
}
