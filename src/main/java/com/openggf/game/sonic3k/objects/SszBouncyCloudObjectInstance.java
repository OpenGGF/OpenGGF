package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code Obj_SSZBouncyCloud} ({@code $7D}, sonic3k.asm:90521-90720): the small cloud that
 * squashes under a standing player, throws them up and back, and sheds four puffs. Twenty-seven
 * act-1 placements, all subtype 0 — the subtype is never read.
 *
 * <p>Init: {@code render_flags 4}, {@code height_pixels $10}, {@code width_pixels $20},
 * {@code priority $80}, {@code make_art_tile(ArtTile_SSZMisc+$102,3,1)} over
 * {@code Map_SSZBouncyCloud}, {@code clr.b $2D(a0)}, {@code y_vel(a0) = y_pos(a0)} as the drift's
 * base, and {@code $30(a0) = (Random_Number & $FFF) + $C00}. As with the collapsing column,
 * {@code $30} is the low word of the {@code Gradual_SwingOffset} speed longword at {@code $2E},
 * so every cloud draws once from the shared RNG at init and starts its drift out of phase.
 *
 * <p>Per frame {@code loc_450EC} adds a {@code Gradual_SwingOffset($1C00,$80)} offset to the base
 * Y and then one of two per-player terms:
 * <ul>
 *   <li>if either player's sag byte {@code 6(a2)} is non-zero, the <em>unsigned</em> larger of the
 *       two, sign-extended — so a sag of {@code $F8} beats one of {@code $12} and then applies as
 *       {@code -8};</li>
 *   <li>otherwise the recoil byte {@code 7(a2)} belonging to whichever player has the larger
 *       countdown index {@code 1(a2)}.</li>
 * </ul>
 *
 * <p>{@code routine(a0)} is not a routine here: it is a shared bounce counter. The first player to
 * land sets it to 7 and it decrements once per frame; a second player landing while it runs takes
 * the current value, so two players who land a few frames apart leave together.
 *
 * <p>{@code sub_45170} per player: landing stashes {@code x_vel} and {@code ground_vel} in
 * {@code 2(a2)}/{@code 4(a2)}, zeroes both, sets {@code anim 1} through the {@code $2D} latch and
 * writes {@code byte_4669F} ({@code $A}) as the sag. Each following frame decrements the counter
 * and reads {@code byte_46698} — {@code $12,$16,$17,$16,$12,$A} — so the cloud dips and comes back
 * before it fires. At zero {@code loc_451DC} restores the stashed speeds, sets {@code y_vel -$700}
 * and {@code angle} to {@code -$1C} or {@code +$1C} by facing, and allocates the four
 * {@code word_466C8} puffs. {@code loc_4527A} then spins the angle back to zero — 6 per frame
 * while the byte is on the far side of the sign boundary and 2 after it crosses, 80 frames either
 * way — while {@code byte_466A0} feeds the cloud a damped recoil from index {@code $26} down.
 */
