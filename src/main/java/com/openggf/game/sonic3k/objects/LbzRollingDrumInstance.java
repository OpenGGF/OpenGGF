package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GroundMode;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Invisible Launch Base rolling cylinder controller.
 *
 * <p>ROM reference: {@code Obj_LBZRollingDrum}, {@code sub_2C3E8}
 * (sonic3k.asm:60585-60726). The subtype byte is the cylinder half-width;
 * {@code _unkF7B0+0/+1} store native P1/P2 angle bytes while riding.
 */
public final class LbzRollingDrumInstance extends AbstractObjectInstance {
    private static final int TOP_BOTTOM_Y_BIAS = 0x53;
    private static final int VERTICAL_RANGE = 0xA6;
    private static final int LOWER_HALF_Y = 0x53;
    private static final int TOP_ANGLE_SEED_THRESHOLD = 8;
    private static final int BOTTOM_ANGLE_SEED_THRESHOLD = 0x9E;
    private static final int TOP_ANGLE_SEED = 0x81;
    private static final int BOTTOM_ANGLE_SEED = 0x01;
    private static final int MAX_BOTTOM_ENTRY_Y_SPEED = 0x38;
    private static final int RELEASE_FALL_Y_SPEED = 0x0400;
    private static final int FLIP_TYPE_ACTIVE = 0x80;
    private static final int FLIP_TYPE_ACTIVE_FROM_REST = 0x81;
    private static final int FLIP_SPEED_RELEASE = 4;
    private static final int RIDE_ANGLE_STEP = 2;
    private static final int TUMBLE_DIVISOR = 0x16;
    private static final int TUMBLE_BASE = 0x31;
    private static final int TUMBLE_FROM_REST_BASE = 0x3D;
    private static final int ANIMATION_ROLLING_DRUM = Sonic3kAnimationIds.WALK.id();

    private final int leftBound;
    private final int rightBound;
    private boolean p1Riding;
    private int fallbackP1Angle;
    private boolean p2Riding;
    private int fallbackP2Angle;

    public LbzRollingDrumInstance(ObjectSpawn spawn) {
        super(spawn, "LBZRollingDrum");
        int halfWidth = spawn.subtype() & 0xFF;
        this.leftBound = -halfWidth;
        this.rightBound = halfWidth;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        int playerIndex = 0;
        for (PlayableEntity candidate : playersToProcess(playerEntity)) {
            if (candidate instanceof AbstractPlayableSprite player) {
                updatePlayer(player, playerIndex);
            }
            playerIndex++;
            if (playerIndex >= 2) {
                break;
            }
        }
    }

    @Override
    public int getOnScreenHalfWidth() {
        // ROM init writes width_pixels=$80 before Delete_Sprite_If_Not_In_Range.
        return 0x80;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible controller; the cylinder art is level terrain.
    }

    public boolean isRidingForTest(AbstractPlayableSprite player) {
        return isNativeRidingForTest(0);
    }

    public int getRideAngleForTest(AbstractPlayableSprite player) {
        return getNativeRideAngleForTest(0);
    }

    public boolean isNativeRidingForTest(int nativePlayerIndex) {
        return nativePlayerIndex == 0 ? p1Riding : p2Riding;
    }

    public int getNativeRideAngleForTest(int nativePlayerIndex) {
        boolean riding = isNativeRidingForTest(nativePlayerIndex);
        if (!riding) {
            return -1;
        }
        return getAngle(nativePlayerIndex);
    }

    private List<PlayableEntity> playersToProcess(PlayableEntity fallbackPlayer) {
        try {
            List<PlayableEntity> players = services().playerQuery()
                    .playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
            if (!players.isEmpty()) {
                return players;
            }
        } catch (IllegalStateException ignored) {
            // Direct object tests can instantiate without ObjectServices.
        }
        return fallbackPlayer == null ? List.of() : List.of(fallbackPlayer);
    }

    private void updatePlayer(AbstractPlayableSprite player, int nativePlayerIndex) {
        if (isRiding(nativePlayerIndex)) {
            updateActiveRide(player, nativePlayerIndex);
            return;
        }
        tryEnterRide(player, nativePlayerIndex);
    }

