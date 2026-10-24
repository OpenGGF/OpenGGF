package com.openggf.game.soniccd.objects;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable image of one physical CD dynamic slot, including all unwritten bytes.
 * Source: Level/_Definitions.inc:oSize/oID..oSubtype2 (25-66), pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. This is neither a player image nor a CPU address map.
 */
public final class CdObjectState {
    public static final int BYTE_SIZE = 0x40;

    /** Whole-pool identity is independent of layout row, saved ID and object ID. */
    public record Identity(int wholeIndex) {
        public Identity { Objects.checkIndex(wholeIndex - 32, 96); }
        public int dynamicIndex() { return wholeIndex - 32; }
        public int nativeAddress24() { return 0xFFD000 + wholeIndex * BYTE_SIZE; }
    }

    /** Native byte fields; overlapping aliases are intentional. No reserved/player field aliases. */
    public enum ByteField {
        ID(0), SPRITE_FLAGS(1), SCREEN_Y(0x0A), Y_RADIUS(0x16), X_RADIUS(0x17),
        PRIORITY(0x18), WIDTH(0x19), MAP_FRAME(0x1A), ANIMATION_FRAME(0x1B),
        ANIMATION(0x1C), PREVIOUS_ANIMATION(0x1D), ANIMATION_TIME(0x1E),
        COLLISION_TYPE(0x20), COLLISION_STATUS(0x21), FLAGS(0x22), SAVED_FLAGS_ID(0x23),
        // oSolidType / oLayer (Level/_Definitions.inc:59-66) use rs.b 0
        // after the primary byte, aliasing the following secondary byte.
        ROUTINE(0x24), SOLID_TYPE(0x25), ROUTINE_2(0x25), ANGLE(0x26), SUBTYPE(0x28),
        LAYER(0x29), SUBTYPE2(0x29),
        // ObjRing_Init:.InitSubObj / ObjRing_Collect: one-based member at oVar34.
        RING_MEMBER(0x34),
        // Obj3DRamp_Main (3D Ramp/Main.asm:230,294,326): launch timer byte at oVar2E. Storage only.
        RAMP_TIMER(0x2E),
        // ObjPoints (HUD and Points/Main.asm:11,40,47): oPntsTimer EQU oVar2A, a byte lifetime. Storage only.
        POINTS_TIMER(0x2A);
        private final int offset;
        ByteField(int offset) { this.offset = offset; }
        public int byteOffset() { return offset; }
    }

    public enum WordField {
        TILE(2), X(8), X_FRACTION(0x0A), Y(0x0C), Y_FRACTION(0x0E),
        X_VELOCITY(0x10), Y_VELOCITY(0x12),
        // ObjMosqui_Init / ObjRing_Init:.InitSubObj: original anchors, not current X shadows.
        MOSQUI_ANCHOR_X(0x2A), RING_ANCHOR_X(0x32),
        // Obj3DPlant_Init / Obj3DRamp_Init (3D Ramp/Main.asm:51,59,74,220): the family's shared original X at
        // oVar2A, the plant's own base X at oVar2C and the booster's anchor at oVar2A. Overlapping storage only.
        PLANT_ANCHOR_X(0x2A), PLANT_BASE_X(0x2C), RAMP_ANCHOR_X(0x2A),
        // MOVE.W #imm,oAnim(a0) (Explosion/Main.asm:80,83; Flower/Main.asm:55) writes oAnim and oPrevAnim together.
        ANIMATION_WORD(0x1C),
        // oExplodePoints EQU oVar3E (Explosion/Main.asm:8,19); Player_TouchEnemy .KillEnemy writes it
        // (Player Object Collision.asm:165,170). Its low byte selects the points frame. Storage only.
        EXPLOSION_POINTS_FRAME(0x3E);
        private final int offset;
        WordField(int offset) { this.offset = offset; }
        public int byteOffset() { return offset; }
    }

