package com.openggf.game.sonic3k.render;

import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Copy-on-write rewind snapshots for the S3K physical display world DATA.
 * Checkpoints taken while nothing writes must share one immutable snapshot;
 * every write path must produce a fresh one without disturbing held checkpoints,
 * and a restore must adopt the snapshot it restored from.
 *
 * <p>Each write-path check compares the incrementally captured snapshot with a
 * reference built by replaying the same operations on a bank/queue that was
 * never captured, so a write that forgot to invalidate the shared image fails.
 */
class TestS3kPhysicalDisplayRewindCopyOnWrite {
    private static final int A = 0xB000;
    private static final int B = 0xB04A;
    private static final S3kPhysicalDisplayQueue.CameraWords CAMERA =
            new S3kPhysicalDisplayQueue.CameraWords(0, 0, 0x7FF);

    @BeforeEach void configure() {
        com.openggf.game.session.EngineServices.configure(com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
    }
    @AfterEach void reset() { com.openggf.tests.TestEnvironment.resetAll(); }

    private record BankOp(String name, Consumer<S3kPhysicalSstHeaderBank> body) { }
    private record QueueOp(String name, Consumer<S3kPhysicalDisplayQueue> body) { }

    private static List<BankOp> bankOps(S3kPhysicalSstHeaderBank.Snapshot slotSource) {
        return List.of(
                new BankOp("clearLevelObjectRam", S3kPhysicalSstHeaderBank::clearLevelObjectRam),
                new BankOp("writeByte", b -> b.writeByte(A, 6, 0x12, "test byte")),
                new BankOp("writeWord", b -> b.writeWord(A, 0x10, 0x3456, "test word")),
                new BankOp("writeLong", b -> b.writeLong(B, 0, 0x01020304L, "test long")),
                new BankOp("writeRenderFlags", b -> b.writeRenderFlags(A, 0x84)),
                new BankOp("invalidatePositionField", b -> b.invalidatePositionField(A, 0x10, 2)),
                new BankOp("writeWord again", b -> b.writeWord(A, 0x10, 0x0100, "test word")),
                new BankOp("invalidateField", b -> b.invalidateField(B, 2, 2)),
                new BankOp("clearSprite", b -> b.clearSprite(B)),
                new BankOp("restoreSlot", b -> b.restoreSlot(A, slotSource)));
    }

    @Test
    void bankCapturesShareOneSnapshotUntilAWrite() {
        var bank = new S3kPhysicalSstHeaderBank();
        var first = bank.capture();
        assertSame(first, bank.capture(), "unchanged captures must share the snapshot");
        bank.clearLevelObjectRam();
        var second = bank.capture();
        assertNotSame(first, second);
        assertSame(second, bank.capture());
        assertFalse(first.knownBytes()[0], "held checkpoint must keep its pre-write contents");
        assertTrue(second.knownBytes()[0]);
    }

    @Test
    void everyBankWritePathInvalidatesTheSharedSnapshot() {
        var slotSource = new S3kPhysicalSstHeaderBank().capture(); // all unknown
        var ops = bankOps(slotSource);
        var bank = new S3kPhysicalSstHeaderBank();
        var held = new ArrayList<S3kPhysicalSstHeaderBank.Snapshot>();
        var references = new ArrayList<S3kPhysicalSstHeaderBank.Snapshot>();
        for (int k = 0; k < ops.size(); k++) {
            var before = bank.capture();
            ops.get(k).body().accept(bank);
            var after = bank.capture();
            var reference = new S3kPhysicalSstHeaderBank();
            for (int n = 0; n <= k; n++) ops.get(n).body().accept(reference);
            var expected = reference.capture();
            assertNotSame(before, after, ops.get(k).name() + " must not reuse the pre-write snapshot");
            assertEquals(expected, after, ops.get(k).name() + " capture must reflect the write");
            held.add(after);
            references.add(expected);
        }
        for (int k = 0; k < held.size(); k++)
            assertEquals(references.get(k), held.get(k), "held checkpoint " + k + " changed after later writes");
    }

    @Test
    void bankRestoreAdoptsTheRestoredSnapshotAndRoundTrips() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.clearLevelObjectRam();
        bank.writeWord(A, 0xA, 0x2468, "test word");
        var saved = bank.capture();
        bank.writeWord(A, 0xA, 0x1357, "test word");
        bank.invalidateField(A, 0xA, 2);
        assertNotEquals(saved, bank.capture());
        bank.restore(saved);
        assertSame(saved, bank.capture(), "restore must share the restored snapshot");
        assertEquals(0x2468, bank.readWord(A, 0xA));
        bank.writeByte(A, 4, 1, "test byte");
        assertNotSame(saved, bank.capture());
        assertFalse(saved.equals(bank.capture()));
        assertEquals(0x2468, readWord(saved.image(), 0xA), "restored snapshot must stay immutable");
    }

