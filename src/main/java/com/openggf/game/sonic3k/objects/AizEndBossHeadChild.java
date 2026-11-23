package com.openggf.game.sonic3k.objects;

import com.openggf.game.CharacterKey;
import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.ObjectHeadRole;
import com.openggf.game.rewind.RewindCapturedReference;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/** AIZ's Obj_RobotnikHead2 (sonic3k.asm:136160-136174). */
final class AizEndBossHeadChild extends AbstractObjectInstance implements RewindRecreatable {
    private static final int Y_OFFSET = -0x1C;

    @RewindCapturedReference(reason = "the head follows the same ship slot after reconstruction")
    private AizEndBossShipChild ship;
    private boolean initialized;
    private boolean eggRobo;
    private boolean ended;
    private boolean hidden;
    private boolean highPriority;
    private boolean facingRight;
    private int currentX;
    private int currentY;
    private int animTimer;
    private int animFrame;
    private int mappingFrame;

    AizEndBossHeadChild(AizEndBossShipChild ship) {
        this(new ObjectSpawn(ship.getX(), ship.getY() + Y_OFFSET, 0, 0, 0, false, 0));
        this.ship = ship;
    }

    private AizEndBossHeadChild(ObjectSpawn spawn) {
        super(spawn, "AIZEndBossHead");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override
    public AizEndBossHeadChild recreateForRewind(RewindRecreateContext context) {
        // The exact ship reference hydrates from the captured graph.
        return new AizEndBossHeadChild(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AizEndBossShipChild owner = Objects.requireNonNull(ship, "AIZ head missing parent3");
        if (owner.isDestroyed()) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }

        currentX = owner.getX();
        currentY = owner.getY() + Y_OFFSET;
        var boss = owner.bossForHead();
        facingRight = boss.isFacingRight();
        hidden = owner.isHidden();
        if (!hidden) {
            highPriority = owner.isHighPriority();
        }

        if (!initialized) {
            initialized = true;
            initializeArt();
        } else if (!ended) {
            // Animate_Raw and the two ROM scripts (sonic3k.asm:177424-177443,
            // 136785-136788): zero underflows, selects frame 1, then $FC restarts at 0.
            if (--animTimer < 0) {
                animTimer = eggRobo ? 0xF : 5;
                animFrame ^= 1;
                mappingFrame = animFrame;
            }
            // Obj_RobotnikHeadMain (sonic3k.asm:136137-136152) overrides the raw frame.
            if (boss.getState().defeated) {
                mappingFrame = 3;
                ended = true;
            } else if (boss.getState().invulnerable) {
                mappingFrame = 2;
            }
        }

        updateDynamicSpawn(currentX, currentY);
    }

    private void initializeArt() {
        // Obj_RobotnikHeadInit / sub_67B14 (sonic3k.asm:136112-136132).
        var players = services().playerQuery();
        eggRobo = players != null
                && players.mainPlayerOrNull() instanceof AbstractPlayableSprite main
                && CharacterKey.KNUCKLES.equals(main.characterKey());
        if (eggRobo) {
            try {
                if (!S3kRuntimeArtCoordinator.queueGlobalPatternModule(services(),
                        Sonic3kConstants.ART_KOSM_EGG_ROBO_HEAD_ADDR,
                        Sonic3kConstants.ART_TILE_ROBOTNIK_SHIP * 32)) {
                    throw new IllegalStateException("AIZ EggRobo head module FIFO is full");
                }
            } catch (IOException failure) {
                throw new UncheckedIOException("Unable to submit AIZ EggRobo head art", failure);
            }
        }
    }

    @Override public int getX() { return currentX; }
    @Override public int getY() { return currentY; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public ObjectHeadRole headPresentationRole() { return ObjectHeadRole.BOSS; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || hidden || isDestroyed()) {
            return;
        }
        var renderer = getRenderer(eggRobo
                ? Sonic3kObjectArtKeys.EGG_ROBO_HEAD : Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, currentX, currentY,
                    facingRight, false, 0, highPriority);
        }
    }
}
