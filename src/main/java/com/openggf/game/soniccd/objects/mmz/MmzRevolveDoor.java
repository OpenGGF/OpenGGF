package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Revolve door, object $3E in the R82x and R83x indexes ({@code RevolveDoorObject}, objects/r8/revolve_door.asm:3-243): a
 * turnstile that carries the player between the two {@code stage_layer}s. The placed object is the lower door: standing
 * within eight pixels of its centre on it grabs the player (animation $33, or $34 on layer 1), and when the player's
 * animation reaches 5 the door flips {@code stage_layer} and the player's facing and tile priority. It spawns the upper
 * door (subtype 1, $B0 above, animating while the player rotates) and three poles (routine 8/A) that circle the door
 * while the player's animation is $33 or later, passing in front (layer 4) and behind (layer 1).
 *
 * <p>Player side: the grab sets the player's {@code oVar2C} bit 0, the object-control bit: the player's routine skips its
 * movement modes and block check ({@code btst #0,obj.var_2c(a0)}, objects/player.asm:290-299) and Metallic Madness's
 * touch routine returns (r8/player_object_collide.asm:3-7), so the player stays where the grab put it while animations
 * $33 and $34 (RotateBack, RotateFront) run to their change to 5. The release clears the bit and flips the player's facing
 * and tile priority. The bit is also {@link MmzStageSignals#revolveHold()}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzRevolveDoor extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private enum Role { DOOR, UPPER, POLE }

    /** byte_20B924 (objects/r8/revolve_door.asm:103-110): the poles' start angles, indexed by the loop counter. */
    private static final int[] POLE_ANGLES = {0x40, 0x95, 0xEA, 0x00};
    /** cmpi.b #$33,oAnim(a6): the player's rotate animations start here. */
    private static final int ROTATE_ANIM = 0x33;

    private final Role role;
    private int routine;
    private int anim;
    private int mapFrame;
    private int poleAngle;                       // oVar2D
    private int poleSaved;                       // oVar2E
    private boolean grabbed;                     // oVar2C bit 0
    private CdAnimator animator;
    private boolean initialised;

    public MmzRevolveDoor(ObjectSpawn spawn) {
        this(spawn, Role.DOOR, 0, 0);
    }

    private MmzRevolveDoor(ObjectSpawn spawn, Role role, int routine, int poleAngle) {
        super(spawn, "RevolveDoor");
        this.role = role;
        this.routine = routine;
        this.poleAngle = poleAngle;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        switch (role) {
            case DOOR -> updateDoor(sprite);
            case UPPER -> updateUpper(sprite);
            case POLE -> updatePole(sprite);
        }
    }

    // ---- lower door ----------------------------------------------------------------------------------------

    private void updateDoor(AbstractPlayableSprite player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> idle(player);
            case 4 -> rotating(player);
            case 6 -> release(player);
            default -> { }
        }
    }

    /** RevolveDoorObject_0_Routine0 (objects/r8/revolve_door.asm:39-100): the door, the upper door and three poles. */
    private void init() {
        routine = 2;
        initialised = true;
        priority = 1;
        width = 0x28;
        yRadius = 8;
        tileWord = MmzObjectArt.REVOLVE_DOOR_TILE;
        var objects = services().objectManager();
        if (objects == null) return;
        ObjectSpawn upper = new ObjectSpawn(x, y - 0xB0, spawn.objectId(), 1, 0, false, 0);
        objects.addDynamicObjectAfterCurrent(new MmzRevolveDoor(upper, Role.UPPER, 2, 0));
        for (int d6 = 2; d6 >= 0; d6--) {
            int angle = POLE_ANGLES[d6];
            int poleX = x + ((TrigLookupTable.cosHex(angle) * 0x20) >> 8);
            ObjectSpawn pole = new ObjectSpawn(poleX, y - 0x58, spawn.objectId(), 0, 0, false, 0);
            objects.addDynamicObjectAfterCurrent(new MmzRevolveDoor(pole, Role.POLE, 8, angle).withCentre(x));
        }
    }

    private int centreX = Integer.MIN_VALUE;         // oVar2A: the door's X, the poles' pivot

    private MmzRevolveDoor withCentre(int centre) {
        this.centreX = centre;
        return this;
    }

    /** RevolveDoorObject_0_Routine2, subtype 0 (objects/r8/revolve_door.asm:111-150). */
    private void idle(AbstractPlayableSprite player) {
        boolean contact = solidObject(Entry.TOP);                       // bsr sub_20B9F0
        if (!contact) {                                                  // loc_20B994: clear the release flag
            grabbed = false;
            return;
        }
        int sum = ((x - player.getCentreX()) & 0xFFFF) + 8;              // sub.w; addq.w #8
        boolean carry = sum > 0xFFFF;
        int d0 = sum & 0xFFFF;
        if (carry || d0 >= 0x10) {
            grabbed = false;
            return;
        }
        if (grabbed) return;                                             // bset #0,oVar2C(a0); bne
        grabbed = true;
        MmzStageSignals signals = signals();
        if (signals != null) signals.setRevolveHold(true);               // bset #0,oVar2C(a6)
        player.setObjectControlled(true);                                // the same bit: the player's routine skips its modes
        player.setObjectControlAllowsCpu(true);                          // bit 0 only: oPlayerCtrl's sign stays clear
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player, x);   // move.w obj.x(a0),obj.x(a6)
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAnimationId(stageLayer() != 0 ? ROTATE_ANIM + 1 : ROTATE_ANIM);
        routine += 2;
    }

    /** RevolveDoorObject_0_Routine4 (objects/r8/revolve_door.asm:155-161). */
    private void rotating(AbstractPlayableSprite player) {
        animate();
        if (player.getAnimationId() != 5) {                              // cmpi.b #5,oAnim(a6); bne sub_20B9F0
            solidObject(Entry.TOP);
            return;
        }
        routine += 2;
        release(player);
    }

    /** RevolveDoorObject_0_Routine6 (objects/r8/revolve_door.asm:162-178): flip the layer and the player's facing. */
    private void release(AbstractPlayableSprite player) {
        MmzStageSignals signals = signals();
        if (signals != null) signals.setRevolveHold(false);              // bclr #0,oVar2C(a6)
        player.setObjectControlled(false);
        solidObject(Entry.TOP);
        if (signals != null) signals.flipStageLayer();                   // eori.b #1,stage_layer
        player.setDirection(player.getDirection() == Direction.LEFT ? Direction.RIGHT : Direction.LEFT);   // eori.b #1,oFlags(a6)
        // ori.w #$8000,oTile(a6); tst.b stage_layer; beq; andi.w #$7FFF (revolve_door.asm:168-171): in front on layer 0.
        player.setHighPriority(stageLayer() == 0);
        routine = 2;
    }

    /** Clears the control this door still holds (the player-only half of its release). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (role != Role.DOOR || routine != 4) return;
        MmzStageSignals signals = signals();
        if (signals != null) signals.setRevolveHold(false);
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) sprite.setObjectControlled(false);
    }

    // ---- upper door ----------------------------------------------------------------------------------------

    /** RevolveDoorObject_0_Routine2, subtype 1 (objects/r8/revolve_door.asm:111-120). */
    private void updateUpper(AbstractPlayableSprite player) {
        initialised = true;
        priority = 1;
        width = 0x28;
        yRadius = 8;
        tileWord = MmzObjectArt.REVOLVE_DOOR_TILE;
        if (player.getAnimationId() < ROTATE_ANIM) return;
        animate();
    }

    // ---- poles ---------------------------------------------------------------------------------------------

    /** RevolveDoorObject_0_Routine8/RoutineA (objects/r8/revolve_door.asm:187-240). */
    private void updatePole(AbstractPlayableSprite player) {
        initialised = true;
        priority = 1;
        width = 8;
        yRadius = 0x50;
        tileWord = MmzObjectArt.REVOLVE_DOOR_TILE;
        mapFrame = 2;
        int rotating = player.getAnimationId() >= ROTATE_ANIM ? 1 : 0;
        if (routine == 8) {
            if (rotating == 0) return;
            routine += 2;
            poleSaved = (poleAngle + 0x80) & 0xFF;
        }
        if (routine == 0xA) {
            if (rotating == 0) {
                routine -= 2;
                poleAngle = poleSaved;
            } else {
                boolean facingLeft = player.getDirection() == Direction.LEFT;
                boolean upper = stageLayer() != 0;
                int step = (facingLeft == upper) ? 4 : -4;
                poleAngle = (poleAngle + step) & 0xFF;
            }
        }
        x = (centreX + ((TrigLookupTable.cosHex(poleAngle) * 0x18) >> 8)) & 0xFFFF;
        priority = (poleAngle & 0x80) != 0 ? 1 : 4;
    }

    private void animate() {
        if (animator == null) animator = mmzAnimator("revolveDoorAnimations", anim);
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    @Override
    protected boolean despawnsByCheckObjDespawn() {
        return true;
    }

    @Override
    protected int despawnX() {
        return role == Role.POLE ? centreX : x;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.REVOLVE_DOOR, mapFrame, false, false);
    }
}
