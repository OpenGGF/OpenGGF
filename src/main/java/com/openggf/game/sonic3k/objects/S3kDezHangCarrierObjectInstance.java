package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** SKL $4C, Obj_DEZHangCarrier / sub_4703E (sonic3k.asm:92844-93002). */
public class S3kDezHangCarrierObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int remainingTravel;
    private int routine;
    private final boolean[] grabbed = new boolean[2];
    private final int[] cooldown = new int[2];

    public S3kDezHangCarrierObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZHangCarrier");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
        remainingTravel = (spawn.subtype() & 0xFF) << 2;
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            // Only the PREVIOUS P1 latch starts the carrier. Installing the
            // rising pointer does not execute it until the following pass.
            if (grabbed[0]) routine = 1;
        } else if (routine == 1) {
            move();
            yVelocity = (short) (yVelocity - 8);
            int distance = ceilingDistance();
            if (distance < 0) {
                yFixed -= distance << 16; // sub.w d1,y_pos keeps the fraction
                yVelocity = 0;
                xVelocity = (spawn.renderFlags() & 1) == 0 ? 0x200 : -0x200;
                routine = 2;
            }
        } else if (remainingTravel != 0) {
            remainingTravel = (remainingTravel - 1) & 0xFFFF;
            move();
        }
        updateDynamicSpawn(getX(), getY());
        var query = services().playerQuery();
        PlayableEntity main = query.mainPlayerOrNull();
        if (main == null) main = player;
        updatePlayer(main, 0);
        PlayableEntity second = query.nativeP2OrNull();
        if (second != main) updatePlayer(second, 1);
    }

    protected int ceilingDistance() {
        // ObjCheckCeilingDist enters S3K FindFloor through the upward EOR-$F
        // transform, using y_radius=$14 and the primary collision path.
        return ObjectTerrainUtils.checkNativeUpwardCeilingDist(getX(), getY(), 0x14).distance();
    }
    private void move() {
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
    }

    private void updatePlayer(PlayableEntity entity, int slot) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        if (grabbed[slot]) {
            if (!player.isRenderFlagOnScreen() || player.isHurt() || player.getDead()) {
                release(player, slot, 60);
                return;
            }
            // Ctrl_n_logical low-byte A/B/C press, high-byte held directions.
            if (player.isLogicalJumpPressActive()) {
                int input = player.getLogicalInputState();
                int directions = AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_DOWN
                        | AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT;
                release(player, slot, (input & directions) == 0 ? 18 : 60);
                if ((input & AbstractPlayableSprite.INPUT_LEFT) != 0) player.setXSpeed((short) -0x200);
                if ((input & AbstractPlayableSprite.INPUT_RIGHT) != 0) player.setXSpeed((short) 0x200);
                player.setYSpeed((short) -0x380);
                player.setAir(true);
                player.setJumping(true);
                player.applyCustomRadii(7, 14);
                player.setRollingFlagPreserveRadii(true);
                player.setAnimationId(2);
                player.setRollingJump(false);
                player.setFlipAngle(0);
                return;
            }
            pin(player);
            if ((services().levelManager().getFrameCounter() & 0xF) == 0)
                services().playSfx(Sonic3kSfx.RISING.id);
            return;
        }
        if (cooldown[slot] != 0) {
            cooldown[slot] = (cooldown[slot] - 1) & 0xFF;
            return; // zero is tested BEFORE decrement: no capture on expiry
        }
        if (((player.getCentreX() - getX() + 0x10) & 0xFFFF) >= 0x20
                || ((player.getCentreY() - getY() - 0x28) & 0xFFFF) >= 0x18) return;
        // TST.B object_control / BMI admits positive control such as $01.
        if ((player.isObjectControlled() && !player.isObjectControlAllowsCpu())
                || player.isHurt() || player.getDead() || player.isDebugMode()) return;
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        pin(player);
        player.setAnimationId(0x14);
        player.clearAirForNativeControlRestore(); // BCLR only; not a terrain landing
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        grabbed[slot] = true;
        services().playSfx(Sonic3kSfx.SWITCH.id);
    }
    private void pin(AbstractPlayableSprite player) {
        NativePositionOps.writeXPosPreserveSubpixel(player, getX());
        NativePositionOps.writeYPosPreserveSubpixel(player, getY() + 0x28);
    }
    private void release(AbstractPlayableSprite player, int slot, int delay) {
        ObjectControlState.none().applyTo(player);
        grabbed[slot] = false;
        cooldown[slot] = delay;
    }
    @Override public int getX() { return (xFixed >> 16) & 0xFFFF; }
    @Override public int getY() { return (yFixed >> 16) & 0xFFFF; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, 0x280);
    }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x14; }
    @Override public int getPriorityBucket() { return 1; } // priority=$80, art bit 15 clear
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_HANG_CARRIER);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
    public boolean grabbedForTest(int slot) { return grabbed[slot]; }
    public int cooldownForTest(int slot) { return cooldown[slot]; }
    public int routineForTest() { return routine; }
    public int remainingTravelForTest() { return remainingTravel; }
    public int yVelocityForTest() { return yVelocity; }
    public int yFractionForTest() { return yFixed & 0xFFFF; }
}
