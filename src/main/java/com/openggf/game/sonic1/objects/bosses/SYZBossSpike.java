package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectLifetimeOps;
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
    private static final int NATIVE_PRIORITY = 5;
    // Extension tracking
    private int extensionDepth; // objoff_3C — tracks how far the spike extends
    private boolean spikeActive;
    private boolean renderedLastPass;
    private boolean displayedThisPass;

    // Boss state cache (refreshed every frame in update(), read for extension tracking)
    private int bossRoutineSecondary;
    private int bossDropSubPhase;
    private int bossTimer;

    public SYZBossSpike(Sonic1SYZBossInstance parent) {
        super(parent, "SYZSpike", NATIVE_PRIORITY, Sonic1ObjectIds.SYZ_BOSS);
        this.extensionDepth = 0;
        this.spikeActive = false;
    }

    @Override
    protected boolean tracksViaChildComponents() {
        return false; // Native routine 8 executes at its own SST position.
    }

    @Override
    public SYZBossSpike recreateForRewind(RewindRecreateContext ctx) {
        // The spike is a child of the SYZ boss. If the boss was defeated/swept
        // before capture, the spike has no parent to attach to; drop it (its live
        // update self-expires with a dead parent). acceptDestroyed relinks to a
        // restored-but-destroyed boss whose captured state the spike still tracks.
        //
        // Unbounded by design (not just left over): this spike's own captured spawn
        // (AbstractBossChild's dynamicSpawn) is built once in the constructor from the
        // boss's position AT THAT MOMENT and never refreshed afterward -- update() pulls
        // live position via getX()/getY() -> parent.getX()/getY() instead. The boss then
        // legitimately patrols, drops/rises, and finally escapes off-screen (see
        // Sonic1SYZBossInstance's APPROACH/PATROL/DROP/RISE/ESCAPE state machine), so its
        // live position can end up an arbitrary distance from where the spike's captured
        // spawn was frozen. A tight bound here would wrongly drop a still-correct relink
        // to a boss that has simply moved since the spike was built. This is safe because
        // exactly one live Sonic1SYZBossInstance ever exists per level (bosses are unique
        // per zone/act) -- unlike a same-class sibling search where two live instances can
        // genuinely coexist, there is no wrong-instance risk to bound against here.
        return RewindRecreateObjectLinks.nearestObjectUnbounded(ctx, Sonic1SYZBossInstance.class, true)
                .map(SYZBossSpike::new)
                .orElse(null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed() || !beginUpdate(vIntRunCount)) {
            return;
        }
        // Defensive same-frame destroy sync (belt-and-suspenders alongside
        // AbstractBossChild.isDestroyed()'s own lazy parent-destroyed check below);
        // matches the EHZBossSpike precedent's identical guard.
        if (!(parent instanceof Sonic1SYZBossInstance boss) || parent.isDestroyed()) {
            ObjectLifetimeOps.destroyBossChildLatched(this);
            return;
        }

        bossRoutineSecondary = boss.getState().routineSecondary;
        bossDropSubPhase = boss.getDropSubPhase();
        bossTimer = boss.getGenericTimer();

        // Native routine 8 tests its own retained BuildSprites bit before following
        // the ship. Its later obActWid=8 differs from the face/flame width 32.
        if (bossRoutineSecondary == Sonic1SYZBossInstance.STATE_ESCAPE && !renderedLastPass) {
            ObjectLifetimeOps.destroyBossChildLatched(this);
            return;
        }

        // ROM: BossSpringYard_SpikeMain — tst.b obColType(a1) / beq.s (no collision) /
        // tst.b BossSpringYard_ChildCmd(a1) / bne.s (no collision). obColType(a1)==0
        // means the boss is currently flashing/invulnerable (engine: state.invulnerable);
        // ChildCmd(a1) is the boss's own holdingFlag, set while a block is grabbed.
        spikeActive = !boss.getState().invulnerable && boss.getHoldingFlag() == 0;

        updateExtension();
        displayedThisPass = true;
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (!displayedThisPass) return;
        displayedThisPass = false;
        var camera = services().camera();
        int sx = (short) (getX() - camera.getX());
        int sy = (short) (getY() - camera.getY());
        renderedLastPass = sx >= -8 && sx < camera.getWidth() + 8
                && sy >= -32 && sy < camera.getHeight() + 32;
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
        // Independent components retain the native table priority and SST order.
        return NATIVE_PRIORITY;
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
