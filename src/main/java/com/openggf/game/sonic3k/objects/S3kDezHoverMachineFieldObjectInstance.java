package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** The oscillating field child at ROM {@code loc_494EA}. */
public final class S3kDezHoverMachineFieldObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int angle;
    private int baseX;
    private int priorityWord = 0x200;

    public S3kDezHoverMachineFieldObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZHoverMachineField");
        baseX = ((spawn.x() & 0xFFFF) - 0x20) & 0xFFFF;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        priorityWord = (angle & 0x80) == 0 ? 0x200 : 0x300;
        angle = (angle + 2) & 0xFF;
        int x = (baseX + (TrigLookupTable.cosHex(angle) >> 3)) & 0xFFFF;
        updateDynamicSpawn(x, getY());

        if (tryServices() == null || services().playerQuery() == null) {
            return;
        }
        for (PlayableEntity candidate : services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (candidate instanceof AbstractPlayableSprite player) {
                lift(player);
            }
        }
    }

    private void lift(AbstractPlayableSprite player) {
        int dx = (player.getCentreX() - getX() + 0x40) & 0xFFFF;
        if (dx >= 0x80 || player.getDead() || player.isObjectControlled()) {
            return;
        }
        // sub_4952A leaves the biased relative X in d0 for GetSineCosine. The field shape is
        // therefore a horizontal sine arch; it is not keyed by the child's orbit angle.
        int wave = (TrigLookupTable.sinHex(dx & 0xFF) >> 2) + 0x20;
        int relative = player.getCentreY() - getY();
        int biased = (relative + wave) & 0xFFFF;
        if (biased >= wave + 0x20) {
            return;
        }
        // sub_4952A keeps d1=relative after `sub.w d0,d1` when that subtraction
        // borrows (the player is above the field), then rejoins at `add.w d0,d1`.
        // The non-borrow path alone applies not.w/add.w. Preserve the signed-word
        // branch: dropping relative from the borrow path makes the arch pull upward
        // too quickly as its wave crosses an ASR boundary.
        int correctionWord = relative < 0 ? wave + relative : wave + (~relative << 1);
        // 68000 order matters for negative values: neg.w happens before asr.w.
        int correction = (short) -(short) correctionWord;
        correction >>= 4;
        NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() + correction);
        player.setAir(true);
        player.setRollingJump(false);
        player.setYSpeed((short) 0);
        player.setDoubleJumpFlag(0);
        player.setJumping(false);
        player.setGSpeed((short) 1);
        if ((levelFrameCounter() & 0xF) == 0) {
            services().playSfx(Sonic3kSfx.MAGNETIC_SPIKE.id);
        }
        if ((player.getFlipAngle() & 0xFF) == 0) {
            player.setFlipAngle(1);
            player.setAnimationId(0);
            player.setFlipsRemaining(0x7F);
            player.setFlipSpeed(8);
        }
    }

    private int levelFrameCounter() {
        return services().levelManager() == null ? 0 : services().levelManager().getFrameCounter();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_HOVER_MACHINE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(2, getX(), getY(),
                    (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
        }
    }

    @Override public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(priorityWord);
    }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    int angleForTest() { return angle; }
    int baseXForTest() { return baseX; }
    void liftForTest(AbstractPlayableSprite player) { lift(player); }

    /**
     * The ROM's after-current child first executes on the allocation pass. The engine's
     * dynamically inserted child receives an allocation update before the first routed
     * gameplay row as well, so seed one two-degree step behind to expose the same phase
     * to the first player-interaction pass.
     */
    void seedAfterCurrentAllocationPhase() { angle = 0xFE; }
}
