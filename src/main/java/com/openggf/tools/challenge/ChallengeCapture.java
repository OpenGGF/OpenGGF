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
        for (short sample : focused) {
            wav.writeByte(sample & 255);
            wav.writeByte((sample >>> 8) & 255);
            pcmBytes += 2;
        }
    }
    void menuPacket(short[] samples) throws IOException {
        for (short sample : samples) {
            menuWav.writeByte(sample & 255);
            menuWav.writeByte((sample >>> 8) & 255);
            menuBytes += 2;
        }
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
        state.close();
        writeHeader(menuWav, menuBytes);
        menuWav.close();
        wav.seek(0);
        wav.writeBytes("RIFF");
        le((int) pcmBytes + 36);
        wav.writeBytes("WAVEfmt ");
        le(16);
        word(1);
        word(2);
        le(ChallengeProtocol.RATE);
        le(ChallengeProtocol.RATE * 4);
        word(4);
        word(16);
        wav.writeBytes("data");
        le((int) pcmBytes);
        wav.close();
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
    private void le(int value) throws IOException {
        for (int i = 0; i < 4; i++) wav.writeByte(value >>> (8 * i));
    }
    private void word(int value) throws IOException {
        wav.writeByte(value);
        wav.writeByte(value >>> 8);
    }
}
