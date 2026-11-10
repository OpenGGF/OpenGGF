package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Optional;

/**
 * Finite borrowed water/air storage and native-call cuts, not a second player, pool or lifecycle.
 * Source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: src/r4/main.asm,
 * src/objects/player.asm and src/objects/r4/{player_bubbles,bubble_spawner,water_surface,splash}.asm.
 * All reads are reached reads. An unknown MUST throw there; no zero/default implementation is provided.
 * Writes/calls are synchronous completed prefixes, not returned commands. Root must bind original private
 * invocation identity, actual source/pool and complete State/Environment before adopting any live write.
 * These public interfaces/data alone confer no admission, publication, restore or replay authority.
 */
public final class CdR4WaterAirPorts {
    private CdR4WaterAirPorts() { }
    /** Global native widths, including raw BYTE water speed and full WORD clocks/gates. */
    public enum Value {
        ZONE(1), ACT(1), TIME_ZONE(1), PAUSED(1), WATER_SPEED(1), WATER_ANGLE(1), WATER_FULL(1),
        WATER_Y(2), STATIC_WATER_Y(2), TARGET_WATER_Y(2), CAMERA_X(2), CAMERA_Y(2),
        SPLIT(1), DROWN_TIMER(2), INVINCIBLE(1), BOSS_STARTED(1), ZONE_WORD(2),
        MAX_SPEED(2), ACCELERATION(2), DECELERATION(2), WATER_CURRENT(1), DEBUG_MODE(2),
        VBLANK_LOW(1),
        // Two read widths of the SAME clock, not separate retained clocks/owners.
        STAGE_FRAMES_LOW(1), STAGE_FRAMES(2), HELD(1), SCROLL_LOCK(1);
        public final int bytes;
        Value(int bytes) { this.bytes = bytes; }
    }
    /** Only the 64-byte water-family/player fields actually addressed by these leaves. Aliases are real. */
    public enum B {
        ID(0), SPRITE_FLAGS(1), TILE_HIGH(2), HEIGHT(0x16), WIDTH(0x17), LAYER(0x18), WIDTH_2(0x19),
        MAP_FRAME(0x1A), ANIM(0x1C), FLAGS(0x22), ROUTINE(0x24), ANGLE(0x26), SUBTYPE(0x28),
        PHASE(0x2A), CONTROL(0x2C), ELIGIBLE(0x2E), COUNTDOWN(0x32), RELOAD(0x33),
        REMAINING(0x34), BURST_HIGH(0x36), CHARGE(0x3C);
        public final int offset;
        B(int offset) { this.offset = offset; }
    }
    public enum W {
        TILE(2), X(8), SCREEN_Y(0xA), Y(0xC), VX(0x10), VY(0x12), GROUND_SPEED(0x14),
        DEATH_DELAY(0x2C), ANCHOR_X(0x30), BURST(0x36), TIMER(0x38), EMIT_TIMER(0x3A), AIR_TIMER(0x3E);
        public final int offset;
        W(int offset) { this.offset = offset; }
    }
    public enum L {
        MAPPINGS(4), PATTERN(0x3C);
        public final int offset;
        L(int offset) { this.offset = offset; }
    }
    public interface Slot {
        int read(B field);
        int read(W field);
        int read(L field);
        void write(B field, int byteValue);
        void write(W field, int wordValue);
        void write(L field, int bits);
    }
    public interface WaterState {
        int read(Value field);
        void write(Value field, int value);
        Slot player();
        /** Both cosine and sine native WORD reads, in shared SineCosine order, must be authenticated. */
        CdPlayerMath.SineCosine sineCosine(int angleByte);
    }
    public enum Animation { PLAYER_BUBBLES, BUBBLE_SPAWNER, WATER_SURFACE, SPLASH }
    public interface Calls extends WaterState {
        Slot reservedBubbles();
        /** Actual full96 physical SpawnObject search; empty does not clear or reserve any slot. */
        Optional<Slot> spawn();
        int random();
        void sound(int nativeId);
        void music(int nativeId);
        /** The existing source-equivalent PlayerSetGround algorithm, including reached sound/size writes. */
        void setPlayerGround();
        /** Existing MoveObject kernel, including raw support link read, never an invented velocity add. */
        void move(Slot slot);
        void animate(Slot slot, Animation animation);
        void draw(Slot slot);
        /** Native DeleteObject clears the entire actual64-byte slot. */
        void delete(Slot slot);
    }
    /** Reached pattern BYTE at the complete raw LONG plus unsigned native index; no pointer normalization.
     * Root binds its existing authenticated programme reader, including known bytes before/after the normal
     * eighteen-byte pattern table. Missing/unowned addresses throw here, after the actual child prefix.
     */
    @FunctionalInterface public interface SpawnPatternBytes {
        int readByte(int pointerBits, int unsignedIndex);
    }
    /** Borrowed authenticated data/reader only; these values do not authenticate or admit a programme. */
    public record BubbleData(int mappingsBits, CdAssetBytes wobble, SpawnPatternBytes spawnPatterns,
                             int spawnPatternsAddressBits) { }
    public static int read(WaterState s, Value f) { return checked(s.read(f), f.bytes, f.name()); }
    public static int read(Slot s, B f) { return checked(s.read(f), 1, f.name()); }
    public static int read(Slot s, W f) { return checked(s.read(f), 2, f.name()); }
    public static void write(WaterState s, Value f, int v) { s.write(f, v & (f.bytes == 1 ? 0xFF : 0xFFFF)); }
    public static void write(Slot s, B f, int v) { s.write(f, v & 0xFF); }
    public static void write(Slot s, W f, int v) { s.write(f, v & 0xFFFF); }
    public static int checked(int v, int bytes, String field) {
        if ((v & ~(bytes == 1 ? 0xFF : 0xFFFF)) != 0)
            throw new IllegalArgumentException("Unknown/noncanonical water read " + field);
        return v;
    }
}
