package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdTerrainCollision;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntConsumer;

/**
 * R11A's FindLevelFloor[/2] and FindLevelWall[/2] over bounded native terrain.
 * Owns no player state, engine drawing geometry, clock or mutable collision RAM.
 * Distances are signed native words; an absent angle write preserves the caller's angle.
 *
 * Source: Collision Floor.asm:685-1042 at scddisasm 1ecc5f3a, FixBugs=0.
 * The two floor routines' negative-height, a3=$10 special case is intentional.
 */
public final class CdPlayerTerrain {
    public record Result(int distance, OptionalInt angleWrite) {
        public Result {
            if (distance != (short) distance) {
                throw new IllegalArgumentException("Distance must be a signed native word");
            }
            Objects.requireNonNull(angleWrite);
            if (angleWrite.isPresent() && (angleWrite.getAsInt() & ~0xFF) != 0) {
                throw new IllegalArgumentException("Angle write must be a native byte");
            }
        }
    }

    private enum Axis { FLOOR, WALL }

    private final CdTerrainCollision placements;
    private final CdR11aTerrain.Collision shapes;

    public CdPlayerTerrain(CdR11aTerrain terrain) {
        Objects.requireNonNull(terrain);
        placements = new CdTerrainCollision(terrain);
        shapes = terrain.collision();
    }

    /** a3 is a native word; native player callers normally use +16 or -16. */
    public Result floor(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags) {
        return query(Axis.FLOOR, false, x, y, solidityBit, mirrorMask, step, spriteFlags);
    }

    /**
     * FindLevelFloor with synchronous reached native angle-byte stores. This entry does not
     * pre-clear the caller's angle: ObjGetFloorDist owns that earlier MOVE.B. The observer sees
     * each shape load, flip RMW and forced zero, including a prefix before a later terrain fault.
     * It is a write port to the caller's existing global, never another retained angle owner.
     */
    public Result floor(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags,
                        IntConsumer angleWrite) {
        Objects.requireNonNull(angleWrite, "reached angle writer");
        return query(Axis.FLOOR, false, x, y, solidityBit, mirrorMask, step, spriteFlags, angleWrite);
    }

    public Result floorExtension(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags) {
        return query(Axis.FLOOR, true, x, y, solidityBit, mirrorMask, step, spriteFlags);
    }

    public Result wall(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags) {
        return query(Axis.WALL, false, x, y, solidityBit, mirrorMask, step, spriteFlags);
    }

    public Result wallExtension(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags) {
        return query(Axis.WALL, true, x, y, solidityBit, mirrorMask, step, spriteFlags);
    }

    private Result query(Axis axis, boolean extension, int x, int y, int solidityBit,
                         int mirrorMask, int step, int spriteFlags) {
        if (solidityBit < 0 || solidityBit > 15) {
            throw new IllegalArgumentException("Solidity bit must select the original native word");
        }
        Cursor cursor = new Cursor(axis, solidityBit, mirrorMask & 0xFFFF, (short) step, spriteFlags & 0xFF);
        int distance = extension ? extension(cursor, x & 0xFFFF, y & 0xFFFF)
                : first(cursor, x & 0xFFFF, y & 0xFFFF);
        return new Result((short) distance,
                cursor.angle < 0 ? OptionalInt.empty() : OptionalInt.of(cursor.angle));
    }

    private Result query(Axis axis, boolean extension, int x, int y, int solidityBit,
                         int mirrorMask, int step, int spriteFlags, IntConsumer angleWrite) {
        if (solidityBit < 0 || solidityBit > 15) {
            throw new IllegalArgumentException("Solidity bit must select the original native word");
        }
        Cursor cursor = new Cursor(axis, solidityBit, mirrorMask & 0xFFFF, (short) step, spriteFlags & 0xFF, angleWrite);
        int distance = extension ? extension(cursor, x & 0xFFFF, y & 0xFFFF)
                : first(cursor, x & 0xFFFF, y & 0xFFFF);
        return new Result((short) distance,
                cursor.angle < 0 ? OptionalInt.empty() : OptionalInt.of(cursor.angle));
    }

