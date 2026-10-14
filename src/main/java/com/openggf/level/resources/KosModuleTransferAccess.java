package com.openggf.level.resources;

/** Engine-internal binding outside the annotated creator-facing queue surface. */
public final class KosModuleTransferAccess {
    private KosModuleTransferAccess() { }
    public static void bind(KosinskiModuleQueue queue, NativeKosModuleTransfer transfer) {
        queue.bindNativeTransfer(transfer);
    }
}
