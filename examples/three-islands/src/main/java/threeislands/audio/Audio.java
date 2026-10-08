package threeislands.audio;

import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneMusicPart;
import com.openggf.mods.scene.SceneMusicPlayer;
import com.openggf.mods.scene.SceneMusicPreparation;
import com.openggf.mods.scene.ScenePreparedMusic;
import java.util.List;

/**
 * Routes music to the right source. The mod runs on Sonic 3 &amp; Knuckles, so S3K songs and all
 * sound effects play through the running game's own driver ({@code ctx.audio()}). Sonic 1 and
 * Sonic 2 zone themes come from those ROMs through {@code ctx.music()}: each is synthesised in
 * the background, then played and restarted when it ends. While such a player exists its sound
 * replaces the driver's, so effects are dropped then (the host would mute them anyway); battles
 * always use the S3K driver's battle themes, which keeps every combat sound audible.
 */
public final class Audio {
    // S3K driver IDs (Sonic3kMusic / Sonic3kSfx).
    public static final int MUS_MINIBOSS = 0x18;
    public static final int MUS_BOSS = 0x19;
    public static final int MUS_KNUCKLES = 0x1F;
    public static final int MUS_TITLE = 0x25;
    public static final int MUS_GAME_OVER = 0x27;
    public static final int MUS_ACT_CLEAR = 0x29;
    public static final int MUS_EMERALD = 0x2B;
    public static final int MUS_FINAL_BOSS = 0x30;
    public static final int MUS_ENDING = 0x32;

    public static final int SFX_RING = 0x33;
    public static final int SFX_HURT = 0x35;
    public static final int SFX_SHIELD = 0x3A;
    public static final int SFX_ROLL = 0x3C;
    public static final int SFX_BREAK = 0x3D;
    public static final int SFX_FIRE = 0x3E;
    public static final int SFX_BUBBLE = 0x3F;
    public static final int SFX_LIGHTNING = 0x41;
    public static final int SFX_JUMP = 0x62;
    public static final int SFX_STARPOST = 0x63;
    public static final int SFX_BOSS_HIT = 0x6E;
    public static final int SFX_SUPER = 0x9F;
    public static final int SFX_WARP = 0xAF;
    public static final int SFX_REGISTER = 0xB0;
    public static final int SFX_SPRING = 0xB1;
    public static final int SFX_ERROR = 0xB2;
    public static final int SFX_EXPLODE = 0xB4;
    public static final int SFX_DASH = 0xB6;
    public static final int SFX_CURSOR = 0xB7;
    public static final int SFX_SPINDASH = 0xAB;

    /**
     * A minute of a stock song, then it restarts. Synthesis takes roughly a twelfth of the song's
     * length and the background part render as long again, so a longer song keeps the stand-in
     * playing for longer.
     */
    private static final int SONG_FRAMES = 3600;

    private final SceneContext ctx;
    private String wantGame;
    private int wantId = -1;
    private String playingGame;
    private int playingId = -1;
    private SceneMusicPreparation preparation;
    private SceneMusicPreparation partPreparation;
    private ScenePreparedMusic song;
    private SceneMusicPlayer player;
    private boolean musicFailed;
    private String lastError;

    public Audio(SceneContext ctx) {
        this.ctx = ctx;
    }

    /** Requests an S3K driver song; asking for the one already playing does nothing. */
    public void music(String game, int id) {
        music(game, id, -1);
    }

    /**
     * Requests a song. A Sonic 1 or Sonic 2 song is synthesised in the background; meanwhile
     * the S3K driver plays {@code standIn} (when not -1), and the stock song takes over once
     * ready. Asking for the song already wanted does nothing.
     */
    public void music(String game, int id, int standIn) {
        if (game.equals(wantGame) && id == wantId) return;
        wantGame = game;
        wantId = id;
        if (game.equals("s3k")) {
            stopPlayer();
            ctx.audio().playMusic(id);
            playingGame = game;
            playingId = id;
        } else {
            stopPlayer();
            if (standIn >= 0) {
                if (!"s3k".equals(playingGame) || playingId != standIn) ctx.audio().playMusic(standIn);
                playingGame = "s3k";
                playingId = standIn;
            } else {
                ctx.audio().stopMusic();
                playingGame = null;
                playingId = -1;
            }
            try {
                preparation = ctx.music().prepareAsync(game, id, SONG_FRAMES);
                musicFailed = false;
            } catch (RuntimeException unavailable) {
                preparation = null;
                musicFailed = true;
                lastError = unavailable.toString();
            }
        }
    }

