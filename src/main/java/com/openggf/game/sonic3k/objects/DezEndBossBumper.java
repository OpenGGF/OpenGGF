package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** loc_7F336: native-slot tracking bumper; contact is latched until its own SST pass. */
final class DezEndBossBumper extends DezEndBossSprite
        implements RewindRecreatable, TouchResponseProvider, TouchResponseListener, PoweredScreenAttackSpecial {
    private DezEndBossSprite parent;
    private int angle;
    private int animation;
    private boolean debris;
    private boolean touchPublished;

    private DezEndBossBumper(ObjectSpawn spawn) { super(spawn, "DEZEndBossBumper"); }
    DezEndBossBumper(DezEndBossSprite parent, int subtype) {
        this(new ObjectSpawn(parent.getX(), parent.getY(), 0, subtype, 0, false, 0));
        this.parent = parent;
    }
    @Override public DezEndBossBumper recreateForRewind(RewindRecreateContext context) {
        return new DezEndBossBumper(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        touchPublished = false;
        visible = false;
        if (pendingDelete) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (debris) { updateDebris(); return; }
        if (parent == null) return;
        priority = 3;
        halfWidth = halfHeight = 0x18;
        var query = services().playerQuery();
        var target = spawn.subtype() == 0 ? query.mainPlayerOrNull() : query.nativeP2OrNull();
        // The native slot still contains coordinate words after its code pointer clears.
        // Active rosters supply this slot throughout the encounter.
        int targetX = target == null ? 0 : target.getCentreX();
        int targetY = target == null ? 0 : target.getCentreY();
        angle = S3kNativeObjectAngle.angleTowards(parent.getX(), parent.getY(), targetX, targetY);
        if ((parent.control & 8) != 0 && ((angle + 0x30) & 0xFF) < 0x60) {
            angle = (byte) angle < 0 ? 0xD0 : 0x30;
        }
        int index = angle & 0x3F;
        int first = romByte(0x7FD28 + index), second = romByte(0x7FD28 + 0x3F - index);
        int dx, dy;
        switch (angle >>> 6) {
            case 0 -> { dx = first; dy = second; }
            case 1 -> { dx = second; dy = -first; }
            case 2 -> { dx = -first; dy = -second; }
            default -> { dx = -second; dy = first; }
        }
        writeX(parent.getX() + dx);
        writeY(parent.getY() + dy);
        consumeContact(vIntRunCount);
        int halfAngle = angle & 0x7F;
        int table = halfAngle < 0x40 ? 0x7FB0A : 0x7FB12;
        frame = halfAngle < 0x40 ? 4 : 0xA;
        while (halfAngle >= romByte(table++)) frame++;
        flipX = flipY = (angle & 0x80) != 0;
        visible = true;
        if ((parent.status & 0x80) != 0) {
            // Child_DrawTouch_Sprite_FlickerMove converts only after tracking/contact.
            status |= 0x80;
            debris = true;
            collisionProperty = 0;
            int velocity = 0x852F4 + 8 + spawn.subtype() * 2;
            xVelocity = (short) romWord(velocity);
            yVelocity = (short) romWord(velocity + 2);
            if (flipX) xVelocity = (short) -xVelocity;
        } else touchPublished = true;
        updateDynamicSpawn(getX(), getY());
    }
    private void consumeContact(int vIntRunCount) {
        if (collisionProperty == 0) return;
        var query = services().playerQuery();
        var player = (collisionProperty & 2) != 0 ? query.nativeP2OrNull() : query.mainPlayerOrNull();
        collisionProperty = 0;
        if (player == null) return;
        int launchAngle = (angle + (vIntRunCount & 3) - 2) & 0xFF;
        player.setXSpeed((short) (TrigLookupTable.sinHex(launchAngle) << 3));
        player.setYSpeed((short) (TrigLookupTable.cosHex(launchAngle) << 3));
        player.setAir(true);
        player.setPushing(false);
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.setRollingJump(false);
            sprite.setJumping(false);
        }
        // sub_7FA7E writes anim(a0), not the player's animation.
        animation = 1;
        services().playSfx(Sonic3kSfx.BUMPER.id);
    }
    private void updateDebris() {
        move(0x38);
        if (isCoarseXOutOfRange(getX(), cameraLeft(), coarseXCullRange())
                || ((getY() - cameraTop() + 0x80) & 0xFFFF) > 0x200) {
            status |= 0x80;
            control |= 0x10;
            pendingDelete = true;
            return;
        }
        visible = (control & 0x40) != 0;
        control ^= 0x40;
        updateDynamicSpawn(getX(), getY());
    }
    @Override public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int clock) {
        if (!touchPublished || result.category() != TouchCategory.SPECIAL) return;
        var query = services().playerQuery();
        if (player == query.mainPlayerOrNull()) collisionProperty |= 1;
        else if (player == query.nativeP2OrNull()) collisionProperty |= 2;
    }
    @Override public void orCollisionProperty(int mask) { if (touchPublished) collisionProperty |= mask; }
    @Override public int getCollisionProperty() { return collisionProperty; }
    @Override public int getCollisionFlags() { return debris ? 0 : 0xD7; }
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return touchPublished; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this, multiRegionSource);
    }
    DezEndBossSprite parentForTest() { return parent; }
    int angleForTest() { return angle; }
}
