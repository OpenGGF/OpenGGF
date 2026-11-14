package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Bigbom, object $23 in the R82x index ({@code BigbomObject}, objects/r8/bigbom.asm:3-446; $23 is a null object in R81x and R83x):
 * a walking bomb badnik made of a controller (subtype 0, never drawn) and a body (subtype 2) plus the fuse, explosion
 * and debris of {@link MmzBigbomPart}. The controller makes the body and later runs the explosion countdown; the body
 * falls to the floor, then either patrols with a hurting touch (collision $A6, off on {@code stage_layer} 1) or, with
 * {@code oSubtype2} 2, walks toward the player when one comes within a window. A player within the window while its
 * frame is 0 lights the fuse; the controller then sequences fuse, explosion and seven debris pieces.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzBigbom extends MmzObject implements TouchResponseProvider, ObjectVisualFacing.Source {
    /** CheckObjDespawn2: cmpi.w #$80+(320+$40)+$80,d0; bls (Level/Object Spawner.asm:385-399). */
    private static final int DESPAWN_RANGE = 0x80 + (320 + 0x40) + 0x80;
    /** word_20D8B0 (objects/r8/bigbom.asm:386-392): frames to show each walk frame. */
    private static final int[] FRAME_TIMES = {0x19, 0x0F, 0x19, 0x12};

    private enum Role { CONTROLLER, BODY }

    private final Role role;
    private final MmzBigbom controller;          // oVar3E of the body
    /** The slot of {@code controller} the native child stores and tests each turn ({@code cmpi.b #$23,obj.id(a1)}). */
    private int controllerSlot = -1;
    private MmzBigbom body;                      // oVar3C of the controller
    private final int sub2;                      // oSubtype2
    private int routine;                         // oRoutine
    private int timer;                           // oVar2A
    private int speed;                           // oVar2C (the body's 16.16 X speed)
    private int anchorX;                         // oVar2C of the controller
    private int walkTimer;                       // oVar30
    private int mapFrame;
    private int colType;
    private int spriteFlip;
    private boolean initialised;

    /** The layout entry: a controller. */
    public MmzBigbom(ObjectSpawn spawn) {
        super(spawn, "Bigbom");
        this.role = Role.CONTROLLER;
        this.controller = null;
        this.sub2 = 0;
        this.sub2Resolved = false;
    }

    private boolean sub2Resolved;
    private int resolvedSub2;                    // the layout entry's oSubtype2 (the controller's own)

    private MmzBigbom(ObjectSpawn spawn, MmzBigbom controller, int sub2) {
        super(spawn, "Bigbom");
        this.role = Role.BODY;
        this.controller = controller;
        this.controllerSlot = controller == null ? -1 : controller.getSlotIndex();
        this.sub2 = sub2;
        this.sub2Resolved = true;
    }

    private int sub2() {
        if (!sub2Resolved) {
            resolvedSub2 = subtype2();
            sub2Resolved = true;
        }
        return role == Role.BODY ? sub2 : resolvedSub2;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!hasArt()) return;
        if (role == Role.CONTROLLER) controllerStep(player); else bodyStep(player);
    }

    private boolean hasArt() {
        var provider = artProvider();
        return provider == null || MmzObjectCatalogue.offset(provider.payload(), "bigbomMappings") >= 0;
    }

    // ---- controller (BigbomObject_0_Routine0, off_20D4C4) ----------------------------------------------------

    private void controllerStep(PlayableEntity player) {
        switch (routine) {
            case 0 -> controllerInit();
            case 4 -> { timer = 0x1E; routine = 6; countdownToFuse(); }    // Routine4 falls into Routine6 (:71-75)
            case 6 -> countdownToFuse();
            case 0xA -> { timer = 0; routine = 0xC; countUp(); }          // RoutineA falls into RoutineC
            case 0xC -> countUp();
            default -> { }                                                 // Routine2 and Routine8: rts
        }
    }

    /** BigbomObject_1_Routine0 (objects/r8/bigbom.asm:48-65). */
    private void controllerInit() {
        anchorX = x;
        initialised = true;
        var objects = services().objectManager();
        ObjectSpawn s = new ObjectSpawn(x, y, spawn.objectId(), 2, 0, false, 0);
        MmzBigbom made = new MmzBigbom(s, this, sub2());
        if (objects == null) return;
        objects.addDynamicObject(made);                                    // jsr SpawnObject; beq (loc_20D4E6)
        body = made;
        routine = 2;
    }

    /** BigbomObject_1_Routine6 (objects/r8/bigbom.asm:75-102): after $1E calls, advance the body and add the fuse. */
    private void countdownToFuse() {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        body.routine += 2;
        var objects = services().objectManager();
        if (objects == null) return;
        ObjectSpawn s = new ObjectSpawn(x, (y - 0x1F) & 0xFFFF, spawn.objectId(), 4, 0, false, 0);
        objects.addDynamicObject(new MmzBigbomPart(s, MmzBigbomPart.Kind.FUSE, this, sub2(),
                (body.spriteFlip & 1) != 0 ? 1 : 0, 0, 0));
        routine = 8;
    }

    /** BigbomObject_1_RoutineC (objects/r8/bigbom.asm:117-170): at call $1E the explosion, $1F the debris, $28 gone. */
    private void countUp() {
        timer = (timer + 1) & 0xFFFF;
        var objects = services().objectManager();
        if (timer == 0x20) body.routine += 2;
        if (timer == 0x1E && objects != null) {
            objects.addDynamicObject(new MmzBigbomPart(
                    new ObjectSpawn(x, y, spawn.objectId(), 6, 0, false, 0), MmzBigbomPart.Kind.EXPLOSION, this, sub2(), 0, 0, 0));
        }
        if (timer == 0x1F && objects != null) {
            // The seven debris pieces' X speed, Y speed (loc_20D5C8, :139-163), 16.16.
            int[][] pieces = {{-0x10000, -0x42000}, {-0x20000, -0x52000}, {-0x30000, -0x62000}, {0x10000, -0x42000},
                    {0x20000, -0x52000}, {0x30000, -0x62000}, {0, -0x32000}};
            for (int[] piece : pieces) {
                objects.addDynamicObject(new MmzBigbomPart(
                        new ObjectSpawn(x, y, spawn.objectId(), 8, 0, false, 0), MmzBigbomPart.Kind.DEBRIS, this, sub2(),
                        0, piece[0], piece[1]));
            }
        }
        if (timer == 0x28) setDestroyed(true);                             // jmp DeleteObject
    }

    // ---- body (BigbomObject_0_Routine2, off_20D6EC) ----------------------------------------------------------

    private void bodyStep(PlayableEntity player) {
        if (!slotHoldsId(controllerSlot, 0x23)) {                                    // cmpi.b #$23,oID(a1); bne DeleteObject
            setDestroyed(true);
            return;
        }
        switch (routine) {
            case 0 -> bodyInit();
            case 2 -> bodyFall();
            case 4 -> bodyWait(player);
            case 6 -> bodyStart();
            case 8 -> bodyChase();
            case 0xA -> bodyCheckArrival(player);
            case 0xC -> bodyPatrolStart(player);
            case 0xE -> bodyPatrol(player);
            case 0x10 -> bodyIgnite();
            case 0x12, 0x16 -> bodyHold();
            case 0x14 -> { mapFrame = 4; routine = 0x16; bodyHold(); }      // Routine14 falls into Routine16 (:434-440)
            case 0x18 -> { setDestroyed(true); return; }                   // Routine18: DeleteObject
            default -> { }
        }
        controller.x = x;                                                  // move.w oX(a0),oX(a1); move.w oY(a0),oY(a1)
        controller.y = y;
    }

    /** BigbomObject_2_Routine0 (objects/r8/bigbom.asm:231-239). */
    private void bodyInit() {
        initialised = true;
        priority = 1;
        tileWord = MmzObjectArt.BIGBOM_TILE;
        width = 0x14;
        yRadius = 0x1A;
        routine = 2;
        bodyFall();                                                        // Routine0 falls into Routine2 (:239-241)
    }

    /** BigbomObject_2_Routine2 (objects/r8/bigbom.asm:241-263). */
    private void bodyFall() {
        addToY(0x10000);
        int d1 = floorDistanceAt(y, yRadius);
        if (d1 >= 0) return;
        y = (y + d1) & 0xFFFF;
        if (sub2 == 2) {
            routine = 4;
            spriteFlip ^= 1;                                               // bchg #0,oSprFlags; bchg #0,oFlags
        } else {
            routine = 0xC;
            colType = 0xA6;
        }
    }

    /** sub_20D8B8 (objects/r8/bigbom.asm:394-408): carry set when the player is within the box. */
    private boolean playerInRange(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return false;
        int dy = ((sprite.getCentreY() - y) - 0xFF60) & 0xFFFF;             // subi.w #$FF60,d0
        if (dy >= 0xF0) return false;                                      // subi.w #$F0; bcc
        int dx = ((sprite.getCentreX() - x) - 0xFFA0) & 0xFFFF;
        return dx < 0xC0;
    }

    /** BigbomObject_2_Routine4 (objects/r8/bigbom.asm:265-279): a player to the right starts the chase. */
    private void bodyWait(PlayableEntity player) {
        if (!playerInRange(player)) return;
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (((sprite.getCentreX() - x) & 0x8000) != 0) return;             // bpl.s loc_20D78A
        routine = 6;
        bodyStart();                                                        // falls into Routine6
    }

    /** BigbomObject_2_Routine6 (objects/r8/bigbom.asm:281-286). */
    private void bodyStart() {
        routine = 8;
        speed = 0x28000;
        timer = 0xE6;
        walkTimer = 0x12;
        mapFrame = 3;
        // Routine6 falls into Routine8 in the same call (:288).
        bodyChase();
    }

    private void applyLayerCollision() {
        colType = stageLayer() != 0 ? 0 : 0xA6;                             // tst.b stage_layer; bne; move.b oVar3D,d0
    }

    /** BigbomObject_2_Routine8 (objects/r8/bigbom.asm:288-311). */
    private void bodyChase() {
        applyLayerCollision();
        addToX(speed);
        advanceWalkFrame();
        timer = (timer - 1) & 0xFFFF;                                      // addi.w #-1,oVar2A(a0); bpl
        if ((timer & 0x8000) != 0) routine += 2;
    }

    /** loc_20D7BA (objects/r8/bigbom.asm:294-306): the walk frame advances through word_20D8B0 while oVar30 counts down. */
    private void advanceWalkFrame() {
        walkTimer = (walkTimer - 1) & 0xFFFF;
        if ((walkTimer & 0x8000) == 0) return;                             // subq.w #1; bpl
        mapFrame = (mapFrame + 1) & 3;
        walkTimer = FRAME_TIMES[mapFrame];
    }

    /** BigbomObject_2_RoutineA (objects/r8/bigbom.asm:318-336): arrived; a player in range lights the fuse. */
    private void bodyCheckArrival(PlayableEntity player) {
        applyLayerCollision();
        if (stageLayer() != 0) return;
        if (playerInRange(player)) routine = 0x10;
    }

    /** BigbomObject_2_RoutineC (objects/r8/bigbom.asm:338-344): start the patrol. */
    private void bodyPatrolStart(PlayableEntity player) {
        routine = 0xE;
        speed = -0xA000;
        timer = 0;
        walkTimer = 0;
        mapFrame = 3;
        // Falls into RoutineE.
        bodyPatrol(player);
    }

    /** BigbomObject_2_RoutineE (objects/r8/bigbom.asm:345-380). */
    private void bodyPatrol(PlayableEntity player) {
        addToX(speed);
        y = (y + floorDistanceAt(y, yRadius)) & 0xFFFF;                    // jsr CheckBlockDown; add.w d1,oY(a0)
        timer = (timer - 1) & 0xFFFF;
        if ((timer & 0x8000) != 0) {
            speed = -speed;
            spriteFlip ^= 1;
            timer = sub2 != 0 ? 0x19 : 0xCC;                                // loc_20D878 (:360-362)
        }
        advanceWalkFrame();
        if (mapFrame == 0 && playerInRange(player)) bodyIgnite();           // loc_20D8A0 (:374-379), loc_20D8DC falls into Routine10
    }

    /** BigbomObject_2_Routine10 (objects/r8/bigbom.asm:413-417): tell the controller to start its countdown. */
    private void bodyIgnite() {
        routine = 0x12;
        controller.routine += 2;
        bodyHold();
    }

    /** BigbomObject_2_Routine12 (objects/r8/bigbom.asm:418-429). */
    private void bodyHold() {
        if (sub2 != 2) return;
        applyLayerCollision();
    }

    // ---- touch, despawn, draw ------------------------------------------------------------------------------

    @Override public int getCollisionFlags() { return role == Role.BODY ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    protected boolean despawnsByCheckObjDespawn() {
        return role == Role.CONTROLLER && sub2() != 2;                      // cmpi.b #2,oSubtype2; beq (:20-32)
    }

    @Override protected int despawnX() { return role == Role.CONTROLLER && initialised ? anchorX : x; }

    /** The controller never draws, so its on-screen bit stays clear and only the chunk distance decides (:20-32). */
    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        if (!despawnsByCheckObjDespawn()) return false;
        int chunk = despawnX() & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > DESPAWN_RANGE;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    public int routine() { return routine; }
    public int mapFrame() { return mapFrame; }
    boolean isBody() { return role == Role.BODY; }
    /** {@code addq.b #2,oRoutine(a1)} on the controller by a part (objects/r8/bigbom.asm:505-511). */
    void advanceRoutine() { routine += 2; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (role != Role.BODY || !initialised) return;
        drawSheet(MmzObjectArt.BIGBOM, mapFrame, (spriteFlip & 1) != 0, false);
    }
}
