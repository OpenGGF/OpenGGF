package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.*;

/** loc_80B22: one tracking hand and its three separately damageable finger SSTs. */
final class DezFinalHand extends DezFinalBossSprite implements RewindRecreatable {
    interface Owner extends ObjectInstance {
        int handControl();
        void handControl(int value);
        void handDestroyed(int subtype);
    }
    private Owner parent;
    private int routine;
    private int timer;
    private int callback;
    private int offsetX;
    private int destroyedMask;
    private int animationCursor;
    private int animationTimer;

    private DezFinalHand(ObjectSpawn spawn) { super(spawn, "DEZFinalHand"); }
    DezFinalHand(Owner parent, int subtype) {
        this(new ObjectSpawn(parent.getX(), parent.getY(), 0, subtype, 0, false, 0)); this.parent = parent;
    }
    @Override public DezFinalHand recreateForRewind(RewindRecreateContext context) { return new DezFinalHand(context.spawn()); }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (parent == null) return;
        switch (routine) {
            case 0 -> {
                routine = 2;
                for (int i = 0; i < 3; i++) {
                    final int index = i;
                    var finger = spawnChild(() -> new Finger(this, index * 2));
                    if (finger == null || finger.isDestroyed()) break;
                }
                offsetX = spawn.subtype() == 0 ? -0x80 : 0x80;
                writeY(0xE3);
                track(); // loc_80B66 falls through, before sub_80A26 refreshes X.
            }
            case 2 -> track();
            case 4, 12 -> { move(0); if (--timer < 0) callback(); }
            case 6 -> animate(0x8133E);
            case 8 -> {
                if (--timer < 0) { routine = 10; control &= ~2; callback = 0x80CAE; }
            }
            case 10 -> animate(0x81349);
            default -> throw new IllegalStateException("DEZ hand routine " + routine);
        }
        if ((destroyedMask & 7) == 7) {
            parent.handDestroyed(spawn.subtype()); pendingDelete = true; status |= 0x80;
        } else writeX(parent.getX() + offsetX);
        updateDynamicSpawn(getX(), getY());
    }
    private void track() {
        if ((parent.handControl() & 2) != 0) {
            routine = 4; yVelocity = -0x80; timer = 7; callback = 0x80C34; return;
        }
        var player = services().playerQuery().mainPlayerOrNull();
        if (player == null) return;
        int correction = (destroyedMask & 1) == 0 ? 0 : (destroyedMask & 2) == 0 ? -0x20 : 0x20;
        int distance = (short) (player.getCentreX() - (getX() + correction));
        int magnitude = Math.abs(distance);
        if (magnitude >= 0x80 || magnitude <= 4) return;
        int anchor = spawn.subtype() == 0 ? -0x80 : 0x80;
        int speed = 2 + 2 * Integer.bitCount(destroyedMask & 7);
        if (distance >= 0) { if (offsetX < anchor + 0x40) offsetX = (short) (offsetX + speed); }
        else if (offsetX >= anchor - 0x40) offsetX = (short) (offsetX - speed);
    }
    private void callback() {
        switch (callback) {
            case 0x80C34 -> { timer = 0x10; yVelocity = 0; callback = 0x80C48; }
            case 0x80C48 -> { routine = 6; callback = 0x80C60; }
            case 0x80C60 -> {
                routine = 8; timer = 0x2F; control |= 2;
                // $39 remains the native initialized value 3, even after finger losses.
                ((DezFinalBossZoneRuntimeState) services().zoneRuntimeState()).screenShake().writeFlag(0xC);
                services().playSfx(Sonic3kSfx.THUMP_BOSS.id);
            }
            case 0x80CAE -> { routine = 12; yVelocity = 0x80; timer = 7; callback = 0x80CCA; }
            case 0x80CCA -> { routine = 2; writeY(0xE3); parent.handControl(parent.handControl() & ~2); }
            default -> throw new IllegalStateException("DEZ hand callback " + callback);
        }
    }
    private void animate(int script) {
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) return;
        animationCursor = (animationCursor + 2) & 0xFF;
        int next = romByte(script + animationCursor);
        if (next == 0xF4) { animationTimer = 0; callback(); animationCursor = 0; }
        else { frame = next; animationTimer = romByte(script + animationCursor + 1); }
    }

    static final class Finger extends DezFinalBossSprite implements RewindRecreatable, TouchResponseProvider, TouchResponseAttackable {
        private DezFinalHand parent;
        private int parentSlot = -1;
        private int routine;
        private int collision;
        private int savedCollision;
        private int flash;
        private int timer;
        private int dx;
        private boolean touchPublished;
        private Finger(ObjectSpawn spawn) { super(spawn, "DEZFinalFinger"); }
        Finger(DezFinalHand parent, int subtype) {
            this(new ObjectSpawn(parent.getX(), parent.getY(), 0, subtype, 0, false, 0)); this.parent = parent; this.parentSlot = parent.getSlotIndex();
        }
        @Override public Finger recreateForRewind(RewindRecreateContext context) { return new Finger(context.spawn()); }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            visible = touchPublished = false;
            if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            // loc_80D64 outlives loc_80B42's Go_Delete_Sprite. Refresh_ChildPosition
            // reads the SST address, not a retained Java object: deleted slots read
            // zero position words and a reused slot supplies its new occupant's words.
            if (routine == 3) {
                trackDyingSlot();
                if (--timer < 0) { pendingDelete = true; status |= 0x80; }
                visible = true; updateDynamicSpawn(getX(), getY()); return;
            }
            if (parent == null) return;
            if (routine == 0) {
                routine = 1; priority = 5; halfWidth = 0x10; halfHeight = 0x60;
                highPriority = false; collisionProperty = 3;
                dx = (byte) romByte(0x8131C + (spawn.subtype() / 2) * 6);
            }
            frame = parent.frame;
            boolean attacking = (parent.control & 2) != 0;
            if (routine == 1 && attacking) {
                routine = 2; collision = 0x1A; highPriority = true; priority = 1;
            } else if (routine == 2 && !attacking) {
                routine = 1; highPriority = false; priority = 5;
            }
            track();
            // sub_810FA returns immediately for a live collision byte on frame 4;
            // the closed-hand path instead enters sub_810D6 directly.
            if (routine == 1 || frame != 4 || collision == 0) {
                if (collisionProperty == 0) defeat();
                else {
                    if (routine == 2 && frame == 4 && collision == 0 && flash == 0) {
                        flash = 0x20; status |= 0x40; services().playSfx(Sonic3kSfx.BOSS_HIT.id);
                    }
                    if (flash != 0) {
                        frame = parent.frame + ((flash & 1) == 0 ? 0x13 : 0);
                        flash = (flash - 1) & 0xFF;
                        if (flash == 0) { status &= ~0x40; collision = savedCollision; }
                    }
                }
            }
            visible = true; touchPublished = routine == 2;
            updateDynamicSpawn(getX(), getY());
        }
        private void trackDyingSlot() {
            ObjectInstance occupant = null;
            for (var candidate : services().objectManager().getActiveObjects()) {
                if (candidate instanceof AbstractObjectInstance object
                        && !object.isDestroyed() && object.getSlotIndex() == parentSlot) {
                    occupant = object; break;
                }
            }
            writeX((occupant == null ? 0 : occupant.getX()) + dx);
            writeY((occupant == null ? 0 : occupant.getY()) - 0x10);
        }
        private void track() { writeX(parent.getX() + dx); writeY(parent.getY() - 0x10); }
        private void defeat() {
            routine = 3; timer = 0x1F;
            spawnChild(() -> new DezMinibossExplosionController(getX(), getY(), 6));
            parent.destroyedMask |= 1 << (spawn.subtype() / 2);
            // loc_80D64 only dereferences the SST address thereafter. Release the
            // identity link now: its owner can retire before this finger, and a
            // rewind snapshot must not try to capture an already-unregistered object.
            parent = null;
        }
        @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
            if (!touchPublished || collision == 0) return;
            savedCollision = collision; collision = 0; collisionProperty = (collisionProperty - 1) & 0xFF;
        }
        @Override public int getCollisionFlags() { return touchPublished ? collision : 0; }
        @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
        @Override public int getCollisionProperty() { return collisionProperty; }
        @Override public boolean requiresContinuousTouchCallbacks() { return true; }
        @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) { return TouchResponseProfile.fromProvider(this, multiRegionSource); }
    }
}
