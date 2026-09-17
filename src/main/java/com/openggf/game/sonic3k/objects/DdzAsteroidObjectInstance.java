package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code Obj_DDZAsteroid} ($B7, sonic3k.asm:174310-174477).
 *
 * <p>{@code sub_83146}: the subtype's high nibble selects size 0/1/2 (16/24/40 pixels, mapping
 * frames {@code $26-$28}, {@code Check_InMyRange} boxes {@code word_831A2-word_831B2}); the low
 * nibble selects {@code x_vel} from {@code word_8317E}. Each frame the asteroid moves
 * ({@code MoveSprite2}, skipped in debug placement), subtracts the wrap offset {@code _unkFAAE}, and
 * while Player 1 is powered tests the box against Player 1 before {@code Sprite_OnScreen_Test}.
 *
 * <p>A touched asteroid ({@code loc_8222A}) plays {@code sfx_Collapse}, pushes the flight controller
 * back at {@code -$400}, removes {@code $10000} from the autoscroll speed (floor {@code $10000}),
 * splits a size-2 asteroid into three size-1 asteroids placed by Player 1's height relative to it,
 * scatters 5 or 7 {@code loc_823EE} debris, clears its respawn bit and deletes itself. An
 * {@code AllocateObject} failure abandons the remaining children.
 */
public final class DdzAsteroidObjectInstance extends AbstractDdzObjectInstance {
    /** {@code word_8317E}. */
    private static final int[] X_VELOCITIES = {-0x80, -0x40, 0, 0x40, 0x80, -0x200};
    /** {@code byte_8318A}: width, height, mapping frame per size. */
    private static final int[][] SIZE_ATTRIBUTES = {{0x10, 0x10, 0x26}, {0x18, 0x18, 0x27}, {0x28, 0x28, 0x28}};
    /** {@code word_831A2}, {@code word_831AA}, {@code word_831B2}. */
    private static final int[][] HIT_BOXES = {
            {-0x18, 0x30, -0x18, 0x30}, {-0x20, 0x40, -0x20, 0x40}, {-0x28, 0x50, -0x38, 0x68}};
    /** {@code word_82352}, {@code word_8235E}, {@code word_8236A}: split-child offsets. */
    private static final int[][][] SPLIT_OFFSETS = {
            {{0, -0x18}, {0x18, 0}, {0, 0x18}},
            {{-0x18, 0}, {0, 0x18}, {0x18, 0}},
            {{-0x18, 0}, {0, -0x18}, {0x18, 0}}};
    /** {@code word_82376}: the parent's own shift before the debris. */
    private static final int[][] SPLIT_SHIFT = {{-0x10, 0}, {0, -0x18}, {0, 0x18}};
    /** {@code word_8238A} (5 pieces) and {@code word_823B4} (7): dx, dy, subtype word, x_vel, y_vel. */
    private static final int[][][] DEBRIS = {
            {{-8, 2, 0, -0x80, 0x140}, {8, -4, 0, -0xC0, -0x100}, {-4, -8, 0x100, -0x100, -0x200},
                    {8, 4, 0x100, -0x200, 0x100}, {4, 8, 0x100, -0x180, 0x280}},
            {{-0xC, -4, 0, -0x80, -0x180}, {-4, 0xC, 0, -0x100, 0x100}, {6, -8, 0, -0xC0, -0x80},
                    {-0xC, 0xC, 0x100, -0x200, 0x100}, {-4, -0xC, 0x100, -0x100, -0x80},
                    {8, 8, 0x100, -0xC0, 0x60}, {0, 0, 0x100, -0x140, 0x80}}};
    private static final int ASTEROID_PALETTE = 1;

    private int subtype;
    /** 16.16 {@code x_pos}/{@code y_pos}. */
    private int xPos;
    private int yPos;
    private short xVel;
    private boolean initialized;


