package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Declared EndSignControlDoStart component boundary, driven by production SST scans, not a cold route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizAct2LevelSizeWorkers {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest @ValueSource(ints = {0, 16})
    void threeAfterCurrentWorkersKeepTheirOrderAndNativeLifetimesThenReplayTwice(int minYDistance)
            throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        var below = manager.createDynamicObject(() -> new ScanWitness(1));
        var boss = manager.createDynamicObject(() -> new S3kBossDefeatSignpostFlow(
                0x11F0, 0, S3kBossDefeatSignpostFlow.CleanupAction.NONE, true));
        boss.beginNativeReplacement(boss.getSlotIndex());
        var firstHole = manager.createDynamicObject(() -> new ScanWitness(1));
        var middle = manager.createDynamicObject(() -> new ScanWitness(2));
        var oldResults = manager.createDynamicObject(() -> new S3kResultsScreenObjectInstance(true));
        var lastHole = manager.createDynamicObject(() -> new ScanWitness(1));
        var tail = manager.createDynamicObject(() -> new ScanWitness(3));
        int belowSlot = below.getSlotIndex();
        int bossSlot = boss.getSlotIndex();
        int xSlot = firstHole.getSlotIndex();
        int minSlot = oldResults.getSlotIndex();
        int maxSlot = lastHole.getSlotIndex();
        int middleSlot = middle.getSlotIndex();
        int tailSlot = tail.getSlotIndex();
        assertTrue(belowSlot < bossSlot && bossSlot < xSlot && xSlot < middleSlot
                && middleSlot < minSlot && minSlot < maxSlot && maxSlot < tailSlot);

        // Explicit component entry: the defeated control is retained, before its title-completion poll.
        // The actual DoStart flow waits on the title flag while the declared results owner retires.
        var phase = S3kBossDefeatSignpostFlow.class.getDeclaredField("phase");
        phase.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object doStart = Enum.valueOf((Class) phase.getType(), "AWAIT_ACT_TRANSITION");
        phase.set(boss, doStart);
        scan(fixture, 0);
        manager.removeDynamicObject(oldResults);
        assertNull(NativeSstSlotAccess.currentOwner(manager, belowSlot));
        assertNull(NativeSstSlotAccess.currentOwner(manager, xSlot));
        assertNull(NativeSstSlotAccess.currentOwner(manager, minSlot));
        assertNull(NativeSstSlotAccess.currentOwner(manager, maxSlot));
        GameServices.gameState().setEndOfLevelFlag(true);
        middle.armed = tail.armed = true;
        var level = GameServices.level().getCurrentLevel();
        int targetX = level.getMaxX();
        int targetMin = level.getMinY();
        int targetMax = level.getMaxY();
        fixture.camera().setMinX((short) 0x10E0);
        fixture.camera().setMaxX((short) (targetX - 16));
        fixture.camera().setMinY((short) (targetMin + minYDistance));
        fixture.camera().setMaxY((short) (targetMax - 16));

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        scan(fixture, 1);
        CompositeSnapshot activeWorkers = registry.capture();
        for (int pass = 2; pass <= 45; pass++) scan(fixture, pass);
        CompositeSnapshot after = registry.capture();
        var observedMiddle = (ScanWitness) NativeSstSlotAccess.currentOwner(manager, middleSlot);
        var observedTail = (ScanWitness) NativeSstSlotAccess.currentOwner(manager, tailSlot);
        String expectedBirth = xSlot + ":S3kIncLevelEndXGradualInstance:0,"
                + minSlot + ":S3kCameraGradualObjectInstance:2,"
                + maxSlot + ":S3kCameraGradualObjectInstance:3";
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        checks.add(() -> assertEquals(expectedBirth, observedMiddle.birth,
                "all three siblings allocate after the active control, ignoring a lower hole and remembered results slot"));
        int displacementX = 0, displacementMin = 0, displacementMax = 0;
        for (int pass = 1; pass <= 45; pass++) {
            displacementX += pass / 4;
            displacementMin += pass / 4;
            displacementMax += pass / 2;
            int expectedX = Math.min(targetX, targetX - 16 + displacementX);
            int expectedMin = Math.max(targetMin, targetMin + minYDistance - displacementMin);
            int expectedMax = Math.min(targetMax, targetMax - 16 + displacementMax);
            String expected = expectedX + "," + expectedMin + "," + expectedMax + "," + targetMax;
            String expectedLive = (displacementX < 16) + "," + (displacementMin < minYDistance)
                    + "," + (displacementMax <= 16);
            int index = pass - 1;
            checks.add(() -> assertEquals(expected, observedTail.bounds.get(index),
                    "literal worker high-word accumulation before the ordinary camera tail, dispatch " + (index + 1)));
            checks.add(() -> assertEquals(expectedLive, observedTail.live.get(index),
                    "X bhs / min-Y ble / max-Y bgt retirement predicates, dispatch " + (index + 1)));
        }
        checks.add(() -> assertEquals(minYDistance == 0 ? "false" : "true", observedTail.minLive.getFirst(),
                "already-at-target min-Y consumes its SST before self-retiring in the creation scan"));
        checks.add(() -> assertNull(NativeSstSlotAccess.currentOwner(manager, xSlot)));
        checks.add(() -> assertNull(NativeSstSlotAccess.currentOwner(manager, minSlot)));
        checks.add(() -> assertNull(NativeSstSlotAccess.currentOwner(manager, maxSlot)));
        checks.add(() -> assertEquals(0x10E0, fixture.camera().getMinX() & 0xFFFF));
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            same(before, registry.capture());
            for (int pass = 1; pass <= 45; pass++) scan(fixture, pass);
            same(after, registry.capture());
            // Also recreate the actual live helper owners, including their
            // accumulated fractional steps and Act 2 target semantics.
            registry.restore(activeWorkers);
            same(activeWorkers, registry.capture());
            for (int pass = 2; pass <= 45; pass++) scan(fixture, pass);
            same(after, registry.capture());
        }
        System.out.printf("AIZ_SIZE distance=%d control=%d lowerHole=%d first=%d formerResults=%d last=%d birth=%s minLive1=%s%n",
                minYDistance, bossSlot, belowSlot, xSlot, minSlot, maxSlot, observedMiddle.birth,
                observedTail.minLive.getFirst());
        assertAll(checks);
    }

    private static void scan(HeadlessTestFixture fixture, int pass) {
        GameServices.level().getObjectManager().update(
                fixture.camera().getX(), fixture.sprite(), List.of(), pass);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static final class ScanWitness extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private boolean armed;
        private String birth;
        private List<String> bounds = new ArrayList<>();
        private List<String> minLive = new ArrayList<>();
        private List<String> live = new ArrayList<>();
        private ScanWitness(int kind) { this(new ObjectSpawn(0, 0, 0, kind, 0, false, 0)); }
        public ScanWitness(ObjectSpawn spawn) { super(spawn, "AizSizeScanWitness"); setRomWorldPositioned(false); }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (getSpawn().subtype() == 1) { setDestroyed(true); return; }
            if (!armed) return;
            var objects = services().objectManager();
            var workers = objects.getActiveObjects().stream().filter(o -> !o.isDestroyed())
                    .filter(o -> o instanceof S3kIncLevelEndXGradualInstance
                            || o instanceof S3kCameraGradualObjectInstance)
                    .map(o -> (AbstractObjectInstance) o)
                    .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
            if (getSpawn().subtype() == 2 && birth == null) {
                birth = workers.stream().map(o -> o.getSlotIndex() + ":" + o.getClass().getSimpleName()
                        + ":" + o.getSpawn().subtype()).reduce((a, b) -> a + "," + b).orElse("");
            }
            if (getSpawn().subtype() == 3) {
                var camera = services().camera();
                bounds.add((camera.getMaxX() & 0xFFFF) + "," + (camera.getMinY() & 0xFFFF)
                        + "," + (camera.getMaxY() & 0xFFFF) + "," + (camera.getMaxYTarget() & 0xFFFF));
                boolean x = workers.stream().anyMatch(o -> o instanceof S3kIncLevelEndXGradualInstance);
                boolean min = workers.stream().anyMatch(o -> o instanceof S3kCameraGradualObjectInstance
                        && o.getSpawn().subtype() == 2);
                boolean max = workers.stream().anyMatch(o -> o instanceof S3kCameraGradualObjectInstance
                        && o.getSpawn().subtype() == 3);
                minLive.add(Boolean.toString(min));
                live.add(x + "," + min + "," + max);
            }
        }
    }
}
