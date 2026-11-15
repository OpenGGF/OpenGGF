package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Metallic Madness's tube door, object $20 ({@code TubeDoorObject}, objects/r8/tube_door.asm:3-286; Wacky Workbench's is another
 * routine, {@link ObjWwzTubeDoor}). One placed object makes a three-part door:
 * <ul>
 *   <li>the placed object is a small top-solid end cap (routine 6 after {@code sub_20F1D0}); once the player comes within
 *       $140 pixels it spawns the other two (routine 4, :60-100);</li>
 *   <li>a second cap, $30 pixels to the right, mirrored (subtype $FF, :63-75);</li>
 *   <li>the door leaf between them, $18 to the right ({@code subtype_2} $FF, :76-86): a top-solid panel that opens when its
 *       switch ({@code switch_flags[subtype]}, bit 7) is held or the player jumps up into it, plays its opening animation,
 *       stays open while the player is near and closes again (routines 0 to $C, :109-260).</li>
 * </ul>
 * The caps and the leaf delete themselves when the placed object is gone (:5-11, :109-113).
 */
public final class ObjMmzTubeDoor extends CdObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private enum Role { PLACED, CAP, LEAF }

    private final Role role;
    private final ObjMmzTubeDoor parent;
    /** obj.var_3e of a cap or the leaf: the placed object's slot ({@code move.w a0,obj.var_3e(a1)}, :64, :83). */
    private final int parentSlot;
    private final int leafSubtype;
    private int routine;
    private int originX;                 // obj.var_2a of the placed object: its initial x
    private int counter;                 // obj.var_2a of the leaf
    private int mapFrame;
    private boolean mirrored;
    private CdAnimator animator;

    public ObjMmzTubeDoor(ObjectSpawn spawn) {
        super(spawn, "Tube door");
        this.role = Role.PLACED;
        this.parent = null;
        this.parentSlot = -1;
        this.leafSubtype = spawn.subtype();
        this.originX = spawn.x();
        this.mirrored = (spawn.renderFlags() & 1) != 0;
    }

    private ObjMmzTubeDoor(ObjectSpawn spawn, Role role, ObjMmzTubeDoor parent, boolean mirrored) {
        super(spawn, "Tube door");
        this.role = role;
        this.parent = parent;
        this.parentSlot = parent.getSlotIndex();
        this.leafSubtype = spawn.subtype();
        this.mirrored = mirrored;
        this.originX = spawn.x();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (role != Role.PLACED && !slotHoldsId(parentSlot, 0x20)) {   // cmpi.b #$20,0(a1); bne: DeleteObject (:8-10, :110-112)
            setDestroyed(true);
            return;
        }
        AbstractPlayableSprite sprite = player instanceof AbstractPlayableSprite s ? s : null;
        if (role == Role.LEAF) leaf(sprite); else part(sprite);
    }

    // ---- The placed object and the mirrored cap (:12-100) -------------------------------------------------

    private void part(AbstractPlayableSprite player) {
        switch (routine) {
            case 0 -> {                                              // TubeDoorObject_1_Routine0 (:38-44), then Routine2
                initPart(this);
                routine = 2;
                mapFrame = 5;
                wake(player);
            }
            case 2 -> wake(player);
            case 4 -> spawnParts();
            default -> { if (player != null) solidObject(Entry.TOP); }   // TubeDoorObject_1_Routine6 (:101-107)
        }
    }

    /** TubeDoorObject_1_Routine2 (:45-55): the player within $140 pixels either side wakes the door. */
    private void wake(AbstractPlayableSprite player) {
        if (player == null) return;
        int d = ((player.getCentreX() & 0xFFFF) - (x & 0xFFFF) + 0x140) & 0xFFFF;
        if (d >= 0x280) return;
        routine += 2;
        mapFrame = 0;
    }

    /** TubeDoorObject_1_Routine4 (:60-100). */
    private void spawnParts() {
        var objects = services().objectManager();
        if (objects == null) return;
        ObjMmzTubeDoor cap = new ObjMmzTubeDoor(new ObjectSpawn(x + 0x30, y, 0x20, 0xFF, 0, false, 0), Role.CAP, this, true);                 // bchg #0,obj.sprite_flags(a1): the new slot's flags are clear
        objects.addDynamicObject(cap);
        initPart(this);
        initPart(cap);
        ObjMmzTubeDoor leaf = new ObjMmzTubeDoor(new ObjectSpawn(x + 0x18, y, 0x20, leafSubtype, 0, false, 0), Role.LEAF, this, false);
        objects.addDynamicObject(leaf);
        routine = 6;                                                 // sub_20F1D0 set routine 6; bra Routine6
        solidObject(Entry.TOP);
    }

    /** sub_20F1D0 (:87-99): the end cap's fields. */
    private void initPart(ObjMmzTubeDoor part) {
        part.routine = 6;
        part.priority = 3;
        part.width = 8;
        part.yRadius = 4;
        part.tileWord = SharedObjectCatalogue.offset(cdLevel().payload(), "mmzTubeDoorTile");
        part.y = y;
    }

    // ---- The door leaf (:109-260) ----------------------------------------------------------------------------------

    private void leaf(AbstractPlayableSprite player) {
        switch (routine) {
            case 0 -> {                                              // TubeDoorObject_0_Routine0 (:132-141), then Routine2
                routine = 2;
                priority = 4;
                width = 0x10;
                yRadius = 4;
                tileWord = SharedObjectCatalogue.offset(cdLevel().payload(), "mmzTubeDoorTile");
                leafRoutine2();
            }
            case 2 -> leafRoutine2();
            case 4 -> closed(player);
            case 6 -> {                                              // TubeDoorObject_0_Routine6 (:188-195)
                counter--;
                if (counter < 0) routine += 2;
                animate();
            }
            case 8 -> {                                              // TubeDoorObject_0_Routine8 (:199-211)
                if (!inside(player, false, -0x100, 0x200, -0xA0, 0x140)) routine -= 6;
            }
            case 0xA -> {                                            // TubeDoorObject_0_RoutineA (:217-235)
                if (inside(player, true, -8, 0x10, -0x78, 0x74)) {
                    routine += 2;
                    animator().anim = 1;
                    counter = 7;
                }
            }
            case 0xC -> {                                            // TubeDoorObject_0_RoutineC (:237-260)
                counter--;
                if (counter < 0) {
                    routine -= 0xA;
                    animator().anim = 0;
                    mapFrame = 1;
                } else {
                    animate();
                }
            }
            default -> { }
        }
    }

    /** TubeDoorObject_0_Routine2 (:142-149): shut, and clear the switch's "pressed" bit. */
    private void leafRoutine2() {
        routine = 4;
        mapFrame = 1;
        SharedSwitchFlags flags = services().gameService(SharedSwitchFlags.class);
        if (flags != null) flags.set(leafSubtype, flags.get(leafSubtype) & ~0x40);   // bclr #6,(a1,d0.w)
    }

    /** TubeDoorObject_0_Routine4 (:150-186): top-solid; open on the switch or on a jump up into it. */
    private void closed(AbstractPlayableSprite player) {
        if (player != null) solidObject(Entry.TOP);
        SharedSwitchFlags flags = services().gameService(SharedSwitchFlags.class);
        if (flags != null && (flags.get(leafSubtype) & 0x80) != 0) {         // tst.b (a2,d0.w); bpl.s ... bra.s loc_20F2CE
            routine += 2;
            counter = 5;
            return;
        }
        if (inside(player, true, -8, 0x10, -3, 0x7B)) {                      // loc_20F2B2
            routine += 6;
            mapFrame = 5;
        }
    }

    /**
     * sub_20F36A / loc_20F370 (:262-278): the player inside a box about the door. {@code needsUp} is the entry that first
     * requires {@code obj.y_speed(a6)} to be negative. The tests are the borrows of {@code sub.w}: the player's Y less the
     * door's Y less {@code d2} must be below {@code d3}, and its X less the door's X less {@code d0} below {@code d1}.
     */
    private boolean inside(AbstractPlayableSprite player, boolean needsUp, int d0, int d1, int d2, int d3) {
        if (player == null) return false;
        if (needsUp && player.getYSpeed() >= 0) return false;                // tst.w obj.y_speed(a6); bpl.s
        int dy = ((player.getCentreY() & 0xFFFF) - (y & 0xFFFF) - d2) & 0xFFFF;
        if (dy >= (d3 & 0xFFFF)) return false;
        int dx = ((player.getCentreX() & 0xFFFF) - (x & 0xFFFF) - d0) & 0xFFFF;
        return dx < (d1 & 0xFFFF);
    }

    private CdAnimator animator() {
        if (animator == null) animator = animator(e -> com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue
                .offset(artProvider().payload(), "mmzTubeDoorAnims"), 0);
        return animator;
    }

    private void animate() {
        CdAnimator a = animator();
        if (a == null) return;
        a.step();
        mapFrame = a.mapFrame();
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return role == Role.PLACED; }

    @Override protected int despawnX() { return originX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(SharedObjectArt.MMZ_TUBE_DOOR, mapFrame, mirrored, false);
    }
}
