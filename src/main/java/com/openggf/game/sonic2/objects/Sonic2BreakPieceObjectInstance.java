package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.List;

/** A static ROM mapping piece; presentation availability never owns its existence. */
public final class Sonic2BreakPieceObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, ObjectVisualFacing.NonDirectional {
    private int subX;
    private int subY;
    private int xVelocity;
    private int yVelocity;
    private int gravity;
    private int halfWidth;
    private int halfHeight;
    private boolean explicitHeight;
    private boolean highPriority;
    private int priorityBucket;
    private boolean birthOnScreen;
    private boolean firstUpdate = true;
    private String artKey;
    private SpriteMappingPiece piece;

    public Sonic2BreakPieceObjectInstance(ObjectSpawn spawn) {
        super(spawn, "BreakPiece");
        subX = spawn.x() << 8;
        subY = spawn.y() << 8;
    }

    Sonic2BreakPieceObjectInstance(ObjectSpawn spawn, Sonic2BreakIntoPieces.Piece data,
                                   String artKey, int gravity, int halfWidth, int halfHeight,
                                   boolean explicitHeight, boolean highPriority, int priorityBucket,
                                   boolean birthOnScreen) {
        this(spawn);
        this.piece = data.mapping();
        this.xVelocity = data.xVelocity();
        this.yVelocity = data.yVelocity();
        this.artKey = artKey;
        this.gravity = gravity;
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.explicitHeight = explicitHeight;
        this.highPriority = highPriority;
        this.priorityBucket = priorityBucket;
        this.birthOnScreen = birthOnScreen;
    }

    @Override
    public Sonic2BreakPieceObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Sonic2BreakPieceObjectInstance(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) return;
        // Obj2F_Fragment (s2.asm:49294-49300) and
        // Obj32_Fragment (s2.asm:49433-49439) move before gravity and the
        // inherited on-screen-bit delete.
        subX += (short) xVelocity;
        subY += (short) yVelocity;
        yVelocity = (short) (yVelocity + gravity);
        boolean onScreen = firstUpdate ? birthOnScreen : isWithinSolidContactBounds();
        firstUpdate = false;
        if (!onScreen) com.openggf.level.objects.ObjectLifetimeOps.destroyLatched(this);
    }

    @Override public int getX() { return (short) (subX >> 8); }
    @Override public int getY() { return (short) (subY >> 8); }
    @Override public int getOnScreenHalfWidth() { return halfWidth; }
    @Override public int getOnScreenHalfHeight() { return halfHeight; }
    @Override protected boolean usesCustomRenderHeight() { return explicitHeight; }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public int getPriorityBucket() { return RenderPriority.bucket(priorityBucket); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || artKey == null || piece == null) return;
        var art = services().renderManager();
        var renderer = art == null ? null : art.getRenderer(artKey);
        if (renderer != null && renderer.isReady())
            renderer.drawPieces(List.of(piece), getX(), getY(),
                    (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
}
