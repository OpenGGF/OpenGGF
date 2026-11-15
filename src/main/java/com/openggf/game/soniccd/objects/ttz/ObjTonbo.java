package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;

/**
 * Tonbo, the dragonfly badnik, object $2B ({@code TonboObject}, objects/r4/tonbo.asm:3-73): a collision-$2C
 * badnik (tile $2416, priority 1, $38 wide, $20 tall) that flies back and forth along X at {@code -$10000}
 * (subtype 0) or {@code -$8000} (other subtypes, smaller sprites) 16.16 a frame while bobbing on a sine of
 * its own phase ({@code SineCosine(low byte of oVar2E) * 4096}, so ±16 pixels), the phase advancing 4 (or 1) a
 * frame. Every {@code oVar38} frames ($100 or $200; the first turn at half of it) it turns round, flipping its
 * sprite and status bits. It ends in CheckObjectDespawn2 on its first X.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjTonbo extends TtzBadnik {
    private int originX;
    private int originY;           // oVar2A: the 16.16 base Y
    private int velocity;          // oVar30
    private int phaseStep;         // oVar34
    private int phase;             // oVar2E
    private int turnTimer;         // oVar36
    private int turnPeriod;        // oVar38
    private boolean second;

    public ObjTonbo(ObjectSpawn spawn) {
        super(spawn, "Tonbo");
    }

    @Override protected String sheetKind() { return second ? TtzObjectArt.TONBO2 : TtzObjectArt.TONBO1; }
    @Override protected int despawnX() { return originX; }

    @Override
    protected void run(PlayableEntity player) {
        if (routine == 0) init();
        move();
    }

    /** TonboObject_0_Routine0 (objects/r4/tonbo.asm:21-49). */
    private void init() {
        routine = 2;
        initialised = true;
        colType = 0x2C;
        priority = 1;
        width = 0x1C;
        yRadius = 0x10;
        tileWord = TtzObjectArt.TONBO_TILE;
        originX = x;
        originY = (y << 16) | ySub;
        if (spawn.subtype() == 0) {
            velocity = -0x10000;
            phaseStep = 4;
            turnPeriod = 0x100;
        } else {
            second = true;
            velocity = -0x8000;
            phaseStep = 1;
            turnPeriod = 0x200;
        }
        turnTimer = turnPeriod >> 1;
        animator = TtzObjectArt.animator(artProvider(), "tonboAnimations", 0);
    }

    /** TonboObject_0_Routine2 (objects/r4/tonbo.asm:51-72). */
    private void move() {
        addToX(velocity);
        int sine = TrigLookupTable.sinHex(phase & 0xFF);       // SineCosine on the low byte of oVar2E
        setY(originY + ((sine << 16) >> 4));
        phase = (phase + phaseStep) & 0xFFFF;
        turnTimer = (turnTimer - 1) & 0xFFFF;
        if (turnTimer == 0) {
            turnTimer = turnPeriod;
            velocity = -velocity;
            flipHorizontally();
        }
        animate();
    }
}
