package com.openggf.tools.fbzvisual;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Atomically publishes accepted captures or a receipt-only rejection. */
public final class FbzVisualCapturePublisher {

    private final ObjectMapper mapper;

    public FbzVisualCapturePublisher(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public void publishAccepted(FbzVisualCapturePaths paths,
                                byte[] fullPng,
                                byte[] nativeCropPng,
                                FbzVisualCaptureReceipt receipt) throws IOException {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(fullPng, "fullPng");
        Objects.requireNonNull(nativeCropPng, "nativeCropPng");
        Objects.requireNonNull(receipt, "receipt");
        if (!"accepted".equals(receipt.status())) {
            throw new IllegalArgumentException("publishAccepted requires an accepted FBZ receipt");
        }
        if (paths.fullPng().equals(paths.nativeCropPng()) && !Arrays.equals(fullPng, nativeCropPng)) {
            throw new IllegalArgumentException("Native full-frame and crop bytes differ for one output path");
        }

        Set<Path> temporary = new LinkedHashSet<>();
        try {
            Path fullTemp = writeTemporary(paths.fullPng(), fullPng);
            temporary.add(fullTemp);
            Path cropTemp = fullTemp;
            if (!paths.fullPng().equals(paths.nativeCropPng())) {
                cropTemp = writeTemporary(paths.nativeCropPng(), nativeCropPng);
                temporary.add(cropTemp);
            }
            Path receiptTemp = writeTemporary(paths.receipt(), mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(receipt));
            temporary.add(receiptTemp);

            moveAtomically(fullTemp, paths.fullPng());
            temporary.remove(fullTemp);
            if (!paths.fullPng().equals(paths.nativeCropPng())) {
                moveAtomically(cropTemp, paths.nativeCropPng());
                temporary.remove(cropTemp);
            }
            moveAtomically(receiptTemp, paths.receipt());
            temporary.remove(receiptTemp);
        } catch (IOException failure) {
            deleteImages(paths);
            try {
                writeReceipt(paths.receipt(), receipt.rejectedAfterPublicationFailure(
                        "publication failure: " + failure.getMessage()));
            } catch (IOException receiptFailure) {
                failure.addSuppressed(receiptFailure);
            }
            throw failure;
        } finally {
            for (Path path : temporary) {
                Files.deleteIfExists(path);
            }
        }
    }

    public void publishRejected(FbzVisualCapturePaths paths,
                                FbzVisualCaptureReceipt receipt) throws IOException {
        Objects.requireNonNull(paths, "paths");
        Objects.requireNonNull(receipt, "receipt");
        if (!"rejected".equals(receipt.status())) {
            throw new IllegalArgumentException("publishRejected requires a rejected FBZ receipt");
        }
        deleteImages(paths);
        writeReceipt(paths.receipt(), receipt);
    }

    /** Publishes a complete cadence set or rolls back every frame and receipt. */
    public void publishCadenceSeries(List<FbzVisualCadenceCapture.FramePublication> frames)
            throws IOException {
        Objects.requireNonNull(frames, "frames");
        if (frames.size() < 5) {
            throw new IllegalArgumentException("FBZ cadence publication requires at least five frames");
        }
        Set<Path> destinations = new LinkedHashSet<>();
        Map<Path, Path> temporaryByDestination = new java.util.LinkedHashMap<>();
        for (FbzVisualCadenceCapture.FramePublication frame : frames) {
            if (!destinations.add(frame.png()) || !destinations.add(frame.receipt())) {
                throw new IllegalArgumentException("Duplicate FBZ cadence publication path");
            }
            if (Files.exists(frame.png()) || Files.exists(frame.receipt())) {
                throw new IOException("Refusing to overwrite FBZ cadence evidence: " + frame.png());
            }
        }
        try {
            for (FbzVisualCadenceCapture.FramePublication frame : frames) {
                temporaryByDestination.put(frame.png(), writeTemporary(frame.png(), frame.pngBytes()));
                temporaryByDestination.put(frame.receipt(), writeTemporary(frame.receipt(),
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(frame.provenance())));
            }
            for (Map.Entry<Path, Path> entry : temporaryByDestination.entrySet()) {
                moveAtomically(entry.getValue(), entry.getKey());
            }
        } catch (IOException | RuntimeException failure) {
            for (Path destination : destinations) Files.deleteIfExists(destination);
            throw failure;
        } finally {
            for (Path temporary : temporaryByDestination.values()) Files.deleteIfExists(temporary);
        }
    }

    private void writeReceipt(Path destination, FbzVisualCaptureReceipt receipt) throws IOException {
        Path temporary = writeTemporary(destination,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(receipt));
        try {
            moveAtomically(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path writeTemporary(Path destination, byte[] bytes) throws IOException {
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IOException("FBZ capture path has no parent: " + destination);
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve(destination.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.write(temporary, bytes);
        return temporary;
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteImages(FbzVisualCapturePaths paths) throws IOException {
        Files.deleteIfExists(paths.fullPng());
        if (!paths.nativeCropPng().equals(paths.fullPng())) {
            Files.deleteIfExists(paths.nativeCropPng());
        }
    }
}
