package com.openggf.stageplay.editor;

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
        CAMERA_KEY("Camera keys"), CAPTION("Captions"), AUDIO("Audio cues"),
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
    public void deactivate() { stop(); menu.close(); active = false; }

    public void seek(int frame) { stop(); controller.seek(frame); }
    public void selectActor(String id) { stop(); controller.selectActor(id); }

    public void invoke(Action action) {
        try {
            host.finishPendingEdits();
            if (action != Action.PLAY && action != Action.RECORD) stop();
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
                case CAMERA_KEY -> cameraMenu();
                case CAPTION -> captionMenu();
                case AUDIO -> audioMenu();
                case RECORD -> { if (recording()) stop(); else startTake(); }
                case PLAY -> { if (controller.playing() || recording()) stop(); else play(); }
                case STOP -> controller.seek(0);
                case UNDO -> controller.undo();
                case REDO -> controller.redo();
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
                recordStep(input.isKeyDown(GLFW_KEY_D), input.isKeyDown(GLFW_KEY_A),
                        input.isKeyDown(GLFW_KEY_S), input.isKeyDown(GLFW_KEY_W));
            } else if (startedThisTick) startedThisTick = false;
            else if (controller.playing()) {
                if (controller.advance()) host.present(controller.document(), controller.frame());
            } else if (previewSoundActive) stop();
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
        var candidate = takeDocument(takeKeys);
        controller.edit(ignored -> candidate);
        // A failed stop or commit must leave the last valid take available for retry.
        takeBefore = null; takeKeys = null; takeActor = null;
        status = "Recorded visual take. Undo removes the whole take.";
    }

    private void play() {
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
    public StageplayDocument previewDocument() {
        if (!recording()) return controller.document();
        return takeDocument(takeKeys);
    }

    private StageplayDocument takeDocument(List<ActorKey> keys) {
        return takeBefore.withActors(takeBefore.actors().stream().map(a -> a.id().equals(takeActor)
                ? new Actor(a.id(), a.name(), a.gameId(), a.character(), keys) : a).toList());
    }

    private void newDocument() {
        controller.replace(host.initialDocument());
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
        if (!assetRoot.equals(target.getParent()) && controller.anyRevision(d -> !d.set().levelProject().isEmpty()
                || d.audio().stream().anyMatch(c -> !c.asset().isEmpty())))
            throw new IOException("This scene's undo history uses relative assets. Save beside them, or reopen the scene to start a new editing history before relocating it.");
        StageplayDocument candidate = rebase(controller.document(), assetRoot, target.getParent());
        Files.createDirectories(target.getParent());
        StageplayStore.write(target, candidate);
        // Rebasing is a document edit, preserving existing undo history and meaningful dirty state.
        controller.edit(ignored -> candidate); controller.markSaved();
        path = target; assetRoot = target.getParent(); status = "Saved " + target;
    }

    private static StageplayDocument rebase(StageplayDocument doc, Path oldRoot, Path newRoot) throws IOException {
        if (!oldRoot.equals(newRoot) && (!doc.set().levelProject().isEmpty() || doc.audio().stream().anyMatch(c -> !c.asset().isEmpty())))
            throw new IOException("Save beside the existing scene assets. To move the project, move its whole folder so relative paths and undo history stay valid.");
        var set = doc.set();
        String project = relative(oldRoot, newRoot, set.levelProject());
        var audio = new ArrayList<AudioCue>();
        for (var cue : doc.audio()) audio.add(new AudioCue(cue.id(), cue.frame(), cue.kind(), cue.gameId(),
                cue.soundId(), relative(oldRoot, newRoot, cue.asset()), cue.gain()));
        return doc.withSet(new SetSource(set.gameId(), set.zone(), set.act(), set.romSha1(), project)).withAudio(audio);
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
            var camera = StageplayTimeline.sample(controller.document(), 0).camera();
            Actor value = new Actor(id,v.get("name"),v.get("game"),v.get("character"),add
                    ? List.of(new ActorKey(0,camera.x()+controller.document().width()/2.0,camera.y()+controller.document().height()/2.0,0,-1,false,true,1,Interpolation.HOLD)) : actor.keys());
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
                    edit(d -> d.withCamera(d.camera().stream().filter(k -> k.frame()!=controller.frame()).toList()));
                })));
    }

    private void cameraForm() {
        var camera = StageplayTimeline.sample(controller.document(), controller.frame()).camera(); int frame = controller.frame();
        var interpolation = controller.document().camera().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow().interpolation();
        menu.showForm("Camera key at frame " + frame,List.of(number("x","World left X",camera.x()),number("y","World top Y",camera.y()),
                number("zoom","Zoom",camera.zoom()),text("interpolation","HOLD / LINEAR / SMOOTH",interpolation.name())),"Set camera",v -> edit(d -> {
            var keys = new ArrayList<>(d.camera()); keys.removeIf(k -> k.frame()==frame);
            keys.add(new CameraKey(frame,decimal(v,"x"),decimal(v,"y"),decimal(v,"zoom"),Interpolation.valueOf(v.get("interpolation").toUpperCase(java.util.Locale.ROOT))));
            keys.sort(Comparator.comparingInt(CameraKey::frame)); return d.withCamera(keys);
        }));
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

    private void edit(UnaryOperator<StageplayDocument> operation) { controller.edit(operation); status="Scene edited"; }
    public void report(Throwable failure) { status=failure.getMessage()==null?failure.toString():failure.getMessage(); menu.report(failure); }
    public void setStatus(String value) { status=Objects.requireNonNull(value); }
    private static String nextId(String prefix,StageplayDocument doc) {
        var used = new java.util.HashSet<String>();
        doc.actors().forEach(a -> used.add(a.id())); doc.captions().forEach(c -> used.add(c.id())); doc.audio().forEach(c -> used.add(c.id()));
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
