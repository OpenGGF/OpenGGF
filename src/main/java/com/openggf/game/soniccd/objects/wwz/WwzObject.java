package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * What the Wacky Workbench objects share: the routine byte, the 16.16 position halves
 * ({@code oXSub}/{@code oYSub}), the status and drawn flip bits, the map frame and one sheet to draw.
 * {@code oSprFlags} and {@code oFlags} start as the layout entry's flip bits (SpawnObject,
 * Level/Object Spawner.asm:288-292); an object another object allocates starts cleared.
 */
public abstract class WwzObject extends CdObject {

    /** {@code oRoutine}, a byte stepping by 2. */
    protected int routine;
    /** {@code oXSub} and {@code oYSub}, the low words of {@code oX} and {@code oY}. */
    protected int xSub;
    protected int ySub;
    protected int mapFrame;
    /** {@code oSprFlags} bits 0-1: the drawn flip. */
    protected int spriteFlip;

    protected WwzObject(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.spriteFlip = spawn.renderFlags() & 3;
    }

    /** The renderer key of the sheet this object draws, or {@code null} to draw nothing. */
    protected abstract String sheetKey();

    /** {@code add.l d0,oX(a0)}. */
    protected final void addToX(int velocity) {
        int position = ((x & 0xFFFF) << 16 | xSub) + velocity;
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    /** {@code add.l d0,oY(a0)}. */
    protected final void addToY(int velocity) {
        int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    /** {@code move.l oX(a0),d0 / sub.l d1,d0}: the 16.16 position. */
    protected final int xPosition() {
        return (x & 0xFFFF) << 16 | xSub;
    }

    protected final int yPosition() {
        return (y & 0xFFFF) << 16 | ySub;
    }

    protected final void setXPosition(int position) {
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    protected final void setYPosition(int position) {
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    protected static AbstractPlayableSprite sprite(com.openggf.game.PlayableEntity player) {
        return player instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /**
     * {@code timeZone} (0 past, 1 present, 2 future), read live from the campaign owner
     * ({@link com.openggf.game.soniccd.timetravel.CdTimeTravel}); the loaded stage's period only
     * without an owner.
     */
    @Override
    protected int timeZone() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        return tt != null ? tt.timeZone().nativeValue() : super.timeZone();
    }

    /** {@code goodFuture}, read live from the campaign owner; the stage's period only without one. */
    protected boolean goodFuture() {
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if (tt != null) return tt.goodFuture();
        var level = cdLevel();
        return level != null && level.payload().stage().period() == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE;
    }

    public final int routine() { return routine; }
    public final int mapFrame() { return mapFrame; }
    public final int xSubpixel() { return xSub; }
    public final int ySubpixel() { return ySub; }
    public final int spriteFlip() { return spriteFlip; }
    public final int objectX() { return x; }
    public final int objectY() { return y; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        String key = sheetKey();
        if (key == null || artProvider() == null) return;
        drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