    /** A one-shot S3K song (jingles); the next {@link #music} call replaces it. */
    public void jingle(int id) {
        stopPlayer();
        wantGame = "s3k";
        wantId = id;
        playingGame = "s3k";
        playingId = id;
        ctx.audio().playMusic(id);
    }

    public void fadeOut() {
        stopPlayer();
        ctx.audio().fadeOutMusic();
        wantGame = null;
        wantId = -1;
    }

    /** Plays a sound effect when nothing would mask it. */
    public void sfx(int id) {
        if (player == null) ctx.audio().playSfx(id);
    }

    public boolean playerActive() {
        return player != null;
    }

    /** Polls background synthesis and restarts finished songs; call once per update. */
    public void tick() {
        try {
            if (preparation != null) {
                switch (preparation.state()) {
                    case READY -> {
                        song = preparation.prepared();
                        preparation = null;
                        // The engine renders the selected part in the background before start.
                        partPreparation = ctx.music().preparePartAsync(song, List.of(new SceneMusicPart(0, 0, 0, false)));
                    }
                    case FAILED, CANCELLED -> {
                        preparation = null;
                        musicFailed = true;
                    }
                    default -> { }
                }
            }
            if (partPreparation != null) {
                switch (partPreparation.state()) {
                    case READY -> {
                        // prepared() publishes the rendered mix; start() refuses until it has.
                        partPreparation.prepared();
                        partPreparation = null;
                        startPlayer();
                    }
                    case FAILED, CANCELLED -> {
                        partPreparation = null;
                        musicFailed = true;
                    }
                    default -> { }
                }
            }
            if (player != null && player.finished() && song != null) {
                player.stop();
                startPlayer();
            }
        } catch (RuntimeException failure) {
            stopPlayer();
            musicFailed = true;
            lastError = failure.toString();
        }
    }

    /** True once the wanted song is audible (a driver song, or a started stock song). */
    public boolean ready() {
        return wantGame == null || wantGame.equals("s3k") || player != null || musicFailed;
    }

    private void startPlayer() {
        if (song == null) return;
        // The player replaces the driver's output; stop the stand-in so it does not resume later.
        if ("s3k".equals(playingGame)) ctx.audio().stopMusic();
        player = ctx.music().start(song, List.of(new SceneMusicPart(0, 0, 0, false)), 0);
        playingGame = wantGame;
        playingId = wantId;
    }

    private void stopPlayer() {
        if (preparation != null) preparation.cancel();
        if (partPreparation != null) partPreparation.cancel();
        preparation = null;
        partPreparation = null;
        if (player != null) player.stop();
        player = null;
        song = null;
    }

    /** A one-line state summary for diagnostics. */
    public String describe() {
        return "want " + wantGame + ":" + Integer.toHexString(wantId) + " playing " + playingGame + ":"
                + Integer.toHexString(playingId) + " player " + (player != null) + (player != null ? " finished "
                + player.finished() + " at " + player.samplePosition() + " underruns " + player.underrunCount() : "")
                + " preparing " + (preparation == null ? "-" : preparation.state() + " " + preparation.progressPercent()
                + "% " + preparation.error()) + " part " + (partPreparation == null ? "-" : partPreparation.state()
                + " " + partPreparation.error()) + " failed " + musicFailed + (lastError == null ? "" : " error " + lastError);
    }

    public boolean failed() {
        return musicFailed;
    }

    public String playingGame() {
        return playingGame;
    }

    public int playingId() {
        return playingId;
    }

    /** Releases everything when the scene closes. */
    public void close() {
        stopPlayer();
        try {
            ctx.audio().stopMusic();
        } catch (RuntimeException ignored) {
            // Shutting down; nothing else to release.
        }
    }
}
