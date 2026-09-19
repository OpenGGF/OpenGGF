package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.List;

/**
 * LRZ2 {@code Obj_LRZChainedPlatforms} (sonic3k.asm:97180-97360).
 *
 * <p>Placed subtypes {@code $80-$82} are compact parent records. They expand the ROM table at
 * {@code off_4A914} into four, four or eight independently solid platforms. Each child then
 * follows one of {@code word_4A896/4A8C0/4A8EA}'s ten waypoints at at most one pixel per frame,
 * preserving the proportional 8.8 velocity on the minor axis.
 */
public final class LrzChainedPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {
    private static final int[][][] PATHS = {
            {{0, 0}, {-0x16, 0x0A}, {-0x20, 0x20}, {-0x20, 0xC0}, {-0x16, 0xD6},
                    {0, 0xE0}, {0x16, 0xD6}, {0x20, 0xC0}, {0x20, 0x20}, {0x16, 0x0A}},
            {{0, 0}, {-0x16, 0x0A}, {-0x20, 0x20}, {-0x20, 0x140}, {-0x16, 0x156},
                    {0, 0x160}, {0x16, 0x156}, {0x20, 0x140}, {0x20, 0x20}, {0x16, 0x0A}},
            {{0, 0}, {-0x16, 0x0A}, {-0x20, 0x20}, {-0x20, 0x1C0}, {-0x16, 0x1D6},
                    {0, 0x1E0}, {0x16, 0x1D6}, {0x20, 0x1C0}, {0x20, 0x20}, {0x16, 0x0A}}
    };
    /** {@code off_4A914}: x, y, child subtype. */
    private static final int[][][] GROUPS = {
            {{0, 0, 0x01}, {-0x20, 0x70, 0x03}, {0, 0xE0, 0x06}, {0x20, 0x70, 0x08}},
            {{0, 0, 0x11}, {-0x20, 0xB0, 0x13}, {0, 0x160, 0x16}, {0x20, 0xB0, 0x18}},
            {{0, 0, 0x21}, {-0x20, 0x72, 0x23}, {-0x20, 0xF0, 0x23},
                    {-0x20, 0x16E, 0x23}, {0, 0x1E0, 0x26}, {0x20, 0x16E, 0x28},
                    {0x20, 0xF0, 0x28}, {0x20, 0x72, 0x28}}
    };

    private boolean initialized;
    private int baseX;
    private int baseY;
    private int path;
    private int waypoint;
    private int step;
    private int xFixed;
    private int yFixed;
    private int targetX;
    private int targetY;
    private int xVelocity;
    private int yVelocity;

    private record RewindExtra(boolean initialized, int baseX, int baseY, int path, int waypoint,
                               int step, int xFixed, int yFixed, int targetX, int targetY,
                               int xVelocity, int yVelocity)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public LrzChainedPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZChainedPlatforms");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialize();
        }
        moveTowardTarget();
    }

    private void initialize() {
        initialized = true;
        baseX = spawn.x();
        baseY = spawn.y();
        int subtype = spawn.subtype() & 0xFF;
        if ((subtype & 0x80) != 0) {
            int group = Math.min(subtype & 0x7F, GROUPS.length - 1);
            int[][] rows = GROUPS[group];
            configure(rows[0][0], rows[0][1], rows[0][2]);
            for (int i = 1; i < rows.length; i++) {
                int[] row = rows[i];
                spawnAfterCurrentSibling(() -> new LrzChainedPlatformObjectInstance(
                        new ObjectSpawn(baseX + row[0], baseY + row[1], spawn.objectId(), row[2],
                                spawn.renderFlags(), false, spawn.rawYWord())));
            }
            return;
        }
        configure(0, 0, subtype);
    }

    private void configure(int offsetX, int offsetY, int subtype) {
        xFixed = (baseX + offsetX) << 16;
        yFixed = (baseY + offsetY) << 16;
        path = Math.min((subtype >>> 4) & 3, PATHS.length - 1);
        waypoint = subtype & 0x0F;
        step = (spawn.renderFlags() & 1) == 0 ? 1 : -1;
        if (step < 0) {
            waypoint = normalizeWaypoint(waypoint + step);
        }
        selectTarget();
    }

    private void moveTowardTarget() {
        if ((xFixed >> 16) == targetX && (yFixed >> 16) == targetY) {
            waypoint = normalizeWaypoint(waypoint + step);
            selectTarget();
        }
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
    }

    private void selectTarget() {
        int[] point = PATHS[path][waypoint];
        targetX = baseX + point[0];
        targetY = baseY + point[1];
        int dx = targetX - (xFixed >> 16);
        int dy = targetY - (yFixed >> 16);
        int absX = Math.abs(dx);
        int absY = Math.abs(dy);
        if (absY >= absX) {
            yVelocity = Integer.compare(dy, 0) * 0x100;
            xVelocity = absY == 0 ? 0 : (-dx * 0x100) / -absY;
        } else {
            xVelocity = Integer.compare(dx, 0) * 0x100;
            yVelocity = absX == 0 ? 0 : (-dy * 0x100) / -absX;
        }
    }

    private static int normalizeWaypoint(int value) {
        int count = PATHS[0].length;
        int normalized = value % count;
        return normalized < 0 ? normalized + count : normalized;
    }

    @Override public int getX() { return xFixed >> 16; }
    @Override public int getY() { return yFixed >> 16; }
    @Override public int getOutOfRangeReferenceX() { return baseX; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public int getPriorityBucket() { return 4; } // priority $200
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x23, 0x18, 0x19); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.LRZ2_CHAINED_PLATFORM);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, getX(), getY(), false, false);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                initialized, baseX, baseY, path, waypoint, step, xFixed, yFixed,
                targetX, targetY, xVelocity, yVelocity));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            initialized = extra.initialized();
            baseX = extra.baseX();
            baseY = extra.baseY();
            path = extra.path();
            waypoint = extra.waypoint();
            step = extra.step();
            xFixed = extra.xFixed();
            yFixed = extra.yFixed();
            targetX = extra.targetX();
            targetY = extra.targetY();
            xVelocity = extra.xVelocity();
            yVelocity = extra.yVelocity();
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new LrzChainedPlatformObjectInstance(context.spawn());
    }

    int pathForTest() { return path; }
    int waypointForTest() { return waypoint; }
    int xVelocityForTest() { return xVelocity; }
    int yVelocityForTest() { return yVelocity; }
}
