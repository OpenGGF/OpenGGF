package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineTiming;
import com.openggf.level.objects.AbstractObjectInstance;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Matched original-route post-step/support observations, comparison only.
 * Origin: DEZ retained-definition first-difference investigation, 2026-10-17.
 * Inputs: unchanged GameplayCaptureSession/driver, original BK2, explicit main/window/restore keys;
 * system properties openggf.tools.matchedStateJournal.path and .source (exact tested source label).
 * No input, clock, geometry, checkpoint, collision provider or gameplay value is written here.
 * Samples are AFTER the caller's original step/draw or restore; collision descriptors and
 * inside-method writers are unavailable. Only riding class/own slot are read, never object XY.
 * The format describes one forward window, one replay, and two restores per declared spot.
 * Caller contract: preserve original step/draw/assert/restore order and supply the current
 * session player through the session's pure field getter after any recreation. Completion means
 * the original caller returned, not that unobserved neutral ending-helper frames were sampled.
 * No maintained route or production caller is installed; future callers require their own
 * matched source/input qualification. Dictionary ordinals are local; compare decoded class names.
 * Compare: GameplayMatchedStateJournal baseline.bin candidate.bin original.bk2
 */
public final class GameplayMatchedStateJournal implements AutoCloseable {
    public enum Context { MAIN, FORWARD, REPLAY, RESTORE_BEFORE_REPLAY, RESTORE_BEFORE_MAIN }
    private static final String PROPERTY = "openggf.tools.matchedStateJournal";
    private static final int MAGIC = 0x474D4A31, VERSION = 1, CLASS = 1, ROW = 2, END = 3;
    private static final long CAP = 10L * 1024 * 1024;
    private static final int HEADER_CAP = 4096, CLASS_CAP = 64, NAME_CAP = 512, FOOTER_BYTES = 41;
    private static final int KEY_FIELDS = 6, RIDING_CLASS = 33;
    private static final String[] FIELDS = {
            "sequence", "outer", "context", "window_offset", "submitted_movie_index", "raw_input_packed",
            "zone_act_words", "game_mode", "load_generation_low", "load_generation_high",
            "manager_level_frame", "object_frame", "object_vbla", "power_on_vint32",
            "p1_centre_x", "p1_centre_y", "p1_x_fraction16", "p1_y_fraction16",
            "p1_x_velocity", "p1_y_velocity", "p1_ground_velocity", "p1_angle",
            "p1_radii_words", "p1_declared_flags", "p1_logical_input", "p1_rings",
            "camera_x", "camera_y", "camera_x_copy", "camera_y_copy", "camera_min_y", "camera_max_y",
            "riding_own_slot", "riding_class_dictionary", "fresh_boundary_pending", "p1_shield_type"
    };
    private final DataOutputStream output;
    private final BoundedOutput bytes;
    private final Header header;
    private final Map<String, Integer> classes = new LinkedHashMap<>();
    private final int[] counts = new int[5];
    private int rows, verified;
    private boolean complete, failed, closed;

    private record Header(String origin, String source, String movieHash, int frames, int spots, int window) {
        long expectedRows() { return Math.addExact(frames, Math.multiplyExact((long) spots, 2L * window + 2)); }
    }
    private record Projection(int[] values, String ridingClass) { }
    private record Row(int[] values, String ridingClass) { }
    private record Journal(Header header, List<Row> rows, boolean complete) { }

    private GameplayMatchedStateJournal(DataOutputStream output, BoundedOutput bytes, Header header) {
        this.output = output; this.bytes = bytes; this.header = header;
    }

