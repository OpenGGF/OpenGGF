package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_SSZFloatingPlatform} ({@code $7F}, sonic3k.asm:89968-90005): the small sanctuary
 * platform that dips under a standing player.
 *
 * <p>Init: {@code render_flags 4}, {@code height_pixels $11}, {@code width_pixels $20},
 * {@code priority $180}, {@code make_art_tile(ArtTile_SSZMisc,2,0)} over
 * {@code Map_SSZFloatingPlatform} frame 1, and {@code y_vel(a0) = y_pos(a0)} — the placement Y is
 * kept in {@code y_vel} and {@code y_pos} is rebuilt from it every frame.
 *
 * <p>{@code loc_44AA0} keeps a dip counter in {@code $2E(a0)}: while either standing bit of
 * {@code status(a0)} is set it counts up to 4, otherwise back down to 0, one pixel per frame, and
 * {@code y_pos = y_vel + $2E}. There is no clock gate and no randomness. The solid call is
 * {@code SolidObjectTop} with {@code d1 = $2B}, {@code d2 = d3 = $11} — note {@code d1} is
 * {@code $2B} and not the {@code $20} {@code width_pixels} the init writes, so the platform is
 * wider to stand on than it is to draw.
 *
 * <p>The subtype is never read: all five act-1 placements are {@code $7F:$00}.
 */
public final class SszFloatingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code move.b #1,mapping_frame(a0)}. */
    private static final int MAPPING_FRAME = 1;
    /** {@code make_art_tile(ArtTile_SSZMisc,2,0)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code cmpi.w #4,d0} / {@code bhs.s}: the dip stops at four pixels. */
    private static final int MAX_DIP = 4;
    /** {@code moveq #$2B,d1} / {@code moveq #$11,d2} / {@code moveq #$11,d3}. */
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x2B, 0x11, 0x11);

    /** {@code y_vel(a0)}: the placement Y the dip is measured from. */
    private int baseY;
    /** {@code $2E(a0)}: how far the platform is currently dipped, 0 to 4. */
    private int dip;
    private int y;

    public SszFloatingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZFloatingPlatform");
        baseY = spawn.y();
        y = spawn.y();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // loc_44AA0: andi.w #standing_mask,d1 covers both p1_standing_bit (3) and p2_standing_bit.
        if (isPlayerRiding()) {
            if (dip < MAX_DIP) {
                dip++;
            }
        } else if (dip != 0) {
            dip--;
        }
        y = (baseY + dip) & 0xFFFF;
        // The SolidObjectTop call itself is the engine's solid phase, which runs every
        // SolidObjectProvider after the object updates.
    }

    @Override public int getX() { return getSpawn().x(); }
    @Override public int getY() { return y; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x11; }

    int dipForTest() { return dip; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer =
                getRenderer(Sonic3kObjectArtKeys.SSZ_FLOATING_PLATFORM);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, getX(), y, false, false, PALETTE_LINE);
        }
    }
}
