package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindDeterminismAuditor;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Independent production-route rewind spots; no gameplay is seeded from trace rows. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz1RouteRewind {
    enum Spot {
        INTRO_HANDOFF, KNUCKLES_WAIT, KNUCKLES_ACTIVE, INTRO_COMPLETE,
        TREE_APPROACH, TREE_LOCK, TREE_RELEASE
    }

    @ParameterizedTest(name = "AIZ1 native 320 Sonic+Tails rewind: {0}")
    @EnumSource(Spot.class)
    void liveSpotRestoresAndReplaysTwice(Spot spot) throws Exception {
        var savedConfig = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            var setup = TestS3kAiz1RoutePilot.prepareRoute();
            HeadlessTestFixture fixture = setup.fixture();
            InputProgram.Cursor input = new InputProgram.Cursor(setup.program(), setup.preLevelRows());
            int previousMask = 0;
            boolean treeLockSeen = false;
            int frame = 0;
            while (frame < InputProgram.frames(setup.program())) {
                treeLockSeen |= treeLocked();
                if (atSpot(spot, fixture, treeLockSeen)) break;
                previousMask = input.next();
                InputProgram.step(fixture, previousMask);
                frame++;
                assertFalse(fixture.sprite().getDead(), "P1 died reaching " + spot + " at " + frame);
                assertEquals(0, GameServices.level().getCurrentAct(), "missed AIZ1 spot " + spot);
            }
            assertTrue(atSpot(spot, fixture, treeLockSeen), "spot never reached: " + spot);
            int captureFrame = frame;
            var registry = fixture.gameplayMode().getRewindRegistry();
            CompositeSnapshot before = registry.capture();
            List<Integer> replayMasks = new ArrayList<>();
            for (int i = 0; i < 90; i++) {
                int mask = input.next();
                replayMasks.add(mask);
                InputProgram.step(fixture, mask);
                assertFalse(fixture.sprite().getDead(), "P1 died advancing " + spot);
                assertEquals(0, GameServices.level().getCurrentAct(), "spot window crossed a load: " + spot);
            }
            CompositeSnapshot expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                fixture.runner().primeInputState(new Bk2FrameInput(0, previousMask,
                        (previousMask & 0x10) == 0 ? 0 : 1, false, ""));
                assertSnapshotsMatch(before, registry.capture(), spot + " restore cycle " + cycle, captureFrame);
                for (int mask : replayMasks) InputProgram.step(fixture, mask);
                assertSnapshotsMatch(expected, registry.capture(), spot + " replay cycle " + cycle, captureFrame);
            }
            System.out.printf("AIZREWIND spot=%s frame=%d replay=90 cycles=2%n", spot, captureFrame);
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(savedConfig);
        }
    }

    private static boolean atSpot(Spot spot, HeadlessTestFixture fixture, boolean treeLockSeen) {
        int knucklesRoutine = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(CutsceneKnucklesAiz1Instance.class::isInstance)
                .map(CutsceneKnucklesAiz1Instance.class::cast)
                .mapToInt(CutsceneKnucklesAiz1Instance::getRoutine).findFirst().orElse(-1);
        int x = fixture.sprite().getCentreX() & 0xFFFF;
        return switch (spot) {
            case INTRO_HANDOFF -> AizPlaneIntroInstance.getActiveIntroInstance() != null
                    && AizPlaneIntroInstance.getActiveIntroInstance().getRoutine() == 10;
            // CutsceneKnucklesAiz1: routines 2 (wait trigger) and 6 (stand timer).
            case KNUCKLES_WAIT -> knucklesRoutine == 2;
            case KNUCKLES_ACTIVE -> knucklesRoutine == 6;
            case INTRO_COMPLETE -> fixture.camera().isLevelStarted();
            // Obj_AIZHollowTree captures at $2C99; approach from within 32 pixels.
            case TREE_APPROACH -> x >= 0x2C79 && x < 0x2C99 && !treeLockSeen;
            case TREE_LOCK -> treeLocked();
            case TREE_RELEASE -> treeLockSeen && !fixture.sprite().isObjectMappingFrameControl()
                    && fixture.camera().getMinX() == 0x1300 && fixture.camera().getMaxX() == 0x4000;
        };
    }

    private static boolean treeLocked() {
        // Obj_AIZHollowTree's Player_1 capture writes both camera bounds to $2C60.
        return GameServices.camera().getMinX() == 0x2C60 && GameServices.camera().getMaxX() == 0x2C60;
    }

    static void assertSnapshotsMatch(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String context, int frame) {
        assertSnapshotsMatch(expected, actual, context, frame, 90);
    }

    static void assertSnapshotsMatch(CompositeSnapshot expected, CompositeSnapshot actual,
                                    String context, int frame, int replayFrames) {
        List<String> differences = new ArrayList<>();
        boolean divergent = new RewindDeterminismAuditor(differences::add)
                .report(frame, frame + replayFrames, contentSnapshot(expected), contentSnapshot(actual));
        assertFalse(divergent, () -> context + ": " + String.join("\n", differences));
    }
    /** The production diff treats Pattern as an identity value; compare all title-card pixels. */
    private static CompositeSnapshot contentSnapshot(CompositeSnapshot snapshot) {
        var entries = new java.util.LinkedHashMap<>(snapshot.entries());
        Object title = entries.get("s3k-title-card");
        if (title != null) {
            var fields = new java.util.LinkedHashMap<String, Object>();
            for (var component : title.getClass().getRecordComponents()) {
                try {
                    Object value = component.getAccessor().invoke(title);
                    if (value instanceof com.openggf.level.Pattern[] patterns) {
                        byte[][] pixels = new byte[patterns.length][];
                        for (int i = 0; i < patterns.length; i++) {
                            if (patterns[i] != null) {
                                pixels[i] = new byte[64];
                                patterns[i].copyInto(pixels[i], 0);
                            }
                        }
                        value = pixels;
                    }
                    fields.put(component.getName(), value);
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError("cannot compare title-card field " + component.getName(), exception);
                }
            }
            entries.put("s3k-title-card", new TitleState(fields));
        }
        return new CompositeSnapshot(entries);
    }

    public record TitleState(java.util.Map<String, Object> fields) { }

}