    private int first(Cursor cursor, int x, int y) {
        Integer metric = metric(cursor, x, y);
        int offset = (cursor.axis == Axis.FLOOR ? y : x) & 15;
        if (metric == null || metric == 0) {
            return nearby(cursor, x, y, cursor.step) + 16;
        }
        if (metric < 0) {
            if (cursor.axis == Axis.FLOOR && cursor.step == 16) {
                cursor.storeAngle(0);
                return 15 - (16 + offset);
            }
            if ((short) (metric + offset) >= 0) {
                return nearby(cursor, x, y, cursor.step) + 16;
            }
            return nearby(cursor, x, y, -cursor.step) - 16;
        }
        if (metric == 16) {
            return nearby(cursor, x, y, -cursor.step) - 16;
        }
        return 15 - (metric + offset);
    }

    private int extension(Cursor cursor, int x, int y) {
        Integer metric = metric(cursor, x, y);
        int offset = (cursor.axis == Axis.FLOOR ? y : x) & 15;
        if (metric == null || metric == 0) {
            return 15 - offset;
        }
        if (metric < 0) {
            if (cursor.axis == Axis.FLOOR && cursor.step == 16) {
                cursor.storeAngle(0);
                return 15 - (16 + offset);
            }
            // The native negative extension returns NOT d1, not a metric formula.
            return (short) (metric + offset) >= 0 ? 15 - offset : (short) ~offset;
        }
        return 15 - (metric + offset);
    }

    private int nearby(Cursor cursor, int x, int y, int delta) {
        return cursor.axis == Axis.FLOOR ? extension(cursor, x, (y + delta) & 0xFFFF)
                : extension(cursor, (x + delta) & 0xFFFF, y);
    }

    private Integer metric(Cursor cursor, int x, int y) {
        int word = nativeBlockWord(x, y, cursor.spriteFlags, cursor.prefixReads);
        if (word < 0 || (word & 0x7FF) == 0 || (word & (1 << cursor.solidityBit)) == 0) {
            return null;
        }
        int block = word & 0x7FF;
        if (block >= shapes.ids().length()) {
            var original = new IllegalStateException("Collision ID requires unavailable resident bytes: " + block);
            if (cursor.prefixReads) throw new UnownedTerrainRead(new CollisionId(block), original);
            throw original;
        }
        int id = shapes.idForBlock(block);
        if (id == 0) {
            return null;
        }
        int angle = shapes.angle(id);
        cursor.storeAngle(angle); // MOVE.B angle-map byte,(a4), before any flip or height read.
        int sample = cursor.axis == Axis.FLOOR ? x : y;
        if (cursor.axis == Axis.FLOOR) {
            if ((word & 0x800) != 0) {
                sample = ~sample;
                angle = -angle & 0xFF;
                cursor.storeAngle(angle); // NEG.B (a4).
            }
            if ((word & 0x1000) != 0) {
                angle = (angle + 0x40) & 0xFF;
                cursor.storeAngle(angle); // ADDI.B #$40,(a4).
                angle = -angle & 0xFF;
                cursor.storeAngle(angle); // NEG.B (a4).
                angle = (angle - 0x40) & 0xFF;
                cursor.storeAngle(angle); // SUBI.B #$40,(a4).
            }
        } else {
            if ((word & 0x1000) != 0) {
                sample = ~sample;
                angle = (angle + 0x40) & 0xFF;
                cursor.storeAngle(angle); // ADDI.B #$40,(a4).
                angle = -angle & 0xFF;
                cursor.storeAngle(angle); // NEG.B (a4).
                angle = (angle - 0x40) & 0xFF;
                cursor.storeAngle(angle); // SUBI.B #$40,(a4).
            }
            if ((word & 0x800) != 0) {
                angle = -angle & 0xFF;
                cursor.storeAngle(angle); // NEG.B (a4).
            }
        }
        // All reached angle stores already occurred; a zero metric may now fall back.
        int value = cursor.axis == Axis.FLOOR ? shapes.height(id, sample & 15)
                : shapes.width(id, sample & 15);
        int metricFlip = cursor.axis == Axis.FLOOR ? 0x1000 : 0x800;
        return ((word ^ cursor.mirrorMask) & metricFlip) == 0 ? value : -value;
    }

    private int nativeBlockWord(int x, int y, int spriteFlags) {
        return switch (placements.lookup(x, y, spriteFlags)) {
            case CdTerrainCollision.Blank blank -> -1;
            case CdTerrainCollision.Available available -> available.word().rawWord();
            case CdTerrainCollision.Unavailable unavailable -> throw new IllegalStateException(
                    "Unavailable native collision word at $"
                            + Integer.toHexString(unavailable.address().nativeAddress())
                            + ": " + unavailable.reason());
        };
    }

