package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidObjectParams;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code loc_57B8E} (sonic3k.asm:116724-116734): the ten invisible sloped platforms
 * {@code SSZ1_BackgroundInit} builds from {@code word_5853E} so the drawn cloud background can
 * be stood on.
 *
 * <p>Each {@code word_5853E} row is {@code render_flags}, {@code x_pos}, the base {@code y_pos}
 * kept in {@code y_vel}, the half-width in {@code $2E} and a longword pointer to the slope table
 * in {@code $30}; {@code height_pixels} is {@code $80} for every row. The object has no
 * {@code mappings} and never calls a draw routine — the clouds the player sees are the
 * background plane, and these only carry the collision.
 *
 * <p>Every frame it sets {@code y_pos = y_vel - _unkEE9C} and calls
 * {@code SolidObjectTopSloped2}, so the platforms rise and fall against the cloud oscillator
 * that {@link SszCloudOscillatorObjectInstance} drives — in the opposite direction to the
 * background, which adds the same word.
 *
 * <p>{@code SolidObjCheckSloped2} indexes the slope table with
 * {@code (x_pos(player) - x_pos + $2E) >> 1} and lands the player on {@code y_pos - slope[i]}
 * with no baseline term, admitting overlaps 1 to 16 only ({@code loc_1E45A}). The index reaches
 * {@code $2E}, so {@code $2E + 1} bytes are read; eight of the ten rows ask for more bytes than
 * their own table holds and run on into the next one — only the two {@code $40} rows fit, and
 * row 1's {@code $180} half-width reaches into {@code byte_58658}'s ramp. Those bytes are read
 * from the ROM exactly as the 68000 reads them rather than clamped, because the shipped
 * behaviour is what the platform geometry is.
 */
public final class SszSolidCloudObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SpawnRewindRecreatable {
    private static final Logger LOGGER =
            Logger.getLogger(SszSolidCloudObjectInstance.class.getName());

    /** {@code word_5853E}: {@code dc.w $A-1} then ten twelve-byte rows. */
    public static final int CLOUD_COUNT = 0x0A;
    static final int ROW_BYTES = 12;
    /** {@code move.b #$80,height_pixels(a1)}. */
    static final int HEIGHT_PIXELS = 0x80;
    /** {@code loc_1E45A} admits raw feet-relative overlaps 1..16. */
    private static final int TOP_LANDING_OVERLAP_LIMIT = 0x11;

    /**
     * The {@code word_5853E} row this platform is, carried in the spawn's subtype. Not final:
     * the generic rewind schema restores ordinary scalars and cannot write a final field.
     */
    private int rowIndex;

    private int xPos;
    private int baseY;
    private int halfWidth;
    private boolean flipped;
    private byte[] slope;
    private boolean rowLoaded;

    private int y;

    public SszSolidCloudObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszSolidCloud");
        this.rowIndex = spawn.subtype();
    }

    /**
     * Builds the instance {@code loc_57812} creates for {@code word_5853E} row {@code index}. The
     * row index is the whole of the identity: the position and geometry are read from the ROM on
     * the first frame, through {@code services()}, so a spawn-based rewind recreation rebuilds the
     * same platform from the subtype alone.
     */
    public static SszSolidCloudObjectInstance forRow(int index) {
        return new SszSolidCloudObjectInstance(new ObjectSpawn(0, 0, 0, index, 0, false, 0));
    }

    /** Reads this platform's {@code word_5853E} row once the object has its services. */
    private void ensureRow() {
        if (rowLoaded || tryServices() == null) {
            return;
        }
        Row row = readRow(rowIndex);
        xPos = row.xPos();
        baseY = row.baseY();
        halfWidth = row.halfWidth();
        flipped = row.flipped();
        slope = row.slope();
        y = baseY;
        rowLoaded = true;
        updateDynamicSpawn(xPos, baseY);
    }

    /** {@code word_5853E} row {@code index}, read from the ROM at {@code $5853E + 2 + 12 * index}. */
    record Row(int renderFlags, int xPos, int baseY, int halfWidth, int slopePointer, byte[] slope) {
        boolean flipped() {
            // SolidObjSloped2 / SolidObjCheckSloped2: btst #0,render_flags(a0) mirrors the index.
            return (renderFlags & 1) != 0;
        }
    }

    private Row readRow(int index) {
        try {
            com.openggf.data.Rom rom = services().rom();
            if (rom == null) {
                return new Row(0, 0, 0, 0, 0, new byte[]{0});
            }
            int base = Sonic3kConstants.SSZ_SOLID_CLOUD_TABLE_ADDR + 2 + ROW_BYTES * index;
            byte[] row = rom.readBytes(base, ROW_BYTES);
            int renderFlags = word(row, 0);
            int xPos = word(row, 2);
            int baseY = word(row, 4);
            int halfWidth = word(row, 6);
            int pointer = (word(row, 8) << 16) | word(row, 10);
            // The index reaches halfWidth inclusive (SolidObjSloped2's (dx + d1) >> 1).
            byte[] slopeBytes = rom.readBytes(pointer & 0xFFFFFF, halfWidth + 1);
            return new Row(renderFlags, xPos, baseY, halfWidth, pointer, slopeBytes);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSZ solid cloud row " + index + " unavailable", e);
            return new Row(0, 0, 0, 0, 0, new byte[]{0});
        }
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        ensureRow();
        SszZoneRuntimeState state = sszState();
        int oscillator = state == null ? 0 : state.cloudOscillator();
        // loc_57B8E: move.w y_vel(a0),d0 / sub.w (_unkEE9C).w,d0 / move.w d0,y_pos(a0)
        y = (short) (baseY - oscillator);
        // loc_57B8E's SolidObjectTopSloped2 call is the engine's solid phase: the contact
        // system drives every SlopedSolidProvider after the object updates, so writing the
        // new y_pos here is the whole of this object's own work.
    }

    /**
     * {@code loc_57B8E} has no {@code Sprite_OnScreen_Test}, {@code Delete_Sprite_If_Not_In_Range} or
     * any other range check: {@code SSZ1_BackgroundInit} allocates it once at load and it runs for
     * the whole act. Without this the engine's {@code MarkObjGone} equivalent unloads it the
     * first frame its position leaves the camera window.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    /** No {@code mappings} and no draw call in {@code loc_57B8E}. */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }

    @Override public int getX() { ensureRow(); return xPos; }
    @Override public int getY() { ensureRow(); return y; }
    @Override public int getOutOfRangeReferenceX() { ensureRow(); return xPos; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean isSlopeFlipped() { ensureRow(); return flipped; }
    @Override public byte[] getSlopeData() { ensureRow(); return slope; }
    @Override public int getSlopeBaseline() { return 0; }
    @Override public Integer getDirectTopLandingOverlapLimit() { return TOP_LANDING_OVERLAP_LIMIT; }

    @Override
    public SolidObjectParams getSolidParams() {
        ensureRow();
        return SolidObjectParams.of(halfWidth, HEIGHT_PIXELS, HEIGHT_PIXELS);
    }

    int rowIndexForTest() { return rowIndex; }
    int halfWidthForTest() { return halfWidth; }
    int baseYForTest() { return baseY; }

    private SszZoneRuntimeState sszState() {
        return tryServices() == null ? null
                : S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }
}
