package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.RingSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streams public gameplay state before step, after step and after normal drawing.
 * Inputs are an explicit original S3K ROM, BK2/input log, input limit and NEW output
 * directory. The sole profile is cold SSZ zone 10/act 0, native 800, Sonic + Tails,
 * donor off, Settings7/title false; it does not establish other route coverage.
 *
 * <p>Origin: WidePaired R5 ordinary projection, 2026-10-21, dd77b57c2483.
 * Only the public projection is promoted: no private owner carriers, restore,
 * state import, authored steering, terrain reprobes or gameplay seeds. Ring data
 * comes from the read-only copied RingManager.capture() snapshot. Reference labels
 * bind actual Java objects within this invocation, NOT native addresses or
 * cross-JVM identity. Output is temporary observation, not JUnit/parity evidence.
 *
 * <pre>
 * GameplayStateProjectionTool --profile s3k-ssz1-paired-800 --rom /original/s3k.gen
 *   --input route.bk2 --frames 9914 --out-dir target/new-projection
 * </pre>
 * Use the ordinary capture classpath and display-free EGL backend on Linux.
 */
public final class GameplayStateProjectionTool {
    static final String PROFILE = "s3k-ssz1-paired-800";
    static final int MAX_INPUTS = 20_000;
    static final int MAX_BINDINGS = 1_024;
    static final int MAX_ROW_BYTES = 64 * 1_024;
    static final long MAX_OUTPUT_BYTES = 128L * 1_024 * 1_024;
    private static final List<String> ACTOR_FIELDS = List.of(
            "binding", "type", "x", "y", "x_sub", "y_sub", "x_vel", "y_vel", "g_speed",
            "air", "rolling", "jumping", "spindash", "angle", "direction", "anim", "mapping",
            "anim_index", "anim_tick", "x_radius", "y_radius", "rings", "shield", "shield_type",
            "hurt", "iframes", "invincible_frames", "dead", "control_locked", "object_controlled",
            "object_allows_cpu", "object_suppresses_movement", "native_slot_present", "on_object",
            "interact_slot", "cpu_controlled", "cpu_state", "cpu_frame_counter", "cpu_control_counter",
            "cpu_leader_binding", "cpu_up", "cpu_down", "cpu_left", "cpu_right", "cpu_jump_press", "cpu_jump");
    static final List<String> HEADER = header();

    private final LocalBindings bindings = new LocalBindings();

