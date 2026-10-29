package com.openggf.game.sonic2.competition.specialstage;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.specialstage.Sonic2PerspectiveData;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageBomb;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRing;

import java.util.Objects;

/** Retail Obj60/Obj61 clocks for the two-human halfpipe, independent of ordinary SS state. */
public final class CompetitiveHalfpipeItems {
    private static final int DEPTH_ANIMATIONS = 0x35180;
    private static final int RING_ANIMATIONS = 0x362D2;
    private static final int BOMB_ANIMATIONS = 0x364CE;

    private CompetitiveHalfpipeItems() { }

    public static Sonic2SpecialStageObject create(CompetitiveHalfpipeRomData data,
                                                 boolean bomb, int depth, int angle) {
        Objects.requireNonNull(data, "data");
        Sonic2PerspectiveData projection = perspective(data);
        Sonic2SpecialStageObject result = bomb ? new Bomb(data.rom(), projection) : new Ring(data.rom(), projection);
        result.initialize(depth, angle);
        return result;
    }

    /** Uses raw unsigned Y once, before the native signed-byte projection conversion. */
    public static Sonic2PerspectiveData perspective(CompetitiveHalfpipeRomData data) {
        return new Perspective(data.perspective());
    }

    /**
     * Moves/projects both item kinds; bombs animate before the caller's touch pass.
     * Rings animate in {@link #afterTouch}, matching loc_34FF0's collision order.
     * Effects keep moving and run AnimateSprite here, even when projected offscreen.
     */
    public static void advance(Sonic2SpecialStageObject object, int perspectiveFrame,
                               Sonic2PerspectiveData perspective, boolean flipped,
                               boolean drawingIndex4) {
        core(object).advance(perspectiveFrame, perspective, flipped, drawingIndex4);
    }

    public static void afterTouch(Sonic2SpecialStageObject object) {
        core(object).afterTouch();
    }

    public static Object capture(Sonic2SpecialStageObject object) {
        return core(object).snapshot();
    }

    public static Sonic2SpecialStageObject restore(CompetitiveHalfpipeRomData data, Object snapshot) {
        if (!(snapshot instanceof Snapshot saved)) {
            throw new IllegalArgumentException("Not a competitive halfpipe item snapshot");
        }
        Sonic2SpecialStageObject result = create(data, saved.bomb(), 0, saved.angle());
        core(result).restore(saved);
        return result;
    }

    private static Core core(Sonic2SpecialStageObject object) {
        if (object instanceof Ring ring) return ring.nativeState;
        if (object instanceof Bomb bomb) return bomb.nativeState;
        throw new IllegalArgumentException("Not a competitive halfpipe item");
    }

    private record Snapshot(boolean bomb, Sonic2SpecialStageObject.State state, int angle,
                            int depthFixed, int x, int y, int floorY, int animation,
                            int animationFrame, int countdown, int previousAnimation,
                            int mappingFrame, boolean onScreen, boolean pendingRingAnimation) { }

    private static final class Core {
        private final RomByteReader rom;
        private final boolean bomb;
        private final Sonic2PerspectiveData defaultPerspective;
        private Sonic2SpecialStageObject.State state;
        private int angle;
        private int depthFixed;
        private int x;
        private int y;
        private int floorY;
        private int animation;
        private int animationFrame;
        private int countdown;
        private int previousAnimation;
        private int mappingFrame;
        private boolean onScreen;
        private boolean pendingRingAnimation;

        private Core(RomByteReader rom, boolean bomb, Sonic2PerspectiveData perspective) {
            this.rom = rom;
            this.bomb = bomb;
            this.defaultPerspective = perspective;
        }

        private void initialize(int depth, int angle) {
            state = Sonic2SpecialStageObject.State.ACTIVE;
            this.angle = angle & 0xFF;
            depthFixed = depth << 16;
            x = 0x7F;
            y = 0x58;
            floorY = 0;
            animation = 0;
            animationFrame = 0;
            countdown = 0;
            previousAnimation = 0;
            mappingFrame = 0;
            onScreen = false;
            pendingRingAnimation = false;
        }

        private void advance(int frame, Sonic2PerspectiveData perspective, boolean flipped,
                             boolean drawingIndex4) {
            pendingRingAnimation = false;
            if (state == Sonic2SpecialStageObject.State.REMOVED) return;
            boolean effect = state != Sonic2SpecialStageObject.State.ACTIVE;
            if (effect) animation = 10;
            decrementDepth(drawingIndex4);
            if (state == Sonic2SpecialStageObject.State.REMOVED) return;
            project(perspective, frame, flipped);
            if (effect) {
                animateEffect();
            } else if (bomb) {
                animateActive();
            } else {
                pendingRingAnimation = true;
            }
        }