public final class SszBouncyCloudObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private static final Logger LOGGER =
            Logger.getLogger(SszBouncyCloudObjectInstance.class.getName());

    /** {@code move.w #$80,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);
    /** {@code make_art_tile(ArtTile_SSZMisc+$102,3,1)}. */
    private static final int PALETTE_LINE = 3;
    /** {@code moveq #$20,d1} / {@code moveq #$10,d2} / {@code moveq #0,d3}. */
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x20, 0x10, 0x00);
    /** {@code move.l #$1C00,d0} / {@code move.l #$80,d1}. */
    private static final int SWING_SPEED = 0x1C00;
    private static final int SWING_ACCELERATION = 0x80;
    /** {@code andi.w #$FFF,d0} / {@code addi.w #$C00,d0}. */
    private static final int PHASE_MASK = 0x0FFF;
    private static final int PHASE_BIAS = 0x0C00;
    /** {@code moveq #7,d0}: the shared bounce countdown. */
    public static final int BOUNCE_FRAMES = 7;
    /** {@code move.w #-$700,y_vel(a1)}. */
    public static final int LAUNCH_Y_VEL = -0x700;
    /** {@code moveq #-$1C,d0} and its {@code neg.w}. */
    public static final int LAUNCH_ANGLE = 0x1C;
    /** {@code move.b #$26,1(a2)}. */
    public static final int RECOIL_START_INDEX = 0x26;
    /** {@code word_466C8}: four rows of X offset, Y offset, X velocity, Y velocity. */
    public static final int PUFF_TABLE_ADDR = Sonic3kConstants.SSZ_BOUNCY_CLOUD_PUFF_TABLE_ADDR;
    public static final int PUFF_COUNT = 4;
    /** {@code byte_46698}, with {@code byte_4669F} as its eighth byte. */
    public static final int SAG_TABLE_ADDR = Sonic3kConstants.SSZ_BOUNCY_CLOUD_SAG_TABLE_ADDR;
    /** {@code byte_466A0}. */
    public static final int RECOIL_TABLE_ADDR = Sonic3kConstants.SSZ_BOUNCY_CLOUD_RECOIL_TABLE_ADDR;
    /** {@code Ani_SSZBouncyCloud} anim 0: {@code $B, 3,4,5,6,7, $FF}. */
    private static final int DRIFT_DELAY = 0x0B;
    private static final int[] DRIFT_FRAMES = {3, 4, 5, 6, 7};
    /** Anim 1: {@code $FF, 0, $FF} — frame 0 held for 256 frames at a time. */
    private static final int SQUASH_FRAME = 0;

    /** {@code y_vel(a0)}: the placement Y the drift is measured from. */
    private final int baseY;
    private final int x;
    private int y;
    /** {@code $2E}/{@code $32}/{@code $36}: the {@code Gradual_SwingOffset} longwords and flag. */
    private int swingSpeed;
    private int swingOffset;
    private boolean swingReversed;
    private boolean phaseSeeded;
    /** {@code routine(a0)}: the shared bounce countdown, not a routine index. */
    private int bounceCounter;
    /** {@code $2D(a0)} (flip_type), which {@code loc_45142} copies into {@code anim} each frame. */
    private int animLatch;

    /** {@code $38(a0)} and {@code $40(a0)}: the two eight-byte per-player blocks. */
    private int p1State;
    private int p1RecoilIndex;
    private int p1SavedXVel;
    private int p1SavedGroundVel;
    private int p1Sag;
    private int p1Recoil;
    private boolean p1LaunchFacingLeft;
    private int p2State;
    private int p2RecoilIndex;
    private int p2SavedXVel;
    private int p2SavedGroundVel;
    private int p2Sag;
    private int p2Recoil;
    private boolean p2LaunchFacingLeft;

    /** {@code anim_frame}/{@code anim_frame_timer} for {@code Animate_Sprite}. */
    private int animFrame;
    private int animTimer;
    private int previousAnim = -1;
    private int mappingFrame = DRIFT_FRAMES[0];

    public SszBouncyCloudObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZBouncyCloud");
        this.x = spawn.x();
        this.baseY = spawn.y();
        this.y = spawn.y();
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        // sub_45170 calls SolidObjectTop_1P itself, after loc_450EC has written this frame's y_pos.
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        seedPhase();
        // loc_450EC: swing offset, then the sag or recoil term, then y_pos.
        int offset = gradualSwingOffset();
        y = (baseY + offset + verticalPlayerTerm()) & 0xFFFF;
        // tst.b routine(a0) / subq.b #1,routine(a0).
        if (bounceCounter != 0) {
            bounceCounter--;
        }
        // move.b $2D(a0),anim(a0): the latch decides this frame's animation before either
        // sub_45170 call can set it again.
        int anim = animLatch;
        checkpointAll();
        anim = servicePlayer(0, player, anim);
        // lea (Player_2).w,a1: the routine's second pass is the native Player 2 slot.
        PlayableEntity second = services().playerQuery().nativeP2OrNull();
        anim = servicePlayer(1, second, anim);
        animate(anim);
    }

    /**
     * {@code loc_450EC}'s two-branch pick between the sag bytes and the recoil bytes. The sag
     * comparison is unsigned ({@code cmp.b} / {@code bhs.s}) and only then sign-extended, and the
     * recoil comparison is on the countdown indices rather than the values.
     */
    private int verticalPlayerTerm() {
        int sagOne = p1Sag & 0xFF;
        int sagTwo = p2Sag & 0xFF;
        if ((sagOne | sagTwo) != 0) {
            return (byte) (Integer.compareUnsigned(sagOne, sagTwo) >= 0 ? sagOne : sagTwo);
        }
        int indexOne = p1RecoilIndex & 0xFF;
        int indexTwo = p2RecoilIndex & 0xFF;
        return (byte) (Integer.compareUnsigned(indexOne, indexTwo) >= 0 ? p1Recoil : p2Recoil);
    }

    /** One {@code sub_45170} call. Returns the animation id this frame ends with. */
    private int servicePlayer(int slot, PlayableEntity entity, int anim) {
        if (!(entity instanceof AbstractPlayableSprite sprite)) {
            return anim;
        }
        int state = slot == 0 ? p1State : p2State;
        if (state == 0) {
            if (!isRiding(sprite)) {
                return anim;
            }
            // btst d6,status(a0) set: the player has just been given the top of the cloud.
            store(slot, sprite.getXSpeed(), sprite.getGSpeed());
            sprite.setXSpeed((short) 0);
            sprite.setGSpeed((short) 0);
            animLatch = 1;
            int counter = bounceCounter;
            if (counter == 0) {
                counter = BOUNCE_FRAMES;
                bounceCounter = BOUNCE_FRAMES;
            }
            setState(slot, counter);
            setSag(slot, sagByte(BOUNCE_FRAMES));
            return 1;
        }
        if (state < 0) {
            settleLaunchedPlayer(slot, sprite);
            return anim;
        }
        // loc_451C2: subq.b #1,(a2).
        state--;
        setState(slot, state);
        if (state != 0) {
            setSag(slot, sagByte(state));
            return anim;
        }
        launch(slot, sprite);
        return anim;
    }

    /** {@code loc_451DC}. */
    private void launch(int slot, AbstractPlayableSprite sprite) {
        services().playSfx(Sonic3kSfx.BOUNCY.id);
        sprite.setXSpeed((short) savedXVel(slot));
        sprite.setGSpeed((short) savedGroundVel(slot));
        sprite.setYSpeed((short) LAUNCH_Y_VEL);
        sprite.setAir(true);
        sprite.setOnObject(false);
        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.releaseRidingObject(sprite, this);
        }
        sprite.setJumping(false);
        sprite.setSpindash(false);
        // move.b #2,routine(a1) / clr.b anim(a1): the player returns to its normal routine with
        // the walk/run script, whatever the cloud interrupted.
        sprite.setAnimationId(Sonic3kAnimationIds.WALK);
        boolean facingLeft = sprite.getDirection() == Direction.LEFT;
        // st (a2) then bclr #0,(a2) when facing right: bit 0 of the state byte is the facing the
        // spin-down reads, so it survives the player turning in mid-air.
        setLaunchFacingLeft(slot, facingLeft);
        sprite.setAngle((byte) (facingLeft ? -LAUNCH_ANGLE : LAUNCH_ANGLE));
        setState(slot, -1);
        animLatch = 0;
        setSag(slot, 0);
        setRecoilIndex(slot, RECOIL_START_INDEX);
        spawnPuffs(sprite);
    }

    /** {@code loc_45226}'s four {@code word_466C8} allocations. */
    private void spawnPuffs(AbstractPlayableSprite sprite) {
        int playerX = sprite.getCentreX() & 0xFFFF;
        for (int index = 0; index < PUFF_COUNT; index++) {
            int[] row = puffRow(index);
            if (row == null) {
                return;
            }
            int puffX = (playerX + row[0]) & 0xFFFF;
            int puffY = (y + row[1]) & 0xFFFF;
            int velX = row[2];
            int velY = row[3];
            if (spawnChild(() -> new SszBouncyCloudPuffObjectInstance(
                    new ObjectSpawn(puffX, puffY, 0, 0, 0, false, 0), velX, velY, x)) == null) {
                return;
            }
        }
    }

    /** {@code loc_4527A}: the post-launch angle spin-down and recoil feed. */
    private void settleLaunchedPlayer(int slot, AbstractPlayableSprite sprite) {
        // tst.b $2E(a1) / btst #1,status(a1) / andi.w #$28,d0: another object taking control, a
        // landing, or a push all end the spin immediately.
        if (sprite.isObjectControlled() || !sprite.getAir()
                || sprite.isOnObject() || sprite.getPushing()) {
            clearSlot(slot);
            return;
        }
        int index = recoilIndex(slot) & 0xFF;
        setRecoil(slot, recoilByte(index));
        if (index - 1 >= 0) {
            setRecoilIndex(slot, index - 1);
        }
        int angle = sprite.getAngle() & 0xFF;
        int next;
        if (!launchFacingLeft(slot)) {
            // d1 = $60002: 2 while the byte reads negative, 6 while it reads positive.
            next = angle + ((angle & 0x80) != 0 ? 2 : 6);
            if (next > 0xFF) {
                next = 0;
            }
        } else {
            next = angle - ((angle & 0x80) == 0 ? 2 : 6);
            if (next < 0) {
                next = 0;
            }
        }
        sprite.setAngle((byte) next);
        if (next == 0) {
            clearSlot(slot);
        }
    }

    private void clearSlot(int slot) {
        setState(slot, 0);
    }

    private boolean isRiding(AbstractPlayableSprite sprite) {
        var objectManager = services().objectManager();
        return objectManager != null && objectManager.isRidingObject(sprite, this);
    }

    /**
     * {@code jsr (Random_Number)} in the init, written to {@code $30(a0)}. Deferred to the first
     * frame because the shared RNG lives behind {@code services()}.
     */
    private void seedPhase() {
        if (phaseSeeded) {
            return;
        }
        phaseSeeded = true;
        var rng = services().rng();
        int draw = rng == null ? 0 : rng.nextWord() & 0xFFFF;
        swingSpeed = ((draw & PHASE_MASK) + PHASE_BIAS) & 0xFFFF;
    }

    /** {@code Gradual_SwingOffset} (sonic3k.asm:92484-92515); returns the offset's high word. */
    private int gradualSwingOffset() {
        int step = SWING_ACCELERATION;
        if (swingReversed) {
            step = -step;
            swingOffset += swingSpeed;
            if (swingOffset < 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = SWING_SPEED;
                swingOffset = 0;
                swingReversed = false;
            }
        } else {
            swingOffset += swingSpeed;
            if (swingOffset > 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = -SWING_SPEED;
                swingOffset = 0;
                swingReversed = true;
            }
        }
        return (short) (swingOffset >> 16);
    }

    /** {@code Animate_Sprite} over {@code Ani_SSZBouncyCloud} anims 0 and 1. */
    private void animate(int anim) {
        if (anim != previousAnim) {
            previousAnim = anim;
            animFrame = 0;
            animTimer = 0;
        }
        if (--animTimer >= 0) {
            return;
        }
        if (anim == 1) {
            animTimer = 0xFF;
            mappingFrame = SQUASH_FRAME;
            return;
        }
        animTimer = DRIFT_DELAY;
        mappingFrame = DRIFT_FRAMES[animFrame % DRIFT_FRAMES.length];
        animFrame = (animFrame + 1) % DRIFT_FRAMES.length;
    }

    /** {@code byte_46698}, read live from the ROM; index 7 is {@code byte_4669F}. */
    public int sagByte(int index) {
        return romByte(SAG_TABLE_ADDR + index);
    }

    /** {@code byte_466A0}. */
    public int recoilByte(int index) {
        return romByte(RECOIL_TABLE_ADDR + index);
    }

    private int romByte(int address) {
        try {
            var rom = services().rom();
            return rom == null ? 0 : rom.readBytes(address, 1)[0] & 0xFF;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSZ bouncy cloud table byte at $"
                    + Integer.toHexString(address) + " unavailable", e);
            return 0;
        }
    }

    /** One {@code word_466C8} row: X offset, Y offset, X velocity, Y velocity, all signed. */
    private int[] puffRow(int index) {
        try {
            var rom = services().rom();
            if (rom == null) {
                return null;
            }
            byte[] bytes = rom.readBytes(PUFF_TABLE_ADDR + index * 8, 8);
            return new int[]{word(bytes, 0), word(bytes, 2), word(bytes, 4), word(bytes, 6)};
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSZ bouncy cloud puff row " + index + " unavailable", e);
            return null;
        }
    }

    private static int word(byte[] data, int offset) {
        return (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    private void store(int slot, int xVel, int groundVel) {
        if (slot == 0) {
            p1SavedXVel = xVel;
            p1SavedGroundVel = groundVel;
        } else {
            p2SavedXVel = xVel;
            p2SavedGroundVel = groundVel;
        }
    }

    private int savedXVel(int slot) { return slot == 0 ? p1SavedXVel : p2SavedXVel; }

    private int savedGroundVel(int slot) {
        return slot == 0 ? p1SavedGroundVel : p2SavedGroundVel;
    }

    private void setState(int slot, int value) {
        if (slot == 0) {
            p1State = value;
        } else {
            p2State = value;
        }
    }

    private void setSag(int slot, int value) {
        if (slot == 0) {
            p1Sag = value;
        } else {
            p2Sag = value;
        }
    }

    private int recoilIndex(int slot) { return slot == 0 ? p1RecoilIndex : p2RecoilIndex; }

    private void setRecoilIndex(int slot, int value) {
        if (slot == 0) {
            p1RecoilIndex = value;
        } else {
            p2RecoilIndex = value;
        }
    }

    private void setRecoil(int slot, int value) {
        if (slot == 0) {
            p1Recoil = value;
        } else {
            p2Recoil = value;
        }
    }

    private boolean launchFacingLeft(int slot) {
        return slot == 0 ? p1LaunchFacingLeft : p2LaunchFacingLeft;
    }

    private void setLaunchFacingLeft(int slot, boolean value) {
        if (slot == 0) {
            p1LaunchFacingLeft = value;
        } else {
            p2LaunchFacingLeft = value;
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    public int bounceCounterForTest() { return bounceCounter; }
    public int stateForTest(int slot) { return slot == 0 ? p1State : p2State; }
    public int sagForTest(int slot) { return slot == 0 ? p1Sag : p2Sag; }
    public int recoilIndexForTest(int slot) { return slot == 0 ? p1RecoilIndex : p2RecoilIndex; }
    int mappingFrameForTest() { return mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_BOUNCY_CLOUD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false, PALETTE_LINE);
        }
    }
}
