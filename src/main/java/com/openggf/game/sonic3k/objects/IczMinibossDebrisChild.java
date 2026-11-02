package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** loc_7168A: four body quarters, followed by Obj_FlickerMove. */
public final class IczMinibossDebrisChild extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private Motion motion;
    private boolean initialized;
    public IczMinibossDebrisChild(ObjectSpawn spawn) {
        super(spawn, "ICZMinibossDebris");
        int index = spawn.subtype() >>> 1;
        motion = new Motion(spawn.x(), spawn.y(), index + 2, 0xB + index, 3, 1);
    }
    @Override public void update(int clock, PlayableEntity player) {
        // Setup dispatch selects velocity but does not move or draw.
        if (!initialized) { initialized = true; motion.visible = false; return; }
        motion.tick(services());
        if (!motion.active) ObjectLifetimeOps.expireDynamic(this);
    }
    @Override public int getX() { return motion.x; }
    @Override public int getY() { return motion.y; }
    @Override public int getPriorityBucket() { return 3; }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.ICZ_MINIBOSS);
        if (renderer != null && renderer.isReady() && motion.active && motion.visible)
            renderer.drawFrameIndex(motion.frame, motion.x, motion.y, false, false, 1);
    }

    /** Captured independently for the fourteen folded native child SSTs. */
    static final class Motion implements RewindStateful<Motion.Snapshot> {
        private static final int[][] VELOCITIES = {{-256,-256},{256,-256},{-512,-512},{512,-512},
                {-768,-512},{768,-512},{-512,-512},{0,-512}};
        int x, y, xSub, ySub, xVel, yVel, frame, bucket, palette;
        boolean visible = true, active = true, odd;
        Motion() {}
        Motion(int x, int y, int index, int frame, int bucket, int palette) {
            this.x=x; this.y=y; this.xVel=VELOCITIES[index][0]; this.yVel=VELOCITIES[index][1];
            this.frame=frame; this.bucket=bucket; this.palette=palette;
        }
        void tick(ObjectServices services) {
            if (!active) return;
            xSub += xVel; ySub += yVel;
            x += xSub >> 8; y += ySub >> 8; xSub &= 255; ySub &= 255; yVel += 0x38;
            odd = !odd; visible = !odd;
            if (services != null && services.camera() != null) {
                int cx = services.camera().getX() & 65535, cy = services.camera().getY() & 65535;
                active = ((((x & 0xFF80) - ((cx-0x80)&0xFF80)) & 65535) <= 0x280)
                        && (((y-cy+0x80)&65535) <= 0x200);
            }
        }
        record Snapshot(int x,int y,int xSub,int ySub,int xVel,int yVel,int frame,int bucket,int palette,
                        boolean visible,boolean active,boolean odd) {}
        @Override public Snapshot captureRewindStateValue() {
            return new Snapshot(x,y,xSub,ySub,xVel,yVel,frame,bucket,palette,visible,active,odd);
        }
        @Override public void restoreRewindStateValue(Snapshot s) {
            x=s.x; y=s.y; xSub=s.xSub; ySub=s.ySub; xVel=s.xVel; yVel=s.yVel;
            frame=s.frame; bucket=s.bucket; palette=s.palette; visible=s.visible; active=s.active; odd=s.odd;
        }
    }
}
