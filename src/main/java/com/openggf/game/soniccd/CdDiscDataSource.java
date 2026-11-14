package com.openggf.game.soniccd;

import com.openggf.data.Rom;
import com.openggf.game.GameDataSource;
import com.openggf.game.soniccd.media.CdProgramAssets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Session source for one user-supplied Sonic CD disc. It has no cartridge ROM:
 * every runtime byte is read from the disc's ISO file system through
 * {@link CdProgramAssets}. Assets are addressed by their ISO names
 * ({@code R11A__.MMD;1}), which are the {@link CdNativeFile} names.
 */
public final class CdDiscDataSource implements GameDataSource, java.io.Closeable {
    private final CdProgramAssets assets;
    private final String identity;

    private CdDiscDataSource(CdProgramAssets assets) {
        this.assets = assets;
        this.identity = "disc:scd:sha256:" + assets.identity().normalizedSha256();
    }

    /** Opens the CUE sheet of a user-supplied disc. */
    public static CdDiscDataSource open(Path cue) throws IOException {
        return new CdDiscDataSource(CdProgramAssets.open(Objects.requireNonNull(cue, "cue")));
    }

    public CdProgramAssets assets() {
        return assets;
    }

    /** Reads one complete program file from the disc. */
    public byte[] readFile(CdNativeFile file) throws IOException {
        long size = assets.entry(file).size();
        return assets.read(file, 0, Math.toIntExact(size));
    }

    @Override
    public Optional<Rom> rom() {
        return Optional.empty();
    }

    @Override
    public InputStream openAsset(String normalizedPath) throws IOException {
        for (CdNativeFile file : CdNativeFile.values()) {
            if (file.isoName().equals(normalizedPath)) {
                return new ByteArrayInputStream(readFile(file));
            }
        }
        throw new IOException("Not a Sonic CD disc file: " + normalizedPath);
    }

    @Override
    public String identity() {
        return identity;
    }

    @Override
    public void close() throws IOException {
        assets.close();
    }
}
