package com.openggf.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Capabilities shared by immutable packed and explicit-development directory snapshots. */
@com.openggf.game.ModApi
public non-sealed interface SnapshotModAssetRoot extends ModAssetRoot {
    List<String> validatedEntryNames() throws IOException;
    String immutableSha256() throws IOException;
    Path immutableContentPath() throws IOException;
}
