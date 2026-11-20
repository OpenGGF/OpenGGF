package com.openggf.game.soniccd.audio;

import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Native audio requests from the Sonic CD level program and its objects. */
public final class CdSoundRequests {
    public static final int YES_VOICE = CdSubCpuAudio.commandId("SCMD_YESSFX");
    public static final int FADE_PCM = CdSubCpuAudio.commandId("SCMD_FADEPCM");
    /** {@code SCMD_RESULTMUS} (_Include/System.inc:251): StartResults' results music (Level End/Main.asm:827-828). */
    public static final int RESULTS_MUSIC = CdSubCpuAudio.commandId("SCMD_RESULTMUS");
    public static final int INVINCIBILITY = CdSubCpuAudio.commandId("SCMD_INVINCMUS");
    public static final int SPEED_SHOES = CdSubCpuAudio.commandId("SCMD_SHOESMUS");
    /** {@code SCMD_BREAKSFX} ($92, System.inc:290): the glass break sound. */
    public static final int GLASS_BREAK = CdSubCpuAudio.commandId("SCMD_BREAKSFX");
    /** {@code SCMD_GIVEUPSFX} (_Include/System.inc:265): the give-up's "I'm outta here" (Sonic/Main.asm:46-47). */
    public static final int GIVE_UP_VOICE = CdSubCpuAudio.commandId("SCMD_GIVEUPSFX");
    /** {@code FM_SHIELD} (FM/_Sound IDs.inc:17). */
    public static final int SHIELD = 0x97;
    static final int RESULTS_CHIME = 0x9A;

    /** {@code FM_SIGNPOST} (FM/_Sound IDs.inc:23). */
    public static final int SIGNPOST = 0x9D;

    private static final int PAUSE_CDDA = CdSubCpuAudio.commandId("SCMD_PAUSECDA");
    private static final int UNPAUSE_CDDA = CdSubCpuAudio.commandId("SCMD_UNPAUSECDA");
    private static final int PAUSE_PCM = CdSubCpuAudio.commandId("SCMD_PAUSEPCM");
    private static final int UNPAUSE_PCM = CdSubCpuAudio.commandId("SCMD_UNPAUSEPCM");

    private CdSoundRequests() { }

    /**
     * {@code PauseMusic} (Level/Main.asm:620-643) and {@code UnpauseMusic} (Level/Main.asm:652-672)
     * send one command per pause transition. The Past uses PCM unless a power-up plays a CD track.
     */
    public static void setLevelMusicPaused(ObjectServices services, AbstractPlayableSprite player, boolean paused) {
        if (inPast(services) && player.getInvincibleFrames() == 0 && !player.hasSpeedShoes()) {
            services.playMusic(paused ? PAUSE_PCM : UNPAUSE_PCM);
        } else {
            services.playMusic(paused ? PAUSE_CDDA : UNPAUSE_CDDA);
        }
    }

    /**
     * {@code .GiveInvinc}/{@code .GiveSpeedShoes}: in the past the PCM music is faded first,
     * then the power-up's CD track starts (Level/_Objects/Monitor and Time Post/Main.asm:534-591).
     */
    public static void powerUpMusic(ObjectServices services, int command) {
        if (inPast(services)) services.playMusic(FADE_PCM);
        services.playMusic(command);
    }

    /** {@code .CheckS}: {@code .GiveShield}, {@code .GiveInvinc}, then {@code .GiveSpeedShoes} (:612-615). */
    public static void sMonitor(ObjectServices services) {
        services.playSfx(SHIELD);
        powerUpMusic(services, INVINCIBILITY);
        powerUpMusic(services, SPEED_SHOES);
    }

    /**
     * ObjResults_Bonus .ChkDone writes FM_KACHING directly to the FM driver in the USA build
     * (Level/_Objects/Results/Main.asm:234-250).
     */
    public static void resultsChime(ObjectServices services) {
        AudioManagerInternalAccess.playSecondaryNativeRequest(services.audioManager(), RESULTS_CHIME);
    }

    /** {@code .Give1UP}'s voice clip (Level/_Objects/Monitor and Time Post/Main.asm:489-494). */
    public static void oneUp(ObjectServices services) {
        services.playMusic(YES_VOICE);
    }

    /** {@code move.w #$92,d0; jsr SubCpuCommand}: a glass block breaks (r3/player_block_check.asm:346-347). */
    public static void glassBreak(ObjectServices services) {
        services.playMusic(GLASS_BREAK);
    }

    /** {@code tst.b timeZone}: zero is the past. */
    static boolean inPast(ObjectServices services) {
        var levels = services.levelManager();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel level
                && level.payload().stage().period() == CdPeriod.PAST;
    }
}