    /** Per-call native angle-register state, never retained between probes. */
    private static final class Cursor {
        final Axis axis;
        final int solidityBit;
        final int mirrorMask;
        final int step;
        final int spriteFlags;
        final IntConsumer angleWrite;
        int angle = -1;
        boolean prefixReads; // Invocation-local opt-in; all legacy constructors leave it false.

        Cursor(Axis axis, int solidityBit, int mirrorMask, int step, int spriteFlags) {
            this(axis, solidityBit, mirrorMask, step, spriteFlags, null);
        }

        Cursor(Axis axis, int solidityBit, int mirrorMask, int step, int spriteFlags, IntConsumer angleWrite) {
            this.axis = axis; this.solidityBit = solidityBit; this.mirrorMask = mirrorMask;
            this.step = step; this.spriteFlags = spriteFlags; this.angleWrite = angleWrite;
        }

        void storeAngle(int value) {
            angle = value & 0xFF;
            if (angleWrite != null) angleWrite.accept(angle);
        }
    }

    /** Reached native read detail only; a public value is never proof that a read occurred. */
    public sealed interface UnownedReadDetail permits TerrainWord, CollisionId { }

    public record TerrainWord(CdTerrainCollision.Address address,
                              CdTerrainCollision.UnavailableReason reason) implements UnownedReadDetail {
        public TerrainWord { Objects.requireNonNull(address); Objects.requireNonNull(reason); }
    }

    public record CollisionId(int blockIndex) implements UnownedReadDetail { }

    /**
     * Origin marker emitted only by the opt-in terrain calls at their two bounded read owners.
     * The actual legacy exception remains intact for the caller's eventual terminal propagation.
     */
    public static final class UnownedTerrainRead extends RuntimeException {
        private final UnownedReadDetail detail;
        private final IllegalStateException originalException;

        private UnownedTerrainRead(UnownedReadDetail detail, IllegalStateException originalException) {
            super(originalException.getMessage(), originalException);
            this.detail = detail;
            this.originalException = originalException;
        }

        public UnownedReadDetail detail() { return detail; }
        public IllegalStateException originalException() { return originalException; }
    }

    /** Same FindLevelFloor algorithm and raw stores, with typed unavailable native reads. No preclear. */
    public Result floorWithPrefix(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags,
                                  IntConsumer rawAngleWrite) {
        Objects.requireNonNull(rawAngleWrite, "reached angle writer");
        return queryWithPrefix(Axis.FLOOR, x, y, solidityBit, mirrorMask, step, spriteFlags, rawAngleWrite);
    }

    /** Same FindLevelWall algorithm and raw stores, including writes before an adjacent read fails. */
    public Result wallWithPrefix(int x, int y, int solidityBit, int mirrorMask, int step, int spriteFlags,
                                 IntConsumer rawAngleWrite) {
        Objects.requireNonNull(rawAngleWrite, "reached angle writer");
        return queryWithPrefix(Axis.WALL, x, y, solidityBit, mirrorMask, step, spriteFlags, rawAngleWrite);
    }

    private Result queryWithPrefix(Axis axis, int x, int y, int solidityBit, int mirrorMask, int step,
                                    int spriteFlags, IntConsumer rawAngleWrite) {
        if (solidityBit < 0 || solidityBit > 15) {
            throw new IllegalArgumentException("Solidity bit must select the original native word");
        }
        var cursor = new Cursor(axis, solidityBit, mirrorMask & 0xFFFF, (short) step, spriteFlags & 0xFF, rawAngleWrite);
        cursor.prefixReads = true;
        int distance = first(cursor, x & 0xFFFF, y & 0xFFFF);
        return new Result((short) distance,
                cursor.angle < 0 ? OptionalInt.empty() : OptionalInt.of(cursor.angle));
    }

    private int nativeBlockWord(int x, int y, int spriteFlags, boolean prefixReads) {
        if (!prefixReads) return nativeBlockWord(x, y, spriteFlags);
        return switch (placements.lookup(x, y, spriteFlags)) {
            case CdTerrainCollision.Blank blank -> -1;
            case CdTerrainCollision.Available available -> available.word().rawWord();
            case CdTerrainCollision.Unavailable unavailable -> {
                var original = new IllegalStateException("Unavailable native collision word at $"
                        + Integer.toHexString(unavailable.address().nativeAddress())
                        + ": " + unavailable.reason());
                throw new UnownedTerrainRead(new TerrainWord(unavailable.address(), unavailable.reason()), original);
            }
        };
    }
}
