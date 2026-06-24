package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * S3K SKL object $09 - MHZ curled vine.
 *
 * <p>ROM reference: {@code Obj_MHZCurledVine}. This ports the display-child
 * footprint and the initial top-solid range used before rider pressure uncurls
 * the generated segment surface.
 */
public final class MhzCurledVineObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {
    private static final int INITIAL_CURVE_STATE = 0xFFF40000;
    private static final int INITIAL_RANGE_WIDTH = 0x40;
    private static final int DISPLAY_HALF_HEIGHT = 0x30;
    private static final int SEGMENT_COUNT = 8;
    private static final int INITIAL_SEGMENT_X_OFFSET = 0x38;
    private static final int PRIORITY_BUCKET = 5;
    private static final int[] CURVE_TARGETS = {
            0xFFF40000, 0xFFFA0000, 0xFFFB0000, 0xFFFC0000,
            0xFFFD0000, 0xFFFE0000, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000
    };
    private static final int[] RANGE_WIDTHS = {
            0x40, 0x40, 0x40, 0x40, 0x50, 0x60, 0x70, 0x80, 0x80
    };

    private final Map<AbstractPlayableSprite, Integer> standingSegmentIndices = new IdentityHashMap<>();
    private final int[] segmentXs = new int[SEGMENT_COUNT];
    private final int[] segmentYs = new int[SEGMENT_COUNT];
    private boolean hFlip;
    private int curveState = INITIAL_CURVE_STATE;
    private int rangeWidth = INITIAL_RANGE_WIDTH;

    public MhzCurledVineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MHZCurledVine");
        hFlip = (spawn.renderFlags() & 0x01) != 0;
        updateSegmentPositions();
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        int tableIndex = selectStandingTableIndex();
        rangeWidth = RANGE_WIDTHS[tableIndex];
        curveState = approachCurveState(curveState, CURVE_TARGETS[tableIndex]);
        updateSegmentPositions();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(rangeWidth / 2, 0, 0);
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing() || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        int relativeX = hFlip ? spawn.x() - sprite.getCentreX() : sprite.getCentreX() - spawn.x();
        int segmentIndex = (relativeX + INITIAL_RANGE_WIDTH) >> 4;
        standingSegmentIndices.put(sprite, clamp(segmentIndex, 0, 7));
    }

    @Override
    public void onSolidContactCleared(PlayableEntity player, int frameCounter) {
        if (player instanceof AbstractPlayableSprite sprite) {
            standingSegmentIndices.remove(sprite);
        }
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean usesCollisionHalfWidthForTopLanding() {
        return true;
    }

    @Override
    public boolean usesPlatformObjectLandingSnap() {
        return false;
    }

    @Override
    public boolean usesInstanceSolidStateLatchKey() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return rangeWidth;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return DISPLAY_HALF_HEIGHT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MHZ_CURLED_VINE);
        if (renderer != null) {
            for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
                renderer.drawFrameIndex(0, segmentXs[segment], segmentYs[segment], hFlip, false);
            }
        }
    }

    @Override
    public String traceDebugDetails() {
        return super.traceDebugDetails()
                + " curve=$" + Integer.toHexString(curveState).toUpperCase()
                + " range=$" + Integer.toHexString(rangeWidth).toUpperCase()
                + " hflip=" + hFlip;
    }

    private void updateSegmentPositions() {
        int curveStep = curveState;
        int halfStep = curveStep >> 1;
        int x = spawn.x() + (hFlip ? INITIAL_SEGMENT_X_OFFSET : -INITIAL_SEGMENT_X_OFFSET);
        int y = spawn.y();
        int phase = 0x000A0000;
        int xDirection = hFlip ? -1 : 1;

        for (int segment = 0; segment < SEGMENT_COUNT; segment++) {
            segmentXs[segment] = x;
            segmentYs[segment] = y;
            if (segment == SEGMENT_COUNT - 1) {
                break;
            }

            phase += curveStep;
            int angle = highWord(phase) & 0xFF;
            int sin = TrigLookupTable.sinHex(angle);
            int cos = TrigLookupTable.cosHex(angle);
            x += xDirection * (cos >> 4);
            y += sin >> 4;
            curveStep += halfStep;
        }
    }

    private static int approachCurveState(int current, int target) {
        if (current == target) {
            return current;
        }
        if (Integer.compareUnsigned(current, target) > 0) {
            return current - 0x10000;
        }
        return current + 0x10000;
    }

    private int selectStandingTableIndex() {
        if (standingSegmentIndices.isEmpty()) {
            return 0;
        }
        int minimumSegment = Integer.MAX_VALUE;
        for (int segmentIndex : standingSegmentIndices.values()) {
            minimumSegment = Math.min(minimumSegment, segmentIndex);
        }
        return Math.min(minimumSegment + 1, RANGE_WIDTHS.length - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int highWord(int value) {
        return (short) (value >> 16);
    }
}
