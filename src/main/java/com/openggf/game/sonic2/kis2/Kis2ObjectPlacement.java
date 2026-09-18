package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.game.sonic2.Sonic2ObjectPlacement;
import com.openggf.game.sonic2.ZoneAct;
import com.openggf.level.objects.ObjectSpawn;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * KiS2 object layouts ({@code docs/kis2/BRANCH_DIFFS.md} §Object placements).
 *
 * <p>KiS2 {@code ObjectsManager_Init} indexes {@code Off_Objects_KiS2} with
 * {@code zone*8 + act*4} and follows an absolute lock-on pointer. The table
 * points into the S&amp;K half for 16 rewritten acts, into the S2 cart for
 * HPZ, DEZ and SCZ, and into the chip for CNZ. Tier one resolves the first
 * two through {@link LockOnAddressSpace}; a chip pointer falls back to the
 * stock S2 layout with one logged warning per act.
 */
public class Kis2ObjectPlacement extends Sonic2ObjectPlacement {

    private static final Logger LOGGER = Logger.getLogger(Kis2ObjectPlacement.class.getName());
    private static final int ACTS_PER_ZONE = 2;

    private final RomByteReader sk;
    private final LockOnAddressSpace addressSpace;
    private final Set<Integer> warnedEntries = new HashSet<>();

    public Kis2ObjectPlacement(RomByteReader sk, RomByteReader s2, LockOnAddressSpace addressSpace) {
        super(s2);
        this.sk = sk;
        this.addressSpace = addressSpace;
    }

    /** Reads the table entry KiS2 uses for {@code zoneAct}. */
    public long pointerFor(ZoneAct zoneAct) {
        return Integer.toUnsignedLong(sk.readU32BE(
                Kis2Constants.OFF_OBJECTS_KIS2 + entryIndex(zoneAct) * 4));
    }

    /** Whether the pointer for {@code zoneAct} can be read without the chip. */
    public boolean isResolvable(ZoneAct zoneAct) {
        return addressSpace.resolve(pointerFor(zoneAct)).isPresent();
    }

    @Override
    public List<ObjectSpawn> load(ZoneAct zoneAct) {
        long pointer = pointerFor(zoneAct);
        Optional<LockOnAddressSpace.Read> read = addressSpace.resolve(pointer);
        if (read.isEmpty()) {
            int entry = entryIndex(zoneAct);
            if (warnedEntries.add(entry)) {
                LOGGER.warning("KiS2 object layout for zone " + zoneAct.zone() + " act "
                        + zoneAct.act() + " lives at 0x" + Long.toHexString(pointer)
                        + " in an unavailable lock-on window; using the stock Sonic 2 layout");
            }
            return super.load(zoneAct);
        }
        return CommonPlacementParser.parseObjectRecords(read.get().reader(), read.get().localAddress());
    }

    /**
     * {@code ObjectsManager_Init}: {@code (Current_ZoneAndAct ror.b 1) >> 5}
     * yields {@code zone*8 + act*4}, with act bit 0 only. The engine keeps
     * stock Sonic 2's single-act clamp for WFZ, DEZ and SCZ.
     */
    static int entryIndex(ZoneAct zoneAct) {
        int index = zoneAct.pointerIndex(ACTS_PER_ZONE);
        if (isSingleActZone(zoneAct.zone())) {
            index = zoneAct.zone() * ACTS_PER_ZONE;
        }
        return index;
    }

    private static boolean isSingleActZone(int zone) {
        // ROM zone IDs: WFZ=$06, DEZ=$0E, SCZ=$10 (same clamp as Sonic2ObjectPlacement)
        return zone == 6 || zone == 14 || zone == 16;
    }
}
