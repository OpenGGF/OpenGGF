package com.openggf.editor.stageplay;

import com.openggf.i18n.UiMessages;
import com.openggf.editor.EditorLabels;
import java.util.Map;



import com.openggf.stageplay.StageplayDocument.Facing;

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

    static void objectChoices(EditorProjectMenu menu) {
        var values=menu.values();var labels=new java.util.LinkedHashMap<String,String>();
        try {
            if(values.get("sourceKind").equals("RELEASED_ANIMAL")){menu.configureField("object",UiMessages.text("editor.stageplay_object_authoring.animal_wic5r0"),List.of(),Map.of());return;}
            com.openggf.stageplay.StageplayObjectCatalog.inspect(values.get("game"),Integer.parseInt(values.get("zone")),Integer.parseInt(values.get("act"))).entries()
                    .forEach(entry->labels.put(Integer.toString(entry.objectId()),entry.name()));
            labels.putIfAbsent(values.get("object"),UiMessages.text("editor.stageplay_object_authoring.other_object_f2s0a7"));
            menu.configureField("object",UiMessages.text("editor.stageplay_object_authoring.object_12ydt9b"),List.copyOf(labels.keySet()),labels);
        }catch(IllegalArgumentException ignored){ /* Validation remains with Apply; incomplete numeric edits are allowed. */ }
    }

    void show() {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.cast_rom_object_1wes6q8"), UiMessages.text("editor.stageplay.presentation.action_add_object"), true, () -> properties(null)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.rom_object_coverage_restrictions_10dnku7"), UiMessages.text("editor.stageplay.presentation.action_object_library"), false, this::coverage));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.loaded_level_objects_5dlscd"), UiMessages.text("editor.stageplay.presentation.action_loaded_objects"), false, this::loaded));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.clear_badniks_1s66jn9"), () -> clear(WorldCommandKind.CLEAR_BADNIKS)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.clear_objects_16g6lgh"), () -> clear(WorldCommandKind.CLEAR_OBJECTS)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.world_commands_removals_17gj5c2"), UiMessages.text("editor.stageplay_object_authoring.world_commands_gocuwm"), false, this::worldCommands));
        for (var actor : document().objectActors())
            actions.add(action(actor.name() + " / " + actor.id(), actor.name(), false, () -> actorMenu(actor.id())));
        menu.showActions(EditorProjectMenu.PresentationKind.OBJECT_CAST, UiMessages.text("editor.stageplay_object_authoring.rom_object_cast_1q2makw"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.free_runs_the_native_routine_and_its_world_effects_pupp_ynzzmg"));
    }

    private void coverage() {
        var set = StageplayTimeline.sample(document(), controller.frame()).set();
        var entries = StageplayObjectCatalog.inspect(set.gameId(), set.zone(), set.act()).entries();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        for (var entry : entries) actions.add(action(entry.objectId()+" / "+entry.name(), () -> {
            var details = new ArrayList<EditorProjectMenu.Action>();
            if (!entry.appearanceExclusions().isEmpty()) details.add(action(UiMessages.text("editor.stageplay_object_authoring.declared_appearance_exclusions_rknmmz"),
                    () -> exclusions(entry.appearanceExclusions(), this::coverage)));
            details.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_coverage_2xbh3o"), this::coverage));
            menu.showActions(entry.name(), details);
            menu.setSubtitle(entry.objectId()+" / "+entry.name()+". "+entry.freeRestriction()+". "+entry.citation()+". "+entry.modeSummary());
        }));
        menu.showActions(EditorProjectMenu.PresentationKind.OBJECT_LIBRARY, UiMessages.text("editor.stageplay_object_authoring.every_native_object_id_1qzfdj0", Map.of("value0", String.valueOf(set.gameId()))), actions);
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
                            placementBoundaryKeys(frame.set(), new ObjectKey(0, object.x(), object.y(), 0, -1, Facing.RIGHT, true,
                                    Interpolation.HOLD, ObjectMode.FREE)), List.of());
                    edit.accept(d -> d.withObjectActors(append(d.objectActors(), actor)));
                    actorMenu(id);
                }
            }));
        }
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_cast_12naje3"), this::show));
        menu.showActions(EditorProjectMenu.PresentationKind.LOADED_OBJECTS, UiMessages.text("editor.stageplay_object_authoring.loaded_objects_portable_layout_identities_1rzy5ad"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.targets_use_layoutindex_objectid_subtype_no_runtime_slo_ur5n6k"));
    }

    private void properties(ObjectActor actor) {
        var set = StageplayTimeline.sample(document(), controller.frame()).set();
        var key = actor == null ? new ObjectKey(0, 96, 96, 0, -1, Facing.RIGHT, true, Interpolation.HOLD, ObjectMode.FREE)
                : actor.keys().getFirst();
        menu.showForm(actor == null ? EditorProjectMenu.PresentationKind.ADD_OBJECT : EditorProjectMenu.PresentationKind.OBJECT_PROPERTIES, (actor == null ? UiMessages.text("editor.stageplay_object_authoring.cast_rom_object_1wes6q8") : UiMessages.text("editor.stageplay_object_authoring.object_properties_1cbq7pw")), List.of(
                text("name", UiMessages.text("editor.stageplay_object_authoring.name_1fvl7"), actor == null ? "ROM object" : actor.name()),
                text("game", UiMessages.text("editor.stageplay_object_authoring.source_rom_s1_s2_s3k_29nixn"), actor == null ? set.gameId() : actor.gameId()),
                number("zone", UiMessages.text("editor.stageplay_object_authoring.native_source_zone_1b1e5uw"), actor == null ? set.zone() : actor.zone()),
                number("act", UiMessages.text("editor.stageplay_object_authoring.native_source_act_1fxc3o6"), actor == null ? set.act() : actor.act()),
                number("object", UiMessages.text("editor.stageplay_object_authoring.rom_object_id_decimal_n86kvg"), actor == null ? 1 : actor.objectId()),
                number("subtype", UiMessages.text("editor.stageplay_object_authoring.subtype_1voaw62"), actor == null ? 0 : actor.subtype()),
                number("layout", UiMessages.text("editor.stageplay_object_authoring.layout_index_1_new_cast_z1p3qn"), actor == null ? -1 : actor.layoutIndex()),
                text("sourceKind", UiMessages.text("editor.stageplay_object_authoring.layout_object_released_animal_1kds55t"), actor == null ? "LAYOUT_OBJECT" : actor.sourceKind().name()),
                number("x", UiMessages.text("editor.stageplay_object_authoring.initial_anchor_x_15q49ih"), key.x()), number("y", UiMessages.text("editor.stageplay_object_authoring.initial_anchor_y_15q49ii"), key.y()),
                text("mode", UiMessages.text("editor.stageplay_object_authoring.initial_mode_free_puppet_pose_dxwuwe"), key.mode().name())), UiMessages.text("editor.stageplay_object_authoring.apply_object_1ykvdxt"), values -> {
            String actorId = actor == null ? id("object") : actor.id();
            var mode = ObjectMode.valueOf(upper(values,"mode"));
            var first = new ObjectKey(0, decimal(values,"x"), decimal(values,"y"),
                    mode == ObjectMode.FREE ? 0 : key.animation(), mode == ObjectMode.FREE ? -1 : key.pose(), key.facing(), key.visible(), mode == ObjectMode.FREE ? Interpolation.HOLD : key.interpolation(),
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
                action(UiMessages.text("editor.stageplay_object_authoring.source_anchor_properties_1nhvkc9"), UiMessages.text("editor.editor_hud.properties_hnic7n"), false, () -> properties(actor)),
                action(UiMessages.text("editor.stageplay_object_authoring.mode_position_animation_keys_1jmj5pm"), UiMessages.text("editor.stageplay.presentation.action_position_and_pose"), false, () -> keys(actorId)),
                action(UiMessages.text("editor.stageplay_object_authoring.puppet_move_stop_commands_ieiy0i"), UiMessages.text("editor.stageplay.presentation.action_move_or_stop"), false, () -> commands(actorId)),
                action(UiMessages.text("editor.stageplay_object_authoring.remove_from_world_at_playhead_1bw5ot5"), UiMessages.text("editor.stageplay.presentation.action_remove_from_world"), false, () -> removeAtPlayhead(actorId)),
                action(UiMessages.text("editor.stageplay_object_authoring.available_parts_appearance_exclusions_ou9xix"), UiMessages.text("editor.stageplay.presentation.action_parts_and_limits"), false, () -> parts(actorId)),
                action(UiMessages.text("editor.stageplay_object_authoring.delete_authored_role_and_dialogue_cel0ve"), UiMessages.text("editor.stageplay.presentation.action_delete_role"), false, () -> edit.accept(d -> d
                        .withDialogue(d.dialogue().stream().filter(c -> !c.speakerActor().equals(actorId)).toList())
                        .withVoices(d.voices().stream().filter(v -> !v.speakerActor().equals(actorId)).toList())
                        .withWorldCommands(d.worldCommands().stream()
                                .filter(c -> !c.actorId().equals(actorId)).toList())
                        .withObjectActors(d.objectActors().stream().filter(a -> !a.id().equals(actorId)).toList()))),
                action(UiMessages.text("editor.stageplay_object_authoring.back_to_cast_12naje3"), this::show),
                action(UiMessages.text("editor.stageplay_object_authoring.edit_individual_part_overrides_oj71uz"), UiMessages.text("editor.stageplay_menu_presentation.part_overrides_f13cd6"), false, () -> partKeys(actorId))));
        restriction(actor);
    }

    private void parts(String actorId) {
        var value = inspection.get().stream().filter(item -> item.actorId().equals(actorId)).findFirst();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        value.ifPresent(item -> {
            for (var part : item.parts()) actions.add(action(part.partId(), UiMessages.text("editor.stageplay.presentation.part_number", Map.of("number", actions.size() + 1)), false, () -> {
                menu.showActions(EditorProjectMenu.PresentationKind.OBJECT_PART, part.partId(), List.of(action(UiMessages.text("editor.stageplay_object_authoring.back_to_parts_1prf4l0"), () -> parts(actorId))));
                menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.captured_frame_mapping_frames_pure_rom_animations_enter_1jvho6r", Map.of("value0", String.valueOf(part.defaultPose()), "value1", String.valueOf(part.mappingFrames()), "value2", String.valueOf(part.animations()))));
            }));
        });
        var actor = actor(actorId);
        if (actor.sourceKind() == ObjectSourceKind.LAYOUT_OBJECT) {
            var declared = StageplayObjectCatalog.entry(actor.gameId(), actor.zone(), actor.act(), actor.objectId()).appearanceExclusions();
            if (!declared.isEmpty()) actions.add(action(UiMessages.text("editor.stageplay_object_authoring.declared_appearance_exclusions_rknmmz"), () -> exclusions(declared, () -> parts(actorId))));
        }
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_object_1vcg9m3"), () -> actorMenu(actorId)));
        menu.showActions(EditorProjectMenu.PresentationKind.OBJECT_PARTS, UiMessages.text("editor.stageplay_object_authoring.native_visual_parts_19391uz", Map.of("value0", String.valueOf(actor(actorId).name()))), actions);
        menu.setSubtitle(value.map(item -> String.join(". ", item.limitations()))
                .filter(text -> !text.isBlank()).orElse(UiMessages.text("editor.stageplay_object_authoring.no_initialized_visual_graph_is_available_at_this_frame_3zflrn")));
    }

    private void keys(String actorId) {
        var actor = actor(actorId);
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.set_key_at_playhead_1prj4jl", Map.of("value0", String.valueOf(controller.frame()))), () -> keyForm(actorId, controller.frame())));
        for (var key : actor.keys()) actions.add(action(key.frame() + " / " + key.mode(), () -> keyForm(actorId, key.frame())));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_object_1vcg9m3"), () -> actorMenu(actorId)));
        menu.showActions(EditorProjectMenu.PresentationKind.OBJECT_KEYS, UiMessages.text("editor.stageplay_object_authoring.object_mode_visual_keys_z45x53"), actions);
        restriction(actor);
    }

    private void keyForm(String actorId, int frame) {
        var actor = actor(actorId);
        var key = actor.keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
        String formattedParts = formatParts(key.parts());
        boolean bulkParts = formattedParts.length() <= 8192;
        var fields = new ArrayList<>(List.of(
                number("frame", UiMessages.text("editor.stageplay_object_authoring.frame_14kgnh"), frame), text("mode", UiMessages.text("editor.stageplay_object_authoring.free_puppet_pose_1l334o9"), key.mode().name()),
                number("x", UiMessages.text("editor.stageplay_object_authoring.world_x_1gr5ctm"), key.x()), number("y", UiMessages.text("editor.stageplay_object_authoring.world_y_1gr5ctn"), key.y()),
                number("animation", UiMessages.text("editor.stageplay_object_authoring.pure_rom_animation_0_default_11u6dxo"), key.animation()),
                number("pose", UiMessages.text("editor.stageplay_object_authoring.mapping_frame_1_animation_held_fallback_s3z7s5"), key.pose()),
                new EditorProjectMenu.Field("facing", UiMessages.text("editor.stageplay_object_authoring.facing_yob1fe"), key.facing() == Facing.LEFT ? "Left" : "Right", List.of("Left", "Right")),
                text("visible", UiMessages.text("editor.stageplay_object_authoring.visible_true_false_16cegrc"), Boolean.toString(key.visible())),
                text("interpolation", UiMessages.text("editor.stageplay_object_authoring.hold_linear_smooth_pvczm0"), key.interpolation().name())));
        if (bulkParts) fields.add(new EditorProjectMenu.Field("parts", UiMessages.text("editor.stageplay_object_authoring.part_overrides_root_0_animation_frame_yiwf9l"),
                formattedParts, MenuTextEditor.Mode.TEXT, 8192));
        fields.add(text("delete", UiMessages.text("editor.stageplay_object_authoring.delete_this_key_true_false_13wkxw8"), "false"));
        menu.showForm(UiMessages.text("editor.stageplay_object_authoring.object_key_1ut22jp", Map.of("value0", String.valueOf(frame))), fields, UiMessages.text("editor.stageplay_object_authoring.apply_key_1xti7bh"), values -> {
            int at = integer(values,"frame");
            var keys = new ArrayList<>(actor.keys());
            keys.removeIf(k -> k.frame() == frame || k.frame() == at);
            if (!bool(values,"delete")) keys.add(new ObjectKey(at, decimal(values,"x"), decimal(values,"y"),
                    integer(values,"animation"), integer(values,"pose"), Facing.valueOf(upper(values,"facing")), bool(values,"visible"),
                    Interpolation.valueOf(upper(values,"interpolation")), ObjectMode.valueOf(upper(values,"mode")),
                    bulkParts ? parseParts(values.get("parts")) : key.parts()));
            keys.sort(Comparator.comparingInt(ObjectKey::frame));
            replace(withKeys(actor, keys)); keys(actorId);
        });
        restriction(actor);
        if (!bulkParts) menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.this_key_has_overrides_use_the_object_s_edit_individual_1ow6rai", Map.of("value0", String.valueOf(restrictionText(actor)), "value1", String.valueOf(key.parts().size()))));
    }

    private void partKeys(String actorId) {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.part_overrides_at_playhead_on42o8", Map.of("value0", String.valueOf(controller.frame()))), () -> partOverrides(actorId, controller.frame())));
        for (var key : actor(actorId).keys())
            actions.add(action(UiMessages.text("editor.stageplay_object_authoring.frame_overrides_1l6rn38", Map.of("value0", String.valueOf(key.frame()), "value1", String.valueOf(key.parts().size()))), () -> partOverrides(actorId, key.frame())));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_object_1vcg9m3"), () -> actorMenu(actorId)));
        menu.showActions(EditorProjectMenu.PresentationKind.PART_KEYS, UiMessages.text("editor.stageplay_object_authoring.choose_object_part_key_13ryn4a"), actions);
    }

    private void partOverrides(String actorId, int frame) {
        var key = actor(actorId).keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.add_part_override_1o1z5kq"), UiMessages.text("editor.stageplay_object_authoring.add_part_override_1o1z5kq"), true, () -> partForm(actorId, frame, null)));
        for (var part : key.parts()) actions.add(action(part.partId() + " / " + part.animation() + ":" + part.pose(),
                () -> partForm(actorId, frame, part)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_key_84cigj"), () -> keyForm(actorId, frame)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_object_1vcg9m3"), () -> actorMenu(actorId)));
        menu.showActions(EditorProjectMenu.PresentationKind.PART_OVERRIDES, UiMessages.text("editor.stageplay_object_authoring.part_overrides_frame_1jvxbt9", Map.of("value0", String.valueOf(frame))), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.each_part_path_accepts_up_to_256_characters_up_to_256_o_186t4g4"));
    }

    private void partForm(String actorId, int frame, ObjectPartPose old) {
        menu.showForm((old == null ? UiMessages.text("editor.stageplay_object_authoring.add_object_part_override_14md4zb") : UiMessages.text("editor.stageplay_object_authoring.edit_object_part_override_10c4fam")), List.of(
                text("partId", UiMessages.text("editor.stageplay_object_authoring.portable_part_path_12t2v9p"), old == null ? "root/0" : old.partId()),
                number("animation", UiMessages.text("editor.stageplay_object_authoring.pure_rom_animation_0_default_11u6dxo"), old == null ? 0 : old.animation()),
                number("pose", UiMessages.text("editor.stageplay_object_authoring.mapping_frame_1_animation_held_fallback_s3z7s5"), old == null ? -1 : old.pose()),
                text("delete", UiMessages.text("editor.stageplay_object_authoring.delete_override_true_false_18yuxqf"), "false")), UiMessages.text("editor.stageplay_object_authoring.apply_part_xhdrid"), values -> {
            var actor = actor(actorId);
            var key = actor.keys().stream().filter(k -> k.frame() <= frame).reduce((a,b) -> b).orElseThrow();
            var parts = new ArrayList<>(key.parts());
            int index = old == null ? -1 : parts.indexOf(old);
            if (bool(values,"delete")) { if (index >= 0) parts.remove(index); }
            else {
                var part = new ObjectPartPose(values.get("partId"), integer(values,"animation"), integer(values,"pose"));
                if (index >= 0) parts.set(index, part); else parts.add(part);
            }
            var replacement = new ObjectKey(frame, key.x(), key.y(), key.animation(), key.pose(), key.facing(),
                    key.visible(), key.interpolation(), key.mode(), parts);
            replace(withKeys(actor, replaceKey(actor.keys(), replacement)));
            partOverrides(actorId, frame);
        });
    }

    private void exclusions(List<String> declared, Runnable back) {
        var actions = new ArrayList<EditorProjectMenu.Action>();
        for (int index = 0; index < declared.size(); index++) {
            int selected = index;
            actions.add(action(UiMessages.text("editor.stageplay_object_authoring.appearance_restriction_1yd8oer", Map.of("value0", String.valueOf((index + 1)))), () -> {
                menu.showActions(UiMessages.text("editor.stageplay_object_authoring.appearance_restriction_1yd8oer", Map.of("value0", String.valueOf((selected + 1)))),
                        List.of(action(UiMessages.text("editor.stageplay_object_authoring.back_to_restrictions_1isb15f"), () -> exclusions(declared, back))));
                menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.f1_opens_the_complete_reason_and_routine_citation_1jz6bat", Map.of("value0", String.valueOf(declared.get(selected)))));
            }));
        }
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_187if"), back));
        menu.showActions(EditorProjectMenu.PresentationKind.APPEARANCE_LIMITS, UiMessages.text("editor.stageplay_object_authoring.declared_native_appearance_exclusions_1w3wnz2"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.each_entry_describes_a_source_owned_limitation_with_its_lf2dp3"));
    }

    private void commands(String actorId) {
        var actor = actor(actorId);
        var actions = new ArrayList<EditorProjectMenu.Action>();
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.add_move_stop_at_playhead_1j2cd2c"), UiMessages.text("editor.stageplay_object_authoring.add_move_stop_at_playhead_1j2cd2c"), true, () -> commandForm(actorId, null)));
        for (var command : actor.commands()) actions.add(action(command.frame() + " / " + command.kind(), command.frame() + " / " + EditorLabels.value(command.kind().name()), false, () -> commandForm(actorId, command)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_object_1vcg9m3"), () -> actorMenu(actorId)));
        menu.showActions(EditorProjectMenu.PresentationKind.OBJECT_MOVES, UiMessages.text("editor.stageplay_object_authoring.visual_puppet_commands_1wogw0k"), actions);
        menu.setSubtitle(UiMessages.text("editor.stageplay_object_authoring.move_to_moves_the_visual_anchor_by_speed_pixels_per_fra_18ih7i"));
    }

    private void commandForm(String actorId, ObjectCommand old) {
        menu.showForm(UiMessages.text("editor.stageplay_object_authoring.visual_puppet_instruction_kry2rm"), List.of(number("frame", UiMessages.text("editor.stageplay_object_authoring.frame_14kgnh"), old == null ? controller.frame() : old.frame()),
                text("kind", UiMessages.text("editor.stageplay_object_authoring.move_to_stop_12zy9ii"), old == null ? "MOVE_TO" : old.kind().name()),
                number("x", UiMessages.text("editor.stageplay_object_authoring.destination_x_ia6tgm"), old == null ? 160 : old.x()), number("y", UiMessages.text("editor.stageplay_object_authoring.destination_y_ia6tgn"), old == null ? 96 : old.y()),
                number("speed", UiMessages.text("editor.stageplay_object_authoring.pixels_per_frame_18wfuaf"), old == null ? 2 : old.speed()),
                text("delete", UiMessages.text("editor.stageplay_object_authoring.delete_instruction_true_false_dk1ev3"), "false")), UiMessages.text("editor.stageplay_object_authoring.apply_instruction_11wm858"), values -> {
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
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.add_world_command_placed_removal_1ij200s"), UiMessages.text("editor.stageplay.presentation.action_add_command"), true, () -> worldCommandForm(null)));
        for (var command : document().worldCommands()) actions.add(action(command.frame()+" / "+command.kind()+" / "+command.actorId(), command.frame()+" / "+EditorLabels.value(command.kind().name())+" / "+command.actorId(), false, () -> worldCommandForm(command)));
        actions.add(action(UiMessages.text("editor.stageplay_object_authoring.back_to_cast_12naje3"), this::show)); menu.showActions(UiMessages.text("editor.stageplay_object_authoring.world_commands_gocuwm"), actions);
    }

    private void worldCommandForm(WorldCommand old) {
        var target = old == null ? ObjectTarget.none() : old.target();
        menu.showForm(UiMessages.text("editor.stageplay_object_authoring.world_command_1qej1jx"), List.of(number("frame",UiMessages.text("editor.stageplay_object_authoring.frame_14kgnh"),old == null ? controller.frame() : old.frame()),
                text("kind",UiMessages.text("editor.stageplay_object_authoring.clear_badniks_clear_objects_remove_object_ksbrdc"),old == null ? "REMOVE_OBJECT" : old.kind().name()),
                text("actor",UiMessages.text("editor.stageplay_object_authoring.authored_object_id_blank_placed_identity_14ojhlx"),old == null ? "" : old.actorId()),
                number("layout",UiMessages.text("editor.stageplay_object_authoring.layout_index_9lmjcs"),target.layoutIndex()),number("object",UiMessages.text("editor.stageplay_object_authoring.object_id_8i7gjg"),target.objectId()),number("subtype",UiMessages.text("editor.stageplay_object_authoring.subtype_1voaw62"),target.subtype()),
                text("delete",UiMessages.text("editor.stageplay_object_authoring.delete_command_true_false_1kytt6k"),"false")),UiMessages.text("editor.stageplay_object_authoring.apply_world_command_4wcsiz"), values -> {
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
            return UiMessages.text("editor.stageplay_object_authoring.free_runs_native_routines_and_world_effects_arena_bound_kxjaj1");
        } else {
            if (actor.sourceKind() == ObjectSourceKind.RELEASED_ANIMAL) {
                return UiMessages.text("editor.stageplay_object_authoring.released_animal_the_source_set_supplies_native_species_1wd8ayz");
            }
            var entry = StageplayObjectCatalog.entry(actor.gameId(), actor.zone(), actor.act(), actor.objectId());
            return UiMessages.text("editor.stageplay_object_authoring.puppet_pose_suspend_ai_state_machine_appearances_use_he_1gougoj", Map.of("value0", String.valueOf(entry.freeRestriction() + ". " + entry.citation() + ". " + entry.modeSummary())));
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
            keys.add(new ObjectKey(change.frame(), initial.x(), initial.y(), initial.animation(), initial.pose(), initial.facing(), initial.visible(), initial.interpolation(), initial.mode(), initial.parts()));
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
            return new ObjectKey(key.frame(), key.x(), key.y(), key.animation(), key.pose(), key.facing(), present && (key.visible() || revealMatching),
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
    private static EditorProjectMenu.Action action(String fullLabel, String compactLabel, boolean primary, Runnable callback) {
        return StageplayMenuPresentation.action(fullLabel, compactLabel, primary, callback);
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
        throw new IllegalArgumentException(UiMessages.text("editor.stageplay_object_authoring.must_be_true_or_false_dba4ph", Map.of("value0", String.valueOf(key))));
    }

    private static String formatParts(List<ObjectPartPose> parts) {
        return parts.stream().map(p -> p.partId() + ":" + p.animation() + ":" + p.pose())
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private static List<ObjectPartPose> parseParts(String value) {
        if (value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(";")).map(item -> {
            var fields = item.trim().split(":");
            if (fields.length != 3) throw new IllegalArgumentException(UiMessages.text("editor.stageplay_object_authoring.part_override_must_be_path_animation_frame_13qfv0v"));
            return new ObjectPartPose(fields[0], Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
        }).toList();
    }
}
