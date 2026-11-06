package com.openggf.game.sonic2.competition.specialstage;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.data.RomByteReader;

import java.util.Objects;

/** One native Obj5B particle after its owner's debit and successful allocation. */
public final class CompetitiveHalfpipeSpill {
    public record Velocity(int x, int y) { }
    public record State(int xFixed, int yFixed, int xVelocity, int yVelocity,
                        int animation, int previousAnimation, int animationFrame,
                        int frameDuration, int mappingFrame, int priority, boolean removed) { }

    private final RomByteReader rom;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int animation = 8;
    private int previousAnimation;
    private int animationFrame;
    private int frameDuration;
    private int mappingFrame;
    private int priority = 5;
    private boolean removed;

    /** Initialization has no movement, animation or display side effect. */
    public CompetitiveHalfpipeSpill(CompetitiveHalfpipeRomData data, int screenX, int screenY,
                                   int xVelocity, int yVelocity) {
        rom = Objects.requireNonNull(data, "data").rom();
        xFixed = screenX << 16;
        yFixed = screenY << 16;
        this.xVelocity = (short) xVelocity;
        this.yVelocity = (short) yVelocity;
    }

    /** Ordinals count successful particles only, matching Obj5B's (a2)+ read. */
    public static Velocity velocity(CompetitiveHalfpipeRomData data, int lostCount,
                                    int emittedOrdinal, int parentAngle) {
        Objects.requireNonNull(data, "data");
        if (lostCount < 1 || lostCount > 10 || emittedOrdinal < 0 || emittedOrdinal >= lostCount) {
            throw new IllegalArgumentException("Invalid native spill count or successful ordinal");
        }
        RomByteReader rom = data.rom();
        int table = (lostCount & 1) != 0 ? address(rom, 0x353F4) : address(rom, 0x353EA);
        int angle = (parentAngle + 0x40 + rom.readU8(table + emittedOrdinal)) & 0xFF;
        // CalcSine (s2.asm:4012-4024) indexes the ROM's 320-word sine table;
        // the extra quarter-turn words supply cosine without host trig math.
        int sine = rom.readS16BE(address(rom, 0x33CE) + angle * 2);
        int cosine = rom.readS16BE(address(rom, 0x33CE) + (angle + 0x40) * 2);
        return new Velocity((short) ((cosine * 0x400) >> 8), (short) ((sine * 0x1000) >> 8));
    }

    public void tick() {
        if (removed) return;
        // Obj5B_Main (s2.asm:71463-71494) calls generic ObjectMoveAndFall
        // (gravity $38) before adding another $80: total gravity is $B8.
        // Both positions use the old signed 8.8 velocity and wrap as longs.
        xFixed += xVelocity << 8;
        yFixed += yVelocity << 8;
        yVelocity = (short) (yVelocity + 0x38);
        yVelocity = (short) (yVelocity + 0x80);
        if (yVelocity >= 0) {
            priority = 0;
            animation = 9;
        }
        if (x() < 0 || x() >= 0x100 || y() > 0xE0) {
            removed = true;
            return;
        }
        animate();
    }

    private void animate() {
        // Spill uses generic AnimateSprite, not the depth-driven special-stage
        // animation helper. A switch to falling animation restarts immediately.
        if (animation != previousAnimation) {
            previousAnimation = animation;
            animationFrame = 0;
            frameDuration = 0;
        }
        frameDuration = (frameDuration - 1) & 0xFF;
        if ((byte) frameDuration >= 0) return;
        int script = address(rom, 0x362D2) + rom.readU16BE(address(rom, 0x362D2) + animation * 2);
        frameDuration = rom.readU8(script);
        int next = rom.readU8(script + 1 + animationFrame);
        if (next == 0xFF) {
            animationFrame = 0;
            next = rom.readU8(script + 1);
        }
        if ((next & 0x80) != 0) throw new IllegalStateException("Invalid native spill animation");
        mappingFrame = next & 0x7F;
        animationFrame = (animationFrame + 1) & 0xFF;
    }

    public int x() { return xFixed >> 16; }
    public int y() { return yFixed >> 16; }
    public int mappingFrame() { return mappingFrame; }
    public int priority() { return priority; }
    public boolean removed() { return removed; }
    public State capture() {
        return new State(xFixed, yFixed, xVelocity, yVelocity, animation, previousAnimation,
                animationFrame, frameDuration, mappingFrame, priority, removed);
    }
    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        xFixed = state.xFixed();
        yFixed = state.yFixed();
        xVelocity = state.xVelocity();
        yVelocity = state.yVelocity();
        animation = state.animation();
        previousAnimation = state.previousAnimation();
        animationFrame = state.animationFrame();
        frameDuration = state.frameDuration();
        mappingFrame = state.mappingFrame();
        priority = state.priority();
        removed = state.removed();
    }
}
