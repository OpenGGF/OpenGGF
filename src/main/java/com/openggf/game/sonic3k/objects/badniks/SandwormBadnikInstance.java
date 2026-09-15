package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;
import com.openggf.graphics.GLCommand;
import java.util.List;

/** SKL $95: Obj_Sandworm ($8EA6A), body chain, sand warning and splash slots. */
public final class SandwormBadnikInstance extends AbstractS3kBadnikInstance implements SpawnRewindRecreatable {
    private int routine;
    private int timer;
    private int homeY;
    private boolean waiting = true;
    public SandwormBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Sandworm", Sonic3kObjectArtKeys.SANDWORM, 0, 5);
    }
    @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        if (waiting) { if (isOnScreen(0x20)) waiting = false; return; }
        if (routine == 0) {
            routine = 2; timer = 0x7F;
            for (int i = 0; i < 5; i++) {
                int index = i;
                if (spawnChild(() -> new Segment(getSpawn(), this, index)) == null) break;
            }
            warning(); return;
        }
        if (routine == 2) { if (--timer < 0) emerge(); return; }
        if (routine == 6) {
            if (--timer < 0) { routine = 2; facingLeft = !facingLeft; timer = 0x7F; warning(); }
            return;
        }
        if (--animTimer < 0) { animTimer = 9; mappingFrame = (mappingFrame == 0 ? 2 : 0); }
        moveWithVelocity(); yVelocity = (short)(yVelocity + 0x20);
        if ((currentY & 0xFFFF) >= (homeY & 0xFFFF)) {
            currentY = homeY; routine = 6; timer = 0x3F; splash();
        }
    }
    private void emerge() {
        routine = 4; homeY = currentY; yVelocity = -0x400;
        currentX += facingLeft ? -0x60 : 0x60; xVelocity = facingLeft ? -0x200 : 0x200;
        splash();
    }
    private void warning() {
        for (int i = 0; i < 8; i++) {
            int index = i;
            if (spawnChild(() -> new SandEffect(getSpawn(), false, index, facingLeft)) == null) break;
        }
    }
    private void splash() {
        services().playSfx(Sonic3kSfx.SPLASH_2.id);
        for (int i = 0; i < 3; i++) {
            int index = i;
            if (spawnChild(() -> new SandEffect(getSpawn(), true, index, facingLeft)) == null) break;
        }
    }
    @Override public int getCollisionFlags() { return routine == 4 ? 0x0B : 0; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 12; }

    static final class Segment extends AbstractS3kBadnikInstance implements RewindRecreatable {
        private SandwormBadnikInstance owner;
        private int routine;
        private int timer;
        private int homeY;
        private int index;
        private boolean debris;
        private boolean flicker;
        Segment(ObjectSpawn spawn, SandwormBadnikInstance owner, int index) {
            super(spawn, "SandwormSegment", Sonic3kObjectArtKeys.SANDWORM, 0, 5);
            this.owner = owner; this.index = index; mappingFrame = 1;
        }
        @Override public Segment recreateForRewind(RewindRecreateContext context) { return new Segment(context.spawn(), null, 0); }
        @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
            if (debris) {
                moveWithVelocity(); yVelocity = (short)(yVelocity + 0x38); flicker = !flicker;
                int dx = ((currentX & 0xFF80) - ((cameraLeft() - 0x80) & 0xFF80)) & 65535;
                int dy = (currentY - cameraTop() + 0x80) & 65535;
                if (dx > 0x80 + viewportWidth() + 0xC0 || dy > 0x200) setDestroyed(true);
                return;
            }
            if (owner == null || owner.isDestroyed()) {
                // Child_DrawTouch_Sprite_FlickerMove / Set_IndexedVelocity(d0=0).
                debris = true;
                xVelocity = new int[]{-0x100,0x100,-0x200,0x200,-0x300}[index];
                yVelocity = index < 2 ? -0x100 : -0x200;
                if (!facingLeft) xVelocity = -xVelocity;
                return;
            }
            if (routine == 0) { routine = 1; timer = index * 6 + 5; facingLeft = owner.badnikFacingLeft(); return; }
            if (routine == 1) { if (--timer < 0) { routine = 2; timer = 0x7F; } return; }
            if (routine == 2) {
                if (--timer < 0) {
                    routine = 4; homeY = currentY; yVelocity = -0x400;
                    currentX += facingLeft ? -0x60 : 0x60; xVelocity = facingLeft ? -0x200 : 0x200;
                }
                return;
            }
            if (routine == 6) { if (--timer < 0) { routine = 2; facingLeft = !facingLeft; timer = 0x7F; } return; }
            moveWithVelocity(); yVelocity = (short)(yVelocity + 0x20);
            if ((currentY & 65535) >= (homeY & 65535)) { currentY = homeY; routine = 6; timer = 0x3F; }
        }
        @Override public int getOnScreenHalfWidth() { return 8; }
        @Override public int getOnScreenHalfHeight() { return 8; }
        @Override public int getCollisionFlags() { return routine == 4 && !debris && !isDestroyed() ? 0x8B : 0; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if (!debris || !flicker) super.appendRenderCommands(commands);
        }
        @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) { }
        @Override public void onPoweredScreenAttack(PlayableEntity player) { }
    }

    /** loc_8EBE2 warning and loc_8EC74 splash are independent self-expiring children. */
    static final class SandEffect extends AbstractS3kBadnikInstance implements RewindRecreatable {
        private boolean splash;
        private int routine;
        private int timer;
        private int index;
        SandEffect(ObjectSpawn spawn) { this(spawn, false, 0, true); }
        SandEffect(ObjectSpawn spawn, boolean splash, int index, boolean left) {
            super(spawn, "SandwormSand", Sonic3kObjectArtKeys.SANDWORM, 0, 4);
            this.splash = splash; this.index = index; facingLeft = left;
            currentY += splash ? 4 : -8;
            currentX += splash ? new int[]{0,-4,4}[index] : (left ? -1 : 1) * index * 8;
            timer = splash ? index * 4 : index * 8 - 1;
            mappingFrame = splash ? 5 : 3;
        }
        @Override public SandEffect recreateForRewind(RewindRecreateContext context) { return new SandEffect(context.spawn(), false, 0, true); }
        @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) { routine = 1; if (!splash) return; }
            if (routine == 1) {
                if (--timer < 0) { routine = 2; yVelocity = splash ? -0x400 : -0x80; timer = splash ? 7 : 15; }
                return;
            }
            if (!splash) {
                animate(1, 3, 4); moveWithVelocity();
                if (--timer < 0) { if (routine == 2) { routine = 3; yVelocity = 0x40; timer = 31; } else setDestroyed(true); }
                return;
            }
            if (routine == 2) {
                animate(1, 5, 6); moveWithVelocity();
                if (--timer < 0) { routine = 3; timer = 23; }
            } else if (routine == 3) {
                currentY += (timer & 1) == 0 ? -8 : 8; animate(1, 5, 6);
                if (--timer < 0) { routine = 4; animTimer = 0; animFrame = 0; }
            } else {
                // byte_8ED98: frame 7/3, frame 7/3, frame 8/5, callback delete.
                if (--animTimer < 0) {
                    if (++animFrame >= 3) setDestroyed(true);
                    else { mappingFrame = animFrame == 2 ? 8 : 7; animTimer = animFrame == 2 ? 5 : 3; }
                }
            }
        }
        private void animate(int delay, int a, int b) { if (--animTimer < 0) { animTimer = delay; mappingFrame = (++animFrame & 1) == 0 ? a : b; } }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || routine <= 1) return;
            var renderer = getRenderer(Sonic3kObjectArtKeys.SANDWORM);
            if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false, 2);
        }
        @Override public int getOnScreenHalfWidth() { return splash ? 4 : 8; }
        @Override public int getOnScreenHalfHeight() { return splash ? 16 : 4; }
        @Override public int getCollisionFlags() { return 0; }
        @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) { }
        @Override public void onPoweredScreenAttack(PlayableEntity player) { }
    }
}
