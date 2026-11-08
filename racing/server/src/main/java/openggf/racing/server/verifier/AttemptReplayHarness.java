package openggf.racing.server.verifier;

import openggf.timeattack.TimeAttackAttempt;
import openggf.timeattack.AttemptInputRecording;
import openggf.timeattack.TimeAttackLaunchRequest;
import openggf.timeattack.AttemptStartDescriptor;

import com.openggf.mods.run.DeterminismFingerprint;
import com.openggf.ModSubsystem;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.StockGameDataSources;
import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.ghost.GhostFrameCodec;
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
import java.util.Objects;
import java.util.function.Consumer;

/** Deterministic, input-only replay used by trusted verifier workers. */
public final class AttemptReplayHarness {
    public record Result(boolean finished, int firstInputFrame, int finishFrame,
                         int finalTimeFrames, String ghostStreamHashHex,
                         int framesSimulated, String failureReason,
                         java.util.List<Integer> lapFrames, java.util.List<Integer> splitFrames,
                         com.openggf.mods.run.RaceClock.Failure outcome, java.util.List<Integer> swapRequestFrames) {
        public Result(boolean finished,int firstInputFrame,int finishFrame,int finalTimeFrames,String hash,
                int frames,String failure,java.util.List<Integer> laps,java.util.List<Integer> splits,
                com.openggf.mods.run.RaceClock.Failure outcome) {
            this(finished,firstInputFrame,finishFrame,finalTimeFrames,hash,frames,failure,laps,splits,outcome,java.util.List.of());
        }
        public Result(boolean finished, int firstInputFrame, int finishFrame, int finalTimeFrames,
                String hash, int frames, String failure) {
            this(finished,firstInputFrame,finishFrame,finalTimeFrames,hash,frames,failure,
                    java.util.List.of(),java.util.List.of(),com.openggf.mods.run.RaceClock.Failure.NONE);
        }
    }

    private AttemptReplayHarness() {
    }

    public static synchronized Result replay(AttemptInputRecording recording,
                                             Path romPath) {
        return replay(recording, romPath, ignored -> { });
    }

    /** Observes immutable replay output for deployment probes; never supplies simulation state. */
    public static synchronized Result replay(AttemptInputRecording recording,
                                             Path romPath,
                                             Consumer<GhostFrame> frames) {
        return replay(recording,romPath,frames,java.util.List.of());
    }

