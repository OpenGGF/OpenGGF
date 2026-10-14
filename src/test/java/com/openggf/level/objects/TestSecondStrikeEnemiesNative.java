package com.openggf.level.objects;

import com.openggf.camera.Camera;
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
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

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

    private void setup(int act) throws Exception {
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, act).withSkippedZoneIntro().build();
        sonic = new Sonic("sonic", (short) 400, (short) 200);
        tails = new Tails("tails", (short) 400, (short) 200);
        tails.setCpuControlled(true);
        score = new GameStateManager();
        var camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 900);
        when(camera.getHeight()).thenReturn((short) 500);
        when(camera.getFocusedSprite()).thenReturn(sonic);
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
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 800);
        NativePositionOps.writeXPosPreserveSubpixel(tails, 800);
        for (int tick = 0; tick < 24; tick++) enemy.update(tick, sonic);
        attackPosition(sonic, 400, 200);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 3);
        assertTrue(enemy.isDestroyed());
        assertEquals(100, score.getScore());
        assertEquals(0x300, sonic.getYSpeed(), "native final enemy bounce subtracts $100 at/below the enemy");
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
