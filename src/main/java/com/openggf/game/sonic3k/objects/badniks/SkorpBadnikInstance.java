package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;

/** SKL $94, Obj_Skorp / loc_8E670–sub_8EA1C. Six actual linked tail slots. */
public final class SkorpBadnikInstance extends AbstractS3kBadnikInstance implements SpawnRewindRecreatable {
    private int routine;
    private int timer;
    private int patrolPeriod;
    private int attackBits;
    private int targetX;
    private int targetY;
    private boolean waiting = true;
    private boolean placeholderSubmitted;
    private boolean placeholderRenderedOnscreen;

    public SkorpBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Skorp", Sonic3kObjectArtKeys.SKORP, 6, 3);
    }
    @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        if (waiting) {
            // Obj_WaitOffscreen/loc_85AD2 queues a $20-by-$20 placeholder; only the next
            // pass after Render_Sprites marks it on-screen restores Obj_Skorp (loc_85B02).
            if (placeholderRenderedOnscreen) { waiting = false; placeholderSubmitted = false; }
            else placeholderSubmitted = true;
            return;
        }
        if (routine == 0) {
            routine = 2;
            xVelocity = facingLeft ? -0x80 : 0x80;
            timer = (short) ((spawn.subtype() & 255) - 1);
            patrolPeriod = (short) (timer * 2 + 1);
            AbstractS3kBadnikInstance previous = this;
            for (int i = 0; i < 6; i++) {
                int index = i;
                AbstractS3kBadnikInstance link = previous;
                Tail child = spawnChild(() -> new Tail(getSpawn(), this, link, index));
                if (child == null) break;
                previous = child;
            }
            return;
        }
        if (routine == 4) { if ((attackBits & 2) == 0) routine = 2; return; }
        if (player != null) {
            int dx = (short) (player.getCentreX() - currentX);
            int dy = Math.abs((short) (player.getCentreY() - currentY));
            if ((facingLeft ? dx < 0 : dx >= 0) && Math.abs(dx) >= 0x20 && Math.abs(dx) < 0x80 && dy < 0x28) {
                routine = 4; attackBits |= 2;
                targetX = player.getCentreX(); targetY = player.getCentreY(); return;
            }
        }
        if (--animTimer < 0) { animTimer = 9; mappingFrame = new int[]{1,0,1,2}[++animFrame & 3]; }
        moveWithVelocity();
        // ObjHitFloor2 probes one velocity step ahead and accepts [-1,12).
        var floor = ObjectTerrainUtils.checkFloorDist(currentX + ((xSubpixel + xVelocity) >> 8), currentY, 11);
        if (floor != null && floor.distance() >= -1 && floor.distance() < 12) {
            currentY += floor.distance();
            timer = (short) (timer - 1);
            if (timer < 0) reverse();
        } else reverse();
    }
    @Override protected void onRemovedFromObjectManager() {
        // loc_8E744 tests the root's retired status before drawing each tail.
        // A converted/deleted SST is no longer a Java owner: retain each tail
        // until its own next dispatch, but never retain the retired identity.
        var manager = services().objectManager();
        if (manager != null) for (var object : manager.getActiveObjects()) {
            if (object instanceof Tail tail && tail.owner == this) {
                tail.owner = null;
                tail.previous = null;
            }
        }
    }
    @Override public void refreshPostCameraRenderState() {
        if (waiting && placeholderSubmitted) placeholderRenderedOnscreen = isWithinRenderSpriteBounds(0x20, 0x20);
    }
    private void reverse() { xVelocity = -xVelocity; facingLeft = !facingLeft; timer = patrolPeriod; }
    @Override public int getCollisionFlags() { return routine == 0 ? 0 : 6; }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x14; }

    static final class Tail extends AbstractS3kBadnikInstance implements RewindRecreatable {
        private SkorpBadnikInstance owner;
        private AbstractS3kBadnikInstance previous;
        private int index;
        private int routine;
        private int angle;
        private int wobbleTimer;
        private boolean wobbleDirection;
        private int timer;
        private int callback;
        private static final int[] REST = {0,0x90,0xA0,0xB0,0xB8,0xC0};
        private static final int[] CURL = {0,0x68,0x58,0x48,0x38,0x38};
        Tail(ObjectSpawn spawn) { this(spawn, null, null, 0); }
        Tail(ObjectSpawn spawn, SkorpBadnikInstance owner, AbstractS3kBadnikInstance previous, int index) {
            super(spawn, "SkorpTail", Sonic3kObjectArtKeys.SKORP, 0, index == 5 ? 2 : 3);
            this.owner = owner; this.previous = previous; this.index = index;
            angle = REST[index]; mappingFrame = index == 5 ? 4 : 3;
        }
        @Override public Tail recreateForRewind(RewindRecreateContext context) {
            return new Tail(context.spawn(), null, null, 0);
        }
        @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
            if (owner == null || previous == null || owner.isDestroyed()) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
            facingLeft = previous.badnikFacingLeft();
            if (routine == 0) routine = 2; // loc_8E78C falls through to idle.
            switch (routine) {
                case 2 -> {
                    if (index == 0) { currentX = previous.getX() + (facingLeft ? 8 : -8); currentY = previous.getY() - 16 - previous.mappingFrame; return; }
                    if ((owner.attackBits & 2) != 0) { routine = 4; callback = 8; owner.attackBits &= 0xF3; return; }
                    wobbleTimer = (byte)(wobbleTimer - 1);
                    if (wobbleTimer < 0) { wobbleTimer = 0x10; wobbleDirection = !wobbleDirection; }
                    if ((wobbleTimer & 1) == 0) angle = (angle + (wobbleDirection ? 1 : -1)) & 255;
                    circular();
                }
                case 4 -> {
                    angle = (angle - 8) & 255;
                    if (angle <= CURL[index]) { angle = CURL[index]; routine = 6; if (index == 5) owner.attackBits |= 8; }
                    circular();
                }
                case 6 -> { if ((owner.attackBits & 8) != 0) routine = callback; }
                case 8 -> {
                    angle = (angle + 16) & 255;
                    if ((byte) angle < 0) { angle = 0x80; routine = 12; timer = 15; aim(); }
                    circular();
                }
                case 10 -> { if (--timer < 0) { routine = 14; timer = 15; xVelocity = -xVelocity; yVelocity = -yVelocity; } }
                case 12 -> { moveWithVelocity(); if (--timer < 0) { routine = 10; timer = 15; } }
                case 14 -> { moveWithVelocity(); if (--timer < 0) { routine = 4; callback = 16; owner.attackBits &= ~8; } }
                case 16 -> {
                    angle = (angle + 8) & 255;
                    if (angle >= REST[index]) { angle = REST[index]; routine = 18; if (index == 5) owner.attackBits |= 4; }
                    circular();
                }
                case 18 -> { if ((owner.attackBits & 4) != 0) { routine = 2; owner.attackBits &= ~2; wobbleTimer = 0; wobbleDirection = false; } }
                default -> throw new IllegalStateException("Skorp tail routine " + routine);
            }
        }
        private void circular() {
            int dx = TrigLookupTable.sinHex(angle) << 3;
            if (!facingLeft) dx = -dx;
            int xp = (previous.getX() << 8) + previous.xSubpixel + dx;
            int yp = (previous.getY() << 8) + previous.ySubpixel + (TrigLookupTable.cosHex(angle) << 3);
            currentX = xp >> 8; xSubpixel = xp & 255; currentY = yp >> 8; ySubpixel = yp & 255;
        }
        private void aim() {
            int scale = new int[]{0x3333,0x3333,0x6666,0x9999,0xCCCC,0x10000}[index];
            int dx = (short)(owner.targetX - currentX), dy = (short)(owner.targetY - currentY);
            xVelocity = (short)(((Math.abs(dx) * scale) >>> 16) * 16 * Integer.signum(dx));
            yVelocity = (short)(((Math.abs(dy) * scale) >>> 16) * 16 * Integer.signum(dy));
        }
        // loc_8E76C only draws; the root retirement check owns tail lifetime.
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public int getOnScreenHalfWidth() { return 0x10; }
        @Override public int getOnScreenHalfHeight() { return 0x14; }
        @Override public int getCollisionFlags() { return isDestroyed() || index != 5 ? 0 : 0x87; }
        @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) { }
        @Override public void onPoweredScreenAttack(PlayableEntity player) { }
    }
}
