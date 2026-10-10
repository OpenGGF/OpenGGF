package starpost.scene;

import com.openggf.mods.scene.SceneContext;

/**
 * Chooses and plays the soundtrack: Green Hill by day in spring, Star Light at night, and so on
 * (design doc §8). Every track goes through {@code ctx.audio().playMusic(game, id)}: Sonic 3 &
 * Knuckles's own songs take the base route and Sonic 1's or Sonic 2's take the cross-game donor
 * route, so a song loops at its own loop point and the sound effects stay audible over it.
 */
public final class Music {
    // Sonic 1 driver IDs (Sonic1Music).
    public static final int S1_GHZ = 0x81;
    public static final int S1_LZ = 0x82;
    public static final int S1_MZ = 0x83;
    public static final int S1_SLZ = 0x84;
    public static final int S1_SYZ = 0x85;
    public static final int S1_SBZ = 0x86;
    public static final int S1_ENDING = 0x8B;
    public static final int S1_GOT_THROUGH = 0x8E;
    public static final int S1_CREDITS = 0x91;

    private final SceneContext ctx;
    private boolean enabled = true;
    private String wantedGame;
    private int wantedId = -1;
    private String playingGame;
    private int playingId = -1;

    public Music(SceneContext ctx) {
        this.ctx = ctx;
        ctx.audio().setMusicTempoPercent(65);
    }

    /** Asks for a track; nothing happens if it is already playing (or its ROM was not supplied). */
    public void want(String game, int id) {
        wantedGame = game;
        wantedId = id;
    }

    /** Retain the wanted track while the act owns the driver; resume replays it. */
    public void parkForAct() { release(); }

    public void stop() {
        wantedId = -1;
        release();
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            release();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** One tick: starts or switches to the wanted track. */
    public void update() {
        if (!enabled || wantedId < 0 || wantedId == playingId && wantedGame.equals(playingGame)) {
            return;
        }
        playingGame = wantedGame;
        playingId = wantedId;
        try {
            if (!ctx.audio().playMusic(wantedGame, wantedId)) {
                ctx.audio().stopMusic();   // that game's ROM was not supplied: silence, not the last song
            }
        } catch (RuntimeException e) {
            wantedId = -1;
        }
    }

    private void release() {
        if (playingId >= 0) {
            ctx.audio().stopMusic();
        }
        playingId = -1;
        playingGame = null;
    }
}
