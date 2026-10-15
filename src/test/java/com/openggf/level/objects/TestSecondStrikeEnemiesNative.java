package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native ROM floor/touch/slot owners; local interactions, not campaign entry evidence. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeEnemiesNative {
    @TempDir static Path temporary;
    private static URLClassLoader creator;
    private HeadlessTestFixture fixture;
    private ObjectManager manager;
    private TestObjectServices services;
    private Sonic sonic;
    private Tails tails;
    private GameStateManager score;

    @BeforeAll static void compileOwnedEnemySource() throws Exception {
        creator = com.openggf.mods.code.SecondStrikeSourceFixture.compile(temporary.resolve("enemies"),
                com.openggf.mods.code.SecondStrikeSourceFixture.ENEMIES);
    }
    @AfterAll static void closeCreator() throws Exception { if (creator != null) creator.close(); }
    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        SessionManager.clear();
    }

    private void setup(int act) throws Exception { setup(act, false); }

    private void setup(int act, boolean nativeViewport) throws Exception {
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, act).withSkippedZoneIntro().build();
        sonic = nativeViewport ? (Sonic) fixture.sprite() : new Sonic("sonic", (short) 400, (short) 200);
        tails = new Tails("tails", (short) 400, (short) 200);
        tails.setCpuControlled(true);
        score = new GameStateManager();
        var camera = nativeViewport ? fixture.camera() : mock(Camera.class);
        if (!nativeViewport) {
            when(camera.getX()).thenReturn((short) 0);
            when(camera.getY()).thenReturn((short) 0);
            when(camera.getWidth()).thenReturn((short) 900);
            when(camera.getHeight()).thenReturn((short) 500);
            when(camera.getFocusedSprite()).thenReturn(sonic);
        }
        var debug = mock(DebugOverlayManager.class);
        var module = new Sonic3kGameModule();
        // TestObjectServices otherwise prioritizes the level's stock manager over
        // its direct manager. Children and queries must use this interaction's SST.
        services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
        }.withCamera(camera).withDebugOverlay(debug)
                .withGameState(score).withGameModule(module).withLevelManager(GameServices.level())
                .withSidekicks(List.of(tails));
        var table = new TouchResponseTable(com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom()),
                Sonic3kConstants.TOUCH_SIZES_ADDR, Sonic3kConstants.TOUCH_SIZES_COUNT);
        manager = new ObjectManager(List.of(), module.createObjectRegistry(), 0, null, table,
                GraphicsManager.getInstance(), camera, services);
        // Match the production host's owned class resolution boundary, restricted
        // to the exact loader used by this source-compiled interaction fixture.
        manager.setRewindClassResolver(com.openggf.mods.code.SecondStrikeSourceFixture.ownedResolver(creator));
        services.withDirectObjectManager(manager);
        assertSame(manager, services.objectManager());
        AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0);
    }

    private AbstractObjectInstance enemy(String className, String key, int x, int y, int layout) throws Exception {
        var spawn = new ObjectSpawn(x, y, 0, 0, 0, false, y, layout, "second-strike", "second-strike:" + key);
        var enemy = (AbstractObjectInstance) creator.loadClass("secondstrike.enemies." + className)
                .getConstructor(ObjectSpawn.class).newInstance(spawn);
        manager.addDynamicObject(enemy);
        return enemy;
    }
    private static TouchResponseProvider touch(AbstractObjectInstance object) { return (TouchResponseProvider) object; }
    private void attackPosition(com.openggf.sprites.playable.AbstractPlayableSprite player, int x, int y) {
        player.setAir(true); player.setRolling(true);
        player.setAnimationId(com.openggf.game.sonic3k.constants.Sonic3kAnimationIds.ROLL.id());
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        player.setXSpeed((short) 0x280); player.setYSpeed((short) 0x400); player.setGSpeed((short) 0x280);
    }

    @Test void nativeP1ReboundAndSameFrameP2TouchConsumeOnlyOneHealthPoint() throws Exception {
        setup(0);
        var enemy = enemy("ArtilleryBadnik", "artillery", 400, 200, -1);
        attackPosition(sonic, 400, 200); attackPosition(tails, 400, 200);
        manager.update(0, sonic, List.of(tails), 1, false);
        manager.runTouchResponsesForPlayer(sonic, 2);
        manager.runTouchResponsesForPlayer(tails, 2);
        assertFalse(enemy.isDestroyed());
        assertEquals(0, touch(enemy).getCollisionProperty());
        assertEquals(-0x280, sonic.getXSpeed());
        assertEquals(-0x400, sonic.getYSpeed());
        assertEquals(-0x280, sonic.getGSpeed(), "S3K native rebound also negates ground velocity");
        assertEquals(0x280, tails.getXSpeed(), "P2 sees the post-hit zero collision byte");
        assertEquals(0, score.getScore());
        assertTrue(manager.getActiveObjects().stream().noneMatch(AnimalObjectInstance.class::isInstance));
        assertTrue(manager.getActiveObjects().stream().noneMatch(ExplosionObjectInstance.class::isInstance));
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 800);
        NativePositionOps.writeXPosPreserveSubpixel(tails, 800);
        for (int tick = 0; tick < 24; tick++) enemy.update(tick, sonic);
        attackPosition(sonic, 400, 200);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 3);
        assertTrue(enemy.isDestroyed());
        assertEquals(100, score.getScore());
        assertEquals(0x300, sonic.getYSpeed(), "native final enemy bounce subtracts $100 at/below the enemy");
        var explosion = manager.getActiveObjects().stream().filter(ExplosionObjectInstance.class::isInstance)
                .map(ExplosionObjectInstance.class::cast).findFirst().orElseThrow();
        explosion.update(4, sonic);
        assertEquals(1, manager.getActiveObjects().stream().filter(AnimalObjectInstance.class::isInstance).count());
        assertEquals(1, manager.getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance.class::isInstance).count());
        explosion.update(5, sonic);
        assertEquals(1, manager.getActiveObjects().stream().filter(AnimalObjectInstance.class::isInstance).count(),
                "the rewarded final touch allocates one native animal, once");
    }

    @Test void nativeShortPlatformRecoveryDoesNotWaitForANearbyCpuSidekick() throws Exception {
        setup(0);
        int x = 0x1dc0, y = 0x27c;
        when(services.camera().getX()).thenReturn((short) (x - 160));
        when(services.camera().getY()).thenReturn((short) (y - 112));
        AbstractObjectInstance.updateCameraBounds(x - 160, y - 112, x + 160, y + 112, 0);
        var floor = services.floorSurface(x + 32, y + 16,
                TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
        assertTrue(floor.foundSurface(), "the nearby participant has actual native platform footing");
        assertTrue(floor.distance() >= -1 && floor.distance() < 12);
        var gunner = enemy("ArtilleryBadnik", "artillery", x, y, 40);
        attackPosition(sonic, x, y);
        NativePositionOps.writeXPosPreserveSubpixel(tails, x + 32);
        NativePositionOps.writeYPosPreserveSubpixel(tails, y);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 1);
        assertEquals(0, touch(gunner).getCollisionProperty());
        assertEquals(-0x280, sonic.getXSpeed());
        assertEquals(-0x400, sonic.getYSpeed(), "the first contact uses native multi-hit rebound");
        NativePositionOps.writeXPosPreserveSubpixel(sonic, x + 80);
        for (int tick = 0; tick < 23; tick++) gunner.update(tick, sonic);
        assertEquals(0, touch(gunner).getCollisionFlags());
        gunner.update(23, sonic);
        assertEquals(0x23, touch(gunner).getCollisionFlags(),
                "fixed24-update recovery cannot be held by P2 standing32px away");
        attackPosition(sonic, x, y);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 2);
        assertTrue(gunner.isDestroyed(), "a fresh P1 attack is accepted with nearby CPU Tails unchanged");
        assertEquals(100, score.getScore());
        assertEquals(0x300, sonic.getYSpeed(), "the final contact retains native kill bounce");
    }

    @Test void eachNativeAttackSlotKeepsOneHeldContactWithoutBlockingTheOtherSlot() throws Exception {
        for (boolean p2First : List.of(false, true)) {
            setup(0);
            var gunner = enemy("ArtilleryBadnik", "artillery", 400, 200, -1);
            var first = p2First ? tails : sonic;
            var second = p2First ? sonic : tails;
            attackPosition(first, 400, 200);
            NativePositionOps.writeXPosPreserveSubpixel(second, 480);
            manager.snapshotTouchResponseState();
            manager.runTouchResponsesForPlayer(first, 1);
            assertEquals(0, touch(gunner).getCollisionProperty());
            assertEquals(-0x280, first.getXSpeed());
            assertEquals(-0x400, first.getYSpeed());
            for (int tick = 0; tick < 24; tick++) gunner.update(tick, sonic);
            assertEquals(0x23, touch(gunner).getCollisionFlags());
            manager.snapshotTouchResponseState();
            manager.runTouchResponsesForPlayer(first, 2);
            assertFalse(gunner.isDestroyed(), "one continuously held native attack cannot take both health points");
            assertEquals(0, score.getScore());
            assertEquals(-0x400, first.getYSpeed(), "a rejected final contact does not manufacture a native kill bounce");
            attackPosition(second, 400, 200);
            manager.runTouchResponsesForPlayer(second, 2);
            assertTrue(gunner.isDestroyed(), "the other native slot can make its own fresh attack after recovery");
            assertEquals(100, score.getScore());
            assertEquals(0x300, second.getYSpeed());
        }
    }

    @Test void recoveredNativeBodyHurtsAnUncurledAttackerAndScreenAttackIsADiscreteAction() throws Exception {
        setup(0);
        var gunner = enemy("ArtilleryBadnik", "artillery", 400, 200, -1);
        attackPosition(sonic, 400, 200);
        NativePositionOps.writeXPosPreserveSubpixel(tails, 480);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 1);
        for (int tick = 0; tick < 24; tick++) gunner.update(tick, sonic);
        sonic.setRolling(false); sonic.setAnimationId(0); sonic.setRingCount(1);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 2);
        assertTrue(sonic.isHurt(), "collision recovers even while the attacker's old contact is held");
        assertFalse(gunner.isDestroyed()); assertEquals(0, score.getScore());
        // Publish a normal completed object pass before the actual screen dispatcher.
        manager.update(0, sonic, List.of(tails), 3, false);
        manager.poweredAttacks().apply(sonic);
        assertTrue(gunner.isDestroyed(), "native property0 screen eligibility is independent of held body contact");
        assertEquals(100, score.getScore());
        manager.poweredAttacks().apply(sonic);
        assertEquals(100, score.getScore());
    }

    @Test void additionalNativeAttackersKeepExactHeldContactsThroughProgressedWorldRecreation() throws Exception {
        for (boolean mixed : List.of(false, true)) for (boolean fourthFirst : List.of(false, true)) {
            setup(0);
            AbstractPlayableSprite third = mixed ? new Knuckles("third", (short) 800, (short) 200)
                    : new Tails("third", (short) 800, (short) 200);
            var fourth = new Tails("fourth", (short) 800, (short) 200);
            third.setCpuControlled(true); fourth.setCpuControlled(true);
            var followers = List.of(tails, third, fourth);
            var sprites = new com.openggf.sprites.managers.SpriteManager();
            sprites.addSprite(sonic);
            for (var follower : followers) sprites.addSprite(follower);
            services.withSpriteManager(sprites).withSidekicks(followers);
            NativePositionOps.writeXPosPreserveSubpixel(sonic, 800);
            NativePositionOps.writeXPosPreserveSubpixel(tails, 800);
            manager.update(0, sonic, followers, 0, false);
            var participants = services.playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
            assertEquals(4, participants.size());
            assertSame(sonic, participants.get(0)); assertSame(tails, participants.get(1));
            assertSame(third, participants.get(2)); assertSame(fourth, participants.get(3));
            var gunner = enemy("ArtilleryBadnik", "artillery", 400, 200, -1);
            var first = fourthFirst ? fourth : third;
            var fresh = fourthFirst ? third : fourth;
            attackPosition(first, 400, 200);
            manager.snapshotTouchResponseState();
            manager.runTouchResponsesForPlayer(first, 1);
            assertEquals(0, touch(gunner).getCollisionProperty(), "actual P3/P4 touch consumes one health point");
            assertEquals(-0x280, first.getXSpeed()); assertEquals(-0x400, first.getYSpeed());
            for (int tick = 0; tick < 8; tick++) manager.update(0, sonic, followers, tick + 2, false);
            manager.validateRewindReferenceClosure();
            var captured = manager.rewindSnapshottable().capture();
            assertNotNull(gunner.captureRewindState(manager.captureIdentityContext()).compactGenericState(),
                    "the default object schema admits the fresh player-reference array");
            for (int tick = 0; tick < 16; tick++) manager.update(0, sonic, followers, tick + 10, false);
            var expected = manager.rewindSnapshottable().capture();
            assertEquals(0x23, touch(gunner).getCollisionFlags());
            manager.removeDynamicObject(gunner);
            manager.rewindSnapshottable().restore(captured);
            var recreated = manager.getActiveObjects().stream()
                    .filter(object -> object.getClass().getSimpleName().equals("ArtilleryBadnik"))
                    .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
            assertNotSame(gunner, recreated);
            assertEquals(captured.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects());
            for (int tick = 0; tick < 16; tick++) manager.update(0, sonic, followers, tick + 10, false);
            manager.validateRewindReferenceClosure();
            assertEquals(expected.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects(),
                    "a progressed held-contact snapshot recreates and produces the same next16 object updates");
            manager.snapshotTouchResponseState();
            manager.runTouchResponsesForPlayer(first, 26);
            assertFalse(recreated.isDestroyed(), "the recreated body rejects the same held P3/P4 attack");
            assertEquals(-0x400, first.getYSpeed(), "a rejected final attack cannot manufacture a native kill bounce");
            attackPosition(fresh, 400, 200);
            manager.runTouchResponsesForPlayer(fresh, 26);
            assertTrue(recreated.isDestroyed(), "the other fresh P3/P4 attacker is admitted after24 updates");
            assertEquals(100, score.getScore()); assertEquals(0x300, fresh.getYSpeed());
        }
    }

    @Test void actualNativeBloomPlatformsAdmitOnlyGroundedBoundedGuards() throws Exception {
        var checks = new ArrayList<org.junit.jupiter.api.function.Executable>();
        for (int act = 0; act < 2; act++) {
            setup(act);
            var anchors = act == 0 ? List.of(new int[]{0x1dc0, 0x27c, 40}, new int[]{0x2370, 0x37c, 82})
                    : List.of(new int[]{0x09f0, 0x3fc, 95}, new int[]{0x0e10, 0x33c, 138});
            for (int[] anchor : anchors) {
                AbstractObjectInstance.updateCameraBounds(anchor[0] - 160, anchor[1] - 112,
                        anchor[0] + 320, anchor[1] + 112, 0);
                var parent = enemy("ArtilleryBadnik", "artillery", anchor[0], anchor[1], anchor[2]);
                parent.update(0, sonic);
                var allies = manager.getActiveObjects().stream()
                        .filter(o -> o.getClass().getSimpleName().equals("GuardBadnik") && o.getX() == anchor[0] + 48).toList();
                int floorY = anchor[1];
                var probes = new ArrayList<TerrainSurface>();
                for (int offset = 4; offset <= 48; offset += 4) {
                    var probe = services.floorSurface(anchor[0] + offset, floorY + 16,
                            TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
                    probes.add(probe);
                    floorY += probe.distance();
                }
                int expected = anchor[0] == 0x0e10 ? 0 : 1;
                checks.add(() -> assertEquals(expected, allies.size(), "native parent "
                        + Integer.toHexString(anchor[0]) + " floor probes=" + probes));
                if (expected == 0) {
                    assertFalse(probes.getLast().foundSurface(), "native gap must reject the proposed ally");
                }
                if (allies.isEmpty()) continue;
                var ally = allies.getFirst();
                var floor = services.floorSurface(ally.getX(), ally.getY() + 16,
                        TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
                assertTrue(floor.foundSurface());
                assertEquals(0, floor.distance(), "actual ROM floor at the companion's native feet");
                assertEquals(2, touch((AbstractObjectInstance) ally).getCollisionProperty());
                System.out.printf("SecondStrike native ally parent=%04X,%04X ally=%04X,%04X slot=%d%n",
                        anchor[0], anchor[1], ally.getX(), ally.getY(), ((AbstractObjectInstance) ally).getSlotIndex());
            }
        }
        assertAll(checks);
    }

    @ParameterizedTest @EnumSource(WidescreenAspect.class)
    void nativeCompanionEntryKeepsItsFullTellAtEverySupportedViewport(WidescreenAspect aspect) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        try {
            setup(0, true);
            var camera = fixture.camera();
            assertSame(camera, services.camera());
            assertFalse(mockingDetails(camera).isMock());
            assertEquals(aspect.pixelWidth(), camera.getWidth(), "the production camera observes the configured preset");
            assertEquals(224, camera.getHeight());
            int x = 0x1dc0, y = 0x27c, left = x + 16 - aspect.pixelWidth();
            camera.setFrozen(true);
            camera.setX((short) left); camera.setY((short) (y - 112));
            NativePositionOps.writeXPosPreserveSubpixel(sonic, x - 80);
            NativePositionOps.writeYPosPreserveSubpixel(sonic, y);
            NativePositionOps.writeXPosPreserveSubpixel(tails, x - 240);
            NativePositionOps.writeYPosPreserveSubpixel(tails, y);
            manager.snapshotTouchResponseState();
            var parent = enemy("ArtilleryBadnik", "artillery", x, y, 40);
            parent.update(0, sonic);
            var ally = manager.getActiveObjects().stream()
                    .filter(object -> object.getClass().getSimpleName().equals("GuardBadnik"))
                    .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
            var identity = services.objectQuery().identityOf(ally).orElseThrow();
            int slot = ally.getSlotIndex(), entryX = ally.getX();
            assertTrue(entryX - ally.getOnScreenHalfWidth() > camera.getX() + camera.getWidth(),
                    "the declared body is completely hidden beyond the real viewport");
            var floor = services.floorSurface(ally.getX(), ally.getY() + 16,
                    TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
            assertTrue(floor.foundSurface()); assertEquals(0, floor.distance(), "actual ROM primary floor under the admitted ally");
            for (int tick = 0; tick < 8; tick++) {
                ally.update(tick, sonic);
                assertFalse(ally.isDestroyed(), "the independent32px lifetime range preserves pre-entry identity");
                assertEquals(0x0b, touch(ally).getCollisionFlags(), "a hidden ally has not armed its charge tell");
                assertEquals(entryX, ally.getX());
            }
            camera.setX((short) (left + 64));
            manager.snapshotTouchResponseState();
            assertTrue(ally.getX() + ally.getOnScreenHalfWidth() <= camera.getX() + camera.getWidth());
            ally.update(8, sonic);
            assertEquals(identity, services.objectQuery().identityOf(ally).orElseThrow());
            assertEquals(slot, ally.getSlotIndex());
            assertEquals(0x8b, touch(ally).getCollisionFlags(), "visible entry begins the native HURT tell");
            attackPosition(sonic, ally.getX(), ally.getY()); sonic.setRingCount(1);
            manager.snapshotTouchResponseState();
            manager.runTouchResponsesForPlayer(sonic, 9);
            assertTrue(sonic.isHurt(), "the real native touch owner enforces the armed tell, even against rolling Sonic");
            assertEquals(2, touch(ally).getCollisionProperty()); assertEquals(0, score.getScore());
            for (int tick = 0; tick < 35; tick++) {
                ally.update(10 + tick, sonic);
                assertEquals(entryX, ally.getX(), "all36 visible tell updates remain stationary on native terrain");
            }
            ally.update(45, sonic);
            assertEquals(entryX, ally.getX(), "dash entry follows the complete tell without early travel");
            ally.update(46, sonic);
            assertEquals(entryX - 10, ally.getX(), "the first native-floor dash stride follows the whole tell");
            assertEquals(identity, services.objectQuery().identityOf(ally).orElseThrow());
        } finally {
            config.clearSessionOverrides();
        }
    }

    @Test void nativeGuardMidReturnKeepsItsFloorSlotIdentityAndOrdinaryAttackBounce() throws Exception {
        setup(0);
        when(services.camera().getX()).thenReturn((short) 0x1aa0);
        when(services.camera().getY()).thenReturn((short) 0x350);
        AbstractObjectInstance.updateCameraBounds(0x1aa0, 0x350, 0x1f24, 0x544, 0);
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 0x1c40 - 140);
        NativePositionOps.writeYPosPreserveSubpixel(sonic, 0x3c0);
        NativePositionOps.writeXPosPreserveSubpixel(tails, 0x1c40 - 240);
        NativePositionOps.writeYPosPreserveSubpixel(tails, 0x3c0);
        var guard = enemy("GuardBadnik", "guard", 0x1c40, 0x3c0, 28);
        int slot = guard.getSlotIndex();
        guard.update(0, sonic);
        for (int tick = 0; tick < 36; tick++) guard.update(tick, sonic);
        for (int tick = 0; tick < 23; tick++) guard.update(tick, sonic);
        int brakeX = guard.getX();
        assertTrue(brakeX < 0x1c40 && brakeX >= 0x1c40 - 225,
                "the real slope may end a charge before its225px budget");
        assertEquals(0x0b, touch(guard).getCollisionFlags());
        for (int tick = 0; tick < 84; tick++) guard.update(tick, sonic);
        assertTrue(guard.getX() > brakeX && guard.getX() <= brakeX + 24,
                () -> "real ROM slope admits a bounded slow return; native primary probes="
                + java.util.stream.IntStream.rangeClosed(-4, 6).mapToObj(offset -> offset + ":"
                + services.floorSurface(guard.getX() + offset, guard.getY() + 16,
                TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY)).toList());
        assertEquals(0x0b, touch(guard).getCollisionFlags());
        var captured = manager.rewindSnapshottable().capture();
        for (int tick = 0; tick < 10; tick++) guard.update(tick, sonic);
        var expected = guard.captureRewindState();
        manager.removeDynamicObject(guard);
        manager.rewindSnapshottable().restore(captured);
        var restored = manager.getActiveObjects().stream()
                .filter(object -> object.getClass().getSimpleName().equals("GuardBadnik"))
                .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
        assertEquals(slot, restored.getSlotIndex());
        assertEquals(captured.dynamicObjects(), manager.rewindSnapshottable().capture().dynamicObjects());
        for (int tick = 0; tick < 10; tick++) restored.update(tick, sonic);
        assertEquals(expected, restored.captureRewindState());
        var floor = services.floorSurface(restored.getX(), restored.getY() + 16,
                TerrainSurface.Layer.FOREGROUND, TerrainSurface.Path.PRIMARY);
        assertTrue(floor.foundSurface(), "native primary terrain remains under the return");
        attackPosition(sonic, restored.getX(), restored.getY());
        manager.update(0x1ba0, sonic, List.of(tails), 1, false);
        manager.runTouchResponsesForPlayer(sonic, 2);
        assertEquals(1, touch(restored).getCollisionProperty());
        assertEquals(-0x280, sonic.getXSpeed());
        assertEquals(-0x400, sonic.getYSpeed());
        assertEquals(-0x280, sonic.getGSpeed());
        assertEquals(slot, restored.getSlotIndex());
        assertEquals(0, score.getScore());
    }

    @Test void nativePoweredScreenDispatchKeepsStockArmourEligibilityAndOnlyDefeatsTheFinalHit() throws Exception {
        setup(0);
        when(services.camera().getX()).thenReturn((short) 0x1aa0);
        when(services.camera().getY()).thenReturn((short) 0x350);
        AbstractObjectInstance.updateCameraBounds(0x1aa0, 0x350, 0x1f24, 0x544, 0);
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 0x1c40 - 140);
        NativePositionOps.writeYPosPreserveSubpixel(sonic, 0x3c0);
        NativePositionOps.writeXPosPreserveSubpixel(tails, 0x1c40 - 240);
        var guard = enemy("GuardBadnik", "guard", 0x1c40, 0x3c0, 21);
        var gunner = enemy("ArtilleryBadnik", "artillery", 0x1ce0, 0x3c0, 22);
        manager.update(0x1aa0, sonic, List.of(tails), 1, false);
        assertEquals(0x8b, touch(guard).getCollisionFlags());
        assertEquals(1, touch(gunner).getCollisionProperty());
        manager.poweredAttacks().apply(sonic);
        assertEquals(2, touch(guard).getCollisionProperty(), "armed HURT targets are not screen-attack enemies");
        assertEquals(1, touch(gunner).getCollisionProperty(), "native screen attacks skip multi-hit armour");
        assertEquals(0, score.getScore());

        // Separately admit one ordinary hit, then exercise the actual screen dispatcher
        // at property0. A direct powered callback alone does not prove that eligibility.
        ((TouchResponseAttackable) gunner).onPlayerAttack(sonic,
                new TouchResponseResult(0, 16, 16, TouchCategory.ENEMY));
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 0x1d90);
        NativePositionOps.writeXPosPreserveSubpixel(tails, 0x1d90);
        for (int tick = 0; tick < 24; tick++) gunner.update(tick, sonic);
        manager.update(0x1aa0, sonic, List.of(tails), 2, false);
        assertEquals(0, touch(gunner).getCollisionProperty());
        assertEquals(0x23, touch(gunner).getCollisionFlags());
        manager.poweredAttacks().apply(sonic);
        assertTrue(gunner.isDestroyed());
        assertFalse(guard.isDestroyed());
        assertEquals(100, score.getScore());
        manager.poweredAttacks().apply(sonic);
        assertEquals(100, score.getScore(), "the final defeat cannot repeat rewards");
    }

    @Test void companionSlotGraphRestoresWithoutDuplicationAndRangeRetirementReleasesItsSlot() throws Exception {
        setup(0);
        AbstractObjectInstance.updateCameraBounds(0x1d20, 0x20c, 0x1f00, 0x2ec, 0);
        var parent = enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c, 40);
        parent.update(0, sonic);
        var ally = manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("GuardBadnik"))
                .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
        int slot = ally.getSlotIndex();
        assertTrue(slot >= 0);
        var captured = manager.rewindSnapshottable().capture();
        assertEquals(2, captured.dynamicObjects().size());
        captured.dynamicObjects().forEach(entry -> assertEquals("second-strike", entry.ownerModId()));
        parent.setDestroyed(true); // Range/owner retirement, not a combat final hit.
        ally.update(1, sonic);
        assertTrue(ally.isDestroyed());
        manager.removeDynamicObject(ally);
        manager.rewindSnapshottable().restore(captured);
        // Camera belongs to the session, outside this manager-only snapshot.
        // Keep the same declared local viewport for the forward duplicate check.
        AbstractObjectInstance.updateCameraBounds(0x1d20, 0x20c, 0x1f00, 0x2ec, 0);
        var restored = manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("GuardBadnik"))
                .map(AbstractObjectInstance.class::cast).toList();
        assertEquals(1, restored.size());
        assertEquals(slot, restored.getFirst().getSlotIndex());
        var restoredCapture = manager.rewindSnapshottable().capture();
        assertEquals(captured.dynamicObjects(), restoredCapture.dynamicObjects(),
                "captured owners, object identities and every enemy scalar must restore exactly");
        assertArrayEquals(captured.usedSlotsBits(), restoredCapture.usedSlotsBits());
        assertEquals(captured.dynamicObjectIdCounter(), restoredCapture.dynamicObjectIdCounter());
        var restoredParent = manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("ArtilleryBadnik"))
                .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
        restoredParent.update(2, sonic);
        assertEquals(1, manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("GuardBadnik")).count());
        AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0);
        restored.getFirst().update(3, sonic);
        manager.removeDynamicObject(restored.getFirst());
        assertEquals(0, score.getScore());
        var replacement = enemy("GuardBadnik", "guard", 400, 200, -1);
        assertEquals(slot, replacement.getSlotIndex(), "retired companion's SST slot is available again");
    }
    @Test void colocatedPlacementDeduplicationCannotTransferCompanionLifetimeToAnotherParent() throws Exception {
        setup(0);
        AbstractObjectInstance.updateCameraBounds(0x1d20, 0x20c, 0x1f00, 0x2ec, 0);
        var original = enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c, 40);
        original.update(0, sonic);
        var ally = manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("GuardBadnik"))
                .map(AbstractObjectInstance.class::cast).findFirst().orElseThrow();
        var colocated = enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c, 40);
        colocated.update(0, sonic);
        assertEquals(1, manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("GuardBadnik")).count(),
                "placement deduplication still bounds one ally for this authored anchor");
        assertNotEquals(manager.objectQuery().identityOf(original), manager.objectQuery().identityOf(colocated));
        original.setDestroyedByOffscreen();
        assertEquals(0, ((TouchResponseProvider) ally).getCollisionFlags(),
                "same-pass authority belongs to the exact original lifetime, not another live placement match");
        manager.removeDynamicObject(original);
        ally.update(1, sonic);
        assertTrue(ally.isDestroyed());
        assertFalse(colocated.isDestroyed());
        assertEquals(0, score.getScore());
    }

}
