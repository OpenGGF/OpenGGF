package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.InputRun;
import com.openggf.tests.route.RecentFrameLog;
import com.openggf.tests.route.SidekickAudit;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * AIZ act 1 route pilot on the shared route primitives (second-zone pilot of
 * the technique in docs/architecture/research/2026-09-13-live-state-route-controllers.md).
 *
 * <p>The fallback program is derived from the {@code aiz1_to_hcz_fullrun}
 * fixture's own BK2: the act-1 P1 pad rows from the first level frame to the
 * recorded act-2 reload. The recorder's leading rows (the SEGA/title/level
 * setup before {@code Game_Mode} reaches Level, 289 rows on this fixture)
 * are never ticked by the engine, exactly as the sanctioned trace replay
 * treats them ({@code TraceReplayBootstrap.preLevelFrameCountForTraceReplay}),
 * so program row {@code r} normally plays at engine index {@code r - preLevel}.
 * A late live intro exit inserts neutral holds before the first player input. Nothing
 * is hydrated from physics rows; the trace is read only for its metadata,
 * its pre-level prefix length and the row of its act change.
 *
 * <p>Live-state gates and assertions:
 * <ul>
 * <li>preserve recorded neutral rows; hold the first player-driven row until the engine has
 *     set the ROM's {@code Level_started_flag} ({@code Camera.isLevelStarted},
 *     set by the Knuckles cutscene's exit handoff), which is the engine's own
 *     intro boundary rather than a proxy built from control-lock flags;</li>
 * <li>P1 must not die on any stepped frame;</li>
 * <li>the act-2 reload ({@code LevelManager.getCurrentAct() == 1}) must be
 *     observed within the program's length plus {@link #RELOAD_SLACK_FRAMES};
 *     the run stops on that frame, before the level replaces its object
 *     manager;</li>
 * <li>the CPU sidekick team keeps identity, controller ownership and leader
 *     chain every frame and respawns after any death.</li>
 * </ul>
 * The engine runs under its live load-time simulation, not the recorded lag
 * frames, so it reaches the reload a few dozen rows before the recording;
 * the measured lead is reported only for uninterrupted playback, not asserted.
 * Compatibility routes may continue with live object steering when recording
 * playback ends or asks for an unavailable ability. The native representative
 * explicitly asserts that this continuation remains unused.
 *
 * <p>The native 320px Sonic+Tails representative row of the AIZ1 matrix.
 * Run with {@code mvn -Dmse=off -Dtest=TestS3kAiz1RoutePilot
 * -Ds3k.rom.path=... test}; no opt-in property is needed.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("slow-suite")
class TestS3kAiz1RoutePilot {

    private static final Path FIXTURE_DIRECTORY =
            Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun");
    /**
     * Frames the engine may take beyond the recorded act-1 length to reach the
     * reload. An 80-second simulation watchdog allows authored backtracking,
     * spring ascent and the full FireBreath cutscene after recording handoff.
     * It never selects input or changes gameplay; exhaustion remains a failure.
     */
    private static final int RELOAD_SLACK_FRAMES = 4800;
    /** Longest dead streak the CPU respawn contract tolerates, as in the FBZ2 route. */
    private static final int SIDEKICK_RESPAWN_LIMIT = 0x100;

    @Test
    void act1RouteReachesTheAct2Reload() throws IOException {
        var savedConfig = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        try {
            RouteSetup setup = prepareRoute();
            RouteResult result = runRoute(setup);
            assertFalse(result.continuationUsed(), "native recording unexpectedly needed route recovery");
        } finally {
            TraceReplaySessionBootstrap.restoreGameplayConfig(savedConfig);
        }
    }

    /** Shares only the recorded input recipe; each caller creates a fresh gameplay fixture. */
    static RouteSetup prepareRoute() throws IOException {
        RouteSetup setup = prepareRoute(() -> { });
        org.junit.jupiter.api.Assertions.assertEquals(320, setup.fixture().camera().getWidth());
        return setup;
    }

    @FunctionalInterface
    interface Configuration { void apply() throws IOException; }

    static RouteResult runRoute(RouteSetup setup) {
        var runner = new Aiz1RouteRunner(setup.fixture(), setup.program(), setup.preLevelRows(), setup.recordedReloadRow());
        runner.run();
        return new RouteResult(runner.frames, runner.continuation.used());
    }

    record RouteResult(int frames, boolean continuationUsed) { }

