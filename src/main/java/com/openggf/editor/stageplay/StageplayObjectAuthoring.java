package com.openggf.editor.stageplay;

import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.game.MenuTextEditor;
import com.openggf.stageplay.StageplayController;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.StageplayObjectCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.openggf.stageplay.StageplayDocument.*;

/** Shared object authoring forms: both editor frontends invoke these same commands. */
final class StageplayObjectAuthoring {
    private final EditorProjectMenu menu;
    private final StageplayController controller;
    private final Consumer<UnaryOperator<StageplayDocument>> edit;
    private final Supplier<StageplayTimeline.Frame> preview;
    private final Supplier<List<com.openggf.stageplay.runtime.StageplayObjectInspection>> inspection;

    StageplayObjectAuthoring(EditorProjectMenu menu, StageplayController controller,
                            Consumer<UnaryOperator<StageplayDocument>> edit,
                            Supplier<StageplayTimeline.Frame> preview,
                            Supplier<List<com.openggf.stageplay.runtime.StageplayObjectInspection>> inspection) {
        this.menu = menu; this.controller = controller; this.edit = edit; this.preview = preview;
        this.inspection = inspection;
    }

    void show() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Cast ROM object", () -> properties(null)));
        actions.add(action("ROM object coverage / restrictions", this::coverage));
        actions.add(action("Loaded level objects", this::loaded));
        actions.add(action("Clear badniks", () -> clear(WorldCommandKind.CLEAR_BADNIKS)));
        actions.add(action("Clear objects", () -> clear(WorldCommandKind.CLEAR_OBJECTS)));
        actions.add(action("World commands / removals", this::worldCommands));
        for (var actor : document().objectActors())
            actions.add(action(actor.name() + " / " + actor.id(), () -> actorMenu(actor.id())));
        menu.showActions("ROM object cast", actions);
        menu.setSubtitle("Free runs the native routine and its world effects. Puppet/Pose animate visual parts with AI and contacts suspended. Clear commands preserve players, attached effects and rings.");
    }

    private void coverage() {
        var set = StageplayTimeline.sample(document(), controller.frame()).set();
        var entries = StageplayObjectCatalog.inspect(set.gameId(), set.zone(), set.act()).entries();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        for (var entry : entries) actions.add(action(entry.objectId()+" / "+entry.name(), () -> {
            var details = new ArrayList<EditorProjectMenu.Action>();
            if (!entry.appearanceExclusions().isEmpty()) details.add(action("Declared appearance exclusions",
                    () -> exclusions(entry.appearanceExclusions(), this::coverage)));
            details.add(action("Back to coverage", this::coverage));
            menu.showActions(entry.objectId()+" / "+entry.name(), details);
            menu.setSubtitle(entry.freeRestriction()+". "+entry.citation()+". "+entry.modeSummary());
        }));
        menu.showActions("Every native object ID / "+set.gameId(), actions);
    }

    private void loaded() {
        var frame = preview.get();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        if (frame.world() != null) for (var object : frame.world().objects()) {
            if (object.layoutIndex() < 0) continue;
            var existing = document().objectActors().stream().filter(a -> a.layoutIndex() == object.layoutIndex()
                    && a.objectId() == object.objectId() && a.subtype() == object.subtype()
                    && a.gameId().equals(frame.set().gameId()) && a.zone() == frame.set().zone()
                    && a.act() == frame.set().act()).findFirst();
            actions.add(action("#" + object.layoutIndex() + " / " + object.objectId() + ":" + object.subtype()
                    + " / " + object.type(), () -> {
                if (existing.isPresent()) actorMenu(existing.get().id());
                else {
                    var id = id("object");
                    var actor = new ObjectActor(id, object.type(), frame.set().gameId(), frame.set().zone(),
                            frame.set().act(), object.objectId(), object.subtype(), object.layoutIndex(),
                            placementBoundaryKeys(frame.set(), new ObjectKey(0, object.x(), object.y(), 0, -1, false, true,
                                    Interpolation.HOLD, ObjectMode.FREE)), List.of());
                    edit.accept(d -> d.withObjectActors(append(d.objectActors(), actor)));
                    actorMenu(id);
                }
            }));
        }
        actions.add(action("Back to cast", this::show));
        menu.showActions("Loaded objects / portable layout identities", actions);
        menu.setSubtitle("Targets use layoutIndex + objectId + subtype. No runtime slot is saved. Objects remain native until explicitly directed or removed.");
    }

    private void properties(ObjectActor actor) {
        var set = StageplayTimeline.sample(document(), controller.frame()).set();
        var key = actor == null ? new ObjectKey(0, 96, 96, 0, -1, false, true, Interpolation.HOLD, ObjectMode.FREE)
                : actor.keys().getFirst();
        menu.showForm(actor == null ? "Cast ROM object" : "Object properties", List.of(
                text("name", "Name", actor == null ? "ROM object" : actor.name()),
                text("game", "Source ROM: s1 / s2 / s3k", actor == null ? set.gameId() : actor.gameId()),
                number("zone", "Native source zone", actor == null ? set.zone() : actor.zone()),
                number("act", "Native source act", actor == null ? set.act() : actor.act()),
                number("object", "ROM object ID (decimal)", actor == null ? 1 : actor.objectId()),
                number("subtype", "Subtype", actor == null ? 0 : actor.subtype()),
                number("layout", "Layout index (-1 = new cast)", actor == null ? -1 : actor.layoutIndex()),
                text("sourceKind", "LAYOUT_OBJECT / RELEASED_ANIMAL", actor == null ? "LAYOUT_OBJECT" : actor.sourceKind().name()),
                number("x", "Initial anchor X", key.x()), number("y", "Initial anchor Y", key.y()),
                text("mode", "Initial mode: FREE / PUPPET / POSE", key.mode().name())), "Apply object", values -> {
            String actorId = actor == null ? id("object") : actor.id();
            var mode = ObjectMode.valueOf(upper(values,"mode"));
            var first = new ObjectKey(0, decimal(values,"x"), decimal(values,"y"),
                    mode == ObjectMode.FREE ? 0 : key.animation(), mode == ObjectMode.FREE ? -1 : key.pose(),
                    key.flipX(), key.visible(), mode == ObjectMode.FREE ? Interpolation.HOLD : key.interpolation(),
                    mode, mode == ObjectMode.FREE ? List.of() : key.parts());
            var keys = actor == null ? boundaryKeys(first) : replaceKey(actor.keys(), first);
            int layout = integer(values,"layout"), zone = integer(values,"zone"), act = integer(values,"act");
            String game = values.get("game");
            if (layout >= 0) {
                boolean sourceChanged = actor != null && (!actor.gameId().equals(game) || actor.zone() != zone
                        || actor.act() != act || actor.layoutIndex() < 0);
                keys = scopePlacementKeys(keys, game, zone, act, sourceChanged);
            }
            var replacement = new ObjectActor(actorId, values.get("name"), values.get("game"),
                    zone, act, integer(values,"object"), integer(values,"subtype"),
                    layout, keys, actor == null ? List.of() : actor.commands(), ObjectSourceKind.valueOf(upper(values,"sourceKind")));
            replace(replacement); actorMenu(actorId);
        });
        restriction(actor);
    }

    private void actorMenu(String actorId) {
        var actor = actor(actorId);
        menu.showActions(actor.name(), List.of(
                action("Source / anchor / properties", () -> properties(actor)),
                action("Mode / position / animation keys", () -> keys(actorId)),
                action("Puppet move / stop commands", () -> commands(actorId)),
                action("Remove from world at playhead", () -> removeAtPlayhead(actorId)),
                action("Available parts / appearance exclusions", () -> parts(actorId)),
                action("Delete authored role and dialogue", () -> edit.accept(d -> d
                        .withDialogue(d.dialogue().stream().filter(c -> !c.speakerActor().equals(actorId)).toList())
                        .withVoices(d.voices().stream().filter(v -> !v.speakerActor().equals(actorId)).toList())
                        .withWorldCommands(d.worldCommands().stream()
                                .filter(c -> !c.actorId().equals(actorId)).toList())
                        .withObjectActors(d.objectActors().stream().filter(a -> !a.id().equals(actorId)).toList()))),
                action("Back to cast", this::show),
                action("Edit individual part overrides", () -> partKeys(actorId))));
        restriction(actor);
    }

    private void parts(String actorId) {
        var value = inspection.get().stream().filter(item -> item.actorId().equals(actorId)).findFirst();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        value.ifPresent(item -> {
            for (var part : item.parts()) actions.add(action(part.partId(), () -> {
                menu.showActions(part.partId(), List.of(action("Back to parts", () -> parts(actorId))));
                menu.setSubtitle("Captured frame " + part.defaultPose() + "; mapping frames " + part.mappingFrames()
                        + "; pure ROM animations " + part.animations() + ". Enter this path in the key's part overrides.");
            }));
        });
        var actor = actor(actorId);
        if (actor.sourceKind() == ObjectSourceKind.LAYOUT_OBJECT) {
            var declared = StageplayObjectCatalog.entry(actor.gameId(), actor.zone(), actor.act(), actor.objectId()).appearanceExclusions();
            if (!declared.isEmpty()) actions.add(action("Declared appearance exclusions", () -> exclusions(declared, () -> parts(actorId))));
        }
        actions.add(action("Back to object", () -> actorMenu(actorId)));
        menu.showActions("Native visual parts / " + actor(actorId).name(), actions);
        menu.setSubtitle(value.map(item -> String.join(". ", item.limitations()))
                .filter(text -> !text.isBlank()).orElse("No initialized visual graph is available at this frame. Start in Free in its native set, then switch once the native appearance has initialized."));
    }

    private void keys(String actorId) {
        var actor = actor(actorId);
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Set key at playhead " + controller.frame(), () -> keyForm(actorId, controller.frame())));
        for (var key : actor.keys()) actions.add(action(key.frame() + " / " + key.mode(), () -> keyForm(actorId, key.frame())));
        actions.add(action("Back to object", () -> actorMenu(actorId)));
        menu.showActions("Object mode / visual keys", actions);
        restriction(actor);
    }

    private void keyForm(String actorId, int frame) {
        var actor = actor(actorId);
        var key = actor.keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
        String formattedParts = formatParts(key.parts());
        boolean bulkParts = formattedParts.length() <= 8192;
        var fields = new ArrayList<>(List.of(
                number("frame", "Frame", frame), text("mode", "FREE / PUPPET / POSE", key.mode().name()),
                number("x", "World X", key.x()), number("y", "World Y", key.y()),
                number("animation", "Pure ROM animation (0 default)", key.animation()),
                number("pose", "Mapping frame (-1 = animation / held fallback)", key.pose()),
                text("flip", "Flip X: true / false", Boolean.toString(key.flipX())),
                text("visible", "Visible: true / false", Boolean.toString(key.visible())),
                text("interpolation", "HOLD / LINEAR / SMOOTH", key.interpolation().name())));
        if (bulkParts) fields.add(new EditorProjectMenu.Field("parts", "Part overrides: root/0:animation:frame; ...",
                formattedParts, MenuTextEditor.Mode.TEXT, 8192));
        fields.add(text("delete", "Delete this key: true / false", "false"));
        menu.showForm("Object key " + frame, fields, "Apply key", values -> {
            int at = integer(values,"frame");
            var keys = new ArrayList<>(actor.keys());
            keys.removeIf(k -> k.frame() == frame || k.frame() == at);
            if (!bool(values,"delete")) keys.add(new ObjectKey(at, decimal(values,"x"), decimal(values,"y"),
                    integer(values,"animation"), integer(values,"pose"), bool(values,"flip"), bool(values,"visible"),
                    Interpolation.valueOf(upper(values,"interpolation")), ObjectMode.valueOf(upper(values,"mode")),
                    bulkParts ? parseParts(values.get("parts")) : key.parts()));
            keys.sort(Comparator.comparingInt(ObjectKey::frame));
            replace(withKeys(actor, keys)); keys(actorId);
        });
        restriction(actor);
        if (!bulkParts) menu.setSubtitle(restrictionText(actor)
                + ". This key has " + key.parts().size() + " overrides. Use the object's Edit individual part overrides menu; position/key edits preserve every part.");
    }

    private void partKeys(String actorId) {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Part overrides at playhead " + controller.frame(), () -> partOverrides(actorId, controller.frame())));
        for (var key : actor(actorId).keys())
            actions.add(action("Frame " + key.frame() + " / " + key.parts().size() + " overrides", () -> partOverrides(actorId, key.frame())));
        actions.add(action("Back to object", () -> actorMenu(actorId)));
        menu.showActions("Choose object part key", actions);
    }

    private void partOverrides(String actorId, int frame) {
        var key = actor(actorId).keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add part override", () -> partForm(actorId, frame, null)));
        for (var part : key.parts()) actions.add(action(part.partId() + " / " + part.animation() + ":" + part.pose(),
                () -> partForm(actorId, frame, part)));
        actions.add(action("Back to key", () -> keyForm(actorId, frame)));
        actions.add(action("Back to object", () -> actorMenu(actorId)));
        menu.showActions("Part overrides / frame " + frame, actions);
        menu.setSubtitle("Each part path accepts up to 256 characters; up to 256 overrides are retained. Free uses native appearance and cannot have overrides.");
    }

    private void partForm(String actorId, int frame, ObjectPartPose old) {
        menu.showForm(old == null ? "Add object part override" : "Edit object part override", List.of(
                text("partId", "Portable part path", old == null ? "root/0" : old.partId()),
                number("animation", "Pure ROM animation (0 default)", old == null ? 0 : old.animation()),
                number("pose", "Mapping frame (-1 = animation / held fallback)", old == null ? -1 : old.pose()),
                text("delete", "Delete override: true / false", "false")), "Apply part", values -> {
            var actor = actor(actorId);
            var key = actor.keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
            var parts = new ArrayList<>(key.parts());
            int index = old == null ? -1 : parts.indexOf(old);
            if (bool(values,"delete")) { if (index >= 0) parts.remove(index); }
            else {
                var part = new ObjectPartPose(values.get("partId"), integer(values,"animation"), integer(values,"pose"));
                if (index >= 0) parts.set(index, part); else parts.add(part);
            }
            var replacement = new ObjectKey(frame, key.x(), key.y(), key.animation(), key.pose(), key.flipX(),
                    key.visible(), key.interpolation(), key.mode(), parts);
            replace(withKeys(actor, replaceKey(actor.keys(), replacement)));
            partOverrides(actorId, frame);
        });
    }

    private void exclusions(List<String> declared, Runnable back) {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        for (int index = 0; index < declared.size(); index++) {
            int selected = index;
            actions.add(action("Appearance restriction " + (index + 1), () -> {
                menu.showActions("Appearance restriction " + (selected + 1),
                        List.of(action("Back to restrictions", () -> exclusions(declared, back))));
                menu.setSubtitle(declared.get(selected) + "  F1 opens the complete reason and routine citation.");
            }));
        }
        actions.add(action("Back", back));
        menu.showActions("Declared native appearance exclusions", actions);
        menu.setSubtitle("Each entry describes a source-owned limitation with its native routine citation.");
    }

    private void commands(String actorId) {
        var actor = actor(actorId);
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add move / stop at playhead", () -> commandForm(actorId, null)));
        for (var command : actor.commands()) actions.add(action(command.frame() + " / " + command.kind(), () -> commandForm(actorId, command)));
        actions.add(action("Back to object", () -> actorMenu(actorId)));
        menu.showActions("Visual Puppet commands", actions);
        menu.setSubtitle("MOVE_TO moves the visual anchor by speed pixels per frame. It supplies no controller input and never runs native object AI. Use Pose keys for authored frames and child part overrides.");
    }

    private void commandForm(String actorId, ObjectCommand old) {
        menu.showForm("Visual Puppet instruction", List.of(number("frame", "Frame", old == null ? controller.frame() : old.frame()),
                text("kind", "MOVE_TO / STOP", old == null ? "MOVE_TO" : old.kind().name()),
                number("x", "Destination X", old == null ? 160 : old.x()), number("y", "Destination Y", old == null ? 96 : old.y()),
                number("speed", "Pixels per frame", old == null ? 2 : old.speed()),
                text("delete", "Delete instruction: true / false", "false")), "Apply instruction", values -> {
            var actor = actor(actorId); var commands = new ArrayList<>(actor.commands());
            if (old != null) commands.remove(old);
            if (!bool(values,"delete")) {
                var kind = ObjectCommandKind.valueOf(upper(values,"kind"));
                commands.add(new ObjectCommand(old == null ? id("object-action") : old.id(), integer(values,"frame"), kind,
                        kind == ObjectCommandKind.STOP ? 0 : decimal(values,"x"), kind == ObjectCommandKind.STOP ? 0 : decimal(values,"y"),
                        kind == ObjectCommandKind.STOP ? 0 : decimal(values,"speed")));
            }
            commands.sort(Comparator.comparingInt(ObjectCommand::frame));
            replace(new ObjectActor(actor.id(),actor.name(),actor.gameId(),actor.zone(),actor.act(),actor.objectId(),actor.subtype(),actor.layoutIndex(),actor.keys(),commands,actor.sourceKind()));
            commands(actorId);
        });
    }

    private void clear(WorldCommandKind kind) {
        edit.accept(d -> d.withWorldCommands(sortedCommands(append(d.worldCommands(), new WorldCommand(id("world-action"),
                controller.frame(), kind, "", ObjectTarget.none())))));
        worldCommands();
    }

    private void removeAtPlayhead(String actorId) {
        edit.accept(d -> d.withWorldCommands(sortedCommands(append(d.worldCommands(), new WorldCommand(id("world-action"),
                controller.frame(), WorldCommandKind.REMOVE_OBJECT, actorId, ObjectTarget.none())))));
        worldCommands();
    }

    private void worldCommands() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action("Add world command / placed removal", () -> worldCommandForm(null)));
        for (var command : document().worldCommands()) actions.add(action(command.frame()+" / "+command.kind()+" / "+command.actorId(), () -> worldCommandForm(command)));
        actions.add(action("Back to cast", this::show)); menu.showActions("World commands", actions);
    }

    private void worldCommandForm(WorldCommand old) {
        var target = old == null ? ObjectTarget.none() : old.target();
        menu.showForm("World command", List.of(number("frame","Frame",old == null ? controller.frame() : old.frame()),
                text("kind","CLEAR_BADNIKS / CLEAR_OBJECTS / REMOVE_OBJECT",old == null ? "REMOVE_OBJECT" : old.kind().name()),
                text("actor","Authored object ID (blank = placed identity)",old == null ? "" : old.actorId()),
                number("layout","Layout index",target.layoutIndex()),number("object","Object ID",target.objectId()),number("subtype","Subtype",target.subtype()),
                text("delete","Delete command: true / false","false")),"Apply world command", values -> {
            var commands = new ArrayList<>(document().worldCommands()); if (old != null) commands.remove(old);
            if (!bool(values,"delete")) {
                var kind = WorldCommandKind.valueOf(upper(values,"kind"));
                commands.add(new WorldCommand(old == null ? id("world-action") : old.id(),integer(values,"frame"),kind,
                        kind == WorldCommandKind.REMOVE_OBJECT ? values.get("actor") : "",
                        kind == WorldCommandKind.REMOVE_OBJECT && values.get("actor").isBlank()
                                ? new ObjectTarget(integer(values,"layout"),integer(values,"object"),integer(values,"subtype"),0,0) : ObjectTarget.none()));
            }
            commands.sort(Comparator.comparingInt(WorldCommand::frame)); edit.accept(d -> d.withWorldCommands(commands)); worldCommands();
        });
    }

    private void restriction(ObjectActor actor) {
        menu.setSubtitle(restrictionText(actor));
    }

    private String restrictionText(ObjectActor actor) {
        if (actor == null) {
            return "Free runs native routines and world effects; arena-bound bosses ignore the anchor. Inspect ROM object coverage before casting. Puppet/Pose suspend AI and use visual child followers.";
        } else {
            if (actor.sourceKind() == ObjectSourceKind.RELEASED_ANIMAL) {
                return "Released animal: the source set supplies native species/art. Free runs the native animal routine; Puppet/Pose use visual motion without native effects. See auxiliary routine coverage.";
            }
            var entry = StageplayObjectCatalog.entry(actor.gameId(), actor.zone(), actor.act(), actor.objectId());
            return entry.freeRestriction()+". "+entry.citation()+". "+entry.modeSummary()
                    +". Puppet/Pose suspend AI; state-machine appearances use held/authored frames.";
        }
    }
    private StageplayDocument document() {
        return controller.document();
    }

    private ObjectActor actor(String id) {
        return document().objectActors().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow();
    }

    private String id(String prefix) {
        return StageplayWorkspace.nextId(prefix, document());
    }

    private void replace(ObjectActor actor) {
        edit.accept(d -> {
            var actors = new ArrayList<>(d.objectActors());
            int index = -1;
            for (int i = 0; i < actors.size(); i++) if (actors.get(i).id().equals(actor.id())) { index = i; break; }
            if (index >= 0) actors.set(index, actor); else actors.add(actor);
            return d.withObjectActors(actors);
        });
    }
    private List<ObjectKey> boundaryKeys(ObjectKey initial) {
        var keys = new ArrayList<>(List.of(initial));
        for (var change : document().sceneChanges()) {
            keys.add(new ObjectKey(change.frame(), initial.x(), initial.y(), initial.animation(), initial.pose(),
                    initial.flipX(), initial.visible(), initial.interpolation(), initial.mode(), initial.parts()));
        }
        return keys;
    }
    private List<ObjectKey> placementBoundaryKeys(SetSource source, ObjectKey initial) {
        return scopePlacementKeys(boundaryKeys(initial), source.gameId(), source.zone(), source.act(), false);
    }

    private List<ObjectKey> scopePlacementKeys(List<ObjectKey> keys, String game, int zone, int act, boolean revealMatching) {
        return keys.stream().map(key -> {
            var set = StageplayTimeline.sample(document(), key.frame()).set();
            boolean present = set.gameId().equals(game) && set.zone() == zone && set.act() == act;
            return new ObjectKey(key.frame(), key.x(), key.y(), key.animation(), key.pose(), key.flipX(), present && (key.visible() || revealMatching),
                    key.interpolation(), key.mode(), key.parts());
        }).toList();
    }
    private static ObjectActor withKeys(ObjectActor a, List<ObjectKey> keys) {
        return new ObjectActor(a.id(), a.name(), a.gameId(), a.zone(), a.act(), a.objectId(), a.subtype(),
                a.layoutIndex(), keys, a.commands(), a.sourceKind());
    }

    private static List<ObjectKey> replaceKey(List<ObjectKey> keys, ObjectKey key) {
        var out = new ArrayList<>(keys);
        out.removeIf(k -> k.frame() == key.frame());
        out.add(key);
        out.sort(Comparator.comparingInt(ObjectKey::frame));
        return out;
    }

    private static <T> List<T> append(List<T> values, T value) {
        var out = new ArrayList<>(values);
        out.add(value);
        return out;
    }

    private static List<WorldCommand> sortedCommands(List<WorldCommand> values) {
        return values.stream().sorted(Comparator.comparingInt(WorldCommand::frame)).toList();
    }

    private static EditorProjectMenu.Action action(String label, Runnable callback) {
        return new EditorProjectMenu.Action(label, callback);
    }

    private static EditorProjectMenu.Field text(String key, String label, String value) {
        return new EditorProjectMenu.Field(key, label, value, MenuTextEditor.Mode.TEXT);
    }

    private static EditorProjectMenu.Field number(String key, String label, Number value) {
        return text(key, label, value.toString());
    }

    private static int integer(Map<String, String> values, String key) {
        return Integer.parseInt(values.get(key).trim());
    }

    private static double decimal(Map<String, String> values, String key) {
        return Double.parseDouble(values.get(key).trim());
    }

    private static String upper(Map<String, String> values, String key) {
        return values.get(key).trim().toUpperCase(Locale.ROOT);
    }

    private static boolean bool(Map<String, String> values, String key) {
        if (values.get(key).equalsIgnoreCase("true")) return true;
        if (values.get(key).equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static String formatParts(List<ObjectPartPose> parts) {
        return parts.stream().map(p -> p.partId() + ":" + p.animation() + ":" + p.pose())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private static List<ObjectPartPose> parseParts(String value) {
        if (value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(";")).map(item -> {
            var fields = item.trim().split(":");
            if (fields.length != 3) throw new IllegalArgumentException("Part override must be path:animation:frame");
            return new ObjectPartPose(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
        }).toList();
    }
}
