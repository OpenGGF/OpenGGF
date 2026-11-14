package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * What Stardust Speedway's objects share: the routine byte, the mapping frame, the 16.16 position
 * halves ({@code oXSub}/{@code oYSub}), the velocity words, the flip bits ({@code oFlags}/{@code oSprFlags}
 * bits 0-1), {@code MoveObject} and the sheet lookup. Shipped {@code FixBugs = 0}: each routine is modelled
 * as assembled.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
abstract class SszObject extends CdObject {

    /** oRoutine, a byte stepping by 2. */
    protected int routine;
    protected int mapFrame;
    /** oXVel and oYVel, signed words. */
    protected int xVel;
    protected int yVel;
    /** oXSub and oYSub, the low words of oX and oY. */
    protected int xSub;
    protected int ySub;
    /** oFlags and oSprFlags bits 0-1 (SpawnObject copies the layout flip bits to both, Object Spawner.asm:287-290). */
    protected int statusFlip;
    protected int spriteFlip;
    /** True once the init body ran: {@code oMap} is set, so DrawObject can draw. */
    protected boolean initialised;

    protected SszObject(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.statusFlip = spawn.renderFlags() & 3;
        this.spriteFlip = this.statusFlip;
    }

    /** The sheet kind of the current frame ({@link SszObjectArt}). */
    protected abstract String sheetKind();

    /**
     * {@code MoveObject} (common/object.asm:65-95) with {@code oFlags} bit 3 clear: 16.8 velocities added
     * to the 16.16 position. Bit 3 (carried by a flower, ID $1E) is only set on solid objects the player
     * stands on; none of Stardust Speedway's moving objects is one.
     */
    protected final void moveObject() {
        long position = ((long) (x & 0xFFFF) << 16 | xSub) + ((long) (short) xVel << 8);
        x = (int) (position >>> 16) & 0xFFFF;
        xSub = (int) position & 0xFFFF;
        position = ((long) (y & 0xFFFF) << 16 | ySub) + ((long) (short) yVel << 8);
        y = (int) (position >>> 16) & 0xFFFF;
        ySub = (int) position & 0xFFFF;
    }

    /** {@code bchg #0,oSprFlags(a0); bchg #0,oFlags(a0)}. */
    protected final void flipHorizontally() {
        spriteFlip ^= 1;
        statusFlip ^= 1;
    }

    /**
     * {@code stage_frames} as the objects read it: {@code StageMainLoopUpdate} does {@code addq.w #1,stage_frames} before
     * {@code UpdateObjects} (common/main.asm:408-410), so the first object pass sees 1, while the engine's counter is
     * stepped after the object pass ({@code LevelManager}'s {@code frameCounter++} ends the step) and reads 0 there.
     * The searchlight's dithering parity (objects/r7/searchlight.asm:48-68) depends on it.
     */
    protected final int levelFrames() {
        var levels = services().levelManager();
        // The loaded level already stands at levelFrames 1 (LoadedLevelFrameCounter), so the engine's counter
        // is levelFrames itself during the object pass; no local correction.
        return levels == null ? 0 : levels.getFrameCounter();
    }

    /**
     * {@code timeZone} (0 past, 1 present, 2 future), read live from the campaign owner
     * ({@link com.openggf.game.soniccd.timetravel.CdTimeTravel}); the loaded stage's period only without one.
     */
    protected final int liveTimeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        return tt != null ? tt.timeZone().nativeValue() : timeZone();
    }

    protected final int cameraX() {
        return services().camera().getX();
    }

    protected final int cameraY() {
        return services().camera().getY();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        CdObjectArtProvider provider = artProvider();
        if (provider == null) return;
        String key = SszObjectArt.key(provider, sheetKind(), tileWord, cameraX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
    public final int spriteFlip() { return spriteFlip; }
}
