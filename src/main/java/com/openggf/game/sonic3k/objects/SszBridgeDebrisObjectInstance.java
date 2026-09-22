package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_45052}/{@code loc_4507E} (sonic3k.asm:90491-90518): one piece of a collapsing
 * Sky Sanctuary bridge. {@code Obj_SSZCollapsingBridge} ({@code $7C}) makes four and
 * {@code Obj_SSZCollapsingBridgeDiagonal} ({@code $7B}) makes eight, from the same routine.
 *
 * <p>Init: {@code bset #7,render_flags} (the on-screen flag {@code Draw_Sprite} maintains),
 * {@code height_pixels $10}, {@code width_pixels 8}, {@code priority $200},
 * {@code make_art_tile(ArtTile_SSZMisc+$20,2,1)} over {@code Map_SSZCollapsingBridge} — the same
 * sheet and palette line {@link SszCutsceneBridgeObjectInstance} uses. The parent supplies the
 * mapping frame and the hang delay.
 *
 * <p>{@code loc_4507E} is three lines: a piece whose {@code render_flags} bit 7 is clear — i.e.
 * one {@code Draw_Sprite} did not draw last frame, because it has left the screen — deletes
 * itself; otherwise it counts {@code $2E(a0)} down and, once that reaches zero, falls under
 * {@code MoveSprite}, which is plain {@code $38} gravity with both velocities starting at zero.
 * It never touches the player and the parent never hears from it again.
 */
public final class SszBridgeDebrisObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    /** {@code move.w #$200,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x200);
    /** {@code make_art_tile(ArtTile_SSZMisc+$20,2,1)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code MoveSprite}: {@code addi.w #$38,y_vel(a0)} after the position add. */
    private static final int GRAVITY = 0x38;

    /**
     * The spawn's subtype carries the mapping frame and its {@code renderFlags} slot the hang
     * delay, so a spawn-based rewind recreation rebuilds the piece exactly.
     */
    private int mappingFrame;
    /** {@code $2E(a0)}: frames left before the piece lets go. */
    private int hangDelay;
    /** 16.16 position and velocity, as {@code MoveSprite} keeps them. */
    private int xFixed;
    private int yFixed;
    private int yVel;

    public SszBridgeDebrisObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZBridgeDebris");
        this.mappingFrame = spawn.subtype() & 0xFF;
        this.hangDelay = spawn.renderFlags();
        this.xFixed = spawn.x() << 16;
        this.yFixed = spawn.y() << 16;
    }

    /** Builds the piece {@code loc_44CDE} / {@code loc_44E52} creates. */
    public static SszBridgeDebrisObjectInstance piece(int x, int y, int mappingFrame, int delay) {
        return new SszBridgeDebrisObjectInstance(
                new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, mappingFrame & 0xFF, delay, false, 0));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // tst.b render_flags(a0) / bmi.s loc_4508A: bit 7 is Draw_Sprite's "drawn last frame"
        // flag, so a piece that has left the window deletes on its next pass.
        if (!isOnScreen()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (hangDelay != 0) {
            hangDelay--;
            return;
        }
        // MoveSprite: the position add uses the pre-gravity velocity, then y_vel grows.
        yFixed += yVel << 8;
        yVel += GRAVITY;
    }

    @Override public int getX() { return (xFixed >> 16) & 0xFFFF; }
    @Override public int getY() { return (yFixed >> 16) & 0xFFFF; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }

    int hangDelayForTest() { return hangDelay; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_CUTSCENE_BRIDGE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false, PALETTE_LINE);
        }
    }
}