    private void tryEnterRide(AbstractPlayableSprite player, int nativePlayerIndex) {
        int dx = signedWordDelta(player.getCentreX(), spawn.x());
        if (dx < leftBound || dx >= rightBound) {
            return;
        }

        int verticalDelta = signedWordDelta(player.getCentreY(), spawn.y()) + TOP_BOTTOM_Y_BIAS;
        if (verticalDelta < 0 || verticalDelta >= VERTICAL_RANGE) {
            return;
        }
        if (verticalDelta < LOWER_HALF_Y && player.getYSpeed() < 0) {
            return;
        }
        if (verticalDelta >= LOWER_HALF_Y && player.getYSpeed() > MAX_BOTTOM_ENTRY_Y_SPEED) {
            return;
        }
        if (player.getDead() || player.isDebugMode()) {
            return;
        }

        if (verticalDelta < TOP_ANGLE_SEED_THRESHOLD) {
            setAngle(nativePlayerIndex, TOP_ANGLE_SEED);
        } else if (verticalDelta >= BOTTOM_ANGLE_SEED_THRESHOLD) {
            setAngle(nativePlayerIndex, BOTTOM_ANGLE_SEED);
        }
        setRiding(nativePlayerIndex, true);
        applyRideObjectSetRide(player);
        player.setFlipType(FLIP_TYPE_ACTIVE);
        player.setAnimationId(ANIMATION_ROLLING_DRUM);
        player.forceAnimationRestart();
        if (player.getGSpeed() == 0) {
            player.setGSpeed((short) 1);
        }
        applyRideAnimationState(player);
    }

    private void updateActiveRide(AbstractPlayableSprite player, int nativePlayerIndex) {
        int dx = signedWordDelta(player.getCentreX(), spawn.x());
        boolean insideHorizontalWindow = dx >= leftBound && dx < rightBound;
        if (player.getAir()) {
            if (insideHorizontalWindow && !player.isJumping() && !player.isHurt()) {
                player.setAir(false);
                refreshRideLatch(player);
            } else {
                int dy = signedWordDelta(player.getCentreY(), spawn.y());
                if (dy >= 0) {
                    player.setYSpeed((short) RELEASE_FALL_Y_SPEED);
                }
                release(player, nativePlayerIndex);
                return;
            }
        }

        if (!insideHorizontalWindow) {
            release(player, nativePlayerIndex);
            return;
        }
        if (!player.isOnObject()) {
            if (!player.isJumping() && !player.isHurt()) {
                refreshRideLatch(player);
            } else {
                return;
            }
        }

        int angle = getAngle(nativePlayerIndex) & 0xFF;
        int cos = TrigLookupTable.cosHex(angle);
        int radius = ((player.getYRadius() & 0xFFFF) << 8) + 0x4000;
        int y = spawn.y() + ((cos * radius) >> 16);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        player.setFlipAngle((angle + 0x80) & 0xFF);
        setAngle(nativePlayerIndex, (angle + RIDE_ANGLE_STEP) & 0xFF);
        player.setFlipType(FLIP_TYPE_ACTIVE);
        if (player.getGSpeed() == 0) {
            player.setGSpeed((short) 1);
            player.setFlipType(FLIP_TYPE_ACTIVE_FROM_REST);
        }
        player.setHighPriority(((byte) player.getFlipAngle()) >= 0);
        applyRideAnimationState(player);
        refreshRideLatch(player);
    }

    private void release(AbstractPlayableSprite player, int nativePlayerIndex) {
        setRiding(nativePlayerIndex, false);
        player.setOnObject(false);
        player.setLatchedSolidObjectId(0);
        player.setFlipsRemaining(0);
        player.setFlipSpeed(FLIP_SPEED_RELEASE);
        player.setAir(true);
        player.setObjectMappingFrameControl(false);
        player.setForcedAnimationId(-1);
    }

