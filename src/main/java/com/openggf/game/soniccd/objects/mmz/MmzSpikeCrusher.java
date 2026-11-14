package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Spike crusher, object $33 in the R82x and R83x indexes ({@code SpikeCrusherObject}, objects/r8/trap_2.asm:34-428 and
 * objects/r8/trap_3.asm:21-398; $33 is a null object in R81x). The layout entry is one column of up to three solid parts
 * (a centre part, a left and a right extension: {@code oVar3D} 0, 1, 2) that waits until the player is below it
 * and within a window, then sinks half a pixel a frame until its bottom meets the floor. While the player stays
 * under the centre part of a subtype 0 column it adds up to four more columns above it ({@code sub_20D038}, at
 * $12C, $258, $348 and $438 frames), which fall in turn. Every part with a spiked frame owns a
 * {@link MmzSpikeCrusherTip} that hurts.
 *
 * <p>The two builds differ in one data table, {@code byte_20CF22} (objects/r8/trap_2.asm:180-236, trap_3.asm:159-215), and in
 * R82x clearing the centre part's {@code oVar34} on landing (trap_2.asm:336-342, trap_3.asm:315-321), a field nothing
 * else reads.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzSpikeCrusher extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /** byte_20CF22 (objects/r8/trap_2.asm:180-236): subtype to part style, R82x. Entries 0-5 (the Y table has six rows). */
    private static final int[] STYLE_R82 = {0, 1, 2, 1, 0, 2};
    /** byte_20DB02 (objects/r8/trap_3.asm:159-215): the same for R83x. */
    private static final int[] STYLE_R83 = {0, 1, 0, 2, 0, 0};
    /** byte_20CF5A (objects/r8/trap_2.asm:238-242): the centre part's frame, X offset, oWidth and oYRadius by style. */
    private static final int[][] CENTRE = {{1, 0xCC, 0x1C, 8}, {4, 0xBC, 0x0C, 8}, {5, 0xDC, 0x2C, 8}};
    /** byte_20CF66 (objects/r8/trap_2.asm:243-247): the left extension. */
    private static final int[][] LEFT = {{3, 0x00, 0x18, 8}, {3, 0xE0, 0x18, 8}, {3, 0x20, 0x18, 8}};
    /** byte_20CF72 (objects/r8/trap_2.asm:248-254): the right extension. */
    private static final int[][] RIGHT = {{2, 0x34, 0x1C, 8}, {5, 0x24, 0x2C, 8}, {4, 0x44, 0x0C, 8}};
    /** word_20D02C (objects/r8/trap_2.asm:344-352): the probe offset below the part's Y, by subtype. */
    private static final int[] PROBE = {8, 0x20, 0x38, 0x50, 0x68, 0x80};
    /** loc_20D04E..loc_20D076 (objects/r8/trap_2.asm:363-393): the frames under the column at which a piece is added. */
    private static final int[] ADD_AT = {0x12C, 0x258, 0x348, 0x438};

    private final int part;                      // oVar3D: 0 centre, 1 left, 2 right
    private final int subtype;                   // oSubtype
    private int style;                           // oVar3C
    private int routine;                         // oRoutine
    private int anchorX;                         // oVar36
    private int anchorY;                         // oVar32
    private int added = 1;                       // oVar3B
    private int underTimer;                      // oVar30
    private boolean landed;                      // oVar3F
    private int mapFrame;
    private boolean initialised;

    /** The layout entry or a piece added by an existing column: a centre part. */
    public MmzSpikeCrusher(ObjectSpawn spawn) {
        super(spawn, "SpikeCrusher");
        this.part = 0;
        this.subtype = spawn.subtype() & 0xFF;
    }

    /** An extension made by {@code sub_20CEDC} (objects/r8/trap_2.asm:153-170): position and shape arrive from the centre part. */
    private MmzSpikeCrusher(ObjectSpawn spawn, int part, int subtype, int frame, int widthValue, int heightValue,
                            int anchorX) {
        super(spawn, "SpikeCrusher");
        this.part = part;
        this.subtype = subtype;
        this.mapFrame = frame;
        this.width = widthValue;
        this.yRadius = heightValue;
        this.anchorX = anchorX;
    }

    private boolean r83() {
        var level = cdLevel();
        return level != null && level.payload().stage().actNumber() == 3;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!hasArt()) return;                                       // $33 is a null object in R81x
        switch (routine) {
            case 0 -> init();
            case 2 -> waitForPlayer(player);
            case 4 -> fall(player);
            default -> { }                                           // Routine6: rts
        }
        solid(player);
    }

    private boolean hasArt() {
        var provider = artProvider();
        return provider == null || MmzObjectCatalogue.offset(provider.payload(), "spikeCrusherMappings") >= 0;
    }

    /** SpikeCrusherObject_0_Routine0 (objects/r8/trap_2.asm:81-147). */
    private void init() {
        routine = 2;
        initialised = true;
        priority = 3;
        tileWord = MmzObjectArt.SPIKE_CRUSHER_TILE;
        int[] styles = r83() ? STYLE_R83 : STYLE_R82;
        style = styles[Math.min(subtype, styles.length - 1)];                // sub_20CF14 (:172-178)
        var objects = services().objectManager();
        if (part == 0) {
            int[] row = CENTRE[style];
            mapFrame = row[0];
            width = row[2];
            yRadius = row[3];
            anchorX = x;
            anchorY = y;
            x = (anchorX + (byte) row[1]) & 0xFFFF;                          // ext.w d0; add.w oVar36
            added = 1;
            if (objects == null) return;
            objects.addDynamicObject(extension(1, LEFT[style]));              // jsr SpawnObject; loc_20CE78 (:115-129)
            objects.addDynamicObject(extension(2, RIGHT[style]));             // loc_20CE98 (:130-135)
        }
        if (mapFrame != 3 && objects != null) {                              // loc_20CEAA (:136-147)
            objects.addDynamicObject(new MmzSpikeCrusherTip(
                    new ObjectSpawn(x, 0, spawn.objectId(), 0xFF, 0, false, 0), this, mapFrame));
        }
    }

    /** sub_20CEDC (objects/r8/trap_2.asm:153-170): the extension takes the centre part's anchor, Y and subtype. */
    private MmzSpikeCrusher extension(int which, int[] row) {
        int px = (anchorX + (byte) row[1]) & 0xFFFF;
        ObjectSpawn s = new ObjectSpawn(px, y, spawn.objectId(), subtype, 0, false, 0);
        return new MmzSpikeCrusher(s, which, subtype, row[0], row[2], row[3], anchorX);
    }

    /** SpikeCrusherObject_0_Routine2 (objects/r8/trap_2.asm:255-306): wait for the player below and in the window. */
    private void waitForPlayer(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if ((sprite.getCentreY() & 0xFFFF) < (y & 0xFFFF)) return;                // sub.w; bcs (borrow)
        if (((sprite.getCentreY() - y) & 0xFFFF) >= 0x100) return;                // cmpi.w #$100,d0; bcc
        if (inWindow(sprite, subtype == 0 ? 0x30 : 0x50)) routine = 4;
    }

    /** sub_20CFAA / loc_20CFB2 / loc_20CFB8 (objects/r8/trap_2.asm:277-306): |player X - oVar36| within d1. */
    private boolean inWindow(AbstractPlayableSprite sprite, int d1) {
        int d0 = ((sprite.getCentreX() - anchorX) + d1) & 0xFFFF;
        if ((d0 & 0x8000) != 0) return false;                                // bmi
        return d0 < (d1 << 1);                                               // cmp.w d1,d0; bcc
    }

    /** SpikeCrusherObject_0_Routine4 (objects/r8/trap_2.asm:308-352). */
    private void fall(PlayableEntity player) {
        if (subtype == 0 && part == 0 && player instanceof AbstractPlayableSprite sprite) addPieces(sprite);
        if (landed) return;
        int savedY = y;
        int savedSub = ySub;
        y = (y + PROBE[Math.min(subtype, PROBE.length - 1)]) & 0xFFFF;       // add.w d0,oY
        int distance = floorDistance();
        y = savedY;
        ySub = savedSub;                                                     // move.l (sp)+,oY(a0)
        if (distance >= 0) {
            addToY(0x8000);                                                  // addi.l #$8000,oY
        } else {
            landed = true;                                                   // loc_20D01C: oVar3F = $FF
            // R82x also clears oVar34 here (:336-342); nothing reads the centre part's copy.
        }
    }

    private int floorDistance() {
        return floorDistanceAt(y, yRadius);
    }

    /** sub_20D038 (objects/r8/trap_2.asm:354-422): count frames under the column and add a column at four marks. */
    private void addPieces(AbstractPlayableSprite sprite) {
        int d0 = (sprite.getCentreY() - y) & 0xFFFF;
        boolean above = (sprite.getCentreY() & 0xFFFF) < (y & 0xFFFF);
        if (above && ((-d0) & 0xFFFF) >= 0x80) return;                       // neg.w d0; cmpi.w #$80; bcc
        if (!inWindow(sprite, 0x50)) return;                                 // bsr.w loc_20CFB2
        underTimer = (underTimer + 1) & 0xFFFF;
        boolean mark = false;
        for (int at : ADD_AT) mark |= underTimer == at;
        if (!mark || added >= 5) return;                                     // loc_20D082: cmpi.b #5,oVar3B; bcc
        var objects = services().objectManager();
        if (objects == null) return;
        int pieceY = (anchorY - (added << 4)) & 0xFFFF;
        objects.addDynamicObject(new MmzSpikeCrusher(new ObjectSpawn(anchorX, pieceY, spawn.objectId(), added, 0, false, 0)));
        added++;
    }

    /** sub_20CDD8 (objects/r8/trap_2.asm:56-79): a part that is not the spike frame is solid when drawn and level with the player. */
    private void solid(PlayableEntity player) {
        if (mapFrame == 3 || !renderFlagOnScreen()) return;
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        int d0 = (sprite.getCentreY() - y) & 0xFFFF;
        if ((sprite.getCentreY() & 0xFFFF) < (y & 0xFFFF)) d0 = (-d0) & 0xFFFF;
        if (d0 >= 0x20) return;
        solidObject(Entry.SOLID);
    }

    public int mapFrame() { return mapFrame; }

    public boolean landed() { return landed; }
    public int routine() { return routine; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialised ? anchorX : x; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(MmzObjectArt.SPIKE_CRUSHER, mapFrame, false, false);
    }
}
