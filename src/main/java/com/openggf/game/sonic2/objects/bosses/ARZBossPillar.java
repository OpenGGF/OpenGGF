package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.SolidExecutionMode;

import java.util.List;

/**
 * ARZ Boss Pillar - Rising/lowering solid platform.
 * ROM Reference: s2.asm Obj89 (subtype 4)
 *
 * States:
 * - RAISING: Pillar rises from floor, screen shakes
 * - IDLE: Pillar at target height, spawns arrows when hammer hits
 * - LOWERING: Pillar sinks when boss defeated
 */
public class ARZBossPillar extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable {

    private static final int PILLAR_SUB_RAISING = 0;
    private static final int PILLAR_SUB_IDLE = 2;
    private static final int PILLAR_SUB_LOWERING = 4;

    private static final int LEFT_PILLAR_X = 0x2A50;
    private static final int RIGHT_PILLAR_X = 0x2B70;
    private static final int PILLAR_TARGET_Y = 0x488;
    private static final int PILLAR_START_Y = 0x510;

    private static final SolidObjectParams PILLAR_SOLID_PARAMS = new SolidObjectParams(0x23, 0x44, 0x45, 0, 4);
    private Sonic2ARZBossInstance mainBoss;

    private int x;
    private int y;
    private int renderFlags;
    private int routineSecondary;
    private int mappingFrame;

    private boolean pillarShaking;
    private int pillarShakeTime;

    public ARZBossPillar(ObjectSpawn spawn, Sonic2ARZBossInstance mainBoss) {
        super(spawn, "ARZ Boss Pillar");
        this.mainBoss = mainBoss;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.routineSecondary = PILLAR_SUB_RAISING;
        this.mappingFrame = 0;
        this.pillarShaking = false;
        this.pillarShakeTime = 0;
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        Sonic2ARZBossInstance parent = findClosestLiveParent(ctx);
        return parent == null ? null : new ARZBossPillar(ctx.spawn(), parent);
    }

    private static Sonic2ARZBossInstance findClosestLiveParent(RewindRecreateContext ctx) {
        if (ctx == null || ctx.spawn() == null || ctx.objectServices() == null
                || ctx.objectServices().objectManager() == null) {
            return null;
        }
        Sonic2ARZBossInstance best = null;
        long bestDistance = Long.MAX_VALUE;
        int childX = ctx.spawn().x();
        int childY = ctx.spawn().y();
        for (ObjectInstance inst : ctx.objectServices().objectManager().getActiveObjects()) {
            if (inst instanceof Sonic2ARZBossInstance boss && !boss.isDestroyed()) {
                long dx = (long) boss.getX() - childX;
                long dy = (long) boss.getY() - childY;
                long distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = boss;
                }
            }
        }
        return best;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        // Check if main boss is defeated
        if (mainBoss != null && mainBoss.isInDefeatSequence()) {
            routineSecondary = PILLAR_SUB_LOWERING;
        }

