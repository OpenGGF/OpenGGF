package com.openggf.game.soniccd;

import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.ssz.ObjSpringLauncher;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTtzWorldRewind {
    private static final int REPLAY_FRAMES = 120;

    @TempDir
    Path configDir;

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CdStagePayload.class, names = "R[1345678][12][ABCD]", mode = EnumSource.Mode.MATCH_ALL)
    void replaysLiveObjectsAfterRestore(CdStagePayload payload) throws Exception {
        TestEnvironment.resetAll();
        var previous = EngineServices.current();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            try {
                var runner = new HeadlessTestRunner(CdFixtureReplay.boot(configDir, disc, payload));
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
                Map<String, String> knownDifferences = new LinkedHashMap<>();
                comparePoses(savedPoses, poses(objects), 0, differences, knownDifferences);
                for (int frame = 1; frame <= REPLAY_FRAMES; frame++) {
                    runner.stepIdleFrames(1);
                    comparePoses(replay.get(frame - 1), poses(objects), frame, differences, knownDifferences);
                }
                for (String difference : knownDifferences.values()) {
                    System.out.println(payload + " known rewind gap: " + difference);
                }
                assertTrue(differences.isEmpty(), () -> payload + " first divergence per object type:\n"
                        + String.join("\n", differences.values()));
            } finally {
                SessionManager.clear();
                EngineServices.configure(previous);
                TestEnvironment.resetAll();
            }
        }
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
                                     int frame, Map<String, String> differences,
                                     Map<String, String> knownDifferences) {
        if (expected.equals(actual)) return;
        var slots = new TreeSet<>(expected.keySet());
        slots.addAll(actual.keySet());
        for (int slot : slots) {
            ObjectPose before = expected.get(slot);
            ObjectPose after = actual.get(slot);
            if (Objects.equals(before, after)) continue;
            String mismatch = "frame " + frame + ", slot " + slot + ": expected " + before + ", got " + after;
            if (expectedGap(before, after, frame)) {
                knownDifferences.putIfAbsent(before.type(), mismatch);
                continue;
            }
            if (before != null) differences.putIfAbsent(before.type(), mismatch);
            if (after != null) differences.putIfAbsent(after.type(), mismatch);
        }
    }

    private static boolean expectedGap(ObjectPose before, ObjectPose after, int frame) {
        if (frame == 0 || before == null || after == null) return false;
        // Tidal Tempest's water handler owns the unsaved bob phase. Its signed sine offset is -4 to +4.
        if (before.type().equals("ObjWaterSurface") && Math.abs(before.y() - after.y()) <= 8) {
            return after.equals(new ObjectPose(before.type(), before.id(), before.x(), after.y(),
                    before.mappingFrame(), before.routine(), before.launcherHeading()));
        }
        // TtzAnimal's animation is owned by PR #408. Compare its lifetime, position and routine.
        if (before.type().equals("TtzAnimal")) {
            return after.equals(new ObjectPose(before.type(), before.id(), before.x(), before.y(),
                    after.mappingFrame(), before.routine(), before.launcherHeading()));
        }
        return false;
    }

    private record ObjectPose(String type, int id, int x, int y, Object mappingFrame, Object routine,
                              int launcherHeading) { }
}
