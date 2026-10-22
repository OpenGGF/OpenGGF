package openggf.racing.server.tools;

import com.openggf.net.hub.RelevanceClassifier;
import com.openggf.net.hub.GhostStreamValidator;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestGhostPackSchedule {
    @Test void packSurroundsTheRunnerAndAllSchedulesFinishWithoutReversing() {
        int faster = 0, slower = 0;
        for (int runner = 0; runner < 255; runner++) {
            int last = 0;
            for (int frame = 0; frame < 7000; frame++) {
                int source = GhostPackSchedule.sourceFrame(runner, frame, 3230);
                assertTrue(source >= last, "runner reverses: " + runner);
                assertTrue(source - last <= 2, "runner teleports: " + runner);
                last = source;
            }
            assertEquals(3229, last);
            int finish = GhostPackSchedule.finishFrame(runner, 3230);
            if (finish < 3229) faster++;
            if (finish > 3229) slower++;
        }
        assertTrue(faster >= 120 && slower >= 120, "live runner must finish mid-pack");
    }

    @Test void retimedSteepRouteKeepsIntermediatePosesAndPassesProductionAdmission() {
        List<GhostFrame> route = new ArrayList<>();
        for (int i = 0; i < 1000; i++) route.add(new GhostFrame(i * 8, i * 17, 1,
                false, false, i == 999, 2, false));
        for (int runner = 0; runner < 32; runner++) {
            long[] now = {100_000};
            var validator = new GhostStreamValidator(null, () -> now[0],
                    (kind, detail) -> fail(kind + ": " + detail));
            validator.onAttemptStart(1);
            int previous = 0;
            for (int frame = 0; frame < 2200; frame++) {
                now[0] = 100_000 + frame * 1000L / 60;
                int source = GhostPackSchedule.admittedSourceFrame(runner, frame, route, previous);
                assertTrue(source >= previous && source - previous <= 2);
                byte[] data = new byte[GhostFrameCodec.BYTES];
                GhostFrameCodec.encode(route.get(source), data, 0);
                assertEquals(GhostStreamValidator.Verdict.ACCEPT,
                        validator.onBatch(new GhostPackets.FramesBatch(1, frame, 1, data)));
                previous = source;
                if (source == route.size() - 1) break;
            }
            assertEquals(route.size() - 1, previous);
        }
    }

    @Test void productionNearSelectionHasEntriesExitsAndReturningPeers() {
        var classifier = new RelevanceClassifier();
        Set<Integer> previous = Set.of(), seen = new HashSet<>();
        int entries = 0, exits = 0, returns = 0;
        for (int time = 0; time < 3230; time += 3) {
            classifier.updatePosition(255, time * 4, 100);
            for (int runner = 0; runner < 255; runner++)
                classifier.updatePosition(runner, GhostPackSchedule.sourceFrame(runner, time, 3230) * 4, 100);
            classifier.rebucket();
            Set<Integer> current = classifier.nearSetFor(255);
            assertTrue(current.size() <= RelevanceClassifier.NEAR_CAP);
            for (int slot : current) if (!previous.contains(slot)) { entries++; if (!seen.add(slot)) returns++; }
            for (int slot : previous) if (!current.contains(slot)) exits++;
            previous = current;
        }
        assertTrue(entries > 30 && exits > 30 && returns > 10,
                "near/far switching and return admission must actually be exercised");
    }
}