    public enum LongField {
        MAPPINGS(4), X_POSITION(8), Y_POSITION(0x0C),
        // ObjMosqui_Init:.SetInfo (Mosqui/Main.asm:51-54). Storage only; no body execution here.
        MOSQUI_X_DELTA(0x2C), MOSQUI_ANIMATION_SCRIPT(0x30);
        private final int offset;
        LongField(int offset) { this.offset = offset; }
        public int byteOffset() { return offset; }
    }

    private final Object owner;
    private final Identity identity;
    private final byte[] bytes;

    private CdObjectState(Object owner, Identity identity, byte[] ownedBytes) {
        this.owner = owner;
        this.identity = identity;
        this.bytes = ownedBytes;
    }

    static CdObjectState cleared(Object owner, int wholeIndex) {
        return new CdObjectState(owner, new Identity(wholeIndex), new byte[BYTE_SIZE]);
    }

    boolean belongsTo(Object candidate) { return owner == candidate; }
    public Identity identity() { return identity; }
    public byte[] copyBytes() { return bytes.clone(); }
    public int unsignedByte(ByteField field) { return Byte.toUnsignedInt(bytes[Objects.requireNonNull(field).offset]); }
    public byte signedByte(ByteField field) { return bytes[Objects.requireNonNull(field).offset]; }
    public int word(WordField field) { return readWord(Objects.requireNonNull(field).offset); }
    public short signedWord(WordField field) { return (short) word(field); }
    public int longBits(LongField field) {
        int at = Objects.requireNonNull(field).offset;
        return (readWord(at) << 16) | readWord(at + 2);
    }

    public int id() { return unsignedByte(ByteField.ID); }
    public int spriteFlags() { return unsignedByte(ByteField.SPRITE_FLAGS); }
    public int flags() { return unsignedByte(ByteField.FLAGS); }
    public int centreX() { return word(WordField.X); }
    public int centreY() { return word(WordField.Y); }
    public int routine() { return unsignedByte(ByteField.ROUTINE); }
    public int savedFlagsId() { return unsignedByte(ByteField.SAVED_FLAGS_ID); }
    public int subtype() { return unsignedByte(ByteField.SUBTYPE); }
    public int subtype2() { return unsignedByte(ByteField.SUBTYPE2); }

    /** MOVE.B semantics: truncate only the addressed byte, retaining every other byte. */
    public CdObjectState moveByte(ByteField field, int value) {
        byte[] next = bytes.clone();
        next[Objects.requireNonNull(field).offset] = (byte) value;
        return new CdObjectState(owner, identity, next);
    }

    /** MOVE.W semantics; X/Y writes deliberately retain the fraction words. */
    public CdObjectState moveWord(WordField field, int value) {
        byte[] next = bytes.clone();
        putWord(next, Objects.requireNonNull(field).offset, value);
        return new CdObjectState(owner, identity, next);
    }

    /** MOVE.L semantics, including integer/fraction aliases for native position writers. */
    public CdObjectState moveLong(LongField field, int bits) {
        byte[] next = bytes.clone();
        int at = Objects.requireNonNull(field).offset;
        putWord(next, at, bits >>> 16);
        putWord(next, at + 2, bits);
        return new CdObjectState(owner, identity, next);
    }

    public CdObjectState addByte(ByteField field, int delta) { return moveByte(field, unsignedByte(field) + delta); }
    public CdObjectState addWord(WordField field, int delta) { return moveWord(field, word(field) + delta); }
    public CdObjectState addLong(LongField field, int delta) { return moveLong(field, longBits(field) + delta); }

    private int readWord(int at) { return (Byte.toUnsignedInt(bytes[at]) << 8) | Byte.toUnsignedInt(bytes[at + 1]); }
    private static void putWord(byte[] bytes, int at, int value) {
        bytes[at] = (byte) (value >>> 8);
        bytes[at + 1] = (byte) value;
    }

    @Override public boolean equals(Object other) {
        return other instanceof CdObjectState state && owner == state.owner
                && identity.equals(state.identity) && Arrays.equals(bytes, state.bytes);
    }
    @Override public int hashCode() {
        return 31 * (31 * System.identityHashCode(owner) + identity.hashCode()) + Arrays.hashCode(bytes);
    }
}
