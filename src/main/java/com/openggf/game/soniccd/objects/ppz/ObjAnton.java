package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Anton, object $3F ({@code ObjAnton}, Palmtree Panic/Objects/Anton/Main.asm:7-95): an ant that
 * drops to the floor one pixel a frame and then walks $80 pixels either side of its start, following
 * the floor within 7 pixels and turning at ledges and steps. Collision $29.
 *
 * <p>Shipped {@code FixBugs = 0}: {@code ObjAnton_Init} has no {@code addq.b #2,oRoutine}
 * (Anton/Main.asm:24-50), so routine 0 runs the init body and then {@code ObjAnton_Place}'s fall
 * (Main.asm:54-64) every frame until the floor is reached; routine 2 is {@code ObjAnton_Place} again
 * (one more pixel and floor test) before routine 4, {@code ObjAnton_Main}. The init body therefore
 * re-runs each of those frames, which is idempotent.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjAnton extends PpzBadnik {
    private int originX;
    /** oVar2A: the walk velocity as a 16.16 value (Anton/Main.asm:37,44,48). */
    private int velocity;

    public ObjAnton(ObjectSpawn spawn) {
        super(spawn, "Anton");
    }

    @Override protected int tileIdIndex() { return PpzObjectArt.TILE_ID_ANTON; }
    @Override protected String sheetKind() { return PpzObjectArt.PPZ_ANTON; }
    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        switch (routine) {
            case 0 -> { init(); place(); }
            case 2 -> place();
            case 4 -> walk();
            default -> { }
        }
    }

    /** ObjAnton_Init (Anton/Main.asm:24-50). */
    private void init() {
        priority = 4;
        xRadius = 0x18;
        width = 0x18;
        yRadius = 0x13;
        colType = 0x29;
        originX = x;
        boolean damaged = spawn.subtype() != 0;
        velocity = damaged ? -0x8000 : -0x10000;
        if (animator == null) animator = PpzObjectArt.animator(artProvider(), e -> e.antonAnimations(), 0);
        if (animator != null) animator.anim = damaged ? 1 : 0;
        initialised = true;
    }

    /** ObjAnton_Place (Anton/Main.asm:54-64). */
    private void place() {
        addToY(0x10000);
        if (floorDistance() < 0) routine += 2;          // tst.w d1; bpl.s .End; addq.b #2,oRoutine(a0)
    }

    /**
     * {@code ObjAnton_Main}'s {@code .TurnAround} branches back to the top with no other exit (Anton/Main.asm:90-94): a
     * second turn without a success means both directions fail the floor test and the routine would loop for ever
     * (the ROM would hang). It cannot happen on terrain the ROM can walk on; the engine fails loudly instead.
     */
    private static final int MAX_TURNS = 2;

    private IllegalStateException loopFailure() {
        return new IllegalStateException(String.format(
                "Anton never finds floor: x=%04X y=%04X origin=%04X floor distance %d (slot %d)",
                x, y, originX, floorDistance(), getSlotIndex()));
    }

    /** ObjAnton_Main (Anton/Main.asm:68-95). */
    private void walk() {
        for (int turns = 0; ; turns++) {
            if (turns > MAX_TURNS) throw loopFailure();
            addToX(velocity);
            int distance = Math.abs((short) (x - originX));
            if (distance < 0x80) {                      // cmpi.w #$80,d0; bge.s .TurnAround
                int d1 = floorDistance();
                if (d1 >= -7 && d1 <= 7) {              // cmpi.w #-7,d1; blt / cmpi.w #7,d1; bgt
                    y = (y + d1) & 0xFFFF;              // add.w d1,oY(a0)
                    animate();
                    return;
                }
            }
            velocity = -velocity;                       // .TurnAround: neg.l oVar2A(a0); bchg flips
            flipHorizontally();
        }
    }
}
