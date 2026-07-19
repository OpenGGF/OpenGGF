package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0xE7 - Pachinko flipper.
 *
 * <p>ROM reference: {@code Obj_PachinkoFlipper}. This is a sloped top-solid flipper
 * that locks the player into rolling while standing on it, accelerates them along
 * the surface, and launches them when jump is pressed.
 */
public class PachinkoFlipperObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener, SpawnRewindRecreatable {

    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x20, 0x1C, 0x1D);

    // ROM: byte_49E5A
    private static final byte[] SLOPE_DATA = {
            -4, -4, -4, -4, -4, -4, -4, -4,
            -4, -5, -6, -7, -8, -9, -10, -11,
            -12, -13, -14, -15, -16, -17, -18, -19,
            -20, -21, -22, -23, -24, -25, -26, -27
    };

    // ROM idle anim frame: byte_49E7E = delay 0x1F, frame 4.
    private static final int IDLE_FRAME = 4;

    // ROM trigger anim: byte_49E81 = 3,2,1,0,1,2,3 with 1-frame delay.
    private static final int[] TRIGGER_SEQUENCE = {3, 2, 1, 0, 1, 2, 3};

    private static final int SURFACE_ACCEL = 0x18;
    private static final int SURFACE_ACCEL_FLIPPED = -0x19; // ROM uses NOT.W on 0x18, yielding -25.
    private AbstractPlayableSprite lockedPlayer;
    private boolean contactThisFrame;
    private int triggerFrame = -1;

    public PachinkoFlipperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoFlipper");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Self-contained: rebuilds from the captured spawn. Scalar fields are reapplied
     * by the standard scalar-restore pass after recreate; the locked-player back-reference
     * is not wired here (it was not captured by the deleted explicit restore path either).
     * Replaces the former explicit dynamic restore path (Phase-2 codec-deletion batch 2).
     */

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public byte[] getSlopeData() {
        return SLOPE_DATA;
    }

    @Override
    public boolean isSlopeFlipped() {
        return isFlippedHorizontal();
    }

    /**
     * {@inheritDoc}
     *
     * <p>ROM {@code SolidObjCheckSloped2}/{@code SolidObjSloped2} (sonic3k.asm:42076-42097,
     * 41732-41830), reached via {@code SolidObjectTopSloped2} (sonic3k.asm:41831-41872) as
     * called from {@code Obj_PachinkoFlipper}'s {@code loc_49C8A} (sonic3k.asm:96384
     * {@code jsr (SolidObjectTopSloped2).l}), samples the slope table byte and subtracts it
     * from {@code y_pos(a0)} directly: {@code move.w y_pos(a0),d0 / sub.w d3,d0} with no
     * baseline term. The engine's default {@link SlopedSolidProvider#getSlopeBaseline()}
     * subtracts {@code slopeData[0]} (here {@code -4}) as a relative baseline, which is only
     * correct for objects whose ROM routine itself normalizes against the table's first
     * entry. For the flipper this introduced a spurious 4px offset that raised its effective
     * collision surface, causing a false-positive landing ~1 frame before the ROM player
     * actually reached the surface (trace f427: expected air=1/status=0x07 continuing to
     * fall past the flipper, engine snapped to a landing with y_speed=0, status=0x0C).
     * Returning 0 here makes the engine use the raw (absolute) slope sample, matching
     * {@code SolidObjCheckSloped2}'s literal subtraction.
     */
    @Override
    public int getSlopeBaseline() {
        return 0;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (!contact.standing()) {
            return;
        }

        contactThisFrame = true;

        if (player.isDebugMode()) {
            releaseLockedPlayer();
            return;
        }

        boolean newlyLocked = lockedPlayer != player;
        lockPlayer(player);
        if (newlyLocked) {
            // ROM sub_49CFE (sonic3k.asm:96416-96434): on a NEW lock (a3 byte was
            // 0), the routine locks control, sets roll radii/Status_Roll, and
            // conditionally lifts y_pos, then returns via locret_49D3A WITHOUT
            // ever touching ground_vel(a1). Acceleration (loc_49D54,
            // sonic3k.asm:96449-96457) is only reachable through the ALREADY-locked
            // branch (loc_49D3C, taken when (a3)!=0). Applying acceleration on the
            // same frame as the initial lock double-counted the landing's
            // ground_vel=x_vel carry-over (trace f430: expected g_speed=-0x18,
            // engine produced 0x0000 by adding +0x18 on top of the -0x18 already
            // set by the landing).
            return;
        }
        if (player.isJumpJustPressed()) {
            launchPlayer(player);
        } else {
            applySurfaceAcceleration(player);
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (lockedPlayer != null && (!contactThisFrame || lockedPlayer.getAir() || lockedPlayer.isDebugMode())) {
            releaseLockedPlayer();
        }
        contactThisFrame = false;

        if (triggerFrame >= 0) {
            triggerFrame++;
            if (triggerFrame >= TRIGGER_SEQUENCE.length) {
                triggerFrame = -1;
            }
        }
    }

    private void lockPlayer(AbstractPlayableSprite player) {
        lockedPlayer = player;
        player.setControlLocked(true);
        player.setPinballMode(true);
        if (!player.getRolling()) {
            player.setRolling(true);
            player.applyRollingRadii(false);
            player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
        }
    }

    private void applySurfaceAcceleration(AbstractPlayableSprite player) {
        int accel = isFlippedHorizontal() ? SURFACE_ACCEL_FLIPPED : SURFACE_ACCEL;
        player.setGSpeed((short) (player.getGSpeed() + accel));
        player.setDirection(accel < 0 ? Direction.LEFT : Direction.RIGHT);
    }

    private void launchPlayer(AbstractPlayableSprite player) {
        int launchDistance = player.getCentreX() - spawn.x();
        if (isFlippedHorizontal()) {
            launchDistance = -launchDistance;
        }

        launchDistance += 0x20;
        int velocityMagnitude = -((launchDistance << 5) + 0x800);
        int angle = ((launchDistance >> 2) + 0x40) & 0xFF;

        int sinValue = TrigLookupTable.sinHex(angle);
        int cosValue = TrigLookupTable.cosHex(angle);
        int yVelocity = (sinValue * velocityMagnitude) >> 8;
        int xVelocity = (cosValue * velocityMagnitude) >> 8;
        if (isFlippedHorizontal()) {
            xVelocity = -xVelocity;
        }

        player.setYSpeed((short) yVelocity);
        player.setXSpeed((short) xVelocity);
        player.setAir(true);
        player.setOnObject(false);
        player.setPushing(false);
        player.setGSpeed((short) 0);
        player.setControlLocked(false);
        player.setPinballMode(false);
        player.setDirection(xVelocity < 0 ? Direction.LEFT : Direction.RIGHT);

        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }

        lockedPlayer = null;
        triggerFrame = 0;
        playFlipperSfx();
    }

    private void releaseLockedPlayer() {
        if (lockedPlayer == null) {
            return;
        }
        lockedPlayer.setControlLocked(false);
        lockedPlayer.setPinballMode(false);
        lockedPlayer = null;
    }

    private void playFlipperSfx() {
        try {
            services().playSfx(Sonic3kSfx.FLIPPER.id);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_FLIPPER);
        if (renderer == null) {
            return;
        }
        boolean hFlip = isFlippedHorizontal();
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        int frame = triggerFrame >= 0 ? TRIGGER_SEQUENCE[triggerFrame] : IDLE_FRAME;
        renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }
}
