package com.openggf.mods.scene.art;

/**
 * Stateful object Animate_Sprite word-offset tables (S3K sonic3k.asm): delay bytes,
 * frames below $80, $FF loop, $FE step back, $FD animation switch and $FC routine advance.
 * Speed-driven character and AniRaw scripts use their separate sampling/owner policies.
 */
@com.openggf.game.ModApi
public final class RomAnimationPlayer {
    private final byte[] table;
    private int animation;
    private int previous = -1;
    private int cursor;
    private int timer;
    private int frame;
    private boolean advanced;

    public RomAnimationPlayer(byte[] table, int animation) {
        if (table == null || table.length < 4 || table.length > 65536) {
            throw new IllegalArgumentException("Animation table must contain 4..65536 bytes");
        }
        this.table = table.clone();
        set(animation);
    }
    public void set(int animation) { script(animation); this.animation = animation; }
    public int animation() { return animation; }
    public int frame() { return frame; }
    public boolean advanced() { return advanced; }
    public void clearAdvanced() { advanced = false; }
    public void tick() {
        if (animation != previous) { previous = animation; cursor = 0; timer = 0; }
        if (timer > 0) { timer--; return; }
        int script = script(animation);
        timer = byteAt(script);
        int value = byteAt(script + 1 + cursor);
        if (value < 0x80) { show(value); return; }
        switch (value) {
            case 0xFF -> { cursor = 0; show(mapping(script + 1)); }
            case 0xFE -> {
                int back = byteAt(script + 2 + cursor);
                if (back < 1 || back > cursor) throw new IllegalArgumentException("Animation step-back leaves script");
                cursor -= back;
                show(mapping(script + 1 + cursor));
            }
            case 0xFD -> set(byteAt(script + 2 + cursor));
            case 0xFC -> { advanced = true; timer = 0; cursor++; }
            default -> throw new IllegalArgumentException("Unsupported Animate_Sprite command " + value);
        }
    }
    private void show(int value) { frame = value; cursor++; }
    private int mapping(int offset) {
        int value = byteAt(offset);
        if (value >= 0x80) throw new IllegalArgumentException("Animation branch must target a mapping frame");
        return value;
    }
    private int byteAt(int offset) {
        if (offset < 0 || offset >= table.length) throw new IllegalArgumentException("Animation read leaves table");
        return table[offset] & 0xFF;
    }
    private int script(int animation) {
        if (animation < 0 || animation > (table.length - 2) / 2) throw new IllegalArgumentException("Invalid animation id");
        int offset = (byteAt(animation * 2) << 8) | byteAt(animation * 2 + 1);
        if (offset < 2 || offset >= table.length - 1) throw new IllegalArgumentException("Animation pointer leaves table");
        return offset;
    }
    @com.openggf.game.ModApi
    public record State(int animation, int previousAnimation, int cursor, int timer, int frame, boolean advanced) { }
    public State capture() { return new State(animation, previous, cursor, timer, frame, advanced); }
    public void restore(State state) {
        int script = script(state.animation());
        if (state.previousAnimation() != -1) script(state.previousAnimation());
        if (state.cursor() < 0 || state.cursor() > table.length - script - 1 || state.timer() < 0
                || state.timer() > 255 || state.frame() < 0 || state.frame() > 127) {
            throw new IllegalArgumentException("Invalid animation state");
        }
        animation = state.animation(); previous = state.previousAnimation(); cursor = state.cursor();
        timer = state.timer(); frame = state.frame(); advanced = state.advanced();
    }
}
