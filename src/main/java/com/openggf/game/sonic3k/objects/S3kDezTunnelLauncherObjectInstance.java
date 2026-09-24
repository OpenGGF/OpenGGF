package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.io.IOException;
import java.util.List;

/** SKL $57: Obj_DEZTunnelLauncher ($481F2), controller $484B0 and ring trail $487E4. */
public final class S3kDezTunnelLauncherObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int timer, digit, mappingFrame, digitFrame;
    private boolean countdown;
    private final int[] captured = new int[2];

    public S3kDezTunnelLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTunnelLauncher");
    }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        if (countdown) {
            timer = (timer - 1) & 0xFF;
            if (timer == 0) {
                digit = mappingFrame = digitFrame = 0;
                captured[0] = captured[1] = 0;
                countdown = false;
            } else {
                digitFrame = (timer & 1) != 0 ? digit : 0;
                if (mappingFrame != 6) mappingFrame = (mappingFrame + 1) & 0xFF;
            }
            return;
        }
        capture(services().playerQuery().mainPlayerOrNull(), 0);
        capture(services().playerQuery().nativeP2OrNull(), 1);
        if ((timer | digit) == 0) return;
        timer = (timer - 1) & 0xFF;
        if (timer == 0) {
            timer = 60;
            if (digit != 0) {
                digit--;
                services().playSfx((digit == 7 ? Sonic3kSfx.LAUNCH_GO : Sonic3kSfx.LAUNCH_READY).id);
                if (digit == 7) {
                    var controller = spawnChild(() -> new Controller(spawn));
                    if (controller != null && !controller.isDestroyed()) {
                        controller.routine[0] = captured[0];
                        controller.routine[1] = captured[1];
                    }
                    // Allocation failure still installs Countdown; the native
                    // captured players remain locked when no controller exists.
                    countdown = true;
                }
            }
        }
        mappingFrame = (mappingFrame - 1) & 0xFF;
        if (mappingFrame == 0) mappingFrame = 2;
        digitFrame = (timer & 1) != 0 ? digit : 0;
    }

    private void capture(PlayableEntity entity, int slot) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        if (((player.getCentreX() - getX() + 8) & 0xFFFF) >= 0x10
                || ((player.getCentreY() - getY() + 0x10) & 0xFFFF) >= 0x20
                || player.getAir() || player.isObjectControlled()) return;
        NativePositionOps.writeXPosPreserveSubpixel(player, getX());
        player.setDirection((spawn.renderFlags() & 1) == 0 ? Direction.RIGHT : Direction.LEFT);
        touchFloor(player);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        player.setAnimationId(9);
        captured[slot] = 2;
        if (digit != 0) return;
        services().playSfx(Sonic3kSfx.LAUNCH_GRAB.id);
        timer = 60;
        if (slot == 0) {
            digit = digitFrame = 10;
            services().playSfx(Sonic3kSfx.LAUNCH_READY.id);
        }
        if (mappingFrame == 0) mappingFrame = 7;
    }

    /** Player_TouchFloor: capture admits grounded rolling players too. */
    private void touchFloor(AbstractPlayableSprite player) {
        int oldY = player.getCentreY(), oldRadius = player.getYRadius();
        int savedDoubleJump = player.getDoubleJumpFlag();
        boolean rolling = player.getRolling();
        player.setRolling(false);
        player.restoreDefaultRadii();
        if (rolling) {
            int delta = oldRadius - player.getStandYRadius();
            if (services().gameState().isReverseGravityActive()) delta = -delta;
            if ((((player.getAngle() & 0xFF) + 0x40) & 0x80) != 0) delta = -delta;
            NativePositionOps.writeYPosPreserveSubpixel(player, oldY + delta);
        }
        player.setAir(false);
        player.setPushing(false);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setFlipAngle(0);
        player.setFlipType(0);
        player.setFlipsRemaining(0);
        player.setDoubleJumpFlag(0);
        services().gameState().resetItemBonus();
        player.setLookDelayCounter((short) 0);
        player.applyPostObjectLandingAbilities(savedDoubleJump);
    }

    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    @Override public int getOnScreenHalfWidth() { return 0x48; }
    @Override public int getOnScreenHalfHeight() { return 0x50; }
    @Override public int getPriorityBucket() { return 1; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TUNNEL_LAUNCHER);
        if (renderer == null || !renderer.isReady()) return;
        boolean flip = (spawn.renderFlags() & 2) != 0;
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), flip, flip);
        renderer.drawFrameIndex(digitFrame, getX() + ((spawn.renderFlags() & 1) == 0 ? 0x38 : -0x38),
                getY() + (flip ? 0x2C : -0x2C), flip, flip);
    }
    public int timerForTest() { return timer; }
    public int digitForTest() { return digit; }
    public int frameForTest() { return mappingFrame; }
    public boolean countdownForTest() { return countdown; }

    /** Three native slots: Player_1, Player_2 and the independently allocated trail. */
    public static final class Controller extends AbstractObjectInstance implements RewindRecreatable {
        private boolean initialized;
        private Spawner spawner;
        private final int[] routine = new int[3];
        private final int[] timer = {10, 10, 0};
        private final int[] remaining = new int[3];
        private final int[] angle = new int[3];
        private final int[] waypoint = new int[3];
        public Controller(ObjectSpawn spawn) { super(spawn, "DEZTunnelControl"); }

        @Override public void update(int vIntRunCount, PlayableEntity ignored) {
            if (!initialized) {
                initialized = true;
                var child = spawnChild(() -> new Spawner(spawn));
                if (child != null && !child.isDestroyed()) { spawner = child; routine[2] = 2; }
            }
            runPlayer(services().playerQuery().mainPlayerOrNull(), 0);
            runPlayer(services().playerQuery().nativeP2OrNull(), 1);
            if (spawner != null) {
                if (!spawner.isDestroyed()) run(spawner.body, 2);
                // ROM DEZTunnelControl_Done returns without reading a1 once the
                // trail channel reaches zero. Its earlier setup means the trail
                // can finish before either player; loc_4889E then deletes it in
                // its own later slot pass. Drop the now-unused Java reference
                // immediately so a between-frames rewind never retains a dead
                // owner. The spawner still performs its native final update.
                if (routine[2] == 0 || spawner.isDestroyed()) spawner = null;
            }
        }

        private void runPlayer(PlayableEntity entity, int slot) {
            if (!(entity instanceof AbstractPlayableSprite player) || routine[slot] == 0) return;
            BodyState body = new BodyState();
            body.x = (player.getCentreX() << 16) | player.getXSubpixelRaw();
            body.y = (player.getCentreY() << 16) | player.getYSubpixelRaw();
            body.xVelocity = player.getXSpeed(); body.yVelocity = player.getYSpeed();
            body.angle = player.getAngle() & 0xFF; body.flipAngle = player.getFlipAngle();
            int oldRoutine = routine[slot];
            run(body, slot);
            NativePositionOps.writeXPosPreserveSubpixel(player, body.x >> 16);
            NativePositionOps.writeYPosPreserveSubpixel(player, body.y >> 16);
            player.setSubpixelRaw(body.x & 0xFFFF, body.y & 0xFFFF);
            player.setXSpeed((short) body.xVelocity); player.setYSpeed((short) body.yVelocity);
            player.setAngle((byte) body.angle); player.setFlipAngle(body.flipAngle);
            if (oldRoutine == 2 && routine[slot] != 2) {
                player.setAnimationId(2); player.setJumping(false);
                ObjectControlState.nativeBit7FullControl().applyTo(player);
                player.setGSpeed((short) 0x800); player.setAir(true);
                player.setMappingFrame(0x96);
            }
            if (routine[slot] == 0) ObjectControlState.none().applyTo(player);
        }

        private void run(BodyState body, int slot) {
            try {
                switch (routine[slot]) {
                    case 0 -> { }
                    case 2 -> {
                        timer[slot] = (timer[slot] - 1) & 0xFF;
                        if ((byte) timer[slot] >= 0) return;
                        routine[slot] = 4;
                        body.controlled = true; body.xVelocity = body.yVelocity = 0;
                        body.angle = body.flipAngle = 0;
                        var rom = services().romReader();
                        int path = (int) rom.readU32BE(0x1E4058 + (spawn.subtype() & 0x1F) * 4);
                        remaining[slot] = (rom.readU16BE(path) - 1) & 0xFF;
                        body.wordX(rom.readU16BE(path + 2)); body.wordY(rom.readU16BE(path + 4));
                        waypoint[slot] = path + 6;
                        setupStraight(body, slot);
                    }
                    case 4 -> {
                        int old = timer[slot]; timer[slot] = (old - 1) & 0xFF;
                        // SUBQ.B/BHI: both zero result and borrow (old zero) snap.
                        if (old > 1) { body.x += body.xVelocity << 8; body.y += body.yVelocity << 8; }
                        else {
                            body.wordX(word(waypoint[slot])); body.wordY(word(waypoint[slot] + 2));
                            waypoint[slot] += 4;
                            next(body, slot);
                        }
                    }
                    case 6, 8, 10, 12 -> {
                        int sin = TrigLookupTable.sinHex(angle[slot]);
                        int cos = TrigLookupTable.cosHex(angle[slot]);
                        int oldX = body.x >> 16, oldY = body.y >> 16;
                        if (routine[slot] >= 10) {
                            body.wordY(oldY + (routine[slot] == 10 ? 3 : -3));
                            body.yVelocity = routine[slot] == 10 ? 0x300 : -0x300;
                            body.angle = 0xFF; body.flipAngle = angle[slot];
                            body.wordX((body.x & 0xFFFF) + (sin >> 1));
                        } else {
                            int shift = routine[slot] == 6 ? 1 : 2;
                            body.wordX((body.x & 0xFFFF) + (sin >> shift));
                            body.wordY((body.y & 0xFFFF) + (cos >> shift));
                            body.yVelocity = (short) (((body.y >> 16) - oldY) << 8);
                        }
                        body.xVelocity = (short) (((body.x >> 16) - oldX) << 8);
                        int step = word(waypoint[slot]), end = word(waypoint[slot] + 2);
                        if ((end & 0xFF) == angle[slot]) {
                            if (routine[slot] >= 10) body.angle = body.flipAngle = 0;
                            routine[slot] = 4; waypoint[slot] += 4;
                            next(body, slot);
                        } else angle[slot] = (angle[slot] + step) & 0xFF;
                    }
                    default -> throw new IllegalStateException("Invalid DEZ tunnel routine " + routine[slot]);
                }
            } catch (IOException failure) { throw new IllegalStateException("DEZ tunnel requires ROM path data", failure); }
        }

        private int word(int address) throws IOException { return services().romReader().readU16BE(address); }
        private void next(BodyState body, int slot) throws IOException {
            remaining[slot] = (remaining[slot] - 1) & 0xFF;
            if (remaining[slot] == 0) { routine[slot] = 0; body.controlled = false; return; }
            int descriptor = word(waypoint[slot]);
            if ((descriptor & 0x8000) == 0) { setupStraight(body, slot); return; }
            int kind = (descriptor >> 8) & 0x7F;
            if (slot == 2) spawner.delay = services().romReader().readU8(0x48620 + kind);
            routine[slot] += kind * 2 + 2;
            timer[slot] = descriptor & 0xFF;
            angle[slot] = word(waypoint[slot] + 2) >> 8;
            int quadrant = (angle[slot] + 0x20) & 0xC0;
            int scale = (short) word(0x48618 + kind * 2);
            int centreX = (body.x >> 16) + ((TrigLookupTable.sinHex(quadrant) * scale) >> 8);
            int centreY = (body.y >> 16) + ((TrigLookupTable.cosHex(quadrant) * scale) >> 8);
            if (routine[slot] >= 10) centreY = body.y >> 16;
            // Native $12/$16 alias x_pos/y_pos fractions, including on release.
            body.x = (body.x & 0xFFFF0000) | (centreX & 0xFFFF);
            body.y = (body.y & 0xFFFF0000) | (centreY & 0xFFFF);
        }
        private void setupStraight(BodyState body, int slot) throws IOException {
            int dx = (short) (word(waypoint[slot]) - (body.x >> 16));
            int dy = (short) (word(waypoint[slot] + 2) - (body.y >> 16));
            int vx = dx < 0 ? -0xC00 : 0xC00, vy = dy < 0 ? -0xC00 : 0xC00;
            int duration;
            if (Math.abs(dy) >= Math.abs(dx)) {
                duration = (short) ((dy << 16) / vy);
                body.xVelocity = dx == 0 ? 0 : (short) ((dx << 16) / duration);
                body.yVelocity = vy;
            } else {
                duration = (short) ((dx << 16) / vx);
                body.yVelocity = dy == 0 ? 0 : (short) ((dy << 16) / duration);
                body.xVelocity = vx;
            }
            timer[slot] = ((duration < 0 ? -duration : duration) & 0xFFFF) >>> 8;
        }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) {
            return ((routine[0] + routine[1]) & 0xFF) == 0
                    && isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public Controller recreateForRewind(RewindRecreateContext context) { return new Controller(context.spawn()); }
        public int routineForTest(int slot) { return routine[slot]; }
        public int remainingForTest(int slot) { return remaining[slot]; }
        public Spawner spawnerForTest() { return spawner; }
    }

    /** Native object coordinates include curve centres in their fractional halves. */
    private static final class BodyState {
        int x, y, xVelocity, yVelocity, angle, flipAngle;
        boolean controlled;
        void wordX(int value) { x = (value << 16) | (x & 0xFFFF); }
        void wordY(int value) { y = (value << 16) | (y & 0xFFFF); }
    }

    public static final class Spawner extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private final BodyState body = new BodyState();
        private boolean initialized;
        private int delay;
        public Spawner(ObjectSpawn spawn) {
            super(spawn, "DEZTransRingSpawner"); body.x = spawn.x() << 16; body.y = spawn.y() << 16;
        }
        @Override public void update(int vIntRunCount, PlayableEntity ignored) {
            if (!initialized) { initialized = true; delay = 7; }
            delay = (short) (delay - 1);
            if (delay < 0) {
                delay = 1;
                var ring = spawnChild(() -> new Ring(new ObjectSpawn(getX(), getY(), spawn.objectId(),
                        0, 0, false, 0)));
                if (ring != null && !ring.isDestroyed()) {
                    if (body.angle == 0xFF) {
                        ring.bucket = ((body.flipAngle * 2) & 0x80) == 0 ? 0 : 1;
                        ring.flipX = ring.bucket == 1;
                        ring.animation = (((body.flipAngle + 8) & 0xFF) >>> 4 & 7) + 8;
                    } else {
                        ring.animation = (((TrigLookupTable.calcAngle((short) body.xVelocity,
                                (short) body.yVelocity) + 8) & 0xFF) >>> 4) & 7;
                    }
                }
                services().playSfx(Sonic3kSfx.LIGHT_TUNNEL.id);
            }
            if (!body.controlled) com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
        }
        @Override public int getX() { return body.x >> 16; }
        @Override public int getY() { return body.y >> 16; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    public static final class Ring extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int animation, animationFrame, delay, mappingFrame;
        private int bucket = 1;
        private boolean flipX;
        public Ring(ObjectSpawn spawn) { super(spawn, "DEZTransRing"); }
        @Override public void update(int vIntRunCount, PlayableEntity ignored) {
            delay = (delay - 1) & 0xFF;
            if ((byte) delay >= 0) return;
            try {
                var rom = services().romReader();
                int script = 0x488C2 + rom.readU16BE(0x488C2 + animation * 2);
                delay = rom.readU8(script);
                int frame = rom.readU8(script + 1 + animationFrame);
                if (frame == 0xFC) com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
                else { mappingFrame = frame & 0x1F; animationFrame = (animationFrame + 1) & 0xFF; }
            } catch (IOException failure) { throw new IllegalStateException("DEZ ring requires ROM animation", failure); }
        }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public int getOnScreenHalfWidth() { return 0x10; }
        @Override public int getOnScreenHalfHeight() { return 0x10; }
        @Override public int getPriorityBucket() { return bucket; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TUNNEL_RING);
            if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(), flipX, false);
        }
    }
}
