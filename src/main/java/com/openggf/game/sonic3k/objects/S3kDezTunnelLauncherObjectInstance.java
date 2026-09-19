package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.io.IOException;
import java.util.List;

/** SKL {@code $57}, {@code Obj_DEZTunnelLauncher} and its folded controller. */
public final class S3kDezTunnelLauncherObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int[] PATH_ADDR = {
            0x1FB49E, 0x1FB518, 0x1FB5B2, 0x1FB5B2,
            0x1FB5F0, 0x1FB646, 0x1FB6B4, 0x1FB6CE
    };
    private static final int[] SCALE = {-0x80, -0x40, -0x80, -0x80};

    private int baseX, baseY;
    private int mappingFrame;
    private int indicatorFrame;
    private int cadence;
    private int launchCount;
    private int closeTimer;
    private boolean launching;
    private boolean controllerActive;
    private boolean controllerSlotReserved;
    private RiderState p1 = new RiderState();
    private RiderState p2 = new RiderState();

    public S3kDezTunnelLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTunnelLauncher");
        baseX = spawn.x();
        baseY = spawn.y();
    }

    @Override
    public void update(int frame, PlayableEntity fallback) {
        boolean launchingAtEntry = launching;
        AbstractPlayableSprite main = sprite(tryServices() == null
                ? fallback : services().playerQuery().mainPlayerOrNull());
        AbstractPlayableSprite sidekick = tryServices() == null ? null
                : sprite(services().playerQuery().nativeP2OrNull());
        if (!launching) {
            capture(main, p1, true);
            capture(sidekick, p2, false);
            updateReadySequence();
        }
        if (controllerActive) {
            updateRider(main, p1);
            updateRider(sidekick, p2);
            if (p1.phase == 0 && p2.phase == 0) controllerActive = false;
        }
        if (launchingAtEntry) {
            updateClosingSequence();
        }
        if (!isInRangeAt(baseX)) setDestroyedByOffscreen();
    }

    private void capture(AbstractPlayableSprite player, RiderState state, boolean playerOne) {
        if (player == null || state.phase != 0 || player.getAir() || player.isObjectControlled()) return;
        int dx = (short) (player.getCentreX() - baseX);
        int dy = (short) (player.getCentreY() - baseY);
        if (dx < -8 || dx >= 8 || dy < -0x10 || dy >= 0x10) return;

        NativePositionOps.writeXPosPreserveSubpixel(player, (short) baseX);
        player.setAir(false);
        player.setPushing(false);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAnimationId(9);
        ObjectControlState.nativeBit7FullControl().applyTo(player);
        state.controlGeneration = player.getObjectControlGeneration();
        state.phase = 1;
        if (launchCount == 0) {
            cadence = 60;
            launchCount = playerOne ? 10 : 0;
            mappingFrame = 7;
            indicatorFrame = playerOne ? 10 : 0;
            play(Sonic3kSfx.LAUNCH_GRAB);
            if (playerOne) play(Sonic3kSfx.LAUNCH_READY);
        }
    }

    private void updateReadySequence() {
        if (cadence == 0) return;
        if (--cadence == 0) {
            cadence = 60;
            if (launchCount != 0) {
                launchCount--;
                play(launchCount == 7 ? Sonic3kSfx.LAUNCH_GO : Sonic3kSfx.LAUNCH_READY);
                if (launchCount == 7) beginLaunch();
            }
        }
        mappingFrame = mappingFrame <= 1 ? 2 : mappingFrame - 1;
        indicatorFrame = (cadence & 1) == 0 ? 0 : launchCount;
    }

    private void beginLaunch() {
        launching = true;
        controllerActive = true;
        closeTimer = 60;
        reserveControllerSlot();
        p1.delay = 10;
        p2.delay = 10;
    }

    private void reserveControllerSlot() {
        if (controllerSlotReserved || getSlotIndex() < 0) return;
        controllerSlotReserved = true;
        if (tryServices() != null && services().objectManager() != null)
            services().objectManager().allocateChildSlotsAfter(spawn, 2, getSlotIndex());
    }

    private void updateClosingSequence() {
        if (closeTimer == 0) return;
        closeTimer--;
        indicatorFrame = (closeTimer & 1) == 0 ? 0 : launchCount;
        if (mappingFrame != 6) mappingFrame++;
        if (closeTimer == 0) {
            mappingFrame = indicatorFrame = cadence = launchCount = 0;
            launching = false;
        }
    }

    private void updateRider(AbstractPlayableSprite player, RiderState state) {
        if (player == null || state.phase == 0) return;
        if (state.phase == 1) {
            if (--state.delay >= 0) return;
            loadPath(player, state);
            return;
        }
        if (state.phase == 2) updateLinear(player, state);
        else updateSpecial(player, state);
    }

    private void loadPath(AbstractPlayableSprite player, RiderState state) {
        int address = PATH_ADDR[spawn.subtype() & 7];
        try {
            int count = services().rom().read16BitAddr(address) & 0xFFFF;
            state.data = services().rom().readBytes(address + 2, count * 4);
            state.remaining = count - 1;
            state.pointer = 4;
            writePosition(player, word(state.data, 0), word(state.data, 2));
            player.setAnimationId(2);
            player.setJumping(false);
            player.setGSpeed((short) 0x800);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setAir(true);
            setupLinear(player, state, word(state.data, 4), word(state.data, 6));
            state.phase = 2;
        } catch (IOException | RuntimeException exception) {
            release(player, state);
        }
    }

    private void updateLinear(AbstractPlayableSprite player, RiderState state) {
        if (--state.timer > 0) {
            player.move(player.getXSpeed(), player.getYSpeed());
            return;
        }
        writePosition(player, word(state.data, state.pointer), word(state.data, state.pointer + 2));
        state.pointer += 4;
        readNext(player, state);
    }

    private void readNext(AbstractPlayableSprite player, RiderState state) {
        if (--state.remaining == 0 || state.pointer >= state.data.length) {
            release(player, state);
            return;
        }
        int command = state.data[state.pointer] & 0xFF;
        if ((command & 0x80) == 0) {
            setupLinear(player, state, word(state.data, state.pointer), word(state.data, state.pointer + 2));
            state.phase = 2;
            return;
        }
        int kind = command & 3;
        state.angle = state.data[state.pointer + 2] & 0xFF;
        int centreAngle = (state.angle + 0x20) & 0xC0;
        int scale = SCALE[kind];
        state.centreX = player.getCentreX() + TrigLookupTable.sinHex(centreAngle) * scale / 256;
        state.centreY = player.getCentreY() + TrigLookupTable.cosHex(centreAngle) * scale / 256;
        if (kind >= 2) state.centreY = player.getCentreY();
        state.pointer += 3;
        state.phase = 3 + kind;
    }

    private void updateSpecial(AbstractPlayableSprite player, RiderState state) {
        int step = state.data[state.pointer + 1];
        int end = state.data[state.pointer + 3] & 0xFF;
        if ((state.angle & 0xFF) == end) {
            state.pointer += 4;
            readNext(player, state);
            return;
        }
        int oldX = player.getCentreX();
        int oldY = player.getCentreY();
        int sin = TrigLookupTable.sinHex(state.angle);
        int cos = TrigLookupTable.cosHex(state.angle);
        int x;
        int y;
        if (state.phase == 3 || state.phase == 4) {
            int shift = state.phase == 3 ? 1 : 2;
            x = state.centreX + (sin >> shift);
            y = state.centreY + (cos >> shift);
        } else {
            x = state.centreX + (sin >> 1);
            y = oldY + (state.phase == 5 ? 3 : -3);
        }
        writePosition(player, x, y);
        player.setXSpeed((short) ((x - oldX) << 8));
        player.setYSpeed((short) ((y - oldY) << 8));
        state.angle = (state.angle + step) & 0xFF;
    }

    private void setupLinear(AbstractPlayableSprite player, RiderState state, int targetX, int targetY) {
        int dx = (short) (targetX - player.getCentreX());
        int dy = (short) (targetY - player.getCentreY());
        int dominant = Math.max(Math.abs(dx), Math.abs(dy));
        state.timer = Math.max(1, dominant / 12);
        player.setXSpeed((short) (dx * 256 / state.timer));
        player.setYSpeed((short) (dy * 256 / state.timer));
    }

    private void release(AbstractPlayableSprite player, RiderState state) {
        if (ownsControl(player, state)) ObjectControlState.none().applyTo(player);
        state.reset();
    }

    private boolean ownsControl(AbstractPlayableSprite player, RiderState state) {
        return player.isObjectControlled() && player.getObjectControlGeneration() == state.controlGeneration;
    }

    private static int word(byte[] data, int offset) {
        return (short) (((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    private static void writePosition(AbstractPlayableSprite player, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(player, (short) x);
        NativePositionOps.writeYPosPreserveSubpixel(player, (short) y);
    }

    private void play(Sonic3kSfx sfx) { if (tryServices() != null) services().playSfx(sfx.id); }
    private static AbstractPlayableSprite sprite(PlayableEntity player) {
        return player instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    @Override public int getX() { return baseX; }
    @Override public int getY() { return baseY; }
    @Override public int getOnScreenHalfWidth() { return 0x48; }
    @Override public int getOnScreenHalfHeight() { return 0x50; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x80); }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getReservedChildSlotCount() { return 2; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TUNNEL_LAUNCHER);
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(mappingFrame, baseX, baseY, false, false);
        int dx = (spawn.renderFlags() & 1) == 0 ? 0x38 : -0x38;
        int dy = (spawn.renderFlags() & 2) == 0 ? -0x2C : 0x2C;
        renderer.drawFrameIndex(indicatorFrame, baseX + dx, baseY + dy,
                (spawn.renderFlags() & 2) != 0, false);
    }

    int mappingFrameForTest() { return mappingFrame; }
    int indicatorFrameForTest() { return indicatorFrame; }
    boolean launchingForTest() { return launching; }
    int launchCountForTest() { return launchCount; }
    void armForTest() { cadence = 60; launchCount = 10; mappingFrame = 7; indicatorFrame = 10; }
    void primeRiderForRewindTest(int seed) {
        p1.phase = seed;
        p1.delay = seed + 1;
        p1.controlGeneration = seed + 2L;
        p1.data = new byte[]{(byte) seed, (byte) (seed + 3)};
        p1.pointer = seed + 4;
        p1.remaining = seed + 5;
        p1.timer = seed + 6;
        p1.angle = seed + 7;
        p1.centreX = seed + 8;
        p1.centreY = seed + 9;
    }
    long riderChecksumForTest() {
        return p1.phase + p1.delay + p1.controlGeneration + p1.data[0] + p1.data[1]
                + p1.pointer + p1.remaining + p1.timer + p1.angle + p1.centreX + p1.centreY;
    }

    private static final class RiderState {
        private int phase;
        private int delay;
        private long controlGeneration;
        private byte[] data;
        private int pointer;
        private int remaining;
        private int timer;
        private int angle;
        private int centreX;
        private int centreY;

        private void reset() {
            phase = delay = pointer = remaining = timer = angle = centreX = centreY = 0;
            controlGeneration = 0;
            data = null;
        }

    }
}
