package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
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
 * ROM {@code loc_81F14} (sonic3k.asm:174022-174030) with {@code sub_82CA4} and {@code sub_82BE4}: a
 * turret shot ({@code word_831C6}: priority {@code $280}, frame {@code $24}) moving at
 * {@code word_82CB6[direction]} ({@code $400} along eight directions). While Player 1 is powered a
 * shot touching an unhurt Player 1 ({@code word_82C20}) sets {@code invulnerability_timer = 59},
 * plays {@code sfx_Explode} and deletes; it also deletes when the boss's {@code status} bit 7 is set,
 * and outside {@code Sprite_CheckDeleteXY}.
 */
final class DdzEndBossTurretShotObjectInstance extends AbstractDdzObjectInstance {
    /** {@code word_82CB6}. */
    private static final int[][] VELOCITIES = {
            {0, 0x400}, {0x2D4, 0x2D4}, {0x400, 0}, {0x2D4, -0x2D4},
            {0, -0x400}, {-0x2D4, -0x2D4}, {-0x400, 0}, {-0x2D4, 0x2D4}};
    /** {@code word_82C20}. */
    private static final int[] PLAYER_BOX = {-0x10, 0x20, -0x10, 0x20};
    private static final int PALETTE = 2;

    private int xPos;
    private int yPos;
    private final short xVel;
    private final short yVel;


    DdzEndBossTurretShotObjectInstance(DdzEndBossTurretObjectInstance turret, int x, int y, int direction) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, direction, 0, false, 0), "DDZEndBossTurretShot", turret);
        xPos = (x & 0xFFFF) << 16;
        yPos = (y & 0xFFFF) << 16;
        xVel = (short) VELOCITIES[direction >> 1][0];
        yVel = (short) VELOCITIES[direction >> 1][1];
    }

    @Override
    public DdzEndBossTurretShotObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossTurretShotObjectInstance(null, ctx.spawn().x(), ctx.spawn().y(), ctx.spawn().subtype());
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
        xPos += xVel << 8;
        yPos += yVel << 8;
        if (DdzObjectSupport.playerPowered(services())) {
            // sub_82BE4: the turret's status bit 7 deletes the shot.
            if (!(parent instanceof DdzEndBossTurretObjectInstance turret) || turret.released()) {
                goDelete();
                return;
            }
            AbstractPlayableSprite player = DdzObjectSupport.player(services());
            if (player != null && player.getInvulnerableFrames() == 0
                    && DdzObjectSupport.inMyRange(getX(), getY(), player.getCentreX(), player.getCentreY(), PLAYER_BOX)) {
                player.setInvulnerableFrames(60 - 1);
                services().playSfx(Sonic3kSfx.EXPLODE.id);
                goDelete();
                return;
            }
        }
        if (DdzObjectSupport.outOfRangeX(services(), getX()) || DdzObjectSupport.outOfRangeY(services(), getY())) {
            goDelete();
        }
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(0x24, getX(), getY(), false, false, PALETTE);
        }
    }


}
