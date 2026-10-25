package com.openggf.tests.trace;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.RingSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingDrawInternalAccess;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SessionInvocationExtension;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.trace.timing.HardwareTimingReplayPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two independently authored committed BK2 movies reach native lightning-ring
 * SSTs through ordinary ROM-backed gameplay. Recorded rows are expectations and
 * timing/input alignment only: no shield, ring, object or playable state is
 * installed from comparison data or declared trace pose/RNG. ROM entry owns
 * spawn/camera/RNG; the windows include acquisition, flight,
 * GiveRing, sparkle deletion, and a whole-registry restore while rings fly.
 */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SessionInvocationExtension.class)
class TestS3kAttractedRingMoviePhase {
    private static final Path ROOT = Path.of("src/test/resources/traces/s3k");

    @Test
    void standaloneMgzAttractionThroughSlotDeletion() throws Exception {
        replay(new MovieCase("mgz", "s3k-mgz-sonic-tails.bk2", 374, 534, 550, 650,
                List.of(
                        flying(529, 6, 0x05B0, 0x068F),
                        flying(534, 7, 0x05D0, 0x069F),
                        flying(539, 9, 0x05EF, 0x06AF),
                        sparkle(540, 6, 0x05BB, 0x067D, 11),
                        sparkle(543, 7, 0x05CA, 0x0692, 12),
                        absent(566, 6),
                        absent(571, 7),
                        sparkle(617, 9, 0x0411, 0x05E8, 13),
                        absent(644, 9))));
    }

    @Test
    void completeMovieMgzAttractionThroughSlotDeletion() throws Exception {
        replay(new MovieCase("mgz_completerun", "s3k-complete-sonic-tails.bk2", 316, 447, 465, 500,
                List.of(
                        flying(442, 6, 0x05F0, 0x06B0),
                        flying(447, 7, 0x05D0, 0x06A0),
                        sparkle(450, 6, 0x05F6, 0x06B6, 11),
                        flying(453, 9, 0x05B0, 0x0690),
                        sparkle(456, 7, 0x05D8, 0x06A8, 12),
                        sparkle(462, 9, 0x05B8, 0x0698, 13),
                        absent(475, 6),
                        absent(484, 7),
                        absent(490, 9))));
    }

