package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Tails;

/** SKL $96 Obj_Rockn, loc_8EDC0–loc_8F042, with independently solid shell. */
public final class RocknBadnikInstance extends AbstractS3kBadnikInstance implements SpawnRewindRecreatable {
    private int routine;
    private int leftLimit;
    private int rightLimit;
    private boolean shellRaised;
    private boolean following;
    private boolean waiting = true;
    public RocknBadnikInstance(ObjectSpawn spawn) { super(spawn, "Rockn", Sonic3kObjectArtKeys.ROCKN, 0, 5); }
    @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        if (waiting) { if (isOnScreen(0x20)) waiting = false; return; }
        if (routine == 0) {
            routine = 2;
            int subtype = spawn.subtype() & 255;
            leftLimit = subtype == 0 ? 0 : (currentX - (subtype & 0xF0)) & 65535;
            rightLimit = subtype == 0 ? 0x7FFF : (currentX + ((subtype & 15) << 4)) & 65535;
            xVelocity = facingLeft ? -0x80 : 0x80;
            spawnChild(() -> new Shell(getSpawn(), this)); return;
        }
        if (routine == 2) { if (shellRaised) routine = 4; return; }
        if (--animTimer < 0) { animTimer = 8; mappingFrame = new int[]{0,1,0,2}[++animFrame & 3]; }
        following = false;
        if (player != null && (player.getCentreX() & 65535) >= leftLimit && (player.getCentreX() & 65535) < rightLimit
                && Math.abs((short)(player.getCentreY() - currentY)) < 16) {
            following = true; xVelocity = (player.getCentreX() & 65535) < (currentX & 65535) ? -0x80 : 0x80;
        }
        if (xVelocity != 0) {
            var floor = ObjectTerrainUtils.checkFloorDist(currentX + ((xSubpixel + xVelocity) >> 8), currentY, 7);
            if (floor != null && floor.distance() >= -1 && floor.distance() < 12) currentY += floor.distance();
            else if (following) xVelocity = 0;
            else { facingLeft = !facingLeft; xVelocity = facingLeft ? -0x80 : 0x80; }
        }
        if (xVelocity != 0 || !following) {
            xVelocity = xVelocity < 0 ? -0x80 : 0x80;
            facingLeft = xVelocity < 0;
            if (facingLeft ? (currentX & 65535) < leftLimit : (currentX & 65535) >= rightLimit) {
                xVelocity = -xVelocity; facingLeft = !facingLeft;
            }
            moveWithVelocity();
        }
        checkGroundAttack(player);
        if (!isDestroyed() && tryServices() != null) checkGroundAttack(services().playerQuery().nativeP2OrNull());
    }
    private void checkGroundAttack(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player) || player.getAir()) return;
        int dx = (short)(player.getCentreX() - currentX), dy = (short)(player.getCentreY() - currentY);
        if (dx < -0x29 || dx >= 0x29 || dy < -16 || dy >= 16) return;
        boolean attack = player.getInvincibleFrames() > 0 || player.isSuperSonic() || player.getAnimationId() == 9 || player.getAnimationId() == 2;
        if (player instanceof Knuckles) attack |= player.getDoubleJumpFlag() == 1 || player.getDoubleJumpFlag() == 3;
        if (player instanceof Tails && player.getDoubleJumpFlag() != 0 && !player.isInWater()) {
            int angle = TrigLookupTable.calcAngle((short)dx, (short)dy);
            attack |= ((angle - 0x20) & 255) < 0x40;
        }
        if (attack) {
            int speed = player.getYSpeed();
            player.setYSpeed((short)(speed < 0 ? speed + 0x100 : player.getCentreY() >= currentY ? speed - 0x100 : -speed));
            defeat(player);
        }
    }
    // Touch_Special ignores size $19. Ground attacks are checked by sub_8EEF4.
    @Override protected void onRemovedFromObjectManager() {
        var manager = services().objectManager();
        if (manager != null) for (var object : manager.getActiveObjects()) {
            if (object instanceof Shell shell && shell.owner == this) shell.owner = null;
        }
    }
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public int getCollisionFlags() { return routine == 4 ? 0xD9 : 0; }
    @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) { }
    @Override public int getOnScreenHalfWidth() { return 16; }
    @Override public int getOnScreenHalfHeight() { return 8; }

    static final class Shell extends AbstractS3kBadnikInstance implements SolidObjectProvider, RewindRecreatable {
        private RocknBadnikInstance owner;
        private int routine;
        private int timer;
        Shell(ObjectSpawn spawn, RocknBadnikInstance owner) {
            super(spawn, "RocknShell", Sonic3kObjectArtKeys.SOZ_BREAKABLE_SAND_ROCK, 0, 4);
            this.owner = owner; currentY -= 8;
        }
        @Override public Shell recreateForRewind(RewindRecreateContext context) { return new Shell(context.spawn(), null); }
        @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
            if (owner == null || owner.isDestroyed()) { setDestroyed(true); return; }
            if (routine == 0) { routine = 2; return; }
            if (routine == 2) {
                if (player != null && Math.abs((short)(player.getCentreX() - currentX)) <= 0x28
                        && Math.abs((short)(player.getCentreY() - currentY)) <= 0x40) {
                    routine = 4; timer = 7; spawnChild(() -> new Legs(getSpawn(), this));
                }
            } else if (routine == 4) { if (--timer < 0) { routine = 6; timer = 7; } }
            else if (routine == 6) { currentY -= 2; if (--timer < 0) { routine = 8; owner.shellRaised = true; } }
            else { currentX = owner.getX(); currentY = owner.getY() - (owner.mappingFrame == 0 ? 24 : 23); }
        }
        @Override protected void onRemovedFromObjectManager() {
            // Child_Draw_Sprite retires its dependent display on the next pass.
            var manager = services().objectManager();
            if (manager != null) for (var object : manager.getActiveObjects()) {
                if (object instanceof Legs legs && legs.owner == this) legs.owner = null;
            }
        }
        @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x23, 0x10, 0x11); }
        @Override public boolean isSolidFor(PlayableEntity player) { return !isDestroyed(); }
        @Override public boolean usesInclusiveRightEdge() { return true; }
        @Override public int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) { return 0x18; }
        @Override public int getOnScreenHalfWidth() { return 24; }
        @Override public int getOnScreenHalfHeight() { return 16; }
        @Override public int getCollisionFlags() { return 0; }
        @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) { }
        @Override public void onPoweredScreenAttack(PlayableEntity player) { }
    }
    static final class Legs extends AbstractS3kBadnikInstance implements RewindRecreatable {
        private Shell owner;
        private int timer = 5;
        Legs(ObjectSpawn spawn, Shell owner) { super(spawn, "RocknLegs", Sonic3kObjectArtKeys.ROCKN, 0, 3); this.owner = owner; mappingFrame = 4; }
        @Override public Legs recreateForRewind(RewindRecreateContext context) { return new Legs(context.spawn(), null); }
        @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
            if (owner == null || owner.isDestroyed()) { setDestroyed(true); return; }
            if (--timer < 0) { mappingFrame = 3; timer = -1; }
            currentX = owner.getX(); currentY = owner.getY();
        }
        @Override public int getOnScreenHalfWidth() { return 24; }
        @Override public int getOnScreenHalfHeight() { return 16; }
        @Override public int getCollisionFlags() { return 0; }
        @Override public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) { }
        @Override public void onPoweredScreenAttack(PlayableEntity player) { }
    }
}
