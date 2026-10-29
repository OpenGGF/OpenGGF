package com.openggf.editor.stageplay;

import com.openggf.i18n.UiMessages;
import com.openggf.editor.EditorLabels;
import java.util.Map;



import com.openggf.stageplay.StageplayDocument.Facing;

import com.openggf.control.InputHandler;
import com.openggf.game.MenuDetailsScreen;
import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.game.MenuTextEditor;
import com.openggf.stageplay.StageplayController;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayDialogue;
import com.openggf.stageplay.StageplayGround;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tools.StageplayScriptCompiler;
import com.openggf.tools.StageplayScriptDecompiler;
import com.openggf.stageplay.route.*;
import com.openggf.stageplay.editor.StageplayRouteJob;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        /** Resolve transient framing through the same native preview owner as authored frames. */
        default StageplayTimeline.Frame resolvePreview(StageplayDocument document, Path assetRoot, StageplayTimeline.Frame draft) throws IOException {
            return draft;
        }
        default StageplayGround.Report ground(StageplayDocument document, Path root, int frame,
                                             StageplayGround.Batch batch) throws IOException {
            throw new IOException(UiMessages.text("editor.stageplay_workspace.place_on_floor_is_unavailable_in_this_host_1usuibd"));
        }
        default List<com.openggf.stageplay.runtime.StageplayObjectInspection> objectInspection() { return List.of(); }
        default void stop() { }
        default java.util.Set<com.openggf.game.CharacterDefinition.MovementAbility> movementAbilities(Actor actor, Path root) throws IOException {
            return java.util.Set.of();
        }
        default void startRoute(StageplayDocument document, Path root, StageplayRouteRequest request) throws IOException {
            throw new IOException(UiMessages.text("editor.stageplay_workspace.route_helper_is_unavailable_in_this_host_266462"));
        }
        default StageplayRouteJob.Update pollRoute() { return null; }
        default void requestRouteApply(StageplayDocument document, Path root) throws IOException {
            throw new IOException(UiMessages.text("editor.stageplay_workspace.route_helper_is_unavailable_in_this_host_266462"));
        }
        default void cancelRoute() { }
        default void export(StageplayDocument document, Path source, Path output) throws IOException {
            throw new IOException(UiMessages.text("editor.stageplay_workspace.film_export_is_unavailable_in_this_host_tmyb0l"));
        }
    }

    public enum Action {
        NEW("editor.stageplay_workspace.new_scene_1y5ft2k"), OPEN("editor.stageplay_workspace.open_scene_eka79y"), IMPORT_SCREENPLAY("editor.stageplay_workspace.import_screenplay_zvmokb"),
        EXPORT_SCREENPLAY("editor.stageplay_workspace.export_screenplay_1ukin3g"), SAVE("editor.stageplay_workspace.save_1j2ql"), SAVE_AS("editor.stageplay_workspace.save_as_1mi2x05"),
        SCENE("editor.stageplay_workspace.scene_set_1wx4xml"), ADD_ACTOR("editor.stageplay_workspace.add_actor_8pm4k6"), EDIT_ACTOR("editor.stageplay_workspace.actor_properties_1777ala"),
        DELETE_ACTOR("editor.stageplay_workspace.remove_actor_z6u1yx"), SET_KEY("editor.stageplay_workspace.direct_actor_set_key_izib72"), DELETE_KEY("editor.stageplay_workspace.remove_actor_key_action_9ry3hr"),
        PUPPET_ACTIONS("editor.stageplay_workspace.puppet_actions_qleqlt"), ROUTE_HELPER("editor.stageplay_workspace.plan_route_fkwmnm"), PLACE_ON_FLOOR("editor.stageplay_workspace.place_on_floor_182owpw"), OBJECTS("editor.stageplay_workspace.rom_object_cast_clear_1yomnf0"),
        CAMERA_MODE("editor.menu.stageplay_action.camera_mode"), SET_CAMERA_KEY("editor.menu.stageplay_action.set_camera_key"), CAMERA_KEY("editor.stageplay_workspace.camera_keys_1oo6ju7"),
        RECORD_CAMERA("editor.menu.stageplay_action.record_camera"), CAMERA_EFFECTS("editor.stageplay_workspace.camera_shake_quake_1kx3ox"),
        SCENE_CHANGES("editor.stageplay_workspace.scene_changes_transitions_12bmha4"), FILM_FORMAT("editor.stageplay_workspace.film_format_presets_3yt45j"), CAPTION("editor.stageplay_workspace.captions_8nvjh"), DIALOGUE("editor.stageplay_workspace.dialogue_lines_1el2v2v"),
        DIALOGUE_TYPERS("editor.stageplay_workspace.dialogue_typers_9i6ioz"), DIALOGUE_SKINS("editor.stageplay_workspace.dialogue_decorations_1k831qz"), SPEAKER_VOICE("editor.stageplay_workspace.speaker_voice_1hf42pd"), AUDIO("editor.stageplay_workspace.audio_cues_1lmdfpm"),
        RECORD("editor.menu.stageplay_action.record"), PLAY("editor.stageplay_workspace.play_pause_1kh277d"), STOP("editor.stageplay_workspace.stop_1jgo2"),
        UNDO("editor.stageplay_workspace.undo_1klxg"), REDO("editor.stageplay_workspace.redo_1iiam"), EXPORT("editor.stageplay_workspace.export_film_1r01u1c"), DETAILS("editor.stageplay_workspace.direction_details_1nra075"), EXIT("editor.stageplay_workspace.level_editor_i7o04p");
        private final String label;
        Action(String label) { this.label = label; }
        public String label() { return UiMessages.text(label); }
    }

    private final Host host;
    private final EditorProjectMenu menu = new EditorProjectMenu();
    private final StageplayController controller;
    private Path path;
    private Path assetRoot;
    private StageplayDocument routeSource;
    private Path routeRoot;
    private String routeSelection;
    private StageplayRouteRequest routeRequest;
    private StageplayRouteJob.Update routeUpdate;
    private com.openggf.stageplay.editor.StageplayRouteOverlay proposedOverlay;
    private com.openggf.stageplay.editor.StageplayRouteOverlay appliedOverlay;
    private Path appliedRouteRoot;
    private boolean active;
    private boolean initialized;
    private String status = UiMessages.text("editor.stageplay_workspace.create_a_scene_or_open_a_stageplay_file_e6wix5");
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
    private StageplayPositionPicker picker;
    private EditorProjectMenu.PositionField pickerField;
    private long pickerGeneration;
    private int pickerFrame;
    private StageplayDocument pickerDocument;
    private Path pickerRoot;
    public boolean pickingPosition(){return picker!=null;}
    public StageplayPositionPicker positionPicker(){return picker;}
    public void pickPoint(double x,double y){if(picker!=null)picker.point(x,y);}
    public void panPicker(double dx,double dy){if(picker!=null)picker.pan(dx,dy);}
    public void togglePickerSnap(){if(picker!=null)picker.toggleSnap();}
    public void paintPositionPicker(StageplayPositionPicker.Painter painter,float x,float y,float width,float height) {
        if(picker==null)return;
        var view=previewFrame().camera();
        picker.paint(painter,x,y,width,height,new StageplayPositionPicker.View(view.x(),view.y(),view.zoom()));
    }
    public void confirmPositionPick(){finishPositionPick(true);}
    public void cancelPositionPick(){finishPositionPick(false);}
    private void beginPositionPick(EditorProjectMenu.PositionField field){
        var values=menu.values();double x=Double.parseDouble(values.get(field.x()));
        double y=field.y()==null?java.util.Optional.ofNullable(selectedActorPose()).map(StageplayTimeline.ActorPose::y).orElse(previewFrame().camera().y()+controller.document().height()/2.0)
                :Double.parseDouble(values.get(field.y()));
        if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.position_needs_finite_numbers_1nlqyib"));
        pickerField=field;pickerGeneration=menu.generation();pickerFrame=controller.frame();pickerDocument=controller.document();pickerRoot=assetRoot;
        var frame=previewFrame();
        java.util.function.BiFunction<Integer,Integer,List<StageplayPositionPicker.Floor>> ground=field.floor()?(cx,cy)->{
            try {
                String actor=menu.values().getOrDefault("actor",controller.selectedActorId());
                StageplayGround.Visual visual=menu.values().containsKey("halfWidth")?new StageplayGround.Visual(integer(menu.values(),"halfWidth"),integer(menu.values(),"height"),integer(menu.values(),"footOffset"),StageplayGround.ObjectPath.of(integer(menu.values(),"collisionPath"))):null;
                StageplayGround.Performer performer;
                if (menu.values().containsKey("character") && menu.values().containsKey("game")) {
                    // Add actor has no saved identity yet; query the role being authored.
                    actor = "";
                    performer = new StageplayGround.Player(menu.values().get("game"),menu.values().get("character"),
                            integer(menu.values(),"collision"));
                } else performer = StageplayGround.performerOf(pickerDocument,actor,visual);
                int top=Math.max(Short.MIN_VALUE,cy-32),bottom=Math.min(Short.MAX_VALUE,cy+256);
                var q=new StageplayGround.Query("picker",cx,top,bottom,performer,actor);
                var report=host.ground(pickerDocument,pickerRoot,controller.frame(),new StageplayGround.Batch(List.of(q),100,100000,0));
                var answer=report.answer("picker");
                if(answer.outcome()!=StageplayGround.Outcome.FOUND||answer.truncated())return List.of();
                return answer.placements().stream().map(p->new StageplayPositionPicker.Floor(p.x(),p.y(),p.surfaceY())).toList();
            }catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
        }:null;
        picker=new StageplayPositionPicker(x,y,controller.document().width(),controller.document().height(),new StageplayPositionPicker.View(frame.camera().x(),frame.camera().y(),frame.camera().zoom()),field.snap(),ground);
        menu.suspend();
    }
    private void finishPositionPick(boolean accept){
        if(picker==null)return;
        var selected=picker; if(accept)selected.refreshFloor(); picker=null;menu.resume();
        if(accept&&controller.frame()==pickerFrame&&menu.generation()==pickerGeneration&&controller.document().equals(pickerDocument)&&assetRoot.equals(pickerRoot)){
            menu.setValue(pickerField.x(),EditorProjectMenu.coordinate(Double.toString(selected.x())));
            if(pickerField.y()!=null)menu.setValue(pickerField.y(),EditorProjectMenu.coordinate(Double.toString(selected.pickedY())));
        }
        pickerField=null;pickerDocument=null;pickerRoot=null;
    }
    private StageplayTimeline.CameraPose cameraDraft;
    private boolean startedThisTick;
    private boolean previewSoundActive;
    private int zoomHeldDirection;
    private int zoomHeldTicks;

    public StageplayWorkspace(Host host) {
        this.host = Objects.requireNonNull(host);
        controller = new StageplayController(StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224));
        assetRoot = host.initialAssetRoot();
        menu.onShow(m -> StageplayMenuPresentation.configure(m,menuContext(),controller.selectedActorId()));
        menu.onPositionPick(this::beginPositionPick);
    }

    /** Translate semantic document choices inside the existing authoring boundary. */
    private StageplayMenuPresentation.Context menuContext() {
        var players=new java.util.LinkedHashMap<String,String>();
        var objects=new java.util.LinkedHashMap<String,String>();
        controller.document().actors().forEach(a->players.put(a.id(),a.name()));
        controller.document().objectActors().forEach(a->objects.put(a.id(),a.name()));
        var choices=Map.of("Interpolation",enumNames(Interpolation.values()),"Transition",enumNames(Transition.values()),
                "WaitCondition",enumNames(WaitCondition.values()),"ObjectSourceKind",enumNames(ObjectSourceKind.values()),
                "ObjectMode",enumNames(ObjectMode.values()),"AudioKind",enumNames(AudioKind.values()),
                "WorldCommandKind",enumNames(WorldCommandKind.values()),"ObjectCommandKind",enumNames(ObjectCommandKind.values()));
        return new StageplayMenuPresentation.Context(players,objects,choices,StageplayObjectAuthoring::objectChoices);
    }
    private static List<String> enumNames(Enum<?>[] values) {return java.util.Arrays.stream(values).map(Enum::name).toList();}

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
        return previewFrame().baseCamera();
    }
    public String status() { return status; }
    public String statusSummary(){
        if(previewError!=null)return UiMessages.text("editor.stageplay_workspace.preview_needs_attention_open_direction_details_1ws4kjf");
        return StageplayMenuPresentation.sentence(status);
    }
    public void directionDetails(){
        var overlay=routeOverlay();
        String route=overlay==null?"":"\n"+overlay.reason()+"\n"+overlay.motionSummary()
                +"\n"+overlay.path().size()+" samples, "+overlay.result().expandedNodes()+" nodes, "+overlay.result().simulatedTicks()+" ticks.";
        menu.showActions(UiMessages.text("editor.stageplay_workspace.direction_details_1nra075"),List.of(action(UiMessages.text("editor.stageplay_workspace.back_187if"),()->{})));
        menu.setHelp(UiMessages.text("editor.stageplay_workspace.current_status_and_directing_controls_1gzssf5"),UiMessages.text("editor.stageplay_workspace.camera_wasd_arrows_pan_q_e_zoom_k_captures_framing_r_re_va0kct", Map.of("value0", String.valueOf(status + "\n" + selectedActorStatus() + route))));
    }
    public Path path() { return path; }
    public Path assetRoot() { return assetRoot; }

    /** Authoring-only state. It is never passed to the runtime renderer, exporter or document. */
    public com.openggf.stageplay.editor.StageplayRouteOverlay routeOverlay() {
        var overlay = proposedOverlay != null ? proposedOverlay : appliedOverlay;
        if (overlay == null || !active || !overlay.result().request().actorId().equals(controller.selectedActorId())) return null;
        if (overlay.applied() && (!overlay.result().candidate().equals(controller.document())
                || !Objects.equals(appliedRouteRoot, assetRoot))) return null;
        int start = overlay.result().request().startFrame();
        // A route belongs to one shot, even when another shot reuses the same set.
        int shotStart = 0, shotEnd = controller.document().durationFrames();
        for (var change : controller.document().sceneChanges()) {
            if (change.frame() <= start) shotStart = change.frame();
            else { shotEnd = change.frame(); break; }
        }
        return controller.frame() >= shotStart && controller.frame() < shotEnd ? overlay : null;
    }

    public void paintRouteOverlay(com.openggf.stageplay.editor.StageplayRouteOverlay.Painter painter,
                                  float left, float top, float width, float height) {
        var overlay = routeOverlay();
        if (overlay == null) return;
        var sample = previewFrame();
        double actualX = Double.NaN, actualY = Double.NaN;
        for (var actor : sample.actors()) if (actor.actorId().equals(controller.selectedActorId()) && actor.visible()) {
            actualX = actor.x(); actualY = actor.y(); break;
        }
        overlay.paint(painter, sample.camera(), controller.document().width(), controller.document().height(),
                left, top, width, height, actualX, actualY);
    }

    public void activate() {
        try {
            host.finishPendingEdits();
            if (!initialized) newDocument();
            host.refreshResources();
            invalidatePreview();
            active = true;
        } catch (RuntimeException failure) {
            menu.showActions(UiMessages.text("editor.stageplay_workspace.cannot_open_stageplay_367ck9"), List.of(action(UiMessages.text("editor.stageplay_workspace.keep_editing_1jzrdi5"), () -> { })));
            report(failure);
        }
    }

    /** Workspace changes preserve the scene and unsaved history. */
    public void deactivate() { cancelPositionPick(); cancelRoute(); stop(); menu.close(); cameraDraft = null; cameraMode = false; active = false; }

    public void seek(int frame) { stop(); controller.seek(frame); cameraDraft = null; }
    public void selectActor(String id) { stop(); controller.selectActor(id); }

    public void invoke(Action action) {
        try {
            // Screenplay IO parses before replacing a scene and exports without committing a take.
            if (action != Action.IMPORT_SCREENPLAY && action != Action.EXPORT_SCREENPLAY) {
                host.finishPendingEdits();
                if (action != Action.PLAY && action != Action.RECORD && action != Action.RECORD_CAMERA) stop();
            }
            switch (action) {
                case NEW -> beforeReplace(this::newDocument);
                case OPEN -> beforeReplace(this::openForm);
                case IMPORT_SCREENPLAY -> importScreenplayForm();
                case EXPORT_SCREENPLAY -> exportScreenplayForm();
                case SAVE -> save(null);
                case SAVE_AS -> saveAs(null);
                case SCENE -> sceneForm();
                case ADD_ACTOR -> actorForm(null);
                case EDIT_ACTOR -> actorForm(selectedActor());
                case DELETE_ACTOR -> deleteActor();
                case SET_KEY -> { if (selectedActorIsPuppet()) puppetMenu(); else keyForm(); }
                case DELETE_KEY -> deleteKey();
                case PUPPET_ACTIONS -> puppetMenu();
                case ROUTE_HELPER -> routeMenu();
                case PLACE_ON_FLOOR -> groundForm();
                case OBJECTS -> new StageplayObjectAuthoring(menu, controller, this::edit, this::previewFrame,
                        () -> { previewFrame(); return host.objectInspection(); }).show();
                case CAMERA_MODE -> {
                    cameraMode = !cameraMode; cameraDraft = null;
                    status = (!cameraMode ? UiMessages.text("editor.stageplay_workspace.actor_direction_dgp984") : UiMessages.text("editor.stageplay_workspace.camera_wasd_arrows_pan_q_e_step_integer_zoom_k_capture_f40820"));
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
                case SPEAKER_VOICE -> {
                    String actor = controller.selectedActorId();
                    if (actor.isEmpty()) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.select_a_cast_member_to_edit_their_voice_17mkob7"));
                    voiceForm(actor, "");
                }
                case AUDIO -> audioMenu();
                case RECORD -> { if (recording()) stop(); else startTake(); }
                case PLAY -> { if (controller.playing() || recording()) stop(); else play(); }
                case STOP -> { controller.seek(0); cameraDraft = null; }
                case UNDO -> { controller.undo(); cameraDraft = null; }
                case REDO -> { controller.redo(); cameraDraft = null; }
                case EXPORT -> exportForm();
                case DETAILS -> directionDetails();
                case EXIT -> deactivate();
            }
        } catch (RuntimeException failure) { report(failure); }
    }

    /** Called once per editor presentation tick, after the active frontend's queued commands. */
    public boolean tick(InputHandler input) {
        if (!active && !menu.isOpen()) return false;
        try { pollRoute(); }
        catch(RuntimeException failure) { cancelRoute(); report(failure); }
        if(picker!=null){
            if(controller.frame()!=pickerFrame||menu.generation()!=pickerGeneration||!controller.document().equals(pickerDocument)||!assetRoot.equals(pickerRoot)){cancelPositionPick();return true;}
            var result=picker.tick(input);
            if(result==StageplayPositionPicker.Result.CONFIRM)confirmPositionPick();else if(result==StageplayPositionPicker.Result.CANCEL)cancelPositionPick();
            return true;
        }
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
        status = (physical ? UiMessages.text("editor.stageplay_workspace.recorded_puppet_instructions_undo_removes_the_whole_tak_2p4f3p") : UiMessages.text("editor.stageplay_workspace.recorded_poser_visual_take_undo_removes_the_whole_take_ikoq24"));
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
            status = UiMessages.text("editor.stageplay_workspace.playing_from_frame_seeking_is_visual_and_does_not_repla_a3odut", Map.of("value0", String.valueOf(controller.frame())));
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
        // The prompt bar lists the take's keys; the status says what is being recorded.
        status = track == null ? UiMessages.text("editor.menu.stageplay_workspace.recording_a_poser_take_undo_removes_the_whole_visual_take")
                : UiMessages.text("editor.menu.stageplay_workspace.recording_a_puppet_take_goals_use_native_acceleration_undo_removes_the_whole_take");
    }

    /** Deterministic puppet recording, deliberately separate from engine input replays. */
    void recordStep(boolean right, boolean left, boolean down, boolean up) {
        if (!recording()) return;
        if (recordingPuppet()) { recordPuppetStep(right, left, down, up); return; }
        int frame = controller.frame();
        var draft = takeDocument(takeKeys);
        var pose = StageplayTimeline.sample(draft, frame).actors().stream()
                .filter(a -> a.actorId().equals(takeActor)).findFirst().orElseThrow();
        var held = draft.actors().stream().filter(a -> a.id().equals(takeActor)).findFirst().orElseThrow()
                .keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
        var key = new ActorKey(frame, pose.x() + (right ? 2 : 0) - (left ? 2 : 0),
                pose.y() + (down ? 2 : 0) - (up ? 2 : 0), pose.animation(), pose.pose(), (left ? Facing.LEFT : right ? Facing.RIGHT : held.facing()), pose.visible(), pose.scale(), Interpolation.HOLD);
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
        var document = previewDocument();
        var frame = resolvePreview(document, controller.frame());
        if(picker!=null){
            var cursorView=picker.camera();
            var view=new StageplayTimeline.CameraPose(cursorView.x(),cursorView.y(),cursorView.zoom());
            var draft=new StageplayTimeline.Frame(frame.frame(),frame.actors(),view,frame.captions(),frame.set(),frame.transition(),view,frame.dialogue(),frame.puppets(),frame.world());
            try { var resolved=host.resolvePreview(document,assetRoot,draft);picker.resolvedCamera(new StageplayPositionPicker.View(resolved.camera().x(),resolved.camera().y(),resolved.camera().zoom()));return resolved; }
            catch(IOException failure){report(failure);return frame;}
        }
        if (cameraDraft == null || controller.playing()) return frame;
        // Boundary clipping may suppress some native shake. A moved draft still uses
        // the authored seeded motion, then lets the preview owner bound both layers.
        var authored = StageplayTimeline.sample(document, frame.frame());
        var shaken = new StageplayTimeline.CameraPose(cameraDraft.x()+authored.camera().x()-authored.baseCamera().x(),
                cameraDraft.y()+authored.camera().y()-authored.baseCamera().y(),cameraDraft.zoom());
        var draft = new StageplayTimeline.Frame(frame.frame(),frame.actors(),shaken,frame.captions(),frame.set(),frame.transition(),cameraDraft,frame.dialogue(),frame.puppets(),frame.world());
        try { return Objects.requireNonNull(host.resolvePreview(document, assetRoot, draft), "preview draft"); }
        catch (IOException | RuntimeException failure) {
            previewFailure(failure); cameraDraft = null;
            return frame;
        }
    }

    private void invalidatePreview() { previewSource=null; resolvedPreview=null; previewError=null; previewAt=-1; }

    private StageplayTimeline.Frame resolvePreview(StageplayDocument document,int frame) {
        if (resolvedPreview != null && frame==previewAt && assetRoot.equals(previewRoot) && document.equals(previewSource)) return resolvedPreview;
        previewSource=document; previewRoot=assetRoot; previewAt=frame; previewError=null;
        try { resolvedPreview=Objects.requireNonNull(host.resolvePreview(document,assetRoot,frame),"preview frame"); }
        catch (IOException | RuntimeException failure) {
            previewFailure(failure);
            // Keep authoring available without claiming the unresolved placement is a simulated result.
            resolvedPreview=StageplayTimeline.sample(document,frame);
        }
        return resolvedPreview;
    }

    private void previewFailure(Exception failure) {
        controller.pause(); startedThisTick=false; previewSoundActive=false;
        try { host.stop(); } catch (RuntimeException cleanup) { if (cleanup!=failure) failure.addSuppressed(cleanup); }
        previewError=(failure.getMessage() == null ? UiMessages.text("editor.stageplay_workspace.puppet_preview_unavailable_1c1jcvp", Map.of("value0", String.valueOf((failure.getClass().getSimpleName())))) : UiMessages.text("editor.stageplay_workspace.puppet_preview_unavailable_1c1jcvp", Map.of("value0", String.valueOf((failure.getMessage())))));
        status=previewError;
    }

    public StageplayTimeline.ActorPose selectedActorPose() {
        String id=controller.selectedActorId();
        return previewFrame().actors().stream().filter(a -> a.actorId().equals(id)).findFirst().orElse(null);
    }

    /** Shared readout for both frontends; goals are never reported as actual positions. */
    public String selectedActorStatus() {
        if (controller.selectedActorId().isEmpty()) return UiMessages.text("editor.stageplay_workspace.select_a_cast_member_1cwgajc");
        var frame=previewFrame();
        if (previewError!=null) return previewError;
        var actor=frame.actors().stream().filter(a -> a.actorId().equals(controller.selectedActorId())).findFirst().orElse(null);
        if (actor==null) return UiMessages.text("editor.stageplay_workspace.select_a_cast_member_1cwgajc");
        var sample=frame.puppets().stream().filter(a -> a.actorId().equals(actor.actorId())).findFirst().orElse(null);
        if (sample==null) return UiMessages.text("editor.stageplay_workspace.poser_x_y_i0793", Map.of("value0", String.valueOf(coordinate(actor.x())), "value1", String.valueOf(coordinate(actor.y()))));
        if (sample.status().name().equals("UNRESOLVED")) return UiMessages.text("editor.stageplay_workspace.puppet_preview_unavailable_s4kylo");
        if (sample.status().name().equals("INACTIVE")) return UiMessages.text("editor.stageplay_workspace.inactive_hidden_for_this_shot_12u672t");
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
            throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.puppet_preview_must_be_available_before_recording_this_na404i"));
        if (sample.status().name().equals("INACTIVE"))
            throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.make_this_actor_visible_during_the_shot_before_recordin_17lixvc"));
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
            if (hold<1) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.leave_one_released_frame_between_jump_presses_hoypwq"));
            // Native jump needs a released tick before another press, including imported earlier holds.
            for (int i=0;i<commands.size();i++) {
                var c=commands.get(i);
                if (c.kind()!=PuppetCommandKind.JUMP || c.frame()>=frame || c.frame()+c.holdFrames()<frame) continue;
                int previousHold=frame-c.frame()-1;
                if (previousHold<1) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.leave_one_released_frame_between_jump_presses_hoypwq"));
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
            throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.camera_movement_and_zoom_must_be_finite_zoom_must_be_po_1ds8gjo"));
        if (screenX == 0 && screenY == 0 && zoomFactor == 1) return;
        var camera = directorCamera();
        double zoom = zoomFactor == 1 ? camera.zoom() : Math.clamp(Math.round(camera.zoom() * zoomFactor), 1, 64);
        // Keep the film centre steady while zooming; movement is measured in visible pixels.
        double x = camera.x() + screenX / camera.zoom() + controller.document().width() * (.5 / camera.zoom() - .5 / zoom);
        double y = camera.y() + screenY / camera.zoom() + controller.document().height() * (.5 / camera.zoom() - .5 / zoom);
        cameraDraft = new StageplayTimeline.CameraPose(Math.clamp(x, -1_048_576, 1_048_576),
                Math.clamp(y, -1_048_576, 1_048_576), zoom);
        status = UiMessages.text("editor.stageplay_workspace.camera_framing_preview_k_captures_a_key_r_records_motio_i6z5o0");
    }

    private void captureCameraKey() {
        var camera = integerCamera(directorCamera());
        var key = new CameraKey(controller.frame(), camera.x(), camera.y(), camera.zoom(), Interpolation.LINEAR);
        edit(d -> d.withCamera(withCameraKey(d.camera(), key)));
        cameraDraft = null;
        status = UiMessages.text("editor.stageplay_workspace.camera_key_captured_at_frame_later_keys_animate_framing_9lmdnu", Map.of("value0", String.valueOf(controller.frame())));
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
        status = UiMessages.text("editor.menu.stageplay_workspace.recording_a_camera_take_one_take_is_one_undo_the_prompt_bar_lists_its_keys");
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
        cancelRoute();
        appliedOverlay=null;
        controller.replace(host.initialDocument());
        cameraDraft = null; cameraMode = false;
        host.refreshResources();
        invalidatePreview();
        assetRoot = host.initialAssetRoot().toAbsolutePath().normalize(); path = null; initialized = true;
        status = UiMessages.text("editor.stageplay_workspace.new_scene_add_a_cast_member_then_set_keys_at_timeline_f_1fk06rc");
    }

    private void beforeReplace(Runnable next) {
        if (!controller.dirty()) { next.run(); return; }
        menu.showActions(UiMessages.text("editor.stageplay_workspace.unsaved_stageplay_scene_19h0vnw"), List.of(
                action(UiMessages.text("editor.stageplay_workspace.save_and_continue_1cnrwxf"), () -> save(next)), new EditorProjectMenu.Action(UiMessages.text("editor.stageplay_workspace.discard_scene_changes_15osvm5"), UiMessages.text("editor.menu.stageplay_workspace.unsaved_scene_edits_are_lost"), next),
                action(UiMessages.text("editor.stageplay_workspace.keep_editing_1jzrdi5"), () -> { })));
    }

    /** Called by the host before quitting; cancellation leaves both workspaces alive. */
    public void requestQuit(Runnable next) { stop(); beforeReplace(next); }

    private void openForm() {
        menu.showForm(EditorProjectMenu.PresentationKind.OPEN_SCENE, UiMessages.text("editor.stageplay_workspace.open_stageplay_z1fduk"), List.of(file("path", UiMessages.text("editor.stageplay_workspace.scene_1bgajw"), "projects/")), UiMessages.text("editor.stageplay_workspace.open_1gtii"), values -> io(() -> open(Path.of(values.get("path")))));
    }

    public void open(Path source) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        StageplayDocument candidate = StageplayStore.read(absolute);
        // Parse first. Missing ROMs still permit repairing the document in either editor.
        cancelRoute(); appliedOverlay=null; stop(); controller.replace(candidate); path = absolute; assetRoot = absolute.getParent();
        cameraDraft = null; cameraMode = false;
        host.refreshResources();
        invalidatePreview();
        initialized = true; active = true; status = UiMessages.text("editor.stageplay_workspace.opened_17wjsre", Map.of("value0", String.valueOf(absolute)));
    }

    private void importScreenplayForm() {
        menu.showForm(EditorProjectMenu.PresentationKind.IMPORT_SCREENPLAY, UiMessages.text("editor.stageplay_workspace.import_screenplay_zvmokb"), List.of(file("path", UiMessages.text("editor.stageplay_workspace.screenplay_json_1y3whfs"), assetRoot.toString())),
                UiMessages.text("editor.stageplay_workspace.import_10a9v45"), values -> io(() -> importScreenplay(Path.of(values.get("path")))));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.read_a_shot_local_screenplay_as_a_new_unsaved_scene_exi_jv65rv"));
    }

    /** Parse completely before stopping playback, releasing a live lease or replacing authoring state. */
    public void importScreenplay(Path source) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(absolute) > StageplayScriptCompiler.MAX_BYTES)
            throw new IOException(UiMessages.text("editor.stageplay_workspace.screenplay_input_must_be_a_regular_file_no_larger_than_1rttyb9"));
        StageplayDocument candidate;
        try (var input = Files.newInputStream(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            candidate = StageplayScriptCompiler.compile(input.readNBytes(StageplayScriptCompiler.MAX_BYTES + 1));
        }
        host.finishPendingEdits();
        stop(); // A pending take becomes an ordinary protected edit before asking to replace it.
        beforeReplace(() -> {
            cancelRoute(); appliedOverlay=null; controller.replace(candidate); controller.markUnsaved();
            path = null; assetRoot = absolute.getParent();
            cameraDraft = null; cameraMode = false;
            host.refreshResources(); invalidatePreview();
            initialized = true; active = true;
            status = UiMessages.text("editor.stageplay_workspace.imported_save_creates_a_separate_stageplay_scene_19kmaor", Map.of("value0", String.valueOf(absolute)));
        });
    }

    private void exportScreenplayForm() {
        Path suggested = assetRoot.resolve(slug(controller.document().name()) + ".screenplay.json");
        menu.showForm(EditorProjectMenu.PresentationKind.EXPORT_SCREENPLAY, UiMessages.text("editor.stageplay_workspace.export_screenplay_1ukin3g"), List.of(file("path", UiMessages.text("editor.stageplay_workspace.new_screenplay_json_93aevc"), suggested.toString())),
                UiMessages.text("editor.stageplay_workspace.export_yk542c"), values -> io(() -> exportScreenplay(Path.of(values.get("path")))));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.writes_a_new_screenplay_without_replacing_files_or_chan_1g4d73z"));
    }

    /** Export the current authored preview without moving its assets or marking the scene saved. */
    public void exportScreenplay(Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        if (target.getParent() == null) throw new IOException(UiMessages.text("editor.stageplay_workspace.screenplay_destination_directory_is_missing_yhhnp1"));
        StageplayDocument candidate = rebase(recordingDocument(), assetRoot, target.getParent());
        byte[] bytes = StageplayScriptDecompiler.decompile(candidate);
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), ".stageplay-screenplay-", ".tmp");
        try {
            Files.write(staged, bytes);
            // Fail-if-existing atomic publication: no truncation, symlink following or source overwrite.
            Files.createLink(target, staged);
        } finally { Files.deleteIfExists(staged); }
        status = UiMessages.text("editor.stageplay_workspace.exported_screenplay_yj9k3a", Map.of("value0", String.valueOf(target)));
    }

    private void save(Runnable next) {
        if (path == null) { saveAs(next); return; }
        io(() -> { saveTo(path); if (next != null) next.run(); });
    }

    private void saveAs(Runnable next) {
        String suggested = path == null ? assetRoot.resolve(slug(controller.document().name()) + ".stageplay").toString() : path.toString();
        menu.showForm(EditorProjectMenu.PresentationKind.SAVE_SCENE, UiMessages.text("editor.stageplay_workspace.save_stageplay_mzd5jj"), List.of(file("path", UiMessages.text("editor.stageplay_workspace.scene_file_1uepcc0"), suggested)), UiMessages.text("editor.stageplay_workspace.save_1j2ql"), values -> {
            Path target = Path.of(values.get("path")).toAbsolutePath().normalize();
            if (!target.getFileName().toString().endsWith(".stageplay")) target = target.resolveSibling(target.getFileName() + ".stageplay");
            Path destination = target;
            Runnable write = () -> io(() -> { saveTo(destination); if (next != null) next.run(); });
            if (Files.exists(target) && !target.equals(path)) menu.showActions(UiMessages.text("editor.stageplay_workspace.replace_existing_scene_f4v4d8"), List.of(
                    new EditorProjectMenu.Action(UiMessages.text("editor.stageplay_workspace.replace_10l95tb", Map.of("value0", target.getFileName())), UiMessages.text("editor.menu.stageplay_workspace.overwrites_the_existing_file"), write), action(UiMessages.text("editor.stageplay_workspace.cancel_x9d2fu"), () -> { })));
            else write.run();
        });
    }

    public void saveTo(Path destination) throws IOException {
        stop();
        Path target = destination.toAbsolutePath().normalize();
        if (!assetRoot.equals(target.getParent()) && controller.anyRevision(StageplayWorkspace::hasRelativeAssets))
            throw new IOException(UiMessages.text("editor.stageplay_workspace.this_scene_s_undo_history_uses_relative_assets_save_bes_jqbu6q"));
        StageplayDocument candidate = rebase(controller.document(), assetRoot, target.getParent());
        Files.createDirectories(target.getParent());
        StageplayStore.write(target, candidate);
        // Rebasing is a document edit, preserving existing undo history and meaningful dirty state.
        controller.edit(ignored -> candidate); controller.markSaved();
        path = target; assetRoot = target.getParent(); status = UiMessages.text("editor.stageplay_workspace.saved_1lyob3w", Map.of("value0", String.valueOf(target)));
    }

    private static StageplayDocument rebase(StageplayDocument doc, Path oldRoot, Path newRoot) throws IOException {
        if (!oldRoot.equals(newRoot) && hasRelativeAssets(doc))
            throw new IOException(UiMessages.text("editor.stageplay_workspace.save_beside_the_existing_scene_assets_to_move_the_proje_1lt44zq"));
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
        if (!absolute.startsWith(newRoot)) throw new IOException(UiMessages.text("editor.stageplay_workspace.keep_the_scene_beside_its_assets_or_copy_the_assets_und_154cqqj", Map.of("value0", String.valueOf(reference))));
        return newRoot.relativize(absolute).toString().replace('\\', '/');
    }

    private void sceneForm() {
        var doc = controller.document(); var set = doc.set();
        menu.showForm(UiMessages.text("editor.stageplay_workspace.stageplay_scene_set_o2citr"), List.of(text("name", UiMessages.text("editor.stageplay_workspace.name_1fvl7"), doc.name()), number("duration", UiMessages.text("editor.stageplay_workspace.length_60_hz_frames_1pxsfcd"), doc.durationFrames()),
                number("width", UiMessages.text("editor.stageplay_workspace.width_1dra92"), doc.width()), number("height", UiMessages.text("editor.stageplay_workspace.height_zop8w7"), doc.height()), text("game", UiMessages.text("editor.stageplay_workspace.set_game_s1_s2_s3k_1jljy9i"), set.gameId()),
                number("zone", UiMessages.text("editor.stageplay_workspace.zone_zero_based_161fw8q"), set.zone()), number("act", UiMessages.text("editor.stageplay_workspace.act_zero_based_5bbbpw"), set.act()),
                text("rom", UiMessages.text("editor.stageplay_workspace.optional_rom_sha_1_rz5an4"), set.romSha1()), text("project", UiMessages.text("editor.stageplay_workspace.relative_ogfproject_optional_1hbpjrw"), set.levelProject())),
                UiMessages.text("editor.stageplay_workspace.apply_scene_17kyb7e"), v -> edit(d -> d.withName(v.get("name")).withDurationFrames(integer(v,"duration"))
                        .withSize(integer(v,"width"), integer(v,"height"))
                        .withSet(new SetSource(v.get("game"),integer(v,"zone"),integer(v,"act"),v.get("rom"),v.get("project")))));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.shortening_requires_deleting_out_of_range_keys_and_cues_d18bs8"));
    }

    private void actorForm(Actor actor) {
        boolean add=actor==null;
        var oldTrack=add?null:puppet(controller.document(),actor.id());
        var fields=new ArrayList<>(List.of(text("name",UiMessages.text("editor.stageplay_workspace.name_1fvl7"),add?"Sonic":actor.name()),
                text("game",UiMessages.text("editor.stageplay_workspace.rom_s1_s2_s3k_8yu1ee"),add?controller.document().set().gameId():actor.gameId()),
                text("character",UiMessages.text("editor.stageplay_workspace.character_1n0e4nd"),add?"sonic":actor.character()),
                text("mode",UiMessages.text("editor.stageplay_workspace.performance_puppet_poser_1l04nsq"),add || oldTrack!=null?"PUPPET":"POSER"),
                text("snap",UiMessages.text("editor.stageplay_workspace.start_on_floor_true_false_kz0hnz"),Boolean.toString(oldTrack==null || oldTrack.snapToFloor())),
                number("collision",UiMessages.text("editor.stageplay_workspace.terrain_collision_path_0_1_fqb2an"),oldTrack==null?0:oldTrack.collisionPath())));
        if (add) {
            var camera=StageplayTimeline.sample(controller.document(),0).baseCamera();
            fields.add(number("x",UiMessages.text("editor.stageplay_workspace.initial_world_centre_x_1wet50d"),camera.x()+controller.document().width()/2.0));
            fields.add(number("y",UiMessages.text("editor.stageplay_workspace.initial_world_centre_y_near_floor_e8m3lb"),camera.y()+controller.document().height()/2.0));
        }
        menu.showForm(add ? EditorProjectMenu.PresentationKind.ADD_ACTOR : EditorProjectMenu.PresentationKind.ACTOR_PROPERTIES, (add ? UiMessages.text("editor.stageplay_workspace.add_actor_8pm4k6") : UiMessages.text("editor.stageplay_workspace.actor_properties_1777ala")),fields,UiMessages.text("editor.stageplay_workspace.apply_actor_17b2boj"),v -> {
            String mode=v.get("mode").toUpperCase(java.util.Locale.ROOT);
            if (!mode.equals("PUPPET") && !mode.equals("POSER")) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.performance_must_be_puppet_or_poser_6akrq2"));
            boolean physical=mode.equals("PUPPET");
            String id=add?nextId("actor",controller.document()):actor.id();
            var keys=new ArrayList<ActorKey>();
            if (add) {
                var frames=new ArrayList<Integer>(); frames.add(0); controller.document().sceneChanges().forEach(c -> frames.add(c.frame()));
                for (int frame:frames) {
                    var camera=StageplayTimeline.sample(controller.document(),frame).baseCamera();
                    keys.add(new ActorKey(frame,frame==0?decimal(v,"x"):camera.x()+controller.document().width()/2.0,
                            frame==0?decimal(v,"y"):camera.y()+controller.document().height()/2.0,
                            0,-1, Facing.RIGHT,true,1,Interpolation.HOLD));
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
                menu.showActions(UiMessages.text("editor.stageplay_workspace.change_actor_performance_17c2aay"),List.of(
                        action((physical ? UiMessages.text("editor.stageplay_workspace.use_puppet_replace_poser_motion_keys_1o5p4ap") : UiMessages.text("editor.stageplay_workspace.use_poser_remove_puppet_commands_xj5wh1")),apply),
                        action(UiMessages.text("editor.stageplay_workspace.keep_current_performance_155ccge"),() -> { })));
                menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.this_is_one_undoable_change_puppet_keeps_shot_placement_1ampunj"));
            } else apply.run();
        });
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.puppet_uses_native_movement_and_jumps_floor_snap_attach_1i00qqw"));
    }

    private static StageplayDocument withCast(StageplayDocument d,List<Actor> actors,List<PuppetTrack> puppets) {
        return new StageplayDocument(d.version(),d.name(),d.durationFrames(),d.width(),d.height(),d.set(),actors,d.camera(),
                d.captions(),d.audio(),d.cameraEffects(),d.sceneChanges(),d.dialogueTypers(),d.dialogueSkins(),d.dialogue(),puppets,
                d.objectActors(),d.worldCommands(),d.voices());
    }

    /** Native physics owns motion, scale and animation; authored keys retain shot placement and visibility. */
    private static Actor physicalKeys(Actor actor,List<SceneChange> changes) {
        var boundaries=new java.util.HashSet<Integer>(); boundaries.add(0); changes.forEach(c -> boundaries.add(c.frame()));
        var keys=new ArrayList<ActorKey>(); ActorKey anchor=actor.keys().getFirst();
        for (var key:actor.keys()) {
            if (boundaries.contains(key.frame())) anchor=key;
            keys.add(new ActorKey(key.frame(),anchor.x(),anchor.y(),0,-1, anchor.facing(),key.visible(),1,Interpolation.HOLD));
        }
        return new Actor(actor.id(),actor.name(),actor.gameId(),actor.character(),keys);
    }

    private void puppetMenu() {
        var actor=selectedActor(); var track=puppet(controller.document(),actor.id());
        if (track==null) throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.choose_puppet_in_actor_properties_to_direct_native_move_rye5dk"));
        var actions=new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_workspace.move_to_destination_1ra2wag"),() -> puppetCommandForm(actor, null,PuppetCommandKind.MOVE_TO)));
        actions.add(action(UiMessages.text("editor.stageplay_workspace.jump_1dqha"),() -> puppetCommandForm(actor,null,PuppetCommandKind.JUMP)));
        actions.add(action(UiMessages.text("editor.stageplay_workspace.stop_brake_1cgd0xo"),() -> puppetCommandForm(actor,null,PuppetCommandKind.STOP)));
        {
            for (var kind : List.of(PuppetCommandKind.WAIT, PuppetCommandKind.RELEASE, PuppetCommandKind.CROUCH,
                    PuppetCommandKind.LOOK_UP, PuppetCommandKind.ROLL))
                actions.add(action(kind.name(), EditorLabels.value(kind.name()), false, () -> puppetCommandForm(actor, null, kind)));
            if (actor.gameId().equals("s3k") && actor.character().equals("tails")) {
                actions.add(action(UiMessages.text("editor.stageplay_workspace.fly_toward_target_8i8099"), () -> puppetCommandForm(actor, null, PuppetCommandKind.FLY_TO)));
                actions.add(action(UiMessages.text("editor.stageplay_workspace.hover_bounded_1ddwcb4"), () -> puppetCommandForm(actor, null, PuppetCommandKind.HOVER)));
            }
            if (actor.gameId().equals("s3k") && actor.character().equals("knuckles"))
                actions.add(action(UiMessages.text("editor.stageplay_workspace.glide_toward_target_3bns3j"), () -> puppetCommandForm(actor, null, PuppetCommandKind.GLIDE_TO)));
        }
        actions.add(action(UiMessages.text("editor.stageplay_workspace.spawn_current_shot_placement_na9kos"), UiMessages.text("editor.stageplay.presentation.action_place_actor"), false,() -> puppetPlacementForm(actor)));
        actions.add(action(UiMessages.text("editor.stageplay_workspace.visibility_at_playhead_h1ne5v"), UiMessages.text("editor.stageplay.presentation.action_actor_visibility"), false,() -> puppetVisibilityForm(actor)));
        for (var command:track.commands()) {
            actions.add(action(command.frame()+": "+command.kind(), command.frame()+": "+EditorLabels.value(command.kind().name()), false,() -> {
                var editAction=action(UiMessages.text("editor.stageplay_workspace.edit_1a6ui"),() -> puppetCommandForm(actor,command,command.kind()));
                var deleteAction=action(UiMessages.text("editor.stageplay_workspace.delete_xskn2j"),() -> edit(d -> {
                    var current=puppet(d,actor.id());
                    return replacePuppet(d,new PuppetTrack(actor.id(),current.snapToFloor(),current.collisionPath(),
                            current.commands().stream().filter(c -> !c.id().equals(command.id())).toList()));
                }));
                menu.showActions(UiMessages.text("editor.stageplay_workspace.actor_action_3t7c9t"),List.of(editAction,deleteAction));
            }));
        }
        menu.showActions(EditorProjectMenu.PresentationKind.DIRECT_ACTOR, UiMessages.text("editor.stageplay_workspace.direct_11g05p6", Map.of("value0", String.valueOf(actor.name()))),actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.goals_are_attempts_not_teleports_jump_clears_obstacles_31a40b", Map.of("value0", String.valueOf(selectedActorStatus()))));
    }

    private void puppetCommandForm(Actor actor,PuppetCommand command,PuppetCommandKind kind) {
        var pose=selectedActorPose(); int frame=command==null?controller.frame():command.frame();
        boolean move=kind==PuppetCommandKind.MOVE_TO || kind==PuppetCommandKind.FLY_TO || kind==PuppetCommandKind.GLIDE_TO || kind==PuppetCommandKind.SPINDASH;
        boolean hold=kind==PuppetCommandKind.JUMP || kind==PuppetCommandKind.HOVER || kind==PuppetCommandKind.CROUCH || kind==PuppetCommandKind.LOOK_UP;
        boolean target=move || kind==PuppetCommandKind.WAIT;
        var fields=new ArrayList<EditorProjectMenu.Field>(); fields.add(number("frame",UiMessages.text("editor.stageplay_workspace.earliest_frame_3w9hfk"),frame));
        if(move) {
            fields.add(number("x",UiMessages.text("editor.stageplay_workspace.destination_centre_x_n6ukw5"),command==null?(pose==null?actor.keys().getFirst().x():pose.x())+96:command.x()));
            fields.add(number("y",UiMessages.text("editor.stageplay_workspace.destination_centre_y_n6ukw6"),command==null?(pose==null?actor.keys().getFirst().y():pose.y()):command.y()));
            fields.add(number("urgency",UiMessages.text("editor.stageplay_workspace.urgency_0_1_o42u9j"),command==null?.65:command.urgency()));
        }
        if(hold) {
            int nextJump=puppet(controller.document(),actor.id()).commands().stream()
                    .filter(c -> c.kind()==PuppetCommandKind.JUMP && c.frame()>frame).mapToInt(PuppetCommand::frame).min()
                    .orElse(controller.document().durationFrames()+1);
            int defaultHold=Math.max(1,Math.min(kind==PuppetCommandKind.JUMP?12:60,shotEnd(controller.document(),frame)-frame));
            if(kind==PuppetCommandKind.JUMP) defaultHold=Math.max(1,Math.min(defaultHold,nextJump-frame-1));
            fields.add(number("hold",UiMessages.text("editor.stageplay_workspace.button_intent_duration_frames_z2dhhc"),command==null?defaultHold:command.holdFrames()));
        }
        fields.add(number("timeout",UiMessages.text("editor.stageplay_workspace.timeout_wait_requires_1_36000_5prha5"),command==null?(kind==PuppetCommandKind.WAIT?180:0):command.timeoutFrames()));
        if(kind==PuppetCommandKind.WAIT) fields.add(text("condition",UiMessages.text("editor.stageplay_workspace.arrived_on_object_grounded_airborne_hurt_recovered_ring_fdgzu4"),command==null?"GROUNDED":command.condition().name()));
        if(target) {
            var old=command==null?ObjectTarget.none():command.target();
            fields.add(number("layout",UiMessages.text("editor.stageplay_workspace.rom_placement_layoutindex_1_coordinate_1whe33r"),old.layoutIndex()));
            fields.add(number("object",UiMessages.text("editor.stageplay_workspace.expected_objectid_olv56q"),old.objectId())); fields.add(number("subtype",UiMessages.text("editor.stageplay_workspace.expected_subtype_8t82lu"),old.subtype()));
            fields.add(number("offsetX",UiMessages.text("editor.stageplay_workspace.target_local_offset_x_p79u27"),old.offsetX())); fields.add(number("offsetY",UiMessages.text("editor.stageplay_workspace.target_local_offset_y_p79u28"),old.offsetY()));
        }
        menu.showForm(StageplayMenuPresentation.nativeIntentPresentation(kind), (kind == PuppetCommandKind.MOVE_TO ? ((UiMessages.text("editor.stageplay_workspace.move_to_destination_1ra2wag"))) : (kind == PuppetCommandKind.JUMP ? ((((UiMessages.text("editor.stageplay_workspace.native_jump_1jkrxiv"))))) : (kind == PuppetCommandKind.STOP ? ((((((UiMessages.text("editor.stageplay_workspace.stop_brake_1cgd0xo"))))))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.native_intent_1t9s372", Map.of("value0", String.valueOf(kind)))))))))),fields,UiMessages.text("editor.stageplay_workspace.apply_action_1ryyy4o"),v -> edit(d -> {
            var track=puppet(d,actor.id()); var commands=new ArrayList<>(track.commands());
            String id=command==null?nextId("action",d):command.id(); commands.removeIf(c -> c.id().equals(id));
            commands.add(new PuppetCommand(id,integer(v,"frame"),kind,move?decimal(v,"x"):0,move?decimal(v,"y"):0,
                    move?decimal(v,"urgency"):0,kind==PuppetCommandKind.SPINDASH?StageplayRouteSteering.CHARGE_FRAMES:hold?integer(v,"hold"):0,
                    target?new ObjectTarget(integer(v,"layout"),integer(v,"object"),integer(v,"subtype"),decimal(v,"offsetX"),decimal(v,"offsetY")):ObjectTarget.none(),
                    kind==PuppetCommandKind.WAIT?WaitCondition.valueOf(v.get("condition").toUpperCase(java.util.Locale.ROOT)):WaitCondition.NONE,
                    integer(v,"timeout"),command==null?RouteMotion.NATIVE:command.motion()));
            commands.sort(Comparator.comparingInt(PuppetCommand::frame));
            return replacePuppet(d,new PuppetTrack(actor.id(),track.snapToFloor(),track.collisionPath(),commands));
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.native_buttons_own_movement_and_abilities_wait_gates_la_hy5p72"));
    }

    private void puppetPlacementForm(Actor actor) {
        int frame=controller.document().sceneChanges().stream().filter(c -> c.frame()<=controller.frame()).mapToInt(SceneChange::frame).max().orElse(0);
        var key=actor.keys().stream().filter(k -> k.frame()==frame).findFirst().orElseThrow();
        menu.showForm(EditorProjectMenu.PresentationKind.ACTOR_POSITION, UiMessages.text("editor.stageplay_workspace.shot_placement_at_frame_qo0nia", Map.of("value0", String.valueOf(frame))),List.of(number("x",UiMessages.text("editor.stageplay_workspace.world_centre_x_cjsxmp"),key.x()),number("y",UiMessages.text("editor.stageplay_workspace.world_centre_y_floor_search_1uon4tn"),key.y()),
                new EditorProjectMenu.Field("facing",UiMessages.text("editor.stageplay_workspace.facing_yob1fe"),display(key.facing()),List.of("Left","Right"))),UiMessages.text("editor.stageplay_workspace.apply_placement_13fxtw3"),v -> edit(d -> {
            var replacement=new ActorKey(frame,decimal(v,"x"),decimal(v,"y"),0,-1, Facing.valueOf(v.get("facing").toUpperCase(java.util.Locale.ROOT)),key.visible(),1,Interpolation.HOLD);
            var candidate=new Actor(actor.id(),actor.name(),actor.gameId(),actor.character(),withKey(actor.keys(),replacement));
            var normalized=physicalKeys(candidate,d.sceneChanges());
            return d.withActors(d.actors().stream().map(a -> a.id().equals(actor.id())?normalized:a).toList());
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.placement_resets_the_actor_at_the_start_of_this_shot_fl_3z5kbx"));
    }

    private void puppetVisibilityForm(Actor actor) {
        int frame=controller.frame();
        var key=actor.keys().stream().filter(k -> k.frame()<=frame).reduce((a,b)->b).orElseThrow();
        menu.showForm(EditorProjectMenu.PresentationKind.ACTOR_VISIBILITY, UiMessages.text("editor.stageplay_workspace.visibility_at_frame_tnxfyt", Map.of("value0", String.valueOf(frame))),List.of(text("visible",UiMessages.text("editor.stageplay_workspace.visible_true_false_16cegrc"),Boolean.toString(key.visible()))),UiMessages.text("editor.stageplay_workspace.apply_visibility_1fhy52c"),v -> edit(d ->
                replaceActor(d,actor,withKey(actor.keys(),new ActorKey(frame,key.x(),key.y(),0,-1, key.facing(),bool(v,"visible"),1,Interpolation.HOLD)))));
    }

    private Actor selectedActor() {
        return controller.document().actors().stream().filter(a -> a.id().equals(controller.selectedActorId())).findFirst()
                .orElseThrow(() -> new IllegalStateException(UiMessages.text("editor.stageplay_workspace.select_a_cast_member_first_js8q20")));
    }

    private void keyForm() {
        Actor actor = selectedActor(); int frame = controller.frame();
        var pose = StageplayTimeline.sample(controller.document(), frame).actors().stream().filter(a -> a.actorId().equals(actor.id())).findFirst().orElseThrow();
        var held = actor.keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
        var interpolation = held.interpolation();
        menu.showForm(UiMessages.text("editor.stageplay_workspace.actor_key_at_frame_8p6ho7", Map.of("value0", String.valueOf(frame))), List.of(number("x",UiMessages.text("editor.stageplay_workspace.world_centre_x_cjsxmp"),pose.x()),number("y",UiMessages.text("editor.stageplay_workspace.world_centre_y_cjsxmq"),pose.y()),
                number("animation",UiMessages.text("editor.stageplay_workspace.animation_1fn1jdw"),pose.animation()),number("pose",UiMessages.text("editor.stageplay_workspace.pose_frame_1_animation_151w21q"),pose.pose()),
                new EditorProjectMenu.Field("facing",UiMessages.text("editor.stageplay_workspace.facing_yob1fe"),display(held.facing()),List.of("Left","Right")), text("visible",UiMessages.text("editor.stageplay_workspace.visible_true_false_16cegrc"),Boolean.toString(pose.visible())),
                number("scale",UiMessages.text("editor.stageplay_workspace.scale_1bg7je"),pose.scale()),text("interpolation",UiMessages.text("editor.stageplay_workspace.hold_linear_smooth_pvczm0"),interpolation.name())), UiMessages.text("editor.stageplay_workspace.set_key_1obzb0h"), v -> {
            var key = new ActorKey(frame,decimal(v,"x"),decimal(v,"y"),integer(v,"animation"),integer(v,"pose"), Facing.valueOf(v.get("facing").toUpperCase(java.util.Locale.ROOT)),bool(v,"visible"),decimal(v,"scale"),Interpolation.valueOf(v.get("interpolation").toUpperCase(java.util.Locale.ROOT)));
            edit(d -> replaceActor(d,actor,withKey(actor.keys(),key)));
        });
    }

    private void deleteKey() {
        Actor actor = selectedActor(); int frame = controller.frame();
        var track=puppet(controller.document(),actor.id());
        if (track!=null) {
            var actions=new ArrayList<EditorProjectMenu.Action>();
            for (var command:track.commands()) if (command.frame()==frame) actions.add(action(UiMessages.text("editor.stageplay_workspace.remove_koj3xb", Map.of("value0", String.valueOf(command.kind()))),() -> edit(d -> {
                var current=puppet(d,actor.id()); return replacePuppet(d,new PuppetTrack(actor.id(),current.snapToFloor(),current.collisionPath(),
                        current.commands().stream().filter(c -> !c.id().equals(command.id())).toList()));
            })));
            if (frame!=0 && !isSceneBoundary(frame) && actor.keys().stream().anyMatch(k -> k.frame()==frame))
                actions.add(action(UiMessages.text("editor.stageplay_workspace.remove_visibility_key_1gnl07h"),() -> edit(d -> replaceActor(d,actor,actor.keys().stream().filter(k -> k.frame()!=frame).toList()))));
            if (actions.isEmpty()) throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.no_actor_action_or_removable_visibility_key_at_this_fra_1pct6jv"));
            menu.showActions(UiMessages.text("editor.stageplay_workspace.remove_actor_action_1urwud9"),actions); return;
        }
        if (frame == 0) throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.every_actor_needs_its_frame_zero_key_168g69e"));
        if (isSceneBoundary(frame)) throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.a_scene_change_needs_this_actor_key_remove_the_scene_ch_hmhc1f"));
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
        var actions = new ArrayList<EditorProjectMenu.Action>(List.of(action(UiMessages.text("editor.stageplay_workspace.set_camera_key_1w5tyma"), this::cameraForm),
                action(UiMessages.text("editor.stageplay_workspace.remove_camera_key_a5ihuo"), () -> {
                    if (controller.frame()==0) throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.the_frame_zero_camera_key_is_required_vd6roi"));
                    if (isSceneBoundary(controller.frame())) throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.a_scene_change_needs_this_camera_key_remove_the_scene_c_1drxotj"));
                    edit(d -> d.withCamera(d.camera().stream().filter(k -> k.frame()!=controller.frame()).toList()));
                })));
        for (int zoom : List.of(1, 2, 3, 4))
            actions.add(action(UiMessages.text("editor.stageplay_workspace.capture_x_pixel_framing_1xnrk4r", Map.of("value0", String.valueOf(zoom))), () -> captureZoom(zoom)));
        menu.showActions(EditorProjectMenu.PresentationKind.CAMERA_KEYS, UiMessages.text("editor.stageplay_workspace.camera_at_frame_puklew", Map.of("value0", String.valueOf(controller.frame()))), actions);
    }

    private void captureZoom(int zoom) { moveCamera(0,0,zoom/directorCamera().zoom()); captureCameraKey(); }

    private void cameraForm() {
        int frame = controller.frame();
        var existing=controller.document().camera().stream().filter(k -> k.frame()==frame).findFirst().orElse(null);
        var camera = existing==null ? integerCamera(directorCamera()) : directorCamera();
        var interpolation = controller.document().camera().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow().interpolation();
        var follow=existing==null?CameraFollow.none():existing.follow();
        var resolved = resolvePreview(controller.document(), frame).baseCamera();
        var authored = StageplayTimeline.sample(controller.document(), frame).baseCamera();
        menu.showForm(UiMessages.text("editor.stageplay_workspace.camera_key_at_frame_1bbuh5z", Map.of("value0", String.valueOf(frame))),List.of(number("x",UiMessages.text("editor.stageplay_workspace.world_left_x_vbm7l9"),camera.x()),number("y",UiMessages.text("editor.stageplay_workspace.world_top_y_jgus68"),camera.y()),
                number("zoom",UiMessages.text("editor.stageplay_workspace.zoom_0_05_64_1h36zfx"),camera.zoom()),
                text("interpolation",UiMessages.text("editor.stageplay_workspace.framing_centre_zoom_hold_linear_smooth_fz34g2"),interpolation.name()),
                text("follow",UiMessages.text("editor.stageplay_workspace.follow_actor_ids_comma_separated_empty_authored_6bcmms"),String.join(",",follow.actors())),
                number("deadX",UiMessages.text("editor.stageplay_workspace.horizontal_dead_zone_tce7f0"),follow.deadZoneX()),number("deadY",UiMessages.text("editor.stageplay_workspace.vertical_dead_zone_1jw83xa"),follow.deadZoneY()),
                number("ahead",UiMessages.text("editor.stageplay_workspace.look_ahead_ticks_1dgpw9n"),follow.lookAhead()),number("smooth",UiMessages.text("editor.stageplay_workspace.smoothing_0_001_1_ly8hyn"),follow.smoothing()),
                number("offsetX",UiMessages.text("editor.stageplay_workspace.follow_offset_x_1nzc86y"),follow.offsetX()),number("offsetY",UiMessages.text("editor.stageplay_workspace.follow_offset_y_1nzc86z"),follow.offsetY())),UiMessages.text("editor.stageplay_workspace.set_camera_4gskw3"),v -> edit(d -> {
            double zoom=decimal(v,"zoom");
            var keys = new ArrayList<>(d.camera()); keys.removeIf(k -> k.frame()==frame);
            double x=decimal(v,"x"), y=decimal(v,"y");
            var actors=v.get("follow").isBlank()?List.<String>of():java.util.Arrays.stream(v.get("follow").split(",")).map(String::trim).toList();
            boolean following=!actors.isEmpty();
            var nextFollow=new CameraFollow(actors,decimal(v,"deadX"),decimal(v,"deadY"),decimal(v,"ahead"),decimal(v,"smooth"),
                    decimal(v,"offsetX")+(following?x-resolved.x():0), decimal(v,"offsetY")+(following?y-resolved.y():0));
            // A resolved follow view is not its authored seed. Position edits adjust the follow
            // offset; an unchanged submit must preserve replay's original smoothing history.
            keys.add(new CameraKey(frame,following?(existing==null?authored.x():existing.x()):x,
                    following?(existing==null?authored.y():existing.y()):y,zoom,
                    Interpolation.valueOf(v.get("interpolation").toUpperCase(java.util.Locale.ROOT)),nextFollow));
            keys.sort(Comparator.comparingInt(CameraKey::frame)); return d.withCamera(keys);
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.native_world_camera_following_position_edits_adjust_fol_11vcunz"));
    }

    private boolean isSceneBoundary(int frame) {
        return controller.document().sceneChanges().stream().anyMatch(c -> c.frame() == frame);
    }

    private void filmFormatMenu() {
        menu.showActions(UiMessages.text("editor.stageplay_workspace.film_dimensions_1m19fih"), List.of(
                action(UiMessages.text("editor.stageplay_workspace.widescreen_16_9_384_x_216_15s2bp4"), () -> edit(d -> d.withSize(384,216))),
                action(UiMessages.text("editor.stageplay_workspace.native_320_x_224_y8hg8b"), () -> edit(d -> d.withSize(320,224))),
                action(UiMessages.text("editor.stageplay_workspace.hd_16_9_1280_x_720_14urimr"), () -> edit(d -> d.withSize(1280,720))),
                action(UiMessages.text("editor.stageplay_workspace.custom_dimensions_mga7cs"), this::sceneForm)));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.film_pixels_are_independent_of_window_size_the_preview_2bjsji"));
    }

    private void cameraEffectsMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_workspace.add_shake_quake_anbrxp"), UiMessages.text("editor.stageplay_workspace.add_shake_quake_anbrxp"), true, () -> cameraEffectForm(null)));
        for (var effect : controller.document().cameraEffects()) actions.add(action(effect.startFrame() + ": " + effect.id(),
                () -> menu.showActions(UiMessages.text("editor.stageplay_workspace.camera_effect_1lcgato"), List.of(action(UiMessages.text("editor.stageplay_workspace.edit_1a6ui"), () -> cameraEffectForm(effect)),
                        action(UiMessages.text("editor.stageplay_workspace.delete_xskn2j"), () -> edit(d -> d.withCameraEffects(d.cameraEffects().stream()
                                .filter(e -> !e.id().equals(effect.id())).toList())))))));
        menu.showActions(EditorProjectMenu.PresentationKind.CAMERA_SHAKE, UiMessages.text("editor.stageplay_workspace.camera_shake_quake_1kx3ox"), actions);
    }

    private void cameraEffectForm(CameraEffect effect) {
        int frame = controller.frame();
        menu.showForm(EditorProjectMenu.PresentationKind.CAMERA_SHAKE, UiMessages.text("editor.stageplay_workspace.camera_shake_quake_1kx3ox"), List.of(
                number("start", UiMessages.text("editor.stageplay_workspace.start_frame_17e23gv"), effect == null ? frame : effect.startFrame()),
                number("end", UiMessages.text("editor.stageplay_workspace.end_frame_exclusive_9xyviz"), effect == null ? Math.min(frame+60, controller.document().durationFrames()) : effect.endFrame()),
                number("x", UiMessages.text("editor.stageplay_workspace.horizontal_amplitude_pixels_vtyn2l"), effect == null ? 3 : effect.amplitudeX()),
                number("y", UiMessages.text("editor.stageplay_workspace.vertical_amplitude_pixels_116t22j"), effect == null ? 5 : effect.amplitudeY()),
                number("frequency", UiMessages.text("editor.stageplay_workspace.frequency_hz_0_30_14wpths"), effect == null ? 8 : effect.frequency()),
                number("seed", UiMessages.text("editor.stageplay_workspace.deterministic_seed_eiwwdb"), effect == null ? 1 : effect.seed())), UiMessages.text("editor.stageplay_workspace.apply_effect_1twjsf7"), v -> edit(d -> {
            String id = effect == null ? nextId("shake", d) : effect.id();
            var values = new ArrayList<>(d.cameraEffects()); values.removeIf(e -> e.id().equals(id));
            values.add(new CameraEffect(id, integer(v,"start"), integer(v,"end"), decimal(v,"x"), decimal(v,"y"),
                    decimal(v,"frequency"), integer(v,"seed")));
            values.sort(Comparator.comparingInt(CameraEffect::startFrame));
            return d.withCameraEffects(values);
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.shake_is_layered_over_camera_pan_and_zoom_never_baked_i_16xus07"));
    }

    private void sceneChangesMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_workspace.add_scene_change_1m81m9v"), UiMessages.text("editor.stageplay_workspace.add_scene_change_1m81m9v"), true, () -> sceneChangeForm(null)));
        for (var change : controller.document().sceneChanges()) actions.add(action(change.frame() + ": " + change.transition() + " / " + change.set().gameId(),
                () -> menu.showActions(UiMessages.text("editor.stageplay_workspace.scene_change_vbhj38"), List.of(action(UiMessages.text("editor.stageplay_workspace.edit_1a6ui"), () -> sceneChangeForm(change)),
                        action(UiMessages.text("editor.stageplay_workspace.delete_xskn2j"), () -> edit(d -> withSceneBoundaries(d,d.sceneChanges().stream()
                                .filter(c -> !c.id().equals(change.id())).toList())))))));
        menu.showActions(EditorProjectMenu.PresentationKind.SCENE_CHANGES, UiMessages.text("editor.stageplay_workspace.scene_changes_transitions_12bmha4"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.each_change_starts_a_new_shot_keys_at_its_boundary_belo_3lcajp"));
    }

    private void sceneChangeForm(SceneChange change) {
        var set = change == null ? StageplayTimeline.sample(controller.document(), controller.frame()).set() : change.set();
        menu.showForm(UiMessages.text("editor.stageplay_workspace.scene_change_vbhj38"), List.of(
                number("frame", UiMessages.text("editor.stageplay_workspace.switch_frame_at_least_1_chh377"), change == null ? Math.max(1,controller.frame()) : change.frame()),
                text("transition", UiMessages.text("editor.stageplay_workspace.cut_fade_black_fade_white_wipe_left_wipe_right_1p5jlqz"), change == null ? "CUT" : change.transition().name()),
                number("duration", UiMessages.text("editor.stageplay_workspace.transition_frames_cut_0_1smc67n"), change == null ? 0 : change.durationFrames()),
                text("game", UiMessages.text("editor.stageplay_workspace.incoming_set_s1_s2_s3k_qby5z2"), set.gameId()), number("zone", UiMessages.text("editor.stageplay_workspace.zone_zero_based_161fw8q"), set.zone()),
                number("act", UiMessages.text("editor.stageplay_workspace.act_zero_based_5bbbpw"), set.act()), text("rom", UiMessages.text("editor.stageplay_workspace.optional_rom_sha_1_rz5an4"), set.romSha1()),
                text("project", UiMessages.text("editor.stageplay_workspace.relative_ogfproject_optional_1hbpjrw"), set.levelProject())), UiMessages.text("editor.stageplay_workspace.apply_scene_change_1io2ic6"), v -> edit(d -> {
            String id = change == null ? nextId("scene", d) : change.id();
            var values = new ArrayList<>(d.sceneChanges()); values.removeIf(c -> c.id().equals(id));
            values.add(new SceneChange(id, integer(v,"frame"), new SetSource(v.get("game"), integer(v,"zone"),
                    integer(v,"act"), v.get("rom"), v.get("project")),
                    Transition.valueOf(v.get("transition").toUpperCase(java.util.Locale.ROOT)), integer(v,"duration")));
            values.sort(Comparator.comparingInt(SceneChange::frame));
            return withSceneBoundaries(d, values);
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.transitions_are_centred_on_the_switch_frame_and_cannot_1712gv8"));
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
                var held = actor.keys().stream().filter(k -> k.frame() <= change.frame()).reduce((a,b) -> b).orElseThrow();
                var key = new ActorKey(change.frame(),pose.x(),pose.y(),pose.animation(),pose.pose(), held.facing(),pose.visible(),pose.scale(),Interpolation.HOLD);
                actors.set(i,new Actor(actor.id(),actor.name(),actor.gameId(),actor.character(),withKey(actor.keys(),key)));
            }
        }
        camera.sort(Comparator.comparingInt(CameraKey::frame));
        for (int i=0;i<actors.size();i++) if (puppet(doc,actors.get(i).id())!=null) actors.set(i,physicalKeys(actors.get(i),changes));
        var objects = new ArrayList<ObjectActor>();
        for (var object : doc.objectActors()) {
            var keys = new ArrayList<>(object.keys());
            for (var change : changes) if (keys.stream().noneMatch(k -> k.frame() == change.frame())) {
                var key = object.keys().stream().filter(k -> k.frame() <= change.frame()).reduce((a,b) -> b).orElseThrow();
                keys.add(new ObjectKey(change.frame(), key.x(), key.y(), key.animation(), key.pose(), key.facing(),
                        key.visible(), key.interpolation(), key.mode(), key.parts()));
            }
            keys.sort(Comparator.comparingInt(ObjectKey::frame));
            if (object.layoutIndex() >= 0) {
                keys = new ArrayList<>(keys.stream().map(key -> {
                    SetSource incoming = doc.set();
                    for (var change : changes) if (change.frame() <= key.frame()) incoming = change.set();
                    boolean matches = object.gameId().equals(incoming.gameId()) && object.zone() == incoming.zone()
                            && object.act() == incoming.act();
                    return new ObjectKey(key.frame(), key.x(), key.y(), key.animation(), key.pose(), key.facing(),
                            key.visible() && matches, key.interpolation(), key.mode(), key.parts());
                }).toList());
            }
            objects.add(new ObjectActor(object.id(), object.name(), object.gameId(), object.zone(), object.act(),
                    object.objectId(), object.subtype(), object.layoutIndex(), keys, object.commands(), object.sourceKind()));
        }
        return new StageplayDocument(doc.version(),doc.name(),doc.durationFrames(),doc.width(),doc.height(),doc.set(),
                actors,camera,doc.captions(),doc.audio(),doc.cameraEffects(),changes,doc.dialogueTypers(),doc.dialogueSkins(),doc.dialogue(),doc.puppets(),
                objects,doc.worldCommands(),doc.voices());
    }


    private static StageplayDocument removeActor(StageplayDocument d,String id) {
        return withCast(d.withVoices(d.voices().stream().filter(v -> !v.speakerActor().equals(id)).toList()),d.actors().stream().filter(a -> !a.id().equals(id)).toList(),d.puppets().stream().filter(p -> !p.actorId().equals(id)).toList());
    }

    private void deleteActor() {
        var actor = selectedActor();
        if (controller.document().dialogue().stream().noneMatch(c -> c.speakerActor().equals(actor.id()))) {
            edit(d -> removeActor(d,actor.id()));
            return;
        }
        menu.showActions(UiMessages.text("editor.stageplay_workspace.remove_1ufvy8", Map.of("value0", String.valueOf(actor.name()))), List.of(
                action(UiMessages.text("editor.stageplay_workspace.keep_dialogue_as_offscreen_speech_99uf0o"), () -> edit(d -> {
                    var cues = d.dialogue().stream().map(c -> c.speakerActor().equals(actor.id())
                            ? dialogueSpeaker(c, "", StageplayDialogue.speakerName(d,c)) : c).toList();
                    var voices = new ArrayList<>(d.voices());
                    var voice = com.openggf.stageplay.StageplaySpeech.voice(d, actor.id(), "");
                    for (var line : d.dialogue()) if (line.speakerActor().equals(actor.id())) {
                        String name = StageplayDialogue.speakerName(d, line);
                        if (voices.stream().noneMatch(v -> v.speakerActor().isEmpty() && v.speakerName().equals(name)))
                            voices.add(new SpeakerVoice("", name, voice.tone(), voice.rhythm(), voice.pitch(), voice.variation(), voice.lengthFrames(), voice.level()));
                    }
                    return removeActor(d.withDialogue(cues).withVoices(voices),actor.id());
                })), action(UiMessages.text("editor.stageplay_workspace.remove_actor_and_their_dialogue_dmwg26"), () -> edit(d -> removeActor(d.withDialogue(d.dialogue().stream()
                        .filter(c -> !c.speakerActor().equals(actor.id())).toList()),actor.id()))), action(UiMessages.text("editor.stageplay_workspace.keep_actor_uaum62"), () -> { })));
    }

    private void dialogueMenu() {
        var d = controller.document();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_workspace.add_dialogue_line_1bgcafx"), UiMessages.text("editor.stageplay_workspace.add_dialogue_line_1bgcafx"), true, () -> speakerMenu(null)));
        actions.add(action(UiMessages.text("editor.stageplay_workspace.reusable_typers_gsotkg"), UiMessages.text("editor.stageplay_menu_presentation.dialogue_typing_9i6ljz"), false, this::typerMenu));
        actions.add(action(UiMessages.text("editor.stageplay_workspace.reusable_decorations_1j5tyr2"), UiMessages.text("editor.stageplay.presentation.action_dialogue_styles"), false, this::skinMenu));
        for (var cue : d.dialogue()) actions.add(action(cue.startFrame()+": "+StageplayDialogue.speakerName(d,cue)+" - "+cue.text(),
                () -> dialogueCueMenu(cue)));
        menu.showActions(UiMessages.text("editor.stageplay_workspace.dialogue_lines_1el2v2v"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.names_appear_immediately_typed_text_speaker_pointers_an_1c926qk"));
    }

    private void dialogueCueMenu(DialogueCue cue) {
        menu.showActions(UiMessages.text("editor.stageplay_workspace.dialogue_1gxoy86", Map.of("value0", String.valueOf(StageplayDialogue.speakerName(controller.document(), cue)))), List.of(
                action(UiMessages.text("editor.stageplay_workspace.edit_line_timing_and_panel_1nnzi2r"), UiMessages.text("editor.stageplay.presentation.action_edit_line"), false, () -> dialogueForm(cue)),
                action(UiMessages.text("editor.stageplay_workspace.choose_speaker_1akxy52"), () -> speakerMenu(cue)),
                action(UiMessages.text("editor.stageplay_workspace.choose_decoration_hjl8op"), UiMessages.text("editor.stageplay.presentation.action_choose_style"), false, () -> dialogueProfileMenu(cue,false)),
                action(UiMessages.text("editor.stageplay_workspace.choose_typer_yfrubz"), UiMessages.text("editor.stageplay.presentation.action_choose_typing"), false, () -> dialogueProfileMenu(cue,true)),
                action(UiMessages.text("editor.stageplay_workspace.custom_reveal_timing_v8ijzi"), () -> revealForm(cue)),
                action(UiMessages.text("editor.stageplay_workspace.use_typer_timing_69io0b"), () -> edit(d -> replaceDialogue(d,dialogueTiming(cue,List.of())))),
                action(UiMessages.text("editor.stageplay_workspace.delete_line_82bind"), () -> edit(d -> d.withDialogue(d.dialogue().stream().filter(c -> !c.id().equals(cue.id())).toList()))),
                action(UiMessages.text("editor.stageplay_workspace.speaker_voice_1hf42pd"), () -> voiceForm(cue.speakerActor(), cue.speakerActor().isEmpty() ? cue.speakerName() : ""))));
    }

    private void speakerMenu(DialogueCue cue) {
        var choices = new ArrayList<EditorProjectMenu.Action>();
        for (var actor : controller.document().actors()) choices.add(action(actor.name()+" ("+actor.character()+")", actor.name(), false, () -> {
            if (cue == null) dialogueForm(newDialogue(actor.id(),""));
            else edit(d -> replaceDialogue(d,dialogueSpeaker(cue,actor.id(),"")));
        }));
        for (var actor : controller.document().objectActors()) choices.add(action(UiMessages.text("editor.stageplay_workspace.object_1cku9of", Map.of("value0", String.valueOf(actor.name()))), String.valueOf(actor.name()), false, () -> {
            if (cue == null) dialogueForm(newDialogue(actor.id(),""));
            else edit(d -> replaceDialogue(d,dialogueSpeaker(cue,actor.id(),"")));
        }));
        choices.add(action(UiMessages.text("editor.stageplay_workspace.offscreen_speaker_narrator_53hd9c"), UiMessages.text("editor.stageplay.presentation.action_offscreen_speaker"), false, () -> {
            if (cue == null) dialogueForm(newDialogue("","Narrator"));
            else dialogueForm(dialogueSpeaker(cue,"",StageplayDialogue.speakerName(controller.document(),cue)));
        }));
        menu.showActions(UiMessages.text("editor.stageplay_workspace.who_is_speaking_5r1hdn"), choices);
    }

    private void voiceForm(String actorId, String offscreenName) {
        voiceForm(actorId, offscreenName, com.openggf.stageplay.StageplaySpeech.voice(controller.document(), actorId, offscreenName));
    }

    private void voicePresetMenu(String actorId, String offscreenName) {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        var defaults = com.openggf.stageplay.StageplaySpeech.defaultVoice(controller.document(), actorId, offscreenName);
        for (var preset : com.openggf.stageplay.StageplaySpeech.Preset.values()) {
            var voice = preset.voice(actorId, offscreenName);
            actions.add(action((voice.equals(defaults) ? String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.default_nger5f", Map.of("value0", String.valueOf(preset.label()))))))) : ((preset.label()))),
                    () -> voiceForm(actorId, offscreenName, voice)));
        }
        menu.showActions(UiMessages.text("editor.stageplay_workspace.voice_presets_sz18iu"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.choose_then_adjust_or_apply_13n6zz8"));
    }

    private void voiceForm(String actorId, String offscreenName, SpeakerVoice voice) {
        var doc = controller.document();
        String name = actorId.isEmpty() ? offscreenName : doc.actors().stream().filter(a -> a.id().equals(actorId))
                .map(Actor::name).findFirst().orElseGet(() -> doc.objectActors().stream().filter(a -> a.id().equals(actorId))
                        .map(ObjectActor::name).findFirst().orElseThrow());
        menu.showForm(UiMessages.text("editor.stageplay_workspace.voice_s9fkas", Map.of("value0", String.valueOf(name))), List.of(
                new EditorProjectMenu.Field("tone", UiMessages.text("editor.stageplay_workspace.tone_1jzwy"), voice.tone().name(), List.of("PRESETS", "DEFAULT", "OFF", "SQUARE", "CHIME", "HOLLOW", "BUZZ")),
                new EditorProjectMenu.Field("rhythm", UiMessages.text("editor.stageplay_workspace.blip_on_each_1167clv"), voice.rhythm().name(), List.of("WORD", "LETTER")),
                number("pitch", UiMessages.text("editor.stageplay_workspace.pitch_48_c3_72_c5_96_c7_iwo8yw"), voice.pitch()),
                number("variation", UiMessages.text("editor.stageplay_workspace.pitch_variation_0_12_semitones_1udqgfx"), voice.variation()),
                number("length", UiMessages.text("editor.stageplay_workspace.blip_length_1_18_frames_1u3h6yr"), voice.lengthFrames()),
                number("level", UiMessages.text("editor.stageplay_workspace.voice_level_0_100_1i9m5oa"), voice.level())), UiMessages.text("editor.stageplay_workspace.apply_voice_17mtfj4"), values -> edit(d -> {
            var voices = new ArrayList<>(d.voices());
            voices.removeIf(v -> actorId.isEmpty() ? v.speakerActor().isEmpty() && v.speakerName().equals(offscreenName)
                    : v.speakerActor().equals(actorId));
            if (!values.get("tone").equals("DEFAULT")) voices.add(new SpeakerVoice(actorId, offscreenName,
                    VoiceTone.valueOf(values.get("tone")), VoiceRhythm.valueOf(values.get("rhythm")),
                    integer(values,"pitch"), integer(values,"variation"), integer(values,"length"), integer(values,"level")));
            return d.withVoices(voices);
        }));
        menu.onValueChanged((key, value) -> {
            if (key.equals("tone") && value.equals("PRESETS")) voicePresetMenu(actorId, offscreenName);
        });
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.presets_chooses_default_resets_z3yh5"));
    }

    private DialogueCue newDialogue(String actor, String name) {
        var d = controller.document();
        int margin = Math.min(12,Math.max(0,(d.width()-80)/2));
        int height = Math.min(72,d.height());
        return new DialogueCue(nextId("dialogue",d),controller.frame(),Math.min(controller.frame()+180,d.durationFrames()),
                actor,name,"Hello.","","",margin,com.openggf.tools.StageplayScriptCompiler.DEFAULT_DIALOGUE_Y,d.width()-margin*2,height,"",List.of());
    }

    private void dialogueForm(DialogueCue cue) {
        menu.showForm(UiMessages.text("editor.stageplay_workspace.dialogue_line_1tse7fg"),List.of(
                text("name",(cue.speakerActor().isEmpty() ? UiMessages.text("editor.stageplay_workspace.speaker_name_hraqzw") : UiMessages.text("editor.stageplay_workspace.name_override_blank_actor_19lolqk")),cue.speakerName()),
                new EditorProjectMenu.Field("text",UiMessages.text("editor.stageplay_workspace.text_n_line_break_1hc7ryy"),escapeCaption(cue.text()),MenuTextEditor.Mode.TEXT,8192),
                number("start",UiMessages.text("editor.stageplay_workspace.start_frame_17e23gv"),cue.startFrame()),number("end",UiMessages.text("editor.stageplay_workspace.end_frame_exclusive_9xyviz"),cue.endFrame()),
                number("x",UiMessages.text("editor.stageplay_workspace.panel_screen_x_znqulc"),cue.x()),number("y",UiMessages.text("editor.stageplay_workspace.panel_screen_y_znquld"),cue.y()),
                number("width",UiMessages.text("editor.stageplay_workspace.panel_width_o56tmy"),cue.width()),number("height",UiMessages.text("editor.stageplay_workspace.panel_height_uzye3n"),cue.height()),
                file("portrait",UiMessages.text("editor.stageplay_workspace.relative_portrait_png_optional_z6x1xl"),cue.portraitAsset())),UiMessages.text("editor.stageplay_workspace.apply_dialogue_1mbb4h6"),v -> edit(d -> {
            String line = unescapeCaption(v.get("text"));
            if (!cue.revealFrames().isEmpty() && StageplayDialogue.graphemeCount(line)!=cue.revealFrames().size())
                throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.choose_use_typer_timing_before_changing_this_line_s_cha_kaolqn"));
            return replaceDialogue(d,new DialogueCue(cue.id(),integer(v,"start"),integer(v,"end"),cue.speakerActor(),v.get("name"),line,
                    cue.skinId(),cue.typerId(),integer(v,"x"),integer(v,"y"),integer(v,"width"),integer(v,"height"),v.get("portrait"),cue.revealFrames()));
        }));
        menu.setHelp(UiMessages.text("editor.stageplay_workspace.write_s_line_1c10mfd", Map.of("value0", String.valueOf(StageplayDialogue.speakerName(controller.document(), cue)))),
                UiMessages.text("editor.stageplay_workspace.end_is_exclusive_choose_the_speaker_decoration_and_type_1iwxd8s"));
    }

    private void dialogueProfileMenu(DialogueCue cue, boolean typer) {
        var choices = new ArrayList<EditorProjectMenu.Action>();
        choices.add(action(UiMessages.text("editor.stageplay_workspace.built_in_default_rwnf9x"), () -> edit(d -> replaceDialogue(d,dialogueProfile(cue,typer,"")))));
        if (typer) for (var value : controller.document().dialogueTypers()) choices.add(action(UiMessages.text("editor.stageplay_workspace.frames_per_character_h7c2mh", Map.of("value0", String.valueOf(value.id() + " / " + value.framesPerGrapheme()))),
                () -> edit(d -> replaceDialogue(d,dialogueProfile(cue,true,value.id())))));
        else for (var value : controller.document().dialogueSkins()) choices.add(action(value.id(),
                () -> edit(d -> replaceDialogue(d,dialogueProfile(cue,false,value.id())))));
        menu.showActions(typer ? EditorProjectMenu.PresentationKind.CHOOSE_TYPER : EditorProjectMenu.PresentationKind.CHOOSE_STYLE, (typer ? UiMessages.text("editor.stageplay_workspace.choose_dialogue_typer_f6d9vd") : UiMessages.text("editor.stageplay_workspace.choose_dialogue_decoration_1wvi8wf")),choices);
        if (typer && !cue.revealFrames().isEmpty()) menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.this_line_has_custom_reveal_timing_choose_use_typer_tim_1yp4cqd"));
    }

    private void revealForm(DialogueCue cue) {
        var offsets = StageplayDialogue.revealFrames(controller.document(),cue);
        var fields = new ArrayList<EditorProjectMenu.Field>();
        for (int from=0;from<offsets.size();from+=128) {
            int end=Math.min(from+128,offsets.size());
            fields.add(new EditorProjectMenu.Field("offsets"+(from/128),UiMessages.text("editor.stageplay_workspace.characters_1iqzhdn", Map.of("value0", String.valueOf((from + 1)), "value1", String.valueOf(end))),
                    offsets.subList(from,end).stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", ")),
                    MenuTextEditor.Mode.TEXT,8192));
        }
        menu.showForm(EditorProjectMenu.PresentationKind.REVEAL_TIMING, UiMessages.text("editor.stageplay_workspace.custom_dialogue_reveal_timing_1fp5c10"),fields,UiMessages.text("editor.stageplay_workspace.apply_reveal_timing_3a4hbf"),v -> edit(d -> {
            var values = new ArrayList<Integer>();
            for (int i=0;i<fields.size();i++) {
                String input=v.get("offsets"+i).trim();
                if (!input.isEmpty()) for (String token:input.split("[,\\s]+")) values.add(Integer.parseInt(token));
            }
            if (values.size()!=StageplayDialogue.graphemeCount(cue.text()))
                throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.enter_exactly_one_reveal_frame_per_character_combining_1a2dclu"));
            return replaceDialogue(d,dialogueTiming(cue,values));
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.frame_offsets_from_the_line_s_start_in_character_order_1dc70p8"));
    }

    private void typerMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_workspace.add_typer_90g6c9"), UiMessages.text("editor.stageplay_workspace.add_typer_90g6c9"), true, () -> typerForm(null)));
        for (var typer : controller.document().dialogueTypers()) actions.add(action(typer.id(), () -> menu.showActions(UiMessages.text("editor.stageplay_workspace.typer_1j0f02p", Map.of("value0", String.valueOf(typer.id()))),List.of(
                action(UiMessages.text("editor.stageplay_workspace.edit_typer_14wjcoy"), () -> typerForm(typer)),
                action(UiMessages.text("editor.stageplay_workspace.delete_and_use_default_for_its_lines_dsd0pm"), () -> edit(d -> d.withDialogue(d.dialogue().stream()
                        .map(c -> c.typerId().equals(typer.id()) ? dialogueProfile(c,true,"") : c).toList())
                        .withDialogueTypers(d.dialogueTypers().stream().filter(t -> !t.id().equals(typer.id())).toList())))))));
        menu.showActions(EditorProjectMenu.PresentationKind.DIALOGUE_TYPING, UiMessages.text("editor.stageplay_workspace.reusable_dialogue_typers_1v4hw6m"),actions);
    }

    private void typerForm(DialogueTyper typer) {
        String id=typer==null ? nextProfileId("typer",controller.document().dialogueTypers().stream().map(DialogueTyper::id).toList()) : typer.id();
        var fields=new ArrayList<EditorProjectMenu.Field>();
        if (typer==null) fields.add(text("id",UiMessages.text("editor.stageplay_workspace.profile_name_lzr7pe"),id));
        fields.add(number("speed",UiMessages.text("editor.stageplay_workspace.frames_per_character_0_instant_1365qzz"),typer==null?2:typer.framesPerGrapheme()));
        fields.add(number("punctuation",UiMessages.text("editor.stageplay_workspace.extra_pause_after_punctuation_gst4qk"),typer==null?6:typer.punctuationPauseFrames()));
        fields.add(number("space",UiMessages.text("editor.stageplay_workspace.extra_pause_after_spaces_1je6l8r"),typer==null?0:typer.spacePauseFrames()));
        menu.showForm((typer == null ? ((UiMessages.text("editor.stageplay_workspace.add_dialogue_typer_1el0ib3"))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.typer_1j0f02p", Map.of("value0", String.valueOf(id)))))))),fields,UiMessages.text("editor.stageplay_workspace.apply_typer_17lwdgm"),v -> edit(d -> {
            String profile=typer==null?v.get("id"):id;
            if (typer==null && d.dialogueTypers().stream().anyMatch(t -> t.id().equals(profile))) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.that_typer_name_already_exists_hr8z2w"));
            var values=new ArrayList<>(d.dialogueTypers()); values.removeIf(t -> t.id().equals(profile));
            values.add(new DialogueTyper(profile,integer(v,"speed"),integer(v,"punctuation"),integer(v,"space")));
            return d.withDialogueTypers(values);
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.reusable_profile_names_use_letters_digits_dots_undersco_a7ayht"));
    }

    private void skinMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_workspace.add_decoration_1v6t2un"), UiMessages.text("editor.stageplay_workspace.add_decoration_1v6t2un"), true, () -> skinForm(null)));
        for (var skin : controller.document().dialogueSkins()) actions.add(action(skin.id(), () -> menu.showActions(UiMessages.text("editor.stageplay_workspace.decoration_ypis9l", Map.of("value0", String.valueOf(skin.id()))),List.of(
                action(UiMessages.text("editor.stageplay_workspace.edit_decoration_d48qjq"), () -> skinForm(skin)),
                action(UiMessages.text("editor.stageplay_workspace.delete_and_use_default_for_its_lines_dsd0pm"), () -> edit(d -> d.withDialogue(d.dialogue().stream()
                        .map(c -> c.skinId().equals(skin.id()) ? dialogueProfile(c,false,"") : c).toList())
                        .withDialogueSkins(d.dialogueSkins().stream().filter(s -> !s.id().equals(skin.id())).toList())))))));
        menu.showActions(EditorProjectMenu.PresentationKind.DIALOGUE_STYLES, UiMessages.text("editor.stageplay_workspace.reusable_dialogue_decorations_10zjals"),actions);
    }

    private void skinForm(DialogueSkin skin) {
        String id=skin==null ? nextProfileId("skin",controller.document().dialogueSkins().stream().map(DialogueSkin::id).toList()) : skin.id();
        var fields=new ArrayList<EditorProjectMenu.Field>();
        if (skin==null) fields.add(text("id",UiMessages.text("editor.stageplay_workspace.profile_name_lzr7pe"),id));
        fields.add(text("text",UiMessages.text("editor.stageplay_workspace.text_argb_hex_8ui6gq"),hex(skin==null?0xfff5f0df:skin.textArgb())));
        fields.add(text("name",UiMessages.text("editor.stageplay_workspace.speaker_name_argb_hex_10y3u4b"),hex(skin==null?0xffffcf66:skin.nameArgb())));
        fields.add(text("panel",UiMessages.text("editor.stageplay_workspace.panel_argb_hex_st2ykj"),hex(skin==null?0xff172338:skin.panelArgb())));
        fields.add(text("border",UiMessages.text("editor.stageplay_workspace.border_argb_hex_d8slcr"),hex(skin==null?0xffa8b8cf:skin.borderArgb())));
        fields.add(number("padding",UiMessages.text("editor.stageplay_workspace.inner_padding_4_32_11o6c85"),skin==null?6:skin.padding()));
        fields.add(file("asset",UiMessages.text("editor.stageplay_workspace.relative_decoration_png_optional_1e5vs5w"),skin==null?"":skin.decorationAsset()));
        fields.add(number("slice",UiMessages.text("editor.stageplay_workspace.fixed_png_border_0_64_pixels_i8fxoz"),skin==null?0:skin.decorationBorder()));
        fields.add(text("pointer",UiMessages.text("editor.stageplay_workspace.speaker_pointer_true_false_1isyj5u"),Boolean.toString(skin==null || skin.pointer())));
        menu.showForm((skin == null ? ((UiMessages.text("editor.stageplay_workspace.add_dialogue_decoration_hyd595"))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.decoration_ypis9l", Map.of("value0", String.valueOf(id)))))))),fields,UiMessages.text("editor.stageplay_workspace.apply_decoration_5zgk3m"),v -> edit(d -> {
            String profile=skin==null?v.get("id"):id;
            if (skin==null && d.dialogueSkins().stream().anyMatch(s -> s.id().equals(profile))) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.that_decoration_name_already_exists_32xrvm"));
            var values=new ArrayList<>(d.dialogueSkins()); values.removeIf(s -> s.id().equals(profile));
            values.add(new DialogueSkin(profile,color(v,"text"),color(v,"name"),color(v,"panel"),color(v,"border"),integer(v,"padding"),v.get("asset"),integer(v,"slice"),bool(v,"pointer")));
            return d.withDialogueSkins(values);
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.an_optional_png_stretches_its_centre_and_edges_while_ke_1h1sbjm"));
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
        if (!value.matches("[0-9a-fA-F]{8}")) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.colors_need_eight_argb_hex_digits_1xfaiqc"));
        return (int)Long.parseLong(value,16);
    }

    private void captionMenu() {
        var actions = new ArrayList<EditorProjectMenu.Action>(); actions.add(action(UiMessages.text("editor.stageplay_workspace.add_caption_igyhjb"), UiMessages.text("editor.stageplay_workspace.add_caption_igyhjb"), true, () -> captionForm(null)));
        for (var caption : controller.document().captions()) actions.add(action(caption.startFrame()+": "+caption.text(), () -> menu.showActions(UiMessages.text("editor.stageplay_workspace.caption_10o2v2e"),List.of(
                action(UiMessages.text("editor.stageplay_workspace.edit_1a6ui"),()->captionForm(caption)),action(UiMessages.text("editor.stageplay_workspace.delete_xskn2j"),()->edit(d->d.withCaptions(d.captions().stream().filter(c->!c.id().equals(caption.id())).toList())))))));
        menu.showActions(EditorProjectMenu.PresentationKind.CAPTION_LINES, UiMessages.text("editor.stageplay_workspace.captions_8nvjh"),actions);
    }

    private void captionForm(Caption caption) {
        int frame = controller.frame();
        menu.showForm(UiMessages.text("editor.stageplay_workspace.caption_10o2v2e"),List.of(new EditorProjectMenu.Field("text",UiMessages.text("editor.stageplay_workspace.text_n_line_break_1hc7ryy"),caption==null?"Dialogue":escapeCaption(caption.text()),MenuTextEditor.Mode.TEXT,8192),number("start",UiMessages.text("editor.stageplay_workspace.start_frame_17e23gv"),caption==null?frame:caption.startFrame()),
                number("end",UiMessages.text("editor.stageplay_workspace.end_frame_exclusive_9xyviz"),caption==null?Math.min(frame+120,controller.document().durationFrames()):caption.endFrame()),
                number("x",UiMessages.text("editor.stageplay_workspace.screen_x_1tajef8"),caption==null?16:caption.x()),number("y",UiMessages.text("editor.stageplay_workspace.screen_y_1tajef9"),caption==null?controller.document().height()-28:caption.y()),
                text("color",UiMessages.text("editor.stageplay_workspace.argb_hex_1lddyo7"),caption==null?"FFFFFFFF":String.format("%08X",caption.argb()))),UiMessages.text("editor.stageplay_workspace.apply_caption_mlmwic"),v -> edit(d -> {
            String id=caption==null?nextId("caption",d):caption.id();
            var values=new ArrayList<>(d.captions()); values.removeIf(c->c.id().equals(id));
            values.add(new Caption(id,integer(v,"start"),integer(v,"end"),unescapeCaption(v.get("text")),integer(v,"x"),integer(v,"y"),(int)Long.parseLong(v.get("color"),16)));
            return d.withCaptions(values);
        }));
    }

    private void audioMenu() {
        var actions=new ArrayList<EditorProjectMenu.Action>(); actions.add(action(UiMessages.text("editor.stageplay_workspace.add_audio_cue_lq4iru"), UiMessages.text("editor.stageplay_workspace.add_audio_cue_lq4iru"), true,()->audioForm(null)));
        for(var cue:controller.document().audio()) actions.add(action(cue.frame()+": "+cue.kind()+" "+cue.id(),()->menu.showActions(UiMessages.text("editor.stageplay_workspace.audio_cue_108879l"),List.of(
                action(UiMessages.text("editor.stageplay_workspace.edit_1a6ui"),()->audioForm(cue)),action(UiMessages.text("editor.stageplay_workspace.delete_xskn2j"),()->edit(d->d.withAudio(d.audio().stream().filter(c->!c.id().equals(cue.id())).toList())))))));
        menu.showActions(UiMessages.text("editor.stageplay_workspace.audio_cues_1lmdfpm"),actions);
    }

    private void audioForm(AudioCue cue) {
        String game = cue == null ? controller.document().set().gameId() : cue.gameId();
        int initialSound = cue == null ? com.openggf.game.audio.RomSoundCatalog.sfx(game).getFirst().soundId() : cue.soundId();
        menu.showForm(UiMessages.text("editor.stageplay_workspace.audio_cue_108879l"),List.of(number("frame",UiMessages.text("editor.stageplay_workspace.frame_14kgnh"),cue==null?controller.frame():cue.frame()),
                text("kind",UiMessages.text("editor.stageplay_workspace.rom_music_rom_sfx_voice_music_stop_1i09typ"),cue==null?"ROM_SFX":cue.kind().name()),
                text("game",UiMessages.text("editor.stageplay_workspace.rom_s1_s2_s3k_8yu1ee"),cue==null?controller.document().set().gameId():cue.gameId()),
                number("sound",UiMessages.text("editor.stageplay_workspace.rom_sound_id_decimal_1w0oq4c"),initialSound),
                text("asset",UiMessages.text("editor.stageplay_workspace.relative_wav_ogg_file_5fn7c6"),cue==null?"":cue.asset()),number("gain",UiMessages.text("editor.stageplay_workspace.gain_0_1_dtuu0x"),cue==null?1:cue.gain())),UiMessages.text("editor.stageplay_workspace.apply_audio_17bdhfo"),v -> edit(d -> {
            String id=cue==null?nextId("audio",d):cue.id();
            var values=new ArrayList<>(d.audio()); values.removeIf(c->c.id().equals(id));
            AudioKind kind = AudioKind.valueOf(v.get("kind").toUpperCase(java.util.Locale.ROOT));
            int sound = integer(v,"sound");
            if (kind == AudioKind.ROM_MUSIC) com.openggf.game.audio.RomSoundCatalog.music(v.get("game"),sound);
            if (kind == AudioKind.ROM_SFX) com.openggf.game.audio.RomSoundCatalog.sfx(v.get("game"),sound);
            values.add(new AudioCue(id,integer(v,"frame"),kind,v.get("game"),sound,v.get("asset"),decimal(v,"gain")));
            return d.withAudio(values);
        }));
        configureAudioChoices(initialSound, false);
        menu.onValueChanged((key,value) -> {
            if (key.equals("game") || key.equals("kind")) configureAudioChoices(initialSound, true);
        });
        menu.setHelp(UiMessages.text("editor.stageplay_workspace.choose_a_sound_and_when_it_plays_g2ye4g"),
                UiMessages.text("editor.stageplay_workspace.music_and_sound_effects_use_each_rom_s_native_ids_pick_1fnyr89"));
    }

    private void configureAudioChoices(int initialSound, boolean changedSource) {
        var values = menu.values();
        String kind = values.get("kind"), game = values.get("game");
        var entries = kind.equals("ROM_MUSIC") ? com.openggf.game.audio.RomSoundCatalog.music(game)
                : kind.equals("ROM_SFX") ? com.openggf.game.audio.RomSoundCatalog.sfx(game) : List.<com.openggf.game.audio.RomSoundCatalog.Entry>of();
        if (entries.isEmpty()) { menu.configureField("sound",UiMessages.text("editor.stageplay_workspace.sound_id_tvzfu4"),List.of(),Map.of()); return; }
        var labels = new java.util.LinkedHashMap<String,String>();
        entries.forEach(entry -> labels.put(Integer.toString(entry.soundId()),
                UiMessages.text("editor.stageplay_workspace.message_vnwjla", Map.of("value0", String.valueOf(entry.title().replace(" Zone", "")), "value1", String.valueOf(String.format(java.util.Locale.ROOT, "%02X", entry.soundId()))))));
        if (changedSource && !labels.containsKey(values.get("sound")))
            menu.setValue("sound",Integer.toString(entries.getFirst().soundId()));
        // Keep an existing/custom number editable when it is outside the chosen catalog.
        // The shared Field retains its opening value; Apply still validates the selected kind.
        labels.putIfAbsent(Integer.toString(initialSound),UiMessages.text("editor.stageplay_workspace.custom_id_1ae23uh", Map.of("value0", String.valueOf(initialSound))));
        menu.configureField("sound",(kind.equals("ROM_MUSIC") ? ((UiMessages.text("editor.stageplay_workspace.music_18hb0l"))) : ((UiMessages.text("editor.stageplay_workspace.sound_1boa9b")))),List.copyOf(labels.keySet()),labels);
    }

    private record GroundContext(StageplayDocument document, Path root, String actor, int frame,
                                 long generation, double authoredY) { }

    private void groundForm() {
        var document = controller.document();
        String id = controller.selectedActorId();
        requireGroundPlacement(document, id, controller.frame());
        var object = document.objectActors().stream().filter(a -> a.id().equals(id)).findFirst();
        double x, y;
        if (object.isPresent()) {
            var key = groundObjectKey(object.get(), controller.frame());
            x = key.x(); y = key.y();
        } else {
            var key = selectedActor().keys().stream().filter(k -> k.frame() <= controller.frame())
                    .reduce((a, b) -> b).orElseThrow();
            x = key.x(); y = key.y();
        }
        var context = new GroundContext(document, assetRoot, id, controller.frame(),
                StageplayGround.generation(controller), y);
        int centre = (int) Math.clamp(y, Short.MIN_VALUE + 128.0, Short.MAX_VALUE - 128.0);
        var fields = new ArrayList<EditorProjectMenu.Field>(List.of(
                number("x", UiMessages.text("editor.stageplay_workspace.world_x_1gr5ctm"), Math.round(x)),
                number("top", UiMessages.text("editor.stageplay_workspace.top_native_surface_row_inclusive_1lygacu"), centre - 128),
                number("bottom", UiMessages.text("editor.stageplay_workspace.bottom_native_surface_row_inclusive_1ud3qmw"), centre + 128)));
        if (object.isPresent()) {
            fields.add(number("halfWidth", UiMessages.text("editor.stageplay_workspace.visual_half_width_6e09yk"), 8));
            fields.add(number("height", UiMessages.text("editor.stageplay_workspace.visual_height_aamvaf"), 28));
            fields.add(number("footOffset", UiMessages.text("editor.stageplay_workspace.anchor_to_foot_offset_urvigb"), 14));
            fields.add(number("collisionPath", UiMessages.text("editor.stageplay_workspace.object_path_0_1_51qs6c"), 0));
        }
        menu.showForm(EditorProjectMenu.PresentationKind.FLOOR_PLACEMENTS, UiMessages.text("editor.stageplay_workspace.place_on_floor_at_frame_ud478v", Map.of("value0", String.valueOf(id), "value1", String.valueOf(context.frame()))), fields, UiMessages.text("editor.stageplay_workspace.query_terrain_h9l9dp"), values -> io(() -> {
            requireGroundFresh(context);
            StageplayGround.Visual visual = object.isEmpty() ? null : new StageplayGround.Visual(
                    integer(values, "halfWidth"), integer(values, "height"), integer(values, "footOffset"),
                    StageplayGround.ObjectPath.of(integer(values, "collisionPath")));
            var query = new StageplayGround.Query("placement", integer(values, "x"), integer(values, "top"),
                    integer(values, "bottom"), StageplayGround.performerOf(document, id, visual), id);
            var report = host.ground(document, context.root(), context.frame(), new StageplayGround.Batch(List.of(query), 20000));
            requireGroundFresh(context);
            if (!(report.basis() instanceof StageplayGround.SceneFrame basis) || basis.frame() != context.frame()
                    || !basis.set().equals(StageplayRouteContract.source(document, context.frame())))
                throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.ground_report_does_not_describe_the_requested_scene_fra_1a83zd0"));
            groundResults(context, query, report);
        }));
        menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.native_floor_rows_queries_do_not_edit_the_scene_terrain_15lg209"));
    }

    private void groundResults(GroundContext context, StageplayGround.Query query, StageplayGround.Report report) {
        var answer = report.answer(query.id());
        var actions = new ArrayList<EditorProjectMenu.Action>();
        var detail = new StringBuilder((answer.truncated() ? UiMessages.text("editor.stageplay_workspace.terrain_only_f1_evidence_caveats_outcome_truncated_cave_r1vaqa", Map.of("value0", String.valueOf(answer.outcome()), "value1", String.valueOf(report.basis().caveats()))) : UiMessages.text("editor.stageplay_workspace.terrain_only_f1_evidence_caveats_outcome_caveats_o1lv50", Map.of("value0", String.valueOf(answer.outcome()), "value1", String.valueOf(("")), "value2", String.valueOf(report.basis().caveats())))));
        boolean complete = answer.outcome() == StageplayGround.Outcome.FOUND && !answer.truncated();
        for (var placement : answer.placements()) {
            String label = UiMessages.text("editor.stageplay_workspace.authored_y_floor_angle_b2yf76", Map.of("value0", String.valueOf("X " + placement.x()), "value1", String.valueOf(placement.y()), "value2", String.valueOf(placement.surfaceY()), "value3", String.valueOf(placement.angle())));
            detail.append(label).append(": ").append(placement.evidence()).append('\n');
            actions.add(action((complete ? String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.apply_vpnjx3", Map.of("value0", String.valueOf(label))))))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.review_only_19jqdjx", Map.of("value0", String.valueOf(label)))))))),
                    (complete ? UiMessages.text("editor.stageplay.presentation.floor_apply", Map.of("x", placement.x(), "y", placement.y())) : UiMessages.text("editor.stageplay.presentation.floor_review", Map.of("x", placement.x(), "y", placement.y()))), complete,
                    () -> applyGround(context, query, answer, placement)));
        }
        for (var refusal : answer.refusals()) {
            String label = UiMessages.text("editor.stageplay_workspace.refused_floor_1ggpyhb", Map.of("value0", String.valueOf(refusal.surfaceY()), "value1", String.valueOf(refusal.reason())));
            detail.append(label).append(": ").append(refusal.detail()).append('\n');
            actions.add(action(label, UiMessages.text("editor.stageplay.presentation.floor_refusal", Map.of("surface", refusal.surfaceY())), false, () -> {
                menu.showActions(EditorProjectMenu.PresentationKind.FLOOR_REFUSAL, label, List.of(action(UiMessages.text("editor.stageplay_workspace.back_to_placements_ga98ka"), () -> groundResults(context, query, report))));
                menu.setSubtitle(refusal.detail());
            }));
        }
        int selected = answer.nearest(context.authoredY()).map(answer.placements()::indexOf).orElse(actions.size());
        actions.add(action(UiMessages.text("editor.stageplay_workspace.query_another_band_xzdktm"), this::groundForm));
        actions.add(action(UiMessages.text("editor.stageplay_workspace.cancel_x9d2fu"), () -> { }));
        detail.append(UiMessages.text("editor.stageplay_workspace.player_terrain_proposals_need_native_playback_verificat_da2qye"))
                .append(UiMessages.text("editor.ground.apply_boundary"));
        menu.showActions(EditorProjectMenu.PresentationKind.FLOOR_PLACEMENTS, UiMessages.text("editor.stageplay_workspace.place_on_floor_4wyust", Map.of("value0", String.valueOf(answer.outcome()))), actions, selected);
        String summary=(complete ? UiMessages.text("editor.stageplay_workspace.choose_a_floor_then_verify_it_in_playback_1bpffsh") : (answer.outcome() == StageplayGround.Outcome.NONE_IN_BAND ? UiMessages.text("editor.stageplay_workspace.no_usable_floor_in_this_range_px6dhx") : UiMessages.text("editor.stageplay_workspace.the_floor_search_is_incomplete_try_again_pl608n")));
        menu.setHelp(UiMessages.text("editor.stageplay_workspace.choose_a_placement_or_search_again_1vw8ljg"),detail.toString());menu.setStatusLine(summary,complete?EditorProjectMenu.Tone.SUCCESS:EditorProjectMenu.Tone.WARNING);
        status = (answer.truncated() ? String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.ground_query_placements_refusals_truncated_query_again_1up4gvm", Map.of("value0", String.valueOf(answer.outcome()), "value1", String.valueOf(answer.placements().size()), "value2", String.valueOf(answer.refusals().size()))))))) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.ground_query_placements_refusals_iar3k2", Map.of("value0", String.valueOf(answer.outcome()), "value1", String.valueOf(answer.placements().size()), "value2", String.valueOf(answer.refusals().size()), "value3", String.valueOf(("")))))))));
    }

    private void requireGroundFresh(GroundContext context) {
        if (StageplayGround.generation(controller) != context.generation()
                || !controller.document().equals(context.document()) || !Objects.equals(assetRoot, context.root())
                || !controller.selectedActorId().equals(context.actor()) || controller.frame() != context.frame())
            throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.stale_ground_result_scene_asset_root_selected_actor_or_1wz55su"));
    }

    private void applyGround(GroundContext context, StageplayGround.Query query,
                             StageplayGround.Answer answer, StageplayGround.Placement placement) {
        requireGroundFresh(context);
        requireGroundPlacement(context.document(), context.actor(), context.frame());
        if (answer.outcome() != StageplayGround.Outcome.FOUND || answer.truncated()
                || !answer.placements().contains(placement))
            throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.a_complete_ground_query_is_required_before_applying_a_p_nkp17r"));
        boolean proof = query.performer() instanceof StageplayGround.Visual
                ? placement.evidence() instanceof StageplayGround.VisualSupport
                : placement.evidence() instanceof StageplayGround.PlayerTerrainProposal player && player.distance() == 0;
        if (!proof || placement.x() != query.x() || placement.surfaceY() < query.top() || placement.surfaceY() > query.bottom())
            throw new IllegalStateException(UiMessages.text("editor.stageplay_workspace.ground_placement_has_no_matching_terrain_evidence_yedku8"));
        edit(document -> {
            var actor = document.actors().stream().filter(a -> a.id().equals(context.actor())).findFirst();
            if (actor.isPresent()) {
                int end = StageplayRouteContract.shotEnd(document, context.frame());
                // Every visibility key holds the shot-entry centre; moving only the boundary is invalid.
                var keys = actor.get().keys().stream().map(key -> key.frame() < context.frame() || key.frame() >= end ? key
                        : new ActorKey(key.frame(), placement.x(), placement.y(), key.animation(), key.pose(), key.facing(), key.visible(), key.scale(), key.interpolation())).toList();
                return replaceActor(document, actor.get(), keys);
            }
            return document.withObjectActors(document.objectActors().stream().map(object -> {
                if (!object.id().equals(context.actor())) return object;
                var key = groundObjectKey(object, context.frame());
                var keys = new ArrayList<>(object.keys());
                keys.removeIf(k -> k.frame() == context.frame());
                keys.add(new ObjectKey(context.frame(), placement.x(), placement.y(), key.animation(), key.pose(), key.facing(), key.visible(), key.interpolation(), key.mode(), key.parts()));
                keys.sort(Comparator.comparingInt(ObjectKey::frame));
                return new ObjectActor(object.id(), object.name(), object.gameId(), object.zone(), object.act(),
                        object.objectId(), object.subtype(), object.layoutIndex(), keys, object.commands(), object.sourceKind());
            }).toList());
        });
        status = UiMessages.text("editor.stageplay_workspace.placed_at_x_authored_y_native_floor_verify_the_edited_s_122ml7k", Map.of("value0", String.valueOf(context.actor()), "value1", String.valueOf(placement.x()), "value2", String.valueOf(placement.y()), "value3", String.valueOf(placement.surfaceY())));
    }

    private static ObjectKey groundObjectKey(ObjectActor object, int frame) {
        return object.keys().stream().filter(k -> k.frame() <= frame).reduce((a, b) -> b).orElseThrow();
    }

    private static void requireGroundPlacement(StageplayDocument document, String id, int frame) {
        var actor = document.actors().stream().filter(a -> a.id().equals(id)).findFirst();
        if (actor.isPresent()) {
            StageplayGround.performerOf(document, id, null);
            if (frame != 0 && document.sceneChanges().stream().noneMatch(c -> c.frame() == frame))
                throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.place_physical_players_only_at_a_shot_boundary_use_move_1crcya9"));
            return;
        }
        var object = document.objectActors().stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.select_a_cast_member_first_js8q20")));
        int end = StageplayRouteContract.shotEnd(document, frame);
        if (groundObjectKey(object,frame).mode() == ObjectMode.FREE
                || object.keys().stream().anyMatch(k -> k.frame() > frame && k.frame() < end && k.mode() == ObjectMode.FREE))
            throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.place_on_floor_cannot_relocate_free_or_release_to_free_y6c7i0"));
        // StageplayDocument already rejects duplicate placed identities globally,
        // which is stronger than rejecting overlapping bindings at this playhead.
    }

    private enum RouteMenuAction { APPLY, REFRESH, CANCEL, REPLAN, BACK }

    private void routeMenu() {
        pollRoute();
        if (routeUpdate == null) { routeForm(); return; }
        var actions = new ArrayList<EditorProjectMenu.Action>();
        var indices = new java.util.EnumMap<RouteMenuAction, Integer>(RouteMenuAction.class);
        java.util.function.BiConsumer<RouteMenuAction, EditorProjectMenu.Action> add = (kind, action) -> {
            indices.put(kind, actions.size());
            actions.add(action);
        };
        if (routeUpdate.phase() == StageplayRouteJob.Phase.READY)
            add.accept(RouteMenuAction.APPLY, action(UiMessages.text("editor.stageplay_workspace.check_inputs_and_apply_route_19uole7"), UiMessages.text("editor.stageplay.presentation.action_apply_route"), true, () -> io(() -> host.requestRouteApply(controller.document(),assetRoot))));
        if (routeUpdate.phase() == StageplayRouteJob.Phase.PLANNING || routeUpdate.phase() == StageplayRouteJob.Phase.CHECKING)
            add.accept(RouteMenuAction.REFRESH, action(UiMessages.text("editor.stageplay_workspace.refresh_route_status_1eoto1a"), UiMessages.text("editor.stageplay.presentation.action_refresh"), false, this::routeMenu));
        add.accept(RouteMenuAction.CANCEL, action(UiMessages.text("editor.stageplay_workspace.cancel_route_jhfo77"), this::cancelRoute));
        add.accept(RouteMenuAction.REPLAN, action(UiMessages.text("editor.stageplay_workspace.plan_another_route_1etv7th"), () -> { cancelRoute(); routeForm(); }));
        add.accept(RouteMenuAction.BACK, action(UiMessages.text("editor.stageplay_workspace.back_187if"), () -> { }));
        menu.showActions(EditorProjectMenu.PresentationKind.ROUTE_RESULT, UiMessages.text("editor.stageplay_workspace.collision_route_1vign97"),actions);
        var result = routeUpdate.result();
        var overlay=result==null?null:new com.openggf.stageplay.editor.StageplayRouteOverlay(result,false);
        String summary=(result == null ? (routeUpdate.phase() == StageplayRouteJob.Phase.CHECKING ? UiMessages.text("editor.stageplay_workspace.checking_route_inputs_1yd52g") : UiMessages.text("editor.stageplay_workspace.finding_a_route_x66d8b")) : overlay.summary());
        menu.setHelp(UiMessages.text("editor.stageplay_workspace.review_the_path_before_applying_it_1s4jqwk"),(result == null ? ((routeUpdate.message())) : String.valueOf((String.valueOf((UiMessages.text("editor.stageplay_workspace.samples_nodes_ticks_1lkghm8", Map.of("value0", String.valueOf(result.message() + "\n" + overlay.motionSummary() + "\n" + result.path().size()), "value1", String.valueOf(result.expandedNodes()), "value2", String.valueOf(result.simulatedTicks())))))))));
        menu.setStatusLine(summary,result==null?EditorProjectMenu.Tone.INFO:result.success()?EditorProjectMenu.Tone.SUCCESS:EditorProjectMenu.Tone.WARNING);
        if(result!=null) configureRouteDetails(result,overlay,indices);
    }

    private void configureRouteDetails(com.openggf.stageplay.route.StageplayRouteResult result,
                                       com.openggf.stageplay.editor.StageplayRouteOverlay overlay,
                                       Map<RouteMenuAction, Integer> indices) {
        String who=controller.document().actors().stream().filter(a->a.id().equals(result.request().actorId()))
                .map(Actor::name).findFirst().orElseGet(()->controller.document().objectActors().stream()
                        .filter(a->a.id().equals(result.request().actorId())).map(ObjectActor::name).findFirst().orElse(UiMessages.text("editor.stageplay_workspace.this_actor_1yp7llf")));
        String meaning=switch(result.status()) {
            case BUDGET_EXCEEDED -> UiMessages.text("editor.stageplay_workspace.might_reach_the_goal_but_the_search_ran_out_of_budget_b_1pim76o", Map.of("value0", String.valueOf(who)));
            case SEARCH_EXHAUSTED -> UiMessages.text("editor.stageplay_workspace.the_search_did_not_find_a_route_with_these_settings_fr93a2");
            case VERIFIED -> UiMessages.text("editor.stageplay_workspace.reached_the_goal_in_a_fresh_playback_check_dok4ya", Map.of("value0", String.valueOf(who)));
            case VISUAL_TERRAIN -> UiMessages.text("editor.stageplay_workspace.this_object_can_follow_the_terrain_its_native_behavior_1nmwk42");
            case UNSUPPORTED -> UiMessages.text("editor.stageplay_workspace.the_planner_cannot_check_this_actor_or_starting_point_9x2sd2");
            case CANCELLED -> UiMessages.text("editor.stageplay_workspace.route_planning_was_cancelled_q80yww");
            case VERIFICATION_FAILED -> UiMessages.text("editor.stageplay_workspace.playback_did_not_confirm_the_proposed_route_1b1k5r1");
            case STALE_INPUT -> UiMessages.text("editor.stageplay_workspace.the_scene_or_its_assets_changed_plan_the_route_again_1n46y40");
        };
        String status=switch(result.status()) {
            case BUDGET_EXCEEDED -> UiMessages.text("editor.stageplay_workspace.search_limit_reached_sljlp1");
            case SEARCH_EXHAUSTED -> UiMessages.text("editor.stageplay_workspace.no_route_found_prf0h8");
            case VERIFIED -> UiMessages.text("editor.stageplay_workspace.route_found_13b24ln");
            case VISUAL_TERRAIN -> UiMessages.text("editor.stageplay_workspace.object_route_found_1urdjmy");
            case UNSUPPORTED -> UiMessages.text("editor.stageplay_workspace.route_unsupported_97ybpa");
            case CANCELLED -> UiMessages.text("editor.stageplay_workspace.route_cancelled_16moi6y");
            case VERIFICATION_FAILED -> UiMessages.text("editor.stageplay_workspace.playback_check_failed_1ffcqhm");
            case STALE_INPUT -> UiMessages.text("editor.stageplay_workspace.route_needs_replanning_1cfm80a");
        };
        if(result.success()) {
            long jumps=result.commands().stream().filter(c->c.kind()==PuppetCommandKind.JUMP).count();
            status=UiMessages.text("editor.route.result_summary", Map.of("status", status,
                    "seconds", overlay.performanceFrames()/60.0, "jumps", jumps));
        }
        var sections=new ArrayList<MenuDetailsScreen.Section>();
        var advice=new ArrayList<MenuDetailsScreen.Row>();
        String diagnosis=result.message().toLowerCase(java.util.Locale.ROOT);
        if(java.util.regex.Pattern.compile("(needs|try|use|switch to) run\\b").matcher(diagnosis).find())
            advice.add(new MenuDetailsScreen.Row("",UiMessages.text("editor.stageplay_workspace.use_run_15gh5is")));
        if(java.util.regex.Pattern.compile("more (attempt )?frames").matcher(diagnosis).find())
            advice.add(new MenuDetailsScreen.Row("",UiMessages.text("editor.stageplay_workspace.allow_more_frames_adqkt8")));
        if(java.util.regex.Pattern.compile("(needs|try|use) spindash\\b").matcher(diagnosis).find())
            advice.add(new MenuDetailsScreen.Row("",UiMessages.text("editor.stageplay_workspace.use_spindash_ux26yz")));
        if(!advice.isEmpty())sections.add(new MenuDetailsScreen.Section(UiMessages.text("editor.stageplay_workspace.try_1t3v"),advice,false));
        var motion=result.request().motion();
        String settings=(result.request().footprint() == null ? UiMessages.text("editor.stageplay_workspace.start_speed_stop_face_1b6v88j", Map.of("value0", String.valueOf(routeSetting(motion.startingAction())), "value1", String.valueOf(routeSetting(motion.travelSpeed())), "value2", String.valueOf(routeSetting(motion.endingAction())), "value3", String.valueOf(routeSetting(motion.endingFacing())))) : UiMessages.text("editor.stageplay_workspace.walk_speed_px_frame_hywkx6", Map.of("value0", String.valueOf(result.request().objectSpeed()))));
        sections.add(new MenuDetailsScreen.Section(UiMessages.text("editor.stageplay_workspace.settings_osmo8z"),List.of(new MenuDetailsScreen.Row("",settings)),false));
        sections.add(new MenuDetailsScreen.Section(UiMessages.text("editor.stageplay_workspace.search_14w27d4"),List.of(new MenuDetailsScreen.Row("",
                UiMessages.text("editor.stageplay_workspace.samples_nodes_ticks_wpk0j4", Map.of("value0", String.valueOf(result.path().size()), "value1", String.valueOf(result.expandedNodes()), "value2", String.valueOf(result.simulatedTicks()))))),true));
        var buttons=new ArrayList<MenuDetailsScreen.Action>();
        var actions=menu.actionEntries();
        boolean apply=routeUpdate.phase()==StageplayRouteJob.Phase.READY;
        // A ready route's next step is Apply; otherwise the next step is another plan.
        RouteMenuAction primary=apply?RouteMenuAction.APPLY:RouteMenuAction.REPLAN;
        var order=new java.util.LinkedHashSet<>(List.of(primary,RouteMenuAction.REPLAN,RouteMenuAction.APPLY,RouteMenuAction.CANCEL));
        for(RouteMenuAction kind:order) {
            Integer index=indices.get(kind);
            if(index==null) continue;
            String shown=(kind == RouteMenuAction.APPLY ? UiMessages.text("editor.stageplay_workspace.apply_11sitq") : actions.get(index).label());
            buttons.add(new MenuDetailsScreen.Action(shown,()->menu.runAction(index),kind==primary));
        }
        menu.setDetailsContent(new MenuDetailsScreen.Content(UiMessages.text("editor.stageplay_workspace.route_details_moyzbf"),status,meaning,
                result.success()?MenuDetailsScreen.Tone.SUCCESS:MenuDetailsScreen.Tone.WARNING,sections,
                menu.helpText(),buttons,(apply ? UiMessages.text("editor.stageplay_workspace.apply_replaces_commands_in_the_requested_window_4bhyn7") : "")));
    }

    private static String routeSetting(Enum<?> value) { return EditorLabels.value(value.name()); }

    private void routeForm() {
        int frame = controller.frame();
        var document = controller.document();
        boolean entry = frame == 0 || document.sceneChanges().stream().anyMatch(c -> c.frame()==frame);
        var objectEntry = document.objectActors().stream().filter(actor -> actor.keys().stream().anyMatch(key ->
                key.frame()==frame && key.visible() && key.mode()==ObjectMode.PUPPET)).findFirst();
        if (!entry && objectEntry.isEmpty())
            throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.players_start_at_a_shot_entry_objects_start_at_an_exist_kks8rw"));
        String actor = controller.selectedActorId();
        if (!entry) actor = objectEntry.orElseThrow().id();
        else if (actor.isEmpty()) actor = document.puppets().isEmpty()
                ? document.objectActors().stream().findFirst().map(ObjectActor::id).orElse("") : document.puppets().getFirst().actorId();
        var view = StageplayTimeline.sample(document,frame);
        String selected=actor;
        boolean objectRoute=document.objectActors().stream().anyMatch(a->a.id().equals(selected));
        var previous=document.puppets().stream().filter(t->t.actorId().equals(selected))
                .flatMap(t->t.commands().stream()).filter(c->c.frame()>=frame && c.frame()<shotEnd(document,frame) && c.kind()==PuppetCommandKind.MOVE_TO).findFirst().orElse(null);
        RouteMotion motion=previous==null?RouteMotion.DEFAULT:previous.motion();
        var routeFields=new ArrayList<>(List.of(
                text("actor",UiMessages.text("editor.stageplay_workspace.puppet_actor_object_id_msp184"),actor), number("x",UiMessages.text("editor.stageplay_workspace.destination_centre_x_n6ukw5"),previous==null?view.camera().x()+160:previous.x()),
                number("y",UiMessages.text("editor.stageplay_workspace.destination_centre_y_n6ukw6"),previous==null?view.camera().y()+112:previous.y()),
                number("frames",UiMessages.text("editor.stageplay_workspace.attempt_frames_1tgqird"),Math.min(300,shotEnd(document,frame)-frame)),
                number("nodes",UiMessages.text("editor.stageplay_workspace.maximum_search_nodes_1737qe1"),1024),number("ticks",UiMessages.text("editor.stageplay_workspace.maximum_probe_ticks_1i7v6xy"),120000),
                number("millis",UiMessages.text("editor.stageplay_workspace.search_time_budget_ms_1n0zs0r"),60000),number("urgency",UiMessages.text("editor.stageplay_workspace.player_urgency_0_1_14oigly"),1),
                number("speed",UiMessages.text("editor.stageplay_workspace.visual_object_speed_13ayl0m"),1),number("halfWidth",UiMessages.text("editor.stageplay_workspace.object_half_width_12ocutp"),8),
                number("height",UiMessages.text("editor.stageplay_workspace.object_clearance_height_1ikcojw"),16),number("footOffset",UiMessages.text("editor.stageplay_workspace.object_centre_to_foot_y_1n6gb1k"),8),
                number("collisionPath",UiMessages.text("editor.stageplay_workspace.object_collision_path_0_1_1v78mjq"),0)));
        if(!objectRoute) {
            var nativeActor=document.actors().stream().filter(a->a.id().equals(selected)).findFirst().orElseThrow();
            var starts=new ArrayList<>(List.of("Walk","Jump"));
            try {
                if(host.movementAbilities(nativeActor,assetRoot).contains(com.openggf.game.CharacterDefinition.MovementAbility.SPINDASH)) starts.add("Spindash");
            } catch(IOException failure) { report(failure); }
            routeFields.add(4,new EditorProjectMenu.Field("startingAction",UiMessages.text("editor.stageplay_workspace.starting_action_1fmzr3q"),starts.contains(display(motion.startingAction())) ? display(motion.startingAction()) : "Spindash (unavailable)",
                    starts.contains(display(motion.startingAction())) ? starts : java.util.stream.Stream.concat(
                            java.util.stream.Stream.of("Spindash (unavailable)"),starts.stream()).toList()));
            routeFields.add(5,new EditorProjectMenu.Field("travelSpeed",UiMessages.text("editor.stageplay_workspace.travel_speed_jbc1y9"),display(motion.travelSpeed()),List.of("Walk","Run")));
            routeFields.add(6,new EditorProjectMenu.Field("endingAction",UiMessages.text("editor.stageplay_workspace.ending_action_1g6lwrz"),display(motion.endingAction()),List.of("Gentle Stop","Hard Stop","Overshoot")));
            routeFields.add(7,new EditorProjectMenu.Field("endingFacing",UiMessages.text("editor.stageplay_workspace.ending_facing_1iifkr7"),display(motion.endingFacing()),List.of("Inherit","Left","Right")));
            routeFields.removeIf(f->List.of("urgency","speed","halfWidth","height","footOffset","collisionPath").contains(f.key()));
        }
        menu.showForm((objectRoute ? UiMessages.text("editor.stageplay_workspace.walking_object_route_xdl7db") : UiMessages.text("editor.stageplay_workspace.plan_playable_route_1s2i7e6")),routeFields,UiMessages.text("editor.stageplay_workspace.plan_in_isolated_worker_1pzhr5"),values -> io(() -> {
            String id = values.get("actor");
            boolean object = document.objectActors().stream().anyMatch(a -> a.id().equals(id));
            if (!object && !entry) throw new IOException(UiMessages.text("editor.stageplay_workspace.player_routes_start_at_a_shot_entry_frame_15kn6mb"));
            var footprint = object ? new StageplayRouteFootprint(integer(values,"halfWidth"),integer(values,"height"),
                    integer(values,"footOffset"),integer(values,"collisionPath")) : null;
            if (!object && values.get("startingAction").contains("unavailable"))
                throw new IOException(UiMessages.text("editor.stageplay_workspace.saved_spindash_start_is_unavailable_for_this_actor_choo_u1v455"));
            var request = new StageplayRouteRequest(id,frame,decimal(values,"x"),decimal(values,"y"),integer(values,"frames"),
                    integer(values,"nodes"),integer(values,"ticks"),integer(values,"millis"),1,footprint,object?decimal(values,"speed"):1,
                    object?RouteMotion.DEFAULT:new RouteMotion(StartAction.valueOf(enumValue(values,"startingAction")),
                            TravelSpeed.valueOf(enumValue(values,"travelSpeed")),EndingAction.valueOf(enumValue(values,"endingAction")),
                            EndingFacing.valueOf(enumValue(values,"endingFacing"))));
            String unavailable=StageplayRouteContract.unavailableReason(document,request);
            if(!unavailable.isEmpty()) throw new IOException(unavailable);
            host.startRoute(document,assetRoot,request);
            controller.selectActor(id); proposedOverlay=null;
            routeSource=document; routeRoot=assetRoot; routeSelection=controller.selectedActorId(); routeRequest=request;
            routeUpdate=null; status=UiMessages.text("editor.stageplay_workspace.route_planning_started_in_a_separate_logic_worker_reope_7afkat");
        }));
        menu.onValueChanged((key, value) -> {
            if (key.equals("startingAction") && value.equals("Spindash")) menu.setValue("travelSpeed", "Run");
        });
        if (objectRoute) menu.setSubtitle(UiMessages.text("editor.stageplay_workspace.objects_visual_foreground_only_1whjucr"));
        else menu.setHelp(UiMessages.text("editor.stageplay_workspace.find_a_playable_path_to_a_destination_mivqhh"),
                UiMessages.text("editor.stageplay_workspace.player_routes_start_at_shot_entry_advanced_keeps_route_tpazk6"));
        status=UiMessages.text("editor.stageplay_workspace.choose_a_destination_then_find_a_route_iwmtmq");
    }

    private static String display(Enum<?> value) {
        return java.util.Arrays.stream(value.name().split("_")).map(word->word.charAt(0)+word.substring(1).toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(" "));
    }
    private static String enumValue(Map<String,String> values,String key) {
        return values.get(key).toUpperCase(java.util.Locale.ROOT).replace(' ','_');
    }

    private void pollRoute() {
        if (routeSource == null) return;
        var next = host.pollRoute();
        if (next == null) return;
        if (!routeSource.equals(controller.document()) || !routeRoot.equals(assetRoot)
                || controller.frame()!=routeRequest.startFrame() || !routeSelection.equals(controller.selectedActorId())) {
            cancelRoute(); status=UiMessages.text("editor.stageplay_workspace.stale_route_discarded_scene_asset_root_selected_actor_o_1w8ulw8"); return;
        }
        if (next.equals(routeUpdate)) return;
        routeUpdate=next; status=next.message();
        if (next.result()!=null) {
            proposedOverlay=new com.openggf.stageplay.editor.StageplayRouteOverlay(next.result(),false);
            status=proposedOverlay.statusText();
        }
        if (next.phase()==StageplayRouteJob.Phase.APPLY_READY) {
            try {
                StageplayRouteProtocol.validatePatch(routeSource,routeRequest,next.result().fingerprint(),next.result());
                var candidate=next.result().candidate();
                if(candidate==null) throw new IOException(UiMessages.text("editor.stageplay_workspace.route_did_not_return_a_verified_candidate_fe1cgl"));
                edit(ignored -> candidate);
                appliedOverlay=new com.openggf.stageplay.editor.StageplayRouteOverlay(next.result(),true);
                appliedRouteRoot=assetRoot; cancelRoute();
                status=UiMessages.text("editor.stageplay_workspace.applied_collision_route_as_one_undo_step_playback_remai_vbgiyu");
            } catch(IOException | RuntimeException failure) { cancelRoute(); report(failure); }
        }
    }
    private void cancelRoute() {
        proposedOverlay=null;
        host.cancelRoute(); routeSource=null; routeRoot=null; routeSelection=null; routeRequest=null; routeUpdate=null;
        status=UiMessages.text("editor.stageplay_workspace.route_attempt_cancelled_baaop3");
    }

    private void exportForm() {
        save(() -> menu.showForm(UiMessages.text("editor.stageplay_workspace.export_stageplay_film_16sk7mm"),List.of(file("out",UiMessages.text("editor.stageplay_workspace.new_mkv_file_zhuldw"),assetRoot.resolve(slug(controller.document().name())+".mkv").toString())),UiMessages.text("editor.stageplay_workspace.export_yk542c"),v->io(()->{
            host.validate(controller.document(),assetRoot);
            host.export(controller.document(),path,Path.of(v.get("out")).toAbsolutePath().normalize());
            status=UiMessages.text("editor.stageplay_workspace.film_export_started_in_a_separate_process_see_the_expor_t4an3i");
        })));
    }

    private void edit(UnaryOperator<StageplayDocument> operation) { controller.edit(operation); cameraDraft = null; status=UiMessages.text("editor.stageplay_workspace.scene_edited_w7itst"); }
    public void report(Throwable failure) { status=failure.getMessage()==null?failure.toString():failure.getMessage(); menu.report(failure); }
    public void setStatus(String value) { status=Objects.requireNonNull(value); }
    static String nextId(String prefix,StageplayDocument doc) {
        var used=allIds(doc); int i=1; while(used.contains(prefix+i)) i++; return prefix+i;
    }
    private static java.util.Set<String> allIds(StageplayDocument doc) {
        var used = new java.util.HashSet<String>();
        doc.actors().forEach(a -> used.add(a.id())); doc.captions().forEach(c -> used.add(c.id())); doc.audio().forEach(c -> used.add(c.id()));
        doc.cameraEffects().forEach(e -> used.add(e.id())); doc.sceneChanges().forEach(c -> used.add(c.id()));
        doc.dialogue().forEach(c -> used.add(c.id()));
        doc.puppets().forEach(p -> p.commands().forEach(c -> used.add(c.id())));
        doc.objectActors().forEach(a -> { used.add(a.id()); a.commands().forEach(c -> used.add(c.id())); });
        doc.worldCommands().forEach(c -> used.add(c.id()));
        return used;
    }
    private static String escapeCaption(String text) { return text.replace("\\","\\\\").replace("\n","\\n"); }
    private static String unescapeCaption(String text) {
        var value=new StringBuilder();
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='\\' && i+1<text.length()) { char next=text.charAt(++i); if(next=='n') value.append('\n'); else if(next=='\\') value.append('\\'); else throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.use_n_for_a_new_line_and_for_a_backslash_1yvt9sa")); }
            else value.append(c);
        }
        return value.toString();
    }
    private static String slug(String text) { String value=text.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+","-"); return value.isBlank()?"scene":value; }
    private static EditorProjectMenu.Action action(String label,Runnable callback) { return new EditorProjectMenu.Action(label,callback); }
    private static EditorProjectMenu.Action action(String fullLabel, String compactLabel, boolean primary, Runnable callback) {
        return StageplayMenuPresentation.action(fullLabel, compactLabel, primary, callback);
    }
    private static EditorProjectMenu.Field text(String key,String label,String value) { return new EditorProjectMenu.Field(key,label,value,MenuTextEditor.Mode.TEXT); }
    private static EditorProjectMenu.Field file(String key,String label,String value) { return new EditorProjectMenu.Field(key,label,value,MenuTextEditor.Mode.PATH); }
    private static EditorProjectMenu.Field number(String key,String label,Number value) { return text(key,label,value.toString()); }
    private static int integer(Map<String,String> v,String key) { return Integer.parseInt(v.get(key).trim()); }
    private static double decimal(Map<String,String> v,String key) { return Double.parseDouble(v.get(key).trim()); }
    private static boolean bool(Map<String,String> v,String key) {
        if (v.get(key).equalsIgnoreCase("true")) return true;
        if (v.get(key).equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(UiMessages.text("editor.stageplay_workspace.must_be_true_or_false_dba4ph", Map.of("value0", String.valueOf(key))));
    }
    @FunctionalInterface private interface IoTask { void run() throws IOException; }
    private static void io(IoTask task) { try { task.run(); } catch(IOException failure) { throw new UncheckedIOException(failure); } }
}
