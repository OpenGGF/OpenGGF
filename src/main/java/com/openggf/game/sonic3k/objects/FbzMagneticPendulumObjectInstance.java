package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;

import java.util.List;

/** Locked-on {@code Obj_FBZMagneticPendulum} ($FF), $3D460-$3D9AE. */
public final class FbzMagneticPendulumObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    /** ROM angle:$27 word: signed angle byte followed by its fractional byte. */
    private int anglePhase;
    private int angularVelocity;
    private boolean swinging;
    private boolean grabbed;
    private boolean reversedEndpoint;
    private int releaseCarryFrames;
    private boolean graphAllocationAttempted;
    private boolean orientationLoaded;
    @RewindTransient(reason = "three-slot graph relinks through owner slot")
    private FbzMagneticPendulumEndpointObjectInstance endpoint;

    public FbzMagneticPendulumObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZMagneticPendulum");
        anglePhase = initialAngle() << 8;
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        loadRespawnOrientationOnce();
        if (!graphAllocationAttempted) {
            graphAllocationAttempted = true;
            endpoint = spawnChild(() -> new FbzMagneticPendulumEndpointObjectInstance(
                    buildSpawnAt(spawn.x(), spawn.y()), this));
            if (endpoint.isDestroyed()) endpoint = null;
        }
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        if (releaseCarryFrames > 0 && main != null) {
            main.move(main.getXSpeed(), main.getYSpeed());
            if (--releaseCarryFrames == 0 && main instanceof AbstractPlayableSprite sprite) {
                sprite.setObjectControlled(false);
                sprite.setAir(false);
            }
        } else if (swinging) {
            updateSwing(main);
        }
    }

    private void loadRespawnOrientationOnce() {
        if (orientationLoaded) return;
        orientationLoaded = true;
        if (services().zoneRuntimeState() instanceof FbzZoneRuntimeState state
                && state.pendulumOrientationBit(spawn.layoutIndex())) {
            reversedEndpoint = true;
            anglePhase = (short) (anglePhase + 0x8000);
        }
    }

    private void updateSwing(PlayableEntity main) {
        anglePhase = (short) (anglePhase + angularVelocity);
        int angle = angleValue();
        angularVelocity += signedByte(angle - 0x40) < 0 ? 6 : -6;
        boolean endpointReached = false;
        if (isHorizontal()) {
            if (angularVelocity < 0 && angle >= -0x40 && angle <= 0) {
                anglePhase = -0x40 << 8; reversedEndpoint = false; endpointReached = true;
            } else if (angularVelocity >= 0 && angle >= 0x40) {
                anglePhase = 0x40 << 8; reversedEndpoint = true; endpointReached = true;
            }
        } else {
            int shifted = signedByte(angle + 0x40);
            if (angularVelocity < 0 && shifted >= -0x40 && shifted <= 0) {
                anglePhase = -0x80 << 8; reversedEndpoint = false; endpointReached = true;
            } else if (angularVelocity >= 0 && shifted >= 0x40) {
                anglePhase = 0; reversedEndpoint = true; endpointReached = true;
            }
        }
        if (grabbed && main instanceof AbstractPlayableSprite sprite) {
            positionGrabbedPlayer(sprite);
            if (!endpointReached && sprite.isJumpJustPressed()) releaseByJump(sprite);
        }
        if (endpointReached) finishSwing(main);
    }

    private void releaseByJump(AbstractPlayableSprite player) {
        grabbed = false;
        int nativeX = player.getCentreX();
        int nativeY = player.getCentreY();
        player.setObjectControlled(false);
        player.setSpindash(false);
        player.setAir(true);
        player.setJumping(true);
        player.setRolling(true);
        player.setAnimationId(2);
        player.applyCustomRadii(7, 0xE);
        NativePositionOps.writeXPosPreserveSubpixel(player, nativeX);
        NativePositionOps.writeYPosPreserveSubpixel(player, nativeY);
        player.setGSpeed((short) 0);
        int sine = TrigLookupTable.sinHex(angleValue());
        int cosine = TrigLookupTable.cosHex(angleValue());
        player.setXSpeed((short) (cosine * 7));
        player.setYSpeed((short) (sine * 7));
        services().playSfx(Sonic3kSfx.JUMP.id);
    }

    private void finishSwing(PlayableEntity main) {
        swinging = false;
        services().playSfx(Sonic3kSfx.CLANK.id);
        if (!grabbed || !(main instanceof AbstractPlayableSprite sprite)) return;
        grabbed = false;
        int launch = Math.max(0x100, (Math.abs(angularVelocity) << 8) / 0x51);
        if (isHorizontal()) {
            sprite.setYSpeed((short) 0);
            sprite.setXSpeed((short) -launch);
            sprite.setGSpeed((short) (angleValue() < 0 ? -launch : launch));
        } else {
            sprite.setXSpeed((short) 0);
            sprite.setYSpeed((short) launch);
            sprite.setGSpeed((short) (angleValue() < 0 ? -launch : launch));
        }
        releaseCarryFrames = 1;
    }

    void tryCapture(PlayableEntity main, int endpointX, int endpointY) {
        if (swinging || main == null || main.isObjectControlled() || main.isDebugMode() || main.getDead()) return;
        int incoming;
        int nearRadius = 0x1D;
        int farRadius = 0x21;
        if (!main.getRolling()) {
            nearRadius = main instanceof Tails ? 0x1E : 0x22;
            farRadius = main instanceof Tails ? 0x22 : 0x26;
        }
        if (reversedEndpoint) {
            int transformedNear = 1 - farRadius;
            farRadius = 1 - nearRadius;
            nearRadius = transformedNear;
        }
        if (isHorizontal()) {
            if (main.getXSpeed() < 0
                    || main.getCentreX() < endpointX - 8
                    || main.getCentreX() > endpointX + 0x10) return;
            int minimumY = endpointY - farRadius;
            int maximumY = endpointY - nearRadius;
            if (main.getCentreY() < minimumY || main.getCentreY() > maximumY) return;
            incoming = main.getXSpeed();
        } else {
            if (main.getYSpeed() >= 0
                    || main.getCentreY() < endpointY - 0x10
                    || main.getCentreY() > endpointY + 8) return;
            int minimumX = endpointX - farRadius;
            int maximumX = endpointX - nearRadius;
            if (main.getCentreX() < minimumX || main.getCentreX() > maximumX) return;
            incoming = -main.getYSpeed();
        }
        angularVelocity = (incoming * 0x51) >> 8;
        if (reversedEndpoint) angularVelocity = -angularVelocity;
        swinging = true;
        grabbed = true;
        if (main instanceof AbstractPlayableSprite sprite) sprite.setObjectControlled(true);
    }

    private void positionGrabbedPlayer(AbstractPlayableSprite player) {
        int angle = angleValue();
        int sine = TrigLookupTable.sinHex(angle);
        int cosine = TrigLookupTable.cosHex(angle);
        NativePositionOps.writeXPosPreserveSubpixel(
                player, spawn.x() + attachedOffset(cosine, player.getRolling()));
        NativePositionOps.writeYPosPreserveSubpixel(
                player, spawn.y() + attachedOffset(sine, player.getRolling()));
        player.setAngle((byte) (angle + 0x40));
        int shiftedAngle = signedByte(angle + 0x40);
        int xVelocity = TrigLookupTable.cosHex(shiftedAngle);
        int yVelocity = TrigLookupTable.sinHex(shiftedAngle);
        if (angularVelocity < 0) {
            xVelocity = -xVelocity;
            yVelocity = -yVelocity;
        }
        player.setXSpeed((short) xVelocity);
        player.setYSpeed((short) yVelocity);
    }

    /** Reproduces the ROM's sequential signed longword shifts at $3D648-$3D684. */
    static int attachedOffset(int trigComponent, boolean rolling) {
        int scaled = trigComponent << 16;
        scaled >>= 1;
        int oneEighthOfHalf = scaled >> 3;
        scaled += oneEighthOfHalf;
        if (rolling) {
            scaled -= oneEighthOfHalf >> 4;
        } else {
            scaled += oneEighthOfHalf >> 2;
        }
        return scaled >> 16;
    }

    void cascadeDelete() {
        if (endpoint != null) endpoint.requestCascadeDelete();
        setDestroyed(true);
    }

    static int signedByte(int value) { return (byte) value; }
    public int totalGraphSlots() { return 3; }
    public int initialAngle() { return isHorizontal() ? -0x40 : -0x80; }
    public boolean consumesMagneticPolarity() { return false; }
    public ObjectPlayerParticipationPolicy participationPolicy() { return ObjectPlayerParticipationPolicy.MAIN_ONLY_NATIVE; }
    boolean isHorizontal() { return (spawn.subtype() & 0x80) != 0; }
    int angleValue() { return (byte) (anglePhase >> 8); }
    int angleFraction() { return anglePhase & 0xFF; }
    boolean isSwinging() { return swinging; }

    @Override
    protected void afterRewindRestoreSettled() {
        if (endpoint != null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof FbzMagneticPendulumEndpointObjectInstance point
                    && point.parentSlot() == getSlotIndex()) {
                endpoint = point;
                return;
            }
        }
    }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getPriorityBucket() { return 3; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        boolean out = isCoarseXOutOfRange(spawn.x(), cameraX, 0x280);
        if (out) {
            if (services().zoneRuntimeState() instanceof FbzZoneRuntimeState state) {
                int respawnAngle = angleValue() + (isHorizontal() ? 0 : 0x40);
                state.setPendulumOrientationBit(spawn.layoutIndex(), signedByte(respawnAngle) >= 0);
            }
            cascadeDelete();
        }
        return out;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_MAGNETIC_PENDULUM);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(isHorizontal() ? 1 : 0, spawn.x(), spawn.y(), false, false);
        }
    }
}
