package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $C0 - Orbinaut (LBZ).
 *
 * <p>ROM reference: {@code Obj_Orbinaut} (sonic3k.asm:191626-191725). The
 * parent badnik tracks P1's side, but only advances when the shared ROM helper
 * returns a nonzero branch result. Four child orbs orbit as hurt-category
 * hazards.
 */
public final class OrbinautBadnikInstance extends AbstractS3kBadnikInstance {
    private static final int COLLISION_SIZE_INDEX = 0x0B; // ObjDat_Orbinaut collision_flags.
    private static final int PRIORITY_BUCKET = 5;         // ObjDat_Orbinaut priority $280.
    private static final int X_SPEED = 0x80;              // loc_8C662: move.w #-$80,d1.
    private static final int CHILD_COUNT = 4;

    private boolean initialized;

    public OrbinautBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Orbinaut",
                Sonic3kObjectArtKeys.ORBINAUT, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed() || !isOnScreenX()) {
            return;
        }

        if (!initialized) {
            spawnOrbitingOrbs();
            initialized = true;
            return;
        }

        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite
                ? sprite : null;
        updateFacingAndVelocity(player);
        if (canMoveThisFrame(player)) {
            moveWithVelocity();
        }
    }

    private void spawnOrbitingOrbs() {
        for (int i = 0; i < CHILD_COUNT; i++) {
            final int index = i;
            spawnChild(() -> new OrbinautOrbInstance(spawn, this, index));
        }
    }

    private void updateFacingAndVelocity(AbstractPlayableSprite player) {
        boolean playerOnRight = player != null && !player.getDead() && player.getCentreX() > currentX;
        facingLeft = !playerOnRight;
        xVelocity = playerOnRight ? X_SPEED : -X_SPEED;
    }

    /**
     * ROM sub_8C6D4 returns with the zero flag set for airborne or stationary
     * P1. A nonzero player x/y velocity leaves the zero flag clear, so the
     * caller advances in whichever direction loc_8C662 already selected.
     */
    private boolean canMoveThisFrame(AbstractPlayableSprite player) {
        if (player == null || player.getDead()) {
            return false;
        }
        return !player.getAir() && (player.getXSpeed() != 0 || player.getYSpeed() != 0);
    }

    boolean shouldRotateOrbs(AbstractPlayableSprite player) {
        return canMoveThisFrame(player);
    }

    boolean isFacingRight() {
        return !facingLeft;
    }

    static final class OrbinautOrbInstance extends AbstractObjectInstance implements TouchResponseProvider {
        private static final int COLLISION_FLAGS = 0x8B; // word_8C6FE collision_flags.
        private static final int PRIORITY_BUCKET = 5;
        private static final int ORBIT_RADIUS = 16;      // MoveSprite_CircularSimple with d2 = 4.
        private static final int ANGLE_STEP = 8;         // loc_8C6B0 adds +/-8.
        private static final int MAPPING_FRAME = 1;
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

        private final transient OrbinautBadnikInstance parent;
        private int currentX;
        private int currentY;
        private int angle;

        OrbinautOrbInstance(ObjectSpawn ownerSpawn, OrbinautBadnikInstance parent, int index) {
            super(ownerSpawn, "OrbinautOrb");
            this.parent = parent;
            this.angle = (index * 0x40) & 0xFF; // ChildObjDat_8C704 cardinal offsets.
            updateOrbitPosition();
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (isDestroyed() || parent.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite
                    ? sprite : null;
            if (parent.shouldRotateOrbs(player)) {
                angle = (angle + (parent.isFacingRight() ? -ANGLE_STEP : ANGLE_STEP)) & 0xFF;
            }
            updateOrbitPosition();
        }

        @Override
        public int getCollisionFlags() {
            return COLLISION_FLAGS;
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
        public boolean usesCurrentTouchResponseState() {
            // Obj_Orbinaut's child routine updates its circular position, then
            // calls Child_DrawTouch_Sprite, which adds the current SST pointer
            // to Collision_response_list before drawing (sonic3k.asm:
            // 191685-191688, 178048-178053, 21200-21207).
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
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.ORBINAUT);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(MAPPING_FRAME, currentX, currentY, false, false);
        }

        private void updateOrbitPosition() {
            int dx = (TrigLookupTable.sinHex(angle) * ORBIT_RADIUS) >> 8;
            int dy = (TrigLookupTable.cosHex(angle) * ORBIT_RADIUS) >> 8;
            currentX = parent.getX() + dx;
            currentY = parent.getY() + dy;
        }
    }
}
