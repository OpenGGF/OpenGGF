package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.game.MenuTextEditor;
import com.openggf.stageplay.StageplayController;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.lwjgl.glfw.GLFW.*;

/** Shared authoring commands and forms. Frontends own layout, never a second scene model. */
public final class StageplayWorkspace {
    public interface Host {
        StageplayDocument initialDocument();
        default Path initialAssetRoot() { return Path.of("projects").toAbsolutePath().normalize(); }
        default void finishPendingEdits() { }
        default void refreshResources() { }
        default void validate(StageplayDocument document, Path assetRoot) throws IOException { }
        default void play(StageplayDocument document, Path assetRoot, int frame) throws IOException { }
        default void present(StageplayDocument document, int frame) { }
        default void stop() { }
        default void export(StageplayDocument document, Path source, Path output) throws IOException {
            throw new IOException("Film export is unavailable in this host");
        }
    }

    public enum Action {
        NEW("New scene"), OPEN("Open scene"), SAVE("Save"), SAVE_AS("Save as"),
        SCENE("Scene / set"), ADD_ACTOR("Add actor"), EDIT_ACTOR("Actor properties"),
        DELETE_ACTOR("Remove actor"), SET_KEY("Set actor key"), DELETE_KEY("Remove actor key"),
        CAMERA_MODE("Direct camera (C)"), SET_CAMERA_KEY("Capture camera key (K)"), CAMERA_KEY("Camera keys"),
        RECORD_CAMERA("Record camera take (R)"), CAMERA_EFFECTS("Camera shake / quake"),
        SCENE_CHANGES("Scene changes / transitions"), FILM_FORMAT("Film format presets"), CAPTION("Captions"), AUDIO("Audio cues"),
        RECORD("Record directed take (WASD)"), PLAY("Play / pause"), STOP("Stop"),
        UNDO("Undo"), REDO("Redo"), EXPORT("Export film"), EXIT("Level editor");
        private final String label;
        Action(String label) { this.label = label; }
        public String label() { return label; }
    }

    private final Host host;
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private final StageplayController controller;
    private Path path;
    private Path assetRoot;
    private boolean active;
    private boolean initialized;
    private String status = "Create a scene or open a .stageplay file";
    private StageplayDocument takeBefore;
    private List<ActorKey> takeKeys;
    private String takeActor;
    private List<CameraKey> takeCameraKeys;
    private boolean cameraMode;
    private boolean cameraInputEnabled = true;
    private StageplayTimeline.CameraPose cameraDraft;
    private boolean startedThisTick;
    private boolean previewSoundActive;

