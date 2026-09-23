package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomWorldPositionedObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Native sprite words shared by the DEZ Act-1 encounter's SST objects. */
abstract class DezMinibossSprite extends AbstractObjectInstance implements RomWorldPositionedObject, DezExplosionOwner {
    protected int posX;
    protected int posY;
    protected int xVelocity;
    protected int yVelocity;
    protected int frame;
    protected int priority = 5;
    protected int halfWidth = 0x20;
    protected int halfHeight = 0x20;
    protected int control;
    protected int status;
    protected int codePointer;
    protected int collisionProperty;
    protected int word44;
    /** $3A/$3C: radius/vertical offset in phase 1, shared angular motion in phase 2. */
    protected int word3A;
    protected int word3C;
    protected int childDy;
    protected boolean flipX;
    protected boolean flipY;
    protected boolean highPriority = true;
    protected boolean visible;
    protected boolean pendingDelete;

    DezMinibossSprite(ObjectSpawn spawn, String name) {
        super(spawn, name);
        posX = spawn.x() << 16;
        posY = spawn.y() << 16;
    }

    final com.openggf.level.objects.ObjectServices encounterServices() { return services(); }

    protected final int romByte(int address) {
        try { return services().romReader().readU8(address); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    protected final int romWord(int address) {
        try { return services().romReader().readU16BE(address); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    protected final void move(int gravity) {
        posX += (short) xVelocity << 8;
        posY += (short) yVelocity << 8;
        yVelocity = (short) (yVelocity + gravity);
    }

    protected final void writeX(int value) { posX = (value << 16) | (posX & 0xFFFF); }
    protected final void writeY(int value) { posY = (value << 16) | (posY & 0xFFFF); }

    @Override public int explosionControl() { return control; }

    @Override public int getX() { return posX >>> 16; }
    @Override public int getY() { return posY >>> 16; }
    @Override public int getPriorityBucket() { return priority; }
    @Override public int getOnScreenHalfWidth() { return halfWidth; }
    @Override public int getOnScreenHalfHeight() { return halfHeight; }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public boolean isPersistent() { return true; }

    @Override public void offsetNativePositionWordsPreserveSubpixel(int dx, int dy) {
        writeX(getX() + dx);
        writeY(getY() + dy);
        updateDynamicSpawn(getX(), getY());
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_MINIBOSS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(frame, getX(), getY(), flipX, flipY, -1, highPriority);
        }
    }
}
