package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Retail stream bytes exercise native allocation, cursor publication and restore as one path. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeObjectStream {
    private CompetitiveHalfpipeRomData data;
    private byte[] raw;

    @BeforeEach
    void setUp() throws Exception {
        data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
        raw = data.loader().getObjectLocations().clone();
    }

    @Test
    void nativeHeadersAndEveryFirstTerminatorComeFromTheInjectedRom() {
        assertEquals("000e01f704ca08ce0c190fc4150d", HexFormat.of().formatHex(raw, 0, 14));
        assertEquals(6880, raw.length);
        assertEquals("43f8b2003a3c00764a11670843e9004051cdfff64e75",
                HexFormat.of().formatHex(data.rom().slice(0x6F8E, 22)));
        assertEquals("0c380004db0d660000a070001038db0ab038db4c6700009211c0db4c",
                HexFormat.of().formatHex(data.rom().slice(0x5534, 28)));
        for (int stage = 0; stage < 7; stage++) {
            CompetitiveHalfpipeObjectStream stream = new CompetitiveHalfpipeObjectStream(data);
            stream.load(stage);
            int header = word(raw, stage * 2);
            assertEquals(new CompetitiveHalfpipeObjectStream.State(stage, 0xFF, header), stream.capture());
            assertEquals(0xFF, raw[header] & 0xFF);
            CompetitiveHalfpipeObjectRam<Node> ram = new CompetitiveHalfpipeObjectRam<>();
            Factory factory = new Factory();
            stream.step(0, 3, 16, ram, factory);
            assertEquals(header, stream.capture().publishedCursor());
            stream.step(0, 4, 16, ram, factory);
            assertEquals(header + 1, stream.capture().publishedCursor());
            assertEquals(0, stream.capture().lastSegment());
            assertTrue(factory.created.isEmpty());
            assertNull(ram.at(8), "FF finds a candidate without claiming its object ID");
        }
    }

    @Test
    void ffRequiresFreeDynamicRamAndFailureCannotRetryDuringTheSameSegment() {
        CompetitiveHalfpipeObjectStream stream = stream(0);
        CompetitiveHalfpipeObjectRam<Node> ram = full();
        Factory factory = new Factory();
        assertNull(ram.at(0));
        assertNull(ram.at(127));
        stream.step(0, 4, 16, ram, factory);
        assertEquals(new CompetitiveHalfpipeObjectStream.State(0, 0, 14), stream.capture());
        assertTrue(factory.created.isEmpty(), "fixed slots and the guard are not allocation candidates");
        ram.clear(50);
        stream.step(0, 4, 16, ram, factory);
        assertEquals(14, stream.capture().publishedCursor(), "LastSegment was published before the failed allocation");
        assertNull(ram.at(50));
        stream.step(1, 4, 24, ram, factory);
        assertEquals(15, stream.capture().publishedCursor());
        assertNull(ram.at(50), "reading FF still leaves the only free candidate unclaimed");
        assertTrue(factory.created.isEmpty());
    }

    @Test
    void partialGroupFailureRetainsItsPrefixAndNextSegmentDuplicatesItWithNewDepthBase() {
        CompetitiveHalfpipeObjectStream stream = stream(0);
        CompetitiveHalfpipeObjectRam<Node> ram = new CompetitiveHalfpipeObjectRam<>();
        Factory factory = new Factory();
        for (int segment = 0; segment < 5; segment++) stream.step(segment, 4, 16, ram, factory);
        assertEquals(19, stream.capture().publishedCursor());
        assertEquals("00400148024803480440013802380338ff", HexFormat.of().formatHex(raw, 19, 36));
        for (int slot = 10; slot <= 126; slot++) ram.replace(slot, Node.blocker());
        stream.step(5, 4, 16, ram, factory);
        assertEquals(Node.item(false, 64, 0x40), ram.at(8));
        assertEquals(Node.item(false, 68, 0x48), ram.at(9));
        assertEquals(19, stream.capture().publishedCursor());
        assertEquals(2, factory.created.size());
        ram.clear(10);
        stream.step(5, 4, 24, ram, factory);
        assertNull(ram.at(10), "freeing a slot cannot retry the already-seen segment");
        stream.step(6, 4, 24, ram, factory);
        assertEquals(Node.item(false, 96, 0x40), ram.at(10), "the old prefix restarts with the NEW geometry's base");
        assertEquals(19, stream.capture().publishedCursor());
        for (int slot = 11; slot <= 20; slot++) ram.clear(slot);
        stream.step(7, 4, 12, ram, factory);
        assertEquals(36, stream.capture().publishedCursor());
        assertEquals(Node.item(false, 48, 0x40), ram.at(11));
        assertEquals(Node.item(false, 52, 0x48), ram.at(12));
        assertEquals(Node.item(false, 60, 0x38), ram.at(18));
        assertNull(ram.at(19), "the successful FF terminator needs but does not consume this candidate");
        assertEquals(11, factory.created.size(), "both retained prefixes coexist with the complete retry");
    }

    @Test
    void unavailableMarkerCannotForgeCheckpointAndSuccessfulMarkersClaimTheirCandidate() {
        for (int marker : new int[]{0xFC, 0xFE, 0xFD}) {
            Group group = findMarker(0, marker);
            assertTrue(group.items().isEmpty(), "these independent retail marker groups contain no preceding items");
            CompetitiveHalfpipeObjectStream stream = stream(0);
            stream.restore(new CompetitiveHalfpipeObjectStream.State(0, group.segment() - 1, group.start()));
            CompetitiveHalfpipeObjectRam<Node> ram = full();
            Factory factory = new Factory();
            stream.step(group.segment(), 4, 16, ram, factory);
            assertTrue(factory.created.isEmpty());
            assertEquals(group.start(), stream.capture().publishedCursor());
            ram.clear(8);
            stream.step(group.segment(), 4, 16, ram, factory);
            assertTrue(factory.created.isEmpty(), "the failed marker cannot retry within the same segment");
            stream.step(group.segment() + 1, 4, 12, ram, factory);
            assertEquals(List.of(Node.marker(marker)), factory.created);
            assertEquals(Node.marker(marker), ram.at(8));
            assertEquals(group.end(), stream.capture().publishedCursor());
        }
    }

    @Test
    void actualRawPaddingRemainsReadableAfterTheCompetitiveFinalMarker() {
        CompetitiveHalfpipeObjectStream stream = stream(0);
        CompetitiveHalfpipeObjectRam<Node> ram = new CompetitiveHalfpipeObjectRam<>();
        Factory factory = new Factory();
        int segment = 0;
        while (factory.created.stream().noneMatch(node -> node.marker() == 0xFD)) {
            assertTrue(segment < 100);
            ram.clear();
            stream.step(segment++, 4, 16, ram, factory);
        }
        assertEquals(500, stream.capture().publishedCursor());
        assertEquals("ffffffff", HexFormat.of().formatHex(raw, 500, 504));
        for (int remaining = 0; remaining < 4; remaining++) {
            ram.clear();
            stream.step(segment++, 4, 16, ram, factory);
        }
        assertEquals(504, stream.capture().publishedCursor(), "FD does not install an artificial stream-generation gate");
    }

    @Test
    void streamAndPhysicalRamRecreateAndRepeatTheirForwardAllocationHistory() {
        CompetitiveHalfpipeObjectStream stream = stream(6);
        CompetitiveHalfpipeObjectRam<Node> ram = new CompetitiveHalfpipeObjectRam<>();
        Factory factory = new Factory();
        for (int segment = 0; segment < 16; segment++) run(stream, ram, factory, segment);
        var savedStream = stream.capture();
        var savedRam = ram.capture(node -> node);
        List<Combined> expected = new ArrayList<>();
        for (int segment = 16; segment < 46; segment++) {
            run(stream, ram, factory, segment);
            expected.add(new Combined(stream.capture(), ram.capture(node -> node)));
        }

        CompetitiveHalfpipeObjectStream restored = stream(2);
        restored.restore(savedStream);
        CompetitiveHalfpipeObjectRam<Node> restoredRam = new CompetitiveHalfpipeObjectRam<>();
        restoredRam.restore(savedRam, node -> new Node(node.blocked(), node.bomb(), node.depth(), node.angle(), node.marker()));
        assertEquals(savedRam, restoredRam.capture(node -> node));
        for (var slot : savedRam.slots()) if (slot.state() != null) assertNotSame(slot.state(), restoredRam.at(slot.index()));
        Factory restoredFactory = new Factory();
        for (int index = 0; index < expected.size(); index++) {
            run(restored, restoredRam, restoredFactory, index + 16);
            assertEquals(expected.get(index), new Combined(restored.capture(), restoredRam.capture(node -> node)));
        }
        restored.load(2);
        assertEquals(new CompetitiveHalfpipeObjectStream.State(2, 0xFF, word(raw, 4)), restored.capture());
        assertEquals(expected.getLast().stream(), stream.capture(), "another stream owner's load cannot move this timeline");
    }

    @Test
    void constructorSnapshotsInjectedBytesAndRestoreRejectsInvalidHeadersOrCursors() throws Exception {
        CompetitiveHalfpipeObjectStream stream = new CompetitiveHalfpipeObjectStream(data);
        var unloaded = stream.capture();
        assertThrows(IllegalStateException.class,
                () -> stream.step(0, 4, 16, new CompetitiveHalfpipeObjectRam<>(), new Factory()));
        stream.restore(unloaded);
        stream.load(0);
        byte[] loaderBytes = data.loader().getObjectLocations();
        byte previous = loaderBytes[14];
        try {
            loaderBytes[14] = 0;
            Factory factory = new Factory();
            stream.step(0, 4, 16, new CompetitiveHalfpipeObjectRam<>(), factory);
            assertTrue(factory.created.isEmpty());
            assertEquals(15, stream.capture().publishedCursor(), "the loaded owner holds its own raw byte snapshot");
        } finally {
            loaderBytes[14] = previous;
        }
        for (var invalid : List.of(new CompetitiveHalfpipeObjectStream.State(-2, 0, 14),
                new CompetitiveHalfpipeObjectStream.State(7, 0, 14),
                new CompetitiveHalfpipeObjectStream.State(0, -1, 14),
                new CompetitiveHalfpipeObjectStream.State(0, 256, 14),
                new CompetitiveHalfpipeObjectStream.State(0, 0, 0),
                new CompetitiveHalfpipeObjectStream.State(0, 0, raw.length + 1),
                new CompetitiveHalfpipeObjectStream.State(-1, 0xFF, 14))) {
            var before = stream.capture();
            assertThrows(IllegalArgumentException.class, () -> stream.restore(invalid));
            assertEquals(before, stream.capture(), "invalid restoration does not partially publish state");
        }
    }

    private CompetitiveHalfpipeObjectStream stream(int stage) {
        var stream = new CompetitiveHalfpipeObjectStream(data);
        stream.load(stage);
        return stream;
    }

    private static CompetitiveHalfpipeObjectRam<Node> full() {
        var ram = new CompetitiveHalfpipeObjectRam<Node>();
        for (int slot = 8; slot <= 126; slot++) ram.replace(slot, Node.blocker());
        return ram;
    }

    private static void run(CompetitiveHalfpipeObjectStream stream, CompetitiveHalfpipeObjectRam<Node> ram,
                            Factory factory, int segment) {
        // Deterministic external lifetimes exercise both pressure and holes;
        // no object-stream implementation state is used to choose them.
        ram.clear(8 + segment % 119);
        ram.clear(8 + (segment * 7 + 13) % 119);
        var before = stream.capture();
        stream.step(segment, 2, 24, ram, factory);
        assertEquals(before, stream.capture());
        stream.step(segment, 4, new int[]{24, 16, 12}[segment % 3], ram, factory);
    }

    /** Independent structural walk over the decompressed retail byte stream. */
    private Group findMarker(int stage, int expected) {
        int cursor = word(raw, stage * 2);
        for (int segment = 0; cursor < raw.length; segment++) {
            int start = cursor;
            List<Node> items = new ArrayList<>();
            while ((raw[cursor] & 0x80) == 0) {
                int record = raw[cursor++] & 0xFF;
                items.add(Node.item((record & 0x40) != 0, record & 0x3F, raw[cursor++] & 0xFF));
            }
            int marker = raw[cursor++] & 0xFF;
            if (marker == expected) return new Group(segment, start, cursor, items);
        }
        throw new AssertionError("Missing retail marker " + expected);
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private record Group(int segment, int start, int end, List<Node> items) { }
    private record Combined(CompetitiveHalfpipeObjectStream.State stream, CompetitiveHalfpipeObjectRam.State<Node> ram) { }
    private record Node(boolean blocked, boolean bomb, int depth, int angle, int marker) {
        static Node blocker() { return new Node(true, false, 0, 0, 0); }
        static Node item(boolean bomb, int depth, int angle) { return new Node(false, bomb, depth, angle, 0); }
        static Node marker(int raw) { return new Node(false, false, 0, 0, raw); }
    }
    private static final class Factory implements CompetitiveHalfpipeObjectStream.Factory<Node> {
        private final List<Node> created = new ArrayList<>();
        @Override public Node item(boolean bomb, int depth, int angle) {
            Node node = Node.item(bomb, depth, angle);
            created.add(node);
            return node;
        }
        @Override public Node marker(int raw) {
            Node node = Node.marker(raw);
            created.add(node);
            return node;
        }
    }
}
