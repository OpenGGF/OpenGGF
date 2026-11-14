package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Twin walker, object $26 in the R82x index ({@code TwinWalkerObject}, objects/r8/twin_walker.asm:3-285; $26 is a null object in
 * R81x and R83x): two solid platforms joined by a rope of seven links that walk by turns. The layout entry is the
 * left platform and the controller; it makes seven links and the right platform ({@code oSubtype} 1, which only keep
 * themselves alive while the controller exists) and runs everything itself: the platform a player has stepped on stays
 * put as the pivot while the rope swings the other platform a quarter turn, until that one meets the floor; then the
 * roles swap. The controller's {@code oVar2A} is the phase (2: the right platform is the pivot, 6: the left one),
 * {@code oVar2B} the number of steps left ($C) and {@code oVar2C} the rope angle.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzTwinWalker extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private enum Role { MAIN, RIGHT, LINK }

    private final Role role;
    private final MmzTwinWalker main;           // oVar2E of a part (the controller itself for the controller)
    /** The controller's slot, as native keeps it in oVar2E: a part tests the ID byte there each turn. */
    private final int mainSlot;
    // Controller state.
    private MmzTwinWalker right;                // oVar3E
    private final MmzTwinWalker[] links = new MmzTwinWalker[7];   // oVar30-oVar3C
    private int phase;                          // oVar2A
    private int steps;                          // oVar2B
    private int angle;                          // oVar2C
    // Platform state.
    private int platformRoutine;                // oRoutine
    private int xSpeed;                         // oXVel
    private int mapFrame;
    private boolean initialised;

    public MmzTwinWalker(ObjectSpawn spawn) {
        super(spawn, "TwinWalker");
        this.role = spawn.subtype() == 0 ? Role.MAIN : Role.LINK;
        this.main = this;
        this.mainSlot = -1;
    }

    private MmzTwinWalker(ObjectSpawn spawn, Role role, MmzTwinWalker main) {
        super(spawn, "TwinWalker");
        this.role = role;
        this.main = main;
        this.mainSlot = main.getSlotIndex();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (role != Role.MAIN) {
            // loc_20E482 (objects/r8/twin_walker.asm:273-283): a part lives while the controller does.
            // movea.w oVar2E,a1; cmpi.b #$26,oID(a1): a controller that CheckObjectDespawn unloaded left its slot free.
            if (!slotHoldsId(mainSlot, 0x26)) setDestroyed(true);
            return;
        }
        if (!hasArt()) return;
        if (phase == 0) init();
        if (phase == 4) phase = 6;                                   // Routine4: oVar2A = 6, then Routine6
        if (phase == 8) phase = 2;                                   // Routine8: oVar2A = 2, then Routine2
        if (phase == 2) {                                            // Routine2 (:60-66): the right one pivots
            drive(right);
            platform(this);
        } else if (phase == 6) {                                     // Routine6 (:98-106): the left one pivots
            drive(this);
            platform(right);
        }
    }

    private boolean hasArt() {
        var provider = artProvider();
        return provider == null || MmzObjectCatalogue.offset(provider.payload(), "twinWalkerMappings") >= 0;
    }

    /** TwinWalkerObject_0_Routine0 (objects/r8/twin_walker.asm:22-58). */
    private void init() {
        var objects = services().objectManager();
        if (objects == null) return;
        int x0 = x;
        MmzTwinWalker last = null;
        for (int i = 0; i < 8; i++) {                                // d6 = 8 and dbf: eight SpawnObject calls
            ObjectSpawn s = new ObjectSpawn((x0 + 8 * (i + 1)) & 0xFFFF, y, spawn.objectId(), 1, 0, false, 0);
            MmzTwinWalker part = new MmzTwinWalker(s, i == 7 ? Role.RIGHT : Role.LINK, this);
            part.initialised = true;
            part.tileWord = MmzObjectArt.TWIN_WALKER_TILE;
            part.priority = 3;
            part.mapFrame = 1;
            part.width = 4;
            part.yRadius = 4;
            objects.addDynamicObject(part);                          // jsr SpawnObject (loc_20E1F0)
            if (i < 7) links[i] = part;
            last = part;
        }
        right = last;
        shapePlatform(right);                                        // bsr.w sub_20E2F8 (:114-122)
        shapePlatform(this);
        initialised = true;
        tileWord = MmzObjectArt.TWIN_WALKER_TILE;
        phase = 2;
        steps = 0x0C;
        angle = 0x8000;
    }

    /** sub_20E2F8 (objects/r8/twin_walker.asm:114-122). */
    private static void shapePlatform(MmzTwinWalker p) {
        p.mapFrame = 0;
        p.priority = 4;
        p.width = 0x18;
        p.yRadius = 0x29;
    }

    // ---- sub_20E318: the pivot ----------------------------------------------------------------------------

    /** sub_20E318 (objects/r8/twin_walker.asm:124-190) on {@code pivot}. */
    private void drive(MmzTwinWalker pivot) {
        switch (pivot.platformRoutine) {
            case 2 -> {                                              // Routine2: half a pixel into both positions
                pivot.platformRoutine = 4;
                pivot.xSub = 0x8000;
                pivot.ySub = 0x8000;
                swing(pivot);
            }
            case 4 -> swing(pivot);
            default -> pivot.topSolid();                             // Routine0: TopSolidObject, DrawObject
        }
    }

    /** TwinWalkerObject_1_Routine4 (objects/r8/twin_walker.asm:151-190): turn the rope and lay out the links. */
    private void swing(MmzTwinWalker pivot) {
        angle = (angle + 0x200) & 0xFFFF;                            // addi.w #$200,oVar2C
        int a = (angle >> 8) & 0xFF;                                 // move.b oVar2C(a1),d0
        int sin = TrigLookupTable.sinHex(a);                         // SineCosine: d0 = sine, d1 = cosine
        int cos = TrigLookupTable.sinHex((a + 0x40) & 0xFF);
        int dx = (cos << 16) >> 5;                                   // swap; asr.l #5
        int dy = (sin << 16) >> 5;
        int x32 = ((pivot.x & 0xFFFF) << 16) | pivot.xSub;
        int y32 = ((pivot.y & 0xFFFF) << 16) | pivot.ySub;
        for (MmzTwinWalker link : links) {                           // loc_20E37A
            x32 += dx;
            y32 += dy;
            link.setPosition32(x32, y32);
        }
        MmzTwinWalker target = pivot == right ? this : right;        // tst.b oSubtype(a0): the right one swings the left
        int before = ((target.x & 0xFFFF) << 16) | target.xSub;
        x32 += dx;
        y32 += dy;
        target.setPosition32(x32, y32);
        target.xSpeed = (short) ((x32 - before) >> 8);               // sub.l d3,d5; asr.l #8,d5; move.w d5,oXVel
        pivot.topSolid();
    }

    private void setPosition32(int x32, int y32) {
        x = x32 >>> 16;
        xSub = x32 & 0xFFFF;
        y = y32 >>> 16;
        ySub = y32 & 0xFFFF;
    }

    // ---- sub_20E3B6: the platform that is not the pivot -----------------------------------------------------

    /** sub_20E3B6 (objects/r8/twin_walker.asm:192-266) on {@code platform}. */
    private void platform(MmzTwinWalker platform) {
        if (platform.platformRoutine == 0) {
            // TwinWalkerObject_2_Routine0 (:206-222): stepped on and steps left: start the swing.
            if (platform.topSolid() && steps != 0) {
                steps--;
                platformRoutineBump(this);
                platformRoutineBump(right);
            }
        } else if (platform.platformRoutine == 2) {
            platform.swinging();
        }
    }

    private static void platformRoutineBump(MmzTwinWalker p) {
        p.platformRoutine += 2;
    }

    /** TwinWalkerObject_2_Routine2 (objects/r8/twin_walker.asm:224-252). */
    private void swinging() {
        carriedTopSolid();                                           // bsr.w sub_20E454
        int turned = (main.angle + 0x4000) & 0xFFFF;                 // addi.w #$4000,d0; bmi
        if ((turned & 0x8000) != 0) return;
        int d1 = floorDistanceAt(y, yRadius);
        if (d1 >= 0) return;                                         // tst.w d1; bpl
        y = (y + d1 + 2) & 0xFFFF;                                   // addq.w #2,d1; add.w d1,oY
        main.phase += 2;
        main.angle = (main.angle - 0x8000) & 0xFFFF;
        main.platformRoutine = 0;
        main.right.platformRoutine = 0;
        main.xSpeed = 0;
        main.right.xSpeed = 0;
    }

    /**
     * sub_20E454 (objects/r8/twin_walker.asm:254-270): probe with no X speed and a $2B radius; if the player is on top, repeat
     * the real call with the platform's X speed and its $29 radius so that the rider is carried.
     */
    private void carriedTopSolid() {
        int savedSpeed = xSpeed;
        xSpeed = 0;
        yRadius = 0x2B;
        boolean on = topSolid();
        xSpeed = savedSpeed;
        yRadius = 0x29;
        if (on) topSolid();
    }

    private boolean topSolid() {
        return solidObject(Entry.TOP);
    }

    @Override public int solidXVelocity() { return xSpeed; }

    /** {@code jmp CheckObjectDespawn} ends the controller's routines (objects/r8/twin_walker.asm:67-93). */
    @Override protected boolean despawnsByCheckObjDespawn() { return role == Role.MAIN; }

    // ---- state for tests and tooling ---------------------------------------------------------------------

    public int phase() { return phase; }
    public int steps() { return steps; }
    public int angle() { return angle; }
    public MmzTwinWalker rightPlatform() { return right; }
    public MmzTwinWalker link(int index) { return links[index]; }
    public int platformRoutine() { return platformRoutine; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.TWIN_WALKER, mapFrame, false, false);
    }
}
