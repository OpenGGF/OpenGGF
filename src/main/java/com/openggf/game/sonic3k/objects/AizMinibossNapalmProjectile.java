package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * AIZ miniboss falling shot (ROM {@code AIZMiniboss_FallingShot}, loc_68C96).
 *
 * <p>The live Knuckles route uses the same object routine as the final barrel
 * shot.  It rises for the barrel-shot wait, is placed at the camera's top edge,
 * then falls with {@code MoveSprite2} until {@code ObjHitFloor_DoRoutine}
 * dispatches the explosion callback.  The routine publishes its collision entry
 * after movement, so the touch projection intentionally uses the post-movement
 * position.</p>
 */
public class AizMinibossNapalmProjectile extends AbstractObjectInstance
        implements TouchResponseProvider, SpawnCoordinateRewindRecreatable {
    private static final int COLLISION_FLAGS_HAZARD = 0x98; // ObjDat_AIZMiniboss_BarrelShot
    private static final int FRAME_RISE_A = 0x0C;
    private static final int FRAME_RISE_B = 0x0D;
    private static final int PROJECTILE_PALETTE = 0;
    private static final int Y_RADIUS = 8; // FallingShot_Init: move.b #8,y_radius(a0)
    private static final int RISE_WAIT = 0x60; // BarrelShot_Init: move.w #$60,$2E(a0)
    private static final int DROP_PAUSE = 8; // FallingShot_StartPause
    private static final int[] FALLING_SHOT_DELAYS = {0, 0x20, 0x40};

    // AIZMiniboss_ShotSlotsRight/Left and AIZMiniboss_ShotXOffsetsRight/Left.
    private static final int[] SHOT_SLOTS_RIGHT = {
            2, 3, 4, 0, 0, 2, 4, 0, 1, 3, 4, 0, 0, 1, 4, 0
    };
    private static final int[] SHOT_SLOTS_LEFT = {
            3, 2, 0, 0, 4, 3, 1, 0, 4, 2, 0, 0, 3, 2, 1, 0
    };
    private static final int[] SHOT_X_OFFSETS_RIGHT = {0x24, 0x4C, 0x74, 0x9C, 0xC4};
    private static final int[] SHOT_X_OFFSETS_LEFT = {0x7C, 0xA4, 0xCC, 0xF4, 0x11C};

    /* ObjDat_AIZMiniboss_BarrelShot has no shield-reaction write in the native
     * FallingShot path, so this independent live route keeps the reaction byte
     * clear rather than borrowing the nearby barrel-shot approximation. */
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL,
            false,
            true,
            false,
            TouchShieldDeflectCapability.NONE,
            0,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    private enum State {
        INIT,
        RISE,
        PAUSE,
        DROP_WAIT,
        FALL
    }

    /* These are the object-RAM fields needed by the native routine. */
    private int currentX;
    private int currentY;
    private int xFixed;
    private int yFixed;
    private int yVel;
    private int timer;
    private int mappingFrame;
    private int animTimer;
    private int barrelSubtype;
    private int positionCounter;
    private boolean hFlip;
    private boolean vFlip;
    private boolean needsInitSfx;
    private State state;

    /**
     * Constructs the live Knuckles shot.  The default source is the first AIZ
     * barrel slot; the extended constructor is used by tests/route owners that
     * have the native barrel subtype and facing available.
     */
    public AizMinibossNapalmProjectile(int startX, int startY) {
        this(startX, startY, 0, false, 0);
    }

    AizMinibossNapalmProjectile(int startX, int startY,
                                int barrelSubtype, boolean hFlip, int positionCounter) {
        super(new ObjectSpawn(startX, startY, Sonic3kObjectIds.AIZ_MINIBOSS,
                barrelSubtype, 0, false, 0), "AIZNapalmProjectile");
        this.currentX = startX;
        this.currentY = startY;
        this.xFixed = startX << 16;
        this.yFixed = startY << 16;
        this.yVel = 0;
        this.timer = 0;
        this.mappingFrame = FRAME_RISE_A;
        this.animTimer = 2;
        this.barrelSubtype = barrelSubtype & 0xFF;
        this.positionCounter = positionCounter & 0xFF;
        this.hFlip = hFlip;
        this.vFlip = false;
        this.needsInitSfx = true;
        this.state = State.INIT;
    }

    /** Probe constructor retained for object construction tests. */
    AizMinibossNapalmProjectile(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), spawn.subtype(), false, 0);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        switch (state) {
            case INIT -> initializeNativeObject();
            case RISE -> updateRise();
            case PAUSE -> updatePause();
            case DROP_WAIT -> updateDropWait();
            case FALL -> updateFall();
        }
    }

    /** AIZMiniboss_FallingShot_Init -> AIZMiniboss_BarrelShot_Init. */
    private void initializeNativeObject() {
        if (needsInitSfx) {
            needsInitSfx = false;
            services().playSfx(Sonic3kSfx.PROJECTILE.id);
        }
        yVel = -0x400;
        timer = RISE_WAIT;
        mappingFrame = FRAME_RISE_A;
        animTimer = 2;
        state = State.RISE;
    }

    /** AIZMiniboss_FallingShot_Rise: Animate_Raw, MoveSprite2, Obj_Wait. */
    private void updateRise() {
        animateBarrelShot();
        moveSprite2();
        if (--timer < 0) {
            // AIZMiniboss_FallingShot_StartPause: routine 4, timer = 8.
            timer = DROP_PAUSE;
            state = State.PAUSE;
        }
    }

    /** AIZMiniboss_FallingShot_Wait while the pause callback is armed. */
    private void updatePause() {
        if (--timer < 0) {
            enterDrop();
        }
    }

    /** AIZMiniboss_FallingShot_Drop -> FallingShot_StartFall callback. */
    private void enterDrop() {
        state = State.DROP_WAIT;
        timer = fallingShotDelay();
        vFlip = true;
        yVel = 0x400;

        // SetFallingShotDelay falls through SetShotPosition in the ROM.  The
        // barrel counter is incremented by four before selecting the slot.
        positionCounter = (positionCounter + 4) & 0xFF;
        int slotIndex = ((barrelSubtype >> 1) + (positionCounter & 0x0C)) & 0x0F;
        int[] slotTable = hFlip ? SHOT_SLOTS_LEFT : SHOT_SLOTS_RIGHT;
        int[] offsetTable = hFlip ? SHOT_X_OFFSETS_LEFT : SHOT_X_OFFSETS_RIGHT;
        currentX = services().camera().getX() + offsetTable[slotTable[slotIndex]];
        currentY = services().camera().getY() - 0x20;
        xFixed = currentX << 16;
        yFixed = currentY << 16;
        services().playSfx(Sonic3kSfx.MISSILE_THROW.id);
    }

    /** Obj_Wait after SetFallingShotDelay. */
    private void updateDropWait() {
        if (--timer < 0) {
            // FallingShot_StartFall changes priority/callback.  The first
            // MoveSprite2 call is on the following routine-8 dispatch.
            state = State.FALL;
        }
    }

    /** AIZMiniboss_FallingShot_Fall: Animate_Raw, MoveSprite2, ObjHitFloor_DoRoutine. */
    private void updateFall() {
        animateBarrelShot();
        moveSprite2();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        if (floor.hasCollision()) {
            // ObjHitFloor_DoRoutine adds d1 to the integer y_pos.  Keep the
            // native subpixel remainder while applying that word adjustment.
            yFixed += floor.distance() << 16;
            currentY = yFixed >> 16;
            explode();
        }
    }

    private int fallingShotDelay() {
        int index = barrelSubtype >> 1;
        return index >= 0 && index < FALLING_SHOT_DELAYS.length
                ? FALLING_SHOT_DELAYS[index]
                : 0;
    }

    private void explode() {
        services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
        for (int i = 0; i < AizMinibossNapalmExplosionChild.X_OFFSETS.length; i++) {
            int x = currentX + AizMinibossNapalmExplosionChild.X_OFFSETS[i];
            int y = currentY + AizMinibossNapalmExplosionChild.Y_OFFSETS[i];
            int subtype = i * 2; // CreateChild1_Normal's sequential subtype word index.
            spawnChild(() -> new AizMinibossNapalmExplosionChild(x, y, subtype, true));
        }
        setDestroyed(true);
    }

    private void moveSprite2() {
        yFixed += yVel << 8;
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;
    }

    /** Animate_Raw for AniRaw_AIZMiniboss_BarrelShot's looping C/D prefix. */
    private void animateBarrelShot() {
        if (--animTimer > 0) {
            return;
        }
        animTimer = 2;
        mappingFrame = mappingFrame == FRAME_RISE_A ? FRAME_RISE_B : FRAME_RISE_A;
    }

    @Override
    public int getCollisionFlags() {
        // FallingShot_Init retains ObjDat_AIZMiniboss_BarrelShot's $98 for the
        // live route.  The cutscene parent clears it in a separate native branch;
        // this independent Knuckles controller only creates the live variant.
        return isDestroyed() ? 0 : COLLISION_FLAGS_HAZARD;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getShieldReactionFlags() {
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
    public boolean usesCurrentTouchResponseState() {
        // loc_68C96 runs Add_SpriteToCollisionResponseList after its movement
        // routine, so the following player slot reads this post-move position.
        return true;
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
        // make_art_tile(ArtTile_AIZMiniboss,0,0): priority bit is clear.
        return false;
    }

    @Override
    public int getPriorityBucket() {
        // ObjDat priority $280, then FallingShot_StartFall priority $80.
        return state == State.FALL ? 1 : 5;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, hFlip, vFlip, PROJECTILE_PALETTE);
    }
}
