package com.openggf.game.soniccd.program;

import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectServices;

import java.util.function.IntConsumer;

/**
 * Where a stage program returns to IPX: the level owners that end a stage call these, and the
 * {@link CdSceneCoordinator} turns the {@link CdStageResult} into the engine request. Each method is
 * one native return path of {@code LevelStart} (Level/Main.asm:58-112): the results object's
 * {@code levelRestart} 2 (next level), the death timer's {@code levelRestart} 1 with no lives left
 * ({@code .CheckNoLives}), and the time warp's {@code levelRestart} with {@code timeZone} bit 7.
 * A death with lives left never leaves the stage: {@code LevelStart} reloads it
 * ({@code .CheckNormalLoad}), which is the engine's death reload.
 */
public final class CdStageExit {
    private CdStageExit() { }

    /**
     * {@code ObjResults_NextLevel} (Results/Main.asm:277-345), then {@code LevelStart}'s
     * {@code cmpi.w #2,levelRestart} return to IPX (Level/Main.asm:91-95). The time owner moves to
     * the next act first ({@link CdTimeTravel#actCompleted}); IPX reads {@code goodFuture} after it
     * for the act 3 file ({@code RunBossLevel}, IPX.asm:606-611) and before an act 3 clear for the
     * zone's good future ({@code bclr #0,goodFuture}, IPX.asm:183-187). {@code specialStage} is the
     * big ring's flag (Level End/Main.asm:497); {@code time} is the level's {@code 00 mm ss ff}.
     * {@code ResultsFadeOutMusic} ({@code FadeOutMusic}, Results/Main.asm:20, 314) is the engine's music
     * fade on the requested transition, which the audio profile sends as {@code SCMD_FADECDA}.
     */
    public static CdProgramRequest actCleared(ObjectServices services) {
        GameModule module = services.gameModule();
        CdSceneCoordinator scenes = coordinator(module);
        CdTimeTravel tt = CdTimeTravel.of(module);
        LevelManager levels = services.levelManager();
        if (scenes == null || tt == null || levels == null || !(levels.getCurrentLevel() instanceof CdLevel level)) return null;
        int act = level.payload().stage().actNumber();
        boolean act3GoodFuture = tt.goodFuture();
        tt.actCompleted(level.payload().stage().zone(), act);
        boolean goodFuture = act >= 3 ? act3GoodFuture : tt.goodFuture();
        CdStageResult result = new CdStageResult(lives(services.gameState()), CdStageResult.TIME_PRESENT, goodFuture,
                level.flags().specialStage, tt.timeStones(), time(services.levelGamestate()));
        return ipxRunsTheStage(scenes) ? scenes.stageEnded(result, levels) : null;
    }

    /**
     * {@code ObjSonic_Restart} with no lives left (scddisasm/src/Level/_Objects/Sonic/Main.asm:2448-2475), then {@code LevelStart}'s
     * {@code .CheckNoLives} return (Level/Main.asm:97-112) to {@code RunLevel .LevelOver}: IPX
     * {@code GameOver} (IPX.asm:332-338) or, in Time Attack, {@code TimeAttack}'s return to its menu
     * (IPX.asm:1235-1238).
     * The time owner's game-over clears run here ({@link CdTimeTravel#gameOver}).
     */
    public static CdProgramRequest outOfLives(ObjectServices services) {
        GameModule module = services.gameModule();
        CdSceneCoordinator scenes = coordinator(module);
        CdTimeTravel tt = CdTimeTravel.of(module);
        LevelManager levels = services.levelManager();
        if (scenes == null || tt == null || levels == null) return null;
        int timeZone = tt.timeZone().nativeValue();
        boolean goodFuture = tt.goodFuture();
        if (!scenes.flow().timeAttackMode()) tt.gameOver();
        CdStageResult result = CdStageResult.gameOver(timeZone, goodFuture, tt.timeStones())
                .withTime(time(services.levelGamestate()));
        return ipxRunsTheStage(scenes) ? scenes.stageEnded(result, levels) : null;
    }

    /**
     * Once per level frame after the object pass ({@code Level_MainLoop}'s {@code levelRestart} test,
     * Level/Main.asm:405-408): the time owner's audio requests go to the sub CPU by command
     * (Monitor and Time Post/Main.asm:162-170; Sonic/Main.asm:604-605, 619-620), and a ready warp
     * leaves the stage: {@code LevelStart} sees {@code timeZone} bit 7 and returns
     * (Level/Main.asm:61-80) to {@code RunLevel}, which runs {@code SCMD_WARP} (IPX.asm:551-561).
     * Returns true on the frame the stage is left.
     */
    public static boolean afterObjectPass(GameModule module, LevelManager levels, GameStateManager game,
                                          IntConsumer subCpuCommand) {
        CdTimeTravel tt = CdTimeTravel.of(module);
        CdSceneCoordinator scenes = coordinator(module);
        if (tt == null || scenes == null || levels == null) return false;
        for (CdTimeTravel.Sound sound : tt.drainSounds()) subCpuCommand.accept(command(sound));
        if (tt.readyWarp() == null || !ipxRunsTheStage(scenes) || !scenes.warpLeavesStage(tt.readyWarp())) return false;
        CdStageResult result = CdStageResult.warp(lives(game), tt.timeZone().nativeValue(), tt.goodFuture(), tt.timeStones());
        scenes.stageEnded(result, levels);
        return true;
    }

    /**
     * A stage loaded without IPX (a fixture boot, a tool or the editor loading a stage directly) has no
     * caller to return to; its end is not reported.
     */
    private static boolean ipxRunsTheStage(CdSceneCoordinator scenes) {
        return scenes.flow().route() != null;
    }

    private static int command(CdTimeTravel.Sound sound) {
        return CdSubCpuAudio.commandId(switch (sound) {
            case PAST_VOICE -> "SCMD_PASTSFX";
            case FUTURE_VOICE -> "SCMD_FUTURESFX";
            case FADE_PCM -> "SCMD_FADEPCM";
            case FADE_OUT_MUSIC -> "SCMD_FADECDA";               // FadeOutMusic (Level/Sub CPU.asm:11-12)
        });
    }

    private static int lives(GameStateManager game) {
        return game == null ? 0 : Math.min(game.getLives(), 0xFF);
    }

    private static int time(LevelState state) {
        return state == null ? 0 : nativeTime(state.getTimerFrames());
    }

    /** {@code time}: {@code 00 mm ss ff} (Main CPU Variables.inc:66-69) from elapsed frames. */
    static int nativeTime(long frames) {
        long minutes = Math.min(frames / 3600, 0xFF);
        return (int) ((minutes << 16) | ((frames / 60 % 60) << 8) | (frames % 60));
    }

    private static CdSceneCoordinator coordinator(GameModule module) {
        return module == null ? null : module.getGameService(CdSceneCoordinator.class);
    }
}
