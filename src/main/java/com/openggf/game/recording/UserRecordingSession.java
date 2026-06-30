package com.openggf.game.recording;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.session.EngineServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class UserRecordingSession {
    private static final int ACTION_A_MASK = 0x01;

    private final RecordingLaunchContext launchContext;
    private final Path outputBk2Path;
    private final SonicConfigurationService configService;
    private final RecordingFileWriter writer;
    private final SidecarSnapshotSource snapshotSource;
    private final Supplier<Instant> clock;
    private final List<RecordedFrameInput> bufferedInputs = new ArrayList<>();
    private final List<DesyncLiteFrame> bufferedSidecarFrames = new ArrayList<>();

    private int currentMovieFrame;
    private RecordedFrameInput bufferedFrameInput;
    private DesyncLiteFrame bufferedDesyncFrame;
    private UserRecordingStopReason stopReason;
    private boolean active = true;
    private String ioErrorMessage = "";

    public UserRecordingSession(RecordingLaunchContext launchContext, Path outputBk2Path) {
        this(
                launchContext,
                outputBk2Path,
                EngineServices.current().configuration(),
                UserRecordingWriter::write,
                DesyncLiteSnapshotter::capture,
                Instant::now);
    }

    UserRecordingSession(RecordingLaunchContext launchContext, Path outputBk2Path,
            SonicConfigurationService configService, RecordingFileWriter writer,
            SidecarSnapshotSource snapshotSource, Supplier<Instant> clock) {
        this.launchContext = Objects.requireNonNull(launchContext, "launchContext");
        this.outputBk2Path = Objects.requireNonNull(outputBk2Path, "outputBk2Path");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.snapshotSource = Objects.requireNonNull(snapshotSource, "snapshotSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void beforeLevelFrame(InputHandler input) {
        if (!active) {
            return;
        }
        Objects.requireNonNull(input, "input");
        bufferedFrameInput = new RecordedFrameInput(
                currentMovieFrame,
                inputMask(input, SonicConfiguration.UP, SonicConfiguration.DOWN,
                        SonicConfiguration.LEFT, SonicConfiguration.RIGHT, SonicConfiguration.JUMP),
                actionMask(input, SonicConfiguration.JUMP),
                input.isKeyDown(configService.getInt(SonicConfiguration.START)),
                inputMask(input, SonicConfiguration.P2_UP, SonicConfiguration.P2_DOWN,
                        SonicConfiguration.P2_LEFT, SonicConfiguration.P2_RIGHT, SonicConfiguration.P2_JUMP),
                actionMask(input, SonicConfiguration.P2_JUMP),
                input.isKeyDown(configService.getInt(SonicConfiguration.P2_START)));
    }

    public void afterLevelFrame() {
        if (!active || bufferedFrameInput == null) {
            return;
        }
        bufferedDesyncFrame = snapshotSource.capture(currentMovieFrame);
        bufferedInputs.add(bufferedFrameInput);
        bufferedSidecarFrames.add(bufferedDesyncFrame);
        bufferedFrameInput = null;
        bufferedDesyncFrame = null;
        currentMovieFrame++;
    }

    public void requestStop(UserRecordingStopReason reason) {
        if (!active) {
            return;
        }
        UserRecordingStopReason requestedReason = reason == null ? UserRecordingStopReason.UNKNOWN : reason;
        stopReason = requestedReason;

        if (bufferedInputs.isEmpty() && requestedReason == UserRecordingStopReason.ABORTED_BEFORE_GAMEPLAY) {
            active = false;
            deleteAbortedOutput();
            return;
        }

        try {
            writer.write(outputBk2Path, manifest(requestedReason), List.copyOf(bufferedInputs),
                    List.copyOf(bufferedSidecarFrames));
            active = false;
        } catch (IOException | RuntimeException ex) {
            stopReason = UserRecordingStopReason.IO_ERROR;
            ioErrorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            active = false;
        }
    }

    public boolean isActive() {
        return active;
    }

    public UserRecordingHudState hudState() {
        if (active) {
            return new UserRecordingHudState(
                    true,
                    "REC frame " + currentMovieFrame,
                    outputBk2Path.getFileName() == null ? outputBk2Path.toString() : outputBk2Path.getFileName().toString(),
                    currentMovieFrame,
                    false,
                    false);
        }
        if (stopReason == UserRecordingStopReason.IO_ERROR) {
            String suffix = ioErrorMessage.isBlank() ? "" : ": " + ioErrorMessage;
            return new UserRecordingHudState(
                    true,
                    "REC ERROR",
                    UserRecordingStopReason.IO_ERROR.name() + suffix,
                    currentMovieFrame,
                    false,
                    true);
        }
        UserRecordingStopReason displayedReason = stopReason == null ? UserRecordingStopReason.UNKNOWN : stopReason;
        return new UserRecordingHudState(
                true,
                "REC STOPPED",
                displayedReason.name(),
                currentMovieFrame,
                false,
                false);
    }

    RecordingLaunchContext launchContext() {
        return launchContext;
    }

    Path outputBk2Path() {
        return outputBk2Path;
    }

    int currentMovieFrame() {
        return currentMovieFrame;
    }

    RecordedFrameInput bufferedFrameInput() {
        return bufferedFrameInput;
    }

    DesyncLiteFrame bufferedDesyncFrame() {
        return bufferedDesyncFrame;
    }

    UserRecordingStopReason stopReason() {
        return stopReason;
    }

    private int inputMask(InputHandler input, SonicConfiguration up, SonicConfiguration down,
            SonicConfiguration left, SonicConfiguration right, SonicConfiguration jump) {
        int mask = 0;
        if (input.isKeyDown(configService.getInt(up))) {
            mask |= AbstractPlayableSprite.INPUT_UP;
        }
        if (input.isKeyDown(configService.getInt(down))) {
            mask |= AbstractPlayableSprite.INPUT_DOWN;
        }
        if (input.isKeyDown(configService.getInt(left))) {
            mask |= AbstractPlayableSprite.INPUT_LEFT;
        }
        if (input.isKeyDown(configService.getInt(right))) {
            mask |= AbstractPlayableSprite.INPUT_RIGHT;
        }
        if (input.isKeyDown(configService.getInt(jump))) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }
        return mask;
    }

    private int actionMask(InputHandler input, SonicConfiguration jump) {
        return input.isKeyDown(configService.getInt(jump)) ? ACTION_A_MASK : 0;
    }

    private UserRecordingManifest manifest(UserRecordingStopReason reason) {
        String fileName = outputBk2Path.getFileName() == null ? outputBk2Path.toString() : outputBk2Path.getFileName().toString();
        return new UserRecordingManifest(
                UserRecordingManifest.CURRENT_SCHEMA_VERSION,
                fileName,
                AppVersion.identity(),
                launchContext,
                UserRecordingSidecarMetadata.everyFrame(),
                new RecordingDeterminismMetadata(null, null),
                "A",
                bufferedInputs.size(),
                reason,
                clock.get());
    }

    private void deleteAbortedOutput() {
        try {
            Files.deleteIfExists(outputBk2Path);
        } catch (IOException ex) {
            stopReason = UserRecordingStopReason.IO_ERROR;
            ioErrorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }

    @FunctionalInterface
    interface RecordingFileWriter {
        void write(Path path, UserRecordingManifest manifest, List<RecordedFrameInput> inputs,
                List<DesyncLiteFrame> sidecarFrames) throws IOException;
    }

    @FunctionalInterface
    interface SidecarSnapshotSource {
        DesyncLiteFrame capture(int movieFrame);
    }
}