        private void decrementDepth(boolean drawingIndex4) {
            // loc_3512A (s2.asm:71030-71078): signed LONG <= 0 deletes,
            // including effects. Integer depth zero alone remains allocated.
            depthFixed -= drawingIndex4 ? 0xCCCC : 0xCCCD;
            if (depthFixed <= 0) {
                state = Sonic2SpecialStageObject.State.REMOVED;
                onScreen = false;
                return;
            }
            if (animation != 10) {
                animation = rom.readU8(address(rom, DEPTH_ANIMATIONS) + Math.min(depthFixed >>> 16, 30));
            }
        }

        private void project(Sonic2PerspectiveData perspective, int frame, boolean flipped) {
            Sonic2PerspectiveData.PerspectiveEntry entry = perspective.getEntry(frame, depthFixed >>> 16);
            onScreen = entry != null && entry.isAngleVisible(angle, flipped);
            if (!onScreen) return;
            int[] position = entry.calculateScreenPosition(angle, flipped);
            x = (short) position[0];
            y = (short) position[1];
            floorY = (short) (entry.yBase + entry.yRadius);
        }

        private void afterTouch() {
            if (!pendingRingAnimation || state == Sonic2SpecialStageObject.State.REMOVED) return;
            pendingRingAnimation = false;
            // Obj60 collection changes routine only. The current normal pass
            // still animates the size script; animation A begins next pass.
            animateActive();
        }

        private void animateActive() {
            // FixBugs=0: loc_3539E (s2.asm:71303-71348) never restarts
            // the countdown or frame pointer when perspective size changes.
            countdown = (countdown - 1) & 0xFF;
            if ((byte) countdown >= 0) return;
            int script = scriptAddress();
            countdown = rom.readU8(script);
            int next = rom.readU8(script + 1 + animationFrame);
            if (next >= 0x80) {
                animationFrame = 0;
                next = rom.readU8(script + 1);
            }
            mappingFrame = next & 0x7F;
            animationFrame = (animationFrame + 1) & 0xFF;
        }

        private void animateEffect() {
            // AnimateSprite/Anim_End_FF (s2.asm:30422-30471): each effect
            // loops until native depth deletion, rather than dying at frame 3.
            if (previousAnimation != animation) {
                previousAnimation = animation;
                animationFrame = 0;
                countdown = 0;
            }
            animateActive();
        }

        private int scriptAddress() {
            int table = bomb ? address(rom, BOMB_ANIMATIONS) : address(rom, RING_ANIMATIONS);
            return table + rom.readU16BE(table + animation * 2);
        }

        private boolean collect() {
            if (state != Sonic2SpecialStageObject.State.ACTIVE) return false;
            state = Sonic2SpecialStageObject.State.COLLECTED;
            return true;
        }

        private boolean explode() {
            if (state != Sonic2SpecialStageObject.State.ACTIVE) return false;
            state = Sonic2SpecialStageObject.State.EXPLODING;
            // loc_34F28 resets these bytes immediately, unlike Obj60's touch.
            animationFrame = 0;
            countdown = 0;
            return true;
        }

        private Snapshot snapshot() {
            return new Snapshot(bomb, state, angle, depthFixed, x, y, floorY,
                    animation, animationFrame, countdown, previousAnimation,
                    mappingFrame, onScreen, pendingRingAnimation);
        }

        private void restore(Snapshot saved) {
            state = saved.state();
            angle = saved.angle();
            depthFixed = saved.depthFixed();
            x = saved.x();
            y = saved.y();
            floorY = saved.floorY();
            animation = saved.animation();
            animationFrame = saved.animationFrame();
            countdown = saved.countdown();
            previousAnimation = saved.previousAnimation();
            mappingFrame = saved.mappingFrame();
            onScreen = saved.onScreen();
            pendingRingAnimation = saved.pendingRingAnimation();
        }
    }