    private static List<QueueOp> queueOps(S3kPhysicalSstHeaderBank projection) {
        return List.of(
                new QueueOp("submit", q -> q.submit(A, 0)),
                new QueueOp("submit second", q -> q.submit(A, 0x80)),
                new QueueOp("beginPass", q -> q.beginPass(S3kPhysicalDisplayQueue.Consumer.NORMAL)),
                new QueueOp("publishNext", q -> q.publishNext(projection, CAMERA)),
                new QueueOp("finishVisit", S3kPhysicalDisplayQueue::finishVisit),
                new QueueOp("publishNext region 1", q -> q.publishNext(projection, CAMERA)),
                new QueueOp("finishVisit region 1", S3kPhysicalDisplayQueue::finishVisit),
                new QueueOp("publishNext end of pass", q -> assertNull(q.publishNext(projection, CAMERA))),
                new QueueOp("restore empty", q -> q.restore(new S3kPhysicalDisplayQueue().capture())));
    }

    private static S3kPhysicalSstHeaderBank headerBank() {
        var bank = new S3kPhysicalSstHeaderBank();
        bank.clearLevelObjectRam();
        return bank;
    }

    @Test
    void everyQueueWritePathInvalidatesTheSharedSnapshot() {
        var queue = new S3kPhysicalDisplayQueue();
        var ops = queueOps(headerBank());
        var held = new ArrayList<S3kPhysicalDisplayQueue.Snapshot>();
        var references = new ArrayList<S3kPhysicalDisplayQueue.Snapshot>();
        assertSame(queue.capture(), queue.capture());
        for (int k = 0; k < ops.size(); k++) {
            var before = queue.capture();
            ops.get(k).body().accept(queue);
            var after = queue.capture();
            var reference = new S3kPhysicalDisplayQueue();
            var referenceOps = queueOps(headerBank());
            for (int n = 0; n <= k; n++) referenceOps.get(n).body().accept(reference);
            var expected = reference.capture();
            assertNotSame(before, after, ops.get(k).name() + " must not reuse the pre-write snapshot");
            assertEquals(expected, after, ops.get(k).name() + " capture must reflect the write");
            assertSame(after, queue.capture());
            held.add(after);
            references.add(expected);
        }
        for (int k = 0; k < held.size(); k++)
            assertEquals(references.get(k), held.get(k), "held checkpoint " + k + " changed after later writes");
    }

    @Test
    void queueRestoreAdoptsTheRestoredSnapshot() {
        var queue = new S3kPhysicalDisplayQueue();
        queue.submit(A, 0);
        var saved = queue.capture();
        queue.submit(B, 0);
        assertNotEquals(saved, queue.capture());
        queue.restore(saved);
        assertSame(saved, queue.capture());
        queue.submit(B, 0);
        assertNotSame(saved, queue.capture());
        assertEquals(2, readWord(saved.image(), 0), "restored snapshot must stay immutable");
    }

    @Test
    void worldCheckpointsShareUntilAWriteAndRestoreRoundTrips() throws Exception {
        var world = owner(new WorldSession(new Sonic3kGameModule()));
        var bank = (S3kPhysicalSstHeaderBank) field(world, "bank");
        var queue = (S3kPhysicalDisplayQueue) field(world, "queue");

        Object initial = world.capture();
        assertSame(initial, world.capture(), "idle checkpoints must share one snapshot");

        bank.clearLevelObjectRam();
        bank.writeWord(A, 0xA, 0x4321, "test word");
        queue.submit(A, 0);
        Object seeded = world.capture();
        assertNotSame(initial, seeded);
        assertNotEquals(initial, seeded);
        assertSame(seeded, world.capture());

        queue.submit(B, 0); // a queue-only write must also produce a new checkpoint
        Object queued = world.capture();
        assertNotSame(seeded, queued);
        assertNotEquals(seeded, queued);

        bank.writeWord(A, 0xA, 0x1111, "test word");
        Object later = world.capture();
        assertNotEquals(queued, later);

        world.restore(seeded);
        assertSame(seeded, world.capture(), "restore must share the restored snapshot");
        assertEquals(0x4321, bank.readWord(A, 0xA));
        assertEquals(2, readWord(queue.capture().image(), 0));

        world.restore(initial);
        assertSame(initial, world.capture());
        assertFalse(bank.isKnown(A, 0xA));
        assertThrows(IllegalStateException.class, () -> bank.readWord(A, 0xA));

        world.restore(later);
        assertEquals(later, world.capture());
        assertEquals(0x1111, bank.readWord(A, 0xA));
        assertEquals(4, readWord(queue.capture().image(), 0));
    }

    private static int readWord(byte[] image, int offset) {
        return ((image[offset] & 0xFF) << 8) | (image[offset + 1] & 0xFF);
    }

    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }

    private static S3kPhysicalDisplayWorld owner(WorldSession world) throws Exception {
        return (S3kPhysicalDisplayWorld) field(world, "worldPhysicalData");
    }
}