    /** Disabled without .path. Enabled output must be a new immediate child of this cwd's target/. */
    public static GameplayMatchedStateJournal open(String origin, Path movie, int frames, int spots, int window)
            throws IOException {
        String destination = System.getProperty(PROPERTY + ".path");
        if (destination == null) return new GameplayMatchedStateJournal(null, null, null);
        String source = System.getProperty(PROPERTY + ".source");
        requireText(origin); requireText(source);
        Header header = new Header(origin, source, digest(movie), frames, spots, window);
        validateShape(header);
        Path target = Path.of("target").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) throw new IOException("target must be a real directory");
        Path path = Path.of(destination).toAbsolutePath().normalize();
        if (path.getParent() == null || !path.getParent().toRealPath().equals(target.toRealPath()))
            throw new IOException("Journal output must be an immediate target/ child");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(buffer)) {
            data.writeInt(MAGIC); data.writeInt(VERSION); data.writeInt(FIELDS.length);
            for (String field : FIELDS) data.writeUTF(field);
            data.writeUTF(origin); data.writeUTF(source); data.writeUTF(header.movieHash());
            data.writeInt(frames); data.writeInt(spots); data.writeInt(window);
        }
        if (buffer.size() + 4 > HEADER_CAP) throw new IOException("Journal header cap");
        BoundedOutput bytes = new BoundedOutput(new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)));
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeInt(buffer.size()); out.write(buffer.toByteArray());
            return new GameplayMatchedStateJournal(out, bytes, header);
        } catch (IOException failure) { out.close(); throw failure; }
    }

    public void observe(int outer, Context context, int offset, Bk2FrameInput input,
                        GameplayCaptureSession session) throws IOException {
        if (output == null) return;
        if (closed || complete || failed) throw new IOException("Journal is not accepting observations");
        try {
            Projection before = projection(session);
            int[] row = before.values().clone();
            row[0] = rows; row[1] = outer; row[2] = context.ordinal(); row[3] = offset;
            row[4] = input.frameIndex(); row[5] = packedInput(input);
            validateKey(row, header, rows);
            validateOrder(row, counts, header);
            if (rows >= header.expectedRows()) throw new IOException("Journal row cap");
            row[RIDING_CLASS] = classId(before.ridingClass());
            output.writeInt(ROW);
            for (int value : row) output.writeInt(value);
            output.flush();
            Projection after = projection(session);
            if (!Arrays.equals(before.values(), after.values())
                    || !Objects.equals(before.ridingClass(), after.ridingClass()))
                throw new IOException("Getter passivity changed at sequence " + rows);
            rows++; verified++; counts[context.ordinal()]++;
        } catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }

    /** Call only after the caller's original full-route final assertions/end helper return. */
    public void finish() throws IOException {
        if (output == null) return;
        requireComplete(header, rows, counts);
        complete = true;
    }

    @Override public void close() throws IOException {
        if (output == null || closed) return;
        closed = true;
        try {
            if (!failed) {
                long finalBytes = bytes.written + FOOTER_BYTES;
                if (finalBytes > CAP || rows != verified) throw new IOException("Invalid journal footer");
                output.writeInt(END); output.writeInt(rows); output.writeInt(verified);
                for (int count : counts) output.writeInt(count);
                output.writeBoolean(complete); output.writeLong(finalBytes);
                output.flush();
                if (bytes.written != finalBytes) throw new IOException("Journal byte accounting");
            }
        } finally { output.close(); }
    }

    private int classId(String name) throws IOException {
        if (name == null) return 0;
        Integer existing = classes.get(name);
        if (existing != null) return existing;
        requireText(name);
        if (classes.size() == CLASS_CAP) throw new IOException("Riding class dictionary cap");
        int id = classes.size() + 1;
        output.writeInt(CLASS); output.writeInt(id); output.writeUTF(name);
        classes.put(name, id);
        return id;
    }

    private static Projection projection(GameplayCaptureSession session) throws IOException {
        var p = session.player();
        var level = GameServices.levelOrNull();
        var camera = GameServices.camera();
        var objects = level == null ? null : level.getObjectManager();
        var riding = objects == null ? null : objects.getRidingObject(p);
        String ridingClass = riding == null ? null : riding.getClass().getName();
        int slot = riding == null ? -1 : riding instanceof AbstractObjectInstance object
                ? object.getSlotIndex() : Integer.MIN_VALUE;
        long generation = level == null ? -1 : level.getCompletedProductionLoadGeneration();
        // Declared observation bits, NOT the ROM status byte. No optional object getters.
        int flags = (p.getAir() ? 1 : 0) | (p.isOnObject() ? 2 : 0) | (p.getRolling() ? 4 : 0)
                | (p.getSpindash() ? 8 : 0) | (p.isHurt() ? 16 : 0) | (p.getDead() ? 32 : 0)
                | (p.isObjectControlled() ? 64 : 0) | (p.isObjectControlSuppressesMovement() ? 128 : 0)
                | (p.isLogicalJumpPressActive() ? 256 : 0) | (p.getGroundMode().ordinal() << 9);
        int missing = Integer.MIN_VALUE;
        int[] values = {0, 0, 0, 0, 0, 0,
                level == null ? missing : words(level.getCurrentZone(), level.getCurrentAct()),
                session.loop().getCurrentGameMode().ordinal(), (int) generation, (int) (generation >>> 32),
                level == null ? missing : level.getFrameCounter(),
                objects == null ? missing : objects.getFrameCounter(),
                objects == null ? missing : objects.getVblaCounter(), (int) EngineTiming.vIntRunCounter().value(),
                p.getCentreX(), p.getCentreY(), p.getXSubpixelRaw(), p.getYSubpixelRaw(),
                p.getXSpeed(), p.getYSpeed(), p.getGSpeed(), p.getAngle(), words(p.getXRadius(), p.getYRadius()),
                flags, p.getLogicalInputState(), p.getRingCount(),
                camera == null ? missing : camera.getX(), camera == null ? missing : camera.getY(),
                camera == null ? missing : camera.getXCopy(), camera == null ? missing : camera.getYCopy(),
                camera == null ? missing : camera.getMinY(), camera == null ? missing : camera.getMaxY(),
                slot, 0, level == null ? missing : (level.hasPendingFreshLevelTransitionBoundary() ? 1 : 0),
                p.getShieldType() == null ? -1 : p.getShieldType().ordinal()};
        if (values.length != FIELDS.length) throw new IOException("Projection/schema mismatch");
        return new Projection(values, ridingClass);
    }

    private static int words(int high, int low) throws IOException {
        if (high < Short.MIN_VALUE || high > 0xFFFF || low < Short.MIN_VALUE || low > 0xFFFF)
            throw new IOException("Observation word exceeds declared width");
        return (high & 0xFFFF) << 16 | (low & 0xFFFF);
    }

    private static int packedInput(Bk2FrameInput input) throws IOException {
        if ((input.p1InputMask() & ~31) != 0 || (input.p2InputMask() & ~31) != 0
                || (input.p1ActionMask() & ~7) != 0 || (input.p2ActionMask() & ~7) != 0)
            throw new IOException("Input exceeds declared packed schema");
        return input.p1InputMask() | input.p1ActionMask() << 5 | (input.p1StartPressed() ? 1 << 8 : 0)
                | input.p2InputMask() << 9 | input.p2ActionMask() << 14 | (input.p2StartPressed() ? 1 << 17 : 0)
                | (input.debugModeTogglePressed() ? 1 << 18 : 0) | (input.debugShiftDown() ? 1 << 19 : 0)
                | (input.debugControlDown() ? 1 << 20 : 0) | (input.debugAltDown() ? 1 << 21 : 0)
                | (input.debugSuperDown() ? 1 << 22 : 0);
    }

    private static void validateKey(int[] row, Header h, int sequence) throws IOException {
        int context = row[2], n = row[3];
        if (row[0] != sequence || row[1] < 0 || row[1] >= h.frames() || context < 0 || context > 4
                || ((context == 1 || context == 2) ? n < 1 || n > h.window() : n != 0)
                || row[4] != row[1] + n || row[4] >= h.frames())
            throw new IOException("Invalid explicit journal key at sequence " + sequence);
    }

    private static void validateOrder(int[] row, int[] counts, Header h) throws IOException {
        int outer = row[1], context = row[2], n = row[3];
        if (context == 0) {
            if (outer != counts[0] || counts[1] != counts[2] || counts[3] != counts[4]
                    || counts[1] != counts[4] * h.window()) throw new IOException("Main/window ordering");
        } else {
            if (outer != counts[0] - 1) throw new IOException("Window outer key");
            boolean valid = switch (context) {
                case 1 -> counts[3] == counts[4] && counts[2] == counts[4] * h.window()
                        && n == counts[1] - counts[4] * h.window() + 1;
                case 2 -> counts[3] == counts[4] + 1 && counts[1] == counts[3] * h.window()
                        && n == counts[2] - counts[4] * h.window() + 1;
                case 3 -> counts[3] == counts[4] && counts[1] == (counts[3] + 1) * h.window()
                        && counts[2] == counts[4] * h.window();
                case 4 -> counts[3] == counts[4] + 1 && counts[1] == counts[2]
                        && counts[2] == counts[3] * h.window();
                default -> false;
            };
            if (!valid) throw new IOException("Window/restore ordering");
        }
    }

    private static void validateShape(Header h) throws IOException {
        try {
            long records = h.expectedRows();
            long worst = Math.addExact(HEADER_CAP + CLASS_CAP * (10L + 3 * NAME_CAP) + FOOTER_BYTES,
                    Math.multiplyExact(records, 148L)); // 36 ints + an explicit ROW tag.
            if (h.frames() <= 0 || h.spots() < 0 || h.window() <= 0 || FIELDS.length != 36
                    || records > Integer.MAX_VALUE || worst > CAP)
                throw new IOException("Unsupported journal shape/cap");
        } catch (ArithmeticException invalid) { throw new IOException("Journal shape overflow", invalid); }
    }

    private static void requireComplete(Header h, int rows, int[] counts) throws IOException {
        if (rows != h.expectedRows() || counts[0] != h.frames() || counts[1] != h.spots() * h.window()
                || counts[2] != counts[1] || counts[3] != h.spots() || counts[4] != h.spots())
            throw new IOException("Full original movie/windows were not observed");
    }

    private static void requireText(String text) throws IOException {
        if (text == null || text.isBlank() || text.length() > NAME_CAP || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > NAME_CAP)
            throw new IOException("Missing/oversized journal metadata");
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) >= 0) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static final class BoundedOutput extends FilterOutputStream {
        long written;
        BoundedOutput(OutputStream out) { super(out); }
        @Override public void write(int value) throws IOException {
            if (written >= CAP) throw new IOException("Journal byte cap");
            out.write(value); written++;
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            if (length < 0 || written + length > CAP) throw new IOException("Journal byte cap");
            out.write(data, offset, length); written += length;
        }
    }

    /** Validates both files/keys/inputs before one difference. Exit0 means valid comparison, never parity acceptance. */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("baseline.bin candidate.bin original.bk2");
        Journal a = read(Path.of(args[0]), Path.of(args[2]));
        Journal b = read(Path.of(args[1]), Path.of(args[2]));
        Header x = a.header(), y = b.header();
        if (!x.origin().equals(y.origin()) || !x.movieHash().equals(y.movieHash())
                || x.frames() != y.frames() || x.spots() != y.spots() || x.window() != y.window())
            throw new IOException("Unmatched original driver/movie shape");
        int common = Math.min(a.rows().size(), b.rows().size());
        for (int n = 0; n < common; n++)
            for (int field = 0; field < KEY_FIELDS; field++)
                if (a.rows().get(n).values()[field] != b.rows().get(n).values()[field])
                    throw new IOException("Unmatched key " + n + "/" + FIELDS[field]);
        System.out.println("Validated sources=" + x.source() + " / " + y.source() + "; rows="
                + a.rows().size() + "/" + b.rows().size() + "; complete=" + a.complete() + "/" + b.complete()
                + "; collision descriptors/inside-writer unavailable");
        for (int n = 0; n < common; n++) {
            Row left = a.rows().get(n), right = b.rows().get(n);
            for (int field = KEY_FIELDS; field < FIELDS.length; field++) {
                String lv = field == RIDING_CLASS ? Objects.toString(left.ridingClass(), "none") : Integer.toString(left.values()[field]);
                String rv = field == RIDING_CLASS ? Objects.toString(right.ridingClass(), "none") : Integer.toString(right.values()[field]);
                if (!lv.equals(rv)) {
                    System.out.println("First difference sequence=" + n + " outer=" + left.values()[1]
                            + " context=" + Context.values()[left.values()[2]] + " n=" + left.values()[3]
                            + " movie=" + left.values()[4] + " field=" + FIELDS[field] + " baseline=" + lv + " candidate=" + rv);
                    return;
                }
            }
        }
        System.out.println("No state difference in the " + common + " validated common-prefix observations; partial != full-route qualification.");
    }

    private static Journal read(Path path, Path moviePath) throws Exception {
        long size = Files.size(path);
        if (size > CAP) throw new IOException("Journal exceeds cap");
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(moviePath);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int length = in.readInt();
            if (length < 1 || length + 4 > HEADER_CAP) throw new IOException("Invalid header length");
            byte[] headerBytes = in.readNBytes(length);
            if (headerBytes.length != length) throw new EOFException("Partial header");
            Header h;
            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(headerBytes))) {
                if (data.readInt() != MAGIC || data.readInt() != VERSION || data.readInt() != FIELDS.length)
                    throw new IOException("Unsupported journal schema");
                for (String field : FIELDS) if (!field.equals(data.readUTF())) throw new IOException("Field schema mismatch");
                h = new Header(data.readUTF(), data.readUTF(), data.readUTF(), data.readInt(), data.readInt(), data.readInt());
                requireText(h.origin()); requireText(h.source()); validateShape(h);
                if (!h.movieHash().equals(digest(moviePath)) || h.frames() != movie.getFrameCount()
                        || h.frames() <= 0 || h.spots() < 0 || h.window() <= 0 || data.read() != -1)
                    throw new IOException("Original movie/header binding mismatch");
            }
            Map<Integer, String> classes = new HashMap<>();
            Set<String> names = new HashSet<>();
            List<Row> rows = new ArrayList<>(); int[] counts = new int[5];
            while (true) {
                int tag = in.readInt();
                if (tag == CLASS) {
                    int id = in.readInt(); String name = in.readUTF(); requireText(name);
                    if (id != classes.size() + 1 || classes.size() == CLASS_CAP || !names.add(name)) throw new IOException("Invalid class dictionary");
                    classes.put(id, name);
                } else if (tag == ROW) {
                    if (rows.size() >= h.expectedRows()) throw new IOException("Row cap");
                    int[] values = new int[FIELDS.length];
                    for (int i = 0; i < values.length; i++) values[i] = in.readInt();
                    validateKey(values, h, rows.size()); validateOrder(values, counts, h);
                    if (values[5] != packedInput(movie.getFrame(values[4]))) throw new IOException("Original movie input mismatch");
                    int id = values[RIDING_CLASS];
                    if (id < 0 || id > classes.size()) throw new IOException("Undefined class id");
                    rows.add(new Row(values, classes.get(id))); counts[values[2]]++;
                } else if (tag == END) {
                    int declaredRows = in.readInt(), verified = in.readInt();
                    if (declaredRows != rows.size() || verified != rows.size()) throw new IOException("Passivity/count footer mismatch");
                    for (int count : counts) if (count != in.readInt()) throw new IOException("Context count footer mismatch");
                    boolean complete = in.readBoolean(); long declaredBytes = in.readLong();
                    if (declaredBytes != size || in.read() != -1) throw new IOException("Byte/footer/trailing-data mismatch");
                    if (complete) requireComplete(h, rows.size(), counts);
                    return new Journal(h, rows, complete);
                } else throw new IOException("Unknown journal record");
            }
        }
    }
}
