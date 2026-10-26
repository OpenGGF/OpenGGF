package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Player_ObjCollide selector and generic item routine write, with explicit other-handler boundaries.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Player Object Collision.asm:7-98, ObjColSizes:398-461.
 *
 * <p>The caller owns the negative oPlayerCtrl / oAnim=$2B gate and calls this at
 * Level/_Objects/Sonic/Main.asm:342-347, after Display/RecordPos/Water/Animate and before
 * SpecialChunks (:324-351). This class neither decrements the hurt timer nor runs animation.
 * The parent must stage an item image with the Body result and commit only after all Body work succeeds.
 * A HandlerBoundary is an unimplemented native entry, never a completed response or permission to continue.
 *
 * <p>Immutable inputs only. No live pool, callbacks, capture owner, source/world authentication,
 * player/global state, sound, object body, CPU memory mapping or S1/S2 touch consumer is retained here.
 */
public final class CdPlayerObjectTouch {
    /** Native field values at the actual call cut; integers represent unsigned words/bytes. */
    public record PlayerCut(int centreXWord, int centreYWord, int yRadiusByte, int mapFrameByte,
                            int hurtTimerWord) {
        public PlayerCut {
            requireWidth(centreXWord, 0xFFFF, "oX word");
            requireWidth(centreYWord, 0xFFFF, "oY word");
            requireWidth(yRadiusByte, 0xFF, "oYRadius byte");
            requireWidth(mapFrameByte, 0xFF, "oMapFrame byte");
            requireWidth(hurtTimerWord, 0xFFFF, "post-Display oPlayerHurt word");
        }
    }

    public enum Kind { NONE, ITEM, ENEMY, MONITOR, HAZARD, SPECIAL }

    /** One final selection, at most one detached slot image; no effects to replay. */
    public sealed interface Result permits NoTouch, Item, HandlerBoundary {
        Kind kind();
        Optional<CdObjectState> selectedSlot();
        Optional<CdObjectState> changedSlot();
    }

    public enum NoTouch implements Result {
        INSTANCE;
        @Override public Kind kind() { return Kind.NONE; }
        @Override public Optional<CdObjectState> selectedSlot() { return Optional.empty(); }
        @Override public Optional<CdObjectState> changedSlot() { return Optional.empty(); }
    }

    /** Native generic group-$40 item; hurt >= $5A suppresses its write but not its early return. */
    public static final class Item implements Result {
        private final CdObjectState selectedSlot;
        private final Optional<CdObjectState> changedSlot;

        private Item(CdObjectState selectedSlot, int hurtTimerWord) {
            this.selectedSlot = selectedSlot;
            this.changedSlot = hurtTimerWord < 0x5A
                    ? Optional.of(selectedSlot.addByte(CdObjectState.ByteField.ROUTINE, 2)) : Optional.empty();
        }

        @Override public Kind kind() { return Kind.ITEM; }
        @Override public Optional<CdObjectState> selectedSlot() { return Optional.of(selectedSlot); }
        @Override public Optional<CdObjectState> changedSlot() { return changedSlot; }
    }

    /**
     * Selected Player_TouchEnemy/Monitor/Hazard/Special entry. No handler response is implemented.
     * Parent must reject this boundary at the call cut until it owns the actual selected handler.
     */
    public static final class HandlerBoundary implements Result {
        private final Kind kind;
        private final CdObjectState selectedSlot;

        private HandlerBoundary(Kind kind, CdObjectState selectedSlot) {
            this.kind = kind;
            this.selectedSlot = selectedSlot;
        }

        @Override public Kind kind() { return kind; }
        @Override public Optional<CdObjectState> selectedSlot() { return Optional.of(selectedSlot); }
        @Override public Optional<CdObjectState> changedSlot() { return Optional.empty(); }
    }

    private final List<CdR11aObjectAssets.TouchSize> sizes;

    /**
     * Pure format consumer for the entire 63-pair ObjColSizes table, not an authenticated asset constructor.
     * Byte values include zero and $80..$FF. This copies the sequence and rejects non-byte half sizes.
     * Production callers use fromAssets; parent world/source binding remains separate.
     */
    public CdPlayerObjectTouch(List<CdR11aObjectAssets.TouchSize> sizes) {
        var copy = List.copyOf(Objects.requireNonNull(sizes, "ObjColSizes"));
        if (copy.size() != 63) { throw new IllegalArgumentException("ObjColSizes requires exactly 63 pairs"); }
        for (var size : copy) {
            requireWidth(size.halfWidth(), 0xFF, "ObjColSizes half-width byte");
            requireWidth(size.halfHeight(), 0xFF, "ObjColSizes half-height byte");
        }
        this.sizes = copy;
    }

