package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ROM {@code Obj_SSZCollapsingColumn} ({@code $7E}, sonic3k.asm:90007-90118): the bobbing column
 * that breaks into eight pieces when it is stood on. Twenty-five act-1 placements, all subtype 0 —
 * the subtype is never read.
 *
 * <p>Init: {@code render_flags 4}, {@code routine 1}, {@code height_pixels $21},
 * {@code width_pixels $10}, {@code priority $180},
 * {@code make_art_tile(ArtTile_SSZMisc+$10,3,1)} over {@code Map_SSZFloatingPlatform} frame 2,
 * {@code y_vel(a0) = y_pos(a0)} as the bob's base, and
 * {@code $30(a0) = (Random_Number & $1FFF) + $800}. {@code $30} is the <em>low</em> word of the
 * {@code Gradual_SwingOffset} speed longword at {@code $2E}, so that draw randomises each
 * column's bob phase — an init-time {@code Random_Number} call, which is why the column order of
 * a load matters to the RNG stream.
 *
 * <p>{@code loc_44B30}: with either standing bit set it clears {@code routine}, allocates eight
 * debris pieces from {@code word_46618} (four-word rows: X offset, Y offset, hang delay, mapping
 * frame) incrementing {@code routine} once per piece, plays {@code sfx_Collapse}, blanks its own
 * mapping frame and installs {@code loc_44B90}. Each piece decrements {@code routine} again when
 * its delay runs out or when it leaves the screen, and {@code loc_44B90} parks the column at
 * {@code x_pos $7FFF} once the count is back to zero.
 *
 * <p>The bob and the {@code SolidObjectTop} call ({@code d1 = $1B}, {@code d2 = d3 = $21}) run in
 * both states, so a collapsing column is still solid while its pieces hang.
 */
public final class SszCollapsingColumnObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private static final Logger LOGGER =
            Logger.getLogger(SszCollapsingColumnObjectInstance.class.getName());

    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code make_art_tile(ArtTile_SSZMisc+$10,3,1)}. */
    private static final int PALETTE_LINE = 3;
    /** {@code move.b #2,mapping_frame(a0)}; the collapse writes {@code clr.b mapping_frame}. */
    private static final int INTACT_FRAME = 2;
    private static final int COLLAPSED_FRAME = 0;
    /** {@code moveq #$1B,d1} / {@code moveq #$21,d2} / {@code moveq #$21,d3}. */
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x1B, 0x21, 0x21);
    /** {@code move.l #$2800,d0} / {@code move.l #$80,d1}. */
    private static final int SWING_SPEED = 0x2800;
    private static final int SWING_ACCELERATION = 0x80;
    /** {@code andi.w #$1FFF,d0} / {@code addi.w #$800,d0}. */
    private static final int PHASE_MASK = 0x1FFF;
    private static final int PHASE_BIAS = 0x800;
    /** {@code moveq #8-1,d2}. */
    public static final int DEBRIS_COUNT = 8;
    /** {@code word_46618}: eight four-word rows. */
    static final int DEBRIS_TABLE_ADDR = 0x046618;
    static final int DEBRIS_ROW_BYTES = 8;
    /** {@code move.w #$7FFF,x_pos(a0)}. */
    private static final int PARKED_X = 0x7FFF;

    /** {@code y_vel(a0)}: the placement Y the bob is measured from. */
    private int baseY;
    /** {@code $2E}/{@code $32}/{@code $36}: the {@code Gradual_SwingOffset} longwords and flag. */
    private int swingSpeed;
    private int swingOffset;
    private boolean swingReversed;
    /** {@code routine(a0)}: 1 while intact, then the number of debris pieces still reporting. */
    private int pendingDebris = 1;
    private boolean collapsed;
    private boolean phaseSeeded;
    private int x;
    private int y;

    public SszCollapsingColumnObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZCollapsingColumn");
        x = spawn.x();
        baseY = spawn.y();
        y = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        seedPhase();
        if (!collapsed && isPlayerRiding()) {
            collapse();
        }
        // loc_44B90: tst.b routine(a0) / beq.s + bpl.s — zero or negative parks the column.
        if (pendingDebris <= 0) {
            x = PARKED_X;
        }
        swingOffset += swingSpeed;
        y = (baseY + gradualSwingOffset()) & 0xFFFF;
    }

    /**
     * {@code jsr (Random_Number)} in the init, written to {@code $30(a0)} — the low word of the
     * swing speed longword. Deferred to the first frame because the object only has
     * {@code services()} then, which is also where the shared RNG lives.
     */
    private void seedPhase() {
        if (phaseSeeded || tryServices() == null) {
            return;
        }
        phaseSeeded = true;
        var rng = services().rng();
        int draw = rng == null ? 0 : rng.nextWord() & 0xFFFF;
        swingSpeed = ((draw & PHASE_MASK) + PHASE_BIAS) & 0xFFFF;
    }

    /** {@code loc_44B30}. */
    private void collapse() {
        collapsed = true;
        pendingDebris = 0;
        for (int piece = 0; piece < DEBRIS_COUNT; piece++) {
            int[] row = debrisRow(piece);
            if (row == null) {
                break;
            }
            final int index = piece;
            SszCollapsingColumnDebrisObjectInstance debris = spawnChild(() ->
                    new SszCollapsingColumnDebrisObjectInstance(
                            new ObjectSpawn((x + row[0]) & 0xFFFF, y, 0, index, 0, false, 0),
                            this, row[1], row[2], row[3]));
            if (debris == null) {
                break;
            }
            pendingDebris++;
        }
        services().playSfx(Sonic3kSfx.COLLAPSE.id);
    }

    /** One {@code word_46618} row: X offset, Y offset, hang delay, mapping frame. */
    private int[] debrisRow(int index) {
        try {
            var rom = services().rom();
            if (rom == null) {
                return null;
            }
            byte[] bytes = rom.readBytes(DEBRIS_TABLE_ADDR + index * DEBRIS_ROW_BYTES,
                    DEBRIS_ROW_BYTES);
            return new int[]{
                    (short) word(bytes, 0), (short) word(bytes, 2),
                    word(bytes, 4), word(bytes, 6)
            };
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSZ column debris row " + index + " unavailable", e);
            return null;
        }
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /** {@code Gradual_SwingOffset} (sonic3k.asm:92484-92515); returns the offset's high word. */
    private int gradualSwingOffset() {
        int step = SWING_ACCELERATION;
        if (swingReversed) {
            step = -step;
            if (swingOffset < 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = SWING_SPEED;
                swingOffset = 0;
                swingReversed = false;
            }
        } else {
            if (swingOffset > 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = -SWING_SPEED;
                swingOffset = 0;
                swingReversed = true;
            }
        }
        return (short) (swingOffset >> 16);
    }

    /** {@code subq.b #1,routine(a1)} from a debris piece. */
    void reportDebrisSettled() {
        pendingDebris--;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x21; }

    public boolean collapsedForTest() { return collapsed; }
    public int pendingDebrisForTest() { return pendingDebris; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer =
                getRenderer(Sonic3kObjectArtKeys.SSZ_COLLAPSING_COLUMN);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(collapsed ? COLLAPSED_FRAME : INTACT_FRAME,
                    x, y, false, false, PALETTE_LINE);
        }
    }

    static int artTileBase() {
        return Sonic3kConstants.ARTTILE_SSZ_MISC + 0x10;
    }
}
