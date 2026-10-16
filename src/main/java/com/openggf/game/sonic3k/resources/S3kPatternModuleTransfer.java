package com.openggf.game.sonic3k.resources;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.level.resources.KosModuleTransferAccess;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.level.resources.NativeKosModuleTransfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Process_Kos_Module_Queue's actual direct-child receipts and pending native
 * Add_To_DMA_Queue payloads. VInt8/C/10 and Do_ControllerPal publish them;
 * rendering, repeated host calls and archive claims cannot release them.
 * Origin: S3K ending initial Master visual parity, 2026-10-14.
 */
final class S3kPatternModuleTransfer implements NativeKosModuleTransfer,
        RewindSnapshottable<S3kPatternModuleTransfer.State>, S3kKosModuleQueue.ModuleTransferObserver {
    static final String REWIND_KEY = "s3k-pattern-module-transfer";
    record ChildReceipt(int source, int compressedBytes) { }
    record Binding(HardwareWorkHandle handle, S3kKosModuleDescriptor descriptor, int queuePosition,
                   List<ChildReceipt> submitted, int completed) {
        Binding { submitted = List.copyOf(submitted); }
    }
    record State(List<Binding> bindings, List<KosinskiModuleQueue.DmaWriteState> pendingDma,
                 List<HardwareWorkHandle> automaticClaims) {
        State { bindings = List.copyOf(bindings); pendingDma = List.copyOf(pendingDma); automaticClaims = List.copyOf(automaticClaims); }
    }
    private final List<Binding> bindings = new ArrayList<>();
    private final List<HardwareWorkHandle> automaticClaims = new ArrayList<>();
    private final List<KosinskiModuleQueue.DmaWriteState> pendingDma = new ArrayList<>();
    private KosinskiModuleQueue physical; // Injected session resource, never captured gameplay.

    void attach(KosinskiModuleQueue queue) {
        Objects.requireNonNull(queue, "queue");
        if (physical != null && physical != queue)
            throw new IllegalStateException("Native module transfer cannot mix physical queue owners");
        physical = queue;
        KosModuleTransferAccess.bind(queue, this);
    }
    void bind(KosinskiModuleQueue queue, HardwareWorkHandle handle, S3kKosModuleDescriptor descriptor) {
        attach(queue);
        if (bindings.stream().anyMatch(b -> b.handle().equals(handle)))
            throw new IllegalStateException("KosM job was already paired: " + handle);
        int admittedPosition = -1;
        var entries = queue.queuedArchives();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            int position = i;
            if (entry.archiveAddress() == descriptor.sourceAddress()
                    && entry.destinationVramBytes() == descriptor.destinationAddress()
                    && bindings.stream().noneMatch(b -> b.queuePosition() == position)) {
                admittedPosition = i; break;
            }
        }
        if (admittedPosition < 0)
            throw new IllegalStateException("KosM job has no admitted physical FIFO slot: " + handle);
        bindings.add(new Binding(handle, descriptor, admittedPosition, List.of(), 0));
    }

    void claimAutomatically(HardwareWorkHandle handle) { automaticClaims.add(handle); }
    void claimReady(S3kKosModuleQueue queue) {
        for (var iterator = automaticClaims.iterator(); iterator.hasNext();) {
            var handle = iterator.next();
            if (queue.isReady(handle)) { queue.claim(handle); iterator.remove(); }
        }
    }
    @Override public void childSubmitted(HardwareWorkHandle handle, int index, int source, int compressedBytes) {
        int slot = handleIndex(handle);
        if (slot < 0) return; // A timing-only terrain/title job owns no journal entry.
        Binding old = bindings.get(slot);
        if (old.submitted().size() != index || old.completed() != index)
            throw new IllegalStateException("Out-of-order native module start receipt: " + handle);
        var started = new ArrayList<>(old.submitted());
        started.add(new ChildReceipt(source, compressedBytes));
        bindings.set(slot, new Binding(handle, old.descriptor(), old.queuePosition(), started, old.completed()));
    }
    @Override public void childClaimed(HardwareWorkHandle handle, int index) {
        int slot = handleIndex(handle);
        if (slot < 0) return;
        Binding old = bindings.get(slot);
        if (old.completed() != index || old.submitted().size() != index + 1)
            throw new IllegalStateException("Out-of-order native module claim receipt: " + handle);
        bindings.set(slot, new Binding(handle, old.descriptor(), old.queuePosition(), old.submitted(), index + 1));
    }
    private int handleIndex(HardwareWorkHandle handle) {
        for (int i = 0; i < bindings.size(); i++)
            if (bindings.get(i).handle().equals(handle)) return i;
        return -1;
    }
    private Binding binding(KosinskiModuleQueue.ArchiveState archive) {
        int index = archive.totalModules() - archive.modulesRemaining();
        for (Binding b : bindings) {
            if (b.queuePosition() != 0) continue;
            if (b.descriptor().sourceAddress() != archive.archiveAddress()
                    || b.descriptor().destinationAddress() + index * 0x1000 != archive.destinationVramBytes()
                    || b.descriptor().moduleCount() != archive.totalModules()
                    || b.descriptor().destinationLength() != archive.uncompressedBytes())
                throw new IllegalStateException("Physical/timed KosM FIFO head/header mismatch");
            return b;
        }
        return null;
    }
    @Override public void archiveRetired() {
        // This is the native six-byte FIFO shift, not a module advance or a
        // Java-object identity. The separate captured position stays aligned
        // with physical Snapshot.archives even when those values are copied.
        for (int i = 0; i < bindings.size(); i++) {
            Binding old = bindings.get(i);
            if (old.queuePosition() == 0)
                throw new IllegalStateException("Retired native archive still has an unconsumed binding");
            bindings.set(i, new Binding(old.handle(), old.descriptor(), old.queuePosition() - 1,
                    old.submitted(), old.completed()));
        }
    }
    @Override public boolean owns(KosinskiModuleQueue.ArchiveState archive) { return binding(archive) != null; }
    @Override public boolean mayStart(KosinskiModuleQueue.ArchiveState archive) {
        Binding b = binding(archive);
        if (b == null) return true;
        int index = archive.totalModules() - archive.modulesRemaining();
        if (b.submitted().size() <= index) return false;
        if (b.submitted().get(index).source() != archive.sourceAddress())
            throw new IllegalStateException("Physical/timed KosM module source mismatch");
        return true;
    }
    @Override public boolean mayComplete(KosinskiModuleQueue.ArchiveState archive) {
        Binding b = binding(archive);
        if (b == null) return true;
        int index = archive.totalModules() - archive.modulesRemaining();
        if (b.completed() <= index) return false;
        ChildReceipt receipt = b.submitted().get(index);
        if (receipt.source() != archive.sourceAddress()
                || receipt.compressedBytes() != archive.decompressionEndAddress() - archive.sourceAddress())
            throw new IllegalStateException("Physical/timed KosM decompressor end mismatch");
        return true;
    }
    @Override public void queueDma(KosinskiModuleQueue.ArchiveState archive, KosinskiModuleQueue.DmaChunk chunk) {
        Binding b = Objects.requireNonNull(binding(archive), "paired native archive");
        if (!mayComplete(archive)) throw new IllegalStateException("Native KosM DMA before child claim");
        pendingDma.add(new KosinskiModuleQueue.DmaWriteState(chunk.destinationVramBytes(), chunk.data()));
        if (archive.modulesRemaining() == 1) bindings.remove(b);
    }
    /** Pending Process_Kos_Module_Queue Add_To_DMA_Queue writes, oldest first. */
    List<KosinskiModuleQueue.DmaWriteState> pendingDmaWrites() {
        return List.copyOf(pendingDma);
    }

    void publish() {
        if (physical == null && !pendingDma.isEmpty())
            throw new IllegalStateException("Pending native DMA has no session pattern owner");
        while (!pendingDma.isEmpty()) {
            var next = pendingDma.getFirst();
            if (!physical.applyImmediateDma(next.destinationVramBytes(), next.data())) return;
            pendingDma.removeFirst();
        }
    }
    @Override public String key() { return REWIND_KEY; }
    @Override public State capture() { return new State(bindings, pendingDma, automaticClaims); }
    @Override public void restore(State state) {
        bindings.clear(); bindings.addAll(state.bindings());
        pendingDma.clear(); pendingDma.addAll(state.pendingDma());
        automaticClaims.clear(); automaticClaims.addAll(state.automaticClaims());
    }
    @Override public void clear() { bindings.clear(); pendingDma.clear(); automaticClaims.clear(); }
    @Override public void resetForMissingSnapshot() { clear(); }
}
