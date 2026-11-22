package com.openggf.game.soniccd.audio;

import com.openggf.audio.AudioManagerInternalAccess;
import com.openggf.audio.GameSound;
import com.openggf.game.GameModule;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.timetravel.CdTimeZone;
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

    /** Stage write of 1 to jumpCondition, releasing tracks held at a conditional jump. */
    public static void releaseHeldFmEffects(GameModule module) {
        if (module != null && module.getAudioProfile() instanceof SonicCdAudioProfile profile) {
            profile.releaseHeldFmEffects();
        }
    }

    /** {@code PauseMusic} (Level/Main.asm:620-625) requests {@code FM_CHARGESTOP} on every paused frame. */
    public static void stopChargeSound(ObjectServices services) {
        services.playSfx(GameSound.CHARGE_STOP);
    }

    /**
     * {@code PauseMusic} (Level/Main.asm:620-643) and {@code UnpauseMusic} (Level/Main.asm:652-672)
     * use the live time zone with the warp bit cleared.
     * {@code ObjSonic_TimeWarp} (Level/_Objects/Sonic/Main.asm:639-643) sets the destination
     * before the new stage loads.
     */
    public static void setLevelMusicPaused(ObjectServices services, AbstractPlayableSprite player, boolean paused) {
        var timeTravel = CdTimeTravel.of(services.gameModule());
        if (timeTravel.timeZone() == CdTimeZone.PAST && player.getInvincibleFrames() == 0 && !player.hasSpeedShoes()) {
            services.playMusic(paused ? PAUSE_PCM : UNPAUSE_PCM);
        } else {
            services.playMusic(paused ? PAUSE_CDDA : UNPAUSE_CDDA);
        }
    }

    /**
     * ObjMonitorItem_Main .GiveInvinc/.GiveSpeedShoes tests the whole timeZone byte
     * before fading PCM (Level/_Objects/Monitor and Time Post/Main.asm:554-561,582-589).
     */
    public static void powerUpMusic(ObjectServices services, int command) {
        var timeTravel = CdTimeTravel.of(services.gameModule());
        if (timeTravel != null && timeTravel.timeZone() == CdTimeZone.PAST && !timeTravel.warping()) {
            services.playMusic(FADE_PCM);
        }
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
}
