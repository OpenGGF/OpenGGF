package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;

import java.util.function.ToIntFunction;

/**
 * Pata-Bata, object $3E ({@code ObjPataBata}, Palmtree Panic/Objects/Pata-Bata/Main.asm:7-81): a
 * butterfly that flies $80 pixels either side of its start on a sine wave. Collision $2A. Its init
 * falls through into its main code in the same frame (Main.asm:41-48).
 *
 * <p>The wave is reproduced as assembled (shipped {@code FixBugs = 0}):
 * <ul>
 *   <li>{@code oVar34} is a word added to every frame; {@code move.b oVar34(a0),d0} reads its <em>high</em>
 *       byte (big-endian), so the angle advances by {@code oVar36 >> 8} = -2 (-1 damaged) per frame
 *       (Main.asm:66-69).</li>
 *   <li>{@code d0}'s high word is still the high word of {@code oVar30} (the last long read of {@code d0},
 *       Main.asm:49,60) when {@code CalcSine} replaces its low word and {@code swap d0} moves the sine to the
 *       top: the value shifted and added to the 16.16 centre line is {@code sine << 16 | $FFFF} flying left
 *       (negative {@code oVar30}) and {@code sine << 16 | 0} flying right (Main.asm:70-75).</li>
 *   <li>{@code oVar2C}/{@code oVar2E} are the start Y and the sub-pixel word {@code $8000}, so the centre line
 *       is {@code y << 16 | $8000} (Main.asm:19-20,74).</li>
 * </ul>
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjPataBata extends PpzBadnik {
    private int originX;
    private int baseY;
    /** oVar30: horizontal velocity, 16.16. */
    private int velocity;
    /** oVar34: the phase word. */
    private int phase;
    /** oVar36: phase step, oVar38: the sine's right shift (Main.asm:25-39). */
    private int phaseStep;
    private int shift;
    private boolean damaged;

    public ObjPataBata(ObjectSpawn spawn) {
        super(spawn, "Pata-Bata");
    }

    @Override protected int tileIdIndex() { return PpzObjectArt.TILE_ID_PATA_BATA; }

    @Override protected String sheetKind() {
        return damaged ? PpzObjectArt.PPZ_PATA_BATA2 : PpzObjectArt.PPZ_PATA_BATA1;
    }

    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        if (routine == 0) init();                       // tst.b oRoutine(a0); bne.w ObjPataBata_Main
        fly();
    }

    /** The init body of ObjPataBata (Pata-Bata/Main.asm:9-46). */
    private void init() {
        routine += 2;
        priority = 3;
        colType = 0x2A;
        xRadius = 0x10;
        width = 0x10;
        yRadius = 0x10;
        originX = x;
        baseY = y;
        damaged = spawn.subtype() != 0;
        velocity = damaged ? -0x4000 : -0x8000;
        phaseStep = damaged ? -0x100 : -0x200;
        shift = damaged ? 4 : 3;
        ToIntFunction<PpzObjectCatalogue.Entry> table = e -> e.pataBataAnimations();
        animator = PpzObjectArt.animator(artProvider(), table, damaged ? 1 : 0);
        initialised = true;
    }

    /** ObjPataBata_Main (Pata-Bata/Main.asm:48-80). */
    private void fly() {
        addToX(velocity);
        int distance = Math.abs((short) (x - originX));
        if (distance >= 0x80) {                         // cmpi.w #$80,d0; blt.s .NoFlip
            velocity = -velocity;
            addToX(velocity);
            flipHorizontally();
            phase = 0;                                  // clr.w oVar34(a0)
        }
        phase = (phase + phaseStep) & 0xFFFF;           // add.w d0,oVar34(a0)
        int sine = TrigLookupTable.sinHex((phase >> 8) & 0xFF);
        int high = velocity < 0 ? 0xFFFF : 0;           // the high word of oVar30 left in d0
        int offset = ((sine & 0xFFFF) << 16 | high) >> shift;      // swap d0; asr.l d1,d0
        setY(offset + ((baseY & 0xFFFF) << 16 | 0x8000));          // add.l oVar2C(a0),d0; move.l d0,oY(a0)
        animate();
    }
}
