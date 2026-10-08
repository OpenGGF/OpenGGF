package com.openggf.tools.challenge;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/** Reproducible outside-repository presentation evidence, with synchronized worker state. */
final class ChallengeCapture implements AutoCloseable {
    private final Path directory;
    private final PrintWriter state;
    private final PrintWriter presentation;
    private final long started = System.nanoTime();
    private String lastPresentation = "";
    private final RandomAccessFile wav;
    private long pcmBytes;
    private final RandomAccessFile menuWav;
    private long menuBytes;
    ChallengeCapture(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath();
        Files.createDirectories(this.directory);
        state = new PrintWriter(Files.newBufferedWriter(this.directory.resolve("state.csv")));
        state.println(
                "tick,member,generation,sequence,held,polled,mode,centre_x,centre_y,rings,v_int,level_frame");
        presentation = new PrintWriter(Files.newBufferedWriter(this.directory.resolve("presentation.csv")));
        presentation.println("elapsed_ms,scene,generation,tick,focus,pending,window_focused");
        wav = new RandomAccessFile(this.directory.resolve("focused.wav").toFile(), "rw");
        wav.setLength(0);
        wav.write(new byte[44]);
        menuWav = new RandomAccessFile(this.directory.resolve("menu.wav").toFile(), "rw");
        menuWav.setLength(0);
        menuWav.write(new byte[44]);
    }
    void frame(List<ChallengeProtocol.Frame> frames, int held, short[] focused) throws IOException {
        for (int i = 0; i < frames.size(); i++) {
            var f = frames.get(i);
            state.printf("%d,%d,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d%n", f.sequence(), i, f.generation(),
                    f.sequence(), held, f.polledMask(), f.mode(), f.x(), f.y(), f.rings(), f.vInt(),
                    f.levelFrame());
        }
        state.flush();
        if (state.checkError())
            throw new IOException("Worker state capture could not be written");
        writePcm(wav, focused);
        pcmBytes += focused.length * 2L;
    }
    void presentation(String scene, long generation, long tick, int focus, boolean pending,
            boolean windowFocused) throws IOException {
        String value = scene + "," + generation + "," + tick + "," + focus + "," + pending + "," + windowFocused;
        if (value.equals(lastPresentation))
            return;
        lastPresentation = value;
        presentation.printf("%.3f,%s%n", (System.nanoTime() - started) / 1e6, value);
        presentation.flush();
        if (presentation.checkError())
            throw new IOException("Presentation capture could not be written");
    }
    void menuPacket(short[] samples) throws IOException {
        writePcm(menuWav, samples);
        menuBytes += samples.length * 2L;
    }
    private static void writePcm(RandomAccessFile file, short[] samples) throws IOException {
        byte[] bytes = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            bytes[i * 2] = (byte) samples[i];
            bytes[i * 2 + 1] = (byte) (samples[i] >>> 8);
        }
        file.write(bytes);
    }
    void screenshot(String name, int width, int height) throws IOException {
        ScreenshotCapture.savePNG(
                ScreenshotCapture.captureFramebuffer(width, height), directory.resolve(name + ".png"));
    }
    static void saveFrame(Path path, ChallengeProtocol.Frame frame) throws IOException {
        int[] pixels = new int[ChallengeProtocol.WIDTH * ChallengeProtocol.HEIGHT];
        byte[] bytes = frame.rgba();
        for (int i = 0; i < pixels.length; i++) {
            int p = i * 4;
            pixels[i] = ((bytes[p + 3] & 255) << 24) | ((bytes[p] & 255) << 16) | ((bytes[p + 1] & 255) << 8)
                    | (bytes[p + 2] & 255);
        }
        ScreenshotCapture.savePNG(
                new RgbaImage(ChallengeProtocol.WIDTH, ChallengeProtocol.HEIGHT, pixels), path);
    }
    @Override
    public void close() throws IOException {
        Throwable failure = ChallengeCleanup.closeAll(null, state::close, presentation::close,
                () -> writeHeader(menuWav, menuBytes), menuWav::close,
                () -> writeHeader(wav, pcmBytes), wav::close);
        if (failure != null)
            throw new IOException("Capture finalization failed", failure);
    }
    private static void writeHeader(RandomAccessFile file, long size) throws IOException {
        file.seek(0);
        file.writeBytes("RIFF");
        file.writeInt(Integer.reverseBytes((int) size + 36));
        file.writeBytes("WAVEfmt ");
        file.writeInt(Integer.reverseBytes(16));
        file.writeShort(Short.reverseBytes((short) 1));
        file.writeShort(Short.reverseBytes((short) 2));
        file.writeInt(Integer.reverseBytes(ChallengeProtocol.RATE));
        file.writeInt(Integer.reverseBytes(ChallengeProtocol.RATE * 4));
        file.writeShort(Short.reverseBytes((short) 4));
        file.writeShort(Short.reverseBytes((short) 16));
        file.writeBytes("data");
        file.writeInt(Integer.reverseBytes((int) size));
    }
}
