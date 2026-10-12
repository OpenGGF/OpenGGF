package starpost.scene;

import com.openggf.mods.scene.SceneContext;

/**
 * Chooses and plays the soundtrack: Green Hill by day in spring, Star Light at night, and so on
 * (design doc §8). Every track goes through {@code ctx.audio().playMusic(game, id)}: Sonic 3 &
 * Knuckles's own songs take the base route and Sonic 1's or Sonic 2's take the cross-game donor
 * route, so a song loops at its own loop point and the sound effects stay audible over it.
 */
public final class Music implements AutoCloseable {
    private final starpost.art.AssetFailures failures;
    private boolean closed;
    public java.util.List<starpost.art.AssetFailures.Failure> failures() { return failures.failures(); }
    private void requireOpen() { if (closed) throw new IllegalStateException("Music owner is closed"); }
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
        failures = new starpost.art.AssetFailures(ctx.ownerModId(), ctx::ticks);
        ctx.audio().setMusicTempoPercent(65);
    }

    /** Asks for a track; nothing happens if it is already playing (or its ROM was not supplied). */
    public void want(String game, int id) {
        requireOpen();
        wantedGame = game;
        wantedId = id;
    }

    /** Retain the wanted track while the act owns the driver; resume replays it. */
    public void parkForAct() { release(); }

    public void stop() {
        requireOpen();
        wantedId = -1;
        release();
    }

    public void setEnabled(boolean value) {
        requireOpen();
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
        requireOpen();
        if (!enabled || wantedId < 0 || wantedId == playingId && wantedGame.equals(playingGame)) {
            return;
        }
        var audio = ctx.audio(); // Context/owner failures are not decoder failures.
        Boolean played = failures.load("music:" + wantedGame + ":" + wantedId, () -> {
            if (!audio.playMusic(wantedGame, wantedId)) {
                audio.stopMusic(); // Unavailable donor/domain: silence, not the preceding song.
                starpost.art.AssetFailures.unavailable("Music ROM or song is unavailable");
            }
            return true;
        });
        playingGame = played == null ? null : wantedGame;
        playingId = played == null ? -1 : wantedId;
    }

    private void release() {
        requireOpen();
        if (playingId >= 0) {
            ctx.audio().stopMusic();
        }
        playingId = -1;
        playingGame = null;
    }

    @Override public void close() {
        if (closed) return;
        try { stop(); } finally { closed = true; failures.close(); }
    }
}
