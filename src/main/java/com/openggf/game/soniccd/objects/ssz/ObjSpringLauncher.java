package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Spring launcher, object $1E ({@code SpringLauncherObject}, objects/r7/launch.asm:3-927): a rotating cannon that
 * catches the player, holds him and fires him along its current heading. One placed parent builds three
 * children with {@code SpawnObjectAfter} (:89-149); parent and children are the same object told apart by
 * routine: 2 parent, 4 arm ({@code A}, the spring sprite and the catcher), $A and $C the two gates (decorative
 * posts for subtype 0, the entry and exit of a rail for the other subtypes), 6, 8 and $E states entered later.
 * The parent animates {@code LaunchAnims}; its script index ({@code obj.anim_index}) is the heading, 1 to 8,
 * and every child's sprite, flip and offset is looked up with it.
 *
 * <p><b>Subtype 0 and fixed headings</b> (the layout subtype is rewritten): subtype 2 and above is a launcher
 * frozen at heading {@code subtype - 1} (:80-87, :151-153). The arm, when the player's box overlaps its
 * ({@code $20}, {@code $20}) box (:640-693, so including the hurt-to-normal fixup the booster shares),
 * catches him: the control bit {@code oVar2C} bit 0 is set, he is rolling with radii {@code $E}/{@code 7}
 * and animation 2, and he is placed on the heading's offset (:807-819); his velocity is set at once from the
 * heading ({@code loc_20C194}, :821-868: diagonals {@code (+-$B50, -+$B50)}, horizontals {@code +-$1000},
 * verticals {@code +-$1000} up) and he is released $3C frames later with {@code FM_CE} (:336-345), when
 * {@code SpringAnims} plays and the arm resets (:356-371).
 *
 * <p><b>Subtypes 1 and others</b> ({@code oVar2C} routines, :314-345, :375-476, :758-803): the arm is inert
 * ({@code Routine4}, :171-177) until a gate catches the player; the gate that did starts the other gate drifting
 * one pixel a frame for $1A frames ({@code RoutineE}, :466-483, {@code loc_20C036}), after which the state byte
 * becomes 2 and a tap of A, B or C fires him along the heading (:318-332).
 *
 * <p>The control bit is the shared player's object control ({@code setObjectControlled}); the ROM's
 * {@code oVar3C} (jump flag) clear is {@code setJumping(false)}. {@code debug_mode} (:640, :447, :1010) is not
 * hosted. Facing and ground flags follow the ROM bit writes. The parent is the only routine that despawns
 * ({@code CheckObjectDespawn2} on {@code oVar34}, the placement X); when it does its three children are flagged
 * and delete themselves ({@code oVar3E}, :6-8, :31-52).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSpringLauncher extends SszObject implements ObjectVisualFacing.NonDirectional {
    /** byte_20BC4E: heading -> spring sheet (0 horizontal, 1 vertical, 2 diagonal), scddisasm/src/objects/r7/launch.asm:226-236. */
    private static final int[] SHEET = {2, 0, 2, 1, 2, 0, 2, 1, 2, 0};
    /** unk_20BC64, unk_20BC6E, unk_20BC78: heading -> sprite flip bits per subtype (:243-277). */
    private static final int[][] ARM_FLIPS = {
        {0, 0, 0, 0, 1, 1, 1, 0, 0, 0}, {2, 0, 0, 0, 1, 1, 3, 2, 2, 0}, {1, 1, 3, 2, 2, 0, 0, 0, 1, 0}};
    /** word_20BC82, word_20BCA6, word_20BCCA: heading -> arm offset (x, y) per subtype (:279-310). */
    private static final int[][] ARM_OFFSET = {
        {8, -8, 8, 0, 8, -8, 0, -8, -8, -8, -8, 0, -8, -8, 0, -8, 8, -8},
        {8, 8, 8, 0, 8, -8, 0, -8, -8, -8, -8, 0, -8, 8, 0, 8, 8, 8},
        {-8, -8, -8, 0, -8, 8, 0, 8, 8, 8, 8, 0, 8, -8, 0, -8, -8, -8}};
    /** unk_20BEDA, unk_20BEE4, unk_20BEEE, unk_20BEF8, unk_20BF02 (:487-545): gate frame and flips by heading. */
    private static final int[] GATE_FRAME = {4, 2, 4, 3, 4, 2, 4, 3, 4, 0};
    private static final int[] GATE_FLIP_A0 = {0, 0, 0, 0, 2, 2, 2, 0, 0, 0};
    private static final int[] GATE_FLIP_C0 = {3, 2, 3, 1, 1, 0, 1, 1, 3, 0};
    private static final int[] GATE_FLIP_A1 = {1, 0, 0, 0, 2, 2, 3, 1, 1, 0};
    private static final int[] GATE_FLIP_C1 = {2, 2, 3, 1, 1, 0, 0, 0, 2, 0};
    /** unk_20BF0C, unk_20BF16, unk_20BF20, unk_20BF2A, unk_20BF34, unk_20BF3E (:547-617): gate offset index by heading. */
    private static final int[] GATE_OFFSET_A0 = {1, 0, 1, 2, 3, 4, 3, 2, 1, 0};
    private static final int[] GATE_OFFSET_C0 = {9, 8, 9, 0xA, 0xB, 0xC, 0xB, 0xA, 9, 0};
    private static final int[] GATE_OFFSET_A1 = {7, 0, 1, 2, 3, 4, 5, 6, 7, 0};
    private static final int[] GATE_OFFSET_C1 = {3, 4, 5, 6, 7, 0, 1, 2, 3, 0};
    private static final int[] GATE_OFFSET_A2 = {0xB, 0xC, 0xD, 0xE, 0xF, 8, 9, 0xA, 0xB, 0};
    private static final int[] GATE_OFFSET_C2 = {0xF, 8, 9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF, 0};
    /** word_20BF48 (:619-635): gate offsets (x, y). */
    private static final int[] GATE_OFFSET = {0x18, -0x10, 4, -0x1C, -0x10, -0x18, -0x1C, -4, -0x18, 0x10, -4, 0x1C,
        0x10, 0x18, 0x1C, 4, 0x18, 0x10, 0x1C, -4, 0x10, -0x18, -4, -0x1C, -0x18, -0x10, -0x1C, 4, -0x10, 0x18, 4, 0x1C};
    /** unk_20C224, unk_20C238, unk_20C242: heading -> row of the player offsets below (:871-916). */
    private static final int[] PLAYER_HOLD = {1, 0, 1, 2, 3, 4, 3, 2, 1, 0};
    private static final int[] PLAYER_B = {1, 0, 1, 2, 3, 4, 5, 6, 7, 0};
    private static final int[] PLAYER_C = {3, 4, 5, 6, 7, 0, 1, 2, 3, 0};
    /** word_20C24C (:918-926): the player's offset from the arm or parent (x, y). */
    private static final int[] PLAYER_OFFSET = {0x18, 0, 0x18, -0x18, 0, -0x18, -0x18, -0x18, -0x18, 0, -0x18, 0x18, 0, 0x18, 0x18, 0x18};
    /** word_20C06A, word_20C08E (:734-754): the drifting gate's per-frame step by heading. */
    private static final int[] DRIFT_B = {-1, -1, -1, 0, -1, 1, 0, 1, 1, 1, 1, 0, 1, -1, 0, -1, -1, -1};
    private static final int[] DRIFT_C = {1, 1, 1, 0, 1, -1, 0, -1, -1, -1, -1, 0, -1, 1, 0, 1, 1, 1};

    private static final int HOLD_FRAMES = 0x3C;
    private static final int DRIFT_FRAMES = 0x1A;
    /** {@code FM_CE}, {@code FM_C0} (:344-345, :474). */
    private static final int FM_RELEASE = 0xCE;
    private static final int FM_READY = 0xC0;
    private static final int TILE_ARM_ODD = SszObjectArt.SPRING_TILE_A;
    private static final int TILE_ARM_EVEN = SszObjectArt.SPRING_TILE_B;

    private ObjSpringLauncher parent;
    private ObjSpringLauncher arm;
    private ObjSpringLauncher gateB;
    private ObjSpringLauncher gateC;
    /** oVar2E: the held player; oSubtype (rewritten) and oSubtype2 (the state byte); oVar32; oVar34; oRoutine2; oVar3E. */
    private AbstractPlayableSprite held;
    /** This arm set the held player's control bit and loc_20BD38 has not cleared it yet. */
    private boolean controlOwned;
    private int subtype;
    private int state;
    private int timer;
    private int originX;
    private int savedRoutine;
    private boolean deleteFlag;
    private boolean visible = true;
    private String sheet = SszObjectArt.LAUNCH;
    private SszAnimator animator;

    /** A launcher placed in the layout. */
    public ObjSpringLauncher(ObjectSpawn spawn) {
        super(spawn, "Spring launcher");
        this.subtype = spawn.subtype();
    }

    private ObjSpringLauncher(ObjectSpawn spawn, ObjSpringLauncher parent, int routine) {
        super(spawn, "Spring launcher");
        this.parent = parent;
        this.routine = routine;
        this.subtype = parent.subtype;
        this.originX = parent.x;
    }

    @Override protected String sheetKind() { return sheet; }

    @Override protected boolean despawnsByCheckObjDespawn() { return parent == null && routine == 2; }

    @Override protected int despawnX() { return originX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        boolean out = super.isCustomOutOfRange(cameraX);
        if (out) {                                           // loc_20BA68 (:46-52): flag the three children
            for (ObjSpringLauncher child : new ObjSpringLauncher[] {arm, gateB, gateC}) {
                if (child != null) child.deleteFlag = true;
            }
        }
        return out;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity entity) {
        AbstractPlayableSprite player = entity instanceof AbstractPlayableSprite p ? p : null;
        if (routine >= 4 && deleteFlag) {                    // :4-8
            setDestroyed(true);
            return;
        }
        visible = true;
        if (routine == 0) {                                  // Routine0 falls into Routine2 (:151)
            if (!initParent()) return;
            parentMain();
            return;
        }
        if (player != null && player.getDead()) return;      // cmpi.b #6,player_object+obj.routine; bcc.s: draw only (:15-17)
        dispatch(player);
        if (routine == 4 && subtype != 0) visible = false;   // :22-25
    }

    private void dispatch(AbstractPlayableSprite player) {
        switch (routine) {
            case 2 -> parentMain();
            case 4 -> armIdle(player);
            case 6 -> armHeld(player);
            case 8 -> armReset();
            case 0xA, 0xC -> gate(player);
            case 0xE -> gateDrift();
            default -> { }
        }
    }

    // ---- parent ------------------------------------------------------------------------------------

    /** SpringLauncherObject_0_Routine0 (:71-149); false when art is not ready or a child could not be placed. */
    private boolean initParent() {
        var provider = artProvider();
        if (provider == null) return false;
        routine = 2;
        originX = x;
        tileWord = SszObjectArt.LAUNCH_TILE;
        width = 8;
        yRadius = 8;
        mapFrame = 1;
        animator = SszObjectArt.animator(provider, SszObjectCatalogue.Entry::launchAnimations, 0);
        initialised = true;
        if (subtype >= 2) {                                  // :80-87
            state = subtype - 1;
            animator.setAnimFrame(subtype - 1);
            subtype = 0;
        }
        var objects = services().objectManager();
        if (objects == null) return true;
        int[] routines = {4, 0xA, 0xC};
        ObjSpringLauncher[] made = new ObjSpringLauncher[3];
        for (int i = 0; i < 3; i++) {
            ObjSpringLauncher child = new ObjSpringLauncher(new ObjectSpawn(x, y, spawn.objectId(), subtype, 0, false, 0),
                    this, routines[i]);
            objects.addDynamicObjectAfterCurrent(child);     // jsr SpawnObjectAfter
            if (child.isDestroyed()) {                       // the room check of :89-96: DeleteObject
                for (int j = 0; j < i; j++) made[j].setDestroyed(true);
                setDestroyed(true);
                return false;
            }
            made[i] = child;
            child.initChild(i);
        }
        arm = made[0];
        gateB = made[1];
        gateC = made[2];
        return true;
    }

    /** The per-child writes of :107-146. */
    private void initChild(int index) {
        initialised = true;
        if (index == 0) {                                    // d6 = 2: the spring arm (:115-120)
            tileWord = TILE_ARM_ODD;
            sheet = SszObjectArt.SPRING_H;
            priority = 5;
        } else {                                             // :124-137
            tileWord = SszObjectArt.LAUNCH_TILE;
            yRadius = 0x20;
        }
        if (index == 0) {
            animator = new SszAnimator(artProvider().image(), artProvider().entry().springAnimations(), 0);
        }
    }

    /** SpringLauncherObject_0_Routine2 (:151-167). */
    private void parentMain() {
        if (state != 0) return;                              // tst.b obj.subtype_2(a0); bne.s locret
        if (arm == null) return;
        if (arm.routine < 6 || arm.state == 2) {             // cmpi.b #6,$24(a1); bcs; cmpi.b #2,$29(a1); beq
            animator.step();
            mapFrame = animator.mapFrame();
            if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
        }
    }

    private int heading() {
        return parent.animator.animFrame();                  // $1B(a1): the parent's script index
    }

    // ---- arm ------------------------------------------------------------------------------------------

    /** SpringLauncherObject_0_Routine4 (:171-175) and loc_20BF88 (:639-647). */
    private void armIdle(AbstractPlayableSprite player) {
        if (subtype != 0) return;
        orientArm();
        if (player == null) return;
        if (!SszPlayerOverlap.touches(player, x, y, 0x20, 0x20)) return;          // sub_20BF9C
        capture(player);
    }

    /** sub_20BBC0 (:184-222). */
    private void orientArm() {
        int set = subtype == 0 ? 0 : subtype == 1 ? 1 : 2;
        int heading = heading();
        tileWord = (heading & 1) != 0 ? TILE_ARM_ODD : TILE_ARM_EVEN;
        sheet = switch (SHEET[heading]) {
            case 0 -> SszObjectArt.SPRING_H;
            case 1 -> SszObjectArt.SPRING_V;
            default -> SszObjectArt.SPRING_45;
        };
        spriteFlip = (spriteFlip & ~3) | ARM_FLIPS[set][heading];
        x = (parent.x + ARM_OFFSET[set][heading * 2]) & 0xFFFF;
        y = (parent.y + ARM_OFFSET[set][heading * 2 + 1]) & 0xFFFF;
    }

    /** loc_20C156 (:807-819). */
    private void capture(AbstractPlayableSprite player) {
        takeControl(player);
        held = player;
        controlOwned = true;
        timer = HOLD_FRAMES;
        routine += 2;
        placePlayer(player, this, PLAYER_HOLD, heading());
        launchVelocity(player, heading());
    }

    /** The shared catch writes (:807-813, :758-766): oVar3C clear, control bit, rolling radii, animation 2. */
    private static void takeControl(AbstractPlayableSprite player) {
        player.setJumping(false);
        player.setObjectControlled(true);
        player.setObjectControlAllowsCpu(true);              // TouchResponse and animation still run (player.asm:290-330)
        player.setRolling(true);
        player.setAnimationId(2);
    }

    /** sub_20C002 (:697-711): the player's X and Y become the object's plus the heading's offset. */
    private static void placePlayer(AbstractPlayableSprite player, ObjSpringLauncher at, int[] table, int heading) {
        int offset = table[heading] * 2;
        NativePositionOps.writeXPosPreserveSubpixel(player, at.x + PLAYER_OFFSET[offset]);
        NativePositionOps.writeYPosPreserveSubpixel(player, at.y + PLAYER_OFFSET[offset + 1]);
    }

    /** loc_20C194 (:821-868): the heading's launch velocity, then in the air and off any object (:843-846). */
    private void launchVelocity(AbstractPlayableSprite player, int heading) {
        if ((heading & 1) == 0) {                            // diagonal: mulu.w $1000 of sine/cosine of $E0, lsr.l #8
            int ySpeed = (int) (((TrigLookupTable.sinHex(0xE0) & 0xFFFFL) * 0x1000) >>> 8) & 0xFFFF;
            int xSpeed = (int) (((TrigLookupTable.cosHex(0xE0) & 0xFFFFL) * 0x1000) >>> 8) & 0xFFFF;
            player.setYSpeed((short) ySpeed);
            player.setXSpeed((short) xSpeed);
            if ((spriteFlip & 2) != 0) player.setYSpeed((short) -player.getYSpeed());
            player.setDirection(Direction.RIGHT);            // bclr #0,obj.flags(a2)
            if ((spriteFlip & 1) != 0) {
                player.setDirection(Direction.LEFT);
                player.setXSpeed((short) -player.getXSpeed());
            }
        } else if ((heading & 2) == 0) {                     // horizontal (:850-858): the facing bit is not written
            player.setYSpeed((short) 0);
            player.setXSpeed((short) (((spriteFlip & 1) != 0) ? -0x1000 : 0x1000));
        } else {                                             // vertical (:862-868)
            player.setXSpeed((short) 0);
            player.setYSpeed((short) (((spriteFlip & 2) != 0) ? 0x1000 : -0x1000));   // move.w #$F000 is -$1000
        }
        player.setAir(true);                                 // bset #1,obj.flags(a2)
        player.setOnObject(false);                           // bclr #3,obj.flags(a2)
    }

    /** SpringLauncherObject_0_Routine6 (:314-354). */
    private void armHeld(AbstractPlayableSprite player) {
        AbstractPlayableSprite caught = held;
        if (subtype == 0) {
            if (timer != 0) {                                // loc_20BD2C
                if (--timer != 0) { orientArm(); return; }
                release(caught);                             // loc_20BD38
            }
            animateSpring();                                 // loc_20BD48
            orientArm();                                     // loc_20BD54
            return;
        }
        if (state == 4) {
            animateSpring();
        } else if (state == 2 && player != null && player.isJumpJustPressed()) {     // p1_joy_tap & $70
            state = 4;
            launchVelocity(caught, heading());
            release(caught);
            animateSpring();
        }
        orientArm();
    }

    /** loc_20BD38 (:342-345). */
    private void release(AbstractPlayableSprite caught) {
        if (caught != null) {
            caught.setObjectControlled(false);               // bclr #0,player_object+obj.var_2c
        }
        controlOwned = false;
        services().playSfx(FM_RELEASE);
    }

    /** Clears only the control bit this arm still owns (loc_20BD38's write, without its sound). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (controlOwned && held != null) held.setObjectControlled(false);
        controlOwned = false;
    }

    /** loc_20BD48 (:347-349): AnimateObject over SpringAnims; its $FC steps to routine 8. */
    private void animateSpring() {
        if (animator.step() == 2) routine += 2;
        mapFrame = animator.mapFrame();
        if (animator.showedFrame()) spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
    }

    /** SpringLauncherObject_0_Routine8 (:356-371). */
    private void armReset() {
        routine -= 4;
        animator.setAnimFrame(0);                            // move.b #0,obj.anim_index(a0)
        mapFrame = 0;
        state = 0;
        if (subtype != 0 && parent.gateB != null && parent.gateC != null) {
            parent.gateB.state = 0;
            parent.gateC.state = 0;
        }
    }

    // ---- gates --------------------------------------------------------------------------------------

    /** SpringLauncherObject_0_RoutineA / RoutineC (:375-464). */
    private void gate(AbstractPlayableSprite player) {
        boolean isB = routine == 0xA;
        int[] frame = GATE_FRAME;
        int[] flips;
        int[] offsets;
        if (subtype == 0) {
            flips = isB ? GATE_FLIP_A0 : GATE_FLIP_C0;
            offsets = isB ? GATE_OFFSET_A0 : GATE_OFFSET_C0;
        } else {
            flips = isB ? GATE_FLIP_A1 : GATE_FLIP_C1;
            offsets = isB ? (state == 0 ? GATE_OFFSET_A1 : GATE_OFFSET_A2) : (state == 0 ? GATE_OFFSET_C1 : GATE_OFFSET_C2);
        }
        int heading = parent.animator.animFrame();
        mapFrame = frame[heading];
        spriteFlip = (spriteFlip & ~3) | flips[heading];
        int offset = offsets[heading] * 2;
        x = (parent.x + GATE_OFFSET[offset]) & 0xFFFF;
        y = (parent.y + GATE_OFFSET[offset + 1]) & 0xFFFF;
        if (subtype == 0) return;
        ObjSpringLauncher armObject = parent.arm;
        if (armObject.state == 0) {                          // loc_20BE7E: waiting for a player at this gate
            if (player == null) return;
            if (SszPlayerOverlap.touches(player, x, y, 0x18, 0x18)) gateCatch(player, isB, armObject);
            else state = 0;
        } else if (armObject.state == 2 && state == 0) {     // the held player rides the arm (:430-442)
            if (armObject.held != null) placePlayer(armObject.held, armObject, isB ? PLAYER_B : PLAYER_C, heading);
        }
    }

    /** loc_20C0B2 (:758-803) with loc_20C036 (:715-730). */
    private void gateCatch(AbstractPlayableSprite player, boolean isB, ObjSpringLauncher armObject) {
        takeControl(player);
        armObject.routine += 2;
        armObject.state = 1;
        armObject.held = player;
        armObject.controlOwned = true;
        int heading = parent.animator.animFrame();
        placePlayer(player, armObject, isB ? PLAYER_B : PLAYER_C, heading);
        ObjSpringLauncher other = isB ? parent.gateC : parent.gateB;
        other.savedRoutine = other.routine;
        // The other gate runs its own routine once with the arm's subtype set to 2 (it was routine $A) or 1: this only
        // positions it (the arm state is 1, so nothing else happens, :432-433).
        armObject.subtype = other.routine == 0xA ? 2 : 1;
        other.gate(player);
        other.routine = 0xE;
        other.state = 1;
        int[] drift = other.savedRoutine == 0xA ? DRIFT_B : DRIFT_C;
        other.xVel = drift[heading * 2];
        other.yVel = drift[heading * 2 + 1];
        other.timer = DRIFT_FRAMES;
    }

    /** SpringLauncherObject_0_RoutineE (:466-483). */
    private void gateDrift() {
        if (--timer == 0) {
            parent.arm.state = 2;
            routine = savedRoutine;
            services().playSfx(FM_READY);
            return;
        }
        x = (x + xVel) & 0xFFFF;
        y = (y + yVel) & 0xFFFF;
    }

    @Override
    public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) {
        if (visible) super.appendRenderCommands(commands);
    }

    /** The parent's heading (its script index, {@code obj.anim_index}). */
    public int parentHeading() { return animator == null ? 0 : animator.animFrame(); }

    public int heldFramesLeft() { return timer; }
}
