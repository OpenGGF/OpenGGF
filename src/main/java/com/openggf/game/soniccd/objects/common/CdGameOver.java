package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Game over and time over text, object $3B ({@code ObjGameOver}, Level/_Objects/Game Over/Main.asm:11-112).
 * Routine 0 places "GAME" (or "TIME") at screen X 0 sliding to 160, Y 96, tile $8544, and spawns
 * "OVER" (frame 1) with FindObjSlot sliding from 320 to 160. A time over ({@code timeOver} bit 0,
 * cleared here) with lives left uses the TIME OVER mappings; with no time over and lives left the
 * object deletes itself. Both pieces move 8 pixels a frame to their destination. The art is
 * ShieldArtIndex entry 3 or 4 at tile $544 ({@code powerup} 8 or 10, LoadShieldArt). With no lives left
 * the "OVER" piece plays {@code SCMD_GMOVERMUS} (Game Over/Main.asm:85-87).
 *
 * <p>The object made by the death ({@link #afterDeath}) also carries the player's restart timer,
 * {@code oPlayerReset}: 8 seconds with no lives left, else one second (ObjSonic_DeadChkGone,
 * Sonic/Main.asm:2431-2444), counted down from the next frame by {@code ObjSonic_Restart}
 * (Sonic/Main.asm:2448-2475). On zero, a stage with lives left restarts (the engine's death reload);
 * with none it returns to IPX ({@link com.openggf.game.soniccd.program.CdStageExit#outOfLives}).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.SCREEN_POSITION)
public final class CdGameOver extends CdObject implements ObjectVisualFacing.NonDirectional {
    private int routine;
    private boolean timeOverText;
    private int screenY;
    private int destX;
    private int frame;

    /** {@code oPlayerReset}; 0 when this object does not carry the restart (layout or "OVER" pieces). */
    private int restartTimer;
    /** Time Attack's death sets the timer without loading the text object (Sonic/Main.asm:2431-2437). */
    private boolean timerOnly;

    public CdGameOver(ObjectSpawn spawn) {
        super(spawn, "GameOver");
    }

    /**
     * The death's {@code FindObjSlot} $3B with the player's restart timer; {@code text} false for Time
     * Attack, which skips the object (Sonic/Main.asm:2425-2437).
     */
    public static CdGameOver afterDeath(int restartFrames, boolean text) {
        CdGameOver gameOver = new CdGameOver(new ObjectSpawn(0, 0, 0x3B, 0, 0, false, 0));
        gameOver.restartTimer = restartFrames;
        gameOver.timerOnly = !text;
        return gameOver;
    }

    private CdGameOver(boolean timeOverText) {
        super(new ObjectSpawn(0, 0, 0x3B, 0, 0, false, 0), "GameOver");
        this.timeOverText = timeOverText;
        // .SpawnOverText: frame 1, Y 128+96, X 320+128, destination 160+128 (Game Over/Main.asm:76-83).
        this.routine = 2;
        this.frame = 1;
        this.screenY = 96 + 128;
        this.x = 320 + 128;
        this.destX = 160 + 128;
        this.tileWord = CommonObjectArt.GAME_OVER_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (restartTimer != 0 && routine != 0 && --restartTimer == 0) restart();
        if (timerOnly) {
            routine = 2;
            return;
        }
        if (routine == 0 && !init()) return;
        // ObjGameOver_Main: step 8 toward oGmOverDestX (Game Over/Main.asm:92-101).
        if (x != destX) x += destX > x ? 8 : -8;
    }

    /** ObjGameOver_Init (Game Over/Main.asm:27-88); false when it deletes itself. */
    private boolean init() {
        routine = 2;
        screenY = 96 + 128;
        x = 128;
        destX = 160 + 128;
        tileWord = CommonObjectArt.GAME_OVER_TILE;
        var game = services().gameState();
        int lives = game == null ? 0 : game.getLives();
        CdLevelFlags flags = flags();
        boolean timeOver = flags.timeOver;
        flags.timeOver = false;                              // bclr #0,timeOver
        if (timeOver) {
            if (lives != 0) timeOverText = true;
        } else if (lives != 0) {
            setDestroyed(true);
            return false;
        }
        var objects = services().objectManager();
        if (objects != null) objects.addDynamicObject(new CdGameOver(timeOverText));
        if (lives == 0) services().playMusic(com.openggf.game.soniccd.audio.CdSubCpuAudio.commandId("SCMD_GMOVERMUS"));
        return true;
    }

    /** {@code ObjSonic_Restart}'s timer reached zero: {@code move.w #1,levelRestart} (Sonic/Main.asm:2452). */
    private void restart() {
        var game = services().gameState();
        if (game != null && game.getLives() == 0) {
            com.openggf.game.soniccd.program.CdStageExit.outOfLives(services());
            return;
        }
        var levels = services().levelManager();
        if (levels != null) levels.requestRespawn();
    }

    boolean timeOverText() { return timeOverText; }
    int frame() { return frame; }
    int screenX() { return x; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        PatternSpriteRenderer renderer = art(timeOverText ? CommonObjectArt.TIME_OVER : CommonObjectArt.GAME_OVER);
        var camera = services().camera();
        if (renderer == null || camera == null) return;
        renderer.drawFrameIndex(frame, camera.getX() + x - 128, camera.getY() + screenY - 128, false, false,
                CdObjectArtProvider.tilePalette(tileWord));
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(0); }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
