package com.openggf.game.soniccd.media;

import com.openggf.data.cd.CueDiscImage;
import com.openggf.data.cd.DiscTrack;
import com.openggf.data.cd.Iso9660FileSystem;
import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdProgramCatalogue;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Owns one user-supplied disc and bounded program-local assets. No ROM or creator authority.
 * Opening hashes the complete supplied layout, including audio; it does not certify a release.
 * The source must remain unchanged while open. Disc checks detect ordinary changes, not hostile timestamp spoofing.
 */
public final class CdProgramAssets implements AutoCloseable {
    public enum Use { LOAD_COMPONENT, CONDITIONAL_PCM, PROGRAM_STREAM }
    public record Component(CdNativeFile file, Use use, Iso9660FileSystem.Entry entry) { }

    private final CueDiscImage disc;
    private final Iso9660FileSystem files;
    private final CdDiscHeader header;
    private final CdObservedMediaIdentity identity;

    private CdProgramAssets(CueDiscImage disc, Iso9660FileSystem files, CdDiscHeader header,
                            CdObservedMediaIdentity identity) {
        this.disc = disc;
        this.files = files;
        this.header = header;
        this.identity = identity;
    }

    public static CdProgramAssets open(Path cue) throws IOException {
        return open(cue, Optional.empty(), Optional.empty());
    }

