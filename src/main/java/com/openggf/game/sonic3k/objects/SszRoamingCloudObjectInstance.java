package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code loc_57BB2}/{@code loc_57BF6} (sonic3k.asm:116735-116757) and the placement
 * routine {@code sub_5758A} (sonic3k.asm:116153-116192): the five drifting foreground clouds
 * {@code SSZ1_ScreenInit} allocates from {@code word_58758} and records in
 * {@code HScroll_table+$1F6}.
 *
 * <p>Each row is the base Y ({@code $38}), the base X ({@code $3A}), the drift speed
 * ({@code $40}) and the mapping frame. The init pass also draws {@code Random_Number},
 * masks it to {@code $FFF} and adds {@code $C00}, storing the result in {@code $30(a0)} —
 * the low word of {@code Gradual_SwingOffset}'s {@code $2E} speed longword, so each cloud
 * starts its bob at a different phase. That draw is part of the level's RNG order.
 *
 * <p>Per frame {@code loc_57BF6} adds a {@code Gradual_SwingOffset($1C00,$80)} offset to the
 * base Y into {@code $38}, then subtracts the {@code $3E} longword from the {@code $3A}
 * 16.16 X. {@code $3E} is never written, so the subtrahend is the {@code $40} word alone:
 * under one pixel per frame, leftward, at five slightly different rates.
 *
 * <p>{@code sub_5758A} converts those world values to screen coordinates every frame:
 * the vertical reference is 1¼ of the shake-corrected camera Y plus ⅝ of {@code _unkEE9C}
 * with the shake added back, the horizontal one is 1¼ of the camera X, and the differences
 * are masked to {@code $FF} and {@code $1FF} and biased by {@code $70} and {@code $50}. The
 * masks are what make the clouds roam: a cloud that leaves one edge reappears at the other,
 * and the {@code $1FF} horizontal period is 512 pixels wide regardless of the viewport, so a
 * wide viewport shows the wrap seam closer to the right edge than a 320-pixel one does.
 *
 * <p>{@code render_flags $40} selects multi-draw (bit 6); the clear bit 2 selects
 * screen coordinates in {@code loc_1AE58}. The engine converts those back to world
 * coordinates for its renderer. Bucket 0 deliberately puts these clouds in front
 * of the player and the GHZ ball (bucket 5): native movie frames 453000/453080
 * corroborate that overlap (2026-09-24 SSZ cloud-order observation).
 */
