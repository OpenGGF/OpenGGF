package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** {@code Obj_57E96}/{@code sub_5806E}: SSZ1's spiral-ramp launch into Death Egg. */
public final class SszLaunchControllerObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int START_WAIT = 0x1E;
    private static final int ANGLES_PER_ROW = 0x70;
    private static final int JUMP_COUNTER = 0x910;
    private static final int EXIT_WAIT = 3 * 60;
    private static final int COLUMN_FLOOR_Y = 0x580;
    private static final int[] COLUMN_DELAYS = {0, 9, 0xC, 6, 3, 0xC, 0, 9, 0xC, 3};

    private int timer = START_WAIT;
    private int routine;
    private int rampRow;
    private int rampAngle = 4;
    private int jumpXFixed;
    private int jumpYFixed;
    private int jumpXVelocity;
    private int jumpYVelocity;
    private boolean jumping;
    private boolean exitRequested;
    private final int[] columnDelay = COLUMN_DELAYS.clone();
    private final int[] columnVelocityFixed = new int[COLUMN_DELAYS.length];
    private final int[] columnOffsetFixed = new int[COLUMN_DELAYS.length];
    private boolean columnsFinished;

    public SszLaunchControllerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZ Death Egg launch controller");
    }

    @Override public boolean isPersistent() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignored) {
        if (exitRequested) return;
        if (routine <= 4 && (services().levelManager().getFrameCounter() & 0x0F) == 0) {
            services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        }
        AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
        if (player == null) return;
        updateCrumblingColumns();
        if (routine == 0) {
            if (--timer != 0) return;
            routine = 4;
        }
        if (!jumping) {
            updateRamp(player);
        } else {
            updateJump(player);
        }
    }

    /** {@code sub_5750C}: ten delayed 16.16 falls, their clamp handshake, and `_unkFAA4` carry. */
    private void updateCrumblingColumns() {
        if (columnsFinished) return;
        int cameraY = services().camera().getY() & 0xFFFF;
        int clamped = 0;
        for (int column = 0; column < COLUMN_DELAYS.length; column++) {
            if (columnDelay[column] < 0) {
                clamped++;
                continue;
            }
            if (columnDelay[column] > 0) {
                columnDelay[column]--;
            } else {
                int previousVelocity = columnVelocityFixed[column];
                columnVelocityFixed[column] += 0x800;
                columnOffsetFixed[column] -= previousVelocity;
            }
            if ((columnOffsetFixed[column] >> 16) + cameraY < COLUMN_FLOOR_Y) {
                columnOffsetFixed[column] = (COLUMN_FLOOR_Y - cameraY) << 16;
                columnVelocityFixed[column] = 0;
                columnDelay[column] = -1;
                clamped++;
            }
        }
        carryFinalArenaObject();
        if (clamped != COLUMN_DELAYS.length) return;
        columnsFinished = true;
        runtime().setEventsFg4Low(0xFF);
        deleteCarriedObject();
    }

    private void carryFinalArenaObject() {
        AbstractObjectInstance carried = carriedObject();
        if (carried == null) return;
        int column = Math.max(0, Math.min(COLUMN_DELAYS.length - 1,
                ((carried.getX() - 0x19A0) >> 3 & 0xFFFC) >> 2));
        int targetY = 0x660 - (columnOffsetFixed[column] >> 16);
        carried.applyLevelRepeatOffset(0, targetY - carried.getY());
    }

    private void deleteCarriedObject() {
        AbstractObjectInstance carried = carriedObject();
        if (carried != null) ObjectLifetimeOps.deleteNoRespawn(carried);
    }

    private AbstractObjectInstance carriedObject() {
        int slot = runtime().carriedObjectSlot();
        var manager = services().levelManager().getObjectManager();
        if (manager == null) return null;
        return manager.getActiveObjects().stream()
                .filter(AbstractObjectInstance.class::isInstance)
                .map(AbstractObjectInstance.class::cast)
                .filter(object -> object != this && object.getSlotIndex() == slot && !object.isDestroyed())
                .findFirst().orElse(null);
    }

    private SszZoneRuntimeState runtime() {
        return (SszZoneRuntimeState) services().zoneRuntimeState();
    }

    private void updateRamp(AbstractPlayableSprite player) {
        player.setControlLocked(true);
        int counter = (rampRow << 8) | rampAngle;
        if (counter == JUMP_COUNTER) {
            jumping = true;
            player.setAir(true);
            player.setJumping(true);
            player.applyRollingRadii(true);
            player.setRolling(true);
            player.setAnimationId(2);
            player.setGSpeed((short) 0x800);
            player.setXSpeed((short) 0x400);
            player.setYSpeed((short) -0x680);
            jumpXVelocity = 0x400;
            jumpYVelocity = -0x680;
            jumpXFixed = player.getCentreX() << 16;
            jumpYFixed = player.getCentreY() << 16;
            services().playSfx(Sonic3kSfx.JUMP.id);
            return;
        }

        double angle = rampAngle * (Math.PI * 2.0 / 256.0);
        int x = 0x1A40 + (int) Math.round(Math.sin(angle) * 0x4C);
        int y = 0x570 + (int) Math.round(Math.cos(angle) * 0x1C) - rampRow * 0x70;
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        rampAngle++;
        if (rampAngle >= ANGLES_PER_ROW) {
            rampAngle = 0;
            rampRow++;
        }
    }

    private void updateJump(AbstractPlayableSprite player) {
        if (timer > 0) {
            if (--timer == 0) requestExit();
            return;
        }
        jumpXFixed += jumpXVelocity << 8;
        jumpYFixed += jumpYVelocity << 8;
        jumpYVelocity += 0x38;
        NativePositionOps.writeXPosPreserveSubpixel(player, jumpXFixed >> 16);
        NativePositionOps.writeYPosPreserveSubpixel(player, jumpYFixed >> 16);
        player.setXSpeed((short) jumpXVelocity);
        player.setYSpeed((short) jumpYVelocity);
        if (jumpYVelocity < 0) return;
        player.setControlLocked(true);
        player.setObjectMappingFrameControl(true);
        player.setMappingFrame(0);
        timer = EXIT_WAIT;
    }

    private void requestExit() {
        exitRequested = true;
        services().fadeOutMusic();
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0, true);
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    public int rampCounterForTest() { return (rampRow << 8) | rampAngle; }
    public boolean jumpingForTest() { return jumping; }
    public int exitTimerForTest() { return timer; }
    public boolean exitRequestedForTest() { return exitRequested; }
    public boolean columnsFinishedForTest() { return columnsFinished; }
    public int columnOffsetForTest(int column) { return columnOffsetFixed[column] >> 16; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
