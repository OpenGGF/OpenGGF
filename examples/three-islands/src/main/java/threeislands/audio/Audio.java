package threeislands.audio;

import com.openggf.mods.scene.SceneContext;

/** Routes continuous ROM exploration music and deliberate S3K battle/story cues. */
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

    private final SceneContext ctx;
    private String wantGame, playingGame;
    private int wantId = -1, playingId = -1;
    private boolean liveRom, musicFailed;
    private String lastError;

    public Audio(SceneContext ctx) { this.ctx = ctx; }

    public void music(String game, int id) { music(game, id, -1); }

    /** Requests are idempotent across field updates, dialogue, menus and save restores. */
    public void music(String game, int id, int fallback) {
        if (game.equals(wantGame) && id == wantId) return;
        wantGame = game;
        wantId = id;
        musicFailed = false;
        lastError = null;
        liveRom = false;
        if (game.equals("s3k")) {
            ctx.audio().playMusic(id);
            playingGame = game;
            playingId = id;
            return;
        }
        try {
            if (ctx.audio().playMusic(game, id)) {
                liveRom = true;
                playingGame = game;
                playingId = id;
                return;
            }
            lastError = "Live ROM music unavailable";
        } catch (RuntimeException unavailable) {
            lastError = unavailable.toString();
        }
        musicFailed = true;
        if (fallback >= 0) {
            ctx.audio().playMusic(fallback);
            playingGame = "s3k";
            playingId = fallback;
        } else {
            ctx.audio().stopMusic();
            playingGame = null;
            playingId = -1;
        }
    }

    /** A deliberate one-shot cue; returning to the field restores its own music. */
    public void jingle(int id) {
        wantGame = null;
        music("s3k", id);
    }

    public void fadeOut() {
        ctx.audio().fadeOutMusic();
        wantGame = null;
        wantId = -1;
        playingGame = null;
        playingId = -1;
        liveRom = false;
    }

    public void sfx(int id) {
        if (!liveRom) ctx.audio().playSfx(id);
    }

    public boolean playerActive() { return liveRom; }
    public boolean ready() { return true; }
    public boolean failed() { return musicFailed; }
    public String playingGame() { return playingGame; }
    public int playingId() { return playingId; }

    public String describe() {
        return "want " + wantGame + ":" + Integer.toHexString(wantId) + " playing " + playingGame + ":"
                + Integer.toHexString(playingId) + " live " + liveRom + " failed " + musicFailed
                + (lastError == null ? "" : " error " + lastError);
    }

    public void close() {
        try { ctx.audio().stopMusic(); }
        catch (RuntimeException ignored) { /* Scene shutdown. */ }
        wantGame = playingGame = null;
        wantId = playingId = -1;
        liveRom = false;
    }
}
