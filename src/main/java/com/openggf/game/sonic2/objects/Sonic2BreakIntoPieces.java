package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.Sonic2RomAddresses;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.SpriteMappingPiece;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** S2 BreakObjectToPieces allocation and ROM mapping semantics. */
final class Sonic2BreakIntoPieces {
    private Sonic2BreakIntoPieces() { }

    record Piece(SpriteMappingPiece mapping, int xVelocity, int yVelocity) { }

    static List<Piece> readPieces(ObjectServices services, int mappingAddress, int frame, int velocityAddress) {
        try {
            var rom = services.romReader();
            var mapping = S2SpriteDataLoader.loadStockMappingFrames(rom, mappingAddress).get(frame);
            int velocities = Sonic2RomAddresses.address(rom, velocityAddress);
            List<Piece> result = new ArrayList<>(mapping.pieces().size());
            for (int i = 0; i < mapping.pieces().size(); i++) {
                result.add(new Piece(mapping.pieces().get(i),
                        (short) rom.readU16BE(velocities + i * 4),
                        (short) rom.readU16BE(velocities + i * 4 + 2)));
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read S2 breakable mapping/velocities", failure);
        }
    }

    static <T extends AbstractObjectInstance> T replaceParent(
            AbstractObjectInstance parent, ObjectServices services, int count, IntFunction<T> factory) {
        int parentSlot = requireParentSlot(parent);
        T first = ObjectConstructionContext.with(services, parentSlot, () -> factory.apply(0));
        ObjectLifetimeOps.detachSlotForTransfer(parent);
        ObjectLifetimeOps.addReplacementAtTransferredSlot(services.objectManager(), first, parentSlot);
        addLaterPieces(services, parentSlot, count, factory);
        return first;
    }

    static void runFirstFragment(ObjectServices services, Sonic2BreakPieceObjectInstance first,
                                 int vIntRunCount, com.openggf.game.PlayableEntity player) {
        first.update(vIntRunCount, player);
        // Obj2F_Fragment (s2.asm:49294-49300) and
        // Obj32_Fragment (s2.asm:49433-49439) can immediately DeleteObject. Publish that free slot now; the manager's
        // current execution entry still names the replaced Java parent.
        if (first.isDestroyed()) services.objectManager().releaseSlot(first);
    }

    static int requireParentSlot(AbstractObjectInstance parent) {
        int slot = parent.getSlotIndex();
        if (slot < 0) throw new IllegalStateException("S2 breakup requires its live parent slot");
        return slot;
    }

    // BreakObjectToPieces (s2.asm:29727-29763): a1=a0 for piece zero;
    // every later piece scans AFTER a0, stopping at the first failed admission.
    static void addLaterPieces(ObjectServices services, int parentSlot, int count,
                               IntFunction<? extends AbstractObjectInstance> factory) {
        ObjectManager manager = Objects.requireNonNull(services.objectManager(), "objectManager");
        for (int i = 1; i < count; i++) {
            int slot = manager.allocateSlotAfter(parentSlot);
            if (slot < 0) break;
            int piece = i;
            var fragment = ObjectConstructionContext.with(services, slot, () -> factory.apply(piece));
            ObjectLifetimeOps.addDynamicAtReservedSlot(manager, fragment, slot);
        }
    }
}
