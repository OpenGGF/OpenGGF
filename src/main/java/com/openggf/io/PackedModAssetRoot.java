package com.openggf.io;

import java.io.IOException;
import java.util.List;

/** Capabilities available only for validated immutable packed-mod snapshots. */
public sealed interface PackedModAssetRoot extends ModAssetRoot permits JarModAssetRoot {
    List<String> validatedEntryNames() throws IOException;
    String immutableSha256() throws IOException;
}
