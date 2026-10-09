package typinghog.hub;

import com.openggf.mods.scene.SceneAudio;
import typinghog.play.Sounds;

/**
 * Everything the hub plays through the running game's own sound driver: its title theme, a
 * menu theme, a school theme, the act-clear and game-over jingles, and menu and keystroke
 * effects. IDs belong to the running game's driver (the ROM the session launched with), even
 * when the art on screen comes from another supplied ROM. The keystroke and error clicks are
 * the course's own cues ({@code sounds/<game>.txt}) so the School sounds like play.
 *
 * <p>At most one menu cue plays per tick (the most important wins); cursor and tally clicks are
 * rate-limited so held arrows and count-ups do not machine-gun. A theme already playing is
 * never restarted.
 */
final class HubAudio {
    /** Ordered by priority: a later cue wins when several are requested in one tick. */
    enum Cue { KEY, TALLY, MOVE, BACK, ERROR, CONFIRM, REGISTER, DENIED, START }

    enum Theme { NONE, TITLE, MENU, SCHOOL, CLEAR, GAME_OVER, EMERALD }

    private static final int SOFT_GAP = 3;

    private final SceneAudio audio;
    private final String game;
    private final Sounds course;
    private Theme theme = Theme.NONE;
    private Cue pending;
    private long lastSoft = -100;
    private boolean leaving;

    HubAudio(SceneAudio audio, String runningGame, Sounds course) {
        this.audio = audio;
        this.game = runningGame == null ? "" : runningGame;
        this.course = course;
    }

    /**
     * The voice seam: plays a voice line through this mod's audio manifest in the player's
     * chosen render ({@code voice-<line>} for the Mega Drive DAC version,
     * {@code voice-<line>-clean} for the clean one; nothing when voice is off). Line names are
     * the voice lane's {@code typinghog.audio.VoiceLine} ids; until those assets are in the jar
     * the host reports the clip unavailable and this returns false, so screens can call it now.
     */
    boolean voice(String line, typinghog.save.LaunchIntent.Voice mode) {
        if (leaving || line == null || line.isEmpty() || mode == typinghog.save.LaunchIntent.Voice.OFF) return false;
        String id = "voice-" + line + (mode == typinghog.save.LaunchIntent.Voice.CLEAN ? "-clean" : "");
        try {
            return audio.playSfx(id);
        } catch (IllegalArgumentException badName) {
            return false;
        }
    }

    void cue(Cue cue) {
        if (pending == null || cue.ordinal() > pending.ordinal()) pending = cue;
    }

    /** Plays the tick's most important cue; call once at the end of every update. */
    void tick(long now) {
        Cue cue = pending;
        pending = null;
        if (cue == null || leaving) return;
        boolean soft = cue == Cue.MOVE || cue == Cue.TALLY || cue == Cue.KEY;
        if (soft && now - lastSoft < SOFT_GAP) return;
        int id = sfx(cue);
        if (id < 0) return;
        if (soft) lastSoft = now;
        audio.playSfx(id);
    }

    /** Starts a theme unless it is already the one playing. */
    void theme(Theme wanted) {
        if (leaving || wanted == theme) return;
        int id = music(wanted);
        theme = wanted;
        if (id < 0) {
            audio.stopMusic();
            return;
        }
        audio.playMusic(id);
    }

    Theme current() { return theme; }

    /** The scene is handing off: fade with the host's fade to black and start nothing new. */
    void leaving() {
        leaving = true;
        if (theme != Theme.NONE) audio.fadeOutMusic();
        theme = Theme.NONE;
        pending = null;
    }

    void dispose() {
        audio.stopMusic();
        theme = Theme.NONE;
        pending = null;
    }

    int sfx(Cue cue) {
        switch (cue) {
            case KEY: return course.id(Sounds.Cue.KEY);
            case ERROR, DENIED: return course.id(Sounds.Cue.ERROR);
            default: break;
        }
        return switch (game) {
            // s1disasm: menus and the Got Through card tick with sfx_Switch $CD and end on
            // sfx_Cash $C5; the title cheat chimes sfx_Ring $B5; sfx_GiantRing $C3 enters a stage.
            case "s1" -> switch (cue) {
                case TALLY, MOVE, BACK -> 0xCD;
                case CONFIRM -> 0xB5;
                case REGISTER -> 0xC5;
                case START -> 0xC3;
                default -> -1;
            };
            // s2.asm: SndID_Blip $CD, SndID_Ring $B5, SndID_TallyEnd $C5, SndID_EnterGiantRing $C3.
            case "s2" -> switch (cue) {
                case TALLY, MOVE, BACK -> 0xCD;
                case CONFIRM -> 0xB5;
                case REGISTER -> 0xC5;
                case START -> 0xC3;
                default -> -1;
            };
            // sonic3k.asm: sfx_Switch $5B, sfx_RingRight $33, sfx_Register $B0, sfx_BigRing $B3.
            // Errors and refusals use the course's ERROR cue.
            case "s3k" -> switch (cue) {
                case TALLY, MOVE, BACK -> 0x5B;
                case CONFIRM -> 0x33;
                case REGISTER -> 0xB0;
                case START -> 0xB3;
                default -> -1;
            };
            default -> -1;
        };
    }

    /**
     * Music as the engine's sound tests number it (Sonic1Music, Sonic2Music, Sonic3kMusic).
     * Sonic 1 has no menu track, so its menus use the Special Stage theme as Sitar Hero does.
     */
    int music(Theme wanted) {
        return switch (game) {
            case "s1" -> switch (wanted) {
                case TITLE -> 0x8A;
                case MENU, SCHOOL -> 0x89;
                case CLEAR -> 0x8E;
                case GAME_OVER -> 0x8F;
                case EMERALD -> 0x93;
                case NONE -> -1;
            };
            case "s2" -> switch (wanted) {
                case TITLE -> 0x96;
                case MENU -> 0x89;    // Options
                case SCHOOL -> 0x88;  // Special Stage
                case CLEAR -> 0x97;
                case GAME_OVER -> 0xB8;
                case EMERALD -> 0xBA;
                case NONE -> -1;
            };
            case "s3k" -> switch (wanted) {
                case TITLE -> 0x25;
                case MENU -> 0x2F;    // Data Select
                case SCHOOL -> 0x2D;  // Competition menu
                case CLEAR -> 0x29;
                case GAME_OVER -> 0x27;
                case EMERALD -> 0x2B;
                case NONE -> -1;
            };
            default -> -1;
        };
    }
}
