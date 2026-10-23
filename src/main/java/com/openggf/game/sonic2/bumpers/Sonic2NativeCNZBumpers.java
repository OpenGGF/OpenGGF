package com.openggf.game.sonic2.bumpers;

import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Session-injected native map bumpers. No ordinary placement or global services.
 * SpecialCNZBumpers_Init/Main (s2.asm:32380-32496),
 * Check_CNZ_bumpers (s2.asm:32499-32884), shipped fixBugs=0.
 *
 * <p>Call updateCursors once before initial RunObjects (Init mirrors P1 into P2),
 * then after each native object/deformation pass. Call checkPlayerCollision at
 * each human's TouchResponse endpoint, after rings, using the existing cursors.
 */
public final class Sonic2NativeCNZBumpers {
    private final CNZNativeBumperTable table;
    private final NativeCompetitionRoster roster;
    private final IntConsumer onBounceSlot;
    private State state = State.empty();
    private final RewindSnapshottable<State> rewind = new RewindSnapshottable<>() {
        @Override public String key() { return "sonic2_native_cnz_bumpers"; }
        @Override public State capture() { return state; }
        @Override public void restore(State snapshot) { state = Objects.requireNonNull(snapshot, "snapshot"); }
        @Override public void resetForMissingSnapshot() { reset(); }
    };

    public Sonic2NativeCNZBumpers(CNZNativeBumperTable table, NativeCompetitionRoster roster,
                                IntConsumer onBounceSlot) {
        this.table = Objects.requireNonNull(table, "table");
        this.roster = Objects.requireNonNull(roster, "active native roster");
        this.onBounceSlot = Objects.requireNonNull(onBounceSlot, "onBounceSlot");
    }

    public void reset() { state = State.empty(); }

    public RewindSnapshottable<?> rewindAdapter() { return rewind; }

    public void updateCursors() {
        if (!state.initialized()) {
            int left = windowStart(roster.camera(0).getX());
            int start = forward(0, left);
            int end = forward(start, (left + 0x150) & 0xFFFF);
            Pair pair = new Pair(start, end);
            state = new State(true, pair, pair);
        } else {
            Pair first = move(state.first(), roster.camera(0).getX());
            Pair second = move(state.second(), roster.camera(1).getX());
            state = new State(true, first, second);
        }
    }

    /** Only the two eight-byte Bumper_Manager_Addresses blocks are exchanged. */
    public void swapNativeCompetitionSlots() {
        state = new State(state.initialized(), state.second(), state.first());
    }

    private Pair move(Pair old, int cameraX) {
        int left = windowStart(cameraX);
        return new Pair(adjust(old.start(), left), adjust(old.end(), (left + 0x150) & 0xFFFF));
    }

    private int forward(int index, int edge) {
        while (edge > table.entry(index).x()) index++;
        return index;
    }

    private int adjust(int index, int edge) {
        index = forward(index, edge);
        while (edge <= table.entry(index - 1).x()) index--;
        return index;
    }

    private static int windowStart(int cameraX) {
        int word = cameraX & 0xFFFF;
        return word > 8 ? word - 8 : 1; // subq.w #8 / bhi (unsigned, including zero).
    }