    public static synchronized Result replay(AttemptInputRecording recording, Path romPath,
            Consumer<GhostFrame> frames, java.util.List<openggf.racing.server.master.VerificationJobQueue.SwapReceipt> receipts) {
        Objects.requireNonNull(frames, "frames");
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
            services.audio().setBackend(new com.openggf.audio.NullAudioBackend());
            services.roms().setRom(rom);
            services.audio().setRom(rom);
            GameModule rootModule = services.romDetection().detectAndCreateModule(rom)
                    .orElseThrow(() -> new IllegalArgumentException("unsupported ROM"));
            if (!rootModule.getGameId().code().equals(recording.start().gameId())) {
                return failure("track mismatch");
            }

            GameModule module = resolveAttemptModuleForReplay(
                    services, rootModule, recording.start());

            if (recording.start().trackKind() == com.openggf.mods.run.TrackKind.ARENA)
                return replayArena(recording, services, rootModule, frames, receipts);

            SessionManager.clear();
            GameplayModeContext mode = SessionManager.openGameplaySession(
                    rootModule, module, StockGameDataSources.pinned(rom, rootModule), null);
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
            // Replay under the policy the live run used, so act starts (an intro skipped or
            // played) match the recording's first frame.
            mode.beginGameplayRun(policy(recording.start()),
                    recording.start().zone(), recording.start().act());
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
            TimeAttackAttempt attempt = attempt(recording.start());
            var swaps = com.openggf.game.arena.RaceSwapRuntime.applier(module);
            if (swaps != null) swaps.reset();
            var requests = new java.util.ArrayList<Integer>();
            int eventIndex = 0;
            boolean respawn = false;
            MessageDigest ghostHash = MessageDigest.getInstance("SHA-256");
            byte[] encodedFrame = new byte[GhostFrameCodec.BYTES];
            int simulated = 0;
            for (int frameIndex = 0; frameIndex < recording.frameCount(); frameIndex++) {
                int mask = recording.heldMaskAt(frameIndex) & 0x1F;
                if (eventIndex < recording.events().size() && recording.events().get(eventIndex).step() == frameIndex) {
                    if (swaps == null) return failure("missing swap applier");
                    swaps.queueForStep(replayEvent(recording.events().get(eventIndex++).event(),receipts));
                }
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
                var failure = recording.start().deathRestart() != com.openggf.game.session.GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT
                        ? com.openggf.mods.run.RaceClock.Failure.NONE
                        : GameServices.gameState().getLives() == 0 ? com.openggf.mods.run.RaceClock.Failure.GAME_OVER
                        : GameServices.level().getLevelGamestate().isTimeOver() ? com.openggf.mods.run.RaceClock.Failure.TIME_OVER
                        : com.openggf.mods.run.RaceClock.Failure.NONE;
                attempt.onFrame(mask, endOfLevel, checkpointIndex,new com.openggf.mods.run.RaceClock(true,0,0,failure));
                if (swaps != null) {
                    if(swaps.consumeSignal()==com.openggf.mods.run.RaceSignal.SWAP_REQUEST)requests.add(simulated);
                    checkApplied(recording,simulated,swaps.consumeApplied());
                }
                GhostFrame ghostFrame = withRespawn(com.openggf.mods.run.NativePlayerPose.capture(player)
                        .toGhostFrame(false, Math.clamp(checkpointIndex + 1,0,15)), respawn);
                respawn = false;
                GhostFrameCodec.encode(ghostFrame, encodedFrame, 0);
                ghostHash.update(encodedFrame);
                frames.accept(ghostFrame);
                simulated++;
                if (recording.start().deathRestart() == com.openggf.game.session.GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT
                        && GameServices.level().consumeRespawnRequest()) {
                    GameServices.level().restartCurrentLevelFromCheckpointAfterDeath();
                    respawn = true;
                }
                if (attempt.phase() == TimeAttackAttempt.Phase.FINISHED
                        || attempt.phase() == TimeAttackAttempt.Phase.VOID) {
                    break;
                }
            }
            return new Result(attempt.phase() == TimeAttackAttempt.Phase.FINISHED,
                    attempt.firstInputFrame(), attempt.finishFrame(),
                    attempt.phase() == TimeAttackAttempt.Phase.FINISHED
                            ? attempt.finalTimeFrames() : -1,
                    HexFormat.of().formatHex(ghostHash.digest()), simulated, null,
                    java.util.Arrays.stream(attempt.lapFrames()).boxed().toList(),
                    java.util.Arrays.stream(attempt.splitFrames()).boxed().toList(),attempt.failure(),java.util.List.copyOf(requests));
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

    private static com.openggf.mods.run.RaceEvent replayEvent(com.openggf.mods.run.RaceEvent event,
            java.util.List<openggf.racing.server.master.VerificationJobQueue.SwapReceipt> receipts) {
        // A rejected original target must be re-admitted against replay terrain, not replaced
        // by an unconditional fallback command. No-partner receipts have no target.
        if (event instanceof com.openggf.mods.run.RaceEvent.SwapFallback fallback) {
            var receipt = receipts.stream().filter(r -> r.swapId() == fallback.swapId()).findFirst();
            if (receipt.isPresent() && !"EXPIRED".equals(receipt.get().outcome()) && receipt.get().target() != null) {
                var t = receipt.get().target();
                return new com.openggf.mods.run.RaceEvent.SwapApply(fallback.swapId(),
                        new com.openggf.mods.run.SwapTarget(t.centreX(),t.centreY(),t.xVel(),t.yVel(),
                                t.facingLeft(),t.secondaryPath(),t.priorityBucket(),t.highPriority(),
                                t.shoeKind(),t.shoeTimer(),t.progress()));
            }
        }
        return event;
    }

    private static void checkApplied(AttemptInputRecording recording,int frame,
            com.openggf.mods.run.RaceEventResult result) {
        var expected = recording.events().stream().filter(e -> e.step() == frame).findFirst();
        if (expected.isEmpty()) {
            if (result.outcome() != com.openggf.mods.run.RaceEventResult.Outcome.NONE)
                throw new IllegalArgumentException("race event diverged");
        } else if (!expected.get().event().equals(result.event())
                || result.outcome() == com.openggf.mods.run.RaceEventResult.Outcome.NONE) {
            throw new IllegalArgumentException("race event diverged");
        }
    }

    private static com.openggf.game.session.GameplayRunPolicy policy(AttemptStartDescriptor start) {
        var p = start.mode() == openggf.timeattack.CompetitionRoomSettings.Mode.COMPETITION
                ? openggf.timeattack.TimeAttackRuntime.competitionPolicy(start.monitors())
                : openggf.timeattack.TimeAttackRuntime.runPolicy();
        return new com.openggf.game.session.GameplayRunPolicy(p.specialStageEntry(),p.bonusStageEntry(),
                p.actCompletion(),p.liveRewind(),p.editorEntry(),start.deathRestart(),p.livesCounter(),
                p.zoneIntros(),start.monitors(),start.swapEnabled());
    }

    private static TimeAttackAttempt attempt(AttemptStartDescriptor start) {
        return new TimeAttackAttempt(start.trackKind() == com.openggf.mods.run.TrackKind.ARENA
                ? TimeAttackAttempt.TimingRule.RACE_CLOCK
                : start.mode() == openggf.timeattack.CompetitionRoomSettings.Mode.COMPETITION
                ? TimeAttackAttempt.TimingRule.RELEASE : TimeAttackAttempt.TimingRule.FIRST_INPUT);
    }

    private static GhostFrame withRespawn(GhostFrame f, boolean respawn) {
        return new GhostFrame(f.x(),f.y(),f.mappingFrame(),f.hFlip(),f.vFlip(),false,
                f.priorityBucket(),f.highPriority(),f.hurt(),f.progress(),f.secondaryPath(),f.shoeKind(),respawn);
    }

    private static Result replayArena(AttemptInputRecording recording, EngineContext services,
            GameModule root, Consumer<GhostFrame> frames,
            java.util.List<openggf.racing.server.master.VerificationJobQueue.SwapReceipt> receipts) throws Exception {
        SessionManager.clear();
        services.graphics().initHeadless();
        services.audio().setBackend(new com.openggf.audio.NullAudioBackend());
        var provider = root.getGameService(com.openggf.game.arena.HostedArenaProvider.class);
        if (provider == null) return failure("missing arena provider");
        var context = new com.openggf.game.nativemenu.NativeMenuHostContext(services,root,
                new com.openggf.control.InputHandler(), ignored -> {});
        try (var arena = provider.open(new com.openggf.game.arena.HostedArenaRequest(
                recording.start().zone(),recording.start().character(),
                recording.start().monitors() != com.openggf.mods.run.MonitorRule.OFF),context)) {
            var mode = SessionManager.getCurrentGameplayMode();
            mode.beginGameplayRun(policy(recording.start()),recording.start().zone(),0);
            var swaps = arena.raceSwaps();
            if (swaps != null) swaps.reset();
            var attempt = attempt(recording.start());
            var hash = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[GhostFrameCodec.BYTES];
            var requests = new java.util.ArrayList<Integer>();
            int eventIndex = 0, simulated = 0, previous = 0;
            for (int i=0; i<recording.frameCount(); i++) {
                int mask = recording.heldMaskAt(i) & 31;
                if (eventIndex < recording.events().size() && recording.events().get(eventIndex).step() == i) {
                    if (swaps == null) return failure("missing arena swap applier");
                    swaps.queueForStep(replayEvent(recording.events().get(eventIndex++).event(),receipts));
                }
                int pressed = mask & ~previous;
                var input = com.openggf.control.PlayerInputState.of(mask,pressed,
                        (mask & 16) != 0 ? com.openggf.control.InputActionMasks.ACTION_A : 0,
                        (pressed & 16) != 0 ? com.openggf.control.InputActionMasks.ACTION_A : 0,false,false);
                com.openggf.game.arena.ArenaStep[] output = new com.openggf.game.arena.ArenaStep[1];
                mode.plcFrameLifecycle().runLogicalIteration(() -> {}, frame -> {
                    com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(frame,
                            () -> output[0] = arena.step(input));
                    return null;
                });
                var step = output[0];
                attempt.onFrame(mask,arena.trackFinished(),-1,step.race());
                if (swaps != null) {
                    if(swaps.consumeSignal()==com.openggf.mods.run.RaceSignal.SWAP_REQUEST)requests.add(simulated);
                    checkApplied(recording,simulated,swaps.consumeApplied());
                }
                var ghost = withRespawn(step.pose().toGhostFrame(false,
                        Math.clamp(step.race().currentLap(),0,15)),arena.respawned());
                GhostFrameCodec.encode(ghost,bytes,0); hash.update(bytes); frames.accept(ghost);
                simulated++; previous = mask;
                if (attempt.phase() == TimeAttackAttempt.Phase.FINISHED || attempt.phase() == TimeAttackAttempt.Phase.VOID) break;
            }
            return new Result(attempt.phase() == TimeAttackAttempt.Phase.FINISHED,attempt.clockStartFrame(),
                    attempt.finishFrame(),attempt.phase() == TimeAttackAttempt.Phase.FINISHED ? attempt.finalTimeFrames() : -1,
                    HexFormat.of().formatHex(hash.digest()),simulated,null,
                    java.util.Arrays.stream(attempt.lapFrames()).boxed().toList(),
                    java.util.Arrays.stream(attempt.splitFrames()).boxed().toList(),attempt.failure(),java.util.List.copyOf(requests));
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
