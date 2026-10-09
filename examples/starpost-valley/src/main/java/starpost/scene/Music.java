package starpost.scene;

import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneMusicPlayer;
import com.openggf.mods.scene.SceneMusicPreparation;

/**
 * Chooses and plays the soundtrack: Green Hill by day in spring, Star Light at night, and so on
 * (design doc §8). Tracks from another ROM are synthesised through {@code ctx.music()}; until
 * the background-music engine addition lands, that player silences the driver's sound effects
 * while it plays.
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
    private static final int LENGTH_FRAMES = 60 * 180;

    private final SceneContext ctx;
    private boolean enabled = true;
    private String wantedGame;
    private int wantedId = -1;
    private String playingGame;
    private int playingId = -1;
    private SceneMusicPreparation preparing;
    private SceneMusicPlayer player;

    public Music(SceneContext ctx) {
        this.ctx = ctx;
    }

    /** Asks for a track; nothing happens if it is already playing or being prepared. */
    public void want(String game, int id) {
        wantedGame = game;
        wantedId = id;
    }

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

    /** One tick: starts, switches or loops the wanted track. */
    public void update() {
        if (!enabled || wantedId < 0) {
            return;
        }
        boolean same = wantedId == playingId && wantedGame.equals(playingGame);
        if (!same) {
            release();
            try {
                preparing = ctx.music().prepareAsync(wantedGame, wantedId, LENGTH_FRAMES);
                playingGame = wantedGame;
                playingId = wantedId;
            } catch (RuntimeException e) {
                wantedId = -1;
            }
            return;
        }
        if (preparing != null) {
            switch (preparing.state()) {
                case READY -> {
                    player = ctx.music().start(preparing.prepared(), 0, 0, false, 0);
                    preparing = null;
                }
                case FAILED, CANCELLED -> {
                    preparing = null;
                    wantedId = -1;
                }
                default -> {
                }
            }
        } else if (player != null && player.finished()) {
            // The prepared song ends; start it again from the top.
            player.stop();
            player = null;
            playingId = -1;
        }
    }

    private void release() {
        if (preparing != null) {
            preparing.cancel();
            preparing = null;
        }
        if (player != null) {
            player.stop();
            player = null;
        }
        playingId = -1;
        playingGame = null;
    }
}
