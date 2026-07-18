package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/** ROM child {@code loc_6E82C}, the released magnet head. */
final class CnzEndBossMagnetChild extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {
    @RewindTransient(reason = "Structural boss graph link recreated with the parent.")
    private final CnzEndBossInstance boss;
    private int centreX;
    private int centreY;
    private int ySubpixel;
    private int yVelocity;
    private int frame = 4;
    private boolean dropping;
    private boolean landed;
    private int animTimer;
    private int animIndex;
    private static final int[] ANIMATION = {4, 5, 4, 5, 4, 5, 4};

    CnzEndBossMagnetChild(CnzEndBossInstance boss) {
        super(new ObjectSpawn(boss.getCentreX(), boss.getCentreY() + 0x14, 0, 0, 0, false, 0),
                "CNZEndBossMagnet");
        this.boss = boss;
        resetForNextCycle();
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        CnzEndBossInstance restoredBoss = CnzEndBossRewindLinks.boss(ctx);
        if (restoredBoss == null) return null;
        CnzEndBossMagnetChild restored = new CnzEndBossMagnetChild(restoredBoss);
        restoredBoss.relinkMagnetChild(restored);
        return restored;
    }

    void beginDrop() {
        centreX = boss.getCentreX();
        centreY = boss.getCentreY() + 0x14;
        ySubpixel = 0;
        yVelocity = 0;
        dropping = true;
        landed = false;
        frame = 4;
    }

    void resetForNextCycle() {
        dropping = false;
        landed = false;
        centreX = boss.getCentreX();
        centreY = boss.getCentreY() + 0x14;
        ySubpixel = 0;
        yVelocity = 0;
        frame = 4;
    }

    boolean isLanded() { return landed; }

    @Override public int getX() { return centreX - 0x10; }
    @Override public int getY() { return centreY - 0x10; }
    public int getCentreX() { return centreX; }
    public int getCentreY() { return centreY; }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (boss.isDestroyed() || boss.defeatStarted()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        if (!dropping) {
            centreX = boss.getCentreX();
            centreY = boss.getCentreY() + 0x14;
        } else if (!landed) {
            yVelocity += 0x38;
            ySubpixel += yVelocity;
            centreY += ySubpixel >> 8;
            ySubpixel &= 0xFF;
            var floor = ObjectTerrainUtils.checkFloorDist(centreX, centreY, 0x10);
            if (floor.hasCollision()) {
                centreY += floor.distance();
                services().playSfx(Sonic3kSfx.FLOOR_THUMP.id);
                if (yVelocity >= 0x80) {
                    yVelocity = -(yVelocity >> 1);
                } else {
                    yVelocity = 0;
                    landed = true;
                }
            }
        }
        if (landed && boss.nativeRoutine().ordinal() >= CnzEndBossInstance.Routine.CHARGE.ordinal()) {
            if (--animTimer < 0) {
                animTimer = (animIndex == 4 ? 4 : 0);
                frame = ANIMATION[animIndex++ % ANIMATION.length];
            }
        }
        updateDynamicSpawn(getCentreX(), getCentreY());
    }

    @Override public int getCollisionFlags() { return dropping ? 0x8B : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getPriorityBucket() { return 5; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(frame, centreX, centreY, false, false);
        }
    }
}
