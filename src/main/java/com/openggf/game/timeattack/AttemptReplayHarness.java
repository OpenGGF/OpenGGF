package com.openggf.game.timeattack;

import com.openggf.ModSubsystem;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.ghost.GhostFrameCodec;
import com.openggf.game.ghost.GhostFrameSampler;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.SessionManager;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.RecordingFrameDriver;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.version.AppVersion;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Deterministic, input-only replay used by trusted verifier workers. */
public final class AttemptReplayHarness {
    public record Result(boolean finished, int firstInputFrame, int finishFrame,
                         int finalTimeFrames, String ghostStreamHashHex,
                         int framesSimulated, String failureReason) {
    }

    private AttemptReplayHarness() {
    }

    public static synchronized Result replay(AttemptInputRecording recording,
                                             Path romPath) {
        disableExternalContentForDeterminism();
        Rom rom = new Rom();
        SonicConfigurationService configuration = null;
        Object oldMain = null;
        Object oldSidekick = null;
        try {
            if (!rom.open(romPath.toString())) {
                return failure("ROM open failed");
            }
            String actualFingerprint = new DeterminismFingerprint(
                    AppVersion.get(), rom.calculateChecksum()).asString();
            if (!actualFingerprint.equals(recording.start().fingerprint())) {
                return failure("fingerprint mismatch");
            }

            EngineContext services = EngineServices.current();
            services.roms().setRom(rom);
            GameModule rootModule = services.romDetection().detectAndCreateModule(rom)
                    .orElseThrow(() -> new IllegalArgumentException("unsupported ROM"));
            if (!rootModule.getGameId().code().equals(recording.start().gameId())) {
                return failure("track mismatch");
            }

            GameModule module = resolveAttemptModuleForReplay(
                    services, rootModule, recording.start());

            SessionManager.clear();
            GameplayModeContext mode = SessionManager.openGameplaySession(
                    rootModule, module, null);
            GameplaySessionFactory.attachManagers(mode, services);
            services.graphics().initHeadless();
            TraceReplaySessionBootstrap.resetLevelSubsystemsForReplay();

            configuration = services.configuration();
            oldMain = configuration.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
            oldSidekick = configuration.getConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE);
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    recording.start().character());
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            GameplayTeamBootstrap.BootstrappedTeam team =
                    GameplayTeamBootstrap.registerActiveTeam(
                            module, GameServices.sprites(), configuration);
            AbstractPlayableSprite player = team.mainSprite();
            GameServices.level().loadZoneAndAct(
                    recording.start().zone(), recording.start().act());
            GameServices.camera().setFocusedSprite(player);
            GameServices.camera().setFrozen(false);
            GameServices.camera().updatePosition(true);
            GameServices.level().consumeTitleCardRequest();
            GameServices.level().consumeInLevelTitleCardRequest();
            if (module.getTitleCardProvider().isOverlayActive()) {
                module.getTitleCardProvider().reset();
            }

            RecordingFrameDriver driver = new RecordingFrameDriver(player);
            TimeAttackAttempt attempt = new TimeAttackAttempt();
            MessageDigest ghostHash = MessageDigest.getInstance("SHA-256");
            byte[] encodedFrame = new byte[GhostFrameCodec.BYTES];
            int simulated = 0;
            for (int frameIndex = 0; frameIndex < recording.frameCount(); frameIndex++) {
                int mask = recording.heldMaskAt(frameIndex) & 0x1F;
                driver.stepFrame((mask & AbstractPlayableSprite.INPUT_UP) != 0,
                        (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                        (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                        (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                        (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
                boolean endOfLevel = GameServices.gameState().isEndOfLevelActive()
                        || GameServices.gameState().isActCompletionSignalActive();
                var checkpoint = GameServices.level().getCheckpointState();
                int checkpointIndex = checkpoint == null ? -1
                        : checkpoint.getLastCheckpointIndex();
                attempt.onFrame(mask, endOfLevel, checkpointIndex);
                GhostFrameCodec.encode(GhostFrameSampler.sample(player, false),
                        encodedFrame, 0);
                ghostHash.update(encodedFrame);
                simulated++;
                if (attempt.phase() == TimeAttackAttempt.Phase.FINISHED
                        || attempt.phase() == TimeAttackAttempt.Phase.VOID) {
                    break;
                }
            }
            return new Result(attempt.phase() == TimeAttackAttempt.Phase.FINISHED,
                    attempt.firstInputFrame(), attempt.finishFrame(),
                    attempt.phase() == TimeAttackAttempt.Phase.FINISHED
                            ? attempt.finalTimeFrames() : -1,
                    HexFormat.of().formatHex(ghostHash.digest()), simulated, null);
        } catch (Exception failure) {
            return failure(failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
        } finally {
            if (configuration != null) {
                configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, oldMain);
                configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                        oldSidekick);
            }
            SessionManager.clear();
            rom.close();
        }
    }

    static void disableExternalContentForDeterminism() {
        ModSubsystem.disableCurrentSessionForDeterminism();
    }

    public static String fingerprintForRom(Path romPath) {
        try (Rom rom = new Rom()) {
            if (!rom.open(romPath.toString())) {
                throw new IllegalArgumentException("ROM open failed");
            }
            return new DeterminismFingerprint(
                    AppVersion.get(), rom.calculateChecksum()).asString();
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("ROM checksum failed", failure);
        }
    }

    static GameModule resolveAttemptModuleForReplay(EngineContext services,
            GameModule rootModule, AttemptStartDescriptor start) {
        ModuleResolutionService moduleResolutionService = services.moduleResolutionService();
        return moduleResolutionService.resolveForLaunch(rootModule,
                new GameplayLaunchRequest(start.gameId(), start.character(), java.util.List.of()),
                ModuleResolutionService.LaunchPolicy.DETERMINISTIC);
    }

    private static Result failure(String reason) {
        return new Result(false, -1, -1, -1, "", 0, reason);
    }
}
