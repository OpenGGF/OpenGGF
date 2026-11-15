package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The plant surrounding a 3D ramp, object $2C ({@code Obj3DPlant}, Palmtree Panic/Objects/3D Ramp/
 * Main.asm:7-134). The layout entry becomes the first of a row of plants and allocates the others with
 * {@code FindObjSlot} (Main.asm:24-85): the entry's subtype bit 0 clear gives four row plants (itself
 * and three more at X offsets $40, $80, -$40, -$80) and set gives two ($40, $80); then three static
 * front plants at offsets 0, $60 and -$60 (subtype 1, frame 1, priority 4, width and Y radius $C).
 * So the entry allocates four children (subtype odd) or six.
 *
 * <p>Row plants (subtype 0) slide sideways while the player is on the ramp: their X is the rest
 * offset plus half the player's distance into the ramp's $C0-pixel chunk, mirrored for a player to the
 * left of the base X (Main.asm:93-133). The "on a ramp" bit is the player's {@code oPlayerCtrl} bit 1,
 * read through {@link PpzRampPlayer}. Shipped {@code FixBugs = 0}: {@code FindObjSlot}'s result is not tested
 * (Main.asm:44,67), so a full object pool would write past it; a full pool simply skips the plant here
 * (bounded admission, no RAM model).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class Obj3DPlant extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** Obj3DPlant_Offsets1 and _Offsets2 (3D Ramp/Main.asm:89-90). */
    private static final int[] ROW_OFFSETS = {0x40, 0x80, 0xFFC0, 0xFF80};
    private static final int[] FRONT_OFFSETS = {0, 0x60, 0xFFA0};

    private int routine;
    /** oSubtype: bit 0 is consumed by the init; non-zero plants do not move (Main.asm:94-95). */
    private int subtype;
    /** oVar2A: the ramp's centre X (Main.asm:51). */
    private int baseX;
    /** oVar2C: this plant's rest X (Main.asm:59). */
    private int restX;
    private int mapFrame;

    /** A layout entry: routine 0 turns it into the first plant and allocates the rest. */
    public Obj3DPlant(ObjectSpawn spawn) {
        super(spawn, "3D plant");
        this.subtype = spawn.subtype();
        this.baseX = spawn.x();
        this.priority = 0;
    }

    private Obj3DPlant(int x, int y, int baseX, int restX, int subtype, int mapFrame, int priority, int width,
                       int yRadius) {
        super(new ObjectSpawn(x, y, 0x2C, subtype, 0, false, 0), "3D plant");
        this.routine = 2;
        this.subtype = subtype;
        this.baseX = baseX;
        this.restX = restX;
        this.mapFrame = mapFrame;
        this.priority = priority;
        this.width = width;
        this.yRadius = yRadius;
        this.tileWord = PpzObjectArt.PLANT_TILE;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (bossAct()) return;
        if (routine == 0) init();
        else main(player);
    }

    private boolean bossAct() {
        return PpzStage.bossAct(services());
    }

    /** Obj3DPlant_Init (scddisasm/src/Level/Palmtree Panic/Objects/3D Ramp/Main.asm:24-85). */
    private void init() {
        width = 0x18;
        yRadius = 0x14;
        tileWord = PpzObjectArt.PLANT_TILE;
        int d3 = x;
        int rowCount = (subtype & 1) != 0 ? 2 : 4;      // bclr #0,oSubtype(a0); beq.s .GotCount -> d6 = 3, else 1
        subtype &= ~1;
        // The first iteration of .Init is this entry itself; the rest come from FindObjSlot (.Loop).
        routine = 2;
        baseX = d3;
        x = (d3 + ROW_OFFSETS[0]) & 0xFFFF;
        restX = x;
        var objects = services().objectManager();
        for (int i = 1; i < rowCount && objects != null; i++) {
            int childX = (d3 + ROW_OFFSETS[i]) & 0xFFFF;
            objects.addDynamicObject(new Obj3DPlant(childX, y, d3, childX, 0, 0, 0, 0x18, 0x14));
        }
        for (int i = 0; i < FRONT_OFFSETS.length && objects != null; i++) {
            // .Loop2: subtype 1, oMapFrame 1, oPriority 4, oWidth $C, oYRadius $C; oVar2C is not written.
            objects.addDynamicObject(new Obj3DPlant((d3 + FRONT_OFFSETS[i]) & 0xFFFF, y, d3, 0, 1, 1, 4, 0xC, 0xC));
        }
    }

    /** Obj3DPlant_Main (scddisasm/src/Level/Palmtree Panic/Objects/3D Ramp/Main.asm:93-134). */
    private void main(PlayableEntity player) {
        if (subtype != 0 || player == null) return;
        int shift = 0;
        if (PpzRampPlayer.onRamp(player)) {
            int playerX = player.getCentreX() & 0xFFFF;
            int d0 = playerX & 0xFF;
            if (playerX < baseX) d0 = 0xFF - d0;       // cmp.w oVar2A(a0),d2; bcc.s .GetChunkPos
            d0 = chunkPosition(d0);
            d0 >>= 1;                                   // lsr.w #1,d0
            shift = playerX < baseX ? -d0 : d0;
        }
        x = (restX + shift) & 0xFFFF;                   // add.w oVar2C(a0),d0; move.w d0,oX(a0)
    }

    /** .GetChunkPos .. .GotChunkPos: below $C0 as is, $C0-$EF held at $BF, $F0 up wraps to 0 (Main.asm:109-120). */
    static int chunkPosition(int d0) {
        if (d0 < 0xC0) return d0;
        return d0 >= 0xF0 ? 0 : 0xBF;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || bossAct()) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_PLANT, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int mapFrame() { return mapFrame; }
    public int restX() { return restX; }
}
