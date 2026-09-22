package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Obj_583BE and loc_584A2: progressively dismantle the column behind the climbing leader. */
public final class SszLaunchCrumbleObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    private boolean particle;
    private boolean initialized;
    private boolean displayed;
    private boolean renderOnScreen = true;
    private int row;
    private int timer;
    private int phase;
    private int artWord;
    private int frame;

    public SszLaunchCrumbleObjectInstance(ObjectSpawn spawn) { super(spawn, "SSZLaunchCrumble"); }
    private SszLaunchCrumbleObjectInstance(int x, int y, int art, int delay) {
        this(new ObjectSpawn(x, y, 0, 0, 0, false, 0));
        particle = true; motion.x = x; motion.y = y; artWord = art; timer = delay;
    }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszLaunchCrumbleObjectInstance(context.spawn());
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (particle) { updateParticle(); return; }
        var state = S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElseThrow();
        if (!initialized) {
            if (state.unkEE98() == 0) return;
            initialized = true;
            row = 0x870;
        }
        if ((services().levelManager().getFrameCounter() & 15) == 0)
            services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        if (timer != 0) { timer = (short) (timer - 1); return; }
        int y = player == null ? 0x5C0 : player.getCentreY() & 65535;
        if (y >= 0x4000) y = 0x5C0;
        if (((y + 0x198) & 65535) > row) return;
        if (row < 0x300) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        state.launch().setTileRowOffset(((row << 4) & 0xF00) - 0x2000);
        int table = 0x58894 + (row >= 0x380 ? 0x80 : 0) + (row >= 0x800 ? 0x80 : 0) + (row & 0x70);
        int seed = phase;
        phase = (phase + 3) & 65535;
        int previous = getSlotIndex();
        for (int i = 0; i < 8; i++) {
            int art = word(table + 2 * i);
            int delay = byteAt(0x58A3E + ((seed + i) & 7));
            int x = 0x1A08 + i * 0x10;
            int particleY = row - 0x178;
            var child = ObjectConstructionContext.construct(services(),
                    () -> new SszLaunchCrumbleObjectInstance(x, particleY, art, delay));
            services().objectManager().addDynamicObjectAfterSlot(child, previous);
            if (child.isDestroyed() || child.getSlotIndex() < 0) break;
            previous = child.getSlotIndex();
        }
        row = (row - 0x10) & 65535;
        timer = 7;
    }
    private void updateParticle() {
        if (!initialized) {
            initialized = true;
            if ((short) artWord < 0) {
                int sentinel = artWord;
                artWord = 0;
                if (sentinel == 0xFFFF) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
                frame = sentinel == 0xFFFE ? 1 : 2;
            }
        }
        if (!renderOnScreen) { ObjectLifetimeOps.deleteNoRespawn(this); return; }
        if (timer != 0) timer = (timer - 1) & 255;
        else SubpixelMotion.objectFall(motion, 0x38);
        displayed = true;
    }
    private int word(int address) {
        try { return services().romReader().readU16BE(address); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private int byteAt(int address) {
        try { return services().romReader().readU8(address); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public void refreshPostCameraRenderState() {
        if (displayed) { displayed = false; renderOnScreen = isWithinRenderSpriteBounds(0, 8); }
    }
    @Override public int getX() { return motion.x; }
    @Override public int getY() { return motion.y; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 3; }
    @Override public int getOnScreenHalfWidth() { return 0; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!particle || isDestroyed()) return;
        var renderer = getRenderer("ssz_launch_crumble_" + (artWord & 0x7FF));
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndexWithPaletteBase(frame,
                motion.x, motion.y, (artWord & 0x800) != 0, false, (artWord >>> 13) & 3);
    }
}
