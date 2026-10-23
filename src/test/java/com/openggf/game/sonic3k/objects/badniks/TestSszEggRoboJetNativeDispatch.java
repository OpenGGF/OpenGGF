package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native loc_916A8/loc_916CC component dispatches, not a completed SSZ route.
 * Producer cases use the maintained fighter inlet with its pairing bit declared;
 * velocity/position cases explicitly supply the parent's native word operands.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboJetNativeDispatch {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void realProducerKeepsUnmirroredBirthThroughInitEvenWhenParentMoves(int flags) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        ObjectManager manager = manager();
        EggRoboBadnikInstance body = readyFighter(flags);
        int birthX = (body.getX() - 0x0C) & 0xFFFF;
        int birthY = (body.getY() + 0x1C) & 0xFFFF;

        body.update(3, null); // loc_918C4 creates Jet row zero before the body movement.
        EggRoboJetFlameChildInstance jet = jetsOf(body).getFirst();
        assertTrue(jet.getSlotIndex() > body.getSlotIndex());
        assertEquals(birthX, jet.getX());
        assertEquals(birthY, jet.getY());
        assertEquals(0, jet.mappingFrameForTest(), "the newly allocated SST has not run its attributes");
        assertEquals(0, jet.getPriorityBucket());
        assertEquals(0, jet.getOnScreenHalfWidth());
        assertEquals(0, jet.getOnScreenHalfHeight());
        assertFalse(jet.participatesInRomWorldTransitionOffset());
        assertFalse(booleanField(jet, "hFlip"), "CreateChild1_Normal does not copy render flips");
        assertTrue(draw(jet).tiles().isEmpty(), "allocation does not execute INIT");

        // Deliberate parent-operand component inlet after the actual row allocation.
        parentWords(body, 0xFFF9, 0xFFF4, 0x20);
        jet.update(4, null);
        assertEquals(birthX, jet.getX(), "INIT does not call Refresh_ChildPositionAdjusted");
        assertEquals(birthY, jet.getY());
        assertEquals(6, jet.mappingFrameForTest());
        assertEquals(5, jet.getPriorityBucket());
        assertEquals(0x0C, jet.getOnScreenHalfWidth());
        assertEquals(0x10, jet.getOnScreenHalfHeight());
        assertEquals(body.participatesInRomWorldTransitionOffset(), jet.participatesInRomWorldTransitionOffset());
        assertFalse(booleanField(jet, "hFlip"));
        assertTrue(draw(jet).tiles().isEmpty(), "INIT returns without Draw_Sprite");

        jet.update(5, null);
        assertEquals((0xFFF9 + (flags == 0 ? -0x0C : 0x0C)) & 0xFFFF, jet.getX());
        assertEquals(0x10, jet.getY(), "adjusted Y is a wrapping word addition");
        assertEquals(4, jet.mappingFrameForTest());
        assertFalse(draw(jet).tiles().isEmpty());
        assertTrue(draw(jet).tiles().stream().allMatch(tile -> tile.hFlip() == (flags != 0)));
        assertSame(body, field(jet, "parent"));
        assertTrue(manager.getActiveObjects().contains(jet));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void zeroOrOneHigherSlotKeepsTheRealChildTablePrefixWithoutRetry(int higherFree) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        ObjectManager manager = manager();
        int earlierHole = manager.allocateDynamicSlot();
        assertTrue(earlierHole >= 0);
        EggRoboBadnikInstance body = readyFighter(1);
        assertTrue(earlierHole < body.getSlotIndex());
        manager.releaseDynamicSlot(earlierHole);
        List<Integer> occupied = reserveAfter(manager, body.getSlotIndex());
        if (higherFree == 1) manager.releaseDynamicSlot(occupied.getFirst());

        body.update(3, null);
        List<EggRoboJetFlameChildInstance> prefix = jetsOf(body);
        assertEquals(higherFree, prefix.size(), "Jet is the first table row, never Gun");
        assertTrue(gunsOf(body).isEmpty(), "there is no second after-current slot");
        if (higherFree == 1) {
            assertEquals(occupied.getFirst().intValue(), prefix.getFirst().getSlotIndex());
            assertTrue(draw(prefix.getFirst()).tiles().isEmpty());
        }
        int stillFreeEarlier = manager.allocateDynamicSlot();
        assertEquals(earlierHole, stillFreeEarlier, "an earlier hole cannot satisfy AllocateObjectAfterCurrent");
        manager.releaseDynamicSlot(stillFreeEarlier);
        for (int i = higherFree; i < Math.min(occupied.size(), higherFree + 2); i++) {
            manager.releaseDynamicSlot(occupied.get(i));
        }

        body.update(4, null);
        assertEquals(prefix, jetsOf(body), "the original child-table attempt is not retried");
        assertTrue(gunsOf(body).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"-32768,6", "-1,6", "0,5", "31,5", "32,4", "32767,4"})
    void mainUsesTheSignedWordAndExactTwentyThreshold(int wordVelocity, int frame) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family family = family(0);
        family.jet().update(0, null);
        parentWords(family.body(), 2, 0xFFF0, wordVelocity);

        family.jet().update(1, null);

        assertEquals(0xFFF6, family.jet().getX());
        assertEquals(0x000C, family.jet().getY());
        assertEquals(frame, family.jet().mappingFrameForTest());
        assertFalse(draw(family.jet()).tiles().isEmpty());
    }

    @Test
    void retirementRefreshesFirstThenSelectsAnUndrawnDeleteThatReplaysTwice() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family family = family(1);
        EggRoboJetFlameChildInstance jet = family.jet();
        jet.update(0, null);
        jet.update(1, null);
        assertFalse(draw(jet).tiles().isEmpty());
        parentWords(family.body(), 0xFFF9, 0xFFF4, -1);
        manager().removeDynamicObject(family.body());
        assertTrue(family.body().isDestroyed());

        jet.update(2, null);

        assertEquals(5, jet.getX(), "refresh uses the real retired parent before Child_Draw_Sprite");
        assertEquals(0x10, jet.getY());
        assertEquals(6, jet.mappingFrameForTest(), "the final frame selection precedes status bit 7");
        assertFalse(jet.isDestroyed(), "Go_Delete_Sprite only selects the next own dispatch");
        assertTrue(booleanField(jet, "pendingDelete"));
        assertNull(field(jet, "parent"), "the selected delete no longer uses a raw parent");
        assertTrue(draw(jet).tiles().isEmpty());
        var registry = fixture.gameplayMode().getRewindRegistry();
        ObjectRefId jetId = id(jet);
        var pending = registry.capture(); // Registered closed boundary after the real final refresh.
        jet.update(3, null);
        assertTrue(jet.isDestroyed());
        assertTrue(draw(jet).tiles().isEmpty());

        for (int cycle = 0; cycle < 2; cycle++) {
            manager().removeDynamicObject(jet);
            registry.restore(pending);
            var restored = (EggRoboJetFlameChildInstance) resolve(jetId);
            assertNotSame(jet, restored, "restore must exercise the pure shell");
            assertNull(field(restored, "parent"));
            assertTrue(booleanField(restored, "pendingDelete"));
            assertEquals(5, restored.getX());
            assertEquals(0x10, restored.getY());
            assertEquals(6, restored.mappingFrameForTest());
            assertTrue(draw(restored).tiles().isEmpty());
            assertWorldEquals(pending.get("object-manager"), registry.capture().get("object-manager"));
            restored.update(3, null);
            assertTrue(restored.isDestroyed());
            assertTrue(draw(restored).tiles().isEmpty());
            jet = restored;
        }
    }

    @Test
    void equalCoordinateFamiliesKeepExactOwnersThroughTwoFreshForwardCycles() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        Family first = family(0), second = family(1);
        ObjectRefId bodyAId = id(first.body()), bodyBId = id(second.body());
        ObjectRefId jetAId = id(first.jet()), jetBId = id(second.jet());
        assertNotEquals(bodyAId, bodyBId);
        assertNotEquals(jetAId, jetBId);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var initial = registry.capture(); // Both actual Jet INIT entries are still pending.
        List<JetStep> expected = forward(first, second);
        var expectedWorld = registry.capture().get("object-manager");

        for (int cycle = 0; cycle < 2; cycle++) {
            for (ObjectInstance object : List.of(resolve(jetAId), resolve(jetBId),
                    resolve(bodyAId), resolve(bodyBId))) {
                manager().removeDynamicObject(object);
            }
            registry.restore(initial);
            Family restoredA = new Family((EggRoboBadnikInstance) resolve(bodyAId),
                    (EggRoboJetFlameChildInstance) resolve(jetAId));
            Family restoredB = new Family((EggRoboBadnikInstance) resolve(bodyBId),
                    (EggRoboJetFlameChildInstance) resolve(jetBId));
            assertNotSame(first.jet(), restoredA.jet());
            assertNotSame(second.jet(), restoredB.jet());
            assertSame(restoredA.body(), field(restoredA.jet(), "parent"));
            assertSame(restoredB.body(), field(restoredB.jet(), "parent"));
            assertNotSame(field(restoredA.jet(), "parent"), field(restoredB.jet(), "parent"));
            assertWorldEquals(initial.get("object-manager"), registry.capture().get("object-manager"));
            assertEquals(expected, forward(restoredA, restoredB));
            assertWorldEquals(expectedWorld, registry.capture().get("object-manager"));
        }
    }

    private static List<JetStep> forward(Family first, Family second) {
        List<JetStep> steps = new ArrayList<>();
        for (int tick = 0; tick < 3; tick++) {
            parentWords(first.body(), 0x300 + tick, 0xC00 + tick, -1);
            parentWords(second.body(), 0x300 + tick, 0xC00 + tick, 0x20);
            first.jet().update(tick, null);
            second.jet().update(tick, null);
            steps.add(step(first.jet()));
            steps.add(step(second.jet()));
        }
        assertEquals(6, steps.get(0).frame());
        assertEquals(6, steps.get(1).frame());
        assertTrue(steps.get(0).presentation().tiles().isEmpty());
        assertTrue(steps.get(1).presentation().tiles().isEmpty());
        assertEquals(6, steps.get(2).frame());
        assertEquals(4, steps.get(3).frame());
        return List.copyOf(steps);
    }

    private record Family(EggRoboBadnikInstance body, EggRoboJetFlameChildInstance jet) { }
    private record JetStep(int x, int y, int frame, SpritePresentation.Frame presentation) { }

    private static JetStep step(EggRoboJetFlameChildInstance jet) {
        return new JetStep(jet.getX(), jet.getY(), jet.mappingFrameForTest(), draw(jet));
    }

    private static Family family(int flags) {
        var services = TestEnvironment.objectServices();
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        body.setServices(services);
        manager().addDynamicObject(body);
        // Declared row-allocation component inlet; real table allocation is checked separately.
        var jet = new EggRoboJetFlameChildInstance(new ObjectSpawn(
                0x1F4, 0xC1C, 0, 0, flags, false, 0), body);
        jet.setServices(services);
        manager().addDynamicObjectAfterSlot(jet, body.getSlotIndex());
        return new Family(body, jet);
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

    private static ObjectManager manager() {
        return TestEnvironment.objectServices().objectManager();
    }

    private static List<EggRoboJetFlameChildInstance> jetsOf(EggRoboBadnikInstance body) {
        return manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(jet -> field(jet, "parent") == body).toList();
    }

    private static List<EggRoboGunArmChildInstance> gunsOf(EggRoboBadnikInstance body) {
        return manager().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(gun -> field(gun, "parent") == body).toList();
    }

    private static List<Integer> reserveAfter(ObjectManager manager, int slot) {
        List<Integer> result = new ArrayList<>();
        for (int next; (next = manager.allocateSlotAfter(slot)) >= 0;) result.add(next);
        assertFalse(result.isEmpty());
        return result;
    }

    private static void parentWords(EggRoboBadnikInstance body, int x, int y, int velocity) {
        setInt(body, "x", x);
        setInt(body, "y", y);
        setInt(body, "yVelocity", (short) velocity);
    }

    private static ObjectRefId id(ObjectInstance object) {
        ObjectRefId id = manager().captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id);
        return id;
    }

    private static ObjectInstance resolve(ObjectRefId id) {
        return manager().captureIdentityContext().requireIdentityTable().resolveObject(id, true);
    }

    private static void assertWorldEquals(Object expected, Object actual) {
        assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager",
                expected, actual).isEmpty(),
                () -> com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager", expected, actual).toString());
    }

    private static SpritePresentation.Frame draw(AbstractObjectInstance object) {
        return SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0,
                () -> object.appendRenderCommands(new ArrayList<>()));
    }

    private static Object field(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static boolean booleanField(Object object, String name) {
        return (boolean) field(object, name);
    }

    private static void setInt(Object object, String name, int value) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(object, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