    /**
     * Returns whether a bounce occurred. The first box overlap terminates this
     * native scan even if its diagonal handler returns without bouncing.
     * Caller dispatch owns routine eligibility; these guards mirror Control's
     * TouchResponse suppression, rather than rejecting invulnerability timers.
     */
    public boolean checkPlayerCollision(int slot) {
        if (slot < 0 || slot > 1) throw new IllegalArgumentException("Native slot must be 0 or 1");
        if (!state.initialized()) return false;
        AbstractPlayableSprite player = Objects.requireNonNull(roster.player(slot), "native player");
        if (player.getDead() || player.isHurt() || (slot == 0 && player.isDebugMode())
                || player.isTouchResponseSuppressedByObjectControl()) return false;
        Pair pair = slot == 0 ? state.first() : state.second();
        int left = (player.getCentreX() - 9) & 0xFFFF;
        int radius = ((player.getYRadius() & 0xFF) - 3) & 0xFF;
        int top = (player.getCentreY() - radius) & 0xFFFF;
        if ((player.getMappingFrame() & 0xFF) == 0x4D) { // fixBugs=0, also applies to Tails.
            top = (top + 0xC) & 0xFFFF;
            radius = 0xA;
        }
        for (int index = pair.start(); index < pair.end(); index++) {
            CNZNativeBumperTable.Entry bumper = table.entry(index);
            CNZBumperType type = CNZBumperType.fromId((bumper.id() & 0xE) >> 1);
            if (type == null) throw new IllegalStateException("Invalid native bumper dimensions at " + bumper.address());
            if (overlaps(bumper.x(), type.getHalfWidth(), left, 0x12)
                    && overlaps(bumper.y(), type.getHalfHeight(), top, radius * 2)) {
                if (!bounce(player, bumper)) return false;
                player.setAir(true);
                player.setRollingJump(false);
                player.setPushing(false);
                player.setJumping(false);
                onBounceSlot.accept(slot);
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(int centre, int half, int playerStart, int size) {
        int start = (centre - half) & 0xFFFF;
        int delta = (start - playerStart) & 0xFFFF;
        return start >= playerStart ? delta <= size : delta + half * 2 > 0xFFFF;
    }

    int startAddress(int slot) {
        return table.entry(slot == 0 ? state.first().start() : state.second().start()).address();
    }

    int endAddress(int slot) {
        return table.entry(slot == 0 ? state.first().end() : state.second().end()).address();
    }

    private static boolean bounce(AbstractPlayableSprite p, CNZNativeBumperTable.Entry b) {
        int dx = (short) (b.x() - p.getCentreX());
        int dy = (short) (b.y() - p.getCentreY());
        int negX = (short) -dx;
        int negY = (short) -dy;
        int angle;
        // Handler dispatch uses the entire word, unlike the dimension lookup's $E mask.
        switch (b.id()) {
            case 0, 2 -> {
                if (negY >= 0x20) { p.setYSpeed((short) 0xA00); return true; }
                int slopeX = b.id() == 0 ? dx : negX;
                int sideX = b.id() == 0 ? negX : dx;
                if (sideX >= 0x20) {
                    p.setXSpeed((short) (b.id() == 0 ? 0xA00 : -0xA00)); return true;
                }
                int surfaceY = (Math.min(slopeX, 0x20) + b.y() - 8) & 0xFFFF;
                if (surfaceY >= ((p.getCentreY() + 0xE) & 0xFFFF)) return false;
                angle = b.id() == 0 ? 0x20 : 0x60;
            }
            case 4, 6 -> {
                if ((b.id() == 4 ? negY : dy) >= 8) {
                    p.setYSpeed((short) (b.id() == 4 ? 0xA00 : -0xA00)); return true;
                }
                if (dx >= 0x40) { p.setXSpeed((short) -0xA00); return true; }
                if (negX >= 0x40) { p.setXSpeed((short) 0xA00); return true; }
                angle = b.id() == 4 ? (negX < 0 ? 0x38 : 0x48) : (negX < 0 ? 0xC8 : 0xB8);
            }
            case 8, 10 -> {
                if ((b.id() == 8 ? negX : dx) >= 8) {
                    // Both shipped handlers write +$A00, including the right-facing bumper.
                    p.setXSpeed((short) 0xA00); return true;
                }
                if (dy >= 0x40) { p.setYSpeed((short) -0xA00); return true; }
                // The lower tip writes X, not Y (fixBugs=0 source).
                if (negY >= 0x40) { p.setXSpeed((short) 0xA00); return true; }
                angle = b.id() == 8 ? (negY < 0 ? 0x08 : 0xF8) : (negY < 0 ? 0x78 : 0x88);
            }
            default -> throw new IllegalStateException("Invalid native bumper handler at " + b.address());
        }
        int incoming = TrigLookupTable.calcAngle(p.getXSpeed(), p.getYSpeed());
        // CalcAngle (s2.asm:4037-4074) can return WORD $100 after neg/add $100.
        // Recover that case from the byte-angle helper before native word subtraction.
        if (incoming == 0 && p.getXSpeed() > 0 && p.getYSpeed() < 0) incoming = 0x100;
        int delta = (short) (incoming - angle);
        int outgoing = (Math.abs(delta) & 0xFF) < 0x38 ? (-delta + angle) & 0xFF : angle;
        p.setXSpeed((short) ((-0xA00 * TrigLookupTable.cosHex(outgoing)) >> 8));
        p.setYSpeed((short) ((-0xA00 * TrigLookupTable.sinHex(outgoing)) >> 8));
        return true;
    }

    // Immutable values make captures independent of subsequent cursor movement/swaps.
    private record Pair(int start, int end) { }
    private record State(boolean initialized, Pair first, Pair second) {
        static State empty() { return new State(false, new Pair(0, 0), new Pair(0, 0)); }
    }
}
