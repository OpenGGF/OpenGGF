package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** SKL {@code $4E}, {@code Obj_DEZLiftPad} (sonic3k.asm:93132-93357). */
public final class S3kDezLiftPadObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int SOLID_HALF_WIDTH = 0x18;
    private static final int SOLID_HEIGHT = 9;

    private int originX;
    private int originY;
    private int platformX;
    private int platformY;
    private int motionAngle;
    private int angularVelocity;
    private int releaseDelay;
    private boolean active;
    private boolean decelerating;
    private boolean childSlotReserved;
    private int pointCount;
    private int[] pointX;
    private int[] pointY;

    public S3kDezLiftPadObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZLiftPad");
        originX = spawn.x();
        originY = spawn.y();
        pointCount = spawn.subtype() & 0x0F;
        pointX = new int[pointCount];
        pointY = new int[pointCount];
        updatePositions();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        reserveChildSlot();
        boolean p1Standing = playerEntity != null && tryServices() != null
                && services().objectManager() != null
                && services().objectManager().hasObjectStandingBit(playerEntity, this);
        updateOscillator(p1Standing);
        updatePositions();
        if (!isInRangeAt(originX)) {
            setDestroyedByOffscreen();
        }
    }

    void updateOscillator(boolean p1Standing) {
        if (releaseDelay != 0) {
            if (!p1Standing) {
                releaseDelay--;
            }
            return;
        }
        if (!active) {
            if (!p1Standing) {
                return;
            }
            active = true;
            if (tryServices() != null) {
                services().playSfx(Sonic3kSfx.GRAVITY_LIFT.id);
            }
        }

        if (!decelerating) {
            angularVelocity = (short) (angularVelocity + 8);
            motionAngle = (short) (motionAngle + angularVelocity);
            if (angularVelocity == 0) {
                active = false;
            }
            if ((motionAngle & 0xFF) >= 0x20) {
                decelerating = true;
            }
        } else {
            angularVelocity = (short) (angularVelocity - 8);
            motionAngle = (short) (motionAngle + angularVelocity);
            if (angularVelocity == 0) {
                releaseDelay = 30;
            }
            if ((motionAngle & 0xFF) < 0x20) {
                decelerating = false;
            }
        }
    }

    private void updatePositions() {
        int angle = motionAngle & 0xFF;
        if ((spawn.subtype() & 0x10) != 0) {
            angle = (short) -angle;
            if ((spawn.subtype() & 0x20) != 0) {
                angle = (short) -angle + 0xC0;
            }
        } else if ((spawn.subtype() & 0x20) != 0) {
            angle = (short) -angle + 0x40;
        }
        angle = (short) angle + 0x80;
        if ((spawn.renderFlags() & 1) != 0) {
            angle = (short) -angle + 0x80;
        }

        int sinStep = TrigLookupTable.sinHex(angle & 0xFF) << 12;
        int cosStep = TrigLookupTable.cosHex(angle & 0xFF) << 12;
        int accumX = originX << 16;
        int accumY = originY << 16;
        for (int i = 0; i < pointCount; i++) {
            accumX += cosStep;
            accumY += sinStep;
            pointX[i] = accumX >> 16;
            pointY[i] = accumY >> 16;
        }
        accumX += cosStep;
        accumY += sinStep;
        platformX = (accumX >> 16) - 0x20 + ((spawn.renderFlags() & 1) != 0 ? 0x40 : 0);
        platformY = accumY >> 16;
    }

    private void reserveChildSlot() {
        if (childSlotReserved || getSlotIndex() < 0) {
            return;
        }
        childSlotReserved = true;
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().allocateChildSlotsAfter(spawn, 1, getSlotIndex());
        }
    }

    @Override public int getReservedChildSlotCount() { return 1; }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT, SOLID_HEIGHT); }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean rejectsZeroDistanceTopSolidLanding() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_LIFT_PAD);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(1, originX, originY, false, false);
        for (int i = 0; i < pointCount; i++) {
            renderer.drawFrameIndex(i == 0 ? 2 : 1, pointX[i], pointY[i], false, false);
        }
        renderer.drawFrameIndex(0, platformX, platformY,
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }

    @Override public int getX() { return platformX; }
    @Override public int getY() { return platformY; }
    @Override public int getOutOfRangeReferenceX() { return originX; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int romObjectCodePointerHighWord() { return 4; }

    int motionAngleForTest() { return motionAngle & 0xFFFF; }
    int angularVelocityForTest() { return (short) angularVelocity; }
    int releaseDelayForTest() { return releaseDelay; }
    int pointCountForTest() { return pointCount; }
    void updatePositionsForTest() { updatePositions(); }
}
