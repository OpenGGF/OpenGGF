package com.openggf.game.sonic3k.objects;

import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** SKL $43, Obj_SOZSwingingPlatform/sub_4154C/sub_41636, locked-on ROM. */
public final class SozSwingingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable,
        RomObjectCodePointerProvider {
    private int x, y;
    private int angle = 0x4000, velocity, pause, previousAngle;
    private boolean initialized, active, reversing;
    private Display display;
    private final FbzParticipantStateTable riders = new FbzParticipantStateTable(1);

    public SozSwingingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZSwingingPlatform"); x = spawn.x(); y = spawn.y();
    }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!initialized) {
            initialized = true;
            display = spawnAfterCurrentSibling(() -> new Display(spawn));
            if (display != null && display.isDestroyed()) display = null;
        }
        boolean standing = leader != null && riders.flag(riders.slot(leader), 0);
        int a = (OscillationManager.getByte(0x18) + 0x80) & 255;
        if ((spawn.subtype() & 0xF0) != 0) {
            updateTriggeredSwing(standing, leader instanceof AbstractPlayableSprite p && p.isDebugMode());
            a = angle >>> 8;
        }
        if (a == previousAngle) return;
        previousAngle = a;
        if ((spawn.renderFlags() & 1) != 0) a = -a + 0x80;
        if ((spawn.renderFlags() & 2) != 0) a = -a;
        if (display == null || display.xs.length < 2) return;
        int dy = TrigLookupTable.sinHex(a) << 12;
        int dx = TrigLookupTable.cosHex(a) << 12;
        int ay = dy + (dy >> 1), ax = dx + (dx >> 1);
        for (int i = 1; i < display.xs.length; i++) {
            display.xs[i] = (spawn.x() + (ax >> 16)) & 0xFFFF;
            display.ys[i] = (spawn.y() + (ay >> 16)) & 0xFFFF;
            ax += dx; ay += dy;
        }
        // ROM child main sprite deliberately displays the preceding calculated endpoint.
        display.x = display.nextX; display.y = display.nextY;
        display.nextX = (spawn.x() + (ax >> 16)) & 0xFFFF;
        display.nextY = (spawn.y() + (ay >> 16)) & 0xFFFF;
        ax += dx >> 1; ay += dy >> 1;
        x = (spawn.x() + (ax >> 16)) & 0xFFFF;
        y = (spawn.y() + (ay >> 16)) & 0xFFFF;
        if ((spawn.subtype() & 0xF0) != 0) x = (x + ((spawn.renderFlags() & 1) != 0 ? 0x18 : -0x18)) & 0xFFFF;
    }
    private void updateTriggeredSwing(boolean standing, boolean debug) {
        if (pause != 0) { if (!standing) pause--; return; }
        if (!active) { if (!standing || debug) return; active = true; }
        velocity = (short) (velocity + (reversing ? -8 : 8));
        angle = (angle + velocity) & 0xFFFF;
        if (!reversing) {
            if (velocity == 0) active = false;
            if ((angle >>> 8) >= 0x80) reversing = true;
        } else {
            if (velocity == 0) pause = 0x1E;
            if ((angle >>> 8) < 0x80) reversing = false;
        }
    }
    @Override public void onSolidContact(PlayableEntity p, SolidContact c, int frame) { riders.flag(riders.slot(p), 0, c.standing()); }
    @Override public void onSolidContactCleared(PlayableEntity p, int frame) { riders.flag(riders.slot(p), 0, false); }
    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x20, 0x11, 0x11); }
    @Override public boolean isTopSolidOnly() { return true; }
    @Override public boolean rejectsZeroDistanceTopSolidLanding() { return true; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return spawn.x(); }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        boolean out = isCoarseXOutOfRange(spawn.x(), cameraX, coarseXCullRange());
        if (out && display != null) display.setDestroyed(true);
        return out;
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var r = getRenderer(Sonic3kObjectArtKeys.SOZ_SWINGING_PLATFORM);
        if (r != null && r.isReady()) r.drawFrameIndex(0, x, y, false, false);
    }
    /** ROM's single multisprite chain SST. */
    public static final class Display extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private final int[] xs, ys;
        private int x, y, nextX, nextY;
        public Display(ObjectSpawn spawn) {
            super(spawn, "SOZSwingingPlatformChain");
            xs = new int[spawn.subtype() & 15]; ys = new int[xs.length];
            java.util.Arrays.fill(xs, spawn.x()); java.util.Arrays.fill(ys, spawn.y());
            x = spawn.x(); y = spawn.y();
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public boolean requiresSameFrameUpdate() { return true; }
        @Override public int getPriorityBucket() { return 5; }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getOnScreenHalfWidth() { return 0x60; }
        @Override public int getOnScreenHalfHeight() { return 0x60; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            var r = getRenderer(Sonic3kObjectArtKeys.SOZ_SWINGING_PLATFORM);
            if (r == null || !r.isReady()) return;
            r.drawFrameIndex(1, x, y, false, false);
            for (int i = 0; i < xs.length; i++) r.drawFrameIndex(i == 0 ? 2 : 1, xs[i], ys[i], false, false);
        }
    }
}
