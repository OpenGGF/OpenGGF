package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * SYZ Boss spike — child component that extends below the Eggpod.
 * ROM: BossSpringYard_SpikeMain (routine 8 in _incObj/75 Boss - Spring Yard.asm)
 *
 * Uses Map_BossItems frame 5 (.spike) — 4 sprite pieces forming a cross shape.
 * The spike has collision type $84 (harmful to player) when the boss isn't
 * holding a block and isn't invulnerable.
 *
 * The spike tracks a Y extension value (objoff_3C) that grows during the boss's
 * block drop sequence and retracts when ascending. Display offset = extension >> 2.
 *
 * <p>ROM: {@code BossSpringYard_SpikeMain} is a fully independent SST object slot
 * (allocated after the boss's own slot) that reads the boss's {@code ob2ndRout} /
 * {@code obSubtype} / {@code BossSpringYard_GenericTimer} fresh every frame via its
 * own dispatch — it is never driven by the boss's own routine handler. This class
 * mirrors that in {@link #update}, pulling state directly off the parent boss each
 * frame (matching the established {@code EHZBossSpike} pattern for the S2 analogue)
 * instead of the boss pushing it in from its own {@code updateBossLogic()}, which
 * can be skipped for a frame during the post-hit defeat dispatch deferral
 * ({@link Sonic1SYZBossInstance#defeatDeferralAppliesToThisBoss()}).
 */
public class SYZBossSpike extends AbstractBossChild implements TouchResponseProvider, RewindRecreatable {

    // ROM: obColType = $84 when active (harmful, size category)
    private static final int SPIKE_COLLISION_TYPE = 0x84;
    private static final int ENGINE_BEHIND_SHIP_PRIORITY = 6;
    // ROM: BSYZ_ShipStart etc. use boss_syz bounds far wider than the 320px screen;
    // the boss's own off-screen destroy check (isBossOnScreen()) uses a generous
    // +/-64px margin around the viewport. Reuse the same tolerance so the spike's
    // own off-screen self-delete during escape agrees with the ship's.
    private static final int OFF_SCREEN_MARGIN = 64;

    // Extension tracking
    private int extensionDepth; // objoff_3C — tracks how far the spike extends
    private boolean spikeActive;

    // Boss state cache (refreshed every frame in update(), read for extension tracking)
    private int bossRoutineSecondary;
    private int bossDropSubPhase;
    private int bossTimer;

    public SYZBossSpike(Sonic1SYZBossInstance parent) {
        super(parent, "SYZSpike", ENGINE_BEHIND_SHIP_PRIORITY, Sonic1ObjectIds.SYZ_BOSS);
        this.extensionDepth = 0;
        this.spikeActive = false;
    }

    @Override
    public SYZBossSpike recreateForRewind(RewindRecreateContext ctx) {
        // The spike is a child of the SYZ boss. If the boss was defeated/swept
        // before capture, the spike has no parent to attach to; drop it (its live
        // update self-expires with a dead parent). acceptDestroyed relinks to a
        // restored-but-destroyed boss whose captured state the spike still tracks.
        return RewindRecreateObjectLinks.nearestObject(ctx, Sonic1SYZBossInstance.class, true)
                .map(SYZBossSpike::new)
                .orElse(null);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed() || !shouldUpdate(frameCounter)) {
            return;
        }
        if (!(parent instanceof Sonic1SYZBossInstance boss) || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        bossRoutineSecondary = boss.getState().routineSecondary;
        bossDropSubPhase = boss.getDropSubPhase();
        bossTimer = boss.getGenericTimer();

        // ROM: BossSpringYard_SpikeMain — cmpi.b #$A,ob2ndRout(a1) / tst.b obRender(a0) /
        // bpl.s BossSpringYard_SpikeDelete. Once the boss is fleeing (ob2ndRout==$A,
        // STATE_ESCAPE) the spike self-deletes off its OWN on-screen status, the same
        // shape as the ship's own escape self-delete (BSYZ_Escape.checkOffScreen).
        // X-only, matching Sonic1SYZBossInstance.isBossOnScreen()'s own X-only window --
        // the boss arena's Y placement is not near screen-Y=0, so a Y-inclusive check
        // (isOnScreen()) would flag the spike off-screen long before the ship ever does.
        if (bossRoutineSecondary == Sonic1SYZBossInstance.STATE_ESCAPE && !isOnScreenX(OFF_SCREEN_MARGIN)) {
            setDestroyed(true);
            return;
        }

        // ROM: BossSpringYard_SpikeMain — tst.b obColType(a1) / beq.s (no collision) /
        // tst.b BossSpringYard_ChildCmd(a1) / bne.s (no collision). obColType(a1)==0
        // means the boss is currently flashing/invulnerable (engine: state.invulnerable);
        // ChildCmd(a1) is the boss's own holdingFlag, set while a block is grabbed.
        spikeActive = !boss.getState().invulnerable && boss.getHoldingFlag() == 0;

        updateExtension();
    }

    @Override
    public int getCollisionFlags() {
        if (spikeActive) {
            return SPIKE_COLLISION_TYPE;
        }
        return 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0; // Spike is harmful only — hit counter is on the main boss object
    }

    @Override
    public int getX() {
        return parent.getX();
    }

    @Override
    public int getY() {
        return parent.getY() + (extensionDepth >> 2);
    }

    @Override
    public int getPriorityBucket() {
        // ROM gives the SYZ boss parts the same obPriority, but the original
        // sprite table order places the spike behind the ship. The engine
        // renders the ship as one parent object, so use the next bucket back.
        return ENGINE_BEHIND_SHIP_PRIORITY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer weaponsRenderer = renderManager.getRenderer(ObjectArtKeys.BOSS_WEAPONS);
        if (weaponsRenderer == null || !weaponsRenderer.isReady()) {
            return;
        }

        boolean flipped = (parent.getState().renderFlags & 1) != 0;

        // Map_BossItems frame 5 = .spike
        weaponsRenderer.drawFrameIndex(5, getX(), getY(), flipped, false);
    }

    /**
     * ROM: BossSpringYard_SpikeMain spike extension tracking.
     * During block drop (ob2ndRout=4), the spike extends progressively.
     * Otherwise it retracts toward 0.
     */
    void updateExtension() {
        if (bossRoutineSecondary == 4) {
            // During block drop phase
            if (bossDropSubPhase == 0) {
                // Descending — extend spike
                if (extensionDepth < 0x94) {
                    extensionDepth += 7;
                }
            } else if (bossDropSubPhase == 6 && bossTimer < 0) {
                // Settling with timer negative — retract
                if (extensionDepth > 0) {
                    extensionDepth -= 5;
                    if (extensionDepth < 0) {
                        extensionDepth = 0;
                    }
                }
            }
        } else {
            // Not in block drop — retract
            if (extensionDepth > 0) {
                extensionDepth -= 5;
                if (extensionDepth < 0) {
                    extensionDepth = 0;
                }
            }
        }
    }
}
