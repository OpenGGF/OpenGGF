package openggf.racing.server.verifier;

import openggf.timeattack.AttemptStartDescriptor;
import openggf.timeattack.AttemptInputRecording;
import openggf.timeattack.TimeAttackLaunchRequest;
import openggf.timeattack.GhostStore;
import openggf.timeattack.TimeAttackRuntime;

import openggf.racing.server.verifier.AttemptReplayHarness;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.StockGameDataSources;
import com.openggf.mods.run.NativePlayerPose;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.run.RunLevelStart;
import com.openggf.mods.run.RunStep;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.RecordingFrameDriver;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAttemptReplayHarness {

    @Test
    void replayIsDeterministicAndDoctoredInputsDiverge() throws Exception {
        Path rom = s3kRom();
        String fingerprint = AttemptReplayHarness.fingerprintForRom(rom);
        AttemptInputRecording original = recording(fingerprint, false);
        AttemptInputRecording doctored = recording(fingerprint, true);

        AttemptReplayHarness.Result first = AttemptReplayHarness.replay(original, rom);
        var observed = new ArrayList<GhostFrame>();
        AttemptReplayHarness.Result second = AttemptReplayHarness.replay(original, rom, observed::add);
        AttemptReplayHarness.Result altered = AttemptReplayHarness.replay(doctored, rom);
        assertNull(first.failureReason());
        assertEquals(first, second);
        assertEquals(original.frameCount(), first.framesSimulated());
        assertEquals(first.framesSimulated(), observed.size());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encoded = new byte[GhostFrameCodec.BYTES];
        for (GhostFrame frame : observed) {
            GhostFrameCodec.encode(frame, encoded, 0);
            digest.update(encoded);
        }
        assertEquals(first.ghostStreamHashHex(), HexFormat.of().formatHex(digest.digest()));
        assertNotEquals(first.ghostStreamHashHex(), altered.ghostStreamHashHex());
    }

    @Test
    void nativeDamagedRecoveryAttemptMatchesLiveAndReplayBytesAndDigest(@TempDir Path root)
            throws Exception {
        Path rom = Path.of(System.getProperty("sonic1.rom.path", "s1.gen"));
        Assumptions.assumeTrue(Files.isRegularFile(rom), "S1 ROM unavailable");
        AttemptInputRecording recording = new AttemptInputRecording(new AttemptStartDescriptor(
                "s1", 1, 0, "sonic", AttemptReplayHarness.fingerprintForRom(rom)));
        for (int frame = 0; frame < 900; frame++) {
            recording.appendFrame(AbstractPlayableSprite.INPUT_RIGHT, false);
        }

        List<GhostFrame> live = captureLive(recording, rom, root);
        var replay = new ArrayList<GhostFrame>();
        AttemptReplayHarness.Result result = AttemptReplayHarness.replay(recording, rom, replay::add);
        assertNull(result.failureReason());
        assertEquals(recording.frameCount(), live.size());
        assertEquals(live.size(), replay.size());
        int damaged = -1;
        for (int index = 0; index < live.size(); index++) {
            if (live.get(index).hurt()) { damaged = index; break; }
        }
        assertTrue(damaged >= 0, "the ROM-backed attempt did not reach damage");
        boolean recovered = false;
        for (int index = damaged + 1; index < live.size(); index++) {
            if (!live.get(index).hurt()) { recovered = true; break; }
        }
        assertTrue(recovered, "the ROM-backed attempt did not recover");
        byte[] liveBytes = encodeFrames(live);
        assertArrayEquals(liveBytes, encodeFrames(replay));
        assertEquals(result.ghostStreamHashHex(), hash(liveBytes));
    }

    @Test
    void fingerprintMismatchRefusesToSimulate() {
        Path rom = s3kRom();
        AttemptInputRecording recording = recording("0.0:00000000", false);
        AttemptReplayHarness.Result result = AttemptReplayHarness.replay(recording, rom);
        assertEquals("fingerprint mismatch", result.failureReason());
        assertEquals(0, result.framesSimulated());
    }

    private static AttemptInputRecording recording(String fingerprint, boolean doctored) {
        AttemptInputRecording recording = new AttemptInputRecording(
                new AttemptStartDescriptor("s3k", 1, 0, "sonic", fingerprint));
        for (int frame = 0; frame < 600; frame++) {
            int mask = frame < 300 ? com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT : 0;
            if (doctored && frame >= 200 && frame < 260) {
                mask = com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT;
            }
            if (frame == 120 || frame == 400) {
                mask |= com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP;
            }
            recording.appendFrame(mask, false);
        }
        return recording;
    }

    private static List<GhostFrame> captureLive(AttemptInputRecording recording,
                                                Path romPath, Path root) throws Exception {
        AttemptReplayHarness.disableExternalContentForDeterminism();
        SonicConfigurationService configuration = null;
        Object oldMain = null;
        Object oldSidekick = null;
        var observed = new ArrayList<GhostFrame>();
        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romPath.toString()));
            EngineContext services = EngineServices.current();
            services.roms().setRom(rom);
            services.audio().setRom(rom);
            GameModule rootModule = services.romDetection().detectAndCreateModule(rom).orElseThrow();
            GameModule module = AttemptReplayHarness.resolveAttemptModuleForReplay(
                    services, rootModule, recording.start());
            SessionManager.clear();
            var mode = SessionManager.openGameplaySession(rootModule, module,
                    StockGameDataSources.pinned(rom, rootModule), null);
            GameplaySessionFactory.attachManagers(mode, services);
            services.graphics().initHeadless();
            TraceReplaySessionBootstrap.resetLevelSubsystemsForReplay();

            configuration = services.configuration();
            oldMain = configuration.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
            oldSidekick = configuration.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    recording.start().character());
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            AbstractPlayableSprite player = GameplayTeamBootstrap.registerActiveTeam(
                    module, GameServices.sprites(), configuration).mainSprite();
            mode.beginGameplayRun(TimeAttackRuntime.runPolicy(),
                    recording.start().zone(), recording.start().act());
            GameServices.level().loadZoneAndAct(recording.start().zone(), recording.start().act());
            GameServices.camera().setFocusedSprite(player);
            GameServices.camera().setFrozen(false);
            GameServices.camera().updatePosition(true);
            GameServices.level().consumeTitleCardRequest();
            GameServices.level().consumeInLevelTitleCardRequest();
            if (module.getTitleCardProvider().isOverlayActive()) {
                module.getTitleCardProvider().reset();
            }

            TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                    root.resolve("identity"), () -> false);
            runtime.armForLaunch(new TimeAttackLaunchRequest(recording.start().gameId(),
                    recording.start().zone(), recording.start().act(),
                    recording.start().character(), List.of()));
            runtime.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
                @Override public void onAttemptBegan(int ordinal) { }
                @Override public void onFrameSampled(int ordinal, GhostFrame frame) {
                    observed.add(frame);
                }
                @Override public void onAttemptFinished(int ordinal, int timeFrames,
                        int firstInputFrame, int finishFrame, byte[] inputRecordingSha256,
                        AttemptInputRecording inputRecording) { }
                @Override public void onAttemptVoided(int ordinal) { }
            });
            runtime.onLevelReady(new RunLevelStart(runtime.runSpec(),
                    recording.start().fingerprint(), false, 0, 0, false));
            RecordingFrameDriver driver = new RecordingFrameDriver(player);
            for (int index = 0; index < recording.frameCount(); index++) {
                int mask = recording.heldMaskAt(index);
                driver.stepFrame((mask & AbstractPlayableSprite.INPUT_UP) != 0,
                        (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                        (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                        (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                        (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
                boolean completed = GameServices.gameState().isEndOfLevelActive()
                        || GameServices.gameState().isActCompletionSignalActive();
                var checkpoint = GameServices.level().getCheckpointState();
                int checkpointIndex = checkpoint == null ? -1
                        : checkpoint.getLastCheckpointIndex();
                runtime.afterStep(new RunStep(index, mask, false,
                        NativePlayerPose.capture(player), completed, checkpointIndex, false));
                if (!runtime.isAttemptActive()) break;
            }
            runtime.deactivate();
            return List.copyOf(observed);
        } finally {
            if (configuration != null) {
                configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldMain);
                configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, oldSidekick);
            }
            SessionManager.clear();
        }
    }

    private static byte[] encodeFrames(List<GhostFrame> frames) {
        byte[] bytes = new byte[frames.size() * GhostFrameCodec.BYTES];
        for (int index = 0; index < frames.size(); index++) {
            GhostFrameCodec.encode(frames.get(index), bytes, index * GhostFrameCodec.BYTES);
        }
        return bytes;
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path s3kRom() {
        String configured = System.getProperty("s3k.rom.path", "s3k.gen");
        Path path = Path.of(configured);
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "S3K ROM unavailable; skipping replay harness");
        return path;
    }
}
