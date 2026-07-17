package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Locked-on {@code Obj_FBZExitDoor} ($CE), {@code loc_70C20-loc_70C72}. */
public final class FbzExitDoorInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, SpawnRewindRecreatable {
    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVelocity;
    private int yVelocity;
    private boolean flying;

    public FbzExitDoorInstance(ObjectSpawn spawn) {
        super(spawn, "FBZExitDoor");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (flying) {
            SubpixelMotion.State motion = new SubpixelMotion.State(
                    currentX, currentY, xSub, ySub, xVelocity, yVelocity);
            SubpixelMotion.objectFallXY(motion, 0x20);
            currentX = motion.x;
            currentY = motion.y;
            xSub = motion.xSub;
            ySub = motion.ySub;
            yVelocity = motion.yVel;
            return; // loc_70C66 draws persistently after the hit.
        }
    }

    @Override public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (flying || result.category() != TouchCategory.SPECIAL) return;
        flying = true;
        xVelocity = 0x800;
        PlayableEntity main = player;
        if (tryServices() != null && services().playerQuery() != null) {
            PlayableEntity queried = services().playerQuery().mainPlayerOrNull();
            if (queried != null) main = queried;
        }
        if (main instanceof AbstractPlayableSprite sprite) NativePositionOps.addXPosPreserveSubpixel(sprite, -8);
        else main.setCentreX((short) (main.getCentreX() - 8));
        if (tryServices() != null) services().playSfx(Sonic3kSfx.FLOOR_THUMP.id);
    }

    @Override public int getCollisionFlags() { return !flying ? 0xD7 : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile() {
        return TouchResponseProfile.fromProvider(this);
    }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this, multiRegionSource);
    }
    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }
    @Override public int getPriorityBucket() { return 1; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_EXIT_DOOR);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, currentX, currentY, false, false);
    }

    public boolean isFlying() { return flying; }
    int xVelocity() { return xVelocity; }
    int yVelocity() { return yVelocity; }
    void triggerForTest() { flying = true; xVelocity = 0x800; }
}