    private void applyRideObjectSetRide(AbstractPlayableSprite player) {
        int savedDoubleJumpFlag = player.getDoubleJumpFlag();
        boolean wasAir = player.getAir();
        player.setAngle((byte) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed(player.getXSpeed());
        player.setOnObject(true);
        player.setLatchedSolidObject(Sonic3kObjectIds.LBZ_ROLLING_DRUM, this);
        if (wasAir) {
            applyPlayerTouchFloor(player, savedDoubleJumpFlag);
        }
    }

    private void applyPlayerTouchFloor(AbstractPlayableSprite player, int savedDoubleJumpFlag) {
        player.setAir(false);
        if (!player.getPinballMode()) {
            if (player.getRolling()) {
                player.setRolling(false);
                player.setY((short) (player.getY() - player.getRollHeightAdjustment()));
            } else if (player.getYRadius() != player.getStandYRadius()
                    || player.getXRadius() != player.getStandXRadius()) {
                player.restoreDefaultRadii();
            }
        }
        player.setGroundMode(GroundMode.GROUND);
        player.setPushing(false);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setFlipAngle(0);
        player.setFlipType(0);
        player.setFlipsRemaining(0);
        player.applyPostObjectLandingAbilities(savedDoubleJumpFlag);
    }

    private void refreshRideLatch(AbstractPlayableSprite player) {
        player.setOnObject(true);
        player.setLatchedSolidObject(Sonic3kObjectIds.LBZ_ROLLING_DRUM, this);
    }

    private void applyRideAnimationState(AbstractPlayableSprite player) {
        player.setAnimationId(ANIMATION_ROLLING_DRUM);
        player.setForcedAnimationId(-1);
        player.setObjectMappingFrameControl(true);
        applyRideRenderFlags(player);
        player.setMappingFrame(rollingDrumTumbleFrame(player.getFlipAngle(), player.getFlipType()));
        player.setAnimationTick(0);
    }

    private void applyRideRenderFlags(AbstractPlayableSprite player) {
        boolean facingLeft = Direction.LEFT.equals(player.getDirection());
        int type = player.getFlipType() & 0x7F;
        if (type == 1) {
            // ROM loc_12872/loc_128A8: flip_type=$81 uses only Status_Facing.
            player.setRenderFlips(facingLeft, false);
            return;
        }
        // ROM Anim_Tumble negative flip_type path in LBZ:
        // right-facing ORs render_flags bit 1; left-facing ORs bits 0 and 1.
        player.setRenderFlips(facingLeft, true);
    }

    private static int rollingDrumTumbleFrame(int flipAngle, int flipType) {
        int type = flipType & 0x7F;
        if (type == 1) {
            return (((flipAngle & 0xFF) - 8) & 0xFF) / TUMBLE_DIVISOR + TUMBLE_FROM_REST_BASE;
        }
        return ((0x8F - (flipAngle & 0xFF)) & 0xFF) / TUMBLE_DIVISOR + TUMBLE_BASE;
    }

    private static int signedWordDelta(int value, int origin) {
        return (short) (((value & 0xFFFF) - (origin & 0xFFFF)) & 0xFFFF);
    }

    private boolean isRiding(int nativePlayerIndex) {
        return nativePlayerIndex == 0 ? p1Riding : p2Riding;
    }

    private void setRiding(int nativePlayerIndex, boolean riding) {
        if (nativePlayerIndex == 0) {
            p1Riding = riding;
        } else {
            p2Riding = riding;
        }
    }

    private int getAngle(int nativePlayerIndex) {
        LbzZoneRuntimeState runtimeState = lbzRuntimeStateOrNull();
        if (runtimeState != null) {
            return runtimeState.getRollingDrumAngle(nativePlayerIndex);
        }
        return nativePlayerIndex == 0 ? fallbackP1Angle : fallbackP2Angle;
    }

    private void setAngle(int nativePlayerIndex, int angle) {
        LbzZoneRuntimeState runtimeState = lbzRuntimeStateOrNull();
        if (runtimeState != null) {
            runtimeState.setRollingDrumAngle(nativePlayerIndex, angle);
            return;
        }
        if (nativePlayerIndex == 0) {
            fallbackP1Angle = angle & 0xFF;
        } else {
            fallbackP2Angle = angle & 0xFF;
        }
    }

    private LbzZoneRuntimeState lbzRuntimeStateOrNull() {
        try {
            return S3kRuntimeStates.currentLbz(services().zoneRuntimeRegistry()).orElse(null);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }
}
