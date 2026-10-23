package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * loc_91756/loc_9176C/loc_917AE/loc_917B4 and Child_DrawTouch_Sprite.
 * Actual producer allocation and exact identities are separate from the declared fighter-pairing,
 * request-word and manual routine-entry component inlets. The defeat callback is a production
 * retirement owner, not proof that an ordinary route selected a particular player collision.
 * Art priority, vertical flip, aim, raw Shot attributes and generic manager unload are separate.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboShotNativeLifetime {
    @Test
    void lockedOnRomOwnsTheShotAttributesAndSingleAdjustedChildRow() throws Exception {
        fixture();
        var rom = TestEnvironment.objectServices().rom();
        assertArrayEquals(new byte[] {2, (byte) 0x80, 0x20, 4, 7, 0},
                rom.readBytes(0x919CA, 6), "word_919CA");
        assertArrayEquals(new byte[] {0, 0, 0, 9, 0x17, 0x56, 0x0B, (byte) 0xFC},
                rom.readBytes(0x919DE, 8), "ChildObjDat_919DE, not a fitted muzzle row");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void productionFiringBindsTheImmediateGunAndChargeUsesVintButCountsOwnEntries(int flags) {
        var fixture = fixture();
        Family family = family(flags);
        armThroughBody(fixture, family);
        family.gun().update(4, fixture.sprite());
        EggRoboShotInstance shot = shotOf(family.gun());
        assertSame(family.gun(), field(shot, "chargingGun"));
        assertTrue(bool(shot, "chargingGunBound"));
        assertEquals(id(family.gun()), id((ObjectInstance) field(shot, "chargingGun")));
        assertTrue(family.gun().getSlotIndex() < shot.getSlotIndex());
        boolean left = family.body().badnikFacingLeft();
        assertEquals((family.gun().getX() + (left ? 0xB : -0xB)) & 0xFFFF, shot.getX());
        assertEquals((family.gun().getY() - 4) & 0xFFFF, shot.getY());
        int birthX = shot.getX(), birthY = shot.getY();
        List<SfxCall> sounds = recordSounds(shot);
        assertEquals(31, shot.chargeForTest());

        for (int entry = 0; entry < 32; entry++) {
            int vint = clock(entry);
            shot.update(vint, null);
            assertEquals(30 - entry, shot.chargeForTest(),
                    "one word decrement per own routine, independent of V-int delta");
            assertEquals(entry == 31 ? 7 : ((vint & 1) != 0 ? 7 : 0), shot.mappingFrameForTest());
            assertEquals(birthX, shot.getX(), "arming does not run MoveSprite2 in this entry");
            assertEquals(birthY, shot.getY());
            assertEquals(entry == 31 ? 0x9C : 0, shot.getCollisionFlags());
            assertEquals(entry == 31 ? 1 : 0, sounds.size());
        }
        assertEquals(List.of(new SfxCall(Sonic3kSfx.LASER.id, false, -1, true)), sounds);
        assertFalse(bool(shot, "pendingDelete"));
        assertNull(field(shot, "chargingGun"), "moving routine no longer reads parent3");
        assertFalse(bool(shot, "chargingGunBound"));
        assertFalse(draw(shot).tiles().isEmpty());
        assertEquals(List.of(shot), completedPublication(shot), "actual list builder accepts live tail");
        shot.update(clock(32), null);
        assertEquals((birthX + (left ? -8 : 8)) & 0xFFFF, shot.getX());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,15", "0,31", "1,0", "1,15", "1,31"})
    void pendingGunIsCheckedAfterChargeWorkIncludingTheArmingSfx(int flags, int priorEntries) {
        fixture();
        Family family = firedFamily(flags);
        EggRoboShotInstance shot = shotOf(family.gun());
        List<SfxCall> sounds = recordSounds(shot);
        for (int entry = 0; entry < priorEntries; entry++) shot.update(clock(entry), null);
        assertEquals(List.of(shot), completedPublication(shot));
        retireBodyAndRunChildTails(family);
        assertTrue(family.gun().hasNativeRetirementStatus());
        assertFalse(family.gun().isDestroyed(), "Gun's native status7 precedes physical deletion");
        int x = shot.getX(), y = shot.getY();
        shot.update(0x5123, null);
        assertEquals(30 - priorEntries, shot.chargeForTest());
        assertEquals(7, shot.mappingFrameForTest(), "the supplied V-int is odd, including arming");
        assertEquals(priorEntries == 31 ? 0x9C : 0, shot.getCollisionFlags(),
                "pending list suppression does not rewrite the native arming collision byte");
        if (priorEntries == 31) {
            assertEquals(List.of(new SfxCall(Sonic3kSfx.LASER.id, false, -1, true)), sounds,
                    "SFX still precedes Child_DrawTouch's parent test");
        } else {
            assertTrue(sounds.isEmpty());
        }
        assertPending(shot);
        assertTrue(completedPublication(shot).isEmpty(), "actual list builder rejects pending tail");
        int charge = shot.chargeForTest(), velocity = shot.xVelocityForTest();
        shot.update(0x7000, null);
        assertTrue(shot.isDestroyed());
        assertEquals(charge, shot.chargeForTest(), "selected delete does not decrement or arm again");
        assertEquals(velocity, shot.xVelocityForTest());
        assertEquals(x, shot.getX());
        assertEquals(y, shot.getY());
        assertTrue(draw(shot).tiles().isEmpty());
        assertTrue(completedPublication(shot).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31})
    void anActualLatchedRetiredGunIsDistinctFromAMissingOwner(int priorEntries) {
        fixture();
        Family family = firedFamily(0);
        EggRoboShotInstance shot = shotOf(family.gun());
        for (int entry = 0; entry < priorEntries; entry++) shot.update(clock(entry), null);
        // Declared physical-retirement component inlet; this is not native SST-slot reuse.
        ObjectLifetimeOps.deleteNoRespawn(family.gun());
        assertTrue(family.gun().isDestroyed());
        assertSame(family.gun(), field(shot, "chargingGun"));
        shot.update(1, null);
        assertEquals(30 - priorEntries, shot.chargeForTest());
        assertPending(shot);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void productionDefeatThenOrdinaryManagerPassCreatesAndRetiresShotInTheSamePass(int flags) {
        var fixture = fixture();
        Family family = family(flags);
        armThroughBody(fixture, family);
        family.body().onPlayerAttack(null, null); // Real destruction owner, not a private status seed.
        assertTrue(family.body().isDestroyed());
        List<EggRoboShotInstance> shotsBefore = manager().activeObjectsOfType(EggRoboShotInstance.class);

        manager().update(0x100, null, List.of(), 0, false);

        var newborns = manager().activeObjectsOfType(EggRoboShotInstance.class);
        assertEquals(shotsBefore.size() + 1, newborns.size());
        EggRoboShotInstance shot = newborns.stream()
                .filter(candidate -> shotsBefore.stream().noneMatch(old -> old == candidate))
                .findFirst().orElseThrow();
        assertTrue(family.gun().getSlotIndex() < shot.getSlotIndex());
        assertTrue(family.gun().hasNativeRetirementStatus());
        assertFalse(family.gun().isDestroyed());
        assertEquals(30, shot.chargeForTest(),
                "after-current Shot ran exactly once later in this actual object loop");
        assertPending(shot);
        ObjectRefId shotId = id(shot);
        var list = manager().rewindSnapshottable().capture().collisionResponseState();
        assertFalse(list.previousCollisionObjectIds().contains(shotId));
        assertFalse(list.currentCollisionBuildObjectIds().contains(shotId));
        manager().update(0x100, null, List.of(), 1, false);
        assertTrue(family.gun().isDestroyed());
        assertTrue(shot.isDestroyed());
        assertFalse(manager().getActiveObjects().contains(shot));
    }

    @Test
    void movingShotDoesNotKeepConsultingTheRetiredGun() {
        fixture();
        Family family = firedFamily(0);
        EggRoboShotInstance shot = shotOf(family.gun());
        for (int entry = 0; entry < 32; entry++) shot.update(clock(entry), null);
        assertNull(field(shot, "chargingGun"));
        assertFalse(bool(shot, "chargingGunBound"));
        retireBodyAndRunChildTails(family);
        int x = shot.getX(), velocity = shot.xVelocityForTest();
        shot.update(0x7FFF, null);
        assertEquals((x + (velocity >> 8)) & 0xFFFF, shot.getX());
        assertFalse(shot.isDestroyed());
        assertFalse(bool(shot, "pendingDelete"));
        assertFalse(draw(shot).tiles().isEmpty());
        assertEquals(List.of(shot), completedPublication(shot));
    }

    @Test
    void boundMissingOwnerFailsClosedWithoutChoosingAnEqualCoordinateGun() {
        fixture();
        Family other = family(0);
        var shot = EggRoboShotInstance.gunShot(
                new ObjectSpawn(other.gun().getX(), other.gun().getY(), 0, 0, 0, false, 0), true, null);
        shot.setServices(TestEnvironment.objectServices());
        manager().addDynamicObject(shot);
        assertTrue(bool(shot, "chargingGunBound"));
        assertNull(field(shot, "chargingGun"));
        shot.update(1, null);
        assertEquals(30, shot.chargeForTest(), "negative inlet still reaches the original charge tail");
        assertPending(shot);
        assertFalse(other.gun().hasNativeRetirementStatus());
        assertTrue(completedPublication(shot).isEmpty());
    }

    @Test
    void nonnullUnregisteredGunCannotBeSilentlyCapturedAsNull() {
        fixture();
        var unmanaged = new EggRoboGunArmChildInstance(new ObjectSpawn(0x200, 0xC00, 0, 0, 0, false, 0));
        var shot = EggRoboShotInstance.gunShot(
                new ObjectSpawn(0x200, 0xC00, 0, 0, 0, false, 0), true, unmanaged);
        // Construction above has no services, owner query or registration side effect.
        assertSame(unmanaged, field(shot, "chargingGun"));
        assertFalse(manager().getActiveObjects().contains(unmanaged));
        shot.setServices(TestEnvironment.objectServices());
        manager().addDynamicObject(shot);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> shot.captureRewindState(manager().captureIdentityContext()));
        assertTrue(failure.getMessage().contains("chargingGun"));
        assertTrue(failure.getMessage().contains("no registered id"));
        assertSame(unmanaged, field(shot, "chargingGun"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicUnparentedAndPlayerOwnedInletsKeepTheirOriginalChargeAndMotion(boolean playerOwned) {
        fixture();
        ObjectSpawn spawn = new ObjectSpawn(0x200, 0xC00, 0, 0, 1, false, 0);
        EggRoboShotInstance shot = playerOwned ? EggRoboShotInstance.playerShot(spawn, false)
                : new EggRoboShotInstance(spawn, false);
        shot.setServices(TestEnvironment.objectServices());
        manager().addDynamicObject(shot);
        assertFalse(bool(shot, "chargingGunBound"));
        assertNull(field(shot, "chargingGun"));
        for (int entry = 0; entry < 32; entry++) shot.update(clock(entry), null);
        assertFalse(bool(shot, "pendingDelete"));
        assertEquals(playerOwned ? 0 : 0x9C, shot.getCollisionFlags());
        assertEquals(playerOwned, bool(shot, "playerOwned"));
        shot.update(clock(32), null);
        assertEquals(0x208, shot.getX());
        assertFalse(shot.isDestroyed());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void twoExactEqualCoordinateFamiliesReplayBirthLastChargePendingAndArmedTwice(int boundary) {
        var fixture = fixture();
        Family first = firedFamily(0), second = firedFamily(0);
        EggRoboShotInstance a = shotOf(first.gun()), b = shotOf(second.gun());
        assertEquals(a.getX(), b.getX());
        assertEquals(a.getY(), b.getY());
        assertNotEquals(id(first.gun()), id(second.gun()));
        assertNotEquals(id(a), id(b));
        int prior = boundary == 0 ? 0 : boundary == 1 ? 31 : boundary == 2 ? 15 : 32;
        for (int entry = 0; entry < prior; entry++) {
            a.update(clock(entry), null);
            b.update(clock(entry), null);
        }
        if (boundary == 2) {
            retireBodyAndRunChildTails(first);
            a.update(clock(prior), null);
            assertPending(a);
        }
        FamilyIds firstIds = ids(first, a), secondIds = ids(second, b);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        List<ShotStep> expected = forward(first, a, b);
        CompositeSnapshot after = registry.capture();

        for (int cycle = 0; cycle < 2; cycle++) {
            removeIfRegistered(a);
            removeIfRegistered(b);
            removeIfRegistered(first.gun());
            removeIfRegistered(second.gun());
            removeIfRegistered(first.jet());
            removeIfRegistered(second.jet());
            removeIfRegistered(first.body());
            removeIfRegistered(second.body());
            registry.restore(before);
            Family restoredA = restoredFamily(firstIds), restoredB = restoredFamily(secondIds);
            EggRoboShotInstance restoredShotA = (EggRoboShotInstance) resolve(firstIds.shot());
            EggRoboShotInstance restoredShotB = (EggRoboShotInstance) resolve(secondIds.shot());
            assertNotSame(a, restoredShotA);
            assertNotSame(b, restoredShotB);
            assertNotSame(first.gun(), restoredA.gun());
            assertNotSame(second.gun(), restoredB.gun());
            assertNotSame(first.jet(), restoredA.jet());
            assertNotSame(second.jet(), restoredB.jet());
            if (firstIds.body() != null) {
                assertNotSame(first.body(), restoredA.body());
                assertSame(restoredA.body(), field(restoredA.gun(), "parent"));
            }
            assertNotSame(second.body(), restoredB.body());
            assertSame(restoredB.body(), field(restoredB.gun(), "parent"));
            assertBoundOwner(restoredShotA, restoredA.gun());
            assertBoundOwner(restoredShotB, restoredB.gun());
            assertWorldEquals(before, registry.capture(), "restore boundary " + boundary + " cycle " + cycle);
            assertEquals(expected, forward(restoredA, restoredShotA, restoredShotB));
            assertWorldEquals(after, registry.capture(), "forward boundary " + boundary + " cycle " + cycle);
            first = restoredA;
            second = restoredB;
            a = restoredShotA;
            b = restoredShotB;
        }
    }

    private record Family(EggRoboBadnikInstance body, EggRoboGunArmChildInstance gun,
                          EggRoboJetFlameChildInstance jet) { }
    private record FamilyIds(ObjectRefId body, ObjectRefId gun, ObjectRefId jet, ObjectRefId shot) { }
    private record SfxCall(int sound, boolean pending, int charge, boolean bound) { }
    private record ShotStep(int x, int y, int charge, int velocity, int frame, int collision,
                            boolean pending, boolean destroyed, ObjectRefId gun,
                            SpritePresentation.Frame presentation) { }

    private static List<ShotStep> forward(Family first, EggRoboShotInstance a, EggRoboShotInstance b) {
        List<ShotStep> steps = new ArrayList<>();
        if (!first.gun().hasNativeRetirementStatus()) retireBodyAndRunChildTails(first);
        if (bool(a, "pendingDelete")) {
            a.update(0x6001, null);
            steps.add(step(a));
        } else {
            a.update(0x6001, null);
            steps.add(step(a));
            if (bool(a, "pendingDelete")) {
                a.update(0x6001, null);
                steps.add(step(a));
            }
        }
        int entry = 0;
        for (; entry < 32 && !b.armedForTest(); entry++) {
            b.update(clock(entry), null);
            steps.add(step(b));
        }
        assertTrue(b.armedForTest(), "bounded remaining charge dispatches must reach arming");
        b.update(clock(entry), null);
        steps.add(step(b));
        return List.copyOf(steps);
    }

    private static ShotStep step(EggRoboShotInstance shot) {
        return new ShotStep(shot.getX(), shot.getY(), shot.chargeForTest(), shot.xVelocityForTest(),
                shot.mappingFrameForTest(), shot.getCollisionFlags(), bool(shot, "pendingDelete"),
                shot.isDestroyed(), optionalId((ObjectInstance) field(shot, "chargingGun")), draw(shot));
    }

    private static void assertBoundOwner(EggRoboShotInstance shot, EggRoboGunArmChildInstance gun) {
        if (bool(shot, "chargingGunBound")) {
            assertSame(gun, field(shot, "chargingGun"));
            assertEquals(id(gun), id((ObjectInstance) field(shot, "chargingGun")));
        } else {
            assertNull(field(shot, "chargingGun"));
        }
    }

    private static FamilyIds ids(Family family, EggRoboShotInstance shot) {
        return new FamilyIds(optionalId(family.body()), id(family.gun()), id(family.jet()), id(shot));
    }

    private static Family restoredFamily(FamilyIds ids) {
        return new Family(ids.body() == null ? null : (EggRoboBadnikInstance) resolve(ids.body()),
                (EggRoboGunArmChildInstance) resolve(ids.gun()),
                (EggRoboJetFlameChildInstance) resolve(ids.jet()));
    }

    private static HeadlessTestFixture fixture() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2); // Ordinary SSZ boot; component checkpoints are not cold-load proof.
        manager().setRewindInPlaceRestoreEnabledForTest(false);
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0xB80);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        return fixture;
    }

    private static Family family(int flags) {
        var services = TestEnvironment.objectServices();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        var body = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        body.setServices(services);
        manager().addDynamicObject(body);
        body.update(1, null);
        body.refreshPostCameraRenderState();
        body.update(2, null);
        body.update(3, null);
        var gun = manager().activeObjectsOfType(EggRoboGunArmChildInstance.class).stream()
                .filter(candidate -> field(candidate, "parent") == body).findFirst().orElseThrow();
        var jet = manager().activeObjectsOfType(EggRoboJetFlameChildInstance.class).stream()
                .filter(candidate -> field(candidate, "parent") == body).findFirst().orElseThrow();
        gun.update(0, null);
        jet.update(0, null);
        return new Family(body, gun, jet);
    }

    private static Family firedFamily(int flags) {
        Family family = family(flags);
        // Declared request-word inlet; the positive firing test uses the real Body aiming path.
        set(family.body(), "gunArmed", true);
        family.gun().update(1, null);
        return family;
    }

    private static void armThroughBody(HeadlessTestFixture fixture, Family family) {
        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(), family.body().getX()
                + (family.body().badnikFacingLeft() ? -0x80 : 0x80));
        NativePositionOps.writeYPosPreserveSubpixel(fixture.sprite(), family.body().getY());
        family.body().update(4, fixture.sprite());
        assertTrue(family.body().gunArmed(), "ordinary Body alignment produces the request");
    }

    private static void retireBodyAndRunChildTails(Family family) {
        if (optionalId(family.body()) != null) manager().removeDynamicObject(family.body());
        assertTrue(family.body().isDestroyed(), "actual production onUnload publishes retirement");
        family.jet().update(0x5000, null);
        family.gun().update(0x5000, null);
        assertTrue(family.gun().hasNativeRetirementStatus());
        assertFalse(family.gun().isDestroyed());
    }

    private static EggRoboShotInstance shotOf(EggRoboGunArmChildInstance gun) {
        return manager().activeObjectsOfType(EggRoboShotInstance.class).stream()
                .filter(candidate -> field(candidate, "chargingGun") == gun).findFirst().orElseThrow();
    }

    private static List<SfxCall> recordSounds(EggRoboShotInstance shot) {
        List<SfxCall> sounds = new ArrayList<>();
        ObjectServices actual = TestEnvironment.objectServices();
        ObjectServices observed = mock(ObjectServices.class, delegatesTo(actual));
        doAnswer(invocation -> {
            int sound = invocation.getArgument(0);
            sounds.add(new SfxCall(sound, bool(shot, "pendingDelete"), shot.chargeForTest(),
                    bool(shot, "chargingGunBound")));
            actual.playSfx(sound);
            return null;
        }).when(observed).playSfx(anyInt());
        shot.setServices(observed);
        return sounds;
    }

    /** Real list builder under a declared one-responder component inlet, not an extra object pass. */
    @SuppressWarnings("unchecked")
    private static List<ObjectInstance> completedPublication(EggRoboShotInstance shot) {
        Object list = field(manager(), "collisionResponseList");
        invoke(list, "captureCompletedBuild", new Class<?>[] {List.class}, List.of(shot));
        return List.copyOf((List<ObjectInstance>) invoke(list, "playerReadView", new Class<?>[0]));
    }

    private static int clock(int entry) {
        int[] clocks = {0x2201, 0x2201, 0x23F0, 0x7F13, 0x23F0};
        return clocks[entry % clocks.length];
    }

    private static ObjectManager manager() { return TestEnvironment.objectServices().objectManager(); }
    private static boolean bool(Object object, String name) { return (boolean) field(object, name); }
    private static ObjectRefId optionalId(ObjectInstance object) {
        return manager().captureIdentityContext().requireIdentityTable().idFor(object);
    }
    private static ObjectRefId id(ObjectInstance object) {
        ObjectRefId id = optionalId(object);
        assertNotNull(id);
        return id;
    }
    private static ObjectInstance resolve(ObjectRefId id) {
        return manager().captureIdentityContext().requireIdentityTable().resolveObject(id, true);
    }
    private static void removeIfRegistered(ObjectInstance object) {
        if (object != null && optionalId(object) != null) manager().removeDynamicObject(object);
    }

    private static void assertPending(EggRoboShotInstance shot) {
        assertTrue(bool(shot, "pendingDelete"));
        assertFalse(shot.isDestroyed(), "Go_Delete_Sprite selects the next own dispatch");
        assertNull(field(shot, "chargingGun"), "last-use tail releases its no-longer-read raw link");
        assertFalse(bool(shot, "chargingGunBound"));
        assertFalse(shot.publishesTouchResponseListEntryThisFrame());
        assertTrue(draw(shot).tiles().isEmpty());
    }

    private static SpritePresentation.Frame draw(AbstractObjectInstance object) {
        return SpritePresentationRenderer.prepare(GameServices.graphics(), 0, 0,
                () -> object.appendRenderCommands(new ArrayList<>()));
    }

    private static void assertWorldEquals(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static Field declaredField(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
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
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) {
        try {
            Method method = object.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(object, args);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
