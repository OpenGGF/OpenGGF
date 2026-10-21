package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.game.MenuTextEditor;
import com.openggf.stageplay.StageplayController;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayDialogue;
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
        default StageplayTimeline.Frame resolvePreview(StageplayDocument document, Path assetRoot, int frame) throws IOException {
            return StageplayTimeline.sample(document, frame);
        }
        default void stop() { }
        default void export(StageplayDocument document, Path source, Path output) throws IOException {
            throw new IOException("Film export is unavailable in this host");
        }
    }

    public enum Action {
        NEW("New scene"), OPEN("Open scene"), SAVE("Save"), SAVE_AS("Save as"),
        SCENE("Scene / set"), ADD_ACTOR("Add actor"), EDIT_ACTOR("Actor properties"),
        DELETE_ACTOR("Remove actor"), SET_KEY("Direct actor / set key"), DELETE_KEY("Remove actor key / action"),
        PUPPET_ACTIONS("Puppet actions"),
        CAMERA_MODE("Direct camera (C)"), SET_CAMERA_KEY("Capture camera key (K)"), CAMERA_KEY("Camera keys"),
        RECORD_CAMERA("Record camera take (R)"), CAMERA_EFFECTS("Camera shake / quake"),
        SCENE_CHANGES("Scene changes / transitions"), FILM_FORMAT("Film format presets"), CAPTION("Captions"), DIALOGUE("Dialogue lines"),
        DIALOGUE_TYPERS("Dialogue typers"), DIALOGUE_SKINS("Dialogue decorations"), AUDIO("Audio cues"),
        RECORD("Record actor take (R)"), PLAY("Play / pause"), STOP("Stop"),
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
    private List<PuppetCommand> takeCommands;
    private StageplayDocument takeDraft;
    private int takeDirection;
    private int takeStartFrame;
    private boolean takeJumpHeld;
    private StageplayDocument previewSource;
    private Path previewRoot;
    private int previewAt = -1;
    private StageplayTimeline.Frame resolvedPreview;
    private String previewError;
    private boolean cameraMode;
    private boolean cameraInputEnabled = true;
    private StageplayTimeline.CameraPose cameraDraft;
    private boolean startedThisTick;
    private boolean previewSoundActive;
    private int zoomHeldDirection;
    private int zoomHeldTicks;

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
    public boolean recordingPuppet() { return takeCommands != null; }
    public boolean selectedActorIsPuppet() { return puppet(controller.document(), controller.selectedActorId()) != null; }
    public boolean cameraMode() { return cameraMode; }
    /** Frontends suspend held camera controls while a palette or text widget owns input. */
    public void setCameraInputEnabled(boolean enabled) { cameraInputEnabled = enabled; if (!enabled) resetZoomRepeat(); }
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
            invalidatePreview();
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
                case DELETE_ACTOR -> deleteActor();
                case SET_KEY -> { if (selectedActorIsPuppet()) puppetMenu(); else keyForm(); }
                case DELETE_KEY -> deleteKey();
                case PUPPET_ACTIONS -> puppetMenu();
                case CAMERA_MODE -> {
                    cameraMode = !cameraMode; cameraDraft = null;
                    status = cameraMode ? "Camera: WASD / arrows pan, Q / E step integer zoom, K capture key, R record, C leave camera." : "Actor direction";
                }
                case SET_CAMERA_KEY -> captureCameraKey();
                case CAMERA_KEY -> cameraMenu();
                case RECORD_CAMERA -> { if (recording()) stop(); else startCameraTake(); }
                case CAMERA_EFFECTS -> cameraEffectsMenu();
                case SCENE_CHANGES -> sceneChangesMenu();
                case FILM_FORMAT -> filmFormatMenu();
                case CAPTION -> captionMenu();
                case DIALOGUE -> dialogueMenu();
                case DIALOGUE_TYPERS -> typerMenu();
                case DIALOGUE_SKINS -> skinMenu();
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
        if (menu.update(input)) { resetZoomRepeat(); return true; }
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
        resetZoomRepeat();
        controller.pause(); startedThisTick = false; previewSoundActive = false; host.stop();
        if (!recording()) return;
        var candidate = recordingDocument();
        controller.edit(ignored -> candidate);
        // A failed stop or commit must leave the last valid take available for retry.
        boolean physical = recordingPuppet();
        takeBefore = null; takeKeys = null; takeActor = null; takeCameraKeys = null; takeCommands = null; takeDraft = null; cameraDraft = null;
        status = physical ? "Recorded Puppet instructions. Undo removes the whole take."
                : "Recorded Poser visual take. Undo removes the whole take.";
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
        var track = puppet(controller.document(), actor.id());
        if (track != null) requireResolvedActor(controller.document(), actor.id());
        takeBefore = controller.document(); takeActor = actor.id(); takeDraft = takeBefore; takeStartFrame=controller.frame();
        if (track == null) takeKeys = new ArrayList<>(actor.keys());
        else { takeCommands = new ArrayList<>(track.commands()); takeDirection = 0; takeJumpHeld = false; }
        status = track == null ? "Recording Poser movement: WASD. Space stops. Undo removes the whole visual take."
                : "Recording Puppet actions: A/D move, release/S stop, W jump. Space ends the take. Goals use native acceleration.";
    }

    /** Deterministic puppet recording, deliberately separate from engine input replays. */
    void recordStep(boolean right, boolean left, boolean down, boolean up) {
        if (!recording()) return;
        if (recordingPuppet()) { recordPuppetStep(right, left, down, up); return; }
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
        var candidate = takeDocument(keys);
        takeKeys = keys;
        takeDraft = candidate;
        if (frame == controller.document().durationFrames() - 1) stop(); else controller.seek(frame + 1);
    }

    /** Current draft is shown without polluting undo with one command per captured frame. */
    public StageplayDocument previewDocument() { return recordingDocument(); }

    /** Unkeyed framing is view state, so a full document key budget cannot break preview. */
    public StageplayTimeline.Frame previewFrame() {
        var frame = resolvePreview(previewDocument(), controller.frame());
        if (cameraDraft == null || controller.playing()) return frame;
        var shaken = new StageplayTimeline.CameraPose(cameraDraft.x()+frame.camera().x()-frame.baseCamera().x(),
                cameraDraft.y()+frame.camera().y()-frame.baseCamera().y(),cameraDraft.zoom());
        return new StageplayTimeline.Frame(frame.frame(),frame.actors(),shaken,frame.captions(),frame.set(),frame.transition(),cameraDraft,frame.dialogue(),frame.puppets());
    }

    private void invalidatePreview() { previewSource=null; resolvedPreview=null; previewError=null; previewAt=-1; }

    private StageplayTimeline.Frame resolvePreview(StageplayDocument document,int frame) {
        if (resolvedPreview != null && frame==previewAt && assetRoot.equals(previewRoot) && document.equals(previewSource)) return resolvedPreview;
        previewSource=document; previewRoot=assetRoot; previewAt=frame; previewError=null;
        try { resolvedPreview=Objects.requireNonNull(host.resolvePreview(document,assetRoot,frame),"preview frame"); }
        catch (IOException | RuntimeException failure) {
            controller.pause(); startedThisTick=false; previewSoundActive=false;
            try { host.stop(); } catch (RuntimeException cleanup) { if (cleanup!=failure) failure.addSuppressed(cleanup); }
            previewError="Puppet preview unavailable: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage());
            status=previewError;
            // Keep authoring available without claiming the unresolved placement is a simulated result.
            resolvedPreview=StageplayTimeline.sample(document,frame);
        }
        return resolvedPreview;
    }

    public StageplayTimeline.ActorPose selectedActorPose() {
        String id=controller.selectedActorId();
        return previewFrame().actors().stream().filter(a -> a.actorId().equals(id)).findFirst().orElse(null);
    }

    /** Shared readout for both frontends; goals are never reported as actual positions. */
    public String selectedActorStatus() {
        if (controller.selectedActorId().isEmpty()) return "Select a cast member";
        var frame=previewFrame();
        if (previewError!=null) return previewError;
        var actor=frame.actors().stream().filter(a -> a.actorId().equals(controller.selectedActorId())).findFirst().orElse(null);
        if (actor==null) return "Select a cast member";
        var sample=frame.puppets().stream().filter(a -> a.actorId().equals(actor.actorId())).findFirst().orElse(null);
        if (sample==null) return "Poser x"+coordinate(actor.x())+" y"+coordinate(actor.y());
        if (sample.status().name().equals("UNRESOLVED")) return "Puppet preview unavailable";
        if (sample.status().name().equals("INACTIVE")) return "INACTIVE / hidden for this shot";
        String value=sample.status()+" x"+coordinate(actor.x())+" y"+coordinate(actor.y())+(sample.airborne()?" / airborne":"");
        var track=puppet(previewDocument(),actor.actorId());
        if (track!=null) for (var command:track.commands()) if (command.id().equals(sample.commandId()) && command.kind()==PuppetCommandKind.MOVE_TO)
            value+=" / goal "+coordinate(command.x())+","+coordinate(command.y());
        return value;
    }

    private StageplayTimeline.ActorPose requireResolvedActor(StageplayDocument document,String id) {
        var frame=resolvePreview(document,controller.frame());
        if (previewError!=null) throw new IllegalStateException(previewError);
        var sample=frame.puppets().stream().filter(p -> p.actorId().equals(id)).findFirst().orElse(null);
        if (sample==null || sample.status().name().equals("UNRESOLVED"))
            throw new IllegalStateException("Puppet preview must be available before recording this actor");
        if (sample.status().name().equals("INACTIVE"))
            throw new IllegalStateException("Make this actor visible during the shot before recording physics");
        return frame.actors().stream().filter(a -> a.actorId().equals(id)).findFirst().orElseThrow();
    }

    private void recordPuppetStep(boolean right,boolean left,boolean stop,boolean jump) {
        int frame=controller.frame();
        var pose=requireResolvedActor(takeDraft,takeActor);
        int direction=stop?0:(right?1:0)-(left?1:0);
        var commands=new ArrayList<>(takeCommands);
        boolean replaced=commands.removeIf(c -> c.frame()==frame);
        var oldMove=takeCommands.stream().filter(c -> c.frame()<=frame && c.kind()!=PuppetCommandKind.JUMP).reduce((a,b)->b).orElse(null);
        boolean nearingGoal=direction!=0 && oldMove!=null && oldMove.kind()==PuppetCommandKind.MOVE_TO
                && Math.abs(oldMove.x()-pose.x())<128;
        boolean newShot=takeBefore.sceneChanges().stream().anyMatch(c -> c.frame()==frame);
        if (replaced || frame==takeStartFrame || newShot || direction!=takeDirection || nearingGoal) {
            PuppetCommandKind kind=direction==0?PuppetCommandKind.STOP:PuppetCommandKind.MOVE_TO;
            commands.add(new PuppetCommand(nextCommandId(commands),frame,kind,
                    direction==0?0:Math.clamp(pose.x()+direction*8192,-1_048_576,1_048_576),
                    direction==0?0:pose.y(),direction==0?0:1,0));
        }
        if (jump && !takeJumpHeld) {
            int hold=Math.min(12,shotEnd(takeBefore,frame)-frame);
            int nextJump=commands.stream().filter(c -> c.kind()==PuppetCommandKind.JUMP && c.frame()>frame)
                    .mapToInt(PuppetCommand::frame).min().orElse(takeBefore.durationFrames()+1);
            hold=Math.min(hold,nextJump-frame-1);
            if (hold<1) throw new IllegalArgumentException("Leave one released frame between jump presses");
            // Native jump needs a released tick before another press, including imported earlier holds.
            for (int i=0;i<commands.size();i++) {
                var c=commands.get(i);
                if (c.kind()!=PuppetCommandKind.JUMP || c.frame()>=frame || c.frame()+c.holdFrames()<frame) continue;
                int previousHold=frame-c.frame()-1;
                if (previousHold<1) throw new IllegalArgumentException("Leave one released frame between jump presses");
                commands.set(i,new PuppetCommand(c.id(),c.frame(),c.kind(),c.x(),c.y(),c.urgency(),previousHold));
            }
            commands.add(new PuppetCommand(nextCommandId(commands),frame,PuppetCommandKind.JUMP,0,0,0,hold));
        }
        commands.sort(Comparator.comparingInt(PuppetCommand::frame));
        var track=puppet(takeBefore,takeActor);
        var candidate=replacePuppet(takeBefore,new PuppetTrack(takeActor,track.snapToFloor(),track.collisionPath(),commands));
        // Resolve before publishing: a failed step retains the last valid take and playhead.
        requireResolvedActor(candidate,takeActor);
        takeCommands=commands; takeDraft=candidate; takeDirection=direction; takeJumpHeld=jump;
        if (frame==takeBefore.durationFrames()-1) stop(); else controller.seek(frame+1);
    }

    private String nextCommandId(List<PuppetCommand> commands) {
        var used=allIds(takeBefore); commands.forEach(c -> used.add(c.id()));
        int i=1; while(used.contains("action"+i)) i++; return "action"+i;
    }

    private static int shotEnd(StageplayDocument document,int frame) {
        return document.sceneChanges().stream().filter(c -> c.frame()>frame).mapToInt(SceneChange::frame).min().orElse(document.durationFrames());
    }
    private static String coordinate(double value) { return String.format(java.util.Locale.ROOT,"%.1f",value); }
    private static PuppetTrack puppet(StageplayDocument document,String id) {
        return document.puppets().stream().filter(p -> p.actorId().equals(id)).findFirst().orElse(null);
    }
    private static StageplayDocument replacePuppet(StageplayDocument document,PuppetTrack track) {
        var tracks=new ArrayList<>(document.puppets()); tracks.removeIf(p -> p.actorId().equals(track.actorId())); tracks.add(track);
        return document.withPuppets(tracks);
    }

    private StageplayDocument recordingDocument() {
        if (!recording()) return controller.document();
        return takeDraft;
    }

    /** Transient framing: only Capture key or Record commits it to the shared document. */
    public void moveCamera(double screenX, double screenY, double zoomFactor) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY) || !Double.isFinite(zoomFactor) || zoomFactor <= 0)
            throw new IllegalArgumentException("Camera movement and zoom must be finite; zoom must be positive");
        if (screenX == 0 && screenY == 0 && zoomFactor == 1) return;
        var camera = directorCamera();
        double zoom = zoomFactor == 1 ? camera.zoom() : Math.clamp(Math.round(camera.zoom() * zoomFactor), 1, 64);
        // Keep the film centre steady while zooming; movement is measured in visible pixels.
        double x = camera.x() + screenX / camera.zoom() + controller.document().width() * (.5 / camera.zoom() - .5 / zoom);
        double y = camera.y() + screenY / camera.zoom() + controller.document().height() * (.5 / camera.zoom() - .5 / zoom);
        cameraDraft = new StageplayTimeline.CameraPose(Math.clamp(x, -1_048_576, 1_048_576),
                Math.clamp(y, -1_048_576, 1_048_576), zoom);
        status = "Camera framing preview. K captures a key; R records motion. Seeking discards this unkeyed framing.";
    }

    private void captureCameraKey() {
        var camera = integerCamera(directorCamera());
        var key = new CameraKey(controller.frame(), camera.x(), camera.y(), camera.zoom(), Interpolation.LINEAR);
        edit(d -> d.withCamera(withCameraKey(d.camera(), key)));
        cameraDraft = null;
        status = "Camera key captured at frame " + controller.frame() + ". Later keys animate pans; zoom changes cut at their key.";
    }

    private void startCameraTake() {
        stop();
        var camera = integerCamera(directorCamera());
        var before = controller.document();
        var keys = withCameraKey(before.camera(), new CameraKey(controller.frame(),
                camera.x(), camera.y(), camera.zoom(), Interpolation.HOLD));
        before.withCamera(keys);
        cameraMode = true; takeBefore = before; takeCameraKeys = keys;
        takeDraft = before.withCamera(keys);
        cameraDraft = null;
        status = "Recording camera: WASD / arrows pan, Q / E step integer zoom, Space stops. One take is one undo.";
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
        takeCameraKeys = keys; takeDraft = takeBefore.withCamera(keys); cameraDraft = null;
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
    private double zoomInput(InputHandler input) {
        int direction = (input.isKeyDown(GLFW_KEY_E) ? 1 : 0) - (input.isKeyDown(GLFW_KEY_Q) ? 1 : 0);
        if (direction == 0) { resetZoomRepeat(); return 1; }
        boolean step = direction != zoomHeldDirection;
        if (step) { zoomHeldDirection=direction; zoomHeldTicks=0; }
        else { zoomHeldTicks++; step=zoomHeldTicks>=18 && (zoomHeldTicks-18)%6==0; }
        if (!step) return 1;
        double current=directorCamera().zoom();
        double next=Math.clamp(direction>0 ? Math.floor(current)+1 : Math.ceil(current)-1,1,64);
        return next/current;
    }
    private void resetZoomRepeat() { zoomHeldDirection=0; zoomHeldTicks=0; }
    private StageplayTimeline.CameraPose integerCamera(StageplayTimeline.CameraPose camera) {
        double zoom=Math.clamp(Math.round(camera.zoom()),1,64);
        return new StageplayTimeline.CameraPose(Math.clamp(camera.x()+controller.document().width()*(.5/camera.zoom()-.5/zoom),-1_048_576,1_048_576),
                Math.clamp(camera.y()+controller.document().height()*(.5/camera.zoom()-.5/zoom),-1_048_576,1_048_576),zoom);
    }

    private StageplayDocument takeDocument(List<ActorKey> keys) {
        return takeBefore.withActors(takeBefore.actors().stream().map(a -> a.id().equals(takeActor)
                ? new Actor(a.id(), a.name(), a.gameId(), a.character(), keys) : a).toList());
    }

    private void newDocument() {
        controller.replace(host.initialDocument());
        cameraDraft = null; cameraMode = false;
        host.refreshResources();
        invalidatePreview();
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
        invalidatePreview();
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
                || doc.audio().stream().anyMatch(c -> !c.asset().isEmpty())
                || doc.dialogueSkins().stream().anyMatch(skin -> !skin.decorationAsset().isEmpty())
                || doc.dialogue().stream().anyMatch(cue -> !cue.portraitAsset().isEmpty());
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
        boolean add=actor==null;
        var oldTrack=add?null:puppet(controller.document(),actor.id());
        var fields=new ArrayList<>(List.of(text("name","Name",add?"Sonic":actor.name()),
                text("game","ROM: s1 / s2 / s3k",add?controller.document().set().gameId():actor.gameId()),
                text("character","Character",add?"sonic":actor.character()),
                text("mode","Performance: PUPPET / POSER",add || oldTrack!=null?"PUPPET":"POSER"),
                text("snap","Start on floor: true / false",Boolean.toString(oldTrack==null || oldTrack.snapToFloor())),
                number("collision","Terrain collision path (0 / 1)",oldTrack==null?0:oldTrack.collisionPath())));
        if (add) {
            var camera=StageplayTimeline.sample(controller.document(),0).baseCamera();
            fields.add(number("x","Initial world centre X",camera.x()+controller.document().width()/2.0));
            fields.add(number("y","Initial world centre Y (near floor)",camera.y()+controller.document().height()/2.0));
        }
        menu.showForm(add?"Add actor":"Actor properties",fields,"Apply actor",v -> {
            String mode=v.get("mode").toUpperCase(java.util.Locale.ROOT);
            if (!mode.equals("PUPPET") && !mode.equals("POSER")) throw new IllegalArgumentException("Performance must be PUPPET or POSER");
            boolean physical=mode.equals("PUPPET");
            String id=add?nextId("actor",controller.document()):actor.id();
            var keys=new ArrayList<ActorKey>();
            if (add) {
                var frames=new ArrayList<Integer>(); frames.add(0); controller.document().sceneChanges().forEach(c -> frames.add(c.frame()));
                for (int frame:frames) {
                    var camera=StageplayTimeline.sample(controller.document(),frame).baseCamera();
                    keys.add(new ActorKey(frame,frame==0?decimal(v,"x"):camera.x()+controller.document().width()/2.0,
                            frame==0?decimal(v,"y"):camera.y()+controller.document().height()/2.0,
                            0,-1,false,true,1,Interpolation.HOLD));
                }
            } else keys.addAll(actor.keys());
            var value=new Actor(id,v.get("name"),v.get("game"),v.get("character"),keys);
            if (physical) value=physicalKeys(value,controller.document().sceneChanges());
            Actor replacement=value;
            Runnable apply=() -> {
                edit(d -> {
                    var actors=new ArrayList<>(d.actors()); actors.removeIf(a -> a.id().equals(id)); actors.add(replacement);
                    var tracks=new ArrayList<>(d.puppets()); tracks.removeIf(t -> t.actorId().equals(id));
                    if (physical) tracks.add(new PuppetTrack(id,bool(v,"snap"),integer(v,"collision"),oldTrack==null?List.of():oldTrack.commands()));
                    return withCast(d,actors,tracks);
                });
                controller.selectActor(id);
            };
            if (!add && physical!=(oldTrack!=null)) {
                menu.showActions("Change actor performance?",List.of(
                        action(physical?"Use Puppet; replace Poser motion keys":"Use Poser; remove Puppet commands",apply),
                        action("Keep current performance",() -> { })));
                menu.setSubtitle("This is one undoable change. Puppet keeps shot placements and visibility; Poser starts from those placements. Undo restores all previous keys and commands.");
            } else apply.run();
        });
        menu.setSubtitle("Puppet uses native movement and jumps. Floor snap searches near the feet, up to 128 pixels below; disable it for an airborne start. Poser controls position, animation and frames. Existing scenes keep their original mode.");
    }

    private static StageplayDocument withCast(StageplayDocument d,List<Actor> actors,List<PuppetTrack> puppets) {
        return new StageplayDocument(d.version(),d.name(),d.durationFrames(),d.width(),d.height(),d.set(),actors,d.camera(),
                d.captions(),d.audio(),d.cameraEffects(),d.sceneChanges(),d.dialogueTypers(),d.dialogueSkins(),d.dialogue(),puppets);
    }

    /** Native physics owns motion, scale and animation; authored keys retain shot placement and visibility. */
    private static Actor physicalKeys(Actor actor,List<SceneChange> changes) {
        var boundaries=new java.util.HashSet<Integer>(); boundaries.add(0); changes.forEach(c -> boundaries.add(c.frame()));
        var keys=new ArrayList<ActorKey>(); ActorKey anchor=actor.keys().getFirst();
        for (var key:actor.keys()) {
            if (boundaries.contains(key.frame())) anchor=key;
            keys.add(new ActorKey(key.frame(),anchor.x(),anchor.y(),0,-1,anchor.flipX(),key.visible(),1,Interpolation.HOLD));
        }
        return new Actor(actor.id(),actor.name(),actor.gameId(),actor.character(),keys);
    }

    private void puppetMenu() {
        var actor=selectedActor(); var track=puppet(controller.document(),actor.id());
        if (track==null) throw new IllegalStateException("Choose PUPPET in Actor properties to direct native movement and jump actions");
        var actions=new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Move to destination",() -> puppetCommandForm(actor, null,PuppetCommandKind.MOVE_TO)));
        actions.add(action("Jump",() -> puppetCommandForm(actor,null,PuppetCommandKind.JUMP)));
        actions.add(action("Stop / brake",() -> puppetCommandForm(actor,null,PuppetCommandKind.STOP)));
        actions.add(action("Spawn / current shot placement",() -> puppetPlacementForm(actor)));
        actions.add(action("Visibility at playhead",() -> puppetVisibilityForm(actor)));
        for (var command:track.commands()) {
            actions.add(action(command.frame()+": "+command.kind(),() -> {
                var editAction=action("Edit",() -> puppetCommandForm(actor,command,command.kind()));
                var deleteAction=action("Delete",() -> edit(d -> {
                    var current=puppet(d,actor.id());
                    return replacePuppet(d,new PuppetTrack(actor.id(),current.snapToFloor(),current.collisionPath(),
                            current.commands().stream().filter(c -> !c.id().equals(command.id())).toList()));
                }));
                menu.showActions("Actor action",List.of(editAction,deleteAction));
            }));
        }
        menu.showActions("Direct "+actor.name(),actions);
        menu.setSubtitle(selectedActorStatus()+". Goals are attempts, not teleports; jump clears obstacles using native physics.");
    }

    private void puppetCommandForm(Actor actor,PuppetCommand command,PuppetCommandKind kind) {
        var pose=selectedActorPose();
        int frame=command==null?controller.frame():command.frame();
        var fields=new ArrayList<EditorProjectMenu.Field>(); fields.add(number("frame","Frame",frame));
        if (kind==PuppetCommandKind.MOVE_TO) {
            fields.add(number("x","Destination world X",command==null?(pose==null?actor.keys().getFirst().x():pose.x())+96:command.x()));
            fields.add(number("y","Destination world Y",command==null?(pose==null?actor.keys().getFirst().y():pose.y()):command.y()));
            fields.add(number("urgency","Urgency (>0 to 1, native speed fraction)",command==null?.65:command.urgency()));
        } else if (kind==PuppetCommandKind.JUMP) {
            int nextJump=puppet(controller.document(),actor.id()).commands().stream()
                    .filter(c -> c.kind()==PuppetCommandKind.JUMP && c.frame()>frame).mapToInt(PuppetCommand::frame).min()
                    .orElse(controller.document().durationFrames()+1);
            int defaultHold=Math.max(1,Math.min(12,Math.min(shotEnd(controller.document(),frame)-frame,nextJump-frame-1)));
            fields.add(number("hold","Jump hold (1..60 frames)",command==null?defaultHold:command.holdFrames()));
        }
        menu.showForm(kind==PuppetCommandKind.MOVE_TO?"Move to destination":kind==PuppetCommandKind.JUMP?"Native jump":"Stop / brake",fields,"Apply action",v -> edit(d -> {
            var track=puppet(d,actor.id()); var commands=new ArrayList<>(track.commands());
            String id=command==null?nextId("action",d):command.id(); commands.removeIf(c -> c.id().equals(id));
            commands.add(new PuppetCommand(id,integer(v,"frame"),kind,kind==PuppetCommandKind.MOVE_TO?decimal(v,"x"):0,
                    kind==PuppetCommandKind.MOVE_TO?decimal(v,"y"):0,kind==PuppetCommandKind.MOVE_TO?decimal(v,"urgency"):0,
                    kind==PuppetCommandKind.JUMP?integer(v,"hold"):0));
            commands.sort(Comparator.comparingInt(PuppetCommand::frame));
            return replacePuppet(d,new PuppetTrack(actor.id(),track.snapToFloor(),track.collisionPath(),commands));
        }));
        menu.setSubtitle(selectedActorStatus()+". A move continues until another move or stop. Jump presses need one released frame between holds. Urgency changes button steering, never native speed constants.");
    }

    private void puppetPlacementForm(Actor actor) {
        int frame=controller.document().sceneChanges().stream().filter(c -> c.frame()<=controller.frame()).mapToInt(SceneChange::frame).max().orElse(0);
        var key=actor.keys().stream().filter(k -> k.frame()==frame).findFirst().orElseThrow();
        menu.showForm("Shot placement at frame "+frame,List.of(number("x","World centre X",key.x()),number("y","World centre Y / floor search",key.y()),
                text("flip","Face left: true / false",Boolean.toString(key.flipX()))),"Apply placement",v -> edit(d -> {
            var replacement=new ActorKey(frame,decimal(v,"x"),decimal(v,"y"),0,-1,bool(v,"flip"),key.visible(),1,Interpolation.HOLD);
            var candidate=new Actor(actor.id(),actor.name(),actor.gameId(),actor.character(),withKey(actor.keys(),replacement));
            var normalized=physicalKeys(candidate,d.sceneChanges());
            return d.withActors(d.actors().stream().map(a -> a.id().equals(actor.id())?normalized:a).toList());
        }));
        menu.setSubtitle("Placement resets the actor at the start of this shot. Floor snap searches up to 128 pixels below the feet; disable Start on floor in Actor properties for an airborne start. Use Move to destination for movement.");
    }

    private void puppetVisibilityForm(Actor actor) {
        int frame=controller.frame();
        var key=actor.keys().stream().filter(k -> k.frame()<=frame).reduce((a,b)->b).orElseThrow();
        menu.showForm("Visibility at frame "+frame,List.of(text("visible","Visible: true / false",Boolean.toString(key.visible()))),"Apply visibility",v -> edit(d ->
                replaceActor(d,actor,withKey(actor.keys(),new ActorKey(frame,key.x(),key.y(),0,-1,key.flipX(),bool(v,"visible"),1,Interpolation.HOLD)))));
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
        var track=puppet(controller.document(),actor.id());
        if (track!=null) {
            var actions=new ArrayList<EditorProjectMenu.Action>();
            for (var command:track.commands()) if (command.frame()==frame) actions.add(action("Remove "+command.kind(),() -> edit(d -> {
                var current=puppet(d,actor.id()); return replacePuppet(d,new PuppetTrack(actor.id(),current.snapToFloor(),current.collisionPath(),
                        current.commands().stream().filter(c -> !c.id().equals(command.id())).toList()));
            })));
            if (frame!=0 && !isSceneBoundary(frame) && actor.keys().stream().anyMatch(k -> k.frame()==frame))
                actions.add(action("Remove visibility key",() -> edit(d -> replaceActor(d,actor,actor.keys().stream().filter(k -> k.frame()!=frame).toList()))));
            if (actions.isEmpty()) throw new IllegalStateException("No actor action or removable visibility key at this frame");
            menu.showActions("Remove actor action",actions); return;
        }
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
                }), action("Capture 1x pixel framing", () -> captureZoom(1)),
                action("Capture 2x pixel framing", () -> captureZoom(2)),
                action("Capture 3x pixel framing", () -> captureZoom(3)),
                action("Capture 4x pixel framing", () -> captureZoom(4))));
    }

    private void captureZoom(int zoom) { moveCamera(0,0,zoom/directorCamera().zoom()); captureCameraKey(); }

    private void cameraForm() {
        int frame = controller.frame();
        var existing=controller.document().camera().stream().filter(k -> k.frame()==frame).findFirst().orElse(null);
        var camera = existing==null ? integerCamera(directorCamera()) : directorCamera();
        var interpolation = controller.document().camera().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow().interpolation();
        menu.showForm("Camera key at frame " + frame,List.of(number("x","World left X",camera.x()),number("y","World top Y",camera.y()),
                number("zoom","Zoom (integer 1..64)",camera.zoom()),text("interpolation","HOLD / LINEAR / SMOOTH",interpolation.name())),"Set camera",v -> edit(d -> {
            double zoom=decimal(v,"zoom");
            if ((existing==null || zoom!=existing.zoom()) && (zoom<1 || zoom!=Math.rint(zoom)))
                throw new IllegalArgumentException("New framing uses integer zoom (1..64). Existing fractional values may be kept unchanged.");
            var keys = new ArrayList<>(d.camera()); keys.removeIf(k -> k.frame()==frame);
            keys.add(new CameraKey(frame,decimal(v,"x"),decimal(v,"y"),zoom,Interpolation.valueOf(v.get("interpolation").toUpperCase(java.util.Locale.ROOT))));
            keys.sort(Comparator.comparingInt(CameraKey::frame)); return d.withCamera(keys);
        }));
        menu.setSubtitle("Pan uses the chosen interpolation; zoom is held until the next key. Integer zoom preserves pixel framing. Imported fractional zoom can shimmer while panning.");
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
                        action("Delete", () -> edit(d -> withSceneBoundaries(d,d.sceneChanges().stream()
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
        for (int i=0;i<actors.size();i++) if (puppet(doc,actors.get(i).id())!=null) actors.set(i,physicalKeys(actors.get(i),changes));
        return new StageplayDocument(doc.version(),doc.name(),doc.durationFrames(),doc.width(),doc.height(),doc.set(),
                actors,camera,doc.captions(),doc.audio(),doc.cameraEffects(),changes,doc.dialogueTypers(),doc.dialogueSkins(),doc.dialogue(),doc.puppets());
    }


    private static StageplayDocument removeActor(StageplayDocument d,String id) {
        return withCast(d,d.actors().stream().filter(a -> !a.id().equals(id)).toList(),d.puppets().stream().filter(p -> !p.actorId().equals(id)).toList());
    }

    private void deleteActor() {
        var actor = selectedActor();
        if (controller.document().dialogue().stream().noneMatch(c -> c.speakerActor().equals(actor.id()))) {
            edit(d -> removeActor(d,actor.id()));
            return;
        }
        menu.showActions("Remove " + actor.name() + "?", List.of(
                action("Keep dialogue as offscreen speech", () -> edit(d -> {
                    var cues = d.dialogue().stream().map(c -> c.speakerActor().equals(actor.id())
                            ? dialogueSpeaker(c, "", StageplayDialogue.speakerName(d,c)) : c).toList();
                    return removeActor(d.withDialogue(cues),actor.id());
                })), action("Remove actor and their dialogue", () -> edit(d -> removeActor(d.withDialogue(d.dialogue().stream()
                        .filter(c -> !c.speakerActor().equals(actor.id())).toList()),actor.id()))), action("Keep actor", () -> { })));
    }

    private void dialogueMenu() {
        var d = controller.document();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add dialogue line", () -> speakerMenu(null)));
        actions.add(action("Reusable typers", this::typerMenu));
        actions.add(action("Reusable decorations", this::skinMenu));
        for (var cue : d.dialogue()) actions.add(action(cue.startFrame()+": "+StageplayDialogue.speakerName(d,cue)+" - "+cue.text(),
                () -> dialogueCueMenu(cue)));
        menu.showActions("Dialogue lines", actions);
        menu.setSubtitle("Names appear immediately. Typed text, speaker pointers and decorations share the film timeline.");
    }

    private void dialogueCueMenu(DialogueCue cue) {
        menu.showActions(StageplayDialogue.speakerName(controller.document(),cue)+" / dialogue", List.of(
                action("Edit line, timing and panel", () -> dialogueForm(cue)),
                action("Choose speaker", () -> speakerMenu(cue)),
                action("Choose decoration", () -> dialogueProfileMenu(cue,false)),
                action("Choose typer", () -> dialogueProfileMenu(cue,true)),
                action("Custom reveal timing", () -> revealForm(cue)),
                action("Use typer timing", () -> edit(d -> replaceDialogue(d,dialogueTiming(cue,List.of())))),
                action("Delete line", () -> edit(d -> d.withDialogue(d.dialogue().stream().filter(c -> !c.id().equals(cue.id())).toList())))));
    }

    private void speakerMenu(DialogueCue cue) {
        var choices = new ArrayList<EditorProjectMenu.Action>();
        for (var actor : controller.document().actors()) choices.add(action(actor.name()+" ("+actor.character()+")", () -> {
            if (cue == null) dialogueForm(newDialogue(actor.id(),""));
            else edit(d -> replaceDialogue(d,dialogueSpeaker(cue,actor.id(),"")));
        }));
        choices.add(action("Offscreen speaker / narrator", () -> {
            if (cue == null) dialogueForm(newDialogue("","Narrator"));
            else dialogueForm(dialogueSpeaker(cue,"",StageplayDialogue.speakerName(controller.document(),cue)));
        }));
        menu.showActions("Who is speaking?", choices);
    }

    private DialogueCue newDialogue(String actor, String name) {
        var d = controller.document();
        int margin = Math.min(12,Math.max(0,(d.width()-80)/2));
        int height = Math.min(72,d.height());
        return new DialogueCue(nextId("dialogue",d),controller.frame(),Math.min(controller.frame()+180,d.durationFrames()),
                actor,name,"Hello.","","",margin,Math.max(0,d.height()-height-margin),d.width()-margin*2,height,"",List.of());
    }

    private void dialogueForm(DialogueCue cue) {
        menu.showForm("Dialogue line",List.of(
                text("name",cue.speakerActor().isEmpty()?"Speaker name":"Name override (blank = actor)",cue.speakerName()),
                new EditorProjectMenu.Field("text","Text (\\n = line break)",escapeCaption(cue.text()),MenuTextEditor.Mode.TEXT,8192),
                number("start","Start frame",cue.startFrame()),number("end","End frame (exclusive)",cue.endFrame()),
                number("x","Panel screen X",cue.x()),number("y","Panel screen Y",cue.y()),
                number("width","Panel width",cue.width()),number("height","Panel height",cue.height()),
                file("portrait","Relative portrait PNG (optional)",cue.portraitAsset())),"Apply dialogue",v -> edit(d -> {
            String line = unescapeCaption(v.get("text"));
            if (!cue.revealFrames().isEmpty() && StageplayDialogue.graphemeCount(line)!=cue.revealFrames().size())
                throw new IllegalArgumentException("Choose Use typer timing before changing this line's character count, then edit custom timing again.");
            return replaceDialogue(d,new DialogueCue(cue.id(),integer(v,"start"),integer(v,"end"),cue.speakerActor(),v.get("name"),line,
                    cue.skinId(),cue.typerId(),integer(v,"x"),integer(v,"y"),integer(v,"width"),integer(v,"height"),v.get("portrait"),cue.revealFrames()));
        }));
        menu.setSubtitle("Speaker: "+StageplayDialogue.speakerName(controller.document(),cue)+". Choose decoration and typer from the line menu. Panels use film pixels.");
    }

    private void dialogueProfileMenu(DialogueCue cue, boolean typer) {
        var choices = new ArrayList<EditorProjectMenu.Action>();
        choices.add(action("Built-in default", () -> edit(d -> replaceDialogue(d,dialogueProfile(cue,typer,"")))));
        if (typer) for (var value : controller.document().dialogueTypers()) choices.add(action(value.id()+" / "+value.framesPerGrapheme()+" frames per character",
                () -> edit(d -> replaceDialogue(d,dialogueProfile(cue,true,value.id())))));
        else for (var value : controller.document().dialogueSkins()) choices.add(action(value.id(),
                () -> edit(d -> replaceDialogue(d,dialogueProfile(cue,false,value.id())))));
        menu.showActions(typer ? "Choose dialogue typer" : "Choose dialogue decoration",choices);
        if (typer && !cue.revealFrames().isEmpty()) menu.setSubtitle("This line has custom reveal timing. Choose Use typer timing in its menu to use the new rhythm.");
    }

    private void revealForm(DialogueCue cue) {
        var offsets = StageplayDialogue.revealFrames(controller.document(),cue);
        var fields = new ArrayList<EditorProjectMenu.Field>();
        for (int from=0;from<offsets.size();from+=128) {
            int end=Math.min(from+128,offsets.size());
            fields.add(new EditorProjectMenu.Field("offsets"+(from/128),"Characters "+(from+1)+".."+end,
                    offsets.subList(from,end).stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")),
                    MenuTextEditor.Mode.TEXT,8192));
        }
        menu.showForm("Custom dialogue reveal timing",fields,"Apply reveal timing",v -> edit(d -> {
            var values = new ArrayList<Integer>();
            for (int i=0;i<fields.size();i++) {
                String input=v.get("offsets"+i).trim();
                if (!input.isEmpty()) for (String token:input.split("[,\\s]+")) values.add(Integer.parseInt(token));
            }
            if (values.size()!=StageplayDialogue.graphemeCount(cue.text()))
                throw new IllegalArgumentException("Enter exactly one reveal frame per character (combining characters stay together).");
            return replaceDialogue(d,dialogueTiming(cue,values));
        }));
        menu.setSubtitle("Frame offsets from the line's start, in character order; equal offsets reveal together. Every offset must be inside its interval.");
    }

    private void typerMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add typer", () -> typerForm(null)));
        for (var typer : controller.document().dialogueTypers()) actions.add(action(typer.id(), () -> menu.showActions("Typer: "+typer.id(),List.of(
                action("Edit typer", () -> typerForm(typer)),
                action("Delete and use default for its lines", () -> edit(d -> d.withDialogue(d.dialogue().stream()
                        .map(c -> c.typerId().equals(typer.id()) ? dialogueProfile(c,true,"") : c).toList())
                        .withDialogueTypers(d.dialogueTypers().stream().filter(t -> !t.id().equals(typer.id())).toList())))))));
        menu.showActions("Reusable dialogue typers",actions);
    }

    private void typerForm(DialogueTyper typer) {
        String id=typer==null ? nextProfileId("typer",controller.document().dialogueTypers().stream().map(DialogueTyper::id).toList()) : typer.id();
        var fields=new ArrayList<EditorProjectMenu.Field>();
        if (typer==null) fields.add(text("id","Profile name",id));
        fields.add(number("speed","Frames per character (0 = instant)",typer==null?2:typer.framesPerGrapheme()));
        fields.add(number("punctuation","Extra pause after punctuation",typer==null?6:typer.punctuationPauseFrames()));
        fields.add(number("space","Extra pause after spaces",typer==null?0:typer.spacePauseFrames()));
        menu.showForm(typer==null?"Add dialogue typer":"Typer: "+id,fields,"Apply typer",v -> edit(d -> {
            String profile=typer==null?v.get("id"):id;
            if (typer==null && d.dialogueTypers().stream().anyMatch(t -> t.id().equals(profile))) throw new IllegalArgumentException("That typer name already exists");
            var values=new ArrayList<>(d.dialogueTypers()); values.removeIf(t -> t.id().equals(profile));
            values.add(new DialogueTyper(profile,integer(v,"speed"),integer(v,"punctuation"),integer(v,"space")));
            return d.withDialogueTypers(values);
        }));
        menu.setSubtitle("Reusable profile names use letters, digits, dots, underscores or hyphens. Choose this typer from a dialogue line's menu.");
    }

    private void skinMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add decoration", () -> skinForm(null)));
        for (var skin : controller.document().dialogueSkins()) actions.add(action(skin.id(), () -> menu.showActions("Decoration: "+skin.id(),List.of(
                action("Edit decoration", () -> skinForm(skin)),
                action("Delete and use default for its lines", () -> edit(d -> d.withDialogue(d.dialogue().stream()
                        .map(c -> c.skinId().equals(skin.id()) ? dialogueProfile(c,false,"") : c).toList())
                        .withDialogueSkins(d.dialogueSkins().stream().filter(s -> !s.id().equals(skin.id())).toList())))))));
        menu.showActions("Reusable dialogue decorations",actions);
    }

    private void skinForm(DialogueSkin skin) {
        String id=skin==null ? nextProfileId("skin",controller.document().dialogueSkins().stream().map(DialogueSkin::id).toList()) : skin.id();
        var fields=new ArrayList<EditorProjectMenu.Field>();
        if (skin==null) fields.add(text("id","Profile name",id));
        fields.add(text("text","Text ARGB hex",hex(skin==null?0xfff5f0df:skin.textArgb())));
        fields.add(text("name","Speaker name ARGB hex",hex(skin==null?0xffffcf66:skin.nameArgb())));
        fields.add(text("panel","Panel ARGB hex",hex(skin==null?0xff172338:skin.panelArgb())));
        fields.add(text("border","Border ARGB hex",hex(skin==null?0xffa8b8cf:skin.borderArgb())));
        fields.add(number("padding","Inner padding (4..32)",skin==null?6:skin.padding()));
        fields.add(file("asset","Relative decoration PNG (optional)",skin==null?"":skin.decorationAsset()));
        fields.add(number("slice","Fixed PNG border (0..64 pixels)",skin==null?0:skin.decorationBorder()));
        fields.add(text("pointer","Speaker pointer: true / false",Boolean.toString(skin==null || skin.pointer())));
        menu.showForm(skin==null?"Add dialogue decoration":"Decoration: "+id,fields,"Apply decoration",v -> edit(d -> {
            String profile=skin==null?v.get("id"):id;
            if (skin==null && d.dialogueSkins().stream().anyMatch(s -> s.id().equals(profile))) throw new IllegalArgumentException("That decoration name already exists");
            var values=new ArrayList<>(d.dialogueSkins()); values.removeIf(s -> s.id().equals(profile));
            values.add(new DialogueSkin(profile,color(v,"text"),color(v,"name"),color(v,"panel"),color(v,"border"),integer(v,"padding"),v.get("asset"),integer(v,"slice"),bool(v,"pointer")));
            return d.withDialogueSkins(values);
        }));
        menu.setSubtitle("An optional PNG stretches its centre and edges while keeping corners fixed. Keep the PNG beneath the scene folder. Profile names are stable.");
    }

    private static StageplayDocument replaceDialogue(StageplayDocument doc,DialogueCue cue) {
        var values=new ArrayList<>(doc.dialogue()); values.removeIf(c -> c.id().equals(cue.id())); values.add(cue);
        values.sort(Comparator.comparingInt(DialogueCue::startFrame)); return doc.withDialogue(values);
    }
    private static DialogueCue dialogueSpeaker(DialogueCue c,String actor,String name) {
        return new DialogueCue(c.id(),c.startFrame(),c.endFrame(),actor,name,c.text(),c.skinId(),c.typerId(),c.x(),c.y(),c.width(),c.height(),c.portraitAsset(),c.revealFrames());
    }
    private static DialogueCue dialogueProfile(DialogueCue c,boolean typer,String id) {
        return new DialogueCue(c.id(),c.startFrame(),c.endFrame(),c.speakerActor(),c.speakerName(),c.text(),typer?c.skinId():id,typer?id:c.typerId(),c.x(),c.y(),c.width(),c.height(),c.portraitAsset(),c.revealFrames());
    }
    private static DialogueCue dialogueTiming(DialogueCue c,List<Integer> timing) {
        return new DialogueCue(c.id(),c.startFrame(),c.endFrame(),c.speakerActor(),c.speakerName(),c.text(),c.skinId(),c.typerId(),c.x(),c.y(),c.width(),c.height(),c.portraitAsset(),timing);
    }
    private static String nextProfileId(String prefix,List<String> used) { int i=1; while(used.contains(prefix+i)) i++; return prefix+i; }
    private static String hex(int color) { return String.format("%08X",color); }
    private static int color(Map<String,String> v,String key) {
        String value=v.get(key).trim();
        if (!value.matches("[0-9a-fA-F]{8}")) throw new IllegalArgumentException("Colors need eight ARGB hex digits");
        return (int)Long.parseLong(value,16);
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
        var used=allIds(doc); int i=1; while(used.contains(prefix+i)) i++; return prefix+i;
    }
    private static java.util.Set<String> allIds(StageplayDocument doc) {
        var used = new java.util.HashSet<String>();
        doc.actors().forEach(a -> used.add(a.id())); doc.captions().forEach(c -> used.add(c.id())); doc.audio().forEach(c -> used.add(c.id()));
        doc.cameraEffects().forEach(e -> used.add(e.id())); doc.sceneChanges().forEach(c -> used.add(c.id()));
        doc.dialogue().forEach(c -> used.add(c.id()));
        doc.puppets().forEach(p -> p.commands().forEach(c -> used.add(c.id())));
        return used;
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
