package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** Independent after-current flame allocated by {@link FbzFlamethrowerObjectInstance}. */
public final class FbzFlameObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL,
            false,
            true,
            false,
            TouchShieldDeflectCapability.NONE,
            0x10,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);
    private int x;
    private int y;
    private int xFixed;
    private int xVelocity;
    private int animationBase = 0xC;
    private int animationFrame;
    private int animationTimer = 4;
    private int priorityBucket = 4;

    private FbzFlameObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZFlame");
        x = spawn.x();
        y = spawn.y();
        xFixed = x << 8;
    }

    static FbzFlameObjectInstance rotating(ObjectSpawn spawn, int angle, int animationFrame) {
        FbzFlameObjectInstance flame = new FbzFlameObjectInstance(spawn);
        // GetSineCosine returns sine in d0 and cosine in d1. sub_3CEC0
        // deliberately consumes d1 for both the spawn offset and x_vel.
        int cosine = TrigLookupTable.cosHex(angle);
        flame.xVelocity = cosine << 2;
        flame.priorityBucket = (byte) angle < 0 ? 1 : 4;
        flame.x += cosine >> 4;
        flame.xFixed = flame.x << 8;
        flame.animationFrame = animationFrame;
        return flame;
    }

    static FbzFlameObjectInstance lateral(ObjectSpawn spawn, int angle, int animationFrame, boolean left) {
        FbzFlameObjectInstance flame = new FbzFlameObjectInstance(spawn);
        // loc_3CF4C uses the same d1 cosine component.
        int cosine = TrigLookupTable.cosHex(angle);
        flame.xVelocity = cosine + (cosine >> 1) + 0x280;
        flame.x += left ? -0x10 : 0x10;
        if (left) flame.xVelocity = -flame.xVelocity;
        flame.xFixed = flame.x << 8;
        flame.animationFrame = animationFrame;
        return flame;
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        if (--animationTimer < 0) {
            animationTimer = 5;
            animationBase -= 4;
            if (animationBase == 0) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
        }
        animationFrame = (animationFrame + 1) & 3;
        xFixed += xVelocity;
        x = xFixed >> 8;
        updateDynamicSpawn(x, y);
    }

    int animationBase() { return animationBase; }
    int xVelocity() { return xVelocity; }
    boolean renderFlipX() { return (spawn.renderFlags() & 1) != 0; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getCollisionFlags() { return 0x98; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getShieldReactionFlags() { return 0x10; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TOUCH_RESPONSE_PROFILE; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }
    @Override public int getPriorityBucket() { return priorityBucket; }

    @Override
    public FbzFlameObjectInstance recreateForRewind(RewindRecreateContext context) {
        var recreated = new FbzFlameObjectInstance(context.spawn());
        if (context.state() != null && context.state().compactGenericState() != null) {
            GenericFieldCapturer.restoreObjectSubclassScalarsCompact(
                    recreated, context.state().compactGenericState());
        }
        return recreated;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_FLAMETHROWER);
        if (renderer != null && renderer.isReady() && !isDestroyed()) {
            renderer.drawFrameIndex(animationBase + animationFrame, x, y, renderFlipX(), false);
        }
    }
}