    public StageplayWorkspace(Host host) {
        this.host = Objects.requireNonNull(host);
        controller = new StageplayController(StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224));
        assetRoot = host.initialAssetRoot();
    }

    public StageplayController controller() { return controller; }
    public EditorProjectMenu menu() { return menu; }
    public boolean active() { return active; }
    public boolean recording() { return takeBefore != null; }
    public boolean recordingCamera() { return takeCameraKeys != null; }
    public boolean cameraMode() { return cameraMode; }
    /** Frontends suspend held camera controls while a palette or text widget owns input. */
    public void setCameraInputEnabled(boolean enabled) { cameraInputEnabled = enabled; }
    public StageplayTimeline.CameraPose directorCamera() {
        return cameraDraft != null ? cameraDraft : StageplayTimeline.sample(recordingDocument(), controller.frame()).baseCamera();
    }
    public String status() { return status; }
    public Path path() { return path; }
    public Path assetRoot() { return assetRoot; }

    public void activate() {
        try {
            host.finishPendingEdits();
            if (!initialized) newDocument();
            host.refreshResources();
            active = true;
        } catch (RuntimeException failure) {
            menu.showActions("Cannot open Stageplay", List.of(action("Keep editing", () -> { })));
            report(failure);
        }
    }

    /** Workspace changes preserve the scene and unsaved history. */
    public void deactivate() { stop(); menu.close(); cameraDraft = null; cameraMode = false; active = false; }

    public void seek(int frame) { stop(); controller.seek(frame); cameraDraft = null; }
    public void selectActor(String id) { stop(); controller.selectActor(id); }

    public void invoke(Action action) {
        try {
            host.finishPendingEdits();
            if (action != Action.PLAY && action != Action.RECORD && action != Action.RECORD_CAMERA) stop();
            switch (action) {
                case NEW -> beforeReplace(this::newDocument);
                case OPEN -> beforeReplace(this::openForm);
                case SAVE -> save(null);
                case SAVE_AS -> saveAs(null);
                case SCENE -> sceneForm();
                case ADD_ACTOR -> actorForm(null);
                case EDIT_ACTOR -> actorForm(selectedActor());
                case DELETE_ACTOR -> edit(doc -> doc.withActors(doc.actors().stream()
                        .filter(actor -> !actor.id().equals(selectedActor().id())).toList()));
                case SET_KEY -> keyForm();
                case DELETE_KEY -> deleteKey();
                case CAMERA_MODE -> {
                    cameraMode = !cameraMode; cameraDraft = null;
                    status = cameraMode ? "Camera: WASD / arrows pan, Q / E zoom, K capture key, R record, C leave camera." : "Actor direction";
                }
                case SET_CAMERA_KEY -> captureCameraKey();
                case CAMERA_KEY -> cameraMenu();
                case RECORD_CAMERA -> { if (recording()) stop(); else startCameraTake(); }
                case CAMERA_EFFECTS -> cameraEffectsMenu();
                case SCENE_CHANGES -> sceneChangesMenu();
                case FILM_FORMAT -> filmFormatMenu();
                case CAPTION -> captionMenu();
                case AUDIO -> audioMenu();
                case RECORD -> { if (recording()) stop(); else startTake(); }
                case PLAY -> { if (controller.playing() || recording()) stop(); else play(); }
                case STOP -> { controller.seek(0); cameraDraft = null; }
                case UNDO -> { controller.undo(); cameraDraft = null; }
                case REDO -> { controller.redo(); cameraDraft = null; }
                case EXPORT -> exportForm();
                case EXIT -> deactivate();
            }
        } catch (RuntimeException failure) { report(failure); }
    }

    /** Called once per editor presentation tick, after the active frontend's queued commands. */
    public boolean tick(InputHandler input) {
        if (!active && !menu.isOpen()) return false;
        if (menu.update(input)) return true;
        try {
            if (recording()) {
                if (input.isKeyPressed(GLFW_KEY_SPACE) || input.isKeyPressed(GLFW_KEY_ESCAPE)) { stop(); return true; }
                if (recordingCamera()) recordCameraStep(horizontal(input), vertical(input), zoomInput(input));
                else recordStep(input.isKeyDown(GLFW_KEY_D), input.isKeyDown(GLFW_KEY_A),
                            input.isKeyDown(GLFW_KEY_S), input.isKeyDown(GLFW_KEY_W));
            } else if (startedThisTick) startedThisTick = false;
            else if (controller.playing()) {
                if (controller.advance()) host.present(controller.document(), controller.frame());
            } else if (previewSoundActive) stop();
            else if (cameraMode && cameraInputEnabled && !input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && !input.isKeyDown(GLFW_KEY_RIGHT_CONTROL)
                    && !input.isKeyDown(GLFW_KEY_LEFT_SUPER) && !input.isKeyDown(GLFW_KEY_RIGHT_SUPER)) {
                double speed = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT) ? 8 : 2;
                moveCamera(horizontal(input) * speed, vertical(input) * speed, zoomInput(input));
            }
        } catch (RuntimeException failure) {
            try { stop(); }
            catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            report(failure);
        }
        return true;
    }

    public void stop() {
        controller.pause(); startedThisTick = false; previewSoundActive = false; host.stop();
        if (!recording()) return;
        var candidate = recordingDocument();
        controller.edit(ignored -> candidate);
        // A failed stop or commit must leave the last valid take available for retry.
        takeBefore = null; takeKeys = null; takeActor = null; takeCameraKeys = null; cameraDraft = null;
        status = "Recorded visual take. Undo removes the whole take.";
    }

    private void play() {
        cameraDraft = null;
        io(() -> {
            host.validate(controller.document(), assetRoot);
            controller.play();
            try { host.play(controller.document(), assetRoot, controller.frame()); }
            catch (IOException | RuntimeException failure) { controller.pause(); host.stop(); throw failure; }
            startedThisTick = true;
            previewSoundActive = true;
            status = "Playing from frame " + controller.frame() + "; seeking is visual and does not replay audio.";
        });
    }

    private void startTake() {
        stop();
        var actor = selectedActor();
        cameraMode = false; cameraDraft = null;
        takeBefore = controller.document(); takeActor = actor.id(); takeKeys = new ArrayList<>(actor.keys());
        status = "Recording visual movement: WASD. Space stops. This is a baked take, not simulation input.";
    }

    /** Deterministic puppet recording, deliberately separate from engine input replays. */
    void recordStep(boolean right, boolean left, boolean down, boolean up) {
        if (!recording()) return;
        int frame = controller.frame();
        var draft = takeDocument(takeKeys);
        var pose = StageplayTimeline.sample(draft, frame).actors().stream()
                .filter(a -> a.actorId().equals(takeActor)).findFirst().orElseThrow();
        var key = new ActorKey(frame, pose.x() + (right ? 2 : 0) - (left ? 2 : 0),
                pose.y() + (down ? 2 : 0) - (up ? 2 : 0), pose.animation(), pose.pose(),
                left || (!right && pose.flipX()), pose.visible(), pose.scale(), Interpolation.HOLD);
        var keys = new ArrayList<>(takeKeys);
        keys.removeIf(k -> k.frame() == frame); keys.add(key); keys.sort(Comparator.comparingInt(ActorKey::frame));
        // Validate the whole scene budget before publishing a draft that either frontend can draw.
        takeDocument(keys);
        takeKeys = keys;
        if (frame == controller.document().durationFrames() - 1) stop(); else controller.seek(frame + 1);
    }

    /** Current draft is shown without polluting undo with one command per captured frame. */
    public StageplayDocument previewDocument() { return recordingDocument(); }

    /** Unkeyed framing is view state, so a full document key budget cannot break preview. */
    public StageplayTimeline.Frame previewFrame() {
        var frame = StageplayTimeline.sample(previewDocument(),controller.frame());
        if (cameraDraft == null || controller.playing()) return frame;
        var shaken = new StageplayTimeline.CameraPose(cameraDraft.x()+frame.camera().x()-frame.baseCamera().x(),
                cameraDraft.y()+frame.camera().y()-frame.baseCamera().y(),cameraDraft.zoom());
        return new StageplayTimeline.Frame(frame.frame(),frame.actors(),shaken,frame.captions(),frame.set(),frame.transition(),cameraDraft);
    }

    private StageplayDocument recordingDocument() {
        if (!recording()) return controller.document();
        return recordingCamera() ? takeBefore.withCamera(takeCameraKeys) : takeDocument(takeKeys);
    }

    /** Transient framing: only Capture key or Record commits it to the shared document. */
    public void moveCamera(double screenX, double screenY, double zoomFactor) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY) || !Double.isFinite(zoomFactor) || zoomFactor <= 0)
            throw new IllegalArgumentException("Camera movement and zoom must be finite; zoom must be positive");
        if (screenX == 0 && screenY == 0 && zoomFactor == 1) return;
        var camera = directorCamera();
        double zoom = Math.clamp(camera.zoom() * zoomFactor, .05, 64);
        // Keep the film centre steady while zooming; movement is measured in visible pixels.
        double x = camera.x() + screenX / camera.zoom() + controller.document().width() * (.5 / camera.zoom() - .5 / zoom);
        double y = camera.y() + screenY / camera.zoom() + controller.document().height() * (.5 / camera.zoom() - .5 / zoom);
        cameraDraft = new StageplayTimeline.CameraPose(Math.clamp(x, -1_048_576, 1_048_576),
                Math.clamp(y, -1_048_576, 1_048_576), zoom);
        status = "Camera framing preview. K captures a key; R records motion. Seeking discards this unkeyed framing.";
    }

    private void captureCameraKey() {
        var camera = directorCamera();
        var key = new CameraKey(controller.frame(), camera.x(), camera.y(), camera.zoom(), Interpolation.LINEAR);
        edit(d -> d.withCamera(withCameraKey(d.camera(), key)));
        cameraDraft = null;
        status = "Camera key captured at frame " + controller.frame() + ". Set a later key to animate the pan or zoom.";
    }

    private void startCameraTake() {
        stop();
        var camera = directorCamera();
        var before = controller.document();
        var keys = withCameraKey(before.camera(), new CameraKey(controller.frame(),
                camera.x(), camera.y(), camera.zoom(), Interpolation.HOLD));
        before.withCamera(keys);
        cameraMode = true; takeBefore = before; takeCameraKeys = keys;
        cameraDraft = null;
        status = "Recording camera: WASD / arrows pan, Q / E zoom, Space stops. One take is one undo.";
    }

    void recordCameraStep(int horizontal, int vertical, double zoomFactor) {
        if (!recordingCamera()) return;
        int frame = controller.frame();
        var previousDraft = cameraDraft;
        moveCamera(horizontal * 2, vertical * 2, zoomFactor);
        var camera = directorCamera();
        List<CameraKey> keys;
        try {
            keys = withCameraKey(takeCameraKeys,new CameraKey(frame,camera.x(),camera.y(),camera.zoom(),Interpolation.HOLD));
            takeBefore.withCamera(keys); // Validate before publishing a preview or advancing.
        } catch (RuntimeException failure) { cameraDraft = previousDraft; throw failure; }
        takeCameraKeys = keys; cameraDraft = null;
        if (frame == controller.document().durationFrames() - 1) stop(); else controller.seek(frame + 1);
    }

    private static List<CameraKey> withCameraKey(List<CameraKey> keys, CameraKey key) {
        var result = new ArrayList<>(keys); result.removeIf(k -> k.frame() == key.frame()); result.add(key);
        result.sort(Comparator.comparingInt(CameraKey::frame)); return result;
    }

    public static boolean cameraMotionKey(int key) {
        return key == GLFW_KEY_W || key == GLFW_KEY_A || key == GLFW_KEY_S || key == GLFW_KEY_D
                || key == GLFW_KEY_UP || key == GLFW_KEY_DOWN || key == GLFW_KEY_LEFT || key == GLFW_KEY_RIGHT
                || key == GLFW_KEY_Q || key == GLFW_KEY_E || key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT;
    }

    private static int horizontal(InputHandler input) {
        return (input.isKeyDown(GLFW_KEY_D) || input.isKeyDown(GLFW_KEY_RIGHT) ? 1 : 0)
                - (input.isKeyDown(GLFW_KEY_A) || input.isKeyDown(GLFW_KEY_LEFT) ? 1 : 0);
    }
    private static int vertical(InputHandler input) {
        return (input.isKeyDown(GLFW_KEY_S) || input.isKeyDown(GLFW_KEY_DOWN) ? 1 : 0)
                - (input.isKeyDown(GLFW_KEY_W) || input.isKeyDown(GLFW_KEY_UP) ? 1 : 0);
    }
    private static double zoomInput(InputHandler input) {
        int direction = (input.isKeyDown(GLFW_KEY_E) ? 1 : 0) - (input.isKeyDown(GLFW_KEY_Q) ? 1 : 0);
        return Math.pow(1.02, direction);
    }

    private StageplayDocument takeDocument(List<ActorKey> keys) {
        return takeBefore.withActors(takeBefore.actors().stream().map(a -> a.id().equals(takeActor)
                ? new Actor(a.id(), a.name(), a.gameId(), a.character(), keys) : a).toList());
    }

    private void newDocument() {
        controller.replace(host.initialDocument());
        cameraDraft = null; cameraMode = false;
        host.refreshResources();
        assetRoot = host.initialAssetRoot().toAbsolutePath().normalize(); path = null; initialized = true;
        status = "New scene. Add a cast member, then set keys at timeline frames.";
    }

    private void beforeReplace(Runnable next) {
        if (!controller.dirty()) { next.run(); return; }
        menu.showActions("Unsaved Stageplay scene", List.of(
                action("Save and continue", () -> save(next)), action("Discard scene changes", next),
                action("Keep editing", () -> { })));
    }

    /** Called by the host before quitting; cancellation leaves both workspaces alive. */
    public void requestQuit(Runnable next) { stop(); beforeReplace(next); }

    private void openForm() {
        menu.showForm("Open Stageplay", List.of(file("path", "Scene", "projects/")), "Open", values -> io(() -> open(Path.of(values.get("path")))));
    }

    public void open(Path source) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        StageplayDocument candidate = StageplayStore.read(absolute);
        // Parse first. Missing ROMs still permit repairing the document in either editor.
        stop(); controller.replace(candidate); path = absolute; assetRoot = absolute.getParent();
        cameraDraft = null; cameraMode = false;
        host.refreshResources();
        initialized = true; active = true; status = "Opened " + absolute;
    }

    private void save(Runnable next) {
        if (path == null) { saveAs(next); return; }
        io(() -> { saveTo(path); if (next != null) next.run(); });
    }

    private void saveAs(Runnable next) {
        String suggested = path == null ? assetRoot.resolve(slug(controller.document().name()) + ".stageplay").toString() : path.toString();
        menu.showForm("Save Stageplay", List.of(file("path", "Scene file", suggested)), "Save", values -> {
            Path target = Path.of(values.get("path")).toAbsolutePath().normalize();
            if (!target.getFileName().toString().endsWith(".stageplay")) target = target.resolveSibling(target.getFileName() + ".stageplay");
            Path destination = target;
            Runnable write = () -> io(() -> { saveTo(destination); if (next != null) next.run(); });
            if (Files.exists(target) && !target.equals(path)) menu.showActions("Replace existing scene?", List.of(
                    action("Replace " + target.getFileName(), write), action("Cancel", () -> { })));
            else write.run();
        });
    }

    public void saveTo(Path destination) throws IOException {
        stop();
        Path target = destination.toAbsolutePath().normalize();
        if (!assetRoot.equals(target.getParent()) && controller.anyRevision(StageplayWorkspace::hasRelativeAssets))
            throw new IOException("This scene's undo history uses relative assets. Save beside them, or reopen the scene to start a new editing history before relocating it.");
        StageplayDocument candidate = rebase(controller.document(), assetRoot, target.getParent());
        Files.createDirectories(target.getParent());
        StageplayStore.write(target, candidate);
        // Rebasing is a document edit, preserving existing undo history and meaningful dirty state.
        controller.edit(ignored -> candidate); controller.markSaved();
        path = target; assetRoot = target.getParent(); status = "Saved " + target;
    }

    private static StageplayDocument rebase(StageplayDocument doc, Path oldRoot, Path newRoot) throws IOException {
        if (!oldRoot.equals(newRoot) && hasRelativeAssets(doc))
            throw new IOException("Save beside the existing scene assets. To move the project, move its whole folder so relative paths and undo history stay valid.");
        var set = doc.set();
        String project = relative(oldRoot, newRoot, set.levelProject());
        var audio = new ArrayList<AudioCue>();
        for (var cue : doc.audio()) audio.add(new AudioCue(cue.id(), cue.frame(), cue.kind(), cue.gameId(),
                cue.soundId(), relative(oldRoot, newRoot, cue.asset()), cue.gain()));
        return doc.withSet(new SetSource(set.gameId(), set.zone(), set.act(), set.romSha1(), project)).withAudio(audio);
    }

    private static boolean hasRelativeAssets(StageplayDocument doc) {
        return !doc.set().levelProject().isEmpty() || doc.sceneChanges().stream().anyMatch(c -> !c.set().levelProject().isEmpty())
                || doc.audio().stream().anyMatch(c -> !c.asset().isEmpty());
    }

    private static String relative(Path oldRoot, Path newRoot, String reference) throws IOException {
        if (reference.isEmpty()) return "";
        Path absolute = oldRoot.resolve(reference).normalize();
        if (!absolute.startsWith(newRoot)) throw new IOException("Keep the scene beside its assets, or copy the assets under the new scene folder first: " + reference);
        return newRoot.relativize(absolute).toString().replace('\\', '/');
    }

    private void sceneForm() {
        var doc = controller.document(); var set = doc.set();
        menu.showForm("Stageplay scene / set", List.of(text("name", "Name", doc.name()), number("duration", "Length (60 Hz frames)", doc.durationFrames()),
                number("width", "Width", doc.width()), number("height", "Height", doc.height()), text("game", "Set game: s1 / s2 / s3k", set.gameId()),
                number("zone", "Zone (zero based)", set.zone()), number("act", "Act (zero based)", set.act()),
                text("rom", "Optional ROM SHA-1", set.romSha1()), text("project", "Relative .ogfproject (optional)", set.levelProject())),
                "Apply scene", v -> edit(d -> d.withName(v.get("name")).withDurationFrames(integer(v,"duration"))
                        .withSize(integer(v,"width"), integer(v,"height"))
                        .withSet(new SetSource(v.get("game"),integer(v,"zone"),integer(v,"act"),v.get("rom"),v.get("project")))));
        menu.setSubtitle("Shortening requires deleting out-of-range keys and cues first. Linked sets must be saved under the scene folder.");
    }

    private void actorForm(Actor actor) {
        boolean add = actor == null;
        menu.showForm(add ? "Add actor" : "Actor properties", List.of(text("name", "Name", add ? "Sonic" : actor.name()),
                text("game", "ROM: s1 / s2 / s3k", add ? controller.document().set().gameId() : actor.gameId()),
                text("character", "Character", add ? "sonic" : actor.character())), "Apply actor", v -> {
            String id = add ? nextId("actor", controller.document()) : actor.id();
            var camera = StageplayTimeline.sample(controller.document(), 0).baseCamera();
            var initialKeys = new ArrayList<ActorKey>();
            initialKeys.add(new ActorKey(0,camera.x()+controller.document().width()/2.0,camera.y()+controller.document().height()/2.0,0,-1,false,true,1,Interpolation.HOLD));
            for (var change : controller.document().sceneChanges()) {
                var shotCamera = StageplayTimeline.sample(controller.document(), change.frame()).baseCamera();
                initialKeys.add(new ActorKey(change.frame(),shotCamera.x()+controller.document().width()/2.0,
                        shotCamera.y()+controller.document().height()/2.0,0,-1,false,true,1,Interpolation.HOLD));
            }
            Actor value = new Actor(id,v.get("name"),v.get("game"),v.get("character"),add
                    ? initialKeys : actor.keys());
            edit(d -> { var actors = new ArrayList<>(d.actors()); if (!add) actors.removeIf(a -> a.id().equals(id)); actors.add(value); return d.withActors(actors); });
            controller.selectActor(id);
        });
    }

    private Actor selectedActor() {
        return controller.document().actors().stream().filter(a -> a.id().equals(controller.selectedActorId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Select a cast member first"));
    }

    private void keyForm() {
        Actor actor = selectedActor(); int frame = controller.frame();
        var pose = StageplayTimeline.sample(controller.document(), frame).actors().stream().filter(a -> a.actorId().equals(actor.id())).findFirst().orElseThrow();
        var interpolation = actor.keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow().interpolation();
        menu.showForm("Actor key at frame " + frame, List.of(number("x","World centre X",pose.x()),number("y","World centre Y",pose.y()),
                number("animation","Animation",pose.animation()),number("pose","Pose frame (-1 = animation)",pose.pose()),
                text("flip","Flip X: true / false",Boolean.toString(pose.flipX())), text("visible","Visible: true / false",Boolean.toString(pose.visible())),
                number("scale","Scale",pose.scale()),text("interpolation","HOLD / LINEAR / SMOOTH",interpolation.name())), "Set key", v -> {
            var key = new ActorKey(frame,decimal(v,"x"),decimal(v,"y"),integer(v,"animation"),integer(v,"pose"),bool(v,"flip"),bool(v,"visible"),decimal(v,"scale"),Interpolation.valueOf(v.get("interpolation").toUpperCase(java.util.Locale.ROOT)));
            edit(d -> replaceActor(d,actor,withKey(actor.keys(),key)));
        });
    }

    private void deleteKey() {
        Actor actor = selectedActor(); int frame = controller.frame();
        if (frame == 0) throw new IllegalStateException("Every actor needs its frame-zero key");
        if (isSceneBoundary(frame)) throw new IllegalStateException("A scene change needs this actor key. Remove the scene change first.");
        edit(d -> replaceActor(d,actor,actor.keys().stream().filter(k -> k.frame()!=frame).toList()));
    }

    private static StageplayDocument replaceActor(StageplayDocument doc, Actor actor, List<ActorKey> keys) {
        return doc.withActors(doc.actors().stream().map(a -> a.id().equals(actor.id())
                ? new Actor(a.id(),a.name(),a.gameId(),a.character(),keys) : a).toList());
    }

    private static List<ActorKey> withKey(List<ActorKey> keys, ActorKey key) {
        var result = new ArrayList<>(keys); result.removeIf(k -> k.frame()==key.frame()); result.add(key);
        result.sort(Comparator.comparingInt(ActorKey::frame)); return result;
    }

    private void cameraMenu() {
        menu.showActions("Camera at frame " + controller.frame(), List.of(action("Set camera key", this::cameraForm),
                action("Remove camera key", () -> {
                    if (controller.frame()==0) throw new IllegalStateException("The frame-zero camera key is required");
                    if (isSceneBoundary(controller.frame())) throw new IllegalStateException("A scene change needs this camera key. Remove the scene change first.");
                    edit(d -> d.withCamera(d.camera().stream().filter(k -> k.frame()!=controller.frame()).toList()));
                })));
    }

    private void cameraForm() {
        var camera = directorCamera(); int frame = controller.frame();
        var interpolation = controller.document().camera().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow().interpolation();
        menu.showForm("Camera key at frame " + frame,List.of(number("x","World left X",camera.x()),number("y","World top Y",camera.y()),
                number("zoom","Zoom",camera.zoom()),text("interpolation","HOLD / LINEAR / SMOOTH",interpolation.name())),"Set camera",v -> edit(d -> {
            var keys = new ArrayList<>(d.camera()); keys.removeIf(k -> k.frame()==frame);
            keys.add(new CameraKey(frame,decimal(v,"x"),decimal(v,"y"),decimal(v,"zoom"),Interpolation.valueOf(v.get("interpolation").toUpperCase(java.util.Locale.ROOT))));
            keys.sort(Comparator.comparingInt(CameraKey::frame)); return d.withCamera(keys);
        }));
    }

    private boolean isSceneBoundary(int frame) {
        return controller.document().sceneChanges().stream().anyMatch(c -> c.frame() == frame);
    }

    private void filmFormatMenu() {
        menu.showActions("Film dimensions", List.of(
                action("Widescreen 16:9 (384 x 216)", () -> edit(d -> d.withSize(384,216))),
                action("Native (320 x 224)", () -> edit(d -> d.withSize(320,224))),
                action("HD 16:9 (1280 x 720)", () -> edit(d -> d.withSize(1280,720))),
                action("Custom dimensions", this::sceneForm)));
        menu.setSubtitle("Film pixels are independent of window size. The preview is letterboxed; existing keys and captions keep their coordinates.");
    }

    private void cameraEffectsMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add shake / quake", () -> cameraEffectForm(null)));
        for (var effect : controller.document().cameraEffects()) actions.add(action(effect.startFrame() + ": " + effect.id(),
                () -> menu.showActions("Camera effect", List.of(action("Edit", () -> cameraEffectForm(effect)),
                        action("Delete", () -> edit(d -> d.withCameraEffects(d.cameraEffects().stream()
                                .filter(e -> !e.id().equals(effect.id())).toList())))))));
        menu.showActions("Camera shake / quake", actions);
    }

    private void cameraEffectForm(CameraEffect effect) {
        int frame = controller.frame();
        menu.showForm("Camera shake / quake", List.of(
                number("start", "Start frame", effect == null ? frame : effect.startFrame()),
                number("end", "End frame (exclusive)", effect == null ? Math.min(frame+60, controller.document().durationFrames()) : effect.endFrame()),
                number("x", "Horizontal amplitude (pixels)", effect == null ? 3 : effect.amplitudeX()),
                number("y", "Vertical amplitude (pixels)", effect == null ? 5 : effect.amplitudeY()),
                number("frequency", "Frequency (Hz, >0..30)", effect == null ? 8 : effect.frequency()),
                number("seed", "Deterministic seed", effect == null ? 1 : effect.seed())), "Apply effect", v -> edit(d -> {
            String id = effect == null ? nextId("shake", d) : effect.id();
            var values = new ArrayList<>(d.cameraEffects()); values.removeIf(e -> e.id().equals(id));
            values.add(new CameraEffect(id, integer(v,"start"), integer(v,"end"), decimal(v,"x"), decimal(v,"y"),
                    decimal(v,"frequency"), integer(v,"seed")));
            values.sort(Comparator.comparingInt(CameraEffect::startFrame));
            return d.withCameraEffects(values);
        }));
        menu.setSubtitle("Shake is layered over camera pan and zoom, never baked into camera keys. Preview and export use the same seeded motion.");
    }

    private void sceneChangesMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add scene change", () -> sceneChangeForm(null)));
        for (var change : controller.document().sceneChanges()) actions.add(action(change.frame() + ": " + change.transition() + " / " + change.set().gameId(),
                () -> menu.showActions("Scene change", List.of(action("Edit", () -> sceneChangeForm(change)),
                        action("Delete", () -> edit(d -> d.withSceneChanges(d.sceneChanges().stream()
                                .filter(c -> !c.id().equals(change.id())).toList())))))));
        menu.showActions("Scene changes / transitions", actions);
        menu.setSubtitle("Each change starts a new shot. Keys at its boundary belong to the incoming shot; removing a change preserves its keys.");
    }

    private void sceneChangeForm(SceneChange change) {
        var set = change == null ? StageplayTimeline.sample(controller.document(), controller.frame()).set() : change.set();
        menu.showForm("Scene change", List.of(
                number("frame", "Switch frame (at least 1)", change == null ? Math.max(1,controller.frame()) : change.frame()),
                text("transition", "CUT / FADE_BLACK / FADE_WHITE / WIPE_LEFT / WIPE_RIGHT", change == null ? "CUT" : change.transition().name()),
                number("duration", "Transition frames (CUT = 0)", change == null ? 0 : change.durationFrames()),
                text("game", "Incoming set: s1 / s2 / s3k", set.gameId()), number("zone", "Zone (zero based)", set.zone()),
                number("act", "Act (zero based)", set.act()), text("rom", "Optional ROM SHA-1", set.romSha1()),
                text("project", "Relative .ogfproject (optional)", set.levelProject())), "Apply scene change", v -> edit(d -> {
            String id = change == null ? nextId("scene", d) : change.id();
            var values = new ArrayList<>(d.sceneChanges()); values.removeIf(c -> c.id().equals(id));
            values.add(new SceneChange(id, integer(v,"frame"), new SetSource(v.get("game"), integer(v,"zone"),
                    integer(v,"act"), v.get("rom"), v.get("project")),
                    Transition.valueOf(v.get("transition").toUpperCase(java.util.Locale.ROOT)), integer(v,"duration")));
            values.sort(Comparator.comparingInt(SceneChange::frame));
            return withSceneBoundaries(d, values);
        }));
        menu.setSubtitle("Transitions are centred on the switch frame and cannot overlap. Incoming actor/camera keys are added in the same undo step; position them for the new set.");
    }

    /** Build all required boundary keys and the switch together; no invalid intermediate scene. */
    private static StageplayDocument withSceneBoundaries(StageplayDocument doc, List<SceneChange> changes) {
        var camera = new ArrayList<>(doc.camera());
        var actors = new ArrayList<>(doc.actors());
        for (var change : changes) {
            var sample = StageplayTimeline.sample(doc, change.frame());
            if (camera.stream().noneMatch(k -> k.frame() == change.frame())) {
                var pose = sample.baseCamera();
                camera.add(new CameraKey(change.frame(), pose.x(), pose.y(), pose.zoom(), Interpolation.HOLD));
            }
            for (int i = 0; i < actors.size(); i++) {
                var actor = actors.get(i);
                if (actor.keys().stream().anyMatch(k -> k.frame() == change.frame())) continue;
                var pose = sample.actors().stream().filter(p -> p.actorId().equals(actor.id())).findFirst().orElseThrow();
                var key = new ActorKey(change.frame(),pose.x(),pose.y(),pose.animation(),pose.pose(),pose.flipX(),pose.visible(),pose.scale(),Interpolation.HOLD);
                actors.set(i,new Actor(actor.id(),actor.name(),actor.gameId(),actor.character(),withKey(actor.keys(),key)));
            }
        }
        camera.sort(Comparator.comparingInt(CameraKey::frame));
        return new StageplayDocument(doc.version(),doc.name(),doc.durationFrames(),doc.width(),doc.height(),doc.set(),
                actors,camera,doc.captions(),doc.audio(),doc.cameraEffects(),changes);
    }

    private void captionMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>(); actions.add(action("Add caption", () -> captionForm(null)));
        for (var caption : controller.document().captions()) actions.add(action(caption.startFrame()+": "+caption.text(), () -> menu.showActions("Caption",List.of(
                action("Edit",()->captionForm(caption)),action("Delete",()->edit(d->d.withCaptions(d.captions().stream().filter(c->!c.id().equals(caption.id())).toList())))))));
        menu.showActions("Captions",actions);
    }

    private void captionForm(Caption caption) {
        int frame = controller.frame();
        menu.showForm("Caption",List.of(new EditorProjectMenu.Field("text","Text (\\n = line break)",caption==null?"Dialogue":escapeCaption(caption.text()),MenuTextEditor.Mode.TEXT,8192),number("start","Start frame",caption==null?frame:caption.startFrame()),
                number("end","End frame (exclusive)",caption==null?Math.min(frame+120,controller.document().durationFrames()):caption.endFrame()),
                number("x","Screen X",caption==null?16:caption.x()),number("y","Screen Y",caption==null?controller.document().height()-28:caption.y()),
                text("color","ARGB hex",caption==null?"FFFFFFFF":String.format("%08X",caption.argb()))),"Apply caption",v -> edit(d -> {
            String id=caption==null?nextId("caption",d):caption.id();
            var values=new ArrayList<>(d.captions()); values.removeIf(c->c.id().equals(id));
            values.add(new Caption(id,integer(v,"start"),integer(v,"end"),unescapeCaption(v.get("text")),integer(v,"x"),integer(v,"y"),(int)Long.parseLong(v.get("color"),16)));
            return d.withCaptions(values);
        }));
    }

    private void audioMenu() {
        var actions=new ArrayList<EditorProjectMenu.Action>(); actions.add(action("Add audio cue",()->audioForm(null)));
        for(var cue:controller.document().audio()) actions.add(action(cue.frame()+": "+cue.kind()+" "+cue.id(),()->menu.showActions("Audio cue",List.of(
                action("Edit",()->audioForm(cue)),action("Delete",()->edit(d->d.withAudio(d.audio().stream().filter(c->!c.id().equals(cue.id())).toList())))))));
        menu.showActions("Audio cues",actions);
    }

    private void audioForm(AudioCue cue) {
        menu.showForm("Audio cue",List.of(number("frame","Frame",cue==null?controller.frame():cue.frame()),
                text("kind","ROM_MUSIC / ROM_SFX / VOICE / MUSIC_STOP",cue==null?"ROM_SFX":cue.kind().name()),
                text("game","ROM: s1 / s2 / s3k",cue==null?controller.document().set().gameId():cue.gameId()),
                number("sound","ROM sound ID (decimal)",cue==null?0:cue.soundId()),
                text("asset","Relative WAV / Ogg file",cue==null?"":cue.asset()),number("gain","Gain (0..1)",cue==null?1:cue.gain())),"Apply audio",v -> edit(d -> {
            String id=cue==null?nextId("audio",d):cue.id();
            var values=new ArrayList<>(d.audio()); values.removeIf(c->c.id().equals(id));
            values.add(new AudioCue(id,integer(v,"frame"),AudioKind.valueOf(v.get("kind").toUpperCase(java.util.Locale.ROOT)),v.get("game"),integer(v,"sound"),v.get("asset"),decimal(v,"gain")));
            return d.withAudio(values);
        }));
        menu.setSubtitle("Voice paths are relative to the saved scene. Sound seeking is not simulated; play starts cues at the playhead.");
    }

    private void exportForm() {
        save(() -> menu.showForm("Export Stageplay film",List.of(file("out","New MKV file",assetRoot.resolve(slug(controller.document().name())+".mkv").toString())),"Export",v->io(()->{
            host.validate(controller.document(),assetRoot);
            host.export(controller.document(),path,Path.of(v.get("out")).toAbsolutePath().normalize());
            status="Film export started in a separate process; see the export status.";
        })));
    }

    private void edit(UnaryOperator<StageplayDocument> operation) { controller.edit(operation); cameraDraft = null; status="Scene edited"; }
    public void report(Throwable failure) { status=failure.getMessage()==null?failure.toString():failure.getMessage(); menu.report(failure); }
    public void setStatus(String value) { status=Objects.requireNonNull(value); }
    private static String nextId(String prefix,StageplayDocument doc) {
        var used = new java.util.HashSet<String>();
        doc.actors().forEach(a -> used.add(a.id())); doc.captions().forEach(c -> used.add(c.id())); doc.audio().forEach(c -> used.add(c.id()));
        doc.cameraEffects().forEach(e -> used.add(e.id())); doc.sceneChanges().forEach(c -> used.add(c.id()));
        int i=1; while(used.contains(prefix+i)) i++; return prefix+i;
    }
    private static String escapeCaption(String text) { return text.replace("\\","\\\\").replace("\n","\\n"); }
    private static String unescapeCaption(String text) {
        var value=new StringBuilder();
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='\\' && i+1<text.length()) { char next=text.charAt(++i); if(next=='n') value.append('\n'); else if(next=='\\') value.append('\\'); else throw new IllegalArgumentException("Use \\n for a new line and \\\\ for a backslash"); }
            else value.append(c);
        }
        return value.toString();
    }
    private static String slug(String text) { String value=text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+","-"); return value.isBlank()?"scene":value; }
    private static EditorProjectMenu.Action action(String label,Runnable callback) { return new EditorProjectMenu.Action(label,callback); }
    private static EditorProjectMenu.Field text(String key,String label,String value) { return new EditorProjectMenu.Field(key,label,value,MenuTextEditor.Mode.TEXT); }
    private static EditorProjectMenu.Field file(String key,String label,String value) { return new EditorProjectMenu.Field(key,label,value,MenuTextEditor.Mode.PATH); }
    private static EditorProjectMenu.Field number(String key,String label,Number value) { return text(key,label,value.toString()); }
    private static int integer(Map<String,String> v,String key) { return Integer.parseInt(v.get(key).trim()); }
    private static double decimal(Map<String,String> v,String key) { return Double.parseDouble(v.get(key).trim()); }
    private static boolean bool(Map<String,String> v,String key) {
        if (v.get(key).equalsIgnoreCase("true")) return true;
        if (v.get(key).equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key+" must be true or false");
    }
    @FunctionalInterface private interface IoTask { void run() throws IOException; }
    private static void io(IoTask task) { try { task.run(); } catch(IOException failure) { throw new UncheckedIOException(failure); } }
}