    static RouteSetup prepareRoute(Configuration configuration) throws IOException {
        TraceData trace = TraceData.load(FIXTURE_DIRECTORY);
        Bk2Movie movie = new Bk2MovieLoader().load(findBk2(FIXTURE_DIRECTORY));
        int preLevelRows = TraceReplayBootstrap.preLevelFrameCountForTraceReplay(trace);
        int recordedReloadRow = recordedAct2Row(trace);
        int offset = trace.metadata().bk2FrameOffset();
        List<InputRun> program = InputProgram.fromRecording(
                movie, offset + preLevelRows, offset + recordedReloadRow);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
        configuration.apply();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .withCrossGameDonation(GameServices.configuration().getBoolean(
                        com.openggf.configuration.SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)
                        ? GameServices.configuration().getString(
                                com.openggf.configuration.SonicConfiguration.CROSS_GAME_SOURCE) : null)
                .build();
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                com.openggf.sprites.playable.Sonic.class, fixture.sprite());
        org.junit.jupiter.api.Assertions.assertEquals(1, GameServices.sprites().getSidekicks().size());
        org.junit.jupiter.api.Assertions.assertInstanceOf(com.openggf.sprites.playable.Tails.class,
                GameServices.sprites().getSidekicks().getFirst());
        return new RouteSetup(fixture, program, preLevelRows, recordedReloadRow);
    }

    record RouteSetup(HeadlessTestFixture fixture, List<InputRun> program,
                      int preLevelRows, int recordedReloadRow) { }

    private static Path findBk2(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".bk2")).findFirst()
                    .orElseThrow(() -> new IOException("no .bk2 in " + directory));
        }
    }

    /** First recorded row on which the ROM reports act 2 in Level mode. */
    private static int recordedAct2Row(TraceData trace) {
        for (int row = 0; row < trace.frameCount(); row++) {
            for (TraceEvent event : trace.getEventsForFrame(row)) {
                if (event instanceof TraceEvent.ZoneActState state
                        && state.actualAct() != null && state.actualAct() == 1
                        && state.gameMode() != null && state.gameMode() == 12) {
                    return row;
                }
            }
        }
        throw new IllegalStateException("fixture never reports act 2");
    }

    private static final class Aiz1RouteRunner {
        private final HeadlessTestFixture fixture;
        private final Aiz1IntroProgram program;
        private final int programFrames;
        private final int recordedReloadRow;
        private final Aiz1RouteContinuation continuation = new Aiz1RouteContinuation();
        private final RecentFrameLog recentLog = new RecentFrameLog(30);
        private final SidekickAudit sidekicks = new SidekickAudit(SIDEKICK_RESPAWN_LIMIT);
        private int frames;
        private boolean playerInputSeen;
        private int reloadFrame = -1;
        private int lastAct = -1;
        private boolean lastControlled;
        private boolean lastLevelStarted;

        Aiz1RouteRunner(HeadlessTestFixture fixture, List<InputRun> program,
                        int firstRow, int recordedReloadRow) {
            this.fixture = fixture;
            this.program = new Aiz1IntroProgram(program, firstRow);
            this.programFrames = InputProgram.frames(program);
            this.recordedReloadRow = recordedReloadRow;
        }

        void run() {
            int frameLimit = programFrames + RELOAD_SLACK_FRAMES;
            while (frames < frameLimit) {
                AbstractPlayableSprite player = fixture.sprite();
                int row = program.row();
                int mask = continuation.next(player, program);
                if (mask != 0 && !playerInputSeen) {
                    playerInputSeen = true;
                    assertTrue(GameServices.camera().isLevelStarted(),
                            () -> "the recording's first player input (row " + row
                                    + ") precedes the engine's Level_started_flag: " + diagnostic(player));
                }
                InputProgram.step(fixture, mask);
                frames++;
                recentLog.record(frames, mask, player);
                sidekicks.observe(player, false, false, GameServices.camera().getX() & 0xFFFF);
                int act = GameServices.level().getCurrentAct();
                log(player, act, mask);
                if (player.getDead()) {
                    fail("P1 died: " + diagnostic(player));
                }
                if (act == 1) {
                    reloadFrame = frames;
                    break;
                }
            }
            AbstractPlayableSprite player = fixture.sprite();
            System.out.printf("AIZTL end f=%d row=%d p=(%04X,%04X) act=%d reloadFrame=%d recordedReloadRow=%d recordedLead=%s introHeld=%d %s%n",
                    frames, program.row(), player.getCentreX() & 0xFFFF,
                    player.getCentreY() & 0xFFFF, GameServices.level().getCurrentAct(), reloadFrame,
                    recordedReloadRow, continuation.used() || reloadFrame < 0 ? "n/a" : Integer.toString(recordedReloadRow - program.row()),
                    program.heldFrames(), continuation.diagnostic());
            assertTrue(reloadFrame >= 0, () -> "act-2 reload never observed within "
                    + frameLimit + " frames: " + diagnostic(player));
            assertTrue(sidekicks.identityOrderPreserved(), "sidekick identity/order changed");
            assertTrue(sidekicks.controllerEveryFrame(), "a sidekick lost CPU-controller ownership");
            assertTrue(sidekicks.leaderChainEveryFrame(), "the sidekick leader chain diverged");
            assertTrue(sidekicks.respawnedAfterEveryDeath(),
                    () -> "a sidekick stayed dead beyond the respawn window: " + sidekicks.deathEvidence());
        }

        private void log(AbstractPlayableSprite player, int act, int mask) {
            boolean controlled = player.isObjectControlled();
            boolean levelStarted = GameServices.camera().isLevelStarted();
            boolean changed = act != lastAct || controlled != lastControlled || levelStarted != lastLevelStarted;
            if (frames % 500 == 0 || changed) {
                System.out.printf("AIZTL f=%d row=%d act=%d ctrl=%s started=%s mask=%02X p=(%04X,%04X) v=(%04X,%04X)%n",
                        frames, program.row(), act, controlled, levelStarted, mask,
                        player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF,
                        player.getXSpeed() & 0xFFFF, player.getYSpeed() & 0xFFFF);
                lastAct = act;
                lastControlled = controlled;
                lastLevelStarted = levelStarted;
            }
        }

        private String diagnostic(AbstractPlayableSprite player) {
            return "frame=" + frames + " row=" + program.row()
                    + " " + continuation.diagnostic()
                    + " programExhausted=" + program.exhausted()
                    + " player=($" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                    + ",$" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ")"
                    + " air=" + player.getAir() + " hurt=" + player.isHurt()
                    + " act=" + GameServices.level().getCurrentAct()
                    + " recent=" + recentLog;
        }
    }
}
