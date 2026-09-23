package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.*;

/** loc_7F64A: opening shield plus its independent loc_7F6FA visor SST. */
final class DezEndBossShield extends DezEndBossSprite implements RewindRecreatable, TouchResponseProvider {
    private DezEndBossSprite parent;
    private int state;
    private int timer;
    private int childDy = 0x14;
    private int animationCursor;
    private int animationTimer;
    private boolean touchPublished;

    private DezEndBossShield(ObjectSpawn spawn) { super(spawn, "DEZEndBossShield"); }
    DezEndBossShield(DezEndBossSprite parent) {
        this(new ObjectSpawn(parent.getX(), parent.getY()+0x14, 0, 0, 0, false, 0));
        this.parent = parent;
    }
    @Override public DezEndBossShield recreateForRewind(RewindRecreateContext context) {
        return new DezEndBossShield(context.spawn());
    }
    @Override public void update(int clock, PlayableEntity ignored) {
        visible = touchPublished = false;
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (parent == null) return;
        if (state == 0) {
            priority = 4; halfWidth = 0x18; halfHeight = 4; frame = 0x16;
            state = 1;
            spawnChild(() -> new Visor(this));
            draw(true);
        } else {
            if (state == 1 && (parent.control & 4) != 0) { state = 2; timer = 0x10; }
            if (state == 2) {
                if (--timer < 0) { state = 3; timer = 7; }
                else { if ((control & 4) == 0) childDy = (childDy + 1) & 255; control ^= 4; }
            }
            if (state == 3 && --timer < 0) state = 4;
            if (state == 4) {
                if ((control & 4) == 0) childDy = (childDy - 1) & 255;
                control ^= 4;
            }
            follow(); animate();
            if (state == 4 && (parent.control & 8) == 0) goDelete();
            else draw(state == 1 || state == 4);
        }
        updateDynamicSpawn(getX(), getY());
    }
    private void follow() { writeX(parent.getX()); writeY(parent.getY() + (byte) childDy); }
    private void animate() {
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) return;
        animationCursor = (animationCursor + 2) & 255;
        int next = romByte(0x7FCEF + animationCursor);
        if (next == 0xFC) { animationCursor = 0; next = romByte(0x7FCEF); }
        frame = next;
        animationTimer = romByte(0x7FCF0 + animationCursor);
    }
    private void draw(boolean touch) {
        if ((parent.status & 0x80) != 0) goDelete();
        else { visible = true; touchPublished = touch; }
    }
    private void goDelete() { status |= 0x80; pendingDelete = true; }
    @Override public int getCollisionFlags() { return 0x9C; }
    @Override public int getCollisionProperty() { return collisionProperty; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this, multiRegionSource);
    }
    DezEndBossSprite parentForTest() { return parent; }
    int stateForTest() { return state; }
    int childDyForTest() { return childDy; }

    static final class Visor extends DezEndBossSprite implements RewindRecreatable {
        private DezEndBossShield parent;
        private Visor(ObjectSpawn spawn) { super(spawn, "DEZEndBossVisor"); }
        Visor(DezEndBossShield parent) {
            this(new ObjectSpawn(parent.getX(),parent.getY(),0,0,0,false,0)); this.parent=parent;
        }
        @Override public Visor recreateForRewind(RewindRecreateContext context) { return new Visor(context.spawn()); }
        @Override public void update(int clock, PlayableEntity ignored) {
            visible = false;
            if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            if (parent == null) return;
            priority=4; halfWidth=0x18; halfHeight=0xC; frame=0x13;
            writeX(parent.getX()); writeY(parent.getY());
            // Its header has $9C, but loc_7F708 calls Child_Draw_Sprite, never Touch.
            if ((parent.status & 0x80) != 0) { status|=0x80; pendingDelete=true; }
            else visible=true;
            updateDynamicSpawn(getX(),getY());
        }
        DezEndBossShield parentForTest() { return parent; }
    }
}
