package sitarhero.audio;

import com.openggf.mods.scene.SceneAudio;

/**
 * Owns everything the scene plays through the running game's own sound driver
 * ({@code ctx.audio()}): one menu theme, short menu cues and the results jingle. Song
 * performances use {@code ctx.music()} instead, which is a different source.
 *
 * <p>Two host facts shape this class. While a {@code SceneMusicPlayer} is active its PCM
 * replaces the driver's output, and pausing that player pauses all scene audio. So:
 * <ul>
 *   <li>cues are dropped while a player exists, because nobody would hear them and a
 *       queued request must not surface later;</li>
 *   <li>the menu theme is stopped before a player starts, otherwise the driver keeps
 *       playing it silently and it resurfaces mid-phrase when the song ends;</li>
 *   <li>every return to a menu asks for the theme again, which is idempotent: a theme that
 *       is already playing is never restarted.</li>
 * </ul>
 *
 * <p>IDs belong to the <em>running</em> game's driver (the ROM the mod was launched with),
 * whichever installed ROM supplied the scenery or the song. Each cue is the sound the
 * ROM's own menus and results use for the same job; the evidence is cited per ID below.
 * At most one cue plays per tick: an important action (confirm, back, start) always beats a
 * cursor move on the same tick, and only cursor and tally clicks are rate-limited.
 */
public final class HouseAudio {
    /** Ordered by priority: a later cue wins when several are requested in one tick. */
    public enum Cue { TALLY, MOVE, BACK, CONFIRM, REGISTER, FAIL, DENIED, START }

    private enum Theme { NONE, MENU, JINGLE }

    /** Lets the start cue ring out before the theme fade (S1's fade also silences SFX). */
    public static final int START_CUE_TICKS = 48;
    private static final int SOFT_CUE_GAP = 3;

    private final SceneAudio audio;
    private final String game;
    private Theme theme = Theme.NONE;
    private Cue pending;
    private long lastSoftCue = Long.MIN_VALUE / 2;
    private long fadeAt = -1;
    private boolean leaving;

    /** {@code runningGame} is "s1", "s2" or "s3k"; anything else makes this silent. */
    public HouseAudio(SceneAudio audio, String runningGame) {
        this.audio = audio;
        this.game = runningGame == null ? "" : runningGame;
    }

    /** Requests a cue for this tick; {@link #tick} plays the most important one. */
    public void cue(Cue cue) {
        if (pending == null || cue.ordinal() > pending.ordinal()) pending = cue;
    }

    /**
     * Called once at the end of every scene update. {@code playerActive} is true while a
     * song or calibration player exists (playing, paused or waiting to start).
     */
    public void tick(long now, boolean playerActive) {
        Cue cue = pending;
        pending = null;
        if (fadeAt >= 0 && now >= fadeAt) {
            fadeAt = -1;
            if (theme != Theme.NONE) { audio.fadeOutMusic(); theme = Theme.NONE; }
        }
        if (cue == null || playerActive || leaving) return;
        boolean soft = cue == Cue.MOVE || cue == Cue.TALLY;
        if (soft && now - lastSoftCue < SOFT_CUE_GAP) return;
        int id = sfx(game, cue);
        if (id < 0) return;
        if (soft) lastSoftCue = now;
        audio.playSfx(id);
    }

    /** The menus are showing: make sure the theme is playing, without restarting it. */
    public void menuTheme() {
        if (leaving) return;
        if (theme == Theme.MENU) { fadeAt = -1; return; }
        int id = music(game, Theme.MENU);
        if (id < 0) return;
        audio.playMusic(id);
        theme = Theme.MENU;
        fadeAt = -1;
    }

    /** A song (or calibration) starts loading: ring the cue, then fade the theme under it. */
    public void startGig(long now, Cue cue) {
        cue(cue);
        if (theme != Theme.NONE && fadeAt < 0) fadeAt = now + START_CUE_TICKS;
    }

    /** Call immediately before {@code ctx.music().start}: the driver must be silent underneath. */
    public void beforePlayback() {
        audio.stopMusic();
        theme = Theme.NONE;
        fadeAt = -1;
        pending = null;
    }

    /** A finished attempt: the act-clear jingle for a clear, the ring-loss spill for a failure. */
    public void results(boolean cleared) {
        fadeAt = -1;
        if (cleared) {
            int id = music(game, Theme.JINGLE);
            if (id >= 0) { audio.playMusic(id); theme = Theme.JINGLE; }
        } else {
            cue(Cue.FAIL);
        }
    }

    /** The scene asked to leave: fade with the host's fade to black, and start nothing new. */
    public void leaving() {
        leaving = true;
        if (theme != Theme.NONE) audio.fadeOutMusic();
        theme = Theme.NONE;
        fadeAt = -1;
    }

    /** The scene is closing for any reason: nothing of ours may play on into the next mode. */
    public void dispose() {
        audio.stopMusic();
        theme = Theme.NONE;
        fadeAt = -1;
        pending = null;
    }

    /** True while the menu theme is the driver's current music (for tests and tools). */
    public boolean menuThemePlaying() { return theme == Theme.MENU; }

    /** Sound effect IDs in each driver's own numbering; -1 where the ROM has no such cue. */
    static int sfx(String game, Cue cue) {
        return switch (game) {
            // s1disasm: Got Through Card ticks sfx_Switch $CD and ends on sfx_Cash $C5;
            // the title code entry acknowledges with sfx_Ring $B5. S1 has no error cue.
            case "s1" -> switch (cue) {
                case TALLY, MOVE, BACK -> 0xCD;
                case CONFIRM -> 0xB5;
                case REGISTER -> 0xC5;
                case FAIL -> 0xC6;     // sfx_RingLoss
                case DENIED -> -1;
                case START -> 0xC3;    // sfx_GiantRing
            };
            // s2.asm: menus and the results tally play SndID_Blip $CD and SndID_TallyEnd $C5;
            // SndID_Error $ED rejects a selection.
            case "s2" -> switch (cue) {
                case TALLY, MOVE, BACK -> 0xCD;
                case CONFIRM -> 0xB5;  // SndID_Ring
                case REGISTER -> 0xC5;
                case FAIL -> 0xC6;     // SndID_RingSpill
                case DENIED -> 0xED;
                case START -> 0xC3;    // SndID_EnterGiantRing
            };
            // sonic3k.asm: the title menu and results countdown play sfx_Switch $5B, the
            // results end on sfx_Register $B0; sfx_Error is $B2.
            case "s3k" -> switch (cue) {
                case TALLY, MOVE, BACK -> 0x5B;
                case CONFIRM -> 0x33;  // sfx_RingRight
                case REGISTER -> 0xB0;
                case FAIL -> 0xB9;     // sfx_RingLoss
                case DENIED -> 0xB2;
                case START -> 0xB3;    // sfx_BigRing
            };
            default -> -1;
        };
    }

    /**
     * Music IDs as the engine's sound test numbers them. S1 has no menu track, so its menus
     * use the looping Special Stage theme; S2 uses its Options theme and S3K its Data Select.
     */
    private static int music(String game, Theme theme) {
        boolean menu = theme == Theme.MENU;
        return switch (game) {
            case "s1" -> menu ? 0x89 : 0x8E;   // Special Stage / Got Through
            case "s2" -> menu ? 0x89 : 0x97;   // Options / Stage Clear
            case "s3k" -> menu ? 0x2F : 0x29;  // Data Select / Act Clear
            default -> -1;
        };
    }
}
