package com.openggf.editor.stageplay;

import com.openggf.editor.project.EditorProjectMenu;
import java.util.*;

/** Shared Stageplay wording/layout. Saved values and the form owner's callbacks are unchanged. */
final class StageplayMenuPresentation {
    private StageplayMenuPresentation() { }
    record Context(Map<String,String> players,Map<String,String> objects,Map<String,List<String>> choices,
                   java.util.function.Consumer<EditorProjectMenu> objectChoices) { }
    static String words(String text) {
        return java.util.regex.Pattern.compile("\\b[A-Z][A-Z_]{2,}\\b").matcher(text)
                .replaceAll(m -> Arrays.stream(m.group().split("_")).map(w -> w.charAt(0)+w.substring(1).toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.joining(" ")));
    }
    static String sentence(String text) {
        String first=words(text.split("(?<=[.!?])\\s+|\\R",2)[0]);
        return first.length()>83 ? "Open Details for the current status." : first;
    }
    static void configure(EditorProjectMenu menu, Context context, String selected) {
        String title=menu.title();var fields=menu.formFields();
        if(fields.isEmpty()) { actions(menu,title);
            for(int i=0;i<menu.actionEntries().size();i++){String raw=menu.actionEntries().get(i).label();
                for(var actor:context.objects().entrySet())if(raw.equals(actor.getValue()+" / "+actor.getKey())||raw.equals(actor.getValue()+" (object)"))menu.setActionLabel(i,actor.getValue());
                for(var actor:context.players().values())if(raw.startsWith(actor+" (")&&raw.endsWith(")"))menu.setActionLabel(i,actor);}
            return; }
        Set<String> keys=new HashSet<>();fields.forEach(f->keys.add(f.key()));
        var names=new LinkedHashMap<String,String>();names.putAll(context.players());names.putAll(context.objects());
        // Short labels are presentation metadata; the original labels/ranges remain in F1.
        Map<String,String> labels=Map.ofEntries(
                Map.entry("name","Name"),Map.entry("game","Game"),Map.entry("character","Character"),Map.entry("mode","Mode"),
                Map.entry("x","X"),Map.entry("y","Y"),Map.entry("frame","Frame"),Map.entry("start","Start"),Map.entry("end","End"),
                Map.entry("width","Width"),Map.entry("height","Height"),Map.entry("duration","Length"),Map.entry("zone","Zone"),Map.entry("act","Act"),
                Map.entry("animation","Animation"),Map.entry("pose","Pose"),Map.entry("visible","Visible"),Map.entry("scale","Scale"),Map.entry("interpolation","Easing"),
                Map.entry("rom","ROM identity"),Map.entry("project","Level project"),Map.entry("snap","Start on floor"),Map.entry("collision","Collision path"),
                Map.entry("urgency","Effort"),Map.entry("hold","Hold"),Map.entry("timeout","Timeout"),Map.entry("condition","Wait until"),Map.entry("layout","Placement"),
                Map.entry("object","Object number"),Map.entry("subtype","Subtype"),Map.entry("offsetX","Offset X"),Map.entry("offsetY","Offset Y"),
                Map.entry("zoom","Zoom"),Map.entry("follow","Follow"),Map.entry("deadX","Dead zone X"),Map.entry("deadY","Dead zone Y"),Map.entry("ahead","Look ahead"),Map.entry("smooth","Smoothing"),
                Map.entry("frequency","Frequency"),Map.entry("seed","Random seed"),Map.entry("transition","Transition"),Map.entry("pitch","Pitch"),Map.entry("variation","Variation"),
                Map.entry("length","Length"),Map.entry("level","Level"),Map.entry("tone","Tone"),Map.entry("rhythm","Blip on"),Map.entry("text","Text"),Map.entry("portrait","Portrait"),
                Map.entry("id","Name"),Map.entry("speed","Speed"),Map.entry("punctuation","Punctuation pause"),Map.entry("space","Space pause"),Map.entry("panel","Panel colour"),Map.entry("border","Border colour"),
                Map.entry("padding","Padding"),Map.entry("asset","File"),Map.entry("slice","Border size"),Map.entry("pointer","Speaker pointer"),Map.entry("color","Colour"),Map.entry("kind","Action"),Map.entry("sound","Sound number"),
                Map.entry("gain","Volume"),Map.entry("top","Search top"),Map.entry("bottom","Search bottom"),Map.entry("halfWidth","Half width"),Map.entry("footOffset","Foot offset"),Map.entry("collisionPath","Collision path"),
                Map.entry("actor","Who"),Map.entry("frames","Frames"),Map.entry("nodes","Search nodes"),Map.entry("ticks","Probe ticks"),Map.entry("millis","Time budget"),Map.entry("startingAction","Start"),Map.entry("travelSpeed","Speed"),
                Map.entry("endingAction","Stop"),Map.entry("endingFacing","Face"),Map.entry("sourceKind","Source kind"),Map.entry("parts","Part overrides"),Map.entry("partId","Part"),Map.entry("delete","Delete"),Map.entry("path","File"),Map.entry("out","File"));
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
        for(String key:List.of("frame","start","end","hold","timeout","duration","frames","length"))if(keys.contains(key))menu.setUnit(key,"frames");
        if(keys.contains("game"))menu.setValueLabels("game",Map.of("s1","Sonic 1","s2","Sonic 2","s3k","Sonic 3 & K"));
        if(keys.contains("character"))menu.setValueLabels("character",Map.of("sonic","Sonic","tails","Tails","knuckles","Knuckles"));
        if(keys.contains("actor")) {
            var options=new LinkedHashMap<String,String>();
            if(menu.values().get("actor").isEmpty() || keys.contains("kind"))options.put("","Placed object");
            if(keys.contains("nodes")) {
                if(keys.contains("startingAction"))options.putAll(context.players());
                else options.putAll(context.objects());
            }else names.forEach(options::putIfAbsent);
            if(!options.containsKey(menu.values().get("actor")))options.put(menu.values().get("actor"),menu.values().get("actor"));
            menu.configureField("actor","Who",List.copyOf(options.keySet()),options);
        }
        String hint="Choose the values, then apply the change.";
        List<String> advanced=new ArrayList<>();
        boolean worldPosition=false, floor=false, snap=true;
        if(keys.contains("nodes")) {
            menu.setTitle(keys.contains("startingAction")?"Plan route":"Plan object route");hint="Find a playable path to a destination.";
            menu.setGroup("Destination","x","y");worldPosition=true;floor=true;snap=true;
            if(keys.contains("startingAction"))menu.setGroup("Motion","startingAction","travelSpeed","endingAction","endingFacing");
            else menu.setGroup("Motion","speed");
            add(advanced,keys,"frames","nodes","ticks","millis","urgency","halfWidth","height","footOffset","collisionPath");
            if(keys.contains("endingAction"))menu.setValueLabels("endingAction",Map.of("Gentle Stop","Gentle","Hard Stop","Hard"));
            if(keys.contains("endingFacing"))menu.setValueLabels("endingFacing",Map.of("Inherit","Keep"));
            menu.setUnit("millis","ms");menu.setSubmitLabel("Find route");
        } else if(keys.contains("top")&&keys.contains("bottom")) {
            menu.setTitle("Place on floor");hint="Find a floor near this actor.";
            menu.configureField("x","Column");worldPosition=true;floor=true;
            menu.addContextField("actor","Who",selected,names.getOrDefault(selected,selected));
            add(advanced,keys,"top","bottom","halfWidth","height","footOffset","collisionPath");menu.setSubmitLabel("Find floors");
        } else if(keys.contains("portrait")) {
            menu.setTitle("Dialogue line");hint="Write the line and choose its timing.";
            menu.setGroup("Line","text");menu.setGroup("Timing","start","end");menu.setMultilinePreview("text");
            add(advanced,keys,"name","x","y","width","height","portrait");menu.setSubmitLabel("Apply line");
        } else if(keys.contains("tone")) {
            hint="Choose a voice or start with a preset.";add(advanced,keys,"variation","length");
        } else if(keys.contains("character")&&keys.contains("mode")) {
            menu.setTitle(title.startsWith("Add")?"Add actor":"Actor properties");hint="Puppet moves with physics; Poser uses keys.";
            menu.setGroup("Actor","name","character","mode");if(keys.contains("x")){menu.setGroup("Placement","x","y");worldPosition=true;floor=true;}
            add(advanced,keys,"game","snap","collision");
        } else if(keys.contains("follow")) {
            menu.setTitle("Camera key");hint="Frame the shot, then choose its easing.";
            menu.setGroup("Framing","x","y","zoom","interpolation");worldPosition=true;snap=false;
            add(advanced,keys,"follow","deadX","deadY","ahead","smooth","offsetX","offsetY");
        } else if(keys.contains("seed")) {
            menu.setTitle("Camera shake");hint="Layer a shake over the camera movement.";
            menu.configureField("x","Across");menu.configureField("y","Up and down");menu.setGroup("Timing","start","end");menu.setGroup("Shake","x","y","frequency");add(advanced,keys,"seed");
        } else if(keys.contains("transition")) {
            menu.setTitle("Scene change");hint="Choose the next set and the transition.";menu.setGroup("Transition","frame","transition","duration");menu.setGroup("Set","game","zone","act");add(advanced,keys,"rom","project");
        } else if(keys.contains("duration")&&keys.contains("width")) {
            menu.setTitle("Scene and set");hint="Name the scene and choose its set.";menu.setGroup("Scene","name","duration");menu.setGroup("Set","game","zone","act");add(advanced,keys,"width","height","rom","project");
        } else if(keys.contains("sourceKind")) {
            menu.setTitle(title.startsWith("Cast")?"Add object":"Object properties");hint="Choose an object and its starting position.";
            context.objectChoices().accept(menu);
            menu.onValueChanged((key,value)->{if(List.of("game","zone","act","object","sourceKind").contains(key))context.objectChoices().accept(menu);});
            menu.setGroup("Object","name","object");menu.setGroup("Direction","mode","x","y");worldPosition=true;add(advanced,keys,"game","zone","act","layout","sourceKind","subtype");
        } else if(keys.contains("partId")) {
            menu.setTitle("Part override");hint="Choose a frame for one visible part.";add(advanced,keys,"partId","delete");
        } else if(keys.contains("mode")&&keys.contains("delete")) {
            menu.setTitle("Object key");hint="Choose how this object appears at the playhead.";menu.setGroup("Placement","frame","x","y");menu.setGroup("Direction","mode","facing");worldPosition=true;add(advanced,keys,"animation","pose","visible","interpolation","parts","delete");
        } else if(keys.contains("kind")&&keys.contains("gain")) {
            menu.setTitle("Audio cue");hint="Choose a sound and when it plays.";menu.setValueLabels("kind",Map.of("ROM_MUSIC","Music","ROM_SFX","Sound effect","VOICE","Audio file","MUSIC_STOP","Stop music"));add(advanced,keys,"game","asset","gain");
        } else if(keys.contains("kind")&&keys.contains("actor")) {
            menu.setTitle("World command");hint="Clear objects or remove a cast member.";add(advanced,keys,"layout","object","subtype","delete");
        } else if(keys.contains("kind")&&keys.contains("speed")) {
            menu.setTitle("Object move");hint="Move or stop this directed object.";worldPosition=true;add(advanced,keys,"delete");
        } else if(keys.contains("timeout")) {
            String actionTitle=switch(title){case "Native intent: HOVER"->"Hover action";case "Native intent: FLY_TO"->"Fly to";case "Native intent: GLIDE_TO"->"Glide to";case "Wait","Native intent: WAIT"->"Wait action";case "Release","Native intent: RELEASE"->"Release controls";case "Crouch","Native intent: CROUCH"->"Crouch action";case "Roll","Native intent: ROLL"->"Roll action";case "Native intent: LOOK_UP"->"Look up";default->words(title.replace("Native intent: ",""));};
            menu.setTitle(actionTitle);hint="Choose when the action starts.";
            if(keys.contains("x")){menu.setGroup("Destination","x","y","urgency");worldPosition=true;}
            add(advanced,keys,"layout","object","subtype","offsetX","offsetY");if(!keys.contains("condition"))add(advanced,keys,"timeout");
        } else if(keys.contains("panel")) {
            menu.setTitle("Dialogue style");menu.setSubmitLabel("Apply style");hint="Style the dialogue panel and speaker name.";
            menu.configureField("text","Text colour");menu.configureField("name","Name colour");add(advanced,keys,"padding","asset","slice");
        } else if(keys.contains("punctuation")) {
            menu.setTitle("Dialogue typing");hint="Choose how quickly each letter appears.";add(advanced,keys,"space");
        } else if(keys.contains("color")) {
            menu.setTitle("Caption line");hint="Write the caption and choose its timing.";menu.setMultilinePreview("text");menu.setGroup("Line","text");menu.setGroup("Timing","start","end");add(advanced,keys,"x","y","color");
        } else if(keys.contains("scale")) {
            menu.setTitle("Actor key");hint="Place and pose the actor at the playhead.";menu.setGroup("Placement","x","y","facing");worldPosition=true;add(advanced,keys,"animation","pose","visible","scale","interpolation");
        } else if(title.startsWith("Shot placement")) {
            menu.setTitle("Actor position");hint="Set where this actor starts the shot.";worldPosition=true;floor=true;
        } else if(title.startsWith("Visibility")) {
            menu.setTitle("Actor visibility");hint="Show or hide the actor at the playhead.";
        } else if(title.contains("reveal timing")) {
            menu.setTitle("Reveal timing");hint="Set when each letter appears.";
        } else if(keys.contains("path")) {
            menu.setTitle(title.equals("Open Stageplay")?"Open scene":title.equals("Save Stageplay")?"Save scene":title);
            hint=title.contains("Import")?"Open a screenplay as a new scene.":title.contains("Export")?"Write a new screenplay file.":"Choose the scene file.";
        } else if(keys.contains("out")) {menu.setTitle("Export film");hint="Choose a new film file.";}
        if(keys.contains("x")&&keys.contains("y"))menu.setPair("x","y",keys.contains("seed")?"Amount":"Position");
        if(keys.contains("offsetX")&&keys.contains("offsetY"))menu.setPair("offsetX","offsetY","Offset");
        if(keys.contains("deadX")&&keys.contains("deadY"))menu.setPair("deadX","deadY","Dead zone");
        if(worldPosition)menu.setPositionPicker("x",keys.contains("y")?"y":null,floor,snap);
        if(!advanced.isEmpty()) {menu.setSection("Advanced",advanced.toArray(String[]::new));menu.setSectionSummary("Advanced",keys.contains("nodes")?"Default limits":"More settings");}
        menu.setHelp(hint,title+". The complete original field labels, values and error details remain in F1. Position fields open the level picker; F2 keeps numeric entry. Closed sections keep their values until Apply.");
    }
    private static void add(List<String> target,Set<String> keys,String... names){for(String name:names)if(keys.contains(name))target.add(name);}
    private static void actions(EditorProjectMenu menu,String title) {
        String next=words(title);String hint="Choose an action.";
        if(title.startsWith("Place on floor:")){next="Floor placements";hint="Choose a floor, then check it in playback.";}
        else if(title.equals("Collision route")){next="Route result";hint="Review the path before applying it.";}
        else if(title.startsWith("Direct "))hint="Choose a move, jump or other action.";
        else if(title.startsWith("Camera at frame")){next="Camera keys";hint="Capture or edit the framing.";}
        else if(title.startsWith("Every native object")){next="Object library";hint="Choose an object to read its limits.";}
        else if(title.startsWith("Loaded objects")){next="Loaded objects";hint="Choose a loaded object to direct.";}
        else if(title.startsWith("Native visual parts")){next="Object parts";hint="Choose a part to inspect its frames.";}
        else if(title.startsWith("Reusable dialogue decorations"))next="Dialogue styles";
        else if(title.startsWith("Reusable dialogue typers"))next="Dialogue typing";
        else if(title.startsWith("Declared native"))next="Appearance limits";
        else if(title.startsWith("Refused floor")){next="Floor unavailable";hint="F1 explains why this floor was refused.";}
        else if(title.startsWith("Choose object part"))next="Part keys";
        else if(title.startsWith("Visual Puppet"))next="Object moves";
        else if(title.equals("ROM object cast"))next="Object cast";
        else if(title.equals("Scene changes / transitions"))next="Scene changes";
        else if(title.equals("Camera shake / quake"))next="Camera shake";
        else if(title.equals("Captions"))next="Caption lines";
        if(next.startsWith("Object mode /"))next="Object keys";
        else if(next.startsWith("Part overrides /"))next="Part overrides";
        else if(next.startsWith("Choose dialogue typer"))next="Choose typing";
        else if(next.startsWith("Choose dialogue decoration"))next="Choose style";
        else if(next.startsWith("root/"))next="Object part";
        else if(next.matches("\\d+ / .*"))next=next.replaceFirst("^\\d+ / ","");
        menu.setTitle(next);menu.setHelp(hint,title+". F1 retains the full explanations and original entries.");
        var entries=menu.actionEntries();
        for(int i=0;i<entries.size();i++) {
            String label=words(entries.get(i).label()).replace("ROM object cast / clear","Object cast").replace("Mode / position / animation keys","Position and pose").replace("Source / anchor / properties","Properties").replace("Available parts / appearance exclusions","Parts and limits").replace("Puppet move / stop commands","Move or stop").replace("Delete authored role and dialogue","Delete role").replace("Edit individual part overrides","Part overrides").replace("Add world command / placed removal","Add command").replace("ROM object coverage / restrictions","Object library").replace("Loaded level objects","Loaded objects").replace("World commands / removals","World commands").replace("Cast ROM object","Add object").replace("Cast Rom object","Add object").replace("Rom object coverage / restrictions","Object library").replace("Spawn / current shot placement","Place actor").replace("Visibility at playhead","Actor visibility").replace("Remove from world at playhead","Remove from world").replace("Edit line, timing and panel","Edit line").replace("Choose decoration","Choose style").replace("Choose typer","Choose typing").replace("Reusable decorations","Dialogue styles").replace("Reusable typers","Dialogue typing").replace("Offscreen speaker / narrator","Offscreen speaker").replace("Check inputs and apply route","Apply route").replace("Refresh route status","Refresh");
            if(label.startsWith("Apply: X "))label=label.replaceAll(", floor .*","").replace("authored Y","Y");
            if(label.startsWith("Refused floor "))label=label.replaceAll(":.*","");
            if(label.startsWith("root/"))label="Part "+(i+1);
            menu.setActionLabel(i,label.split("\\R",2)[0]);
        }
        if(!entries.isEmpty()&&(entries.getFirst().label().startsWith("Add ")||entries.getFirst().label().startsWith("Cast ")||entries.getFirst().label().startsWith("Apply:")||entries.getFirst().label().startsWith("Check inputs")))menu.setPrimaryAction(0);
    }
}
