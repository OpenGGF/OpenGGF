package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboPresentation {
    /**
     * Declared component, not a cold route. The shipped JSR at $91518 returns to
     * $9151E; Wait stores that long at $34, and mode0 init replaces only its high
     * word. First two active velocities are $17F/$17D. Their sum $2FC plus the
     * retained $151E fraction crosses a pixel before the $100/(scale+4)=2 projection:
     * native public Y is $0C01, while a zero fraction produces $0C00.
     * No expected value calls the production integrator or reads its private state.
     */
    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "0,1", "1,1"})
    void flyByWaitResidueCarriesIntoPublicYAndReplaysFromInitAndMotion(int flags, int spot) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var manager = TestEnvironment.objectServices().objectManager();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 0, flags, false, 0));
        manager.addDynamicObject(robo);
        var identity = manager.objectQuery().identityOf(robo).orElseThrow();

        dispatchBodyAndChildren(robo, 1); // Wait submits its placeholder.
        assertEquals(0x200, robo.getX());
        assertEquals(0xC00, robo.getY());
        dispatchBodyAndChildren(robo, 2); // Visible continuation returns without init/motion.
        assertEquals(0x200, robo.getX());
        assertEquals(0xC00, robo.getY());
        assertEquals(0x7F, robo.scaleIndexForTest());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeInit = registry.capture();
        dispatchBodyAndChildren(robo, 3); // Init and first active move share the next dispatch.
        assertEquals(flags == 0 ? 0x1FE : 0x1FD, robo.getX());
        assertEquals(0xBFF, robo.getY());
        var beforeCarry = registry.capture();
        dispatchBodyAndChildren(robo, 4);
        assertEquals(flags == 0 ? 0x1FF : 0x1FD, robo.getX());
        assertEquals(0xC01, robo.getY(), "saved continuation low word carries into ordinary public Y");
        var expected = registry.capture();

        var saved = spot == 0 ? beforeInit : beforeCarry;
        for (int cycle = 0; cycle < 2; cycle++) {
            robo.setDestroyed(true); // Require actual manager reconstruction, not an in-place field reset.
            registry.restore(saved);
            assertWorldEquals(saved, registry.capture(), "fraction restore at spot " + spot + " cycle " + cycle);
            robo = (EggRoboBadnikInstance) manager.objectQuery().resolve(identity).orElseThrow();
            if (spot == 0) {
                dispatchBodyAndChildren(robo, 3);
                assertEquals(0xBFF, robo.getY());
                assertWorldEquals(beforeCarry, registry.capture(), "first motion after restored init");
            }
            dispatchBodyAndChildren(robo, 4);
            assertEquals(flags == 0 ? 0x1FF : 0x1FD, robo.getX());
            assertEquals(0xC01, robo.getY());
            assertWorldEquals(expected, registry.capture(), "fraction carry forward replay cycle " + cycle);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retirementPublishesPairingBeforeTheRequestAndDeletesEvenWhenTheFifoRejects(boolean full)
            throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var original = TestEnvironment.objectServices();
        var services = org.mockito.Mockito.spy(original);
        var physical = original.kosinskiModuleQueue();
        if (full) {
            for (int i = 0; i < 4; i++) assertTrue(physical.enqueue(original.rom(), 0x17B17E, 0xA000));
        }
        var ssz = (SszZoneRuntimeState) original.zoneRuntimeState();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 0, 0, false, 0));
        original.objectManager().addDynamicObject(robo);
        robo.setServices(services); // Registration injects the manager's ordinary services first.
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.doAnswer(call -> {
            assertTrue(ssz.eggRoboFlyByPassed(0), "native pairing write precedes request admission");
            assertFalse(robo.isDestroyed(), "native deletion follows request admission");
            calls.incrementAndGet();
            return original.runtimeArtCoordinator();
        }).when(services).runtimeArtCoordinator();
        for (int tick = 0; tick < 200 && !robo.isDestroyed(); tick++) dispatchBodyAndChildren(robo, tick);
        assertTrue(robo.isDestroyed());
        assertEquals(1, calls.get());
        assertTrue(ssz.eggRoboFlyByPassed(0));
        assertEquals(full ? 4 : 1, physical.queuedArchiveCount());
        assertEquals(full ? 0 : 1, original.hardwareTiming().pendingHandles().size());
        robo.update(201, null);
        assertEquals(1, calls.get(), "deleted owner cannot resubmit");
    }

    @org.junit.jupiter.api.Test
    void restoredUndrawnContinuationKeepsItsOverlappingPlaceholderVisibility() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x25F, 0xC00, 0xA0, 4, 0, false, 0));
        robo.setServices(services);
        manager.addDynamicObject(robo);
        robo.update(1, null);
        robo.refreshPostCameraRenderState();
        robo.update(2, null); // Restore-only dispatch: no sprite submission.
        AbstractObjectInstance.updateCameraBounds(0xE0, 0xB80, 0x220, 0xC60, 0x1000);
        robo.refreshPostCameraRenderState();
        var registry = fixture.gameplayMode().getRewindRegistry();
        int bodySlot = robo.getSlotIndex();
        var before = registry.capture();
        dispatchBodyAndChildren(robo, 16);
        assertEquals(3, robo.animalsLeftForTest(), "retained placeholder bit survives the undrawn pass");
        dispatchBodyAndChildren(robo, 32);
        assertEquals(3, robo.animalsLeftForTest(), "the following active render cleared that bit");
        var expected = registry.capture();
        robo.setDestroyed(true);
        registry.restore(before);
        assertWorldEquals(before, registry.capture(), "restore retained visibility before initialization");
        robo = manager.activeObjectsOfType(EggRoboBadnikInstance.class).stream()
                .filter(o -> o.getSlotIndex() == bodySlot).findFirst().orElseThrow();
        dispatchBodyAndChildren(robo, 16);
        assertEquals(3, robo.animalsLeftForTest());
        dispatchBodyAndChildren(robo, 32);
        assertWorldEquals(expected, registry.capture(), "retained visibility forward replay");
    }

    /** Component replay, including deliberately supplied fighter pairing; no cold-route claim. */
    @ParameterizedTest
    @ValueSource(ints = {0, 2, 4})
    void waitingContinuationAndFractionalMovementSurviveDestructiveWorldRestore(int subtype) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, subtype, 0, false, 0));
        robo.setServices(services);
        manager.addDynamicObject(robo);
        robo.update(1, null);
        robo.refreshPostCameraRenderState();
        var registry = fixture.gameplayMode().getRewindRegistry();
        int bodySlot = robo.getSlotIndex();
        int nextTick = 2;
        // Capture the retained render bit, restored-but-uninitialized continuation,
        // and active fractional position independently.
        for (int spot = 0; spot < 3; spot++) {
            int forwardFrames = spot < 2 ? 1 : 8;
            var before = registry.capture();
            for (int i = 0; i < forwardFrames; i++) dispatchBodyAndChildren(robo, nextTick + i);
            var expected = registry.capture();
            robo.setDestroyed(true);
            manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).forEach(o -> o.setDestroyed(true));
            manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).forEach(o -> o.setDestroyed(true));
            registry.restore(before);
            assertWorldEquals(before, registry.capture(), "destructive restore at spot " + spot);
            robo = manager.activeObjectsOfType(EggRoboBadnikInstance.class).stream()
                    .filter(o -> o.getSlotIndex() == bodySlot).findFirst().orElseThrow();
            for (int i = 0; i < forwardFrames; i++) dispatchBodyAndChildren(robo, nextTick + i);
            assertWorldEquals(expected, registry.capture(), "forward replay at spot " + spot);
            nextTick += forwardFrames;
            if (subtype == 4 && spot == 1) {
                // Reach launch through the actual low-nibble release gate, then retain a fractional Y.
                while (!robo.stateForTest().equals("RISING") && nextTick < 100) {
                    dispatchBodyAndChildren(robo, nextTick++);
                }
                assertEquals("RISING", robo.stateForTest());
                for (int i = 0; i < 7; i++) dispatchBodyAndChildren(robo, nextTick++);
            }
        }
    }

    private static void dispatchBodyAndChildren(EggRoboBadnikInstance robo, int tick) {
        robo.update(tick, null);
        robo.refreshPostCameraRenderState();
        var manager = TestEnvironment.objectServices().objectManager();
        manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).forEach(o -> o.update(tick, null));
        manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).forEach(o -> o.update(tick, null));
    }

    private static void assertWorldEquals(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
                    expected.get(key), actual.get(key)).isEmpty(), () -> label + " " + key + ": "
                    + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void parentRetirementSignalsBothChildrenAndRestoredGraphFollowsItsLiveOwner(int flags) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        robo.setServices(services);
        manager.addDynamicObject(robo);
        releasePlaceholder(robo);
        robo.update(0, fixture.sprite());
        var gun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
        var flame = manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst();
        gun.update(0, fixture.sprite());
        flame.update(0, fixture.sprite());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();

        // Sprite_CheckDeleteTouch -> loc_85094 publishes status bit 7 before
        // scheduling Delete_Current_Sprite; Child_Draw_Sprite reads that bit.
        manager.removeDynamicObject(robo);
        assertTrue(robo.isDestroyed(), "manager retirement must publish the native child-exit signal");
        gun.update(1, fixture.sprite());
        flame.update(1, fixture.sprite());
        assertFalse(gun.isDestroyed(), "Gun MAIN selects an undrawn next-entry delete");
        assertFalse(flame.isDestroyed(), "Jet MAIN selects an undrawn next-entry delete");
        assertTrue(draw(gun).tiles().isEmpty());
        assertTrue(draw(flame).tiles().isEmpty());
        gun.update(2, fixture.sprite());
        flame.update(2, fixture.sprite());
        assertTrue(gun.isDestroyed());
        assertTrue(flame.isDestroyed());

        registry.restore(saved);
        assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager",
                saved.get("object-manager"), registry.capture().get("object-manager")).isEmpty(),
                "recreate the live body and both exact child links");
        var restoredBody = manager.activeObjectsOfType(EggRoboBadnikInstance.class).getFirst();
        var restoredGun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
        var restoredFlame = manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst();
        restoredGun.update(1, fixture.sprite());
        restoredFlame.update(1, fixture.sprite());
        assertFalse(restoredGun.isDestroyed(), "restored gun follows the recreated live owner");
        assertFalse(restoredFlame.isDestroyed(), "restored flame follows the recreated live owner");

        manager.removeDynamicObject(restoredBody);
        var retired = registry.capture();
        restoredGun.update(2, fixture.sprite());
        restoredFlame.update(2, fixture.sprite());
        assertFalse(restoredGun.isDestroyed());
        assertFalse(restoredFlame.isDestroyed());
        assertTrue(draw(restoredGun).tiles().isEmpty());
        assertTrue(draw(restoredFlame).tiles().isEmpty());
        restoredGun.update(3, fixture.sprite());
        restoredFlame.update(3, fixture.sprite());
        assertTrue(restoredGun.isDestroyed());
        assertTrue(restoredFlame.isDestroyed());
        registry.restore(retired);
        var orphanGun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
        var orphanFlame = manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst();
        orphanGun.update(2, fixture.sprite());
        orphanFlame.update(2, fixture.sprite());
        // Deliberately unregistered before capture: neither restored child has an exact
        // parent ID or last-refresh operands. These are missing-link negative inlets.
        assertFalse(orphanGun.isDestroyed());
        assertFalse(orphanFlame.isDestroyed());
        assertTrue(draw(orphanGun).tiles().isEmpty());
        assertTrue(draw(orphanFlame).tiles().isEmpty());
        orphanGun.update(3, fixture.sprite());
        orphanFlame.update(3, fixture.sprite());
        assertTrue(orphanGun.isDestroyed(), "the pending orphan deletes on its next own entry");
        assertTrue(orphanFlame.isDestroyed(), "the pending orphan deletes on its next own entry");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void movingBodyGunAndFlameShareNativeAnchorsAndFlipsAfterRecreation(int flags) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        // loc_915F6's releaser rises and falls before entering the hover loop.
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 4, flags, false, 0));
        robo.setServices(services); manager.addDynamicObject(robo);
        releasePlaceholder(robo);
        EggRoboGunArmChildInstance gun = null;
        EggRoboJetFlameChildInstance flame = null;
        boolean moved = false;
        for (int pass = 0; pass < 220; pass++) {
            robo.update(pass, null);
            if (manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).isEmpty()) continue;
            boolean firstGunDispatch = gun == null;
            boolean firstJetDispatch = flame == null;
            gun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
            flame = manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst();
            int gunBirthX = gun.getX(), gunBirthY = gun.getY();
            int birthX = flame.getX(), birthY = flame.getY();
            gun.update(pass, null); flame.update(pass, null);
            boolean flip = flags != 0;
            if (firstGunDispatch) {
                assertEquals(gunBirthX, gun.getX(), "Gun INIT retains the table-row birth X");
                assertEquals(gunBirthY, gun.getY(), "Gun INIT does not refresh from the parent");
                assertTrue(draw(gun).tiles().isEmpty(), "loc_916EE returns without drawing");
            } else {
                assertEquals((robo.getX() + (flip ? 0x1C : -0x1C)) & 0xFFFF, gun.getX(), "sub_91930 X");
                assertFalse(draw(gun).tiles().isEmpty());
                assertTrue(draw(gun).tiles().stream().allMatch(t -> t.hFlip() == flip));
            }
            if (firstJetDispatch) {
                assertEquals(birthX, flame.getX(), "Jet INIT retains the table-row birth X");
                assertEquals(birthY, flame.getY(), "Jet INIT does not refresh from the parent");
                assertEquals(6, flame.mappingFrameForTest());
            } else {
                assertEquals((robo.getX() + (flip ? 0xC : -0xC)) & 0xFFFF, flame.getX());
                assertEquals((robo.getY() + 0x1C) & 0xFFFF, flame.getY(), "MAIN uses live word Y");
            }
            if (!firstGunDispatch && (robo.stateForTest().equals("RISING") || robo.stateForTest().equals("FALLING"))) {
                assertEquals(robo.getY() - 4, gun.getY(), "zero $32 falls back to live Y during launch");
            }
            var body = draw(robo);
            // CPU presentation emits whole tiles, while renderer FrameBounds
            // excludes transparent edge pixels. Use ROM mapping geometry here.
            var minimum = mappingMinimum(robo.mappingFrame, flip);
            assertFalse(body.tiles().isEmpty());
            assertEquals(robo.getX() + minimum[0],
                    body.tiles().stream().mapToDouble(t -> t.x()).min().orElseThrow(), "body draws at its live native X");
            assertEquals(robo.getY() + minimum[1], body.tiles().stream().mapToDouble(t -> t.y()).min().orElseThrow());
            // Jet's independently qualified first-dispatch oracle remains unchanged.
            if (firstJetDispatch) {
                assertTrue(draw(flame).tiles().isEmpty(), "loc_916A8 returns without drawing");
            } else {
                assertFalse(draw(flame).tiles().isEmpty());
                assertTrue(draw(flame).tiles().stream().allMatch(t -> t.hFlip() == flip));
            }
            moved |= robo.getX() != 0x200 && robo.getY() != 0xC00;
        }
        assertTrue(moved, "exercise actual horizontal and vertical movement");
        assertNotNull(gun); assertNotNull(flame);
        var badnikState = robo.captureRewindState().badnikExtra();
        assertEquals(robo.getX(), badnikState.currentX(), "standard badnik capture keeps live X");
        assertEquals(robo.getY(), badnikState.currentY(), "standard badnik capture keeps live Y");
        var gunFrame = draw(gun); var bodyFrame = draw(robo); var flameFrame = draw(flame);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        robo.setDestroyed(true); gun.setDestroyed(true); flame.setDestroyed(true);
        registry.restore(saved);
        assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager",
                saved.get("object-manager"), registry.capture().get("object-manager")).isEmpty(),
                "recreation preserves live position metadata as well as visible art");
        assertEquals(bodyFrame, draw(manager.activeObjectsOfType(EggRoboBadnikInstance.class).getFirst()));
        assertEquals(gunFrame, draw(manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst()));
        assertEquals(flameFrame, draw(manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).getFirst()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void gunTurnsWithTheBodyAndFiresFromTheMirroredMuzzle(int initialFlags) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, initialFlags, false, 0));
        robo.setServices(services); manager.addDynamicObject(robo);
        releasePlaceholder(robo);
        robo.update(0, null); // Real producer dispatch, followed by Gun INIT only.
        var gun = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).getFirst();
        gun.update(0, null);
        assertTrue(draw(gun).tiles().isEmpty());
        var player = fixture.sprite();
        for (int pass = 0; pass < 2; pass++) {
            int targetX = pass == initialFlags ? 0x180 : 0x280;
            com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player, targetX);
            com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, 0xB90);
            robo.update(pass + 1, player);
            gun.update(pass + 1, player);
            boolean flip = targetX > robo.getX();
            assertEquals(robo.getX() + (flip ? 0x1C : -0x1C), gun.getX());
            assertTrue(draw(gun).tiles().stream().allMatch(t -> t.hFlip() == flip));
            assertTrue(draw(robo).tiles().stream().allMatch(t -> t.hFlip() == flip));
        }
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, robo.getY());
        robo.update(3, player);
        gun.update(3, player);
        var shot = manager.activeObjectsOfType(EggRoboShotInstance.class).getFirst();
        boolean flip = !robo.badnikFacingLeft();
        assertEquals(gun.getX() + (flip ? -0xB : 0xB), shot.getX(), "CreateChild10_NormalAdjusted muzzle offset");
        assertEquals(gun.getY() - 4, shot.getY());
        for (int pass = 0; pass < 32; pass++) shot.update(pass, player);
        assertTrue(shot.armedForTest());
        assertEquals(flip ? 0x800 : -0x800, shot.xVelocityForTest());
        int origin = shot.getX(); shot.update(32, player);
        assertEquals(origin + (flip ? 8 : -8), shot.getX());
    }

    /**
     * Find_OtherObject SUB.W/BPL and Change_FlipX, with a declared fighter pairing word.
     * The literal outcomes include equality and both sides of $8000; no host unsigned ordering
     * or fitted route/frame supplies these expectations. Player Y keeps every entry in hover.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void aimUsesTheSignedNativeWordIncludingEqualityAndWrapping(int initialFlags) {
        AimFamily family = aimingComponent(initialFlags);
        int[] playerX = {0x0180, 0x0200, 0x0280, 0xFFFF, 0x81FF, 0x8200, 0x8201};
        // Native low words: 0080,0000,FF80,0201,8001,8000,7FFF respectively.
        boolean[] left = {true, true, false, true, false, false, true};
        for (int vector = 0; vector < playerX.length; vector++) {
            dispatchAim(family, playerX[vector], 0xB90, 0x100 + vector);
            assertAim(family, left[vector], "literal Player1 X=" + Integer.toHexString(playerX[vector]));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void equalXClearsAnActuallyReachedRightFacingBodyAndGun(int initialFlags) {
        AimFamily family = aimingComponent(initialFlags);
        dispatchAim(family, 0x0280, 0xB90, 1);
        assertAim(family, false, "preceding actual right aim");
        dispatchAim(family, 0x0200, 0xB90, 2);
        assertAim(family, true, "zero SUB.W clears the prior H flag");
    }

    @ParameterizedTest
    @CsvSource({"-9,false", "-8,true", "8,true", "9,false"})
    void equalXKeepsTheOriginalInclusiveYWindow(int deltaY, boolean shouldFire) {
        AimFamily family = aimingComponent(0);
        var manager = TestEnvironment.objectServices().objectManager();
        int before = manager.activeObjectsOfType(EggRoboShotInstance.class).size();
        // After real INIT's $F8 movement, the next original MoveSprite2 reaches Y=$0C01.
        // This is a public Player1 pose, not a private Body/history/timer seed.
        dispatchAim(family, 0x0200, 0xC01 + deltaY, 1);
        assertEquals(0xC01, family.body().getY(), "original swing/move precedes the Y comparison");
        assertAim(family, true, "equal X at literal Y boundary " + deltaY);
        assertEquals(shouldFire, family.body().gunArmed());
        assertEquals(before + (shouldFire ? 1 : 0),
                manager.activeObjectsOfType(EggRoboShotInstance.class).size());
        assertEquals(shouldFire ? 0x5F : 0, family.gun().cooldownForTest());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void changedAimReplaysTwiceThroughFreshWholeWorldAtTieAndWrap(int spot) {
        AimFamily family = aimingComponent(spot);
        int targetX = spot == 0 ? 0x0200 : 0xFFFF;
        int nextClock = spot == 0 ? 2 : 1;
        if (spot == 0) {
            dispatchAim(family, 0x0280, 0xB90, 1);
            assertAim(family, false, "checkpoint after real right aim");
        } else {
            // Initialized hover checkpoint before the first wrapped-X aim.
            NativePositionOps.writeXPosPreserveSubpixel(family.fixture().sprite(), targetX);
            NativePositionOps.writeYPosPreserveSubpixel(family.fixture().sprite(), 0xB90);
        }
        AimIds ids = aimIds(family);
        var registry = family.fixture().gameplayMode().getRewindRegistry();
        var before = registry.capture();
        dispatchAim(family, targetX, 0xB90, nextClock);
        assertAim(family, true, "original forward tie/wrap");
        List<SpritePresentation.Frame> expectedFrames = aimPictures(family);
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            var previous = family;
            var manager = TestEnvironment.objectServices().objectManager();
            manager.removeDynamicObject(family.gun());
            manager.removeDynamicObject(family.flame());
            manager.removeDynamicObject(family.body());
            registry.restore(before);
            family = resolveAimFamily(previous.fixture(), ids);
            assertNotSame(previous.body(), family.body());
            assertNotSame(previous.gun(), family.gun());
            assertNotSame(previous.flame(), family.flame());
            assertEquals(ids, aimIds(family), "stable exact IDs, not nearest/class/XY recovery");
            assertWorldEquals(before, registry.capture(), "fresh aim checkpoint " + spot + "/" + cycle);
            dispatchAim(family, targetX, 0xB90, nextClock);
            assertAim(family, true, "fresh forward tie/wrap");
            assertEquals(expectedFrames, aimPictures(family));
            assertWorldEquals(expected, registry.capture(), "fresh aim forward " + spot + "/" + cycle);
        }
    }

    private record AimFamily(HeadlessTestFixture fixture, EggRoboBadnikInstance body,
                             EggRoboGunArmChildInstance gun, EggRoboJetFlameChildInstance flame) { }
    private record AimIds(ObjectRefId body, ObjectRefId gun, ObjectRefId flame) { }

    /** Real placeholder -> retained render -> continuation -> INIT; only pairing is supplied. */
    private static AimFamily aimingComponent(int initialFlags) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        var manager = services.objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, initialFlags, false, 0));
        body.setServices(services);
        manager.addDynamicObject(body);
        releasePlaceholder(body);
        var priorGuns = List.copyOf(manager.activeObjectsOfType(EggRoboGunArmChildInstance.class));
        var priorFlames = List.copyOf(manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class));
        body.update(0, null);
        assertEquals(0x200, body.getX());
        assertEquals(0xC00, body.getY());
        var bornGuns = manager.activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(child -> priorGuns.stream().noneMatch(old -> old == child)).toList();
        var bornFlames = manager.activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(child -> priorFlames.stream().noneMatch(old -> old == child)).toList();
        assertEquals(1, bornGuns.size(), "this actual producer created exactly one Gun");
        assertEquals(1, bornFlames.size(), "this actual producer created exactly one Jet");
        var gun = bornGuns.getFirst();
        var flame = bornFlames.getFirst();
        gun.update(0, null);
        flame.update(0, null);
        assertTrue(draw(gun).tiles().isEmpty(), "Gun INIT has no MAIN draw");
        assertTrue(draw(flame).tiles().isEmpty(), "Jet INIT has no MAIN draw");
        return new AimFamily(fixture, body, gun, flame);
    }

    private static void dispatchAim(AimFamily family, int playerX, int playerY, int vIntRunCount) {
        var player = family.fixture().sprite();
        NativePositionOps.writeXPosPreserveSubpixel(player, playerX);
        NativePositionOps.writeYPosPreserveSubpixel(player, playerY);
        assertEquals(playerX, player.getCentreX() & 0xFFFF);
        assertEquals(playerY, player.getCentreY() & 0xFFFF);
        family.body().update(vIntRunCount, player);
        family.gun().update(vIntRunCount, player);
        family.flame().update(vIntRunCount, player);
    }

    private static void assertAim(AimFamily family, boolean left, String label) {
        assertEquals(0x200, family.body().getX(), label);
        assertEquals(left, family.body().badnikFacingLeft(), label);
        assertEquals(left ? 0x1E4 : 0x21C, family.gun().getX(), label);
        for (SpritePresentation.Frame frame : List.of(draw(family.body()), draw(family.gun()))) {
            assertFalse(frame.tiles().isEmpty(), label);
            assertTrue(frame.tiles().stream().allMatch(tile -> tile.hFlip() == !left), label);
        }
    }

    private static List<SpritePresentation.Frame> aimPictures(AimFamily family) {
        return List.of(draw(family.body()), draw(family.gun()), draw(family.flame()));
    }

    private static ObjectRefId aimId(ObjectInstance object) {
        var table = TestEnvironment.objectServices().objectManager().captureIdentityContext().requireIdentityTable();
        ObjectRefId id = table.idFor(object);
        assertNotNull(id);
        return id;
    }

    private static AimIds aimIds(AimFamily family) {
        return new AimIds(aimId(family.body()), aimId(family.gun()), aimId(family.flame()));
    }

    private static AimFamily resolveAimFamily(HeadlessTestFixture fixture, AimIds ids) {
        var table = TestEnvironment.objectServices().objectManager().captureIdentityContext().requireIdentityTable();
        return new AimFamily(fixture,
                (EggRoboBadnikInstance) table.resolveObject(ids.body(), true),
                (EggRoboGunArmChildInstance) table.resolveObject(ids.gun(), true),
                (EggRoboJetFlameChildInstance) table.resolveObject(ids.flame(), true));
    }

    /** Component setup executes the placeholder and restore dispatches without private-state writes. */
    private static void releasePlaceholder(EggRoboBadnikInstance robo) {
        robo.update(1, null);
        robo.refreshPostCameraRenderState();
        robo.update(2, null);
    }

    private static SpritePresentation.Frame draw(AbstractObjectInstance object) {
        return SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0,
                () -> object.appendRenderCommands(new java.util.ArrayList<>()));
    }

    private static int[] mappingMinimum(int frame, boolean flip) throws Exception {
        var rom = TestEnvironment.objectServices().rom();
        int base = 0x184F34; // Map_EggRobo, locked-on ROM.
        int address = base + (rom.read16BitAddr(base + frame * 2) & 0xFFFF);
        int count = rom.read16BitAddr(address) & 0xFFFF;
        byte[] pieces = rom.readBytes(address + 2, count * 6);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int offset = 0; offset < pieces.length; offset += 6) {
            int x = (short) (((pieces[offset + 4] & 255) << 8) | (pieces[offset + 5] & 255));
            int width = (((pieces[offset + 1] & 15) >> 2) + 1) * 8;
            minX = Math.min(minX, flip ? -x - width : x);
            minY = Math.min(minY, pieces[offset]);
        }
        return new int[] {minX, minY};
    }
}