    private static final class Ring extends Sonic2SpecialStageRing {
        private final Core nativeState;
        private Ring(RomByteReader rom, Sonic2PerspectiveData perspective) {
            nativeState = new Core(rom, false, perspective);
        }
        @Override public void initialize(int depth, int angle) { nativeState.initialize(depth, angle); }
        @Override public void update(int frame, boolean flipped, int speed, boolean drawingIndex4) {
            nativeState.advance(frame, nativeState.defaultPerspective, flipped, drawingIndex4);
            nativeState.afterTouch();
        }
        @Override public void updateScreenPosition(Sonic2PerspectiveData perspective, int frame, boolean flipped) {
            nativeState.project(perspective, frame, flipped);
        }
        @Override public void decrementDepth(boolean drawingIndex4, int speed) { nativeState.decrementDepth(drawingIndex4); }
        @Override public boolean collect() { return nativeState.collect(); }
        @Override public int getMappingFrame() { return nativeState.mappingFrame; }
        @Override public int getSpinFrame() { return Math.max(0, nativeState.animationFrame - 1); }
        @Override public boolean isSparkle() { return nativeState.animation == 10; }
        @Override public State getState() { return nativeState.state; }
        @Override public int getDepth() { return nativeState.depthFixed >> 16; }
        @Override public int getAngle() { return nativeState.angle; }
        @Override public int getScreenX() { return nativeState.x; }
        @Override public int getScreenY() { return nativeState.y; }
        @Override public int getTrackFloorY() { return nativeState.floorY; }
        @Override public int getAnimIndex() { return nativeState.animation; }
        @Override public int getAnimFrame() { return nativeState.animationFrame; }
        @Override public boolean isOnScreen() { return nativeState.onScreen; }
        @Override public boolean isHighPriority() { return false; }
        @Override public boolean isCollidable() { return nativeState.state == State.ACTIVE && nativeState.animation == 8; }
        @Override public boolean shouldRemove() { return nativeState.state == State.REMOVED; }
        @Override public void markForRemoval() { nativeState.state = State.REMOVED; nativeState.onScreen = false; }
    }

    private static final class Bomb extends Sonic2SpecialStageBomb {
        private final Core nativeState;
        private Bomb(RomByteReader rom, Sonic2PerspectiveData perspective) {
            nativeState = new Core(rom, true, perspective);
        }
        @Override public void initialize(int depth, int angle) { nativeState.initialize(depth, angle); }
        @Override public void update(int frame, boolean flipped, int speed, boolean drawingIndex4) {
            nativeState.advance(frame, nativeState.defaultPerspective, flipped, drawingIndex4);
        }
        @Override public void updateScreenPosition(Sonic2PerspectiveData perspective, int frame, boolean flipped) {
            nativeState.project(perspective, frame, flipped);
        }
        @Override public void decrementDepth(boolean drawingIndex4, int speed) { nativeState.decrementDepth(drawingIndex4); }
        @Override public boolean explode() { return nativeState.explode(); }
        @Override public int getMappingFrame() { return nativeState.mappingFrame; }
        @Override public boolean isExploding() { return nativeState.animation == 10; }
        @Override public State getState() { return nativeState.state; }
        @Override public int getDepth() { return nativeState.depthFixed >> 16; }
        @Override public int getAngle() { return nativeState.angle; }
        @Override public int getScreenX() { return nativeState.x; }
        @Override public int getScreenY() { return nativeState.y; }
        @Override public int getTrackFloorY() { return nativeState.floorY; }
        @Override public int getAnimIndex() { return nativeState.animation; }
        @Override public int getAnimFrame() { return nativeState.animationFrame; }
        @Override public boolean isOnScreen() { return nativeState.onScreen; }
        @Override public boolean isHighPriority() { return false; }
        @Override public boolean isCollidable() { return nativeState.state == State.ACTIVE && nativeState.animation == 8; }
        @Override public boolean shouldRemove() { return nativeState.state == State.REMOVED; }
        @Override public void markForRemoval() { nativeState.state = State.REMOVED; nativeState.onScreen = false; }
    }

    private static final class Perspective extends Sonic2PerspectiveData {
        private final byte[] bytes;
        private Perspective(byte[] bytes) { this.bytes = bytes.clone(); }
        @Override public int getMaxDepthCount(int frame) {
            return frame < 0 || frame >= 56 ? 0 : word(word(frame * 2));
        }
        @Override public PerspectiveEntry getEntry(int frame, int depth) {
            if (depth <= 0 || depth > getMaxDepthCount(frame)) return null;
            int offset = word(frame * 2) + 2 + (depth - 1) * 6;
            return new Entry(bytes[offset] & 0xFF, bytes[offset + 1] & 0xFF,
                    bytes[offset + 2] & 0xFF, bytes[offset + 3] & 0xFF,
                    bytes[offset + 4] & 0xFF, bytes[offset + 5] & 0xFF);
        }
        private int word(int offset) { return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF); }
    }

    private static final class Entry extends Sonic2PerspectiveData.PerspectiveEntry {
        private Entry(int x, int y, int xr, int yr, int min, int max) { super(x, y, xr, yr, min, max); }
        @Override public boolean isAngleVisible(int angle, boolean flipped) {
            if (angleMax == 0) return true;
            int a = angle & 0xFF;
            if (!flipped) return a < angleMin || a >= angleMax;
            // loc_35260 (s2.asm:71161-71183): word subtraction/negation,
            // then CMP.B performs unsigned comparisons of the wrapped bytes.
            return a < ((0x80 - angleMax) & 0xFF) || a >= ((0x80 - angleMin) & 0xFF);
        }
    }
}
