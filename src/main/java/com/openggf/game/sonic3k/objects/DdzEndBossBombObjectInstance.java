package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_825CA} (sonic3k.asm:174588-174655): a phase-2 bomb dropped when Player 1 is below
 * the ship ({@code ObjDat3_83244}: priority {@code $300}, frame {@code $25}). It leaves the ship at
 * {@code + ($52, $5A)} moving {@code ($200, $200)} for five frames, then waits
 * {@code subtype * 8} frames at priority {@code $80} emitting {@code loc_826A0} smoke every eighth
 * V-int, then accelerates left by {@code $40} and upward by {@code $20} (never past zero). It follows
 * the wrap offset and the camera delta, hits Player 1 through {@code sub_82C28}, and is deleted when
 * the boss's {@code status} bit 7 is set or it leaves the screen.
 */
final class DdzEndBossBombObjectInstance extends AbstractDdzObjectInstance {
    private static final int PALETTE = 2;

    private final int subtype;
    private int xPos;
    private int yPos;
    private short xVel = 0x200;
    private short yVel = 0x200;
    private int timer = 4;
    private boolean released;
    private boolean highPriority;
    private boolean exploding;
    private int explodeWait;


    DdzEndBossBombObjectInstance(DdzEndBossObjectInstance boss, int subtype) {
        super(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, subtype, 0, false, 0),
                "DDZEndBossBomb", boss);
        this.subtype = subtype;
        if (boss != null) {
            xPos = ((boss.getX() + 0x52) & 0xFFFF) << 16;
            yPos = ((boss.getY() + 0x5A) & 0xFFFF) << 16;
        }
    }

    @Override
    public DdzEndBossBombObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossBombObjectInstance(null, ctx.spawn().subtype());
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    private DdzEndBossObjectInstance boss() {
        return parent instanceof DdzEndBossObjectInstance boss ? boss : null;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        DdzEndBossObjectInstance boss = boss();
        if (exploding) {
            // Wait_Draw, then loc_85088.
            explodeWait = (short) (explodeWait - 1);
            if (explodeWait < 0) {
                goDeleteClearingRespawn();
            }
            return;
        }
        xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
        timer = (short) (timer - 1);
        if (!released) {
            // loc_82606
            if (timer < 0) {
                released = true;
                highPriority = true;
                timer = subtype << 3;
            }
            move();
            if (boss == null || boss.destroyedStatus()) {
                // loc_83038: Delete_Current_Sprite.
                deleteNow();
                return;
            }
            checkDelete();
            return;
        }
        // loc_82652
        if (timer >= 0) {
            if ((vIntRunCount & 7) == 0) {
                int x = getX();
                int y = getY();
                spawnChild(() -> new DdzMissilePuffObjectInstance(x - DdzObjectSupport.wrapOffset(services()),
                        y, -0x200, -0x200));
            }
        } else {
            xVel = (short) (xVel - 0x40);
            int nextY = (short) (yVel - 0x20);
            if (nextY >= 0) {
                yVel = (short) nextY;
            }
        }
        move();
        if (DdzEndBossHitSupport.hitPlayer(services(), getX(), getY())
                || (DdzObjectSupport.playerPowered(services()) && (boss == null || boss.destroyedStatus()))) {
            explode();
            return;
        }
        checkDelete();
    }

    private void move() {
        xPos += xVel << 8;
        yPos += yVel << 8;
        xPos += DdzObjectSupport.cameraDelta(services()) << 16;
    }

    private void checkDelete() {
        // Sprite_CheckDeleteXY
        if (DdzObjectSupport.outOfRangeX(services(), getX()) || DdzObjectSupport.outOfRangeY(services(), getY())) {
            goDelete();
        }
    }

    private void explode() {
        exploding = true;
        explodeWait = 3;
        int x = getX();
        int y = getY();
        spawnChild(() -> new DdzCreateBossExplosionObjectInstance(x, y, 6, this));
    }

    @Override
    public boolean isHighPriority() {
        return highPriority;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(released ? 0x80 : 0x300);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(0x25, getX(), getY(), false, false, PALETTE);
        }
    }


}
