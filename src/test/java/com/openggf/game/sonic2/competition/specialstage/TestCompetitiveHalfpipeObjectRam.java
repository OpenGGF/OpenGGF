package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.data.RomByteReader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCompetitiveHalfpipeObjectRam {
    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void retailOpcodesBoundBothAllocationScansBeforeTheLastPhysicalSlot() throws Exception {
        RomByteReader rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        assertEquals("43f8b2003a3c00764a11670843e9004051cdfff64e75",
                HexFormat.of().formatHex(rom.slice(0x6F8E, 22)));
        assertEquals("22483a3ccfc09a48ec4d5345650c4a11670843e9004051cdfff64e75",
                HexFormat.of().formatHex(rom.slice(0x6FA4, 28)));
        assertEquals(0x41F8B000, rom.readU32BE(0x15FA2), "RunObjects starts at physical B000");
        assertEquals(0x7E7F, rom.readU16BE(0x15FA6), "MOVEQ #$7F,d7 visits all 128 physical slots");
        int first = (rom.readU16BE(0x6F90) - 0xB000) / 0x40;
        int count = rom.readU16BE(0x6F94) + 1; // DBF visits zero as well.
        int excludedEnd = (rom.readU16BE(0x6FA8) - 0xB000) / 0x40;
        assertEquals(8, first);
        assertEquals(119, count);
        assertEquals(127, excludedEnd);
        assertEquals(first, CompetitiveHalfpipeObjectRam.FIRST_DYNAMIC_SLOT);
        assertEquals(first + count - 1, CompetitiveHalfpipeObjectRam.LAST_DYNAMIC_SLOT);
        assertEquals(excludedEnd, CompetitiveHalfpipeObjectRam.GUARD_SLOT);
        assertEquals(128, CompetitiveHalfpipeObjectRam.SLOT_COUNT);
    }

    @Test
    void fixedAndGuardSlotsRemainOutsideTheOrdinaryDynamicAllocator() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        for (int slot = 0; slot < 128; slot++) assertNull(ram.at(slot));
        for (int slot = 0; slot < 8; slot++) ram.fixed(slot, "fixed-" + slot);
        ram.replace(127, "guard");
        for (int slot = 8; slot <= 126; slot++) assertEquals(slot, ram.allocate("dynamic-" + slot));
        var full = ram.capture(value -> value);
        assertEquals(-1, ram.allocate("must-not-appear"));
        assertEquals(full, ram.capture(value -> value));
        assertEquals("fixed-0", ram.at(0));
        assertEquals("guard", ram.at(127));
    }

    @Test
    void fragmentedFullRamAlwaysSearchesTheLowestHoleAndFailureDoesNotMutateOccupants() {
        var ram = new CompetitiveHalfpipeObjectRam<Node>();
        for (int slot = 8; slot <= 126; slot++) ram.replace(slot, new Node("resident-" + slot, -1, slot));
        ram.clear(25); ram.clear(98);
        Node first = new Node("first", -1, 0), second = new Node("second", -1, 0);
        assertEquals(25, ram.allocate(first));
        assertEquals(98, ram.allocate(second));
        var snapshot = ram.capture(Node::encode);
        assertEquals(-1, ram.allocate(new Node("rejected", -1, 0)));
        assertEquals(snapshot, ram.capture(Node::encode));
        assertSame(first, ram.at(25)); assertSame(second, ram.at(98));
        assertNull(ram.at(127), "an empty physical guard is still not an allocation candidate");
    }

    @Test
    void afterCurrentIncludesTheCurrentSlotAndCanStartBeforeTheDynamicRange() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        assertEquals(3, ram.allocateAfter(3, "current-fixed"));
        assertEquals(4, ram.allocateAfter(3, "following-fixed"));
        ram.clear(3);
        assertEquals(3, ram.allocateAfter(3, "reused-current"));
        assertEquals(126, ram.allocateAfter(126, "last-allocatable"));
        assertEquals(-1, ram.allocateAfter(126, "exhausted"));
        assertEquals(-1, ram.allocateAfter(127, "guard"));
        assertNull(ram.at(127));
    }

    @Test
    void freeSlotQueriesNeverClaimAndRetainBothNativeScanBounds() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        assertEquals(8, ram.findFree());
        assertEquals(8, ram.findFree());
        assertNull(ram.at(8));
        assertEquals(3, ram.findFreeAtOrAfter(3));
        assertNull(ram.at(3));
        for (int slot = 8; slot <= 126; slot++) ram.replace(slot, "resident-" + slot);
        var full = ram.capture(value -> value);
        assertEquals(-1, ram.findFree());
        assertEquals(-1, ram.findFreeAtOrAfter(127));
        assertEquals(full, ram.capture(value -> value));
        ram.clear(77);
        assertEquals(77, ram.findFree());
        assertEquals(77, ram.findFreeAtOrAfter(77));
        assertEquals(-1, ram.findFreeAtOrAfter(78));
        assertEquals(77, ram.allocate("claimed"));
        assertEquals("claimed", ram.at(77));
    }

    @Test
    void afterCurrentFailureNeverWrapsIntoAnEarlierFreeSlot() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        for (int slot = 20; slot <= 126; slot++) ram.replace(slot, "occupied-" + slot);
        ram.replace(127, "guard");
        var before = ram.capture(value -> value);
        assertEquals(-1, ram.allocateAfter(20, "rejected"));
        assertEquals(before, ram.capture(value -> value));
        assertNull(ram.at(8));
    }

    @Test
    void livePassExecutesNewHigherOccupantsAndDefersReusedCurrentOrLowerSlots() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        ram.fixed(0, "driver"); ram.replace(10, "ten"); ram.replace(15, "old-fifteen"); ram.replace(127, "guard");
        List<String> seen = new ArrayList<>();
        ram.runPass((slot, node) -> {
            seen.add(slot + ":" + node);
            if (node.equals("driver")) {
                ram.clear(0);
                assertEquals(0, ram.allocateAfter(0, "current-reused"));
                assertEquals(11, ram.allocateAfter(10, "new-higher"));
                ram.replace(15, "new-fifteen");
            } else if (node.equals("ten")) ram.replace(1, "new-lower");
        });
        assertEquals(List.of("0:driver", "10:ten", "11:new-higher", "15:new-fifteen", "127:guard"), seen);
        seen.clear();
        ram.runPass((slot, node) -> seen.add(slot + ":" + node));
        assertEquals(List.of("0:current-reused", "1:new-lower", "10:ten", "11:new-higher", "15:new-fifteen", "127:guard"), seen);
    }

    @Test
    void livePassSkipsDeletedLaterSlotsAndObservesTheirReplacementIfReusedBeforeVisiting() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        ram.fixed(0, "driver"); ram.fixed(1, "old-one"); ram.fixed(2, "deleted");
        List<String> seen = new ArrayList<>();
        ram.runPass((slot, node) -> {
            seen.add(node);
            if (slot == 0) { ram.replace(1, "replacement"); ram.clear(2); }
        });
        assertEquals(List.of("driver", "replacement"), seen);
    }

    @Test
    void aPassRejectsReentryAndReleasesItsGuardAfterVisitorFailure() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        ram.fixed(0, "node");
        ram.runPass((slot, node) -> assertThrows(IllegalStateException.class,
                () -> ram.runPass((nestedSlot, nestedNode) -> fail("nested pass must not execute"))));
        assertThrows(IllegalArgumentException.class, () -> ram.runPass((slot, node) -> { throw new IllegalArgumentException("visitor"); }));
        List<Integer> seen = new ArrayList<>();
        ram.runPass((slot, node) -> seen.add(slot));
        assertEquals(List.of(0), seen);
    }

    @Test
    void snapshotRecreatesStableLinksAndExplicitEmptiesWithoutRetainingMutableNodes() {
        var ram = new CompetitiveHalfpipeObjectRam<Node>();
        Node originalParent = new Node("parent", 8, 7), originalChild = new Node("child", 0, 3);
        ram.fixed(0, originalParent); ram.replace(8, originalChild);
        var snapshot = ram.capture(Node::encode);
        assertEquals(128, snapshot.slots().size());
        assertEquals(new CompetitiveHalfpipeObjectRam.Slot<Frozen>(127, null), snapshot.slots().get(127));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.slots().clear());
        originalParent.counter = 999; originalChild.link = 42;
        ram.clear();
        ram.restore(snapshot, Node::decode);
        assertNotSame(originalParent, ram.at(0)); assertNotSame(originalChild, ram.at(8));
        assertEquals(7, ram.at(0).counter); assertEquals(0, ram.at(8).link);
        assertSame(ram.at(8), ram.at(ram.at(0).link));
        assertSame(ram.at(0), ram.at(ram.at(8).link));
        assertNull(ram.at(127));

        var other = new CompetitiveHalfpipeObjectRam<Node>();
        other.restore(snapshot, Node::decode);
        ram.at(8).counter = 88;
        assertEquals(3, other.at(8).counter);
        assertEquals(new Frozen("child", 0, 3), snapshot.slots().get(8).state());
        assertNotSame(ram.at(8), other.at(8));
    }

    @Test
    void captureRestoreAndForwardReplayRepeatSamePassChildCreationAtTheSameNativeSlot() {
        var ram = new CompetitiveHalfpipeObjectRam<Node>();
        ram.fixed(0, new Node("parent", -1, 0));
        var initial = ram.capture(Node::encode);
        step(ram); step(ram);
        var expected = ram.capture(Node::encode);
        Node firstChild = ram.at(8);
        assertEquals(new Frozen("parent", 8, 2), expected.slots().get(0).state());
        assertEquals(new Frozen("child", 0, 2), expected.slots().get(8).state());
        ram.restore(initial, Node::decode);
        step(ram); step(ram);
        assertEquals(expected, ram.capture(Node::encode));
        assertNotSame(firstChild, ram.at(8));
    }

    @Test
    void decoderFailureCannotPublishAPartlyRestoredRamImage() {
        var source = new CompetitiveHalfpipeObjectRam<String>();
        source.fixed(0, "first"); source.replace(8, "broken"); source.replace(127, "guard");
        var destination = new CompetitiveHalfpipeObjectRam<String>();
        destination.fixed(7, "original"); destination.replace(126, "original-last");
        var before = destination.capture(value -> value);
        assertThrows(IllegalStateException.class, () -> destination.restore(source.capture(value -> value), value -> {
            if (value.equals("broken")) throw new IllegalStateException("decoder");
            return "decoded-" + value;
        }));
        assertEquals(before, destination.capture(value -> value));
    }

    @Test
    void invalidSlotsAndNullCodecsCannotChangeThePhysicalImage() {
        var ram = new CompetitiveHalfpipeObjectRam<String>();
        ram.fixed(0, "original");
        assertThrows(IllegalArgumentException.class, () -> ram.fixed(8, "dynamic"));
        assertThrows(IndexOutOfBoundsException.class, () -> ram.at(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ram.replace(128, "outside"));
        assertThrows(IndexOutOfBoundsException.class, () -> ram.clear(128));
        assertThrows(IndexOutOfBoundsException.class, () -> ram.allocateAfter(-1, "outside"));
        assertThrows(IndexOutOfBoundsException.class, () -> ram.findFreeAtOrAfter(-1));
        assertThrows(NullPointerException.class, () -> ram.allocate(null));
        assertThrows(NullPointerException.class, () -> ram.replace(0, null));
        assertThrows(NullPointerException.class, () -> ram.capture(value -> null));
        assertThrows(IllegalArgumentException.class, () -> new CompetitiveHalfpipeObjectRam.State<String>(List.of()));
        assertEquals("original", ram.at(0));
        ram.clear();
        for (int slot = 0; slot < 128; slot++) assertNull(ram.at(slot));
    }

    private static void step(CompetitiveHalfpipeObjectRam<Node> ram) {
        ram.runPass((slot, node) -> {
            node.counter++;
            if (slot == 0 && node.link < 0) node.link = ram.allocate(new Node("child", 0, 0));
        });
    }

    private record Frozen(String kind, int link, int counter) { }
    private static final class Node {
        final String kind;
        int link, counter;
        Node(String kind, int link, int counter) { this.kind = kind; this.link = link; this.counter = counter; }
        Frozen encode() { return new Frozen(kind, link, counter); }
        static Node decode(Frozen state) { return new Node(state.kind(), state.link(), state.counter()); }
    }
}
