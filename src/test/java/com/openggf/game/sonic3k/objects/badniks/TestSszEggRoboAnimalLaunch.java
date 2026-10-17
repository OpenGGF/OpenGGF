package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AnimalObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent R19 component inlet: real SSZ ROM registry, ordinary EggRobo SST
 * dispatch and its own released child; no ColdCampaign movie or trace state.
 * Native oracle: skdisasm 044fa46725c71187399e13f5ddb70e11d32dc024,
 * loc_915F6 / ChildObjDat_919E6 / loc_917C0..loc_9181C and ROM word_2C7EA.
 * The component's public initial camera/RNG inputs select both facing bits,
 * both low-bit variants and both high-word cap branches. No actor scalars,
 * slots, timers, render caches or child routines are assigned by the test.
 * This constructs a parent release; it does not identify ColdCampaign animal629's
 * unique callback or certify native correctness of that campaign.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboAnimalLaunch {
    private static final int CAMERA_X = 0x0100;
    private static final int CAMERA_Y = 0x0B80;
    private static final int PARENT_X = 0x0180;
    private static final int PARENT_Y = 0x0C00;
    private static final int FORWARD_PASSES = 4;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    private static Stream<Arguments> facingAndVariant() {
        return Stream.of(
                Arguments.of(0, 0, 0x00420002L),
                Arguments.of(1, 0, 0x00420002L),
                Arguments.of(0, 1, 0x01A50002L),
                Arguments.of(1, 1, 0x01A50002L));
    }

    @ParameterizedTest(name = "parent bit0={0}, variant={1}")
    @MethodSource("facingAndVariant")
    void ordinaryParentReleaseInitializesNativeLaunchAndRecreatesTwiceBeforeOracle(
            int parentBit0, int variant, long initialRng) throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(
                SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        SonicConfigurationService.getInstance().setSessionOverride(
                SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle().withSkippedZoneIntro()
                .startPosition((short) PARENT_X, (short) PARENT_Y)
                .startPositionIsCentre().build();
        var manager = GameServices.level().getObjectManager();
        fixture.camera().setX((short) CAMERA_X);
        fixture.camera().setY((short) CAMERA_Y);
        fixture.camera().setXCopy((short) CAMERA_X);
        fixture.camera().setYCopy((short) CAMERA_Y);
        // Actual manager passes publish camera bounds and initialize ordinary scene
        // owners. Stop before a real release-eligible V-int (16 modulo32), avoiding
        // the unrelated arrival ship's 32-tick missile creation, not a movie clock.
        for (int passes = 0; passes < 32
                && ((manager.getVblaCounter() + 1) & 0x1F) != 0x10; passes++) {
            scan(fixture);
        }
        assertEquals(0x10, (manager.getVblaCounter() + 1) & 0x1F);
        var spawn = new ObjectSpawn(PARENT_X, PARENT_Y, 0xA0, 4,
                parentBit0, false, 0);
        int free = manager.firstFreeDynamicSlot();
        var parent = manager.createDynamicObject(() -> (EggRoboBadnikInstance)
                GameServices.module().createObjectRegistry().create(spawn));
        assertNotNull(parent, "real ROM factory must admit the releaser");
        int parentSlot = parent.getSlotIndex();
        assertEquals(free, parentSlot, "ordinary lowest-free parent allocation");
        assertEquals(EggRoboBadnikInstance.Mode.ANIMAL_RELEASER, parent.mode());
        // Production publisher, not a private cached-bound/visibility write.
        manager.snapshotTouchResponseState();
        manager.refreshPostCameraRenderState();
        assertTrue(parent.isOnScreenForTouch(), "declared parent is inside published camera bounds");
        Set<Integer> beforeAnimals = animalSlots(manager);
        int beforeCount = manager.getActiveObjectSlotCount();
        int beforeScore = GameServices.gameState().getScore();
        GameServices.rng().setSeed(initialRng); // Declared component input, never actor/fixture data.
        var control = new GameRng(GameRng.Flavour.S3K, initialRng);
        int raw = control.nextRaw();
        assertEquals(variant, raw & 1, "real RNG low bit covers the declared native variant");
        RomByteReader rom = RomByteReader.fromRom(RomManager.getInstance().getRom());
        int entry = 0x2C7EA + 8 * variant;
        int tableX = (short) rom.readU16BE(entry);
        int tableY = (short) rom.readU16BE(entry + 2);
        long tableMap = rom.readU32BE(entry + 4);
        assertEquals(-0x200, tableX, "independent original ROM word_2C7EA X");
        assertEquals(variant == 0 ? -0x400 : -0x300, tableY);
        assertEquals(variant == 0 ? 0x2CF32L : 0x2CEBAL, tableMap);

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        scan(fixture); // Parent release, AfterCurrent child allocation, then child's own init.
        var child = singleNewAnimal(manager, beforeAnimals);
        int childSlot = child.getSlotIndex();
        assertTrue(childSlot > parentSlot, "actual AfterCurrent child has a later physical SST");
        assertEquals(beforeCount + 1, manager.getActiveObjectSlotCount(),
                "one animal, no extra initializer or points SST");
        assertEquals(beforeScore, GameServices.gameState().getScore(), "release awards no points");
        assertEquals(control.getSeed(), GameServices.rng().getSeed(), "one own-init RNG draw");
        assertEquals(EggRoboBadnikInstance.ANIMAL_RELEASES - 1, parent.animalsLeftForTest(),
                "actual parent dispatch performed the first release");
        Launch launch = launch(child);
        CompositeSnapshot birth = registry.capture();
        for (int pass = 0; pass < FORWARD_PASSES; pass++) scan(fixture);
        CompositeSnapshot forward = registry.capture();

        // All ordinary allocation and two full-world recreation/replay controls
        // precede every native launch assertion, including on the expected red.
        EggRoboBadnikInstance oldParent = parent;
        AnimalObjectInstance oldChild = child;
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            same(before, registry.capture());
            parent = (EggRoboBadnikInstance) NativeSstSlotAccess.currentOwner(manager, parentSlot);
            assertNotNull(parent);
            assertNotSame(oldParent, parent, "forced parent recreation cycle " + cycle);
            oldParent = parent;
            scan(fixture);
            child = singleNewAnimal(manager, beforeAnimals);
            assertEquals(childSlot, child.getSlotIndex(), "same ordinary allocation on replay");
            assertNotSame(oldChild, child, "forced child recreation through parent release");
            assertEquals(launch, launch(child));
            same(birth, registry.capture());
            oldChild = child;
            for (int pass = 0; pass < FORWARD_PASSES; pass++) scan(fixture);
            same(forward, registry.capture());
            registry.restore(birth);
            same(birth, registry.capture());
            child = (AnimalObjectInstance) NativeSstSlotAccess.currentOwner(manager, childSlot);
            assertNotNull(child);
            assertNotSame(oldChild, child, "forced already-born child recreation cycle " + cycle);
            oldChild = child;
            assertEquals(launch, launch(child), "restore consumes no new RNG or initialization");
            for (int pass = 0; pass < FORWARD_PASSES; pass++) scan(fixture);
            same(forward, registry.capture());
        }
        // Native first draw has no translation; routine4/6 is WALK/FLY, not MAIN.
        // Renderer mapping-set ordinals A..E select ROM Map_Animals1..5.
        int expectedX = parentBit0 == 0 ? tableX : -tableX;
        int expectedY = -Math.max(0x100, (raw >>> 16) & 0x1FF);
        assertAll("loc_917C0 native launch AFTER both complete recreation cycles",
                () -> assertEquals(PARENT_X, launch.x()),
                () -> assertEquals(PARENT_Y, launch.y()),
                () -> assertEquals(0, launch.xFraction()),
                () -> assertEquals(0, launch.yFraction()),
                () -> assertEquals(expectedX, launch.vx(), "parent render bit0 signs table X"),
                () -> assertEquals(expectedY, launch.vy(), "original RNG high word, capped [100,1FF]"),
                () -> assertEquals(tableY, launch.groundY()),
                () -> assertEquals(variant, launch.artVariant()),
                () -> assertEquals(variant == 0 ? 4 : 0, launch.mappingSet()),
                () -> assertEquals(variant == 0 ? "WALK" : "FLY", launch.state()),
                () -> assertEquals(1, launch.frame()),
                () -> assertEquals(7, launch.animationTimer()),
                () -> assertFalse(launch.firstDisplay(), "own first dispatch already completed"),
                () -> assertFalse(launch.deferredRng()),
                () -> assertEquals(6, launch.priorityBucket()),
                () -> assertEquals(8, launch.halfWidth()));
    }

    private static void scan(HeadlessTestFixture fixture) {
        var manager = GameServices.level().getObjectManager();
        manager.update(fixture.camera().getX(), fixture.sprite(), List.of(),
                manager.getFrameCounter() + 1, false);
        manager.refreshPostCameraRenderState();
    }

    private static Set<Integer> animalSlots(ObjectManager manager) {
        Set<Integer> slots = new HashSet<>();
        for (var object : manager.getActiveObjects()) {
            if (object instanceof AnimalObjectInstance animal) slots.add(animal.getSlotIndex());
        }
        return slots;
    }

    private static AnimalObjectInstance singleNewAnimal(ObjectManager manager, Set<Integer> before) {
        AnimalObjectInstance found = null;
        for (var object : manager.getActiveObjects()) {
            if (object instanceof AnimalObjectInstance animal && !before.contains(animal.getSlotIndex())) {
                assertNull(found, "exactly one new animal from this ordinary parent release");
                found = animal;
            }
        }
        assertNotNull(found, "actual parent release must allocate its own child");
        return found;
    }

    private record Launch(int x, int y, int xFraction, int yFraction, int vx, int vy,
                          int groundX, int groundY, int artVariant, int mappingSet, String state,
                          int frame, int animationTimer, boolean firstDisplay, boolean deferredRng,
                          int priorityBucket, int halfWidth) { }

    private static Launch launch(AnimalObjectInstance child) throws ReflectiveOperationException {
        return new Launch(child.getX(), child.getY(), integer(child, "xSubpixel"),
                integer(child, "ySubpixel"), integer(child, "xVelocity"), integer(child, "yVelocity"),
                integer(child, "groundXVelocity"), integer(child, "groundYVelocity"),
                integer(child, "artVariant"), integer(child, "mappingSetIndex"),
                field(child, "state").toString(), integer(child, "animFrame"),
                integer(child, "animFrameTimer"), (Boolean) field(child, "firstDisplayFrame"),
                (Boolean) field(child, "deferArtVariantRng"), child.getPriorityBucket(),
                child.getOnScreenHalfWidth());
    }

    private static int integer(Object owner, String name) throws ReflectiveOperationException {
        return (Integer) field(owner, name);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner); // Oracle only: never set actor/cached-render/routine fields.
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
