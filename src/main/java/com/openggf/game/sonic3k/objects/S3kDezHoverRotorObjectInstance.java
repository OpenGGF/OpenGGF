package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Parentless hover-machine sibling, loc_494EA / sub_4952A (sonic3k.asm:95736-95805). */
public final class S3kDezHoverRotorObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int anchorX;
    private int currentX;
    private int angle;
    private int priorityBucket = 5;

    public S3kDezHoverRotorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZHoverRotor");
        anchorX = spawn.x();
        currentX = (anchorX + 0x20) & 0xFFFF;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        // Both sprite order and cosine consume the OLD byte before addq.b #2.
        int previousAngle = angle;
        priorityBucket = (previousAngle & 0x80) == 0 ? 4 : 6;
        angle = (angle + 2) & 0xFF;
        currentX = (anchorX + (TrigLookupTable.cosHex(previousAngle) >> 3)) & 0xFFFF;
        updateDynamicSpawn(currentX, getY());
        var query = services().playerQuery();
        PlayableEntity main = query.mainPlayerOrNull();
        if (main == null) main = player;
        lift(main);
        PlayableEntity second = query.nativeP2OrNull();
        if (second != main) lift(second);
    }

    private void lift(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        int dx = (player.getCentreX() - getX() + 0x40) & 0xFFFF;
        if (dx >= 0x80) return;
        // GetSineCosine here consumes the horizontal contact offset, NOT the
        // rotor's orbit phase. This forms the semicircular lift envelope.
        int height = (TrigLookupTable.sinHex(dx) >> 2) + 0x20;
        int dy = (player.getCentreY() - getY() + height) & 0xFFFF;
        if (dy >= height + 0x20 || player.isHurt() || player.getDead() || player.isObjectControlled()) return;
        // sub.w height / bcs retains negative distances above the centre.
        // At or below it, NOT.W then ADD.W doubles the one's complement.
        dy -= height;
        if (dy >= 0) dy = (short) (~dy * 2);
        int shift = (short) -(dy + height) >> 4;
        NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() + shift);
        player.setAir(true);
        player.setRollingJump(false);
        player.setYSpeed((short) 0);
        player.setDoubleJumpFlag(0);
        player.setJumping(false);
        player.setGSpeed((short) 1);
        // The ROM reads Level_frame_counter, independently for each player.
        if ((services().levelManager().getFrameCounter() & 0xF) == 0)
            services().playSfx(Sonic3kSfx.MAGNETIC_SPIKE.id);
        if (player.getFlipAngle() == 0) {
            player.setFlipAngle(1);
            player.setAnimationId(0);
            player.setFlipsRemaining(0x7F);
            player.setFlipSpeed(8);
        }
    }
    @Override public int getX() { return currentX; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(anchorX, cameraX, coarseXCullRange());
    }
    @Override public int getOnScreenHalfWidth() { return 16; }
    @Override public int getOnScreenHalfHeight() { return 16; }
    @Override public int getPriorityBucket() { return priorityBucket; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_HOVER_MACHINE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(2, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
    public int angleForTest() { return angle; }
}
