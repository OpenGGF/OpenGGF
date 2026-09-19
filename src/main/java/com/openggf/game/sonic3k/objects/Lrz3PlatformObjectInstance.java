package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** ROM {@code Obj_LRZ3Platform} ({@code $AD}, sonic3k.asm:162058-162553). */
public final class Lrz3PlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {
    private int subtype;
    private int x;
    private int y;
    private int ySub;
    private int yVelocity;
    private int timer;
    private boolean activated;
    private boolean spawned;
    private boolean movingChild;

    private record Extra(int x, int y, int ySub, int yVelocity, int timer,
                         boolean activated, boolean spawned, boolean movingChild)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    public Lrz3PlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZ3Platform");
        subtype = spawn.subtype() & 0xFF;
        x = spawn.x() & 0xFFFF;
        y = spawn.y() & 0xFFFF;
        timer = subtype == 0 ? 0x37F : 0;
        movingChild = subtype == 3;
        if (movingChild) {
            yVelocity = -0x80;
            timer = 0x4FF;
            activated = true;
        }
    }

    @Override public int romObjectCodePointerHighWord() { return 0x0007; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (subtype == 4) return; // loc_79CF8: static full solid; raw animation is visual only.
        if (movingChild) {
            moveChild();
            return;
        }
        if (!activated) {
            int dx = (short) (x - services().camera().getX());
            int dy = (short) (y - services().camera().getY());
            if (dx < 0 || dx >= 0x140 || dy < 0 || dy >= 0xE0) return;
            activated = true;
        }
        if (subtype == 0) {
            if (timer-- < 0) {
                timer = 0x17F;
                spawnMovingPlatform(y + (spawned ? 0 : 0xC0));
                spawned = true;
            }
        } else if (!spawned) {
            spawned = true;
            spawnMovingPlatform(y);
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    private void spawnMovingPlatform(int childY) {
        spawnDynamicObject(new Lrz3PlatformObjectInstance(new ObjectSpawn(
                x, childY & 0xFFFF, spawn.objectId(), 3, spawn.renderFlags(), false, 0)));
    }

    /** {@code loc_79D28}: rise for $20 frames, then descend until the lifetime expires. */
    private void moveChild() {
        if (!spawned) {
            spawned = true;
            timer = 0x20;
        }
        int fixed = (y << 8) | (ySub & 0xFF);
        fixed += (short) yVelocity;
        y = (fixed >> 8) & 0xFFFF;
        ySub = fixed & 0xFF;
        int remaining = timer--;
        if (remaining < 0 && yVelocity < 0) {
            yVelocity = 0x80;
            timer = 0x4FF;
        } else if (remaining < 0 && yVelocity > 0) {
            ObjectLifetimeOps.expireDynamic(this);
        }
        updateDynamicSpawn(x, y);
    }

    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x23, 0x10, 0x0D); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() { return SolidRoutineProfile.topSolid(false); }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x180); }
    @Override public int getOnScreenHalfWidth() { return subtype == 4 ? 0x2B : 0x23; }
    @Override public int getOnScreenHalfHeight() { return subtype == 4 ? 0x19 : 0x10; }
    @Override public boolean isPersistent() { return true; }

    @Override public Lrz3PlatformObjectInstance recreateForRewind(RewindRecreateContext c) {
        return new Lrz3PlatformObjectInstance(c.spawn());
    }
    @Override public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext c) {
        return super.captureRewindState(c).withObjectSubclassExtra(
                new Extra(x,y,ySub,yVelocity,timer,activated,spawned,movingChild));
    }
    @Override public void restoreRewindState(PerObjectRewindSnapshot s, RewindCaptureContext c) {
        super.restoreRewindState(s,c);
        if (s.objectSubclassExtra() instanceof Extra e) {
            x=e.x(); y=e.y(); ySub=e.ySub(); yVelocity=e.yVelocity(); timer=e.timer();
            activated=e.activated(); spawned=e.spawned(); movingChild=e.movingChild();
            updateDynamicSpawn(x,y);
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_COLLAPSE_BLOCK);
        if (renderer != null) renderer.drawFrameIndex(subtype == 4 ? 1 : 0, getX(), getY(), false, false);
    }
    public int subtypeForTest() { return subtype; }
    public int yVelocityForTest() { return yVelocity; }
    public int timerForTest() { return timer; }
}
