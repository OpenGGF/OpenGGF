package com.openggf.editor.stageplay;

import com.openggf.i18n.UiMessages;
import java.util.Map;


import com.openggf.editor.project.EditorProjectMenu;
import com.openggf.editor.project.EditorProjectMenu.PresentationKind;
import com.openggf.stageplay.StageplayDocument.PuppetCommandKind;
import com.openggf.game.MenuChrome;
import com.openggf.editor.EditorLabels;
import java.util.*;

/** Shared Stageplay wording/layout. Saved values and the form owner's callbacks are unchanged. */
final class StageplayMenuPresentation {
    private StageplayMenuPresentation() { }
    record Context(Map<String,String> players,Map<String,String> objects,Map<String,List<String>> choices,
                   java.util.function.Consumer<EditorProjectMenu> objectChoices) { }
    // Presentation arrives translated from its owner; never infer semantics from its wording.
    static String words(String text) { return text; }
    static String sentence(String text) {
        String first = text.split("(?<=[.!?])\\s+|\\R", 2)[0];
        return MenuChrome.compactWidth(first) > 498 ? UiMessages.text("editor.stageplay.presentation.open_status_details") : first;
    }
    static void configure(EditorProjectMenu menu, Context context, String selected) {
        String title=menu.title();var fields=menu.formFields();
        PresentationKind kind = menu.presentationKind();
        applyTitle(menu, kind);
        if(fields.isEmpty()) { actions(menu, title, kind); return; }
        Set<String> keys=new HashSet<>();fields.forEach(f->keys.add(f.key()));
        var names=new LinkedHashMap<String,String>();names.putAll(context.players());names.putAll(context.objects());
        // Short labels are presentation metadata; the original labels/ranges remain in F1.
        Map<String,String> labels=Map.ofEntries(
                Map.entry("name",UiMessages.text("editor.stageplay_menu_presentation.name_1fvl7")),Map.entry("game",UiMessages.text("editor.stageplay_menu_presentation.game_1beoi")),Map.entry("character",UiMessages.text("editor.stageplay_menu_presentation.character_1n0e4nd")),Map.entry("mode",UiMessages.text("editor.stageplay_menu_presentation.mode_1firn")),
                Map.entry("x","X"),Map.entry("y","Y"),Map.entry("frame",UiMessages.text("editor.stageplay_menu_presentation.frame_14kgnh")),Map.entry("start",UiMessages.text("editor.stageplay_menu_presentation.start_1br2gy")),Map.entry("end",UiMessages.text("editor.stageplay_menu_presentation.end_1hvf")),
                Map.entry("width",UiMessages.text("editor.stageplay_menu_presentation.width_1dra92")),Map.entry("height",UiMessages.text("editor.stageplay_menu_presentation.height_zop8w7")),Map.entry("duration",UiMessages.text("editor.stageplay_menu_presentation.length_11kyxom")),Map.entry("zone",UiMessages.text("editor.stageplay_menu_presentation.zone_1ntu4")),Map.entry("act",UiMessages.text("editor.stageplay_menu_presentation.act_1enm")),
                Map.entry("animation",UiMessages.text("editor.stageplay_menu_presentation.animation_1fn1jdw")),Map.entry("pose",UiMessages.text("editor.stageplay_menu_presentation.pose_1hg35")),Map.entry("visible",UiMessages.text("editor.stageplay_menu_presentation.visible_z8z87m")),Map.entry("scale",UiMessages.text("editor.stageplay_menu_presentation.scale_1bg7je")),Map.entry("interpolation",UiMessages.text("editor.stageplay_menu_presentation.easing_y7jmtn")),
                Map.entry("rom",UiMessages.text("editor.stageplay_menu_presentation.rom_identity_rr5w6m")),Map.entry("project",UiMessages.text("editor.stageplay_menu_presentation.level_project_mgnk9p")),Map.entry("snap",UiMessages.text("editor.stageplay_menu_presentation.start_on_floor_1hw9i95")),Map.entry("collision",UiMessages.text("editor.stageplay_menu_presentation.collision_path_1c03wqb")),
                Map.entry("urgency",UiMessages.text("editor.stageplay_menu_presentation.effort_ya2fik")),Map.entry("hold",UiMessages.text("editor.stageplay_menu_presentation.hold_1cc0v")),Map.entry("timeout",UiMessages.text("editor.stageplay_menu_presentation.timeout_5stm4h")),Map.entry("condition",UiMessages.text("editor.stageplay_menu_presentation.wait_until_124tn7")),Map.entry("layout",UiMessages.text("editor.stageplay_menu_presentation.placement_1qskvyd")),
                Map.entry("object",UiMessages.text("editor.stageplay_menu_presentation.object_number_pgaecq")),Map.entry("subtype",UiMessages.text("editor.stageplay_menu_presentation.subtype_1voaw62")),Map.entry("offsetX",UiMessages.text("editor.stageplay_menu_presentation.offset_x_1nli6uj")),Map.entry("offsetY",UiMessages.text("editor.stageplay_menu_presentation.offset_y_1nli6uk")),
                Map.entry("zoom",UiMessages.text("editor.stageplay_menu_presentation.zoom_1ntv7")),Map.entry("follow",UiMessages.text("editor.stageplay_menu_presentation.follow_yw5yvl")),Map.entry("deadX",UiMessages.text("editor.stageplay_menu_presentation.dead_zone_x_9oh3i8")),Map.entry("deadY",UiMessages.text("editor.stageplay_menu_presentation.dead_zone_y_9oh3i9")),Map.entry("ahead",UiMessages.text("editor.stageplay_menu_presentation.look_ahead_e06of4")),Map.entry("smooth",UiMessages.text("editor.stageplay_menu_presentation.smoothing_8sdhw")),
                Map.entry("frequency",UiMessages.text("editor.stageplay_menu_presentation.frequency_vzf4u4")),Map.entry("seed",UiMessages.text("editor.stageplay_menu_presentation.random_seed_f1ul5a")),Map.entry("transition",UiMessages.text("editor.stageplay_menu_presentation.transition_4gc60l")),Map.entry("pitch",UiMessages.text("editor.stageplay_menu_presentation.pitch_19x1k0")),Map.entry("variation",UiMessages.text("editor.stageplay_menu_presentation.variation_vsbn5f")),
                Map.entry("length",UiMessages.text("editor.stageplay_menu_presentation.length_11kyxom")),Map.entry("level",UiMessages.text("editor.stageplay_menu_presentation.level_17ncro")),Map.entry("tone",UiMessages.text("editor.stageplay_menu_presentation.tone_1jzwy")),Map.entry("rhythm",UiMessages.text("editor.stageplay_menu_presentation.blip_on_r37sy6")),Map.entry("text",UiMessages.text("editor.stageplay_menu_presentation.text_1jsr1")),Map.entry("portrait",UiMessages.text("editor.stageplay_menu_presentation.portrait_d4o9tn")),
                Map.entry("id",UiMessages.text("editor.stageplay_menu_presentation.name_1fvl7")),Map.entry("speed",UiMessages.text("editor.stageplay_menu_presentation.speed_1bol5z")),Map.entry("punctuation",UiMessages.text("editor.stageplay_menu_presentation.punctuation_pause_1jgh9qo")),Map.entry("space",UiMessages.text("editor.stageplay_menu_presentation.space_pause_17ayla4")),Map.entry("panel",UiMessages.text("editor.stageplay_menu_presentation.panel_colour_ssab9k")),Map.entry("border",UiMessages.text("editor.stageplay_menu_presentation.border_colour_176kr34")),
                Map.entry("padding",UiMessages.text("editor.stageplay_menu_presentation.padding_e70n3l")),Map.entry("asset",UiMessages.text("editor.stageplay_menu_presentation.file_1axlo")),Map.entry("slice",UiMessages.text("editor.stageplay_menu_presentation.border_size_1306out")),Map.entry("pointer",UiMessages.text("editor.stageplay_menu_presentation.speaker_pointer_z3eby4")),Map.entry("color",UiMessages.text("editor.stageplay_menu_presentation.colour_xh1624")),Map.entry("kind",UiMessages.text("editor.stageplay_menu_presentation.action_wchdg6")),Map.entry("sound",UiMessages.text("editor.stageplay_menu_presentation.sound_number_ph3noa")),
                Map.entry("gain",UiMessages.text("editor.stageplay_menu_presentation.volume_16gw3sq")),Map.entry("top",UiMessages.text("editor.stageplay_menu_presentation.search_top_l6y2gt")),Map.entry("bottom",UiMessages.text("editor.stageplay_menu_presentation.search_bottom_s2vi4j")),Map.entry("halfWidth",UiMessages.text("editor.stageplay_menu_presentation.half_width_1ujw6x")),Map.entry("footOffset",UiMessages.text("editor.stageplay_menu_presentation.foot_offset_q3q6w5")),Map.entry("collisionPath",UiMessages.text("editor.stageplay_menu_presentation.collision_path_1c03wqb")),
                Map.entry("actor",UiMessages.text("editor.stageplay_menu_presentation.who_1v32")),Map.entry("frames",UiMessages.text("editor.stageplay_menu_presentation.frames_yxmcau")),Map.entry("nodes",UiMessages.text("editor.stageplay_menu_presentation.search_nodes_1fhx7q1")),Map.entry("ticks",UiMessages.text("editor.stageplay_menu_presentation.probe_ticks_xv8rty")),Map.entry("millis",UiMessages.text("editor.stageplay_menu_presentation.time_budget_wef6c8")),Map.entry("startingAction",UiMessages.text("editor.stageplay_menu_presentation.start_1br2gy")),Map.entry("travelSpeed",UiMessages.text("editor.stageplay_menu_presentation.speed_1bol5z")),
                Map.entry("endingAction",UiMessages.text("editor.stageplay_menu_presentation.stop_1jgo2")),Map.entry("endingFacing",UiMessages.text("editor.stageplay_menu_presentation.face_1argd")),Map.entry("sourceKind",UiMessages.text("editor.stageplay_menu_presentation.source_kind_p7taah")),Map.entry("parts",UiMessages.text("editor.stageplay_menu_presentation.part_overrides_f13cd6")),Map.entry("partId",UiMessages.text("editor.stageplay_menu_presentation.part_1h5oz")),Map.entry("delete",UiMessages.text("editor.stageplay_menu_presentation.delete_xskn2j")),Map.entry("path",UiMessages.text("editor.stageplay_menu_presentation.file_1axlo")),Map.entry("out",UiMessages.text("editor.stageplay_menu_presentation.file_1axlo")));
        for(var f:fields) {
            menu.configureField(f.key(),labels.getOrDefault(f.key(),f.label()));
            List<String> choices=List.of();
            if(f.value().equals("true")||f.value().equals("false"))choices=List.of("false","true");
            else if(f.key().equals("game"))choices=List.of("s1","s2","s3k");
            else if(f.key().equals("interpolation"))choices=context.choices().get("Interpolation");
            else if(f.key().equals("transition"))choices=context.choices().get("Transition");
            else if(f.key().equals("condition"))choices=context.choices().get("WaitCondition");
            else if(f.key().equals("sourceKind"))choices=context.choices().get("ObjectSourceKind");
            else if(f.key().equals("mode"))choices=keys.contains("character")?List.of("PUPPET","POSER"):context.choices().get("ObjectMode");
            else if(f.key().equals("kind"))choices=keys.contains("gain")?context.choices().get("AudioKind"):keys.contains("actor")?context.choices().get("WorldCommandKind"):context.choices().get("ObjectCommandKind");
            if(!choices.isEmpty()&&choices.contains(f.value()))menu.configureField(f.key(),labels.getOrDefault(f.key(),f.label()),choices,Map.of());
        }
        for(String key:List.of("frame","start","end","hold","timeout","duration","frames","length"))if(keys.contains(key))menu.setUnit(key,UiMessages.text("editor.stageplay_menu_presentation.frames_1e328x2"));
        if(keys.contains("game"))menu.setValueLabels("game",Map.of("s1",UiMessages.text("editor.stageplay_menu_presentation.sonic_1_1t0f5st"),"s2",UiMessages.text("editor.stageplay_menu_presentation.sonic_2_1t0f5su"),"s3k",UiMessages.text("editor.stageplay_menu_presentation.sonic_3_k_do22q8")));
        if(keys.contains("character"))menu.setValueLabels("character",Map.of("sonic",UiMessages.text("editor.stageplay_menu_presentation.sonic_1bo4y4"),"tails",UiMessages.text("editor.stageplay_menu_presentation.tails_1byw3n"),"knuckles",UiMessages.text("editor.stageplay_menu_presentation.knuckles_1wl5wgw")));
        if(keys.contains("actor")) {
            var options=new LinkedHashMap<String,String>();
            if(menu.values().get("actor").isEmpty() || keys.contains("kind"))options.put("",UiMessages.text("editor.stageplay_menu_presentation.placed_object_88ixya"));
            if(keys.contains("nodes")) {
                if(keys.contains("startingAction"))options.putAll(context.players());
                else options.putAll(context.objects());
            }else names.forEach(options::putIfAbsent);
            if(!options.containsKey(menu.values().get("actor")))options.put(menu.values().get("actor"),menu.values().get("actor"));
            menu.configureField("actor",UiMessages.text("editor.stageplay_menu_presentation.who_1v32"),List.copyOf(options.keySet()),options);
        }
        String hint=UiMessages.text("editor.stageplay_menu_presentation.choose_the_values_then_apply_the_change_1sriiw");
        List<String> advanced=new ArrayList<>();
        boolean worldPosition=false, floor=false, snap=true;
        if(keys.contains("nodes")) {
            menu.setTitle((keys.contains("startingAction") ? (UiMessages.text("editor.stageplay_menu_presentation.plan_route_fkwmnm")) : (UiMessages.text("editor.stageplay_menu_presentation.plan_object_route_19r7w67"))));hint=UiMessages.text("editor.stageplay_menu_presentation.find_a_playable_path_to_a_destination_mivqhh");
            menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.destination_3xpmni"),"x","y");worldPosition=true;floor=true;snap=true;
            if(keys.contains("startingAction"))menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.motion_127mdau"),"startingAction","travelSpeed","endingAction","endingFacing");
            else menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.motion_127mdau"),"speed");
            add(advanced,keys,"frames","nodes","ticks","millis","urgency","halfWidth","height","footOffset","collisionPath");
            if(keys.contains("endingAction"))menu.setValueLabels("endingAction",Map.of("Gentle Stop",UiMessages.text("editor.stageplay_menu_presentation.gentle_z7r35p"),"Hard Stop",UiMessages.text("editor.stageplay_menu_presentation.hard_1c1sb")));
            if(keys.contains("endingFacing"))menu.setValueLabels("endingFacing",Map.of("Inherit",UiMessages.text("editor.stageplay_menu_presentation.keep_1e1et")));
            menu.setUnit("millis",UiMessages.text("editor.stageplay_menu_presentation.ms_2p2"));menu.setSubmitLabel(UiMessages.text("editor.stageplay_menu_presentation.find_route_aitema"));
        } else if(keys.contains("top")&&keys.contains("bottom")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.place_on_floor_182owpw"));hint=UiMessages.text("editor.stageplay_menu_presentation.find_a_floor_near_this_actor_vjgmkd");
            menu.configureField("x",UiMessages.text("editor.stageplay_menu_presentation.column_xh1aba"));worldPosition=true;floor=true;
            menu.addContextField("actor",UiMessages.text("editor.stageplay_menu_presentation.who_1v32"),selected,names.getOrDefault(selected,selected));
            add(advanced,keys,"top","bottom","halfWidth","height","footOffset","collisionPath");menu.setSubmitLabel(UiMessages.text("editor.stageplay_menu_presentation.find_floors_10cklji"));
        } else if(keys.contains("portrait")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.dialogue_line_1tse7fg"));hint=UiMessages.text("editor.stageplay_menu_presentation.write_the_line_and_choose_its_timing_k6d7ag");
            menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.line_1erkk"),"text");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.timing_15fihka"),"start","end");menu.setMultilinePreview("text");
            add(advanced,keys,"name","x","y","width","height","portrait");menu.setSubmitLabel(UiMessages.text("editor.stageplay_menu_presentation.apply_line_xhbddy"));
        } else if(keys.contains("tone")) {
            hint=UiMessages.text("editor.stageplay_menu_presentation.choose_a_voice_or_start_with_a_preset_68904b");add(advanced,keys,"variation","length");
        } else if(keys.contains("character")&&keys.contains("mode")) {
            hint=UiMessages.text("editor.stageplay_menu_presentation.puppet_moves_with_physics_poser_uses_keys_18xi9up");
            menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.actor_11kb11"),"name","character","mode");if(keys.contains("x")){menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.placement_1qskvyd"),"x","y");worldPosition=true;floor=true;}
            add(advanced,keys,"game","snap","collision");
        } else if(keys.contains("follow")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.camera_key_1yp2z2c"));hint=UiMessages.text("editor.stageplay_menu_presentation.frame_the_shot_then_choose_its_easing_1rip3it");
            menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.framing_hhjumi"),"x","y","zoom","interpolation");worldPosition=true;snap=false;
            add(advanced,keys,"follow","deadX","deadY","ahead","smooth","offsetX","offsetY");
        } else if(keys.contains("seed")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.camera_shake_y55ffv"));hint=UiMessages.text("editor.stageplay_menu_presentation.layer_a_shake_over_the_camera_movement_fii33r");
            menu.configureField("x",UiMessages.text("editor.stageplay_menu_presentation.across_wcg80v"));menu.configureField("y",UiMessages.text("editor.stageplay_menu_presentation.up_and_down_17esu1c"));menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.timing_15fihka"),"start","end");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.shake_1bjeg6"),"x","y","frequency");add(advanced,keys,"seed");
        } else if(keys.contains("transition")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.scene_change_vbhj38"));hint=UiMessages.text("editor.stageplay_menu_presentation.choose_the_next_set_and_the_transition_pkt166");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.transition_4gc60l"),"frame","transition","duration");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.set_1s1u"),"game","zone","act");add(advanced,keys,"rom","project");
        } else if(keys.contains("duration")&&keys.contains("width")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.scene_and_set_kzdpt1"));hint=UiMessages.text("editor.stageplay_menu_presentation.name_the_scene_and_choose_its_set_pwdbsc");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.scene_1bgajw"),"name","duration");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.set_1s1u"),"game","zone","act");add(advanced,keys,"width","height","rom","project");
        } else if(keys.contains("sourceKind")) {
            hint=UiMessages.text("editor.stageplay_menu_presentation.choose_an_object_and_its_starting_position_cs67y5");
            context.objectChoices().accept(menu);
            menu.onValueChanged((key,value)->{if(List.of("game","zone","act","object","sourceKind").contains(key))context.objectChoices().accept(menu);});
            menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.object_12ydt9b"),"name","object");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.direction_h80bq7"),"mode","x","y");worldPosition=true;add(advanced,keys,"game","zone","act","layout","sourceKind","subtype");
        } else if(keys.contains("partId")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.part_override_7cx0l5"));hint=UiMessages.text("editor.stageplay_menu_presentation.choose_a_frame_for_one_visible_part_1gcmv9d");add(advanced,keys,"partId","delete");
        } else if(keys.contains("mode")&&keys.contains("delete")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.object_key_1el3swu"));hint=UiMessages.text("editor.stageplay_menu_presentation.choose_how_this_object_appears_at_the_playhead_scupq0");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.placement_1qskvyd"),"frame","x","y");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.direction_h80bq7"),"mode","facing");worldPosition=true;add(advanced,keys,"animation","pose","visible","interpolation","parts","delete");
        } else if(keys.contains("kind")&&keys.contains("gain")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.audio_cue_108879l"));hint=UiMessages.text("editor.stageplay_menu_presentation.choose_a_sound_and_when_it_plays_g2ye4g");menu.setValueLabels("kind",Map.of("ROM_MUSIC",UiMessages.text("editor.stageplay_menu_presentation.music_18hb0l"),"ROM_SFX",UiMessages.text("editor.stageplay_menu_presentation.sound_effect_kzbqhu"),"VOICE",UiMessages.text("editor.stageplay_menu_presentation.audio_file_1lmf3xi"),"MUSIC_STOP",UiMessages.text("editor.stageplay_menu_presentation.stop_music_1lfogiv")));add(advanced,keys,"game","asset","gain");
        } else if(keys.contains("kind")&&keys.contains("actor")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.world_command_1qej1jx"));hint=UiMessages.text("editor.stageplay_menu_presentation.clear_objects_or_remove_a_cast_member_1q48w0g");add(advanced,keys,"layout","object","subtype","delete");
        } else if(keys.contains("kind")&&keys.contains("speed")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.object_move_5hu67m"));hint=UiMessages.text("editor.stageplay_menu_presentation.move_or_stop_this_directed_object_cxndvt");worldPosition=true;add(advanced,keys,"delete");
        } else if(keys.contains("timeout")) {
            hint=UiMessages.text("editor.stageplay_menu_presentation.choose_when_the_action_starts_1bsvof3");
            if(keys.contains("x")){menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.destination_3xpmni"),"x","y","urgency");worldPosition=true;}
            add(advanced,keys,"layout","object","subtype","offsetX","offsetY");if(!keys.contains("condition"))add(advanced,keys,"timeout");
        } else if(keys.contains("panel")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.dialogue_style_1ep4oeh"));menu.setSubmitLabel(UiMessages.text("editor.stageplay_menu_presentation.apply_style_17l9krj"));hint=UiMessages.text("editor.stageplay_menu_presentation.style_the_dialogue_panel_and_speaker_name_197wwc3");
            menu.configureField("text",UiMessages.text("editor.stageplay_menu_presentation.text_colour_797xtr"));menu.configureField("name",UiMessages.text("editor.stageplay_menu_presentation.name_colour_o04b1d"));add(advanced,keys,"padding","asset","slice");
        } else if(keys.contains("punctuation")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.dialogue_typing_9i6ljz"));hint=UiMessages.text("editor.stageplay_menu_presentation.choose_how_quickly_each_letter_appears_pjpznu");add(advanced,keys,"space");
        } else if(keys.contains("color")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.caption_line_1iz5m32"));hint=UiMessages.text("editor.stageplay_menu_presentation.write_the_caption_and_choose_its_timing_s95jiu");menu.setMultilinePreview("text");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.line_1erkk"),"text");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.timing_15fihka"),"start","end");add(advanced,keys,"x","y","color");
        } else if(keys.contains("scale")) {
            menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.actor_key_139ppck"));hint=UiMessages.text("editor.stageplay_menu_presentation.place_and_pose_the_actor_at_the_playhead_1banvet");menu.setGroup(UiMessages.text("editor.stageplay_menu_presentation.placement_1qskvyd"),"x","y","facing");worldPosition=true;add(advanced,keys,"animation","pose","visible","scale","interpolation");
        } else if(kind == PresentationKind.ACTOR_POSITION || keys.equals(Set.of("x", "y", "facing"))) {
            menu.setTitle(UiMessages.text("editor.stageplay.presentation.actor_position"));hint=UiMessages.text("editor.stageplay.presentation.actor_position_hint");worldPosition=true;floor=true;
        } else if(kind == PresentationKind.ACTOR_VISIBILITY || keys.equals(Set.of("visible"))) {
            menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_actor_visibility"));hint=UiMessages.text("editor.stageplay.presentation.actor_visibility_hint");
        } else if(kind == PresentationKind.REVEAL_TIMING) {
            menu.setTitle(UiMessages.text("editor.stageplay.presentation.reveal_timing"));hint=UiMessages.text("editor.stageplay.presentation.reveal_timing_hint");
        } else if(keys.contains("path")) {
            hint = kind == PresentationKind.IMPORT_SCREENPLAY ? UiMessages.text("editor.stageplay.presentation.import_screenplay_hint")
                    : kind == PresentationKind.EXPORT_SCREENPLAY ? UiMessages.text("editor.stageplay.presentation.export_screenplay_hint")
                    : UiMessages.text("editor.stageplay_menu_presentation.choose_the_scene_file_rm7erq");
        } else if(keys.contains("out")) {menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.export_film_1r01u1c"));hint=UiMessages.text("editor.stageplay_menu_presentation.choose_a_new_film_file_1i1bo1q");}
        if(keys.contains("x")&&keys.contains("y"))menu.setPair("x","y",(keys.contains("seed") ? (UiMessages.text("editor.stageplay_menu_presentation.amount_whwdbs")) : (UiMessages.text("editor.stageplay_menu_presentation.position_dfplqh"))));
        if(keys.contains("offsetX")&&keys.contains("offsetY"))menu.setPair("offsetX","offsetY",UiMessages.text("editor.stageplay_menu_presentation.offset_130iq43"));
        if(keys.contains("deadX")&&keys.contains("deadY"))menu.setPair("deadX","deadY",UiMessages.text("editor.stageplay_menu_presentation.dead_zone_62jzrc"));
        if(worldPosition)menu.setPositionPicker("x",keys.contains("y")?"y":null,floor,snap);
        if(!advanced.isEmpty()) {menu.setSection(UiMessages.text("editor.stageplay_menu_presentation.advanced_1o7meyq"),advanced.toArray(String[]::new));menu.setSectionSummary(UiMessages.text("editor.stageplay_menu_presentation.advanced_1o7meyq"),(keys.contains("nodes") ? (UiMessages.text("editor.stageplay_menu_presentation.default_limits_eb1tkn")) : (UiMessages.text("editor.stageplay_menu_presentation.more_settings_18hdilq"))));}
        menu.setHelp(hint,UiMessages.text("editor.stageplay_menu_presentation.the_complete_original_field_labels_values_and_error_det_29vw8g", Map.of("value0", String.valueOf(title))));
    }
    private static void add(List<String> target,Set<String> keys,String... names){for(String name:names)if(keys.contains(name))target.add(name);}

    private static void applyTitle(EditorProjectMenu menu, PresentationKind kind) {
        switch (kind) {
            case ADD_ACTOR -> menu.setTitle(UiMessages.text("editor.stageplay_hud.add_actor_8pm4k6"));
            case ACTOR_PROPERTIES -> menu.setTitle(UiMessages.text("editor.stageplay_workspace.actor_properties_1777ala"));
            case ADD_OBJECT -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_add_object"));
            case OBJECT_PROPERTIES -> menu.setTitle(UiMessages.text("editor.editor_hud.object_properties_1cbq7pw"));
            case OPEN_SCENE -> menu.setTitle(UiMessages.text("editor.stageplay_workspace.open_scene_eka79y"));
            case SAVE_SCENE -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_save_scene"));
            case NATIVE_HOVER -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_hover"));
            case NATIVE_FLY_TO -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_fly_to"));
            case NATIVE_GLIDE_TO -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_glide_to"));
            case NATIVE_WAIT -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_wait"));
            case NATIVE_RELEASE -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_release"));
            case NATIVE_CROUCH -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_crouch"));
            case NATIVE_ROLL -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_roll"));
            case NATIVE_SPINDASH -> menu.setTitle(EditorLabels.value("SPINDASH"));
            case NATIVE_FACE_LEFT -> menu.setTitle(EditorLabels.value("FACE_LEFT"));
            case NATIVE_FACE_RIGHT -> menu.setTitle(EditorLabels.value("FACE_RIGHT"));
            case NATIVE_LOOK_UP -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_native_look_up"));
            case FLOOR_PLACEMENTS -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_floor_placements"));
            case ROUTE_RESULT -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_route_result"));
            case CAMERA_KEYS -> menu.setTitle(UiMessages.text("editor.stageplay_workspace.camera_keys_1oo6ju7"));
            case OBJECT_LIBRARY -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_object_library"));
            case LOADED_OBJECTS -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_loaded_objects"));
            case OBJECT_PARTS -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_object_parts"));
            case DIALOGUE_STYLES -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_dialogue_styles"));
            case DIALOGUE_TYPING -> menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.dialogue_typing_9i6ljz"));
            case APPEARANCE_LIMITS -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_appearance_limits"));
            case FLOOR_REFUSAL -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_floor_refusal"));
            case PART_KEYS -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_part_keys"));
            case OBJECT_MOVES -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_object_moves"));
            case OBJECT_CAST -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_object_cast"));
            case SCENE_CHANGES -> menu.setTitle(UiMessages.text("editor.stageplay_hud.scene_changes_1bhmq6n"));
            case CAMERA_SHAKE -> menu.setTitle(UiMessages.text("editor.stageplay_hud.camera_shake_y55ffv"));
            case CAPTION_LINES -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_caption_lines"));
            case OBJECT_KEYS -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_object_keys"));
            case PART_OVERRIDES -> menu.setTitle(UiMessages.text("editor.stageplay_menu_presentation.part_overrides_f13cd6"));
            case CHOOSE_TYPER -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_choose_typing"));
            case CHOOSE_STYLE -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.action_choose_style"));
            case OBJECT_PART -> menu.setTitle(UiMessages.text("editor.stageplay.presentation.title_object_part"));
            default -> { }
        }
    }

    static PresentationKind nativeIntentPresentation(PuppetCommandKind kind) {
        return switch (kind) {
            case HOVER -> PresentationKind.NATIVE_HOVER;
            case FLY_TO -> PresentationKind.NATIVE_FLY_TO;
            case GLIDE_TO -> PresentationKind.NATIVE_GLIDE_TO;
            case WAIT -> PresentationKind.NATIVE_WAIT;
            case RELEASE -> PresentationKind.NATIVE_RELEASE;
            case CROUCH -> PresentationKind.NATIVE_CROUCH;
            case ROLL -> PresentationKind.NATIVE_ROLL;
            case LOOK_UP -> PresentationKind.NATIVE_LOOK_UP;
            case SPINDASH -> PresentationKind.NATIVE_SPINDASH;
            case FACE_LEFT -> PresentationKind.NATIVE_FACE_LEFT;
            case FACE_RIGHT -> PresentationKind.NATIVE_FACE_RIGHT;
            default -> PresentationKind.DEFAULT;
        };
    }

    private record PresentedAction(String label, boolean primary, Runnable callback) implements Runnable {
        private PresentedAction { Objects.requireNonNull(label); Objects.requireNonNull(callback); }
        @Override public void run() { callback.run(); }
    }

    static EditorProjectMenu.Action action(String fullLabel, String compactLabel, boolean primary, Runnable callback) {
        return new EditorProjectMenu.Action(fullLabel, new PresentedAction(compactLabel, primary, callback));
    }

    private static void actions(EditorProjectMenu menu, String originalTitle, PresentationKind kind) {
        String hint = switch (kind) {
            case FLOOR_PLACEMENTS -> UiMessages.text("editor.stageplay.presentation.hint_floor_placements");
            case ROUTE_RESULT -> UiMessages.text("editor.stageplay_workspace.review_the_path_before_applying_it_1s4jqwk");
            case DIRECT_ACTOR -> UiMessages.text("editor.stageplay.presentation.hint_direct_actor");
            case CAMERA_KEYS -> UiMessages.text("editor.stageplay.presentation.hint_camera_keys");
            case OBJECT_LIBRARY -> UiMessages.text("editor.stageplay.presentation.hint_object_library");
            case LOADED_OBJECTS -> UiMessages.text("editor.stageplay.presentation.hint_loaded_objects");
            case OBJECT_PARTS -> UiMessages.text("editor.stageplay.presentation.hint_object_parts");
            case FLOOR_REFUSAL -> UiMessages.text("editor.stageplay.presentation.hint_floor_refusal");
            default -> UiMessages.text("editor.stageplay.presentation.choose_action");
        };
        menu.setHelp(hint, UiMessages.text("editor.stageplay.presentation.action_details", Map.of("title", originalTitle)));
        var entries = menu.actionEntries();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            var presentation = entry.callback() instanceof PresentedAction value ? value : null;
            String label = presentation == null ? entry.label() : presentation.label();
            menu.setActionLabel(i, label.split("\\R", 2)[0]);
            if (i == 0 && presentation != null && presentation.primary()) menu.setPrimaryAction(i);
        }
    }

}