        switch (routineSecondary) {
            case PILLAR_SUB_RAISING -> updatePillarRaise(vIntRunCount);
            case PILLAR_SUB_IDLE -> updatePillarIdle(vIntRunCount);
            case PILLAR_SUB_LOWERING -> updatePillarLower();
        }
    }

    /**
     * Pillar raising state.
     * ROM: Obj89_Pillar_Sub0 (s2.asm:65451-65466) calls Obj89_Pillar_SolidObject
     * before it raises the pillar.
     */
    private void updatePillarRaise(int vIntRunCount) {
        checkpointAll();
        if ((vIntRunCount & 0x1F) == 0) {
            services().playSfx(Sonic2Sfx.RUMBLING_2.id);
        }
        y -= 1;
        if (y <= PILLAR_TARGET_Y) {
            y = PILLAR_TARGET_Y;
            routineSecondary = PILLAR_SUB_IDLE;
            // ROM: move.b #0,(Screen_Shaking_Flag).w - stop screen shaking
            services().gameState().setScreenShakeActive(false);
        }
        mappingFrame = 0;
    }

    /**
     * Pillar idle state.
     * ROM: Obj89_Pillar_Sub2 (s2.asm:65468-65497) calls Obj89_Pillar_SolidObject
     * before the hammer check and the shake move.
     */
    private void updatePillarIdle(int vIntRunCount) {
        checkpointAll();
        if (mainBoss != null && mainBoss.isHammerActive()) {
            boolean isLeftPillar = (renderFlags & 1) == 0;
            boolean hammerTargetingLeft = !mainBoss.isTargetingRight();

            if (isLeftPillar == hammerTargetingLeft) {
                mainBoss.clearHammerFlag();
                mainBoss.spawnArrowAndEyes(isLeftPillar);
                pillarShaking = true;
            }
        }
        updatePillarShake(vIntRunCount);
        mappingFrame = 0;
    }

    /**
     * Pillar lowering state (boss defeated).
     * ROM: Obj89_Pillar_Sub4 (s2.asm:65601-65640). FixBugs=0 (shipped): no
     * Obj89_Pillar_SolidObject call while the pillar sinks, and the delete leaves
     * a rider's Status_OnObj set ("one player can walk on air"). The fixed branch
     * keeps calling SolidObject and clears the riders' standing/pushing bits
     * before the delete.
     */
    private void updatePillarLower() {
        services().gameState().setScreenShakeActive(true);

        y += 1;
        if (y >= PILLAR_START_Y) {
            services().gameState().setScreenShakeActive(false);
            setDestroyed(true);
            return;
        }
        mappingFrame = 0;
    }

    private void updatePillarShake(int vIntRunCount) {
        if (!pillarShaking) {
            return;
        }
        if (pillarShakeTime <= 0) {
            pillarShakeTime = 0x1F;
        }
        pillarShakeTime--;
        if (pillarShakeTime <= 0) {
            pillarShaking = false;
            pillarShakeTime = 0;
            resetPillarBasePosition();
            return;
        }
        int baseX = isRightPillar() ? RIGHT_PILLAR_X : LEFT_PILLAR_X;
        int offset = ((vIntRunCount & 1) == 0) ? 1 : -1;
        x = baseX + offset;
        y = PILLAR_TARGET_Y + offset;
    }

    private void resetPillarBasePosition() {
        x = isRightPillar() ? RIGHT_PILLAR_X : LEFT_PILLAR_X;
        y = PILLAR_TARGET_Y;
    }

    private boolean isRightPillar() {
        return (renderFlags & 1) != 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_PARTS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return 2;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // Obj89_Pillar_SolidObject: d1=$23, d2=$44, d3=$45, d4=x_pos, with y_pos
        // temporarily 4 px lower (s2.asm:65652-65661).
        return PILLAR_SOLID_PARAMS;
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        // Obj89_Pillar_SolidObject calls the standard SolidObject helper.
        // Its initial horizontal range check uses BHI, so relX == 2*d1 is
        // still a side contact rather than a release.
        // docs/s2disasm/s2.asm:35338-35436,65330-65339,65531-65539
        return true;
    }

    @Override
    public boolean preservesEdgeSubpixelMotion() {
        // At the exact right edge SolidObject reaches the side path with d0=0;
        // sub.w d0,x_pos preserves the native x_sub low word while publishing
        // Status_Push.
        // docs/s2disasm/s2.asm:35420-35436,65330-65339,65531-65539
        return true;
    }

    @Override
    public boolean usesInstanceSolidStateLatchKey() {
        // The pillar changes y_pos every raising/lowering frame, but its
        // standing/pushing bits remain in the same Obj89 SST slot. Keying the
        // folded engine latch by the rewritten dynamic spawn prevents the old
        // side-push owner from becoming unreachable when the pillar moves.
        // docs/s2disasm/s2.asm:65330-65374,65531-65539
        return true;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