    public DdzAsteroidObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DDZAsteroid", null);
        subtype = spawn.subtype() & 0xFF;
        xPos = (spawn.x() & 0xFFFF) << 16;
        yPos = (spawn.y() & 0xFFFF) << 16;
    }

    /** A split child: {@code move.l #Obj_DDZAsteroid,(a1)} with a new subtype and position. */
    DdzAsteroidObjectInstance(int x, int y, int subtype) {
        this(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, subtype & 0xFF, 0, false, 0));
    }

    @Override
    public DdzAsteroidObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzAsteroidObjectInstance(ctx.spawn());
    }

    private int size() {
        return Math.min((subtype >> 4) & 0xF, 2);
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialized) {
            // sub_83146
            initialized = true;
            xVel = (short) X_VELOCITIES[Math.min(subtype & 0xF, X_VELOCITIES.length - 1)];
        }
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        // loc_821FC: tst.w (Debug_placement_mode).w / MoveSprite2
        if (player == null || !player.isDebugMode()) {
            xPos += xVel << 8;
        }
        xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
        if (player != null && player.isSuperSonic()
                && DdzObjectSupport.inMyRange(getX(), getY(), player.getCentreX(), player.getCentreY(),
                HIT_BOXES[size()])) {
            shatter(player);
            return;
        }
        if (outOfRangeX(getX())) {
            deleteClearingRespawn();
        }
    }

    /** {@code loc_8222A}. */
    private void shatter(AbstractPlayableSprite player) {
        services().playSfx(Sonic3kSfx.COLLAPSE.id);
        DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
        if (controller != null) {
            controller.setXVelocity(-0x400);
        }
        DdzZoneRuntimeState ddz = DdzObjectSupport.ddz(services());
        if (ddz != null) {
            int speed = ddz.scrollSpeed() - 0x10000;
            // cmp.l d1,d0 / bhs.s: unsigned floor at $10000.
            ddz.setScrollSpeed(Integer.compareUnsigned(speed, 0x10000) >= 0 ? speed : 0x10000);
        }
        int x = getX();
        int y = getY();
        boolean allocationFailed = false;
        if ((subtype & 0xF0) == 0x20) {
            int layout = splitLayout(y, player.getCentreY());
            int childSubtype = (subtype & 0x0F) + 0x10;
            for (int[] offset : SPLIT_OFFSETS[layout]) {
                int cx = x + offset[0];
                int cy = y + offset[1];
                if (spawnFreeChild(() -> new DdzAsteroidObjectInstance(cx, cy, childSubtype)) == null) {
                    allocationFailed = true;
                    break;
                }
            }
            if (!allocationFailed) {
                x += SPLIT_SHIFT[layout][0];
                y += SPLIT_SHIFT[layout][1];
            }
        }
        if (!allocationFailed) {
            int[][] pieces = DEBRIS[(subtype & 0x10) != 0 ? 1 : 0];
            for (int[] piece : pieces) {
                int px = x + piece[0];
                int py = y + piece[1];
                if (spawnFreeChild(() -> new DdzAsteroidDebrisObjectInstance(px, py, piece[2] >> 8,
                        piece[3], piece[4])) == null) {
                    break;
                }
            }
        }
        // loc_82334: bclr #7 on the respawn entry, then Delete_Current_Sprite.
        deleteClearingRespawn();
    }

    /**
     * {@code sub.w (Player_1+y_pos).w,d2 / smi d3}: layout 0 when Player 1 is within {@code $10}
     * pixels of the asteroid's height, 1 when the asteroid is lower, 2 when it is higher.
     */
    static int splitLayout(int asteroidY, int playerY) {
        short delta = (short) (asteroidY - playerY);
        boolean asteroidHigher = delta < 0;
        int distance = asteroidHigher ? -delta : delta;
        if ((distance & 0xFFFF) < 0x10) {
            return 0;
        }
        return asteroidHigher ? 2 : 1;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return SIZE_ATTRIBUTES[size()][0];
    }

    @Override
    public int getOnScreenHalfHeight() {
        return SIZE_ATTRIBUTES[size()][1];
    }

    /** {@code make_art_tile(ArtTile_DDZMisc,1,1)}. */
    @Override
    public boolean isHighPriority() {
        return true;
    }

    /** {@code move.w #0,priority(a0)}. */
    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(SIZE_ATTRIBUTES[size()][2], getX(), getY(), false, false, ASTEROID_PALETTE);
        }
    }


}
