package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * loc_916EE/loc_91712/loc_9173A component boundaries, not a completed SSZ route.
 * Producer cases declare the fighter pairing bit. Parent-word cases declare their supplied
 * native operands; missing links never substitute for an actual retired parent's last work.
 * Art priority, vertical flip, Shot-parent lifetime and body aiming are separate contracts.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboGunNativeDispatch {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void realProducerKeepsTheUnmirroredSecondRowThroughAnUndrawnInit(int flags) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        EggRoboBadnikInstance body = readyFighter(flags);
        int birthX = (body.getX() - 0x1C) & 0xFFFF;
        int birthY = (body.getY() - 4) & 0xFFFF;
        body.update(3, null);
        EggRoboGunArmChildInstance gun = gunsOf(body).getFirst();
        EggRoboJetFlameChildInstance jet = jetsOf(body).getFirst();
        assertTrue(body.getSlotIndex() < jet.getSlotIndex());
        assertTrue(jet.getSlotIndex() < gun.getSlotIndex());
        assertEquals(birthX, gun.getX());
        assertEquals(birthY, gun.getY());
        assertEquals(0, gun.getSpawn().renderFlags());
        assertEquals("INIT", phase(gun));
        assertEquals(0, intField(gun, "mappingFrame"));
        assertEquals(0, gun.cooldownForTest());
        assertEquals(0, gun.getPriorityBucket());
        assertEquals(0, gun.getOnScreenHalfWidth());
        assertEquals(0, gun.getOnScreenHalfHeight());
        assertFalse(gun.participatesInRomWorldTransitionOffset());
        assertFalse(booleanField(gun, "hFlip"));
        assertTrue(draw(gun).tiles().isEmpty());

        // Actual row allocation above; supplied parent words below isolate the INIT return.
        parentWords(body, 0xFFF9, 0xFFF4, 0);
        set(body, "gunArmed", true);
        int shots = shotCount();
        gun.update(4, null);
        assertEquals(birthX, gun.getX());
        assertEquals(birthY, gun.getY());
        assertEquals("MAIN", phase(gun));
        assertEquals(2, intField(gun, "mappingFrame"));
        assertEquals(5, gun.getPriorityBucket());
        assertEquals(0x10, gun.getOnScreenHalfWidth());
        assertEquals(0x0C, gun.getOnScreenHalfHeight());
        assertEquals(body.participatesInRomWorldTransitionOffset(), gun.participatesInRomWorldTransitionOffset());
        assertFalse(booleanField(gun, "hFlip"));
        assertEquals(shots, shotCount(), "INIT does not test the already armed request");
        assertTrue(draw(gun).tiles().isEmpty());

        gun.update(5, null);
        assertEquals((0xFFF9 + (flags == 0 ? -0x1C : 0x1C)) & 0xFFFF, gun.getX());
        assertEquals(0xFFF0, gun.getY());
        assertEquals("COOLDOWN", phase(gun));
        assertEquals(0x5F, gun.cooldownForTest());
        assertEquals(shots + 1, shotCount());
        assertFalse(draw(gun).tiles().isEmpty());
        assertTrue(draw(gun).tiles().stream().allMatch(tile -> tile.hFlip() == (flags != 0)));
        assertSame(body, field(gun, "parent"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initInheritsTheActualCoordinateBitWithoutAnEarlyRetiredParentTest(boolean worldPositioned) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family family = family(1);
        EggRoboGunArmChildInstance gun = family.gun();
        int birthX = gun.getX(), birthY = gun.getY();
        set(family.body(), "romWorldPositioned", worldPositioned);
        manager().removeDynamicObject(family.body());
        assertTrue(family.body().isDestroyed());
        gun.update(0, null);
        assertEquals("MAIN", phase(gun), "INIT returns before Child_Draw_Sprite checks status bit 7");
        assertFalse(gun.isDestroyed());
        assertEquals(worldPositioned, gun.participatesInRomWorldTransitionOffset());
        assertEquals(birthX, gun.getX());
        assertEquals(birthY, gun.getY());
        assertTrue(draw(gun).tiles().isEmpty());
        gun.update(1, null);
        assertPending(gun);
        assertNull(field(gun, "parent"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void zeroOneOrTwoHigherSlotsKeepTheNativeTablePrefixWithoutRetry(int higherFree) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        int earlierHole = manager().allocateDynamicSlot();
        assertTrue(earlierHole >= 0);
        EggRoboBadnikInstance body = readyFighter(1);
        assertTrue(earlierHole < body.getSlotIndex());
        manager().releaseDynamicSlot(earlierHole);
        List<Integer> reserved = reserveAfter(body.getSlotIndex());
        assertTrue(reserved.size() >= 2);
        for (int i = 0; i < higherFree; i++) manager().releaseDynamicSlot(reserved.get(i));

        body.update(3, null);
        List<EggRoboJetFlameChildInstance> jets = jetsOf(body);
        List<EggRoboGunArmChildInstance> guns = gunsOf(body);
        assertEquals(higherFree == 0 ? 0 : 1, jets.size());
        assertEquals(higherFree == 2 ? 1 : 0, guns.size());
        if (!jets.isEmpty()) assertEquals(reserved.get(0).intValue(), jets.getFirst().getSlotIndex());
        if (!guns.isEmpty()) {
            assertEquals(reserved.get(1).intValue(), guns.getFirst().getSlotIndex());
            assertEquals("INIT", phase(guns.getFirst()));
            assertTrue(draw(guns.getFirst()).tiles().isEmpty());
        }
        int stillFreeEarlier = manager().allocateDynamicSlot();
        assertEquals(earlierHole, stillFreeEarlier);
        manager().releaseDynamicSlot(stillFreeEarlier);
        for (int i = higherFree; i < Math.min(reserved.size(), higherFree + 2); i++) {
            manager().releaseDynamicSlot(reserved.get(i));
        }
        body.update(4, null);
        assertEquals(jets, jetsOf(body));
        assertEquals(guns, gunsOf(body), "the original table allocation is not retried");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void mainReadsDelayedYThenTheCooldownLatchesUntilTheLiteralUnderflow(int flags) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family family = family(flags);
        EggRoboBadnikInstance body = family.body();
        EggRoboGunArmChildInstance gun = family.gun();
        gun.update(0, null);
        parentWords(body, 0x300, 0xC40, 0xC10);
        gun.update(1, null);
        assertEquals(0xC0C, gun.getY(), "nonzero $32 supplies the delayed anchor");
        parentWords(body, 0x310, 0xC60, 0);
        gun.update(2, null);
        assertEquals(0xC5C, gun.getY(), "zero $32 alone falls back to live Y");
        set(body, "gunArmed", true);
        int shots = shotCount();
        gun.update(3, null);
        assertEquals(shots + 1, shotCount());
        int latchedX = gun.getX(), latchedY = gun.getY();
        boolean latchedFlip = booleanField(gun, "hFlip");
        parentWords(body, 0x440, 0xD40, 0xD10);
        set(body, "facingLeft", flags != 0);
        for (int step = 1; step <= 0x5F; step++) {
            int remaining = 0x5F - step;
            gun.update(0x100 + step, null);
            assertEquals(remaining, gun.cooldownForTest());
            assertEquals("COOLDOWN", phase(gun));
            assertEquals(latchedX, gun.getX());
            assertEquals(latchedY, gun.getY());
            assertEquals(latchedFlip, booleanField(gun, "hFlip"));
            assertTrue(body.gunArmed());
            assertEquals(shots + 1, shotCount());
        }
        gun.update(0x200, null);
        assertEquals(-1, gun.cooldownForTest());
        assertEquals("MAIN", phase(gun));
        assertFalse(body.gunArmed());
        assertEquals(latchedX, gun.getX(), "underflow does not run MAIN in the same entry");
        assertEquals(latchedY, gun.getY());
        gun.update(0x201, null);
        assertEquals((0x440 + (flags == 0 ? 0x1C : -0x1C)) & 0xFFFF, gun.getX());
        assertEquals(0xD0C, gun.getY());
        assertEquals(shots + 1, shotCount());
        assertFalse(draw(gun).tiles().isEmpty());
    }

    @Test
    void failedShotAllocationStillStartsTheCooldownAndDoesNotRetryWhenASlotFrees() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family family = family(0);
        EggRoboGunArmChildInstance gun = family.gun();
        gun.update(0, null);
        List<Integer> reserved = reserveAfter(gun.getSlotIndex());
        set(family.body(), "gunArmed", true);
        int shots = shotCount();
        gun.update(1, null);
        assertEquals("COOLDOWN", phase(gun));
        assertEquals(0x5F, gun.cooldownForTest());
        assertEquals(shots, shotCount());
        manager().releaseDynamicSlot(reserved.getFirst());
        for (int i = 0; i < 96; i++) gun.update(2 + i, null);
        assertEquals("MAIN", phase(gun));
        assertEquals(-1, gun.cooldownForTest());
        assertFalse(family.body().gunArmed());
        gun.update(98, null);
        assertEquals(shots, shotCount(), "no allocation retry or second armed request");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void retiredParentStillSuppliesMainRefreshAndFireBeforePendingDeleteReplaysTwice(int flags) {
        var fixture = replayFixture();
        Family family = family(flags);
        EggRoboGunArmChildInstance gun = family.gun();
        gun.update(0, null);
        gun.update(1, null);
        parentWords(family.body(), 0xFFF9, 0xFFF4, 0x1234);
        set(family.body(), "gunArmed", true);
        int shots = shotCount();
        ObjectRefId gunId = id(gun);
        manager().removeDynamicObject(family.body());
        assertTrue(family.body().isDestroyed());
        gun.update(2, null);
        assertEquals((0xFFF9 + (flags == 0 ? -0x1C : 0x1C)) & 0xFFFF, gun.getX());
        assertEquals(0x1230, gun.getY());
        assertEquals(shots + 1, shotCount(), "the actual fire attempt precedes the parent-tail test");
        assertEquals(0x5F, gun.cooldownForTest());
        assertPending(gun);
        assertNull(field(gun, "parent"), "clear the unused link only after the real final work");
        // Declared manual dispatch boundary: complete this newborn's one native same-pass tail
        // before checkpointing, without a second ObjectManager-driven child entry.
        EggRoboGunArmChildInstance firingGun = gun;
        var newborn = manager().activeObjectsOfType(EggRoboShotInstance.class).stream()
                .filter(candidate -> field(candidate, "chargingGun") == firingGun).findFirst().orElseThrow();
        newborn.update(2, null);
        assertEquals(30, newborn.chargeForTest());
        assertTrue(booleanField(newborn, "pendingDelete"));
        assertFalse(newborn.publishesTouchResponseListEntryThisFrame());
        assertTrue(draw(newborn).tiles().isEmpty());
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot pending = registry.capture();
        gun.update(3, null);
        assertTrue(gun.isDestroyed());
        CompositeSnapshot deleted = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            manager().removeDynamicObject(gun);
            registry.restore(pending);
            var restored = (EggRoboGunArmChildInstance) resolve(gunId);
            assertNotSame(gun, restored);
            assertPending(restored);
            assertNull(field(restored, "parent"));
            assertEquals(0x1230, restored.getY());
            assertWorldEquals(pending, registry.capture(), "pending restore " + cycle);
            restored.update(3, null);
            assertTrue(restored.isDestroyed());
            assertTrue(draw(restored).tiles().isEmpty());
            assertWorldEquals(deleted, registry.capture(), "pending forward " + cycle);
            gun = restored;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 95})
    void retiredParentIsCheckedAfterCooldownWorkIncludingRequestClear(int priorCooldownSteps) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family family = family(0);
        EggRoboGunArmChildInstance gun = family.gun();
        gun.update(0, null);
        set(family.body(), "gunArmed", true);
        gun.update(1, null);
        int latchedX = gun.getX(), latchedY = gun.getY();
        for (int i = 0; i < priorCooldownSteps; i++) gun.update(2 + i, null);
        assertEquals(0x5F - priorCooldownSteps, gun.cooldownForTest());
        parentWords(family.body(), 0x700, 0xE00, 0xD00);
        manager().removeDynamicObject(family.body());
        gun.update(100, null);
        assertEquals(0x5E - priorCooldownSteps, gun.cooldownForTest());
        assertEquals(priorCooldownSteps != 95, family.body().gunArmed(),
                "underflow clears the actual retired parent's request before the tail");
        assertEquals(latchedX, gun.getX());
        assertEquals(latchedY, gun.getY());
        assertPending(gun);
        assertNull(field(gun, "parent"));
        gun.update(101, null);
        assertTrue(gun.isDestroyed());
    }

    @Test
    void aMissingOwnerCannotInventTheRetiredParentsLastOperands() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var gun = new EggRoboGunArmChildInstance(new ObjectSpawn(0x123, 0x456, 0, 0, 1, false, 0));
        gun.setServices(TestEnvironment.objectServices());
        manager().addDynamicObject(gun);
        int shots = shotCount();
        gun.update(0, null);
        assertEquals("MAIN", phase(gun));
        assertFalse(gun.participatesInRomWorldTransitionOffset(), "unknown parent bit is not borrowed");
        assertFalse(booleanField(gun, "hFlip"));
        assertTrue(draw(gun).tiles().isEmpty());
        gun.update(1, null);
        assertPending(gun);
        assertEquals(0x123, gun.getX());
        assertEquals(0x456, gun.getY());
        assertEquals(0, gun.cooldownForTest(), "no fabricated decrement or request-clear operand");
        assertEquals(shots, shotCount());
        gun.update(2, null);
        assertTrue(gun.isDestroyed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void equalCoordinateFamiliesReplayRawInitCooldownAndUnderflowTwice(int boundary) {
        var fixture = replayFixture();
        Family first = family(0), second = family(1);
        ObjectRefId bodyA = id(first.body()), bodyB = id(second.body());
        ObjectRefId gunA = id(first.gun()), gunB = id(second.gun());
        assertNotEquals(bodyA, bodyB);
        assertNotEquals(gunA, gunB);
        if (boundary >= 1) {
            first.gun().update(0, null);
            second.gun().update(0, null);
        }
        if (boundary >= 2) {
            set(first.body(), "gunArmed", true);
            set(second.body(), "gunArmed", true);
            first.gun().update(1, null);
            second.gun().update(1, null);
        }
        if (boundary == 3) {
            for (int i = 0; i < 95; i++) {
                first.gun().update(2 + i, null);
                second.gun().update(2 + i, null);
            }
            assertEquals(0, first.gun().cooldownForTest());
            assertEquals(0, second.gun().cooldownForTest());
        }
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        List<GunStep> expected = forward(first, second);
        CompositeSnapshot after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            for (ObjectInstance object : List.of(resolve(gunA), resolve(gunB), resolve(bodyA), resolve(bodyB))) {
                manager().removeDynamicObject(object);
            }
            registry.restore(before);
            Family restoredA = new Family((EggRoboBadnikInstance) resolve(bodyA),
                    (EggRoboGunArmChildInstance) resolve(gunA));
            Family restoredB = new Family((EggRoboBadnikInstance) resolve(bodyB),
                    (EggRoboGunArmChildInstance) resolve(gunB));
            assertNotSame(first.gun(), restoredA.gun());
            assertNotSame(second.gun(), restoredB.gun());
            assertNotSame(first.body(), restoredA.body());
            assertNotSame(second.body(), restoredB.body());
            assertSame(restoredA.body(), field(restoredA.gun(), "parent"));
            assertSame(restoredB.body(), field(restoredB.gun(), "parent"));
            assertNotSame(field(restoredA.gun(), "parent"), field(restoredB.gun(), "parent"));
            assertWorldEquals(before, registry.capture(), "fresh boundary " + boundary + " cycle " + cycle);
            assertEquals(expected, forward(restoredA, restoredB));
            assertWorldEquals(after, registry.capture(), "forward boundary " + boundary + " cycle " + cycle);
            first = restoredA;
            second = restoredB;
        }
    }

    private record Family(EggRoboBadnikInstance body, EggRoboGunArmChildInstance gun) { }
    private record GunStep(int x, int y, int frame, String phase, int cooldown, boolean hFlip,
                           ObjectRefId parentId, SpritePresentation.Frame presentation) { }

    private static List<GunStep> forward(Family first, Family second) {
        List<GunStep> steps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            parentWords(first.body(), 0x300 + i, 0xC40 + i, 0xC10 + i);
            parentWords(second.body(), 0x300 + i, 0xC40 + i, 0xC10 + i);
            first.gun().update(0x100 + i, null);
            second.gun().update(0x100 + i, null);
            steps.add(step(first.gun()));
            steps.add(step(second.gun()));
        }
        return List.copyOf(steps);
    }

    private static GunStep step(EggRoboGunArmChildInstance gun) {
        return new GunStep(gun.getX(), gun.getY(), intField(gun, "mappingFrame"), phase(gun),
                gun.cooldownForTest(), booleanField(gun, "hFlip"),
                manager().captureIdentityContext().requireIdentityTable().idFor((ObjectInstance) field(gun, "parent")),
                draw(gun));
    }

    private static HeadlessTestFixture replayFixture() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        // Ordinary SSZ boot before these component checkpoints; no cold-load parity claim.
        fixture.stepIdleFrames(2);
        manager().setRewindInPlaceRestoreEnabledForTest(false);
        return fixture;
    }

    private static Family family(int flags) {
        EggRoboBadnikInstance body = readyFighter(flags);
        body.update(3, null);
        return new Family(body, gunsOf(body).getFirst());
    }

    private static EggRoboBadnikInstance readyFighter(int flags) {
        var services = TestEnvironment.objectServices();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        body.setServices(services);
        manager().addDynamicObject(body);
        body.update(1, null);
        body.refreshPostCameraRenderState();
        body.update(2, null);
        return body;
    }

    private static void parentWords(EggRoboBadnikInstance body, int x, int y, int delayedY) {
        set(body, "x", x);
        set(body, "y", y);
        set(body, "previousPreviousY", delayedY);
    }

    private static ObjectManager manager() { return TestEnvironment.objectServices().objectManager(); }
    private static int shotCount() { return manager().activeObjectsOfType(EggRoboShotInstance.class).size(); }
    private static String phase(EggRoboGunArmChildInstance gun) { return field(gun, "phase").toString(); }
    private static int intField(Object object, String name) { return (int) field(object, name); }
    private static boolean booleanField(Object object, String name) { return (boolean) field(object, name); }

    private static List<EggRoboGunArmChildInstance> gunsOf(EggRoboBadnikInstance body) {
        return manager().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(gun -> field(gun, "parent") == body).toList();
    }

    private static List<EggRoboJetFlameChildInstance> jetsOf(EggRoboBadnikInstance body) {
        return manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(jet -> field(jet, "parent") == body).toList();
    }

    private static List<Integer> reserveAfter(int slot) {
        List<Integer> slots = new ArrayList<>();
        for (int next; (next = manager().allocateSlotAfter(slot)) >= 0;) slots.add(next);
        assertFalse(slots.isEmpty());
        return slots;
    }

    private static ObjectRefId id(ObjectInstance object) {
        ObjectRefId id = manager().captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id);
        return id;
    }

    private static ObjectInstance resolve(ObjectRefId id) {
        return manager().captureIdentityContext().requireIdentityTable().resolveObject(id, true);
    }

    private static void assertPending(EggRoboGunArmChildInstance gun) {
        assertEquals("DELETE", phase(gun));
        assertFalse(gun.isDestroyed(), "the selected delete does not execute in this dispatch");
        assertTrue(draw(gun).tiles().isEmpty());
    }

    private static void assertWorldEquals(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static SpritePresentation.Frame draw(AbstractObjectInstance object) {
        return SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0,
                () -> object.appendRenderCommands(new ArrayList<>()));
    }

    private static Field declaredField(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Native operands inherited by a badnik keep their actual declaring owner.
            }
        }
        throw new AssertionError("Missing field " + name);
    }

    private static Object field(Object object, String name) {
        try {
            return declaredField(object, name).get(object);
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        }
    }

    private static void set(Object object, String name, Object value) {
        try {
            declaredField(object, name).set(object, value);
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        }
    }
}
