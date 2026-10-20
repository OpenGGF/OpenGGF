package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss flame barrel child.
 *
 * ROM:
 * - Cutscene barrel: loc_6872C (ChildObjDat_69072)
 * - Miniboss barrel: loc_68C12 (ChildObjDat_69086)
 */
public class AizMinibossFlameBarrelChild extends AizMinibossComponent implements RewindRecreatable {
    private static final int FLAG_PARENT_BITS = 0x38;
    private static final int PARENT_BIT_BARREL_ACTIVATE = 1 << 1;

    private static final int[] START_DELAYS = {0, 0x10, 0x20}; // word_68ECE
    private static final int[][] BARREL_OFFSETS = {
            {0, -0x20}, {9, -0x1C}, {0x12, -0x18}
    };

    // ROM byte_6912F (Animate_RawMultiDelay, first pair skipped on initial play):
    // Open: frame 4 (timer 5 = 6t), frame 5 (timer $17 = 24t), $F4
    private static final int[] OPEN_FRAMES = {4, 5};
    private static final int[] OPEN_DURATIONS = {6, 24};

    // ROM byte_69136 (Animate_RawMultiDelay, first pair skipped on initial play):
    // Close: frame 5 (timer $17 = 24t), frame 4 (timer 5 = 6t), frame 3 (timer 5 = 6t), $F4
    private static final int[] CLOSE_FRAMES = {5, 4, 3};
    private static final int[] CLOSE_DURATIONS = {24, 6, 6};

    private enum State {
        INIT,
        WAIT_ACTIVATE,
        START_DELAY,
        OPENING,
        FIRING_CUTSCENE,
        FIRING_MINIBOSS,
        BETWEEN_SHOTS,
        CLOSING,
        IDLE
    }

    // Non-final so the generic rewind field capturer reapplies them after the
    // recreate hook passes placeholder barrelIndex/false.
    private int barrelIndex;
    private boolean cutsceneVariant;

    private State state = State.INIT;
    private int timer;
    private int mappingFrame = 3;
    private int animIndex;
    /** Native byte $39: FireLoop/FireShot's signed burst counter and the
     *  SAME byte read/written by SetShotPosition's unsigned +4 cycle. */
    private int positionCounter;

    public AizMinibossFlameBarrelChild(AbstractBossInstance parent, int barrelIndex, boolean cutsceneVariant) {
        super(parent, "AIZMinibossBarrel" + barrelIndex, 4, barrelIndex);
        this.barrelIndex = Math.max(0, Math.min(2, barrelIndex));
        this.cutsceneVariant = cutsceneVariant;
        initializeAdjustedPosition(parent, BARREL_OFFSETS[this.barrelIndex][0], BARREL_OFFSETS[this.barrelIndex][1]);
        updateDynamicSpawn();
    }

    private AizMinibossFlameBarrelChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossBarrel" + spawn.subtype(), 4);
    }

    @Override public AizMinibossFlameBarrelChild recreateForRewind(RewindRecreateContext ctx) {
        return new AizMinibossFlameBarrelChild(ctx.spawn());
    }

    @Override public void syncPositionWithParent() {
        refreshAdjustedPosition(BARREL_OFFSETS[barrelIndex][0], BARREL_OFFSETS[barrelIndex][1]);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!beginOwnDispatch(vIntRunCount)) {
            return;
        }

        syncPositionWithParent();

        // loc_6872C/loc_68C12 run the own routine before Child_Draw_Sprite2,
        // including status7 defeat and the final parent38-bit4-observing entry.

        switch (state) {
            case INIT -> {
                state = State.WAIT_ACTIVATE;
                mappingFrame = 3;
            }
            case WAIT_ACTIVATE -> {
                mappingFrame = 3;
                if (!isActivatedByParent()) {
                    break;
                }
                timer = START_DELAYS[barrelIndex];
                state = State.START_DELAY;
            }
            case START_DELAY -> {
                if (--timer >= 0) {
                    break;
                }
                // ROM byte_6912F: begin open animation (first pair skipped)
                animIndex = 0;
                mappingFrame = OPEN_FRAMES[0];
                timer = OPEN_DURATIONS[0];
                state = State.OPENING;
            }
            case OPENING -> {
                if (--timer > 0) {
                    break;
                }
                animIndex++;
                if (animIndex < OPEN_FRAMES.length) {
                    mappingFrame = OPEN_FRAMES[animIndex];
                    timer = OPEN_DURATIONS[animIndex];
                    break;
                }
                // Open animation complete — start firing
                mappingFrame = 5;
                if (cutsceneVariant) {
                    positionCounter = 3;
                    state = State.FIRING_CUTSCENE;
                } else {
                    state = State.FIRING_MINIBOSS;
                }
            }
            case FIRING_CUTSCENE -> {
                fireCutsceneShot();
                state = State.BETWEEN_SHOTS;
            }
            case FIRING_MINIBOSS -> {
                // AIZMiniboss_BarrelController_FireFinal allocates the
                // flare/FallingShot pair from this live barrel's SST slot.
                spawnFallingShot();
                enterClosingAnimation();
            }
            case BETWEEN_SHOTS -> {
                // WaitAfterShot calls Obj_Wait, whose signed word underflow
                // jumps directly to FireShot. Its pair and $39 decrement belong
                // to this dispatch; the signed-counter tail then selects Return.
                if (--timer < 0) {
                    fireCutsceneShot();
                }
                if ((byte) positionCounter < 0) {
                    enterClosingAnimation();
                }
            }
            case CLOSING -> {
                if (--timer > 0) {
                    break;
                }
                animIndex++;
                if (animIndex < CLOSE_FRAMES.length) {
                    mappingFrame = CLOSE_FRAMES[animIndex];
                    timer = CLOSE_DURATIONS[animIndex];
                    break;
                }
                // Close animation complete
                mappingFrame = 3;
                if (cutsceneVariant) {
                    state = State.IDLE;
                } else {
                    clearParentActivationBit();
                    state = State.WAIT_ACTIVATE;
                }
            }
            case IDLE -> mappingFrame = 3;
        }

        finishNativeDrawTail();
    }

    private void fireCutsceneShot() {
        // FireShot publishes word2E before decrement/checked child allocation.
        timer = 0x1C;
        positionCounter = (positionCounter - 1) & 0xFF;
        if (positionCounter == 1) {
            // ChildObjDat_690A8 selects the real FallingShot owner. Its Init
            // reads this barrel operation and clears collision for the cutscene.
            spawnFallingShot();
        } else {
            // loc_68844 path from ChildObjDat_6909A.
            spawnShot(AizMinibossBarrelShotChild.Mode.SIMPLE);
        }
    }

    private void spawnShot(AizMinibossBarrelShotChild.Mode mode) {
        if (services().objectManager() == null) {
            return;
        }
        // ROM ChildObjDat_6909A / ChildObjDat_690A8 spawn a short muzzle-flare child
        // alongside the main shot object.
        spawnChild(() -> new AizMinibossBarrelShotFlareChild(this));
        spawnChild(() -> new AizMinibossBarrelShotChild(bossParent(), this, currentX, currentY + 4, mode));
    }

    private void spawnFallingShot() {
        if (services().objectManager() == null) {
            return;
        }
        spawnChild(() -> new AizMinibossBarrelShotFlareChild(this));
        spawnChild(() -> new AizMinibossNapalmProjectile(
                bossParent(), this, currentX, currentY + 4, 2));
    }

    private void enterClosingAnimation() {
        // ROM byte_69136: close animation (first pair skipped on initial play)
        animIndex = 0;
        mappingFrame = CLOSE_FRAMES[0];
        timer = CLOSE_DURATIONS[0];
        state = State.CLOSING;
    }

    /** ROM: subtype(a1) — barrel subtype used by shots for position selection. */
    int getBarrelSubtype() {
        return barrelIndex * 2;
    }

    /** ROM: $39(a1) — per-barrel position counter read by shots. */
    int getPositionCounter() {
        return positionCounter;
    }

    /** ROM: addq.b #4,d1 / move.b d1,$39(a1) — shots update the barrel's counter. */
    void setPositionCounter(int value) {
        this.positionCounter = value & 0xFF;
    }

    /** FallingShot_Init compares the parent barrel's native operation pointer. */
    boolean isCutsceneController() { return cutsceneVariant; }

    /** Narrow native header read for physical parent3 consumers; no status/lifetime inference. */
    int nativeRefreshRenderFlags() { return nativeRenderFlags; }

    /** ROM render_flags(a1) read by AIZMiniboss_SetShotPosition. */
    boolean isFacingFlipped() {
        return (parentHeader().renderFlags() & 1) != 0;
    }

    private boolean isActivatedByParent() {
        return (parentHeader().flags38() & PARENT_BIT_BARREL_ACTIVATE) != 0;
    }

    private void clearParentActivationBit() {
        var owner = com.openggf.level.objects.NativeSstSlotAccess.currentOwner(
                services().objectManager(), physicalParentSlot());
        if (owner instanceof AbstractBossInstance boss) {
            boss.setCustomFlag(FLAG_PARENT_BITS,
                    boss.getCustomFlag(FLAG_PARENT_BITS) & ~PARENT_BIT_BARREL_ACTIVATE);
        } else if (owner instanceof S3kBossDefeatSignpostFlow flow) {
            flow.clearRetainedBarrelActivation();
        } else {
            throw new IllegalStateException("AIZ barrel has no known writable physical parent38");
        }
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,1,1) — priority bit = 1
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (nativeRetirementPending || isDestroyed()) return;
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY,
                (nativeRenderFlags & 1) != 0, (nativeRenderFlags & 2) != 0);
    }
}