    /** A requested variant is an assertion about source headers, never an execution environment. */
    public static CdProgramAssets open(Path cue, Optional<CdReleaseVariant> requestedVariant,
                                       Optional<Path> originalContainer) throws IOException {
        Objects.requireNonNull(requestedVariant);
        Objects.requireNonNull(originalContainer);
        CueDiscImage disc = CueDiscImage.open(cue);
        try {
            List<DiscTrack> data = disc.tracks().stream().filter(track -> track.mode().isMode1()).toList();
            if (data.size() != 1) {
                throw new IOException("Sonic CD asset inspection requires exactly one MODE1 track");
            }
            int track = data.getFirst().number();
            CdDiscHeader header = CdDiscHeader.parse(disc.readMode1Bytes(track, 0, 0x200));
            if (requestedVariant.isPresent() && header.match(requestedVariant.get()) != CdDiscHeader.Match.MATCH) {
                throw new IOException("Requested source variant " + requestedVariant.get() + " has "
                        + header.match(requestedVariant.get()) + " header agreement; observed "
                        + header.observedVariant().map(Enum::name).orElse("UNKNOWN")
                        + ". Media remains uncertified.");
            }
            Iso9660FileSystem files = Iso9660FileSystem.open(disc, track);
            CdObservedMediaIdentity identity = CdObservedMediaIdentity.capture(disc, originalContainer);
            return new CdProgramAssets(disc, files, header, identity);
        } catch (IOException | RuntimeException exception) {
            try {
                disc.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    /** Geometry observation only, independent of source-header agreement or certification. */
    public boolean hasExpectedTrackShape() {
        return disc.tracks().size() == 35 && disc.tracks().getFirst().number() == 1
                && disc.tracks().getFirst().mode().isMode1()
                && disc.tracks().subList(1, 35).stream().allMatch(track -> track.mode() == DiscTrack.Mode.AUDIO);
    }

    public CdDiscHeader header() { return header; }
    public CdObservedMediaIdentity identity() { return identity; }
    public Optional<CdProgramCatalogue> catalogue() {
        return header.observedVariant().map(CdProgramCatalogue::forRelease);
    }
    public List<Iso9660FileSystem.Entry> files() throws IOException { return files.files(); }
    public Iso9660FileSystem.Entry entry(CdNativeFile file) throws IOException {
        return files.entry(Objects.requireNonNull(file).isoName());
    }
    public byte[] read(CdNativeFile file, long offset, int length) throws IOException {
        return files.readFile(Objects.requireNonNull(file).isoName(), offset, length);
    }
    public InputStream openFile(CdNativeFile file) throws IOException {
        return files.openFile(Objects.requireNonNull(file).isoName());
    }

    /** A detached immutable file image. Its bytes remain readable after this owner closes. */
    public CdMmdImage mmd(CdNativeFile file) throws IOException {
        Objects.requireNonNull(file);
        if (file.kind() != CdNativeFile.Kind.STAGE_PROGRAM && file.kind() != CdNativeFile.Kind.MAIN_CPU_PROGRAM
                && file.kind() != CdNativeFile.Kind.UNUSED_COPY) {
            throw new IllegalArgumentException("Native file is not an MMD image: " + file);
        }
        if (entry(file).size() > CdMmdHeader.WORD_RAM_BYTES) {
            throw new IOException("MMD exceeds original Word RAM image");
        }
        return CdMmdImage.parse(files.readFile(file.isoName()));
    }

    /** A bounded original-file view. Reads and streams remain owned by this disc. */
    public FileSlice slice(CdNativeFile file, long offset, long length) throws IOException {
        CdMmdImage.checkRange(offset, length, entry(file).size());
        return new FileSlice(file, offset, length);
    }

    public final class FileSlice {
        private final CdNativeFile file;
        private final long offset;
        private final long length;

        private FileSlice(CdNativeFile file, long offset, long length) {
            this.file = file;
            this.offset = offset;
            this.length = length;
        }

        public CdNativeFile file() { return file; }
        public long fileOffset() { return offset; }
        public long length() { return length; }
        public byte[] read(long relativeOffset, int count) throws IOException {
            CdMmdImage.checkRange(relativeOffset, count, length);
            return CdProgramAssets.this.read(file, offset + relativeOffset, count);
        }
        public InputStream openStream() throws IOException {
            InputStream input = openFile(file);
            try {
                input.skipNBytes(offset);
            } catch (IOException | RuntimeException exception) {
                input.close();
                throw exception;
            }
            return new InputStream() {
                private long remaining = length;
                private boolean closed;
                private void check() throws IOException {
                    if (closed) {
                        throw new IOException("Asset slice stream is closed");
                    }
                    entry(file);
                }
                @Override public int read() throws IOException {
                    byte[] one = new byte[1];
                    return read(one, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(one[0]);
                }
                @Override public int read(byte[] buffer, int start, int count) throws IOException {
                    Objects.checkFromIndexSize(start, count, buffer.length);
                    check();
                    if (count == 0) { return 0; }
                    if (remaining == 0) { return -1; }
                    int read = input.read(buffer, start, (int) Math.min(remaining, count));
                    if (read < 0) { throw new IOException("Truncated asset slice"); }
                    remaining -= read;
                    return read;
                }
                @Override public long skip(long count) throws IOException {
                    check();
                    long skipped = input.skip(Math.min(remaining, Math.max(0, count)));
                    remaining -= skipped;
                    return skipped;
                }
                @Override public int available() throws IOException {
                    check();
                    return (int) Math.min(remaining, input.available());
                }
                @Override public void close() throws IOException {
                    if (!closed) { closed = true; input.close(); }
                }
            };
        }
    }

    public List<Component> stage(CdStageKey key) throws IOException {
        return components(requiredCatalogue().stage(key).loadCommand());
    }

    /** Potential source dependencies, without asserting a read occurs on every command. */
    public List<Component> components(CdCommand command) throws IOException {
        CdProgramCatalogue catalogue = requiredCatalogue();
        if (catalogue.disposition(command) == CdProgramCatalogue.CommandDisposition.INVALID) {
            throw new IllegalArgumentException("Invalid native command has no executable asset request");
        }
        List<Component> components = new ArrayList<>();
        for (CdNativeFile file : catalogue.nativeLoadFiles(command)) {
            Use use = file.kind() == CdNativeFile.Kind.PCM_DRIVER ? Use.CONDITIONAL_PCM : Use.LOAD_COMPONENT;
            components.add(new Component(file, use, entry(file)));
        }
        var program = catalogue.programForCommand(command);
        if (program.isPresent()) {
            for (CdNativeFile file : program.get().streamFiles()) {
                components.add(new Component(file, Use.PROGRAM_STREAM, entry(file)));
            }
        }
        return List.copyOf(components);
    }

    private CdProgramCatalogue requiredCatalogue() throws IOException {
        files.files();
        return catalogue().orElseThrow(() -> new IOException("Unknown source headers have no release-bound catalogue"));
    }

    @Override public void close() throws IOException { disc.close(); }
}
