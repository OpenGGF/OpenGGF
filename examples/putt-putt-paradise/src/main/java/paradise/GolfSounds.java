package paradise;

import com.openggf.audio.GameMusic;
import com.openggf.game.GameServices;
import paradise.ui.GolfFeedback;

/**
 * Golf's sound choices, all played by the native SMPS driver from the player's Sonic 2 ROM.
 * This mod only patches Sonic 2, so its presentation cues name Sonic 2 sound IDs directly;
 * the citations are the s2disasm routines that use each sound for the same job.
 */
final class GolfSounds {
    /** SndID_Blip: Obj0F's title-menu selection blip (s2.asm:27227) and the results tally tick. */
    static final int BLIP = 0xCD;
    /** SndID_TallyEnd: the results tally finishing (s2.asm:28451). */
    static final int TALLY_END = 0xC5;
    /** SndID_Signpost: Obj0D spinning as the player passes the end of an act (s2.asm:34680). */
    static final int SIGNPOST = 0xCF;
    /** SndID_Checkpoint: Obj79's star post ding-dong (s2.asm:44644); announces the next golfer. */
    static final int CHECKPOINT = 0xA1;
    /** SndID_Ring: Sonic 2 acknowledges an accepted cheat with it (s2.asm:4650); confirms readiness and menu choices. */
    static final int RING = 0xB5;
    /** SndID_Error: LevelSelect2P_PressStart refuses an unavailable choice (s2.asm:11847); also a ball lost without damage. */
    static final int ERROR = 0xED;
    /** MusID_Options (engine Sonic2Music.OPTIONS): MenuScreen's music for Sonic 2's menus (s2.asm:11813). */
    static final int MENU_MUSIC = 0x89;

    private GolfSounds() { }

    static void play(GolfFeedback.Cue cue) {
        var audio = GameServices.audio();
        switch (cue) {
            case HANDOFF -> audio.playSfx(CHECKPOINT);
            case READY -> audio.playSfx(RING);
            case FINISH -> audio.playSfx(SIGNPOST);
            case PENALTY -> audio.playSfx(ERROR);
            case TALLY_TICK -> audio.playSfx(BLIP);
            case TALLY_END -> audio.playSfx(TALLY_END);
            case CLEAR -> audio.playMusic(GameMusic.ACT_CLEAR);
        }
    }

    static void menu(paradise.ui.GolfMenu.Cue cue) {
        var audio = GameServices.audio();
        switch (cue) {
            case MUSIC -> audio.playMusic(MENU_MUSIC);
            case MOVE -> audio.playSfx(BLIP);
            case ENTER -> audio.playSfx(RING);
            case LAUNCH -> audio.playSfx(com.openggf.audio.GameSound.SPINDASH_RELEASE);
            case ERROR -> audio.playSfx(ERROR);
        }
    }
}
