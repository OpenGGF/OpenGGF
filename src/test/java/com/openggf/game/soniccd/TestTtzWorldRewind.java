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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTtzWorldRewind {
    @TempDir
    Path configDir;

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = "R[1345678][12][ABCD]", mode = EnumSource.Mode.MATCH_ALL)
    void restoresTheWorldAfterAdvancing(CdStagePayload payload) throws Exception {
        checkRewind(payload, 0);
    }

    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = {"R42B", "R62A", "R71B", "R72C"})
    void replaysMultipartObjectMotionAfterRestore(CdStagePayload payload) throws Exception {
        checkRewind(payload, 16);
    }

    private void checkRewind(CdStagePayload payload, int replayFrames) throws Exception {
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
                int objectId = switch (payload) {
                    case R42B -> 0x3D;
                    case R62A, R62B, R62C, R62D -> 0x04;
                    case R71B -> 0x20;
                    case R72A, R72B, R72C, R72D -> 0x1E;
                    default -> -1;
                };
                var savedPoses = poses(objects, objectId);
                if (objectId >= 0) {
                    assertTrue(savedPoses.size() > 1, payload + " has spawned the multipart object");
                }

                List<List<ObjectPose>> replay = new ArrayList<>();
                for (int frame = 0; frame < 120; frame++) {
                    runner.stepIdleFrames(1);
                    if (frame < replayFrames) replay.add(poses(objects, objectId));
                }
                assertTrue(level.getFrameCounter() > savedFrame, "the level advanced after capture");

                registry.restore(snapshot);
                assertEquals(savedFrame, level.getFrameCounter(), "rewind restores the saved level frame");
                assertEquals(savedPoses, poses(objects, objectId), "rewind restores the parts in their saved slots");
                for (int frame = 0; frame < replay.size(); frame++) {
                    runner.stepIdleFrames(1);
                    assertEquals(replay.get(frame), poses(objects, objectId), "replayed frame " + (frame + 1));
                }
            } finally {
                SessionManager.clear();
                EngineServices.configure(previous);
                TestEnvironment.resetAll();
            }
        }
    }

    private static List<ObjectPose> poses(ObjectManager objects, int objectId) {
        return objects.getActiveObjects().stream()
                .filter(object -> !object.isDestroyed() && object.getSpawn().objectId() == objectId)
                .map(AbstractObjectInstance.class::cast)
                .map(object -> new ObjectPose(object.getSlotIndex(), object.getX(), object.getY(),
                        object instanceof ObjSpringLauncher launcher ? launcher.parentHeading() : 0))
                .sorted(Comparator.comparingInt(ObjectPose::slot))
                .toList();
    }

    private record ObjectPose(int slot, int x, int y, int animationIndex) { }
}