    /** Runtime bytes come only from the existing immutable loaded object-asset view; no literal fallback. */
    public static CdPlayerObjectTouch fromAssets(CdR11aObjectAssets assets) {
        var view = Objects.requireNonNull(assets, "assets").view(CdR11aObjectAssets.Table.TOUCH_DIMENSIONS);
        return new CdPlayerObjectTouch(CdR11aObjectAssets.decodeTouchDimensions(view.bytes().copy()));
    }

    /**
     * Scans exactly Snapshot.slots(): all 96 dynamic slots, whole indices 32..127 in physical order.
     * Snapshot's private constructor and immutable slot images supply that structural contract.
     * Any pool's snapshot is a valid pure input; this consumer cannot certify world/source ownership.
     */
    public Result scan(PlayerCut player, CdObjectPool.Snapshot objects) {
        Objects.requireNonNull(player, "player cut");
        Objects.requireNonNull(objects, "dynamic snapshot");

        int playerLeft = (player.centreXWord() - 8) & 0xFFFF;
        // MOVEQ #0 / MOVE.B / SUBQ.B: upper bytes stay zero; e.g. radius 0 becomes $00FD, not $FFFD.
        int d5 = (player.yRadiusByte() - 3) & 0xFF;
        int playerTop = (player.centreYWord() - d5) & 0xFFFF;
        if (player.mapFrameByte() == 0x39) {
            playerTop = (playerTop + 0xC) & 0xFFFF;
            d5 = 0xA;
        }
        d5 = (d5 + d5) & 0xFFFF;

        for (var slot : objects.slots()) {
            // TST.B / BPL, then MOVE.B / BNE. Native does not test oID, even on a dirty ID-zero slot.
            if ((slot.spriteFlags() & 0x80) == 0) { continue; }
            int collisionType = slot.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE);
            if (collisionType == 0) { continue; }
            var size = selectedSize(collisionType); // Before geometry or handler/hurt gates.

            // .CheckWidth: the first SUB.W's carry is discarded by the second SUB.W.
            int d1 = size.halfWidth();
            int objectLeft = (slot.centreX() - d1) & 0xFFFF;
            int d0 = (objectLeft - playerLeft) & 0xFFFF;
            if (objectLeft < playerLeft) { // Second SUB.W borrowed; ADD.W doubled size must carry.
                d1 = (d1 + d1) & 0xFFFF;
                if (d0 + d1 <= 0xFFFF) { continue; }
            } else if (d0 > 0x10) { // .TouchRight: unsigned BHI; equality touches.
                continue;
            }

            // .CheckHeight: retain the same word carry/unsigned branches, including wrap asymmetry.
            d1 = size.halfHeight();
            int objectTop = (slot.centreY() - d1) & 0xFFFF;
            d0 = (objectTop - playerTop) & 0xFFFF;
            if (objectTop < playerTop) {
                d1 = (d1 + d1) & 0xFFFF;
                if (d1 + d0 <= 0xFFFF) { continue; }
            } else if (d0 > d5) { // .TouchBottom: unsigned BHI; equality touches.
                continue;
            }

            // Every selected overlap returns; unsupported entries never fall through to later items.
            int group = collisionType & 0xC0;
            if (group == 0) { return new HandlerBoundary(Kind.ENEMY, slot); }
            if (group == 0xC0) { return new HandlerBoundary(Kind.SPECIAL, slot); }
            if (group == 0x80) { return new HandlerBoundary(Kind.HAZARD, slot); }
            if ((collisionType & 0x3F) == 6) { return new HandlerBoundary(Kind.MONITOR, slot); }
            return new Item(slot, player.hurtTimerWord());
        }
        return NoTouch.INSTANCE;
    }

    private CdR11aObjectAssets.TouchSize selectedSize(int collisionType) {
        int selector = collisionType & 0x3F;
        if (selector == 0) {
            // Native points two bytes before ObjColSizes. That adjacent CPU data is outside this finite view.
            throw new IllegalArgumentException("Selected collision type has no bounded ObjColSizes entry: " + collisionType);
        }
        return sizes.get(selector - 1);
    }

    private static void requireWidth(int value, int mask, String field) {
        if ((value & ~mask) != 0) { throw new IllegalArgumentException(field + " exceeds its native width"); }
    }
}
