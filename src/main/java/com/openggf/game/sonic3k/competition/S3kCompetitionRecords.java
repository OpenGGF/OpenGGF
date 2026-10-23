package com.openggf.game.sonic3k.competition;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The native 80-byte Competition_saved_data block, supplied by the save owner.
 * No records are invented and this class performs no external writes.
 *
 * <p>sub_37430 (sonic3k.asm:74637-74683) indexes by (zone-$E)*16,
 * compares unsigned long times strictly, shifts the matching character bytes,
 * and calls Write_SaveGeneral only after a qualifying insertion. The returned
 * insertion rank is the host's permission to persist the changed block.
 */
public final class S3kCompetitionRecords implements RewindSnapshottable<S3kCompetitionRecords.State> {
    public static final int ARENA_COUNT = 5;
    public static final int ENTRIES_PER_ARENA = 3;
    public static final int BYTES_PER_ARENA = 16;
    public static final int BYTE_COUNT = ARENA_COUNT * BYTES_PER_ARENA;

    /** Packed native unsigned long, plus the unmodified native character byte. */
    public record Entry(long packedTime, int characterId) {
        public Entry {
            if (packedTime < 0 || packedTime > 0xFFFF_FFFFL)
                throw new IllegalArgumentException("Record time must fit a native unsigned long");
            requireByte(characterId, "character");
        }
    }

    public record ArenaRecords(S3kCompetitionSession.Arena arena, List<Entry> entries, int padding) {
        public ArenaRecords {
            Objects.requireNonNull(arena, "arena");
            entries = List.copyOf(entries);
            if (entries.size() != ENTRIES_PER_ARENA)
                throw new IllegalArgumentException("A native arena has exactly three records");
            requireByte(padding, "padding");
        }
    }

    /** Physical save order is ALZ, BPZ, DPZ, CGZ, EMZ, independently of menu/GP order. */
    public record State(List<ArenaRecords> arenas) {
        public State {
            arenas = List.copyOf(arenas);
            if (arenas.size() != ARENA_COUNT)
                throw new IllegalArgumentException("A native competition block has five arenas");
            for (int i = 0; i < ARENA_COUNT; i++) if (arenas.get(i).arena().zoneId() != 0xE + i)
                throw new IllegalArgumentException("Competition records must remain in native zone-ID order");
        }
    }

    private final byte[] data;

    public S3kCompetitionRecords(byte[] nativeBlock) {
        Objects.requireNonNull(nativeBlock, "nativeBlock");
        if (nativeBlock.length != BYTE_COUNT)
            throw new IllegalArgumentException("Expected the native 80-byte competition save block");
        data = nativeBlock.clone();
    }

    public byte[] encode() { return data.clone(); }

    public List<Entry> entries(S3kCompetitionSession.Arena arena) {
        int base = offset(arena);
        List<Entry> result = new ArrayList<>(ENTRIES_PER_ARENA);
        for (int rank = 0; rank < ENTRIES_PER_ARENA; rank++)
            result.add(new Entry(Integer.toUnsignedLong(readLong(base + rank * 4)), data[base + 12 + rank] & 0xFF));
        return List.copyOf(result);
    }

    public int padding(S3kCompetitionSession.Arena arena) { return data[offset(arena) + 15] & 0xFF; }

    /**
     * @return native new-record rank 0..2, or -1 when no byte changed and no save is due
     */
    public int insert(S3kCompetitionSession.Arena arena, long unsignedPackedTime, int characterId) {
        Entry candidate = new Entry(unsignedPackedTime, characterId);
        int base = offset(arena), rank = -1;
        int time = (int) candidate.packedTime();
        for (int i = 0; i < ENTRIES_PER_ARENA; i++) {
            if (Integer.compareUnsigned(time, readLong(base + i * 4)) < 0) { rank = i; break; }
        }
        if (rank < 0) return -1;
        // Byte/long copies follow the exact loc_37430/loc_37476/loc_3749A order.
        // Equal first place is deliberately allowed to insert below that entry.
        for (int i = ENTRIES_PER_ARENA - 1; i > rank; i--) {
            writeLong(base + i * 4, readLong(base + (i - 1) * 4));
            data[base + 12 + i] = data[base + 12 + i - 1];
        }
        writeLong(base + rank * 4, time);
        data[base + 12 + rank] = (byte) candidate.characterId();
        return rank;
    }

    public int insert(S3kCompetitionSession.Arena arena, long unsignedPackedTime,
            S3kCompetitionPlayer.Character character) {
        return insert(arena, unsignedPackedTime, Objects.requireNonNull(character).ordinal());
    }

    public static long packTime(int minutes, int seconds, int centiseconds) {
        requireByte(minutes, "minutes");
        if (seconds < 0 || seconds >= 60 || centiseconds < 0 || centiseconds >= 100)
            throw new IllegalArgumentException("Native time requires seconds 0..59 and centiseconds 0..99");
        return ((long) minutes << 16) | ((long) seconds << 8) | centiseconds;
    }

    public static long packCentiseconds(int totalCentiseconds) {
        if (totalCentiseconds < 0) throw new IllegalArgumentException("Negative record time");
        return packTime(totalCentiseconds / 6000, totalCentiseconds / 100 % 60, totalCentiseconds % 100);
    }

    @Override public String key() { return "s3k.competition.records"; }

    @Override public State capture() {
        List<ArenaRecords> arenas = new ArrayList<>(ARENA_COUNT);
        for (int i = 0; i < ARENA_COUNT; i++) {
            var arena = S3kCompetitionSession.Arena.forZone(0xE + i);
            arenas.add(new ArenaRecords(arena, entries(arena), padding(arena)));
        }
        return new State(arenas);
    }

    @Override public void restore(State state) {
        Objects.requireNonNull(state, "state");
        for (ArenaRecords arena : state.arenas()) {
            int base = offset(arena.arena());
            for (int rank = 0; rank < ENTRIES_PER_ARENA; rank++) {
                Entry entry = arena.entries().get(rank);
                writeLong(base + rank * 4, (int) entry.packedTime());
                data[base + 12 + rank] = (byte) entry.characterId();
            }
            data[base + 15] = (byte) arena.padding();
        }
    }

    private static int offset(S3kCompetitionSession.Arena arena) {
        return (Objects.requireNonNull(arena, "arena").zoneId() - 0xE) * BYTES_PER_ARENA;
    }
    private int readLong(int address) {
        return (data[address] & 0xFF) << 24 | (data[address + 1] & 0xFF) << 16
                | (data[address + 2] & 0xFF) << 8 | data[address + 3] & 0xFF;
    }
    private void writeLong(int address, int value) {
        data[address] = (byte) (value >>> 24); data[address + 1] = (byte) (value >>> 16);
        data[address + 2] = (byte) (value >>> 8); data[address + 3] = (byte) value;
    }
    private static void requireByte(int value, String field) {
        if (value < 0 || value > 0xFF) throw new IllegalArgumentException(field + " must fit a native byte");
    }
}
