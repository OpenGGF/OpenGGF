package com.openggf.level.resources;

/** Engine-internal binding outside the annotated creator-facing queue surface. */
public final class KosModuleTransferAccess {
    private KosModuleTransferAccess() { }
    /**
     * A complete physical pattern, unlike unbound logical DMA bookkeeping.
     * The queue validates and confirms the write before recording it.
     */
    public static boolean publishPattern(KosinskiModuleQueue queue, int destination, byte[] pattern) {
        return queue.publishConfirmedPattern(destination, pattern);
    }

    public static void bind(KosinskiModuleQueue queue, NativeKosModuleTransfer transfer) {
        queue.bindNativeTransfer(transfer);
    }
}
