package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.Tails;

import java.util.List;

import static com.openggf.game.sonic3k.objects.HpzKnucklesCutsceneSupport.*;

/**
 * ROM {@code loc_64C70}-{@code locret_64E86} (sonic3k.asm:132562-132747): the Player 1 script
 * {@code loc_64472} allocates for the Hidden Palace Master Emerald theft.
 *
 * <p>At X {@code $14D0} it stops and locks Player 1, locks Player 2 ({@code loc_863C0}) and sets
 * {@code Camera_target_max_Y_pos} to {@code $300}; once grounded it holds right to X {@code $15A8},
 * freezes the player in {@code object_control $83} (mapping {@code $C4}, Tails {@code $B0}),
 * locks the scroll, fades to {@code mus_Miniboss} and pans the camera 2 pixels a frame to
 * {@code $1580} ({@code _unkFAB8} bit 0). When Knuckles leaps at the ship (bit 1) it releases the
 * player holding right, moving the camera with the player's speed (at most {@code $300}) to
 * {@code $17D0}, where it fixes the camera and frees both controllers. When Knuckles lands (bit 5)
 * it walks Player 1 to X {@code $1892}, raises {@code _unkFAAC}, turns the player left after
 * {@code $40} frames, frees Player 1 once they reach Y {@code $580} on the ground (camera minimum Y
 * {@code $5C0}) and Player 2 once Player 1 walks left of X {@code $1660}.
 */
public final class HpzEmeraldTheftPlayerControlObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int STOP_X = 0x14D0;
    private static final int FREEZE_X = 0x15A8;
    private static final int PAN_END_X = 0x1580;
    private static final int CAMERA_LOCK_X = 0x17D0;
    private static final int ALTAR_X = 0x1892;
    private static final int FREE_Y = 0x580;
    private static final int RELEASE_P2_X = 0x1660;

    private static final int PHASE_WAIT_X = 0;
    private static final int PHASE_WALK_TO_ALTAR = 1;
    private static final int PHASE_PAN = 2;
    private static final int PHASE_WAIT_RELEASE = 3;
    private static final int PHASE_FOLLOW = 4;
    private static final int PHASE_WAIT_LANDED = 5;
    private static final int PHASE_WAIT_GROUNDED = 6;
    private static final int PHASE_WALK_TO_FLOOR = 7;
    private static final int PHASE_TURN = 8;
    private static final int PHASE_WAIT_FALL = 9;
    private static final int PHASE_WAIT_LEFT = 10;
    private static final int PHASE_DONE = 11;

    private int phase;
    private int timer;
    /** Low word of {@code Camera_X_pos}'s longword, which {@code loc_64D5C} adds speed into. */
    private int cameraFraction;
    /** Held bits of {@code Ctrl_1_logical} latched by {@code loc_64DAA}'s lock. */
    private int latchedHeld;

    public HpzEmeraldTheftPlayerControlObjectInstance() {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "HpzEmeraldTheftPlayerControl");
    }

    @Override
    public HpzEmeraldTheftPlayerControlObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzEmeraldTheftPlayerControlObjectInstance();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite p1 = player1(services());
        HpzZoneRuntimeState hpz = hpz(services());
        if (p1 == null || hpz == null) {
            return;
        }
        var camera = services().camera();
        switch (phase) {
            case PHASE_WAIT_X -> {
                // loc_64C70
                if (x(p1) < STOP_X) {
                    return;
                }
                phase = PHASE_WALK_TO_ALTAR;
                stopObject(p1);
                lock(p1, 0);
                spawnDynamicObjectLowestFreeSlot(new S3kNativeP2LockInstance());
                camera.setMaxYTarget((short) 0x300);
            }
            case PHASE_WALK_TO_ALTAR -> {
                // loc_64CA6
                if (p1.getAir()) {
                    return;
                }
                p1.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
                if (x(p1) < FREEZE_X) {
                    return;
                }
                phase = PHASE_PAN;
                camera.setScrollLocked(true);
                spawnDynamicObjectLowestFreeSlot(
                        SongFadeTransitionInstance.transitionTo(Sonic3kMusic.MINIBOSS.id));
                p1.setControlLocked(false);
                ObjectControlState.nativeBit7FullControl().applyTo(p1);
                p1.setObjectMappingFrameControl(true);
                p1.setMappingFrame(p1 instanceof Tails ? 0xB0 : 0xC4);
                p1.setAnimationId(7);
                p1.setForcedInputMask(0);
                p1.clearLogicalInputState();
                stopObject(p1);
            }
            case PHASE_PAN -> {
                // loc_64D1A
                int cameraX = (camera.getX() + 2) & 0xFFFF;
                camera.setX((short) cameraX);
                if (cameraX >= PAN_END_X) {
                    phase = PHASE_WAIT_RELEASE;
                    hpz.setKnucklesCutsceneFlag(FLAG_CAMERA_READY);
                }
            }
            case PHASE_WAIT_RELEASE -> {
                // loc_64D38
                if (!hpz.knucklesCutsceneFlag(FLAG_RELEASE_PLAYER)) {
                    return;
                }
                phase = PHASE_FOLLOW;
                ObjectControlState.none().applyTo(p1);
                p1.setObjectMappingFrameControl(false);
                lock(p1, AbstractPlayableSprite.INPUT_RIGHT);
            }
            case PHASE_FOLLOW -> follow(p1, camera);
            case PHASE_WAIT_LANDED -> {
                // loc_64DAA
                if (!hpz.knucklesCutsceneFlag(FLAG_KNUCKLES_LANDED)) {
                    return;
                }
                phase = PHASE_WAIT_GROUNDED;
                p1.setControlLocked(true);
                // st (Ctrl_1_locked) without a Ctrl_1_logical write: Sonic_Control stops copying
                // the pad, so the held bits latched this frame (e.g. a jump still held) keep
                // driving movement until loc_64DE0 writes the walk input. The press byte latched
                // with them is clear, so the held jump must not re-press.
                latchedHeld = p1.getLogicalInputState();
                holdLatchedInput(p1);
                spawnDynamicObjectLowestFreeSlot(new S3kNativeP2LockInstance());
            }
            case PHASE_WAIT_GROUNDED -> {
                // loc_64DCC
                if (!p1.getAir()) {
                    phase = PHASE_WALK_TO_FLOOR;
                }
                holdLatchedInput(p1);
            }
            case PHASE_WALK_TO_FLOOR -> {
                // loc_64DE0
                int d1 = (x(p1) - ALTAR_X) & 0xFFFF;
                int mask = x(p1) < ALTAR_X ? AbstractPlayableSprite.INPUT_RIGHT
                        : AbstractPlayableSprite.INPUT_LEFT;
                p1.setForcedInputMask(mask);
                int distance = (short) d1;
                if (distance < 0) {
                    distance = -distance;
                }
                if ((distance & 0xFFFF) > 4) {
                    return;
                }
                phase = PHASE_TURN;
                timer = 0x3F;
                hpz.markPlayerReachedEmeraldAltar();
                p1.setForcedInputMask(0);
                p1.clearLogicalInputState();
                stopObject(p1);
            }
            case PHASE_TURN -> {
                // loc_64E28
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    return;
                }
                phase = PHASE_WAIT_FALL;
                p1.setDirection(Direction.LEFT);
            }
            case PHASE_WAIT_FALL -> {
                // loc_64E46
                if (y(p1) < FREE_Y || p1.getAir()) {
                    return;
                }
                phase = PHASE_WAIT_LEFT;
                p1.setControlLocked(false);
                camera.setMinY((short) 0x5C0);
            }
            case PHASE_WAIT_LEFT -> {
                // loc_64E6C
                if (x(p1) >= RELEASE_P2_X) {
                    return;
                }
                phase = PHASE_DONE;
                unlockPlayer2();
            }
            default -> {
            }
        }
    }

    /** {@code loc_64D5C}. */
    private void follow(AbstractPlayableSprite p1, com.openggf.camera.Camera camera) {
        int d0 = p1.getXSpeed() & 0xFFFF;
        if (d0 > 0x300) {
            d0 = 0x300;
        }
        p1.setXSpeed((short) d0);
        p1.setGSpeed((short) d0);
        // ext.l d0 / lsl.l #8,d0 / add.l d0,(Camera_X_pos).w
        long cameraLong = (((long) camera.getX() & 0xFFFF) << 16 | cameraFraction) + ((long) (short) d0 << 8);
        int cameraX = (int) (cameraLong >> 16) & 0xFFFF;
        cameraFraction = (int) cameraLong & 0xFFFF;
        camera.setX((short) cameraX);
        if (CAMERA_LOCK_X > cameraX) {
            return;
        }
        phase = PHASE_WAIT_LANDED;
        camera.setX((short) CAMERA_LOCK_X);
        camera.setMinX((short) CAMERA_LOCK_X);
        camera.setMaxX((short) CAMERA_LOCK_X);
        camera.setMinY(camera.getMaxY());
        p1.setControlLocked(false);
        p1.setForcedInputMask(0);
        unlockPlayer2();
    }

    private void holdLatchedInput(AbstractPlayableSprite p1) {
        p1.setForcedInputMask(latchedHeld);
        if ((latchedHeld & AbstractPlayableSprite.INPUT_JUMP) != 0) {
            p1.suppressNextJumpPress();
        }
        // The forced mask also publishes a press bit; the latched Ctrl_1_logical word the ROM
        // records into Stat_table (and Tails' CPU replays) has the held bits only.
        p1.writeLogicalInputAndCurrentFollowerHistory(latchedHeld, false);
    }

    /** {@code st (Ctrl_1_locked).w} with {@code Ctrl_1_logical} held bits. */
    private static void lock(AbstractPlayableSprite p1, int heldMask) {
        p1.setControlLocked(true);
        p1.clearLogicalInputState();
        p1.setForcedInputMask(heldMask);
    }

    /** {@code Stop_Object}. */
    private static void stopObject(AbstractPlayableSprite sprite) {
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
    }

    /** {@code clr.b (Ctrl_2_locked).w}. */
    private void unlockPlayer2() {
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        for (PlayableEntity entity : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)) {
            if (entity != main && entity instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(false);
            }
        }
    }

    int phaseForTest() {
        return phase;
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
