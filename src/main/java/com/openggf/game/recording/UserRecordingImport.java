package com.openggf.game.recording;

import com.openggf.debug.playback.Bk2Archive;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Import policy for copied/downloaded user recordings, independent of research BK2 movies. */
final class UserRecordingImport {
    static final int MAX_METADATA_BYTES = 64 * 1_024;
    static final int MAX_LINE_BYTES = 1_024;
    static final int MAX_FRAMES = 216_000; // One hour at 60 Hz; not the Time Attack attempt limit.
    static final int MAX_INPUT_BYTES = 8 * 1_024 * 1_024;
    static final int MAX_SIDECAR_BYTES = 128 * 1_024 * 1_024;
    static final String MANIFEST_ENTRY = "OpenGGF/manifest.json";
    static final String SIDECAR_ENTRY = "OpenGGF/desync-lite.jsonl";

    private UserRecordingImport() { }

    static UserRecordingManifest readManifest(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            // Check all recognized entries even if a future sidecar schema will not be read.
            Bk2Archive.findUniqueEntry(zip, "Header.txt");
            Bk2Archive.findUniqueEntry(zip, "Input Log.txt");
            Bk2Archive.findUniqueEntry(zip, SIDECAR_ENTRY);
            ZipEntry entry = Bk2Archive.findUniqueEntry(zip, MANIFEST_ENTRY);
            if (entry == null) throw new IOException("BK2 missing required entry: " + MANIFEST_ENTRY);
            String json = Bk2Archive.readText(zip, entry, MAX_METADATA_BYTES);
            UserRecordingManifest manifest;
            try {
                manifest = UserRecordingCatalog.readCatalogManifest(json);
            } catch (IOException invalid) {
                // Parser messages can include JSON property names/values supplied by the archive.
                throw new IOException("Invalid recording manifest");
            }
            if (manifest.frameCount() < 0 || manifest.frameCount() > MAX_FRAMES) {
                throw new IOException("Recording manifest frame count exceeds import range");
            }
            return manifest;
        }
    }

    static Bk2Movie loadMovie(Bk2MovieLoader loader, Path path, UserRecordingManifest manifest) throws IOException {
        Bk2Movie movie = loader.load(path, new Bk2MovieLoader.ReadLimits(
                MAX_METADATA_BYTES, MAX_INPUT_BYTES, MAX_LINE_BYTES, manifest.frameCount()));
        if (movie.getFrameCount() != manifest.frameCount()) throw new IOException("Recording input frame count mismatch");
        return movie;
    }
}