    private GameplayStateProjectionTool() { }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        new GameplayStateProjectionTool().run(arguments);
        System.out.printf("ORDINARY_PROJECTION profile=%s inputs=%d rows=%d fields=%d junit_tests=0 skips=0 out=%s%n",
                PROFILE, arguments.frames(), arguments.frames() * 3, HEADER.size(), arguments.outDir());
    }

    private void run(Arguments arguments) throws IOException, XMLStreamException {
        requireFile(arguments.rom(), "--rom");
        requireFile(arguments.input(), "--input");
        Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(arguments.input());
        validateMovie(movie, arguments.frames());
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "tails", "off", null, null, null);
        // Reserve output before Engine boot. Failed/partial runs remove only their own products.
        try (ProjectionOutput output = new ProjectionOutput(arguments.outDir())) {
            List<String> last = null;
            try (var session = new GameplayCaptureSession(settings)) {
                session.boot(arguments.rom(), 10, 0, settings);
                bindings.bind(GameServices.level().getCurrentLevel(), "world0");
                bindings.bind(session.player(), "p1_0");
                var sidekicks = GameServices.sprites().getRegisteredSidekicks();
                if (!sidekicks.isEmpty()) {
                    bindings.bind(sidekicks.getFirst(), "p2_0");
                }
                for (int input = 0; input < arguments.frames(); input++) {
                    Bk2FrameInput pad = movie.getFrame(input);
                    output.row(row(session, input, "PRE_STEP", pad));
                    session.step(pad);
                    output.row(row(session, input, "POST_STEP", pad));
                    session.renderFrame();
                    last = row(session, input, "POST_DRAW", pad);
                    output.row(last);
                }
            }
            output.complete(arguments, last);
        }
    }

    private static void requireFile(Path path, String option) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(option + " must name a readable file: " + path);
        }
    }

    static void validateMovie(Bk2Movie movie, int frames) {
        if (frames < 1 || frames > MAX_INPUTS || frames > movie.getFrameCount()) {
            throw new IllegalArgumentException("--frames must be 1.." + MAX_INPUTS
                    + " and <= movie input count (" + movie.getFrameCount() + ")");
        }
        for (int input = 0; input < frames; input++) {
            Bk2FrameInput pad = movie.getFrame(input);
            if (pad.p2InputMask() != 0 || pad.p2ActionMask() != 0 || pad.p2StartPressed()
                    || pad.debugModeTogglePressed() || pad.debugShiftDown() || pad.debugControlDown()
                    || pad.debugAltDown() || pad.debugSuperDown()) {
                throw new IllegalArgumentException(PROFILE + " requires neutral P2 and no debug input at " + input);
            }
        }
    }

    private List<String> row(GameplayCaptureSession session, int input, String phase, Bk2FrameInput pad) {
        var level = GameServices.level();
        var sprites = GameServices.sprites();
        var sidekicks = sprites.getRegisteredSidekicks();
        var camera = GameServices.camera();
        var row = new ArrayList<String>(HEADER.size());
        put(row, input, phase, pad.rawLine(), session.loop().getCurrentGameMode(),
                level.getCurrentZone(), level.getCurrentAct(), bindings.id(level.getCurrentLevel()),
                bindings.id(session.player()), bindings.id(sprites.getMainPlayable()),
                bindings.id(camera.getFocusedSprite()), sidekicks.size(), GameServices.gameState().getLives(),
                camera.getX() & 0xffff, camera.getY() & 0xffff);
        actor(row, session.player());
        actor(row, sidekicks.isEmpty() ? null : sidekicks.getFirst());
        appendRings(row, level.getRingManager().capture());
        return row;
    }

    private void actor(List<String> row, AbstractPlayableSprite player) {
        if (player == null) {
            for (int i = 0; i < ACTOR_FIELDS.size(); i++) {
                row.add("");
            }
            return;
        }
        var cpu = player.getCpuController();
        put(row, bindings.id(player), player.getClass().getSimpleName(),
                player.getCentreX() & 0xffff, player.getCentreY() & 0xffff,
                player.getXSubpixelRaw(), player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(),
                player.getGSpeed(), player.getAir(), player.getRolling(), player.isJumping(), player.getSpindash(),
                player.getAngle() & 0xff, player.getDirection(), player.getAnimationId(), player.getMappingFrame(),
                player.getAnimationFrameIndex(), player.getAnimationTick(), player.getXRadius(), player.getYRadius(),
                player.getRingCount(), player.hasShield(), player.getShieldType(), player.isHurt(),
                player.getInvulnerableFrames(), player.getInvincibleFrames(), player.getDead(),
                player.isControlLocked(), player.isObjectControlled(), player.isObjectControlAllowsCpu(),
                player.isObjectControlSuppressesMovement(), player.isNativeSlotPresent(), player.isOnObject(),
                player.getInteractSlotIndex(), player.isCpuControlled(), cpu == null ? "" : cpu.getState(),
                cpu == null ? "" : cpu.getCurrentCpuFrameCounter(), cpu == null ? "" : cpu.getDiagnosticControlCounter(),
                cpu == null ? "" : bindings.id(cpu.getLeader()), cpu == null ? "" : cpu.getInputUp(),
                cpu == null ? "" : cpu.getInputDown(), cpu == null ? "" : cpu.getInputLeft(),
                cpu == null ? "" : cpu.getInputRight(), cpu == null ? "" : cpu.getInputJumpPress(),
                cpu == null ? "" : cpu.getInputJump());
    }

    static void appendRings(List<String> row, RingSnapshot snapshot) {
        var sparkles = new StringBuilder();
        for (var entry : snapshot.sparkleTimers()) {
            if (!sparkles.isEmpty()) sparkles.append(';');
            sparkles.append(entry.ringIndex()).append(':').append(entry.startFrame());
        }
        var attracted = new StringBuilder();
        for (var entry : snapshot.attractedRings()) {
            if (!attracted.isEmpty()) attracted.append(';');
            attracted.append(entry.sourceIndex()).append(':').append(entry.slotIndex()).append(':')
                    .append(entry.objectSlotIndex()).append(':').append(entry.active()).append(':')
                    .append(entry.x()).append(':').append(entry.y()).append(':').append(entry.xSub()).append(':')
                    .append(entry.ySub()).append(':').append(entry.xVel()).append(':').append(entry.yVel()).append(':')
                    .append(entry.collected()).append(':').append(entry.sparkleStartFrame()).append(':')
                    .append(entry.mappingFrame()).append(':').append(entry.animationFrameTimer()).append(':')
                    .append(entry.sparkleAnimationFrame());
        }
        put(row, Arrays.toString(snapshot.collectedWords()), sparkles, snapshot.placementCursorIndex(),
                snapshot.placementLastCameraX(), Arrays.toString(snapshot.activeSpawnIndices()),
                snapshot.spillAnimCounter(), snapshot.spillAnimAccum(), snapshot.spillAnimFrame(), attracted);
    }

    private static void put(List<String> row, Object... values) {
        for (Object value : values) row.add(value == null ? "" : String.valueOf(value));
    }

    private static List<String> header() {
        var fields = new ArrayList<>(List.of("input", "phase", "pad", "mode", "zone", "act", "world_binding",
                "session_player_binding", "main_binding", "focus_binding", "sidekick_count", "lives",
                "camera_x", "camera_y"));
        for (String actor : List.of("p1", "p2")) {
            for (String field : ACTOR_FIELDS) fields.add(actor + "_" + field);
        }
        fields.addAll(List.of("rings_collected_words", "rings_sparkles", "rings_placement_cursor",
                "rings_last_camera_x", "rings_active_indices", "rings_spill_counter", "rings_spill_accum",
                "rings_spill_frame", "rings_attracted"));
        return List.copyOf(fields);
    }

    static byte[] csv(List<String> row) {
        var line = new StringBuilder();
        for (String value : row) {
            if (!line.isEmpty()) line.append(',');
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        byte[] bytes = line.append('\n').toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_ROW_BYTES) throw new IllegalArgumentException("Projection row exceeds 64 KiB");
        return bytes;
    }

    record Arguments(Path rom, Path input, int frames, Path outDir) {
        static Arguments parse(String[] args) {
            Map<String, String> options = new HashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                String name = args[i];
                if (!List.of("--profile", "--rom", "--input", "--frames", "--out-dir").contains(name)) {
                    throw new IllegalArgumentException("Unknown option: " + name);
                }
                if (i + 1 == args.length || args[i + 1].isBlank() || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for " + name);
                }
                if (options.putIfAbsent(name, args[i + 1]) != null) {
                    throw new IllegalArgumentException("Duplicate option: " + name);
                }
            }
            for (String name : List.of("--profile", "--rom", "--input", "--frames", "--out-dir")) {
                if (!options.containsKey(name)) throw new IllegalArgumentException("Required option: " + name);
            }
            if (!PROFILE.equals(options.get("--profile"))) {
                throw new IllegalArgumentException("Only supported --profile: " + PROFILE);
            }
            int frames;
            try {
                frames = Integer.parseInt(options.get("--frames"));
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("--frames must be an integer in 1.." + MAX_INPUTS, failure);
            }
            if (frames < 1 || frames > MAX_INPUTS) {
                throw new IllegalArgumentException("--frames must be 1.." + MAX_INPUTS);
            }
            Path rom = Path.of(options.get("--rom"));
            if (!rom.isAbsolute()) throw new IllegalArgumentException("--rom must be an explicit absolute path");
            return new Arguments(rom.normalize(), Path.of(options.get("--input")).toAbsolutePath().normalize(),
                    frames, Path.of(options.get("--out-dir")).toAbsolutePath().normalize());
        }
    }

    /** Strong, invocation-local bindings; equality and identity hashes are never used as labels. */
    static final class LocalBindings {
        private final IdentityHashMap<Object, String> objects = new IdentityHashMap<>();
        private int next;

        void bind(Object object, String label) {
            if (object == null || objects.containsKey(object) || objects.values().stream().anyMatch(label::equals)) {
                throw new IllegalArgumentException("Duplicate/null local binding");
            }
            if (objects.size() == MAX_BINDINGS) throw new IllegalStateException("Local binding limit reached");
            objects.put(object, label);
        }

        String id(Object object) {
            if (object == null) return "none";
            String label = objects.get(object);
            if (label == null) {
                label = "other" + next++;
                bind(object, label);
            }
            return label;
        }
    }

    /** Streams bounded products into one newly created directory; incomplete products are removed. */
    static final class ProjectionOutput implements AutoCloseable {
        private final Path directory;
        private final List<Path> ownedFiles = new ArrayList<>();
        private OutputStream csv;
        private long bytes;
        private int rows;
        private boolean completed;

        ProjectionOutput(Path directory) throws IOException {
            this.directory = Files.createDirectory(directory);
            try {
                csv = create("projection.csv");
                writeCsv(HEADER);
            } catch (IOException | RuntimeException failure) {
                try { close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private OutputStream create(String name) throws IOException {
            Path path = directory.resolve(name);
            OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            ownedFiles.add(path);
            return new OutputStream() {
                @Override public void write(int value) throws IOException {
                    write(new byte[]{(byte) value}, 0, 1);
                }
                @Override public void write(byte[] buffer, int offset, int length) throws IOException {
                    if (bytes + length > MAX_OUTPUT_BYTES) throw new IOException("Projection output exceeds 128 MiB");
                    stream.write(buffer, offset, length);
                    bytes += length;
                }
                @Override public void close() throws IOException { stream.close(); }
            };
        }

        private void writeCsv(List<String> values) throws IOException {
            byte[] line = GameplayStateProjectionTool.csv(values);
            csv.write(line);
        }

        void row(List<String> values) throws IOException {
            if (completed || csv == null || rows == MAX_INPUTS * 3) throw new IllegalStateException("Projection row limit/closed output");
            if (values.size() != HEADER.size()) throw new IllegalArgumentException("Expected 115 projection fields");
            writeCsv(values);
            rows++;
        }

        void complete(Arguments arguments, List<String> endpoint) throws IOException, XMLStreamException {
            if (completed || csv == null || rows != arguments.frames() * 3
                    || endpoint == null || endpoint.size() != HEADER.size()) {
                throw new IllegalStateException("Incomplete projection");
            }
            csv.close();
            csv = null;
            try (OutputStream stream = create("projection.xml")) {
                var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(stream, "UTF-8");
                xml.writeStartDocument("UTF-8", "1.0");
                xml.writeStartElement("ordinary-projection");
                xml.writeAttribute("profile", PROFILE);
                xml.writeAttribute("rom", arguments.rom().toString());
                xml.writeAttribute("input", arguments.input().toString());
                xml.writeAttribute("completed_inputs", String.valueOf(arguments.frames()));
                xml.writeAttribute("rows", String.valueOf(rows));
                xml.writeAttribute("fields", String.valueOf(HEADER.size()));
                xml.writeAttribute("skips", "0");
                xml.writeAttribute("junit_tests", "0");
                xml.writeAttribute("bindings", "LOCAL Java references, not native addresses or cross-JVM identity");
                xml.writeStartElement("properties");
                for (String key : List.of("java.version", "java.home", "java.class.path", "openggf.headless.gl",
                        "java.io.tmpdir", "org.lwjgl.system.SharedLibraryExtractPath", "sonic1.rom.path",
                        "sonic2.rom.path", "s3k.rom.path")) {
                    xml.writeEmptyElement("property");
                    xml.writeAttribute("name", key);
                    xml.writeAttribute("value", System.getProperty(key, ""));
                }
                xml.writeEndElement();
                xml.writeStartElement("endpoint");
                for (int i = 0; i < HEADER.size(); i++) {
                    xml.writeStartElement("field");
                    xml.writeAttribute("name", HEADER.get(i));
                    xml.writeCharacters(endpoint.get(i));
                    xml.writeEndElement();
                }
                xml.writeEndElement();
                xml.writeEndElement();
                xml.writeEndDocument();
                xml.close();
            }
            completed = true;
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                if (csv != null) csv.close();
            } catch (IOException closeFailure) {
                failure = closeFailure;
            }
            csv = null;
            if (!completed) {
                var paths = new ArrayList<>(ownedFiles);
                paths.add(directory); // Unknown additions survive; a nonempty directory reports cleanup failure.
                for (Path path : paths) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException cleanupFailure) {
                        if (failure == null) failure = cleanupFailure;
                        else failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (failure != null) throw failure;
        }
    }
}
