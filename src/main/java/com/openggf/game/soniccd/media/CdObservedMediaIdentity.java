package com.openggf.game.soniccd.media;

import com.openggf.data.cd.CueDiscImage;
import com.openggf.data.cd.DiscTrack;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Frozen observations of all supplied tracks, not an authenticity certificate.
 * scd-cue-layout-content-v1 hashes track number, stored mode, disc INDEX01, program,
 * stored-gap and synthetic-gap lengths, then the SHA-256 of stored gap+program frames.
 * Fields use big-endian DataOutput widths, the mode uses writeUTF, and the digest is 32 raw bytes.
 * File names, file grouping, file-relative indexes, CATALOG and CUE text are excluded.
 * Raw/cooked MODE1 and stored/synthetic gaps remain distinct. No physical-TOC equivalence is claimed.
 * An optional original container is caller-supplied provenance, not proof that it produced these files.
 */
public record CdObservedMediaIdentity(String definition, String normalizedSha256, FileHash cue,
                                       List<FileHash> binaryFiles, Optional<FileHash> originalContainer,
                                       List<TrackContent> tracks) {
    public static final String DEFINITION = "scd-cue-layout-content-v1";
    private static final int HASH_FRAMES = 1024;

    public record FileHash(String path, long bytes, String sha256) { }
    public record TrackContent(int number, DiscTrack.Mode mode, long discIndex01Frame,
                               long programFrames, long storedPregapFrames, long syntheticPregapFrames,
                               String storedFramesSha256) { }

    public CdObservedMediaIdentity {
        binaryFiles = List.copyOf(binaryFiles);
        tracks = List.copyOf(tracks);
    }

    public static CdObservedMediaIdentity capture(CueDiscImage disc, Path cue,
                                                  Optional<Path> originalContainer) throws IOException {
        disc.requireCue(cue);
        return capture(disc, originalContainer);
    }

    /** Observes only the CUE bytes and BINARY handles already bound to this disc owner. */
    public static CdObservedMediaIdentity capture(CueDiscImage disc,
                                                  Optional<Path> originalContainer) throws IOException {
        checkAll(disc);
        byte[] cueBytes = disc.readCueBytes();
        FileHash cueHash = new FileHash(disc.cuePath().toString(), cueBytes.length, hex(sha256().digest(cueBytes)));
        List<FileHash> sources = new ArrayList<>();
        for (CueDiscImage.SourceFile source : disc.sourceFiles()) {
            MessageDigest digest = sha256();
            long offset = 0;
            while (offset < source.bytes()) {
                int count = (int) Math.min(128 * 1024, source.bytes() - offset);
                digest.update(source.read(offset, count));
                offset += count;
            }
            sources.add(new FileHash(source.path().toString(), offset, hex(digest.digest())));
        }
        Optional<FileHash> container = originalContainer.isPresent()
                ? Optional.of(hashFile(originalContainer.get())) : Optional.empty();
        List<TrackContent> tracks = new ArrayList<>();
        MessageDigest normalized = sha256();
        try (DataOutputStream layout = new DataOutputStream(
                new DigestOutputStream(OutputStream.nullOutputStream(), normalized))) {
            layout.writeUTF(DEFINITION);
            layout.writeInt(disc.tracks().size());
            for (DiscTrack track : disc.tracks()) {
                MessageDigest content = sha256();
                long first = -track.filePregapFrames();
                while (first < track.programFrames()) {
                    int frames = (int) Math.min(HASH_FRAMES, track.programFrames() - first);
                    content.update(disc.readRawFrames(track.number(), first, frames));
                    first += frames;
                }
                byte[] digest = content.digest();
                tracks.add(new TrackContent(track.number(), track.mode(), track.discIndex01Frame(),
                        track.programFrames(), track.filePregapFrames(), track.syntheticPregapFrames(), hex(digest)));
                layout.writeInt(track.number());
                layout.writeUTF(track.mode().name());
                layout.writeLong(track.discIndex01Frame());
                layout.writeLong(track.programFrames());
                layout.writeLong(track.filePregapFrames());
                layout.writeLong(track.syntheticPregapFrames());
                layout.write(digest);
            }
        }
        checkAll(disc);
        return new CdObservedMediaIdentity(DEFINITION, hex(normalized.digest()), cueHash,
                sources, container, tracks);
    }

    private static void checkAll(CueDiscImage disc) throws IOException {
        for (DiscTrack track : disc.tracks()) {
            disc.readRawFrames(track.number(), 0, 0);
        }
    }

    private static FileHash hashFile(Path path) throws IOException {
        Path real = path.toRealPath();
        BasicFileAttributes before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile()) {
            throw new IOException("Identity input is not a regular file");
        }
        MessageDigest digest = sha256();
        long size = 0;
        byte[] buffer = new byte[128 * 1024];
        try (var input = Files.newInputStream(real)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                size = Math.addExact(size, count);
            }
        }
        BasicFileAttributes after = Files.readAttributes(real, BasicFileAttributes.class);
        if (size != before.size() || size != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Identity input changed while hashing");
        }
        return new FileHash(real.toString(), size, hex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM has no SHA-256", exception);
        }
    }

    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
}
