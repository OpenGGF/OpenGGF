package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.ssz.ObjBooster;
import com.openggf.game.soniccd.objects.ssz.ObjLightEdge;
import com.openggf.game.soniccd.objects.ssz.ObjPathSwap;
import com.openggf.game.soniccd.objects.ssz.ObjSparks;
import com.openggf.game.soniccd.objects.ssz.ObjSpringLauncher;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTtzWorldRewind {
    private static final int REPLAY_FRAMES = 120;

    @TempDir
    Path configDir;

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CdStagePayload.class, names = "R[1345678][12][ABCD]", mode = EnumSource.Mode.MATCH_ALL)
    void replaysLiveObjectsAfterRestore(CdStagePayload payload) throws Exception {
        withStage(payload, (sonic, runner) -> {
            runner.stepIdleFrames(2);

            var gameplay = SessionManager.getCurrentGameplayMode();
            var level = gameplay.getLevelManager();
            int savedFrame = level.getFrameCounter();
            var registry = gameplay.getRewindRegistry();
            var snapshot = registry.capture();
            var objects = level.getObjectManager();
            var savedPoses = poses(objects);
            int multipartId = switch (payload) {
                case R42B -> 0x3D;
                case R62A, R62B, R62C, R62D -> 0x04;
                case R71B -> 0x20;
                case R72A, R72B, R72C, R72D -> 0x1E;
                default -> -1;
            };
            assertTrue(!savedPoses.isEmpty(), payload + " has live objects at capture");
            if (multipartId >= 0) {
                assertTrue(savedPoses.values().stream().filter(pose -> pose.id() == multipartId).count() > 1,
                        payload + " has spawned the multipart object");
            }

            List<Map<Integer, ObjectPose>> replay = new ArrayList<>(REPLAY_FRAMES);
            for (int frame = 0; frame < REPLAY_FRAMES; frame++) {
                runner.stepIdleFrames(1);
                replay.add(poses(objects));
            }
            assertTrue(level.getFrameCounter() > savedFrame, "the level advanced after capture");

            registry.restore(snapshot);
            assertEquals(savedFrame, level.getFrameCounter(), "rewind restores the saved level frame");
            Map<String, String> differences = new LinkedHashMap<>();
            comparePoses(savedPoses, poses(objects), 0, differences);
            for (int frame = 1; frame <= REPLAY_FRAMES; frame++) {
                runner.stepIdleFrames(1);
                comparePoses(replay.get(frame - 1), poses(objects), frame, differences);
            }
            assertTrue(differences.isEmpty(), () -> payload + " first divergence per object type:\n"
                    + String.join("\n", differences.values()));
        });
    }

    @Test
    void restoresTheWaterLineAndRetainedPumpSpeed() throws Exception {
        withStage(CdStagePayload.R41C, (sonic, runner) -> {
            var gameplay = SessionManager.getCurrentGameplayMode();
            var levels = gameplay.getLevelManager();
            var water = GameServices.waterOrNull();
            int zone = levels.getFeatureZoneId();
            int act = levels.getFeatureActId();
            // UpdateWaterAnimate (r4/main.asm:573-583) produces an observable offset before capture.
            for (int frame = 0; frame < 16; frame++) water.updateDynamic(zone, act, 0, 0);
            int savedLine = water.getGameplayWaterLevelY(zone, act);
            var registry = gameplay.getRewindRegistry();
            var snapshot = registry.capture();

            NativePositionOps.writeXPosResetSubpixel(sonic, 0x1000);
            int initialBase = water.getWaterLevelY(zone, act);
            water.updateDynamic(zone, act, 0, 0);
            water.update();
            int replayBase = water.getWaterLevelY(zone, act);
            assertEquals(initialBase - 1, replayBase, "initial pump speed");

            // WaterEventsAct1Future (r4/main.asm:664-673) raises the speed at $1980 and leaves it set.
            NativePositionOps.writeXPosResetSubpixel(sonic, 0x1980);
            for (int frame = 0; frame < 64; frame++) water.updateDynamic(zone, act, 0, 0);
            assertTrue(water.getGameplayWaterLevelY(zone, act) != savedLine, "the water bob changed");

            registry.restore(snapshot);
            int restoredLine = water.getGameplayWaterLevelY(zone, act);
            NativePositionOps.writeXPosResetSubpixel(sonic, 0x1000);
            water.updateDynamic(zone, act, 0, 0);
            water.update();
            assertAll(
                    () -> assertEquals(savedLine, restoredLine, "water line on restore"),
                    () -> assertEquals(replayBase, water.getWaterLevelY(zone, act), "retained pump speed after restore"));
        });
    }

    @Test
    void replaysBoosterSparksAfterRestore() throws Exception {
        withStage(CdStagePayload.R71A, (sonic, runner) -> {
            var objects = GameServices.level().getObjectManager();
            ObjBooster booster = admitPlacement(sonic, objects, 0x08, ObjBooster.class);
            sonic.setAir(false);
            booster.update(1, sonic);
            ObjSparks sparks = objects.getActiveObjects().stream().filter(ObjSparks.class::isInstance)
                    .map(ObjSparks.class::cast).findFirst().orElseThrow();
            sparks.update(1, sonic);

            int slot = sparks.getSlotIndex();
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var snapshot = registry.capture();
            var expected = animationFrames(sparks, ObjSparks::mapFrame, sonic);
            registry.restore(snapshot);
            ObjSparks restored = objects.getActiveObjects().stream().filter(ObjSparks.class::isInstance)
                    .map(ObjSparks.class::cast).filter(object -> object.getSlotIndex() == slot)
                    .findFirst().orElseThrow();
            assertNotSame(sparks, restored);
            assertEquals(expected, animationFrames(restored, ObjSparks::mapFrame, sonic));
        });
    }

    @Test
    void replaysPlacedPathSwapFlashAfterRestore() throws Exception {
        withStage(CdStagePayload.R71A, (sonic, runner) -> {
            var objects = GameServices.level().getObjectManager();
            ObjPathSwap swap = admitPlacement(sonic, objects, 0x1D, ObjPathSwap.class);
            swap.update(1, sonic);
            assertEquals(4, swap.routine(), "overlap starts the path swap flash");
            swap.update(2, sonic);

            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var snapshot = registry.capture();
            var expected = animationFrames(swap, ObjPathSwap::mapFrame, sonic);
            registry.restore(snapshot);
            var restored = assertInstanceOf(ObjPathSwap.class, objects.getActiveObjectForRewind(swap.getSpawn()));
            assertNotSame(swap, restored);
            assertEquals(expected, animationFrames(restored, ObjPathSwap::mapFrame, sonic));
        });
    }

    @Test
    void replaysUnindexedLightEdgeAnimationAfterRestore() throws Exception {
        withStage(CdStagePayload.R71A, (sonic, runner) -> {
            // ObjectIndex (r7/object_index_1_2.asm:3-63) has no Light Edge entry.
            // Use per-object capture with the stage's art and services.
            var objects = GameServices.level().getObjectManager();
            var object = new ObjLightEdge(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
            objects.addDynamicObject(object);
            object.update(1, sonic);

            var snapshot = object.captureRewindState();
            var expected = animationFrames(object, ObjLightEdge::mapFrame, sonic);
            object.restoreRewindState(snapshot);
            assertEquals(expected, animationFrames(object, ObjLightEdge::mapFrame, sonic));
        });
    }

    private static <T extends AbstractObjectInstance> T admitPlacement(
            AbstractPlayableSprite sonic, ObjectManager objects, int id, Class<T> type) {
        var spawn = objects.getAllSpawns().stream().filter(placement -> placement.objectId() == id)
                .findFirst().orElseThrow();
        NativePositionOps.writeXPosResetSubpixel(sonic, spawn.x());
        NativePositionOps.writeYPosResetSubpixel(sonic, spawn.y());
        GameServices.camera().updatePosition(true);
        int cameraX = GameServices.camera().getX();
        objects.reset(cameraX);
        ObjectManagerInternalAccess.runSpawnerBeforeObjectPass(objects, cameraX);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        return assertInstanceOf(type, objects.getActiveObjectForRewind(spawn));
    }

    private static <T extends AbstractObjectInstance> List<Integer> animationFrames(
            T object, ToIntFunction<T> mapFrame, AbstractPlayableSprite sonic) {
        List<Integer> frames = new ArrayList<>();
        for (int frame = 0; frame < 8; frame++) {
            object.update(frame, sonic);
            frames.add(mapFrame.applyAsInt(object));
        }
        assertTrue(frames.stream().distinct().count() > 1, "the object's animation advanced");
        return frames;
    }

    private void withStage(CdStagePayload payload, StageCheck check) throws Exception {
        TestEnvironment.resetAll();
        var previous = EngineServices.current();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            try {
                var sonic = CdFixtureReplay.boot(configDir, disc, payload);
                check.run(sonic, new HeadlessTestRunner(sonic));
            } finally {
                SessionManager.clear();
                EngineServices.configure(previous);
                TestEnvironment.resetAll();
            }
        }
    }

    @FunctionalInterface
    private interface StageCheck {
        void run(AbstractPlayableSprite sonic, HeadlessTestRunner runner) throws Exception;
    }

    private static Map<Integer, ObjectPose> poses(ObjectManager objects) throws IllegalAccessException {
        Map<Integer, ObjectPose> poses = new TreeMap<>();
        for (var instance : objects.getActiveObjects()) {
            if (instance.isDestroyed()) continue;
            var object = (AbstractObjectInstance) instance;
            int slot = object.getSlotIndex();
            var pose = new ObjectPose(object.getClass().getSimpleName(), object.getLiveObjectId(),
                    object.getX(), object.getY(),
                    readState(object, "mapFrame", "animFrame", "frame"),
                    readState(object, "routine", "state"),
                    object instanceof ObjSpringLauncher launcher ? launcher.parentHeading() : 0);
            assertTrue(poses.putIfAbsent(slot, pose) == null, () -> "multiple live objects in slot " + slot);
        }
        return poses;
    }

    // The CD object families keep separate fields for native mapping and routine bytes.
    private static Object readState(AbstractObjectInstance object, String... names) throws IllegalAccessException {
        for (String name : names) {
            for (Class<?> type = object.getClass(); type != AbstractObjectInstance.class; type = type.getSuperclass()) {
                Field field;
                try {
                    field = type.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    continue;
                }
                field.setAccessible(true);
                return field.get(object);
            }
        }
        return null;
    }

    private static void comparePoses(Map<Integer, ObjectPose> expected, Map<Integer, ObjectPose> actual,
                                     int frame, Map<String, String> differences) {
        if (expected.equals(actual)) return;
        var slots = new TreeSet<>(expected.keySet());
        slots.addAll(actual.keySet());
        for (int slot : slots) {
            ObjectPose before = expected.get(slot);
            ObjectPose after = actual.get(slot);
            if (Objects.equals(before, after)) continue;
            String mismatch = "frame " + frame + ", slot " + slot + ": expected " + before + ", got " + after;
            if (before != null) differences.putIfAbsent(before.type(), mismatch);
            if (after != null) differences.putIfAbsent(after.type(), mismatch);
        }
    }

    private record ObjectPose(String type, int id, int x, int y, Object mappingFrame, Object routine,
                              int launcherHeading) { }
}
