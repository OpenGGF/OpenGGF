package com.openggf.mods.code;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.presentation.SceneFrameCodec;
import com.openggf.game.presentation.SceneImage;
import com.openggf.game.presentation.SceneViewPresenter;
import com.openggf.game.rewind.RewindAdapterOwnership;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Two-JVM GolfMode integration peer, originating in the Putt Putt Paradise task.
 * Inputs: host/guest, port and SDK-validated mod jar; ROM is an absolute JVM property.
 * Commands drive public controlled stepping. Reflection configures the creator selection
 * and reads wire/presenter diagnostics; it never writes physics or restores course state.
 */
public final class GolfOnlinePeerProbe {
    private static final String OWNER = "putt-putt-paradise";
    private final boolean host;
    private final HeadlessTestFixture fixture;
    private final GameplayFrameController controller;
    private final Object creatorState;
    private final ModFaultBoundary boundary;
    private final InputHandler input = new InputHandler();
    private int previousHeld, previousActions;
    private boolean previousStart;
    private long rows, heldRows, gameplayRows, setupRows;

    private GolfOnlinePeerProbe(boolean host, HeadlessTestFixture fixture,
                                URLClassLoader loader, ModFaultBoundary boundary) throws Exception {
        this.host = host; this.fixture = fixture;
        this.boundary = boundary;
        var module = GameServices.module();
        this.controller = module.gameplayFrameController();
        this.creatorState = module.getGameService(loader.loadClass("paradise.GolfMode"));
        require(controller != null, "actual creator controller missing");
        require(creatorState != null, "published concrete creator state missing");
        require(module.getGameService(GameplayFrameController.class) == controller,
                "typed controller service must retain the published controller");
        require(module.rewindAdapters().stream().anyMatch(adapter -> adapter == controller),
                "rewind graph must capture the published controller");
        require(RewindAdapterOwnership.hasIdentity(controller, OWNER, "services/golf/mode")
                        && RewindAdapterOwnership.hasIdentity(creatorState, OWNER, "services/golf/mode"),
                "controller and concrete state must share their registered owner identity");
        String key = ((RewindSnapshottable<?>)controller).key();
        require(fixture.gameplayMode().getRewindRegistry().capture().entries().containsKey(key),
                "timeline must capture the published controller");
        require(!fixture.gameplayMode().getRewindRegistry().captureCourse().entries().containsKey(key),
                "course rollback must preserve the live controller ledger");
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 3 || args.length == 4, "expected host/guest, port, validated jar, optional rewinds");
        boolean host = args[0].equals("host");
        require(host || args[0].equals("guest"), "invalid peer role");
        Path jar = Path.of(args[2]).toAbsolutePath();
        SharedLevel bootstrap = null;
        GolfOnlinePeerProbe peer = null;
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, GolfOnlinePeerProbe.class.getClassLoader())) {
            Throwable initialFailure = null;
            try {
                bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
                var configuration = SonicConfigurationService.getInstance();
                configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, host ? "sonic" : "tails");
                configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
                configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
                GameModule effective = GameServices.module();
                var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                        owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
                try (var assets = ModAssetRoot.jar(jar.getParent(), jar, ModInputLimits.production())) {
                    var context = new ModContext(OWNER, "s2", assets);
                    ((GgfMod)loader.loadClass("paradise.PuttPuttParadiseMod").getConstructor().newInstance()).register(context);
                    var plan = context.freeze();
                    effective = new ModBackedGamePatch(plan, boundary).apply(effective, null);
                    for (var patch : plan.explicitPatches()) effective = patch.apply(effective, null);
                }
                SessionManager.clear(); GameModuleRegistry.setCurrent(effective); TestEnvironment.activeGameplayMode();
                var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
                peer = new GolfOnlinePeerProbe(host, fixture, loader, boundary);
                peer.configure(loader, Integer.parseInt(args[1]), args.length == 4 && args[3].equals("rewinds")); peer.report();
                try (var commands = new BufferedReader(new InputStreamReader(System.in))) {
                    for (String line; (line = commands.readLine()) != null;) {
                        String[] command = line.trim().split("\\s+");
                        if (command[0].equals("QUIT")) { peer.shutdown(); return; }
                        if (command[0].equals("OVERLAY")) peer.controller.drawOverlay();
                        else if (command[0].equals("READY_INTENT")) peer.readyIntent(loader, command.length > 1 && command[1].equals("stale"));
                        else {
                            require(command.length == 5 && command[0].equals("STEP"), "invalid peer command");
                            int count = Integer.parseInt(command[1]); require(count >= 1 && count <= 60, "unbounded row request");
                            for (int row = 0; row < count; row++) peer.step(Integer.parseInt(command[2]), Integer.parseInt(command[3]), Boolean.parseBoolean(command[4]));
                        }
                        peer.report();
                    }
                }
            } catch (Exception | Error failure) {
                initialFailure = failure;
                throw failure;
            } finally {
                try (AutoCloseable bootstrapCleanup = bootstrap == null ? () -> { } : bootstrap::dispose) {
                    if (peer != null) peer.shutdown();
                } catch (Exception | Error cleanupFailure) {
                    if (initialFailure == null) throw cleanupFailure;
                    initialFailure.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void configure(URLClassLoader loader, int port, boolean rewinds) throws Exception {
        Class selection = loader.loadClass("paradise.ui.GolfMenu$Selection");
        Class mode = loader.loadClass("paradise.ui.GolfMenu$Mode");
        Class character = loader.loadClass("paradise.ui.GolfMenu$CharacterChoice");
        Class viewport = loader.loadClass("paradise.ui.GolfMenu$Viewport");
        Class rules = loader.loadClass("paradise.model.RewindAllowance$Rules");
        Object choice = selection.getConstructor(mode, character, character, int.class, String.class, int.class, viewport, rules)
                .newInstance(Enum.valueOf(mode, host ? "HOST" : "JOIN"), Enum.valueOf(character, host ? "SONIC" : "TAILS"),
                        Enum.valueOf(character, host ? "TAILS" : "SONIC"), 0, "127.0.0.1", port, Enum.valueOf(viewport, "NATIVE_4_3"), rules.getMethod(rewinds ? "defaults" : "off").invoke(null));
        creatorCallback(creatorState, creatorState.getClass().getMethod("configure", selection), choice);
    }

    /**
     * Sends a READY intent for the current (or previous) guest turn straight through the room,
     * bypassing GolfMode's one-request-per-press gate: a duplicate/replayed/stale message.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void readyIntent(URLClassLoader loader, boolean stale) throws Exception {
        require(!host, "READY_INTENT is a guest command");
        Object room = room(); Object opened = value(value(room, "state"), "remoteTurnOpened");
        require(opened != null, "no turn to confirm");
        Object id = value(opened, "id");
        Class shotId = loader.loadClass("paradise.net.GolfPacket$ShotId");
        if (stale) id = shotId.getConstructors()[0].newInstance(value(id, "match"), value(id, "hole"),
                (long) value(id, "turn") - 1, (long) value(id, "shot") - 1, value(id, "owner"));
        Class action = loader.loadClass("paradise.net.GolfPacket$ShotAction");
        Object control = loader.loadClass("paradise.net.GolfPacket$ShotControl").getConstructor(shotId, action)
                .newInstance(id, Enum.valueOf(action, "READY"));
        var send = room.getClass().getDeclaredMethod("send", loader.loadClass("paradise.net.GolfPacket"));
        send.setAccessible(true); creatorCallback(room, send, control);
    }

    private void step(int held, int actions, boolean start) throws Exception {
        var before = fixture.gameplayMode().getRewindRegistry().captureCourse();
        boolean entryMusicPending = GameServices.module().getLevelInitProfile().isLevelMusicPublicationPending();
        boolean entryTitleActive = GameServices.module().getTitleCardProvider().isOverlayActive();
        String entryArtKey = GameServices.module().getObjectArtProvider()
                instanceof com.openggf.game.rewind.RewindSnapshottable<?> art ? art.key() : null;
        Object beforeMatch = creatorValue("matchState");
        Object beforePending = beforeMatch == null ? null : value(beforeMatch, "pending");
        Object beforeId = beforePending == null ? null : value(beforePending, "id");
        Object beforeOnline = field(creatorState, "online");
        Object beforeStatus = beforeOnline == null ? null : declaredValue(beforeOnline, "shotStatus");
        String beforePhase = beforeStatus == null ? "AIM" : value(beforeStatus, "phase").toString();
        var controls = PlayerInputState.of(held, held & ~previousHeld, actions, actions & ~previousActions, start, start && !previousStart);
        previousHeld = held; previousActions = actions; previousStart = start;
        LevelFrameResult result = ControlledFrameRuntime.step(fixture.gameplayMode(), input,
                LogicalInputSnapshot.ofPlayers(controls, PlayerInputState.neutral()));
        rows++;
        if (result == LevelFrameResult.GAMEPLAY_FRAME) { gameplayRows++; require(host, "guest advanced native gameplay"); }
        else if (result == LevelFrameResult.SETUP_ONLY) setupRows++;
        else if (result == LevelFrameResult.HELD && opened()) {
            heldRows++;
            Object afterMatch = creatorValue("matchState");
            // A short WATCH can finish its reverse playback in this very row.
            // Authoritative pending identity, not the guest-only room turn view,
            // identifies the deliberate whole-course restore/refund boundary.
            if (host && beforeId != null && value(afterMatch, "pending") == null
                    && value(beforeMatch, "turnSequence").equals(value(afterMatch, "turnSequence"))
                    && (beforePhase.equals("WATCH") || beforePhase.equals("REWINDING"))) return;
            var after = fixture.gameplayMode().getRewindRegistry().captureCourse();
            require(before.entries().keySet().equals(after.entries().keySet()), "held row changed course adapter layout");
            for (var entry : before.entries().entrySet()) {
                // The released native title and pending entry music retain their presentation
                // lifetime on held rows. The course's simulation owners still stay frozen.
                if (entryMusicPending && entry.getKey().equals(com.openggf.game.sonic2.timing.Sonic2LevelMusicScheduler.REWIND_KEY)) continue;
                if (entryTitleActive && entry.getKey().equals(com.openggf.game.sonic2.titlecard.TitleCardManager.REWIND_KEY)) continue;
                // The title's final dispatch queues its own standard-water/animal art.
                if (entryTitleActive && entry.getKey().equals(com.openggf.game.sonic2.resources.Sonic2PlcService.REWIND_KEY)) continue;
                if (entryTitleActive && entry.getKey().equals(entryArtKey)) continue;
                var changes = RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(), after.get(entry.getKey()));
                require(changes.isEmpty(), "held row " + rows + " changed " + entry.getKey() + ": " + changes.stream().limit(4).toList());
            }
        }
    }

    private boolean opened() throws Exception {
        Object state = creatorValue("roomState");
        return state != null && value(state, "remoteTurnOpened") != null;
    }
    private Object room() throws Exception { Object online = field(creatorState, "online"); return online == null ? null : field(online, "room"); }

    private void report() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pid", ProcessHandle.current().pid()); values.put("host", host);
        values.put("rows", rows); values.put("heldRows", heldRows); values.put("gameplayRows", gameplayRows); values.put("setupRows", setupRows);
        Object state = creatorValue("roomState");
        values.put("ready", state != null && (boolean)value(state, "ready")); values.put("held", state == null || (boolean)value(state, "held"));
        values.put("ended", state != null && (boolean)value(state, "ended")); values.put("owner", state == null ? -1 : value(state, "owner"));
        values.put("hostCharacter", state == null ? "none" : value(state, "hostCharacter")); values.put("guestCharacter", state == null ? "none" : value(state, "guestCharacter"));
        Object turn = state == null ? null : value(state, "remoteTurnOpened");
        values.put("turn", turn == null ? -1 : value(value(turn, "id"), "turn"));
        values.put("shot", turn == null ? -1 : value(value(turn, "id"), "shot"));
        values.put("wire0", state == null ? "none" : score(value(state, "player0"))); values.put("wire1", state == null ? "none" : score(value(state, "player1")));
        Object match = creatorValue("matchState"), pending = match == null ? null : value(match, "pending");
        values.put("matchStatus", match == null ? "none" : value(match, "status"));
        values.put("pending", pending == null ? -1 : value(value(pending, "id"), "shotSequence"));
        Object hud = creatorValue("hudShotView");
        values.put("hudStage", value(hud, "stage"));
        values.put("hudControls", value(hud, "showShotControls"));
        values.put("hudHint", value(hud, "hint"));
        values.put("rewindEffect", controller.rewindPresentation().intensity());
        values.put("reverseAudio", GameServices.audio().isReverseAudioOutputActive());
        values.put("logicalReverseAudio", GameServices.audio().isReverseAudioPresentationActive());
        var scores = creatorState.getClass().getDeclaredMethod("scores"); scores.setAccessible(true);
        var shown = (List<?>) creatorCallback(creatorState, scores);
        values.put("shownStrokes0", shown.isEmpty() ? -1 : value(shown.get(0), "strokes"));
        values.put("musicPending", GameServices.module().getLevelInitProfile().isLevelMusicPublicationPending());
        values.put("poseKind", value(value(((RewindSnapshottable<?>)controller).capture(), "poseClock"), "kind"));
        values.put("stage", value(creatorValue("shotState"), "stage"));
        Object readiness = creatorValue("readinessState");
        values.put("readiness", value(readiness, "phase")); values.put("readinessOwner", value(readiness, "owner"));
        values.put("elevation", value(creatorValue("shotState"), "elevationDegrees"));
        for (int owner = 0; owner < 2; owner++) {
            Object golfer = match == null ? null : ((List<?>)value(match, "golfers")).get(owner);
            values.put("strokes" + owner, golfer == null ? -1 : ((List<?>)value(golfer, "holes")).stream().mapToInt(s -> integer(s, "strokes")).sum());
            values.put("dnf" + owner, golfer != null && (boolean)value(golfer, "dnf"));
        }
        Object online = field(creatorState, "online");
        Object status = online == null ? null : declaredValue(online, "shotStatus");
        values.put("phase", status == null ? "AIM" : value(status, "phase"));
        values.put("holeRewinds", status == null ? -2 : value(status, "holeRemaining"));
        values.put("turnRewinds", status == null ? -2 : value(status, "turnRemaining"));
        values.put("replaySpeed", status == null ? 1 : value(status, "replaySpeed"));
        Object meter=creatorValue("shotState");
        values.put("spin",value(meter,"spin")); values.put("targetSpin",value(meter,"targetSpin"));
        values.put("power",value(meter,"power"));
        if(!host && online!=null) {
            Object basis=declaredValue(online,"guestGuide");
            values.put("guideAvailable",basis!=null);
            if(basis!=null) {
                values.put("guideCentreX",declaredValue(basis,"centreX"));
                values.put("guideCentreY",declaredValue(basis,"centreY"));
                values.put("guideAngle",declaredValue(basis,"angle"));
                values.put("guideCameraX",declaredValue(basis,"cameraX"));
                values.put("guideCameraY",declaredValue(basis,"cameraY"));
            }
        }
        if(host) {
            var p=GameServices.camera().getFocusedSprite();
            values.put("ballRolling",p.getRolling());
            values.put("ballX",p.getCentreX()); values.put("ballY",p.getCentreY()); values.put("ballAngle",p.getAngle()&255);
            values.put("cameraX",GameServices.camera().getX()); values.put("cameraY",GameServices.camera().getY());
        }
        values.put("accepted", online != null && (boolean)declaredValue(online, "guestAccepted"));
        values.put("chargeCues", online==null?0:field(online,"cue"));
        values.put("concededOwner", online == null ? -1 : declaredValue(online, "concededOwner"));
        long revision = -1; String imageHash = "none";
        if (online != null && host) {
            Object frame = field(field(online, "room"), "latestView");
            if (frame != null) {
                revision = ((Number)value(frame, "revision")).longValue();
                try (SceneViewPresenter composed = GameServices.level().createScenePresenter()) {
                    require(composed.accept(SceneFrameCodec.decode((byte[])value(frame, "payload"))), "host cannot compose its transmitted scene");
                    imageHash = imageHash(composed.image(0, 0));
                }
            }
        } else if (online != null && field(online, "presenter") instanceof SceneViewPresenter presenter && presenter.revision() >= 0) {
            revision = presenter.revision(); imageHash = imageHash(presenter.image(0, 0));
        }
        values.put("viewRevision", revision); values.put("viewHash", imageHash);
        StringJoiner line = new StringJoiner("|", "GOLF|", ""); values.forEach((key, item) -> line.add(key + "=" + item));
        System.out.println(line); System.out.flush();
    }

    private boolean closed;
    private void shutdown() throws Exception {
        if (closed) return; closed = true;
        Object ownedRoom = room(); controller.close();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (ownedRoom != null && integer(ownedRoom, "activeWorkers") != 0 && System.nanoTime() < deadline) Thread.sleep(2);
        require(ownedRoom == null || integer(ownedRoom, "activeWorkers") == 0, "room workers survived teardown");
        System.out.println("GOLF|closed=true|workers=0|heldRows=" + heldRows + "|gameplayRows=" + gameplayRows); System.out.flush();
    }
    private String score(Object score) throws Exception { return value(score, "strokes") + "," + value(score, "penalties") + "," + value(score, "finished"); }
    private static String imageHash(SceneImage image) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256"); var word = ByteBuffer.allocate(4);
        for (int pixel : image.argb()) { word.clear(); word.putInt(pixel); digest.update(word.array()); }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private int integer(Object object, String accessor) {
        try { return ((Number)value(object, accessor)).intValue(); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    /** Fixture configuration and creator diagnostics use the same boundary as the published graph. */
    private Object creatorValue(String accessor) throws Exception {
        return value(creatorState, accessor);
    }
    private Object creatorCallback(Object target, Method method, Object... arguments) {
        return boundary.call(OWNER, () -> {
            try { return method.invoke(target, arguments); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Checked creator fixture callback failure", cause);
            } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        });
    }
    private Object value(Object object, String accessor) throws Exception {
        return creatorCallback(object, object.getClass().getMethod(accessor));
    }
    private Object declaredValue(Object object, String accessor) throws Exception {
        var method = object.getClass().getDeclaredMethod(accessor); method.setAccessible(true);
        return creatorCallback(object, method);
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
