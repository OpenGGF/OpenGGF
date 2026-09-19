package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** SKL {@code $53}, {@code Obj_DEZConveyorPad} (sonic3k.asm:93696-93850). */
public final class S3kDezConveyorPadObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable,
        RomObjectCodePointerProvider {
    private int xFixed, yFixed;
    private int travelLeft;
    private int verticalStep;
    private int animation;
    private int animationTimer;
    private boolean started;
    private int floorState;
    private boolean p1Standing, p2Standing;
    private boolean previousStanding;

    public S3kDezConveyorPadObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZConveyorPad");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
        travelLeft = (spawn.subtype() & 0x7F) << 3;
        verticalStep = (spawn.subtype() & 0x80) == 0 ? 1 : -1;
        animation = 2;
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        boolean standing = p1Standing || p2Standing;
        boolean justStarted = false;
        if (standing && !started) {
            started = true;
            animation = spawn.renderFlags() & 1;
            justStarted = true;
        }
        // loc_479F0 changes the routine and branches straight to animation/
        // SolidObjectFull. loc_47A14 movement and rider carry start on the
        // following object pass, after the standing bit was first observed.
        if (started && !justStarted) {
            if (travelLeft != 0) {
                travelLeft--;
                yFixed += verticalStep << 16;
            } else if ((spawn.subtype() & 0x7F) == 0) {
                updateTerrainFollowingPad();
            }
            carryRiders();
            // loc_47A38 stores the current standing mask only after carrying,
            // then reverses anim on a newly-set standing bit. The first carry
            // therefore uses the placement direction; the next pass uses the
            // reversed belt direction.
            if (standing && !previousStanding) {
                animation ^= 1;
            }
            previousStanding = standing;
        }
        animate();
        if (isOnScreen(0) && (levelFrame(vIntRunCount) & 0xF) == 0 && tryServices() != null) {
            services().playSfx(Sonic3kSfx.CONVEYOR_PLATFORM.id);
        }
    }

    private void updateTerrainFollowingPad() {
        int direction = animation == 0 ? 1 : -1;
        xFixed += direction << 16;
        TerrainCheckResult wall = direction > 0
                ? ObjectTerrainUtils.checkRightWallDist(getX() + 0x40, getY())
                : ObjectTerrainUtils.checkLeftWallDist(getX() - 0x40, getY());
        if (wall.distance() < 0) {
            xFixed += (direction > 0 ? wall.distance() : -wall.distance()) << 16;
            animation ^= 1;
        }
        if (tryServices() == null || services().levelManager() == null) return;
        if (floorState == 0) yFixed += verticalStep << 16;
        int left = floorDistance(getX() - 0x30);
        int right = floorDistance(getX() + 0x30);
        int distance = Math.min(left, right);
        if (distance < 0 || distance <= 0x0E) {
            yFixed += distance << 16;
            floorState = 1;
        } else {
            floorState = 2;
        }
    }

    private int floorDistance(int x) {
        return ObjectTerrainUtils.checkFloorDist(services().levelManager(),
                services().backgroundPlaneCollisionProvider(), false,
                x & 0xFFFF, (getY() + 0x0F) & 0xFFFF).distance();
    }

    private void carryRiders() {
        if (tryServices() == null || services().playerQuery() == null) return;
        List<PlayableEntity> players = services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        for (int i = 0; i < players.size(); i++) {
            if ((i == 0 ? p1Standing : p2Standing) && players.get(i) instanceof AbstractPlayableSprite sprite) {
                int step = animation == 0 ? 2 : -2;
                NativePositionOps.writeXPosPreserveSubpixel(sprite, sprite.getCentreX() + step);
            }
        }
    }

    private void animate() {
        if (++animationTimer < 2) return;
        animationTimer = 0;
        int frame = mappingFrameForTest();
        frame += animation == 0 ? 1 : -1;
        if (frame < 1) frame = 3;
        if (frame > 3) frame = 1;
        setMappingFrameForTest(frame);
    }

    private int mappingFrame = 1;
    int mappingFrameForTest() { return mappingFrame; }
    void setMappingFrameForTest(int value) { mappingFrame = value; }
    private int levelFrame(int fallback) {
        return tryServices() != null && services().levelManager() != null
                ? services().levelManager().getFrameCounter() : fallback;
    }

    @Override public SolidObjectParams getSolidParams() {
        int width = (spawn.subtype() & 0x7F) == 0 ? 0x40 : 0x80;
        return SolidObjectParams.of(width + 0x0B, 0x10, 0x11);
    }
    @Override public void onSolidContact(PlayableEntity p, SolidContact c, int f) {
        if (p.isCpuControlled()) p2Standing = c.standing(); else p1Standing = c.standing();
    }
    @Override public void onSolidContactCleared(PlayableEntity p, int f) {
        if (p.isCpuControlled()) p2Standing = false; else p1Standing = false;
    }
    @Override public int getX() { return xFixed >> 16; }
    @Override public int getY() { return yFixed >> 16; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public int getOnScreenHalfWidth() { return (spawn.subtype() & 0x7F) == 0 ? 0x40 : 0x80; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        boolean wide = (spawn.subtype() & 0x7F) != 0;
        PatternSpriteRenderer renderer = getRenderer(wide ? Sonic3kObjectArtKeys.DEZ_CONVEYOR_PAD_WIDE : Sonic3kObjectArtKeys.DEZ_CONVEYOR_PAD);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
    int travelLeftForTest() { return travelLeft; }
    void setStandingForTest(boolean standing) { p1Standing = standing; }
    int animationForTest() { return animation; }
}
