package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code state.csv} beside a trace capture: one row per captured video frame, in
 * {@link GameplayCaptureSession#stateHeader()}'s columns (the gameplay capture's writer) plus
 * {@code angle} and the {@code trace_row} the frame shows. {@code cam_x}/{@code cam_y} are the
 * top-left of the filmed picture at any width, so {@code x - cam_x} is the player's column.
 * The {@code input} column is left empty; the trace's own rows carry the recorded input.
 */
final class TraceCaptureStateWriter implements AutoCloseable {
    static final String FILE_NAME = "state.csv";
    static final String HEADER = GameplayCaptureSession.stateHeader() + ",angle,trace_row";

    private final BufferedWriter out;

    TraceCaptureStateWriter(Path outDir) throws IOException {
        Files.createDirectories(outDir);
        out = Files.newBufferedWriter(outDir.resolve(FILE_NAME), StandardCharsets.UTF_8);
        out.write(HEADER);
        out.newLine();
    }

    /** Writes the row for video frame {@code frame}, showing trace row {@code traceRow}. */
    void row(long frame, int traceRow) throws IOException {
        row(frame, traceRow, "LEVEL");
    }

    /**
     * As {@link #row(long, int)} with the game mode the frame shows. A clip past the trace's last row can
     * show a screen with no player (the title, a warp's program screen): its row has the frame, the mode and
     * the row number, and empty player columns, whatever sprite the session still holds.
     */
    void row(long frame, int traceRow, String mode) throws IOException {
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        boolean levelScreen = "LEVEL".equals(mode);
        if (levelScreen && player == null) {
            return;
        }
        if (!levelScreen) {
            String[] columns = new String[HEADER.split(",").length];
            java.util.Arrays.fill(columns, "");
            columns[0] = Long.toString(frame);
            columns[java.util.Arrays.asList(HEADER.split(",")).indexOf("mode")] = mode;
            columns[columns.length - 1] = Integer.toString(traceRow);
            out.write(String.join(",", columns));
            out.newLine();
            return;
        }
        out.write(GameplayCaptureSession.stateLine(frame, player, mode, null)
                + "," + (player.getAngle() & 0xFF) + "," + traceRow);
        out.newLine();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
