package com.openggf.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Capabilities available only for validated immutable packed-mod snapshots. */
@com.openggf.game.ModApi
public sealed interface PackedModAssetRoot extends ModAssetRoot permits JarModAssetRoot {
    List<String> validatedEntryNames() throws IOException;
    String immutableSha256() throws IOException;
    /** Engine-owned immutable snapshot path for bytecode consumers. */
    Path immutableContentPath() throws IOException;
}
