package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * One slot of a Sonic CD boss on the shared object owner: the routine byte, the 16.16 position
 * ({@code oX}/{@code oXSub}, {@code oY}/{@code oYSub}), the mapping frame, {@code oColType}/{@code oColStatus}
 * and {@code AnimateObject}'s four bytes as plain fields, so every part rewinds by the generic capture
 * (object links are captured as object ids). Parts link to each other with typed fields named for what the
 * native word links point at; nothing models the slot or its addresses.
 *
 * <p>Helpers: {@link #spawn} is {@code FindObjSlot} (Object Functions.asm), {@link #explode} the boss
 * explosion ({@code st oRoutine2} + ID $18: the explosion's non-badnik form, no points or flower,
 * Explosion/Main.asm:7-20), {@link #fmSound} {@code PlayFMSound} by native ID and {@link #subCpu}
 * {@code SubCPUCmd} by native command ID. Drawing uses the part's sheet key and frame; a part whose
 * routine skips {@code DrawObject} returns {@code false} from {@link #drawn()}.
 */
public abstract class CdBossPart extends CdObject
        implements TouchResponseProvider, RewindRecreatable, ObjectVisualFacing.Source {
    /** {@code FM_EXPLODE} (Sound Drivers/FM/_Sound IDs.inc:24). */
    public static final int FM_EXPLODE = 0x9E;

    protected int routine;
    protected int xSub;
    protected int ySub;
    protected int mapFrame;
    protected int colType;
    protected int colStatus;
    // AnimateObject state (Object Animate.asm:14-94): oAnim, oPrevAnim, oAnimFrame, oAnimTime.
    protected int anim;
    protected int prevAnim;
    protected int animFrame;
    protected int animTime;

    protected CdBossPart(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /** A part allocated at {@code (x, y)} with object ID {@code id} (the {@code move.b #id,obj.id(a1)} store). */
    protected CdBossPart(int id, int x, int y, String name) {
        super(new ObjectSpawn(x, y, id, 0, 0, false, 0), name);
    }

    /**
     * The part's logical facing. By default a part has no shared horizontal body facing (its mapping frames carry
     * any direction); a part whose routine mirrors its whole body by its {@code oFlags} direction bit overrides
     * this with that bit (Tidal Tempest's Eggman and Egg Mobiles).
     */
    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return ObjectVisualFacing.Basis.NON_DIRECTIONAL;
    }

    // ---- 16.16 position --------------------------------------------------------------------------------

    /** {@code move.l oX(a0)}: X with its subpixel word. */
    public final long xLong() { return ((long) (x & 0xFFFF) << 16) | (xSub & 0xFFFF); }

    public final long yLong() { return ((long) (y & 0xFFFF) << 16) | (ySub & 0xFFFF); }

    public final void setXLong(long value) {
        x = (int) ((value >>> 16) & 0xFFFF);
        xSub = (int) (value & 0xFFFF);
    }

    public final void setYLong(long value) {
        y = (int) ((value >>> 16) & 0xFFFF);
        ySub = (int) (value & 0xFFFF);
    }

    /** {@code add.l d,oX(a0)}. */
    public final void addXLong(long delta) { setXLong((xLong() + delta) & 0xFFFFFFFFL); }

    public final void addYLong(long delta) { setYLong((yLong() + delta) & 0xFFFFFFFFL); }

    /** {@code add.w d,oX(a0)}: the pixel word only. */
    public final void addX(int delta) { x = (x + delta) & 0xFFFF; }

    public final void addY(int delta) { y = (y + delta) & 0xFFFF; }

    /** {@code oX} as the signed word the boss compares ({@code cmpi.w}/{@code blt}). */
    public final int xWord() { return (short) x; }

    public final int yWord() { return (short) y; }

    public final int routine() { return routine; }

    /** {@code move.b #n,obj.routine(a1)}: another part's write. */
    public final void setRoutine(int value) { routine = value & 0xFF; }

    public final int mapFrame() { return mapFrame; }

    /** {@code move.b #n,obj.sprite_frame(a1)}. */
    public final void setMapFrame(int value) { mapFrame = value & 0xFF; }

    /** {@code move.b #n,obj.sprite_layer(a1)}: the spawner's priority write. */
    public final void setLayer(int value) { priority = value & 0xFF; }

    /** {@code move.w d,obj.x(a0)}: the pixel word; the subpixel word is kept. */
    public final void setXWord(int value) { x = value & 0xFFFF; }

    public final void setYWord(int value) { y = value & 0xFFFF; }

    // ---- touch -----------------------------------------------------------------------------------------

    @Override public int getCollisionFlags() { return colType & 0xFF; }

    @Override public int getCollisionProperty() { return colStatus & 0xFF; }

    /** {@code oColType}, as the boss's own hit checks read it ({@code tst.b obj.collide_type}). */
    public final int colType() { return colType & 0xFF; }

    /** {@code move.b #type,obj.collide_type; move.b #status,obj.collide_status}. */
    public final void setCollision(int type, int status) {
        colType = type & 0xFF;
        colStatus = status & 0xFF;
    }

    // ---- services --------------------------------------------------------------------------------------

    /** {@code FindObjSlot}: the first free dynamic slot; returns false when none is free ({@code bne}). */
    protected final boolean spawn(AbstractObjectInstance child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        objects.addDynamicObject(child);
        return !child.isDestroyed();
    }

    /** A boss explosion: {@code FindObjSlot}; {@code st oRoutine2}; ID $18 at {@code (x, y)}; {@code FM_EXPLODE}. */
    protected final void explode(int ex, int ey) {
        if (spawn(new CdExplosion(ex & 0xFFFF, ey & 0xFFFF, 0, false, 0, 0))) fmSound(FM_EXPLODE);
    }

    protected final void fmSound(int id) { services().playSfx(id); }

    protected final void subCpu(int command) { services().playMusic(command); }

    /** {@code jmp DeleteObject}: the slot is cleared and the part is not drawn this frame. */
    protected final void deleteObject() { setDestroyed(true); }

    /** The stage file, or {@code null} before art is loaded. */
    protected final CdStageImage stageImage() {
        CdObjectArtProvider provider = artProvider();
        return provider == null ? null : provider.image();
    }

    /**
     * {@code LoadPalette} (Level/Palette Load.asm:14-26) of the stage's palette table entry {@code id}: source,
     * destination (relative to the table's first, the palette buffer) and a longword count.
     */
    protected final void loadPalette(int id) {
        CdStageImage image = stageImage();
        var level = cdLevel();
        if (image == null || level == null) return;
        int table = com.openggf.game.soniccd.level.CdStageCatalogue.entry(level.payload()).paletteTable();
        int entry = table + id * 8;
        writePalette(image, image.offsetOf(image.u32(entry)), image.u16(entry + 4) - image.u16(table + 4),
                (image.u16(entry + 6) + 1) * 4);
    }

    /** {@code LoadCapsulePal} (Level/_Objects/Level End/Main.asm:913-921): 32 bytes to palette line 1. */
    protected final void loadCapsulePalette() {
        CdStageImage image = stageImage();
        var level = cdLevel();
        if (image == null || level == null) return;
        int source = CdBossCatalogue.offset(level.payload(), "capsulePalette");
        if (source >= 0) writePalette(image, source, 0x10 * 2, 0x20);
    }

    /** Writes {@code bytes} from the stage file at {@code source} to palette RAM offset {@code destination}. */
    protected final void writePalette(CdStageImage image, int source, int destination, int bytes) {
        byte[] data = image.slice(source, bytes);
        var level = services().levelManager().getCurrentLevel();
        for (int line = destination / 32; line * 32 < destination + bytes && line < 4; line++) {
            byte[] lineBytes = new byte[32];
            var palette = level.getPalette(line);
            for (int c = 0; c < 16; c++) {
                int word = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(c));
                lineBytes[c * 2] = (byte) (word >> 8);
                lineBytes[c * 2 + 1] = (byte) word;
            }
            for (int i = 0; i < 32; i++) {
                int at = line * 32 + i - destination;
                if (at >= 0 && at < bytes) lineBytes[i] = data[at];
            }
            services().updatePalette(line, lineBytes);
        }
    }

    /**
     * {@code AnimateObject} (Object Animate.asm:14-94) over the script table at {@code table} in the stage file,
     * with the part's own {@code oAnim}/{@code oPrevAnim}/{@code oAnimFrame}/{@code oAnimTime}: $FF loops, $FE
     * steps back, $FD changes animation, $FC returns 2 (routine + 2). Returns 0 or 2.
     */
    protected final int animate(int table) {
        CdStageImage image = stageImage();
        if (image == null || table < 0) return 0;
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return 0;
        int script = table + (short) image.u16(table + anim * 2);
        animTime = image.u8(script);
        int value = image.u8(script + 1 + animFrame);
        if (value < 0x80) {
            showFrame(value);
            return 0;
        }
        switch (value) {
            case 0xFF -> {
                animFrame = 0;
                showFrame(image.u8(script + 1));
            }
            case 0xFE -> {
                animFrame = (animFrame - image.u8(script + 2 + animFrame)) & 0xFF;
                showFrame(image.u8(script + 1 + animFrame));
            }
            case 0xFD -> anim = image.u8(script + 2 + animFrame);
            case 0xFC -> {
                return 2;
            }
            default -> { }
        }
        return 0;
    }

    private void showFrame(int value) {
        mapFrame = value & 0x1F;
        animFrame = (animFrame + 1) & 0xFF;
    }

    /** {@code clr.b oMapFrame; clr.b oAnimFrame; clr.b oAnimTime; clr.b oVar1F; move.b #n,oAnim}. */
    protected final void restartAnimation(int newAnim) {
        mapFrame = 0;
        animFrame = 0;
        animTime = 0;
        anim = newAnim;
    }

    // ---- drawing ---------------------------------------------------------------------------------------

    /** The part's sheet key for this stage, or {@code null} before art is loaded. */
    protected abstract String sheetKey();

    /** False when this frame's routine skipped {@code DrawObject}. */
    protected boolean drawn() { return routine != 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !drawn()) return;
        String key = sheetKey();
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    // ---- rewind ----------------------------------------------------------------------------------------

    /** A shell of the same class; the generic capture restores its scalars and links afterwards. */
    protected abstract CdBossPart shell(ObjectSpawn spawn);

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return shell(ctx.spawn());
    }
}
