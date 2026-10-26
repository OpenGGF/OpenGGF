package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.CharacterKey;
import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.ObjectHeadRole;
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

/** Actual Obj_RobotnikHead parent3/INIT/MAIN/END/Child_Draw_Sprite2 (136033-136092). */
final class HczEndBossRobotnikHead extends AbstractObjectInstance implements RewindRecreatable {
    private HczEndBossRobotnikShip ship;
    /** Native $44, copied from the actual ship's parent3 by Head INIT. */
    private HczEndBossInstance boss;
    private int currentX;
    private int currentY;
    private int routine;
    private int animFrame;
    private int animCounter;
    private int mappingFrame;
    private int control;
    private boolean flipX;
    private boolean flipY;
    private boolean highPriority;
    private boolean visible;
    private boolean pendingDelete;
    /** INIT's native mapping/script selection, retained independently of later team changes. */
    private boolean eggRobo;

    HczEndBossRobotnikHead(HczEndBossRobotnikShip ship) {
        this(new ObjectSpawn(ship.getX(), ship.getY() - 0x1C, 0, 0, 0, false, 0));
        this.ship = Objects.requireNonNull(ship, "ship");
        highPriority = ship.isHighPriority(); // CreateChild1_Normal copies art first.
    }

    private HczEndBossRobotnikHead(ObjectSpawn spawn) {
        super(spawn, "HCZEndBossRobotnikHead");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override
    public HczEndBossRobotnikHead recreateForRewind(RewindRecreateContext context) {
        return new HczEndBossRobotnikHead(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        if (pendingDelete) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        HczEndBossRobotnikShip owner = Objects.requireNonNull(ship, "HCZ head missing exact parent3");
        // Refresh_ChildPositionAdjusted is before the routine dispatch.
        flipX = owner.facingRight();
        flipY = owner.flippedY();
        currentX = owner.getX() & 0xFFFF;
        currentY = (owner.getY() + (flipY ? 0x1C : -0x1C)) & 0xFFFF;
        if (routine == 0) {
            routine = 2;
            mappingFrame = 0;
            highPriority = false; // ObjDat_RobotnikHead, then Child_SyncDraw.
            if ((owner.nativeControl() & 0x40) != 0) {
                control |= 0x40; // Hidden branch does not copy the creator's art bit.
            } else {
                control &= ~0x40;
                highPriority = owner.isHighPriority();
            }
            boss = owner.bossForHeadInit();
            // Obj_RobotnikHeadInit reads Player_1.character_id, not Player_mode,
            // the configured default or a participating sidekick.
            var players = services().playerQuery();
            eggRobo = players != null
                    && players.mainPlayerOrNull() instanceof AbstractPlayableSprite main
                    && CharacterKey.KNUCKLES.equals(main.characterKey());
            if (eggRobo) {
                try {
                    // sub_67B14 changes mappings/script and submits once. It has
                    // no readiness wait; the global owner outlives this child.
                    if (!S3kRuntimeArtCoordinator.queueGlobalPatternModule(services(),
                            Sonic3kConstants.ART_KOSM_EGG_ROBO_HEAD_ADDR,
                            Sonic3kConstants.ART_TILE_ROBOTNIK_SHIP * 32)) {
                        throw new IllegalStateException("HCZ EggRobo head module FIFO is full");
                    }
                } catch (IOException failure) {
                    throw new UncheckedIOException("Unable to submit HCZ EggRobo head art", failure);
                }
            }
        } else if (routine == 2) {
            animCounter = (animCounter - 1) & 0xFF;
            if ((byte) animCounter < 0) {
                if (eggRobo) {
                    animateEggRoboHead();
                } else {
                    animCounter = 5;
                    animFrame ^= 1; // $05,$00,$01,$FC.
                    mappingFrame = animFrame;
                }
            }
            HczEndBossInstance statusOwner = Objects.requireNonNull(boss, "HCZ head missing exact $44");
            if (statusOwner.getState().defeated) {
                routine = 4;
                mappingFrame = 3;
                boss = null; // END performs no more $44 reads.
            } else if (statusOwner.getState().invulnerable) {
                mappingFrame = 2;
            }
        } else if (routine != 4) {
            throw new IllegalStateException("Invalid HCZ head routine " + routine);
        }
        if (owner.deleteSelected()) {
            control |= 0x10;
            pendingDelete = true;
            ship = null;
            boss = null;
        } else {
            visible = true;
        }
        updateDynamicSpawn(currentX, currentY);
    }

    private void animateEggRoboHead() {
        try {
            var reader = services().romReader();
            int script = Sonic3kConstants.ANI_RAW_EGG_ROBO_HEAD_ADDR;
            animFrame = (animFrame + 1) & 0xFF;
            int next = reader.readU8(script + 1 + animFrame);
            if (next == 0xFC) {
                // AnimateRaw_Restart publishes the first frame, then clears the cursor.
                animFrame = 0;
                next = reader.readU8(script + 1);
            }
            mappingFrame = next;
            animCounter = reader.readU8(script);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to read HCZ EggRobo head animation", failure);
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
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(eggRobo
                ? Sonic3kObjectArtKeys.EGG_ROBO_HEAD : Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndexForcedPriority(mappingFrame, currentX, currentY,
                    flipX, flipY, 0, highPriority);
        }
    }
}
