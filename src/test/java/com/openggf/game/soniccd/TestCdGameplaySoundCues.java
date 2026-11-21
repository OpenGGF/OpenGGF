package com.openggf.game.soniccd;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdResults;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdGameplaySoundCues {
    @TempDir Path config;

    @AfterEach
    void reset() {
        GameServices.audio().setRequestObserver(AudioRequestObserver.NONE);
        TestEnvironment.resetAll();
    }

    @Test
    void resultsUseTheTimerHighByteAndChimeOnTheLastBonusStep() throws Exception {
        TestEnvironment.resetAll();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var sonic = CdFixtureReplay.boot(config, disc, CdStagePayload.R11A);
            var results = new CdResults(1, 20_000, 0);
            GameServices.level().getObjectManager().addDynamicObject(results);
            sonic.setX((short) (GameServices.camera().getX() + 400));

            List<Integer> tally = new ArrayList<>();
            List<Integer> chime = new ArrayList<>();
            int[] frame = {0};
            GameServices.audio().setRequestObserver((kind, id) -> {
                if (kind != AudioRequestObserver.RequestClass.SFX) return;
                if (id == 0xBD) tally.add(frame[0]);
                if (id == 0x9A) chime.add(frame[0]);
            });

            int firstBonus = -1;
            for (; frame[0] < 500; frame[0]++) {
                results.update(frame[0], sonic);
                if (firstBonus < 0 && GameServices.gameState().getScore() > 0) firstBonus = frame[0];
            }
            assertEquals(20_000, GameServices.gameState().getScore());
            // The native results line starts at 480 and reaches the first tally step at timer 442.
            // The memory btst sees the high byte: 255 through 244 request FM_TALLY.
            int start = firstBonus;
            assertAll(
                    () -> assertEquals(IntStream.rangeClosed(start + 187, start + 198).boxed().toList(), tally),
                    () -> assertEquals(List.of(start + 199), chime));
        }
    }

    @Test
    void resultsRequestTheWarpCueOnlyWhenTheArmedCountdownReachesThirty() throws Exception {
        TestEnvironment.resetAll();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var sonic = CdFixtureReplay.boot(config, disc, CdStagePayload.R11A);
            var level = (CdLevel) GameServices.level().getCurrentLevel();
            sonic.setX((short) (GameServices.camera().getX() + 400));

            List<Integer> requests = new ArrayList<>();
            int[] frame = {0};
            GameServices.audio().setRequestObserver((kind, id) -> {
                if (kind == AudioRequestObserver.RequestClass.SFX && id == 0xC8) requests.add(frame[0]);
            });
            int lastBonus = -1;
            for (boolean specialStage : new boolean[]{false, true}) {
                int startingScore = GameServices.gameState().getScore();
                var results = new CdResults(1, 50_000, 0);
                GameServices.level().getObjectManager().addDynamicObject(results);
                level.flags().specialStage = specialStage;
                int stop = frame[0] + 650;
                for (; frame[0] < stop; frame[0]++) {
                    int score = GameServices.gameState().getScore();
                    results.update(frame[0], sonic);
                    if (score != GameServices.gameState().getScore()) lastBonus = frame[0];
                }
                assertEquals(startingScore + 50_000, GameServices.gameState().getScore());
            }
            // The bonus outlasts the line timer. The final transfer raises it from 0 to 45.
            assertEquals(List.of(lastBonus + 15), requests);
        }
    }

    private record SoundRequest(AudioRequestObserver.RequestClass kind, int id) {}

    @Test
    void ringPickupsRequestOnlyTheNativeCue() throws Exception {
        TestEnvironment.resetAll();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var sonic = CdFixtureReplay.boot(config, disc, CdStagePayload.R11A);
            var levels = GameServices.level();
            CdFixtureReplay.releaseTitleCard(new com.openggf.tests.HeadlessTestRunner(sonic));
            var level = levels.getLevelGamestate();
            var rings = levels.getCurrentLevel().getRings().iterator();
            var placements = levels.getRingManager();
            List<SoundRequest> requests = new ArrayList<>();
            GameServices.audio().setRequestObserver((kind, id) -> requests.add(new SoundRequest(kind, id)));
            int lives = GameServices.gameState().getLives();

            var pickup = new SoundRequest(AudioRequestObserver.RequestClass.SFX, 0x95);
            var extraLife = new SoundRequest(AudioRequestObserver.RequestClass.MUSIC, 0x7A);
            var expected = List.of(List.of(pickup), List.of(extraLife), List.of(pickup),
                    List.of(pickup), List.of(extraLife), List.of(pickup));
            List<List<SoundRequest>> actual = new ArrayList<>();
            level.setRings(98);
            for (int frame = 0; frame < expected.size(); frame++) {
                if (frame == 3) level.setRings(198);
                assertTrue(placements.collectPlacedRing(rings.next(), sonic, frame));
                actual.add(List.copyOf(requests));
                requests.clear();
            }

            assertAll(
                    () -> assertEquals(expected, actual, "Sound requests at 99, 100, 101, 199, 200 and 201 rings"),
                    () -> assertEquals(lives + 2, GameServices.gameState().getLives()));
        }
    }

    @Test
    void lostRingPickupsRequestOnlyTheNativeCue() throws Exception {
        TestEnvironment.resetAll();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var sonic = CdFixtureReplay.boot(config, disc, CdStagePayload.R11A);
            var levels = GameServices.level();
            CdFixtureReplay.releaseTitleCard(new com.openggf.tests.HeadlessTestRunner(sonic));
            var level = levels.getLevelGamestate();
            level.setRings(2);
            levels.getRingManager().spawnLostRings(sonic, 2, 0);
            assertEquals(0, level.getRings());
            var lostRings = levels.getObjectManager().activeObjectsOfType(LostRingObjectInstance.class);
            assertEquals(2, lostRings.size());
            level.setRings(99);
            int lives = GameServices.gameState().getLives();
            List<SoundRequest> requests = new ArrayList<>();
            GameServices.audio().setRequestObserver((kind, id) -> requests.add(new SoundRequest(kind, id)));

            var awardingRing = lostRings.get(0);
            awardingRing.markCollected(0, sonic);
            awardingRing.update(1, sonic);
            assertEquals(100, level.getRings());
            assertEquals(lives + 1, GameServices.gameState().getLives());
            assertEquals(List.of(new SoundRequest(AudioRequestObserver.RequestClass.MUSIC, 0x7A)),
                    requests, "Lost-ring sound requests at 100 rings");

            requests.clear();
            var ordinaryRing = lostRings.get(1);
            ordinaryRing.markCollected(1, sonic);
            ordinaryRing.update(2, sonic);
            assertEquals(101, level.getRings());
            assertEquals(lives + 1, GameServices.gameState().getLives());
            assertEquals(List.of(new SoundRequest(AudioRequestObserver.RequestClass.SFX, 0x95)),
                    requests, "Lost-ring sound requests at 101 rings");
        }
    }

    @Test
    void workbenchRequestsBounceOnTheNativeStandingToRollingFrame() throws Exception {
        TestEnvironment.resetAll();
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var fixture = CdFixtureReplay.Fixture.under(root, CdStagePayload.R61A);
            var nativeTrace = TraceData.load(fixture.traceDir());
            int bounce = IntStream.range(1, nativeTrace.frameCount())
                    .filter(row -> !nativeTrace.getFrame(row - 1).rolling()
                            && nativeTrace.getFrame(row).rolling()
                            && (nativeTrace.getFrame(row).ySpeed() & 0xFFFF) == 0xEA00)
                    .findFirst().orElseThrow();
            List<Integer> requests = new ArrayList<>();
            int[] frame = {1};
            var replay = CdFixtureReplay.replay(disc, config, fixture, bounce + 3, true, -1, -1,
                    new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            if (row == 0) {
                                GameServices.audio().setRequestObserver((kind, id) -> {
                                    if (kind == AudioRequestObserver.RequestClass.SPECIAL_SFX && id == 0xDA) {
                                        requests.add(frame[0]);
                                    }
                                });
                            }
                            frame[0] = row + 1;
                        }
                    });
            assertTrue(replay.firstErrorRow() < 0, () -> "Native bounce replay diverged: " + replay.fields());
            assertEquals(List.of(bounce), requests);
        }
    }
}
