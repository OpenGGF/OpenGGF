package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import java.io.IOException;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One {@code BossExplosionHitbox} child from {@code ChildObjDat_690D8}.
 *
 * <p>The ROM creates seven independent object slots.  Each starts with
 * {@code ObjDat_BossExplosionHitbox} ($97 collision, {@code Map_BossExplosion})
 * and uses its subtype to stagger {@code AniRaw_BossExplosion} before the
 * animation starts.</p>
 */
public final class AizMinibossNapalmExplosionChild extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    static final int[] X_OFFSETS = {0, 8, -8, 4, -4, 4, -4}; // ChildObjDat_690D8
    static final int[] Y_OFFSETS = {-0x24, -0x1C, -0x1C, -0x14, -0x14, -4, -4};

    private static final int COLLISION_FLAGS = 0x97; // ObjDat_BossExplosionHitbox
    private static final int RAW_SCRIPT = 0x83FCC; // AniRaw_BossExplosion frame/delay bank.
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE =
            TouchResponseProfile.standardEnemy();

    private enum State {
        INIT,
        WAIT,
        READY,
        ANIMATE
    }

    private int currentX;
    private int currentY;
    private int subtype;
    private boolean hazardous;
    private State state;
    private int delayTimer;
    private int animationIndex;
    private int animationTimer;
    private int rawCursor;
    private boolean pendingDelete;

    public AizMinibossNapalmExplosionChild(int x, int y, int subtype) {
        this(x, y, subtype, true);
    }

    public AizMinibossNapalmExplosionChild(int x, int y, int subtype, boolean hazardous) {
        this(new ObjectSpawn(x, y, Sonic3kObjectIds.AIZ_MINIBOSS, subtype & 0xFF, 0, false, 0));
        this.hazardous = hazardous; // native Init copies the parent's live collision byte.
    }

    private AizMinibossNapalmExplosionChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossNapalmExplosion");
        currentX = spawn.x(); currentY = spawn.y(); subtype = spawn.subtype() & 0xFF;
        state = State.INIT;
        delayTimer = (0x0C - subtype) * 2; // BossChild_SetSubtypeDelay word counter.
        animationIndex = 0; // cleared mapping byte; raw cursor is independent.
    }

    @Override
    public AizMinibossNapalmExplosionChild recreateForRewind(RewindRecreateContext ctx) {
        return new AizMinibossNapalmExplosionChild(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (pendingDelete) {
            ObjectLifetimeOps.expireDynamic(this); // next physical Delete_Current_Sprite dispatch.
            return;
        }
        switch (state) {
            case INIT -> {
                // BossExplosionHitbox_Init is a real routine-0 dispatch.  It
                // installs ObjDat_BossExplosionHitbox, the animation script,
                // and the subtype delay before the first Obj_Wait dispatch.
                state = State.WAIT;
            }
            case WAIT -> {
                if (--delayTimer < 0) {
                    // BossExplosionHitbox_StartAnim changes routine to 4 but
                    // the current dispatch still has no Draw_And_Touch call.
                    // Keep a distinct READY state so harmful touch and the
                    // first Animate_RawMultiDelay step begin on the following
                    // routine-4 dispatch.
                    state = State.READY;
                }
            }
            case READY -> {
                // BossExplosionHitbox_Animate is routine 4's first dispatch;
                // Animate_RawMultiDelay must publish frame 0 before touch and
                // rendering become observable.
                advanceAnimation();
                state = State.ANIMATE;
            }
            case ANIMATE -> advanceAnimation();
        }
    }

    private void advanceAnimation() {
        animationTimer = (animationTimer - 1) & 0xFF;
        if ((byte) animationTimer >= 0) return;
        rawCursor = (rawCursor + 2) & 0xFF;
        int frame = rawByte(rawCursor);
        if (frame == 0xF4) {
            // Callback clears raw cursor/timer; Animate still tail-jumps Draw_And_Touch.
            rawCursor = 0;
            animationTimer = 0;
            pendingDelete = true;
            return;
        }
        animationIndex = frame;
        animationTimer = rawByte(rawCursor + 1);
    }

    private int rawByte(int offset) {
        try {
            var reader = services().romReader();
            if (reader == null) throw new IllegalStateException("BossExplosionHitbox requires its ROM raw animation");
            return reader.readU8(RAW_SCRIPT + offset);
        } catch (IOException e) {
            throw new IllegalStateException("BossExplosionHitbox requires its ROM raw animation", e);
        }
    }

    @Override
    public int getCollisionFlags() {
        return hazardous && state == State.ANIMATE && !isDestroyed()
                ? COLLISION_FLAGS
                : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return buildSpawnAt(currentX, currentY);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_BossExplosion2,0,0): priority bit is clear.
        return false;
    }

    @Override
    public int getPriorityBucket() {
        // ObjDat_BossExplosionHitbox priority is $80.
        return 1;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state != State.ANIMATE) {
            return;
        }
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(ObjectArtKeys.BOSS_EXPLOSION);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(animationIndex, currentX, currentY, false, false);
    }
}
