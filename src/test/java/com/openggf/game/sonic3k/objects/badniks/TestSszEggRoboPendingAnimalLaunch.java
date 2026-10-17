package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.AnimalType;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** R26 component inlet for a real factory-allocated animal captured BEFORE its first SST dispatch. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboPendingAnimalLaunch {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    private static Stream<Arguments> launchInputs() {
        return Stream.of(Arguments.of(false, 0x00420002L), Arguments.of(true, 0x00420002L),
                Arguments.of(false, 0x01A50002L), Arguments.of(true, 0x01A50002L));
    }

    @ParameterizedTest
    @MethodSource("launchInputs")
    void allocatedPendingLaunchRestoresWithoutRngAndReplaysBothBoundariesTwice(
            boolean parentFlipX, long initialSeed) throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle().withSkippedZoneIntro()
                .startPosition((short) 0x180, (short) 0xC00).startPositionIsCentre().build();
        var manager = GameServices.level().getObjectManager();
        fixture.camera().setX((short) 0x100); fixture.camera().setY((short) 0xB80);
        fixture.camera().setXCopy((short) 0x100); fixture.camera().setYCopy((short) 0xB80);
        for (int passes = 0; passes < 32 && ((manager.getVblaCounter() + 1) & 0x1F) != 0x10; passes++) scan(fixture);
        assertEquals(0x10, (manager.getVblaCounter() + 1) & 0x1F);
        GameServices.rng().setSeed(initialSeed); // Declared independent component RNG input.
        GameRng control = new GameRng(GameRng.Flavour.S3K, initialSeed);
        int raw = control.nextRaw();
        int free = manager.firstFreeDynamicSlot();
        int count = manager.getActiveObjectSlotCount();
        var animal = manager.createDynamicObject(() -> AnimalObjectInstance.deferredWalkingFlyingLaunch(
                new ObjectSpawn(0x180, 0xC00, 0, 0, 0, false, 0), manager.getObjectServices(),
                AnimalType.RABBIT, AnimalType.CHICKEN, parentFlipX));
        assertNotNull(animal);
        int slot = animal.getSlotIndex();
        assertEquals(free, slot, "ordinary factory allocation, no assigned SST");
        assertEquals(count + 1, manager.getActiveObjectSlotCount());
        assertEquals(initialSeed, GameServices.rng().getSeed(), "factory consumes no RNG");
        assertEquals(Boolean.TRUE, scalar(animal, "deferArtVariantRng"));
        assertEquals(Boolean.TRUE, scalar(animal, "firstDisplayFrame"));
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot pending = registry.capture();
        assertEquals(initialSeed, GameServices.rng().getSeed(), "pending capture consumes no RNG");
        scan(fixture);
        assertEquals(control.getSeed(), GameServices.rng().getSeed(), "first own dispatch draws once");
        assertEquals(0x180, animal.getX(), "initial draw has no translation");
        assertEquals(0xC00, animal.getY());
        assertEquals(parentFlipX ? 0x200 : -0x200, scalar(animal, "xVelocity"));
        assertEquals(-Math.max(0x100, (raw >>> 16) & 0x1FF), scalar(animal, "yVelocity"));
        assertEquals((raw & 1) == 0 ? "WALK" : "FLY", scalar(animal, "state").toString());
        assertEquals(1, scalar(animal, "animFrame"));
        CompositeSnapshot initialized = registry.capture();
        for (int pass = 0; pass < 4; pass++) scan(fixture);
        CompositeSnapshot forward = registry.capture();
        assertEquals(control.getSeed(), GameServices.rng().getSeed(), "later routines consume no new RNG");
        AnimalObjectInstance previous = animal;
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(pending); same(pending, registry.capture());
            animal = (AnimalObjectInstance) NativeSstSlotAccess.currentOwner(manager, slot);
            assertNotNull(animal); assertNotSame(previous, animal, "pending forced recreation");
            assertEquals(initialSeed, GameServices.rng().getSeed(), "pending restore constructor is RNG-free");
            assertEquals(Boolean.TRUE, scalar(animal, "deferArtVariantRng"));
            assertEquals(Boolean.TRUE, scalar(animal, "firstDisplayFrame"));
            previous = animal;
            scan(fixture); same(initialized, registry.capture());
            assertEquals(control.getSeed(), GameServices.rng().getSeed());
            for (int pass = 0; pass < 4; pass++) scan(fixture);
            same(forward, registry.capture());
            registry.restore(initialized); same(initialized, registry.capture());
            animal = (AnimalObjectInstance) NativeSstSlotAccess.currentOwner(manager, slot);
            assertNotNull(animal); assertNotSame(previous, animal, "initialized forced recreation");
            assertEquals(control.getSeed(), GameServices.rng().getSeed(), "initialized restore never redraws RNG");
            assertEquals(Boolean.FALSE, scalar(animal, "deferArtVariantRng"));
            previous = animal;
            for (int pass = 0; pass < 4; pass++) scan(fixture);
            same(forward, registry.capture());
            assertEquals(control.getSeed(), GameServices.rng().getSeed());
        }
    }

    private static void scan(HeadlessTestFixture fixture) {
        var manager = GameServices.level().getObjectManager();
        manager.update(fixture.camera().getX(), fixture.sprite(), List.of(), manager.getFrameCounter() + 1, false);
        manager.refreshPostCameraRenderState();
    }

    private static Object scalar(AnimalObjectInstance animal, String name) throws ReflectiveOperationException {
        var field = AnimalObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(animal);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
    }
}
