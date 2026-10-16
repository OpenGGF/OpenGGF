package com.openggf.level.resources;

/** Internal native queue capability. A paired ROM job owns its real start/claim
 * receipts and queues DMA for the later VInt; an unpaired queue keeps its
 * standalone scheduling contract. No comparison data selects these edges.
 */
public interface NativeKosModuleTransfer {
    boolean mayStart(KosinskiModuleQueue.ArchiveState archive);
    boolean mayComplete(KosinskiModuleQueue.ArchiveState archive);
    boolean owns(KosinskiModuleQueue.ArchiveState archive);
    void queueDma(KosinskiModuleQueue.ArchiveState archive, KosinskiModuleQueue.DmaChunk chunk);
    void archiveRetired();
    void clear();
}