    private static void replay(MovieCase movieCase) throws Exception {
        Path directory = ROOT.resolve(movieCase.directory());
        TraceData trace = TraceData.load(directory);
        Path moviePath = trace.metadata().sourceBk2() == null
                ? directory.resolve(movieCase.movie())
                : ROOT.resolve("_movies").resolve(movieCase.movie());
        assertTrue(Files.isRegularFile(moviePath), "required committed movie: " + moviePath);
        assertTrue(trace.hardwareTimingSchedule().hasRecordedInput(), "native hardware-timing input is required");
        assertTrue(trace.frameCount() > movieCase.end());
        Bk2Movie movie = new Bk2MovieLoader().load(moviePath);
        var originalConfig = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        HeadlessTestFixture fixture = null;
        try {
            var config = GameServices.configuration();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            config.resolveDisplayAspect();
            fixture = HeadlessTestFixture.builder()
                    .withRecording(moviePath)
                    .withRecordingStartFrame(trace.metadata().bk2FrameOffset())
                    .withHardwareReadinessAdmissionPolicy(HardwareReadinessAdmissionPolicy.RECORDED)
                    .withZoneAndAct(2, 0)
                    .withFreshLevelStartLifecycle()
                    .build();
            // Only readiness for matching production-submitted ROM work is
            // supplied by the trace. applyBootstrap also imports metadata RNG;
            // applyStartPositionAndGroundSnap imports pose/carry state. Neither
            // belongs in a cold-ROM-entry movie qualification.
            TraceReplaySessionBootstrap.installHardwareTimingReplay(trace, fixture, false);
            int movieStart = trace.metadata().bk2FrameOffset();
            if (movieStart > 0) fixture.runner().primeInputState(movie.getFrame(movieStart - 1));
            assertTrue(fixture.gameplayMode().getLevelManager().consumePendingInitialProcessSpritesPass(),
                    "the native ROM-entry setup pass executes before any movie row");

            var registry = fixture.gameplayMode().getRewindRegistry();
            CompositeSnapshot saved = null;
            CompositeSnapshot expectedForward = null;
            Observation savedObservation = null;
            int savedMovieCursor = -1;
            List<Observation> firstForward = new ArrayList<>();
            for (int row = 0; row <= movieCase.end(); row++) {
                drive(trace, fixture, row);
                qualify(movieCase, fixture, row);
                if (row == movieCase.restoreFrom()) {
                    saved = registry.capture();
                    savedObservation = observe(fixture);
                    savedMovieCursor = movie.getFrameCount() - fixture.runner().getRecordingFramesRemaining();
                    requireRewindOwners(saved);
                    assertTrue(savedObservation.rings().stream().filter(ring -> !ring.collected()).count() >= 2,
                            "the captured runtime must contain multiple flying rings");
                } else if (row > movieCase.restoreFrom() && row <= movieCase.restoreThrough()) {
                    firstForward.add(observe(fixture));
                }
                if (row == movieCase.restoreThrough()) {
                    expectedForward = registry.capture();
                    registry.restore(saved);
                    assertOwnersEqual(saved, registry.capture());
                    assertEquals(savedObservation, observe(fixture), "captured ring reservations/queue restore immediately");
                    objects().validateRewindReferenceClosure();
                    assertTrue(NativeSpriteSubmissionAccess.nativePassPresent(objects()));
                    assertTrue(RingDrawInternalAccess.slottedAttracted(GameServices.level().getRingManager(), 2).isEmpty(),
                            "restored native admission cannot receive inferred legacy ring art");
                    // The gameplay registry owns hardware jobs AND replay cursor /
                    // raw-frame latch. The observer is stateless. Only the BK2
                    // transport and logical input edge baseline live in the runner.
                    fixture.runner().setBk2Movie(movie, savedMovieCursor);
                    fixture.runner().primeInputState(movie.getFrame(savedMovieCursor - 1));
                    int observation = 0;
                    for (int replayRow = movieCase.restoreFrom() + 1;
                         replayRow <= movieCase.restoreThrough(); replayRow++) {
                        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                                TraceReplayBootstrap.phaseForReplay(trace,
                                        trace.getFrame(replayRow - 1), trace.getFrame(replayRow)),
                                "selected rewind window polls every movie row");
                        drive(trace, fixture, replayRow);
                        qualify(movieCase, fixture, replayRow);
                        assertEquals(firstForward.get(observation++), observe(fixture),
                                "runtime ring motion/queue forward replay at row " + replayRow);
                    }
                    assertOwnersEqual(expectedForward, registry.capture());
                }
            }
            assertNotNull(saved);
            assertNotNull(expectedForward);
            fixture.closeHardwareTimingReplayPrefix(trace.getFrame(movieCase.end()).frame());
        } finally {
            if (fixture != null) fixture.abortHardwareTimingReplayRun();
            try { SessionManager.clear(); }
            finally { TraceReplaySessionBootstrap.restoreGameplayConfig(originalConfig); }
        }
    }

    private static void drive(TraceData trace, HeadlessTestFixture fixture, int row) {
        TraceFrame current = trace.getFrame(row);
        TraceFrame previous = row == 0 ? null : trace.getFrame(row - 1);
        fixture.beginTraceRow(row, current.frame());
        TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, previous, current);
        TraceReplayBootstrap.markVblankStarvedIterationForReplay(previous, current);
        TraceReplayBootstrap.markIterationHeldIntoNextRowForReplay(current,
                row + 1 < trace.frameCount() ? trace.getFrame(row + 1) : null);
        int input = TraceReplayFrameClosureDriver.driveS3k(phase,
                TraceReplayBootstrap.shouldUsePreviousRecordingInputForTraceReplay(trace),
                fixture::stepFrameFromRecording, fixture::stepFrameFromRecordingUsingPreviousInput,
                fixture::skipFrameFromRecording, fixture::consumeRecordingFrameInputOnly,
                fixture::advancePlayableAnimationsOnly, fixture::suppressFirstSidekickAnimationOnce,
                () -> objects().validateRewindReferenceClosure());
        assertEquals(current.input(), input, "actual BK2 input alignment at row " + row);
        assertTrue(fixture.drainUnmatchedRecordedHardwareCompletions().isEmpty(),
                "every recorded completion must match production work at row " + row);
    }

    private static void qualify(MovieCase movieCase, HeadlessTestFixture fixture, int row) {
        if (row == movieCase.shieldFrame()) {
            assertEquals(ShieldType.LIGHTNING, fixture.sprite().getShieldType(),
                    "ordinary monitor gameplay must acquire the lightning shield");
        }
        for (Checkpoint expected : movieCase.checkpoints()) {
            if (expected.row() != row) continue;
            var rings = GameServices.level().getRingManager();
            var entry = Arrays.stream(rings.capture().attractedRings())
                    .filter(ring -> ring.objectSlotIndex() == expected.slot()).findFirst();
            if (expected.bucket() < 0) {
                assertTrue(entry.isEmpty(), "native sparkle deletion releases ring slot at row " + row);
                continue;
            }
            assertEquals(ShieldType.LIGHTNING, fixture.sprite().getShieldType());
            assertTrue(entry.isPresent(), "real attracted ring in SST slot " + expected.slot() + " at row " + row);
            var actual = entry.orElseThrow();
            assertTrue(actual.active());
            assertTrue(actual.sourceIndex() >= 0, "attraction originates from placed-stage ROM rings");
            assertEquals(expected.x(), actual.x(), "native ring x at row " + row);
            assertEquals(expected.y(), actual.y(), "native ring y at row " + row);
            assertEquals(expected.bucket() == 1, actual.collected(), "native GiveRing state at row " + row);
            if (!actual.collected()) {
                assertTrue(actual.xVel() != 0 || actual.yVel() != 0,
                        "the actual higher SST dispatch must move the newly attracted ring");
            }
            assertEquals(Integer.valueOf(ObjectManager.SLOT_STATE_UNATTRIBUTED),
                    objects().occupiedDynamicSlotIdsWithReservations().get(expected.slot()),
                    "live attracted rings reserve native SSTs without Java ObjectInstances");
            assertTrue(NativeSpriteSubmissionAccess.nativePassPresent(objects()));
            assertEquals(1L, NativeSpriteSubmissionAccess.admissions(objects()).stream()
                    .filter(call -> call.slot() == expected.slot() && call.bucket() == expected.bucket()).count(),
                    "one genuine Draw_Sprite call in its native bucket at row " + row);
            assertTrue(RingDrawInternalAccess.slottedAttracted(rings, expected.bucket()).isEmpty(),
                    "native calls own drawing rather than a late inferred bridge");
            if (expected.playerRings() >= 0) {
                assertEquals(expected.playerRings(), fixture.sprite().getRingCount(), "native ring award at row " + row);
            }
        }
    }

    private static Observation observe(HeadlessTestFixture fixture) {
        return new Observation(List.copyOf(Arrays.asList(GameServices.level().getRingManager().capture().attractedRings())),
                NativeSpriteSubmissionAccess.admissions(objects()),
                Map.copyOf(objects().occupiedDynamicSlotIdsWithReservations()), fixture.sprite().getRingCount());
    }

    private static ObjectManager objects() { return GameServices.level().getObjectManager(); }

    private static List<String> rewindOwners() {
        return List.of("rings", "object-manager", NativeSpriteSubmissionAccess.REWIND_KEY,
                "sprites", "camera", HardwareTimingService.REWIND_KEY, HardwareTimingReplayPort.REWIND_KEY);
    }

    private static void requireRewindOwners(CompositeSnapshot snapshot) {
        for (String key : rewindOwners()) assertTrue(snapshot.containsKey(key), "required production rewind owner: " + key);
    }

    private static void assertOwnersEqual(CompositeSnapshot expected, CompositeSnapshot actual) {
        for (String key : rewindOwners()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), key + ": " + differences);
        }
    }

    private static Checkpoint flying(int row, int slot, int x, int y) {
        return new Checkpoint(row, slot, x, y, 2, -1);
    }
    private static Checkpoint sparkle(int row, int slot, int x, int y, int playerRings) {
        return new Checkpoint(row, slot, x, y, 1, playerRings);
    }
    private static Checkpoint absent(int row, int slot) { return new Checkpoint(row, slot, 0, 0, -1, -1); }

    // Checkpoints above come from committed aux object_state / object_removed
    // and physics ring totals, selected independently before this test existed.
    // 0x1A88C is flight, 0x1A920 is GiveRing/sparkle; these values never drive gameplay.
    private record Checkpoint(int row, int slot, int x, int y, int bucket, int playerRings) { }
    private record MovieCase(String directory, String movie, int shieldFrame, int restoreFrom,
                             int restoreThrough, int end, List<Checkpoint> checkpoints) { }
    private record Observation(List<RingSnapshot.AttractedRingEntry> rings,
                               List<NativeSpriteSubmissionAccess.Admission> calls,
                               Map<Integer, Integer> occupiedSlots, int playerRings) { }
}
