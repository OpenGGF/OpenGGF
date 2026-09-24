package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.rings.RingSpawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parses Sonic 3&amp;K ring placement data into {@link RingSpawn} records.
 *
 * <p>S3K uses a raw 4-byte ring record format:
 * <pre>
 *   Bytes 0-1: X position (16-bit, big-endian)
 *   Bytes 2-3: Y position (16-bit, big-endian)
 *   Terminator: 0xFFFF
 * </pre>
 *
 * <p>Unlike Sonic 2's preload step, the S3K ring collision routine advances
 * directly across these 4-byte records and does not expand row/column count
 * nibbles.
 *
 * <p>The pointer table at {@link Sonic3kConstants#RING_LOC_PTRS_ADDR} uses
 * 32-bit absolute addresses, indexed as {@code zone * 2 + act}.
 */
public class Sonic3kRingPlacement {
    private static final int RING_RECORD_SIZE = 4;
    private static final int TERMINATOR = 0xFFFF;

    private final RomByteReader rom;

    public Sonic3kRingPlacement(RomByteReader rom) {
        this.rom = rom;
    }

    /**
     * Loads ring spawns for the given zone and act.
     *
     * @param zone Zone index (0-based, e.g. 0=AIZ, 1=HCZ, ...)
     * @param act  Act index (0-based)
     * @return Sorted, immutable list of ring spawns
     */
    public List<RingSpawn> load(int zone, int act) {
        int ptrIndex = zone * 2 + act;
        int listAddr = rom.readU32BE(Sonic3kConstants.RING_LOC_PTRS_ADDR + ptrIndex * 4);

        if (listAddr == 0 || listAddr >= rom.size()) {
            return List.of();
        }

        return dropLeadingSentinel(parseRawRingRecords(rom, listAddr));
    }

    /**
     * Drops the leading {@code (0,0)} record that opens every S3K ring list but the Pachinko one.
     *
     * <p>{@code loc_EB52} counts {@code Perfect_rings_left} from {@code Ring_start_addr_ROM + 4},
     * and the ring manager's window search never reaches record 0 either: {@code loc_E8BE}
     * (X-ordered lists) and {@code loc_E904} (the Pachinko Y-ordered list) both build their search
     * key as {@code camera - 8} clamped up to 1 ({@code moveq #1,d4}) and then advance
     * {@code Ring_start_addr_ROM} while the key is above the record's managed coordinate. A record
     * sitting at coordinate 0 on both axes is therefore always stepped over, whichever manager runs.
     *
     * <p>The surviving rings keep their record index as {@code placementId}, which is also the index
     * the ROM's {@code Ring_status_table} walks in lockstep with the record list.
     */
    private static List<RingSpawn> dropLeadingSentinel(List<RingSpawn> rings) {
        return rings.stream()
                .filter(ring -> ring.placementId() != 0 || ring.x() != 0 || ring.y() != 0)
                .toList();
    }

    static List<RingSpawn> parseRawRingRecords(RomByteReader rom, int startAddr) {
        List<RingSpawn> spawns = new ArrayList<>();
        int cursor = startAddr;

        while (cursor + RING_RECORD_SIZE <= rom.size()) {
            int x = rom.readU16BE(cursor);
            if (x == TERMINATOR) {
                break;
            }

            spawns.add(new RingSpawn(x, rom.readU16BE(cursor + 2), spawns.size()));
            cursor += RING_RECORD_SIZE;
        }

        spawns.sort(Comparator.comparingInt(RingSpawn::x));
        return List.copyOf(spawns);
    }
}