public final class SszRoamingCloudObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final Logger LOGGER =
            Logger.getLogger(SszRoamingCloudObjectInstance.class.getName());

    /** {@code SSZ1_ScreenInit}: {@code moveq #5-1,d1}. */
    public static final int CLOUD_COUNT = 5;
    static final int ROW_BYTES = 8;
    /** {@code loc_57BF6}: {@code move.l #$1C00,d0} / {@code move.l #$80,d1}. */
    static final int SWING_SPEED = 0x1C00;
    static final int SWING_ACCELERATION = 0x80;
    /** {@code loc_57BB2}: {@code andi.w #$FFF,d0} / {@code addi.w #$C00,d0}. */
    static final int PHASE_SEED_MASK = 0xFFF;
    static final int PHASE_SEED_BASE = 0xC00;
    /** {@code sub_5758A}: {@code addi.w #$70,d3} / {@code addi.w #$50,d3}. */
    static final int SCREEN_Y_BIAS = 0x70;
    static final int SCREEN_X_BIAS = 0x50;
    /** {@code andi.w #$FF,d3} / {@code andi.w #$1FF,d3}. */
    static final int SCREEN_Y_MASK = 0xFF;
    static final int SCREEN_X_MASK = 0x1FF;
    /** {@code move.w #0,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0);
    /** {@code move.b #$30,width_pixels} / {@code move.b #$10,height_pixels}. */
    private static final int HALF_WIDTH = 0x30;
    private static final int HALF_HEIGHT = 0x10;
    /** Genesis sprite space: screen X {@code $80} is the left edge of the display. */
    private static final int SPRITE_ORIGIN = 0x80;

    /**
     * The {@code word_58758} row this cloud is, carried in the spawn's subtype. Not final:
     * the generic rewind schema restores ordinary scalars and cannot write a final field.
     */
    private int rowIndex;
    private int mappingFrame;
    private int baseY;
    /** {@code $3E} longword: the high word is never written, so this is the {@code $40} word. */
    private int driftPerFrame;
    private boolean rowLoaded;

    private final S3kGradualSwing swing = new S3kGradualSwing();
    /** {@code $3A(a0)} as a 16.16 longword. */
    private int xFixed;
    /** {@code $38(a0)}: the base Y plus this frame's swing offset. */
    private int oscillatingY;

    private int worldX;
    private int worldY;

    /**
     * Rewind recreation: the spawn carries the {@code word_58758} row in its subtype, and the
     * swing the {@code Random_Number} draw seeded is restored from the captured extra, so the
     * seed itself is not needed again.
     */
    public SszRoamingCloudObjectInstance(ObjectSpawn spawn) {
        this(spawn, 0);
    }

    public SszRoamingCloudObjectInstance(ObjectSpawn spawn, int phaseSeed) {
        super(spawn, "SszRoamingCloud");
        this.rowIndex = spawn.subtype();
        // loc_57BB2: jsr Random_Number / andi.w #$FFF,d0 / addi.w #$C00,d0 / move.w d0,$30(a0).
        swing.seedSpeed((phaseSeed & PHASE_SEED_MASK) + PHASE_SEED_BASE);
        this.worldX = spawn.x();
        this.worldY = spawn.y();
    }

    /** Reads this cloud's {@code word_58758} row once the object has its services. */
    private void ensureRow() {
        if (rowLoaded || tryServices() == null) {
            return;
        }
        Row row = readRow(rowIndex);
        baseY = row.baseY();
        mappingFrame = row.mappingFrame();
        driftPerFrame = row.driftSpeed();
        xFixed = row.baseX() << 16;
        oscillatingY = row.baseY();
        rowLoaded = true;
    }

    /**
     * Builds the instance {@code loc_5726A} creates for {@code word_58758} row {@code index}.
     * {@code phaseSeed} is the {@code Random_Number} word the ROM draws per cloud, in the
     * loop's own order.
     */
    public static SszRoamingCloudObjectInstance forRow(int index, int phaseSeed) {
        return new SszRoamingCloudObjectInstance(
                new ObjectSpawn(0, 0, 0, index, 0x40, false, 0), phaseSeed);
    }

    /** {@code word_58758} row {@code index}. */
    record Row(int baseY, int baseX, int driftSpeed, int mappingFrame) {}

    private Row readRow(int index) {
        try {
            com.openggf.data.Rom rom = services().rom();
            if (rom == null) {
                return new Row(0, 0, 0, 0);
            }
            byte[] row = rom.readBytes(
                    Sonic3kConstants.SSZ_ROAMING_CLOUD_TABLE_ADDR + ROW_BYTES * index, ROW_BYTES);
            return new Row(word(row, 0), word(row, 2), word(row, 4), word(row, 6));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSZ roaming cloud row " + index + " unavailable", e);
            return new Row(0, 0, 0, 0);
        }
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        ensureRow();
        // loc_57BF6: the swing offset rides on top of the row's base Y.
        oscillatingY = (short) (swing.step(SWING_SPEED, SWING_ACCELERATION) + baseY);
        // move.l $3E(a0),d0 / sub.l d0,$3A(a0): $3E's high word is zero, so this is the $40 word.
        xFixed -= driftPerFrame;
        resolveScreenPosition();
    }

    /**
     * {@code sub_5758A}. The ROM walks the five slots recorded in {@code HScroll_table+$1F6}
     * from {@code SSZ1_ScreenEvent}; the arithmetic is per-cloud, so each instance applies its
     * own row here.
     */
    private void resolveScreenPosition() {
        ensureRow();
        if (tryServices() == null || services().camera() == null) {
            return;
        }
        var camera = services().camera();
        int cameraX = (short) camera.getXCopy();
        int cameraY = (short) camera.getYCopy();
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        int oscillator = state == null ? 0 : state.cloudOscillator();
        int shake = screenShakeOffset();

        // d0 = (camY - shake) * 5/4 ; d1 = _unkEE9C * 5/4 / 2 ; d0 += d1 ; d0 += shake
        int reference = (short) (cameraY - shake);
        reference = (short) (reference + (reference >> 2));
        int oscillatorTerm = (short) (oscillator + (oscillator >> 2));
        reference = (short) (reference + (oscillatorTerm >> 1) + shake);
        // d1 = camX * 5/4
        int horizontal = (short) (cameraX + (cameraX >> 2));

        int screenY = (((oscillatingY - reference) & SCREEN_Y_MASK) + SCREEN_Y_BIAS);
        int screenX = ((((xFixed >> 16) - horizontal) & SCREEN_X_MASK) + SCREEN_X_BIAS);

        worldX = (camera.getX() + screenX - SPRITE_ORIGIN) & 0xFFFF;
        worldY = (camera.getY() + screenY - SPRITE_ORIGIN) & 0xFFFF;
        updateDynamicSpawn(worldX, worldY);
    }

    /** {@code Screen_shake_offset} as {@code sub_5758A} reads it. */
    private int screenShakeOffset() {
        return 0;
    }

    /**
     * {@code loc_57BF6} has no {@code Sprite_OnScreen_Test}, {@code Delete_Sprite_If_Not_In_Range} or
     * any other range check: {@code SSZ1_ScreenInit} allocates it once at load and it runs for
     * the whole act. Without this the engine's {@code MarkObjGone} equivalent unloads it the
     * first frame its position leaves the camera window.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        resolveScreenPosition();
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ROAMING_CLOUD);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, worldX, worldY, false, false);
        }
    }

    @Override public int getX() { return worldX; }
    @Override public int getY() { return worldY; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }
    @Override public int getOnScreenHalfHeight() { return HALF_HEIGHT; }

    int rowIndexForTest() { return rowIndex; }
    int oscillatingYForTest() { return oscillatingY; }
    int fixedXForTest() { return xFixed; }

}
