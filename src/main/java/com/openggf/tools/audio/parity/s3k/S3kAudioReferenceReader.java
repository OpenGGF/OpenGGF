package com.openggf.tools.audio.parity.s3k;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.tools.audio.parity.AudioParityChipWrite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Streaming reader for the v1 S3K oracle reference JSONL (plain or gzip).
 *
 * <p>Validates the metadata row, per-row shape, the terminal tick count and
 * the terminal body SHA-256 (over the raw tick-row bytes) while decoding each
 * RAM snapshot into the fixed sixteen-slot track vocabulary and projecting
 * the CPU-tagged write bus onto Z80-owned driver writes.
 */
public final class S3kAudioReferenceReader {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Metadata(String schema, String romSha1, String movieName, String movieSha256,
            int movieFrameCount, int ticks, String observerCoreSha256) {
    }

    private S3kAudioReferenceReader() {
    }

    public static Metadata read(Path path, Consumer<S3kAudioTick> tickConsumer) {
        try (InputStream raw = Files.newInputStream(path);
                InputStream stream = path.getFileName().toString().endsWith(".gz")
                        ? new GZIPInputStream(raw) : raw;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String metadataLine = reader.readLine();
            if (metadataLine == null) {
                throw new IllegalArgumentException("reference stream is empty");
            }
            Metadata metadata = parseMetadata(metadataLine);
            MessageDigest body = MessageDigest.getInstance("SHA-256");
            int ordinal = 0;
            String line;
            String terminal = null;
            while ((line = reader.readLine()) != null) {
                JsonNode row = JSON.readTree(line);
                String kind = row.path("row").asText();
                if (kind.equals("terminal")) {
                    terminal = line;
                    break;
                }
                if (!kind.equals("tick")) {
                    throw new IllegalArgumentException("unknown row kind: " + kind);
                }
                body.update((line + "\n").getBytes(StandardCharsets.UTF_8));
                S3kAudioTick tick = parseTick(row, ordinal);
                ordinal++;
                tickConsumer.accept(tick);
            }
            if (terminal == null) {
                throw new IllegalArgumentException("reference stream has no terminal row");
            }
            JsonNode terminalRow = JSON.readTree(terminal);
            if (terminalRow.path("ticks").asInt(-1) != ordinal
                    || metadata.ticks() != ordinal) {
                throw new IllegalArgumentException("terminal tick count mismatch: metadata "
                        + metadata.ticks() + ", terminal " + terminalRow.path("ticks").asInt(-1)
                        + ", observed " + ordinal);
            }
            String expected = terminalRow.path("body_sha256").asText();
            String observed = HexFormat.of().formatHex(body.digest());
            if (!expected.equals(observed)) {
                throw new IllegalArgumentException(
                        "terminal body digest mismatch: expected " + expected + ", observed " + observed);
            }
            if (reader.readLine() != null) {
                throw new IllegalArgumentException("trailing rows after terminal");
            }
            return metadata;
        } catch (IOException error) {
            throw new IllegalArgumentException("cannot read S3K oracle reference: " + error.getMessage(),
                    error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    /**
     * Projects the power-on frame stream onto complete Z80 driver services.
     * The boot service begins before the first Z80 write and completes when
     * {@code zInitAudioDriver} stores 5 to {@code zPalDblUpdCounter}
     * (D:523-551); all earlier frame fragments form one service. Every later
     * NTSC frame is one ordinary {@code zVInt} service.
     */
    public static Metadata readDriverServices(
            Path path, Consumer<S3kAudioTick> serviceConsumer) {
        return readDriverServices(path, S3kRequestObservationSidecar.absent(), serviceConsumer);
    }

    /**
     * As above, but consults source-observed mailbox requests for the rows the
     * stream's pre-invocation sampling cannot see. The sidecar supplies a
     * driver input only; it contributes no compared value.
     */
    public static Metadata readDriverServices(Path path,
            S3kRequestObservationSidecar requests, Consumer<S3kAudioTick> serviceConsumer) {
        List<S3kAudioTick> frames = new ArrayList<>();
        Metadata metadata = read(path, frames::add);
        int completion = -1;
        for (int index = 0; index < frames.size(); index++) {
            Integer counter = frames.get(index).global().palDoubleUpdateCounter();
            if (counter != null && counter == 5) {
                completion = index;
                break;
            }
            if (counter == null || counter != 0) {
                throw new IllegalArgumentException(
                        "invalid pre-install PAL counter at frame " + index);
            }
        }
        if (completion < 0) {
            throw new IllegalArgumentException(
                    "reference never completes zInitAudioDriver");
        }

        List<AudioParityChipWrite> bootWrites = new ArrayList<>();
        for (int index = 0; index <= completion; index++) {
            bootWrites.addAll(frames.get(index).writes());
        }
        S3kAudioTick bootCompletion = frames.get(completion);
        serviceConsumer.accept(new S3kAudioTick(0, bootCompletion.lag(),
                List.of(0, 0, 0), bootCompletion.global(),
                bootCompletion.tracks(), bootWrites));
        int ordinal = 1;
        boolean segaPcmSuspended = false;
        for (int index = completion + 1; index < frames.size(); index++) {
            S3kAudioTick frame = frames.get(index);
            boolean hasPcmTransport = frame.writes().stream()
                    .anyMatch(S3kAudioReferenceReader::isPcmTransportWrite);
            S3kAudioTick.ProducerInputEvidence inputEvidence =
                    S3kAudioTick.ProducerInputEvidence.available();
            if (segaPcmSuspended) {
                if (hasPcmTransport) {
                    // zPlaySEGAPCM executes with interrupts disabled; these
                    // frame rows contain transport only, not zVInt services
                    // (D:4372-4424).
                    continue;
                }
            }
            List<AudioParityChipWrite> serviceWrites = frame.writes().stream()
                    .filter(write -> !isPcmTransportWrite(write))
                    .toList();
            List<Integer> mailbox = frame.mailbox();
            if (segaPcmSuspended && !serviceWrites.isEmpty()) {
                segaPcmSuspended = false;
                if (mailbox.stream().allMatch(value -> value == 0)) {
                    // The stream samples the mailbox before each invocation, so a
                    // request written and consumed inside one frame is invisible
                    // to it. A source-observed byte, read at Play_Music's
                    // bus-release instruction while the Z80 was still stopped,
                    // supplies that input; without one the evidence stays
                    // unavailable exactly as before.
                    Optional<Integer> observed = requests.requestAt(index);
                    if (observed.isPresent()) {
                        mailbox = List.of(observed.get(), 0, 0);
                    } else {
                        inputEvidence = S3kAudioTick.ProducerInputEvidence.unavailable(
                                "mailbox input was unavailable for the first observable service after reference producer interrupt services suspended");
                    }
                }
            }
            serviceConsumer.accept(new S3kAudioTick(ordinal++, frame.lag(),
                    mailbox, frame.global(), frame.tracks(), serviceWrites,
                    inputEvidence));
            if (mailbox.contains(S3kAudioParitySchema.CMD_SEGA)) {
                segaPcmSuspended = true;
            }
        }
        return metadata;
    }

    private static boolean isPcmTransportWrite(AudioParityChipWrite write) {
        return write.chip().equals("ym2612") && write.port() == 0
                && (write.register() == 0x2a
                        || write.register() == 0x2b && write.value() == 0x80);
    }

    static Metadata parseMetadata(String line) throws IOException {
        JsonNode row = JSON.readTree(line);
        if (!row.path("row").asText().equals("metadata")) {
            throw new IllegalArgumentException("first row is not capture metadata");
        }
        String schema = row.path("schema").asText();
        if (!S3kAudioParitySchema.VERSION.equals(schema)) {
            throw new IllegalArgumentException("unknown S3K oracle schema: " + schema);
        }
        String romSha1 = row.path("rom_sha1").asText();
        if (!S3kAudioParitySchema.S3K_LOCKED_ON_SHA1.equals(romSha1)) {
            throw new IllegalArgumentException("reference was not captured from the pinned locked-on ROM");
        }
        JsonNode window = row.path("ram_window");
        if (window.path("start").asInt(-1) != S3kAudioParitySchema.RAM_WINDOW_START
                || window.path("exclusive_end").asInt(-1) != S3kAudioParitySchema.RAM_WINDOW_END) {
            throw new IllegalArgumentException("reference RAM window is not zDataStart..zTracksSaveEnd");
        }
        return new Metadata(schema, romSha1,
                row.path("movie").path("name").asText(),
                row.path("movie").path("sha256").asText(),
                row.path("movie").path("frame_count").asInt(),
                row.path("ticks").asInt(),
                row.path("observer_core_zst_sha256").asText());
    }

    private static S3kAudioTick parseTick(JsonNode row, int expectedOrdinal) {
        int frame = row.path("frame").asInt(-1);
        if (frame != expectedOrdinal) {
            throw new IllegalArgumentException(
                    "tick ordinal mismatch: expected " + expectedOrdinal + ", got " + frame);
        }
        List<Integer> mailbox = new ArrayList<>(List.of(0, 0, 0));
        JsonNode mailboxNode = row.path("mailbox");
        if (mailboxNode.isArray()) {
            for (int index = 0; index < 3; index++) {
                mailbox.set(index, mailboxNode.get(index).asInt());
            }
        }
        byte[] ram = HexFormat.of().parseHex(row.path("ram").asText());
        if (ram.length != S3kAudioParitySchema.RAM_WINDOW_END - S3kAudioParitySchema.RAM_WINDOW_START) {
            throw new IllegalArgumentException("RAM snapshot has wrong length at tick " + frame);
        }
        List<AudioParityChipWrite> writes = new ArrayList<>();
        for (JsonNode write : row.path("writes")) {
            String chip = write.get(0).asText();
            if (chip.equals("ym")) {
                // ["ym", port, register, value, source_cpu]. The fixture
                // retains the whole bus, but this is a Z80 driver oracle: the
                // 68k PSGInitValues bootstrap and other host writes belong to
                // a separate execution boundary (sonic3k.asm:175-184,260).
                if (!z80Owned(write, 4, frame)) {
                    continue;
                }
                writes.add(AudioParityChipWrite.ym2612(write.get(1).asInt(),
                        write.get(2).asInt(), write.get(3).asInt()));
            } else if (chip.equals("psg")) {
                if (!z80Owned(write, 2, frame)) {
                    continue;
                }
                writes.add(AudioParityChipWrite.psg(write.get(1).asInt()));
            } else {
                throw new IllegalArgumentException("unknown chip in write row: " + chip);
            }
        }
        return new S3kAudioTick(frame, row.path("lag").asBoolean(false), mailbox,
                decodeGlobals(ram), decodeTracks(ram), writes);
    }

    private static boolean z80Owned(JsonNode write, int sourceIndex, int frame) {
        int sourceCpu = write.path(sourceIndex).asInt(-1);
        if (sourceCpu != S3kAudioParitySchema.SOURCE_CPU_Z80
                && sourceCpu != S3kAudioParitySchema.SOURCE_CPU_M68K) {
            throw new IllegalArgumentException(
                    "unknown write source CPU " + sourceCpu + " at tick " + frame);
        }
        return sourceCpu == S3kAudioParitySchema.SOURCE_CPU_Z80;
    }

    private static int ramByte(byte[] ram, int address) {
        return ram[address - S3kAudioParitySchema.RAM_WINDOW_START] & 0xff;
    }

    static S3kAudioTick.GlobalState decodeGlobals(byte[] ram) {
        return new S3kAudioTick.GlobalState(
                ramByte(ram, 0x1C24), ramByte(ram, 0x1C13), ramByte(ram, 0x1C08),
                ramByte(ram, 0x1C2F), ramByte(ram, 0x1C30), ramByte(ram, 0x1C0D),
                ramByte(ram, 0x1C0E), ramByte(ram, 0x1C0F), ramByte(ram, 0x1C29),
                ramByte(ram, 0x1C10),
                List.of(ramByte(ram, 0x1C05), ramByte(ram, 0x1C06), ramByte(ram, 0x1C07)),
                ramByte(ram, 0x1C09), ramByte(ram, 0x1C04));
    }

    static List<S3kAudioTrackState> decodeTracks(byte[] ram) {
        List<S3kAudioTrackState> tracks = new ArrayList<>(S3kAudioParitySchema.ROLES.size());
        for (int index = 0; index < S3kAudioParitySchema.ROLES.size(); index++) {
            String role = S3kAudioParitySchema.ROLES.get(index);
            int base = S3kAudioParitySchema.ROLE_TRACK_BASE[index];
            int control = ramByte(ram, base);
            boolean playing = (control & 0x80) != 0;
            if (!playing) {
                tracks.add(S3kAudioTrackState.idle(role));
                continue;
            }
            tracks.add(new S3kAudioTrackState(role, true,
                    (control & 0x04) != 0,
                    (control & 0x02) != 0,
                    (control & 0x10) != 0,
                    ramByte(ram, base + 0x01),
                    ramByte(ram, base + 0x02),
                    ramByte(ram, base + 0x03) | ramByte(ram, base + 0x04) << 8,
                    ramByte(ram, base + 0x05),
                    ramByte(ram, base + 0x06),
                    ramByte(ram, base + 0x07),
                    ramByte(ram, base + 0x08),
                    ramByte(ram, base + 0x0A),
                    ramByte(ram, base + 0x0B),
                    ramByte(ram, base + 0x0C),
                    ramByte(ram, base + 0x0E) << 8 | ramByte(ram, base + 0x0D),
                    ramByte(ram, base + 0x10),
                    ramByte(ram, base + 0x17),
                    ramByte(ram, base + 0x1E),
                    ramByte(ram, base + 0x1F)));
        }
        return tracks;
    }
}
