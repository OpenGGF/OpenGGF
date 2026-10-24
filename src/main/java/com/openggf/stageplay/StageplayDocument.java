package com.openggf.stageplay;

import com.openggf.game.ModApi;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable, portable authored scene. Time is measured in 60 Hz presentation frames;
 * coordinates are stage pixels, independent of any gameplay or trace clock.
 * Resource references never contain ROM-derived bytes.
 */
@ModApi
public record StageplayDocument(int version, String name, int durationFrames, int width, int height,
        SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions,
        List<AudioCue> audio, List<CameraEffect> cameraEffects, List<SceneChange> sceneChanges,
        List<DialogueTyper> dialogueTypers, List<DialogueSkin> dialogueSkins, List<DialogueCue> dialogue,
        List<PuppetTrack> puppets, List<ObjectActor> objectActors, List<WorldCommand> worldCommands) {
    public static final int VERSION = 8;
    public static final int FRAMES_PER_SECOND = 60;
    public static final int MAX_DURATION_FRAMES = 36_000;
    private static final int MAX_KEYS = 100_000;
    private static final double MAX_COORDINATE = 1_048_576;

    public StageplayDocument {
        require(version == VERSION, "Unsupported Stageplay version: " + version);
        name = label(name, "name");
        range(durationFrames, 1, MAX_DURATION_FRAMES, "durationFrames");
        range(width, 160, 1920, "width");
        range(height, 112, 1080, "height");
        require(set != null, "set is required");
        actors = boundedCopy(actors, 32, "actors");
        camera = boundedCopy(camera, MAX_KEYS, "camera");
        captions = boundedCopy(captions, 1024, "captions");
        audio = boundedCopy(audio, 4096, "audio");
        cameraEffects = boundedCopy(cameraEffects, 256, "cameraEffects");
        sceneChanges = boundedCopy(sceneChanges, 128, "sceneChanges");
        dialogueTypers = boundedCopy(dialogueTypers, 64, "dialogueTypers");
        dialogueSkins = boundedCopy(dialogueSkins, 64, "dialogueSkins");
        dialogue = boundedCopy(dialogue, 1024, "dialogue");
        puppets = boundedCopy(puppets, 32, "puppets");
        objectActors = boundedCopy(objectActors, 1024, "objectActors");
        worldCommands = boundedCopy(worldCommands, 4096, "worldCommands");
        require(!camera.isEmpty(), "camera must start at frame zero");
        Set<String> ids = new HashSet<>();
        int totalKeys = camera.size();
        for (Actor actor : actors) {
            require(ids.add(actor.id()), "Duplicate id: " + actor.id());
            totalKeys += actor.keys().size();
            require(actor.keys().getLast().frame() < durationFrames, "Actor key exceeds scene duration");
        }
        Set<String> placements = new HashSet<>();
        for (ObjectActor actor : objectActors) {
            require(ids.add(actor.id()), "Duplicate id: " + actor.id());
            totalKeys += actor.keys().size();
            for (ObjectKey key : actor.keys()) totalKeys += key.parts().size();
            require(actor.keys().getLast().frame() < durationFrames, "Object key exceeds scene duration");
            if (actor.layoutIndex() >= 0)
                require(placements.add(actor.gameId() + ":" + actor.zone() + ":" + actor.act() + ":"
                                + actor.layoutIndex() + ":" + actor.objectId() + ":" + actor.subtype()),
                        "Duplicate placed object identity: " + actor.id());
        }
        require(totalKeys <= MAX_KEYS, "Scene has too many keys");
        int previousFrame = -1;
        for (CameraKey key : camera) {
            for (String actor : key.follow().actors()) {
                require(actors.stream().anyMatch(a -> a.id().equals(actor)), "Unknown camera actor: " + actor);
                require(puppets.stream().anyMatch(p -> p.actorId().equals(actor)), "Following requires a Puppet actor: " + actor);
                int first = sceneChanges.stream().filter(c -> c.frame() <= key.frame()).mapToInt(SceneChange::frame).max().orElse(0);
                int end = sceneChanges.stream().filter(c -> c.frame() > key.frame()).mapToInt(SceneChange::frame).min().orElse(durationFrames);
                require(actors.stream().filter(a -> a.id().equals(actor)).findFirst().orElseThrow().keys().stream()
                        .anyMatch(k -> k.frame() >= first && k.frame() < end && k.visible()), "Camera target is absent from this shot: " + actor);
            }
            require(key.frame() > previousFrame, "Camera keys must be strictly ascending");
            require(previousFrame != -1 || key.frame() == 0, "Camera must start at frame zero");
            require(key.frame() < durationFrames, "Camera key exceeds scene duration");
            previousFrame = key.frame();
        }
        for (Caption caption : captions) {
            require(ids.add(caption.id()), "Duplicate id: " + caption.id());
            require(caption.endFrame() <= durationFrames, "Caption exceeds scene duration");
        }
        for (AudioCue cue : audio) {
            require(ids.add(cue.id()), "Duplicate id: " + cue.id());
            require(cue.frame() < durationFrames, "Audio cue exceeds scene duration");
        }
        for (CameraEffect effect : cameraEffects) {
            require(ids.add(effect.id()), "Duplicate id: " + effect.id());
            require(effect.endFrame() <= durationFrames, "Camera effect exceeds scene duration");
        }
        int previousChange = -1, previousTransitionEnd = -1;
        Set<Integer> cameraFrames = new HashSet<>();
        for (CameraKey key : camera) cameraFrames.add(key.frame());
        for (SceneChange change : sceneChanges) {
            require(ids.add(change.id()), "Duplicate id: " + change.id());
            require(change.frame() > previousChange, "Scene changes must be strictly ascending");
            int start = change.frame() - change.durationFrames() / 2;
            int end = change.frame() + (change.durationFrames() + 1) / 2;
            require(start >= 0 && end < durationFrames, "Scene transition exceeds scene duration");
            require(start >= previousTransitionEnd, "Scene transitions must not overlap");
            require(cameraFrames.contains(change.frame()), "Scene change requires a camera key at its frame");
            for (Actor actor : actors) {
                require(actor.keys().stream().anyMatch(key -> key.frame() == change.frame()),
                        "Scene change requires an actor key at its frame: " + actor.id());
            }
            for (ObjectActor actor : objectActors)
                require(actor.keys().stream().anyMatch(key -> key.frame() == change.frame()),
                        "Scene change requires an object key at its frame: " + actor.id());
            previousChange = change.frame();
            previousTransitionEnd = end;
        }
        Set<String> puppetActors = new HashSet<>();
        Set<Integer> boundaries = new HashSet<>();
        boundaries.add(0);
        sceneChanges.forEach(change -> boundaries.add(change.frame()));
        int commandCount = 0;
        for (PuppetTrack puppet : puppets) {
            require(puppetActors.add(puppet.actorId()), "Duplicate puppet track: " + puppet.actorId());
            Actor actor = actors.stream().filter(value -> value.id().equals(puppet.actorId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown puppet actor: " + puppet.actorId()));
            ActorKey anchor = actor.keys().getFirst();
            for (ActorKey key : actor.keys()) {
                require(key.interpolation() == Interpolation.HOLD && key.animation() == 0 && key.pose() == -1
                                && key.scale() == 1,
                        "Physics puppets require held unit-scale keys and native animation: " + actor.id());
                if (boundaries.contains(key.frame())) anchor = key;
                else require(key.x() == anchor.x() && key.y() == anchor.y() && key.facing() == anchor.facing(),
                        "Physics puppet position/facing keys are only allowed at shot boundaries: " + actor.id());
            }
            for (PuppetCommand command : puppet.commands()) {
                if (command.kind() == PuppetCommandKind.FLY_TO || command.kind() == PuppetCommandKind.HOVER)
                    require(actor.gameId().equals("s3k") && actor.character().equals("tails"), "Native flight requires S3K Tails");
                if (command.kind() == PuppetCommandKind.GLIDE_TO)
                    require(actor.gameId().equals("s3k") && actor.character().equals("knuckles"), "Native glide requires S3K Knuckles");
                require(ids.add(command.id()), "Duplicate id: " + command.id());
                require(command.frame() < durationFrames, "Puppet command exceeds scene duration");
                if (command.kind() == PuppetCommandKind.JUMP || command.kind() == PuppetCommandKind.HOVER
                        || command.kind() == PuppetCommandKind.CROUCH || command.kind() == PuppetCommandKind.LOOK_UP) {
                    int end = command.frame() + command.holdFrames();
                    require(end <= durationFrames, "Jump hold exceeds scene duration");
                    for (SceneChange change : sceneChanges)
                        require(change.frame() <= command.frame() || change.frame() >= end,
                                "Jump hold crosses a scene boundary");
                }
            }
            commandCount += puppet.commands().size();
        }
        for (ObjectActor actor : objectActors) {
            ObjectKey anchor = actor.keys().getFirst();
            for (ObjectKey key : actor.keys()) {
                if (boundaries.contains(key.frame())) anchor = key;
                SetSource objectSet = set;
                for (SceneChange change : sceneChanges) if (change.frame() <= key.frame()) objectSet = change.set();
                if (key.visible()) {
                    if (actor.layoutIndex() >= 0)
                        require(actor.gameId().equals(objectSet.gameId()) && actor.zone() == objectSet.zone()
                                        && actor.act() == objectSet.act(),
                                "Placed object belongs to another set: " + actor.id());
                }
                if (key.mode() == ObjectMode.FREE)
                    require(key.x() == anchor.x() && key.y() == anchor.y() && key.facing() == anchor.facing(),
                            "Free object keys must retain the shot's native anchor: " + actor.id());
            }
            for (ObjectCommand command : actor.commands()) {
                require(ids.add(command.id()), "Duplicate id: " + command.id());
                require(command.frame() < durationFrames, "Object command exceeds scene duration");
                ObjectKey pose = actor.keys().getFirst();
                for (ObjectKey key : actor.keys()) if (key.frame() <= command.frame()) pose = key;
                require(pose.visible() && pose.mode() == ObjectMode.PUPPET,
                        "Object commands require a visible Puppet object: " + command.id());
            }
            commandCount += actor.commands().size();
        }
        int previousWorldCommand = -1;
        for (WorldCommand command : worldCommands) {
            require(ids.add(command.id()), "Duplicate id: " + command.id());
            require(command.frame() >= previousWorldCommand, "World commands must be ordered by frame");
            require(command.frame() < durationFrames, "World command exceeds scene duration");
            previousWorldCommand = command.frame();
            if (!command.actorId().isEmpty())
                require(objectActors.stream().anyMatch(actor -> actor.id().equals(command.actorId())),
                        "Unknown object actor: " + command.actorId());
        }
        commandCount += worldCommands.size();
        require(commandCount <= 4096, "Scene has too many directing commands");
        Set<String> typerIds = new HashSet<>(), skinIds = new HashSet<>();
        for (DialogueTyper typer : dialogueTypers)
            require(typerIds.add(typer.id()), "Duplicate dialogue typer: " + typer.id());
        for (DialogueSkin skin : dialogueSkins)
            require(skinIds.add(skin.id()), "Duplicate dialogue skin: " + skin.id());
        for (DialogueCue cue : dialogue) {
            require(ids.add(cue.id()), "Duplicate id: " + cue.id());
            require(cue.endFrame() <= durationFrames, "Dialogue exceeds scene duration: " + cue.id());
            require(cue.x() + cue.width() <= width && cue.y() + cue.height() <= height,
                    "Dialogue panel exceeds the viewport: " + cue.id());
            Actor speaker = null;
            if (!cue.speakerActor().isEmpty()) {
                for (Actor actor : actors) if (actor.id().equals(cue.speakerActor())) { speaker = actor; break; }
                require(speaker != null, "Unknown dialogue speaker: " + cue.speakerActor());
            }
            String speakerName = cue.speakerName().isEmpty() ? speaker.name() : cue.speakerName();
            StageplayDialogue.validate(cue, speakerName,
                    StageplayDialogue.resolveSkin(dialogueSkins, cue.skinId()),
                    StageplayDialogue.resolveTyper(dialogueTypers, cue.typerId()));
        }
    }

    /** Convenience constructor without object tracks. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio,
            List<CameraEffect> cameraEffects, List<SceneChange> sceneChanges,
            List<DialogueTyper> dialogueTypers, List<DialogueSkin> dialogueSkins, List<DialogueCue> dialogue,
            List<PuppetTrack> puppets) {
        this(version, name, durationFrames, width, height, set, actors, camera, captions, audio, cameraEffects,
                sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, List.of(), List.of());
    }

    /** Convenience constructor for a scene without optional tracks. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio) {
        this(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Convenience constructor with camera effects and scene changes. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio,
            List<CameraEffect> cameraEffects, List<SceneChange> sceneChanges) {
        this(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, List.of(), List.of(), List.of(), List.of());
    }

    /** Convenience constructor with dialogue and manual choreography. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio,
            List<CameraEffect> cameraEffects, List<SceneChange> sceneChanges,
            List<DialogueTyper> dialogueTypers, List<DialogueSkin> dialogueSkins, List<DialogueCue> dialogue) {
        this(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, List.of());
    }

    /** A ten-second scene with an empty cast and a stationary camera. */
    public static StageplayDocument empty(String gameId, int zone, int act, double x, double y,
            int width, int height) {
        return new StageplayDocument(VERSION, "Untitled", 600, width, height,
                new SetSource(gameId, zone, act, "", ""), List.of(),
                List.of(new CameraKey(0, x, y, 1, Interpolation.HOLD)), List.of(), List.of());
    }

    public StageplayDocument withName(String value) {
        return new StageplayDocument(version, value, durationFrames, width, height, set, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    /** Shortening a scene rejects remaining keys/cues beyond its new end. */
    public StageplayDocument withDurationFrames(int value) {
        return new StageplayDocument(version, name, value, width, height, set, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withSize(int newWidth, int newHeight) {
        return new StageplayDocument(version, name, durationFrames, newWidth, newHeight, set, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withSet(SetSource value) {
        return new StageplayDocument(version, name, durationFrames, width, height, value, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withActors(List<Actor> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, value, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withCamera(List<CameraKey> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, value, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withCaptions(List<Caption> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, value, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withAudio(List<AudioCue> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, value, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withCameraEffects(List<CameraEffect> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio, value, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    /** Changes and their required actor/camera anchors must be published as one valid document. */
    public StageplayDocument withSceneChanges(List<SceneChange> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio, cameraEffects, value, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withDialogueTypers(List<DialogueTyper> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, value, dialogueSkins, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withDialogueSkins(List<DialogueSkin> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, value, dialogue, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withDialogue(List<DialogueCue> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, value, puppets, objectActors, worldCommands);
    }

    public StageplayDocument withPuppets(List<PuppetTrack> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, value, objectActors, worldCommands);
    }

    public StageplayDocument withObjectActors(List<ObjectActor> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, value, worldCommands);
    }

    public StageplayDocument withWorldCommands(List<WorldCommand> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets, objectActors, value);
    }

    /** Stock set reference and optional identity/authored-level reference. */
    @ModApi
    public record SetSource(String gameId, int zone, int act, String romSha1, String levelProject) {
        public SetSource {
            gameId = game(gameId);
            range(zone, 0, 255, "zone");
            range(act, 0, 255, "act");
            require(romSha1 != null && (romSha1.isEmpty() || romSha1.matches("[0-9a-f]{40}")),
                    "romSha1 must be empty or a lowercase SHA-1");
            levelProject = relativePath(levelProject, "levelProject", true);
            require(levelProject.isEmpty() || levelProject.toLowerCase(Locale.ROOT).endsWith(".ogfproject"),
                    "levelProject must reference an .ogfproject file");
        }
    }

    /** A named role with stock ROM appearance and authored performance keys. */
    @ModApi
    public record Actor(String id, String name, String gameId, String character, List<ActorKey> keys) {
        public Actor {
            id = identifier(id);
            name = label(name, "actor name");
            gameId = game(gameId);
            require(character != null && switch (gameId) {
                case "s1" -> character.equals("sonic");
                case "s2" -> character.equals("sonic") || character.equals("tails");
                case "s3k" -> character.equals("sonic") || character.equals("tails") || character.equals("knuckles");
                default -> false;
            }, "Unsupported stock character for " + gameId + ": " + character);
            keys = boundedCopy(keys, MAX_KEYS, "actor keys");
            require(!keys.isEmpty() && keys.getFirst().frame() == 0, "Actor keys must start at frame zero");
            int previous = -1;
            for (ActorKey key : keys) {
                require(key.frame() > previous, "Actor keys must be strictly ascending");
                previous = key.frame();
            }
        }
    }

    /** Native logical horizontal direction; deliberate turn/skid/look-back poses retain their art. */
    @ModApi public enum Facing { LEFT, RIGHT }

    /** Interpolation applies from this key to the next; discrete properties change at the key. */
    @ModApi
    public record ActorKey(int frame, double x, double y, int animation, int pose, Facing facing,
            boolean visible, double scale, Interpolation interpolation) {
        public ActorKey {
            range(frame, 0, MAX_DURATION_FRAMES - 1, "actor frame");
            coordinate(x, "actor x");
            coordinate(y, "actor y");
            range(animation, 0, 65535, "animation");
            range(pose, -1, 65535, "pose");
            finiteRange(scale, .01, 64, "scale");
            require(interpolation != null && facing != null, "interpolation and facing are required");
        }
    }

    /** A ROM object role; layoutIndex -1 casts a new instance, otherwise identity addresses a loaded placement. */
    @ModApi
    public record ObjectActor(String id, String name, String gameId, int zone, int act, int objectId,
                              int subtype, int layoutIndex, List<ObjectKey> keys, List<ObjectCommand> commands,
                              ObjectSourceKind sourceKind) {
        public ObjectActor(String id, String name, String gameId, int zone, int act, int objectId,
                           int subtype, int layoutIndex, List<ObjectKey> keys, List<ObjectCommand> commands) {
            this(id, name, gameId, zone, act, objectId, subtype, layoutIndex, keys, commands, ObjectSourceKind.LAYOUT_OBJECT);
        }
        public ObjectActor {
            id = identifier(id); name = label(name, "object actor name"); gameId = game(gameId);
            range(zone, 0, 255, "object source zone"); range(act, 0, 255, "object source act");
            range(objectId, 0, 255, "objectId"); range(subtype, 0, 255, "object subtype");
            range(layoutIndex, -1, 65535, "object layoutIndex");
            require(sourceKind != null, "Object sourceKind is required");
            if (sourceKind == ObjectSourceKind.RELEASED_ANIMAL)
                require(objectId == 0 && subtype == 0 && layoutIndex == -1,
                        "Released animals use the native provider, zero objectId/subtype and no layout identity");
            keys = boundedCopy(keys, MAX_KEYS, "object keys");
            commands = boundedCopy(commands, 4096, "object commands");
            require(!keys.isEmpty() && keys.getFirst().frame() == 0, "Object keys must start at frame zero");
            int previous = -1;
            for (ObjectKey key : keys) {
                require(key.frame() > previous, "Object keys must be strictly ascending");
                previous = key.frame();
            }
            previous = -1;
            for (ObjectCommand command : commands) {
                require(command.frame() > previous, "Object commands must be strictly ascending");
                previous = command.frame();
            }
        }
    }

    /** Discrete mode/animation changes apply at the key; only directed visual modes interpolate position. */
    @ModApi
    public record ObjectKey(int frame, double x, double y, int animation, int pose, Facing facing,
                            boolean visible, Interpolation interpolation, ObjectMode mode, List<ObjectPartPose> parts) {
        public ObjectKey(int frame, double x, double y, int animation, int pose, Facing facing,
                         boolean visible, Interpolation interpolation, ObjectMode mode) {
            this(frame, x, y, animation, pose, facing, visible, interpolation, mode, List.of());
        }
        public ObjectKey {
            range(frame, 0, MAX_DURATION_FRAMES - 1, "object frame");
            coordinate(x, "object x"); coordinate(y, "object y");
            range(animation, 0, 65535, "object animation"); range(pose, -1, 65535, "object pose");
            require(interpolation != null && mode != null && facing != null, "Object interpolation, mode and facing are required");
            parts = boundedCopy(parts, 256, "object parts");
            Set<String> partIds = new HashSet<>();
            for (ObjectPartPose part : parts) require(partIds.add(part.partId()), "Duplicate object part: " + part.partId());
            if (mode == ObjectMode.FREE)
                require(interpolation == Interpolation.HOLD && animation == 0 && pose == -1 && parts.isEmpty(),
                        "Free objects use native animation, held anchors and no part overrides");
        }
    }

    /** Portable graph path formed from parent paths and stable native creation order, never runtime slots. */
    @ModApi
    public record ObjectPartPose(String partId, int animation, int pose) {
        public ObjectPartPose {
            require(partId != null && partId.length() <= 256
                            && partId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}(/[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}){0,31}"),
                    "Invalid portable object part path");
            range(animation, 0, 65535, "object part animation"); range(pose, -1, 65535, "object part pose");
        }
    }

    /** Visual displacement in stage pixels per presentation frame; it never submits native object AI input. */
    @ModApi
    public record ObjectCommand(String id, int frame, ObjectCommandKind kind, double x, double y, double speed) {
        public ObjectCommand {
            id = identifier(id); range(frame, 0, MAX_DURATION_FRAMES - 1, "object command frame");
            require(kind != null, "Object command kind is required");
            coordinate(x, "object destination x"); coordinate(y, "object destination y");
            if (kind == ObjectCommandKind.MOVE_TO) finiteRange(speed, Double.MIN_VALUE, 4096, "object speed");
            else require(x == 0 && y == 0 && speed == 0, "STOP has no destination or speed");
        }
    }

    /** Clears apply to native world roots; remove addresses exactly one cast role or portable loaded placement. */
    @ModApi
    public record WorldCommand(String id, int frame, WorldCommandKind kind, String actorId, ObjectTarget target) {
        public WorldCommand {
            id = identifier(id); range(frame, 0, MAX_DURATION_FRAMES - 1, "world command frame");
            require(kind != null && target != null, "World command fields are required");
            actorId = optionalIdentifier(actorId);
            require(target.offsetX() == 0 && target.offsetY() == 0, "World commands use exact placement identities without offsets");
            if (kind == WorldCommandKind.REMOVE_OBJECT)
                require(!actorId.isEmpty() ^ target.layoutIndex() >= 0, "REMOVE_OBJECT requires exactly one actor or placement identity");
            else require(actorId.isEmpty() && target.layoutIndex() == -1, "Clear commands do not have a target");
        }
    }

    @ModApi public enum ObjectMode { FREE, PUPPET, POSE }
    @ModApi public enum ObjectSourceKind { LAYOUT_OBJECT, RELEASED_ANIMAL }
    @ModApi public enum ObjectCommandKind { MOVE_TO, STOP }
    @ModApi public enum WorldCommandKind { CLEAR_BADNIKS, CLEAR_OBJECTS, REMOVE_OBJECT }

    /** Presence opts an actor into native physics; absent actors retain manual choreography. */
    @ModApi
    public record PuppetTrack(String actorId, boolean snapToFloor, int collisionPath, List<PuppetCommand> commands) {
        public PuppetTrack {
            actorId = identifier(actorId);
            range(collisionPath, 0, 1, "puppet collision path");
            commands = boundedCopy(commands, 4096, "puppet commands");
            int previous = -1, movement = -1, jump = -1, jumpEnd = -1;
            for (PuppetCommand command : commands) {
                require(command.frame() >= previous, "Puppet commands must be ordered by frame");
                previous = command.frame();
                if (command.kind() == PuppetCommandKind.JUMP) {
                    require(command.frame() != jump && command.frame() > jumpEnd,
                            "Puppet jumps require a released tick between button holds");
                    jump = command.frame(); jumpEnd = jump + command.holdFrames();
                } else {
                    require(command.frame() != movement, "Only one puppet movement command is allowed per frame");
                    movement = command.frame();
                }
            }
        }
    }

    /** Native input intent. WAIT gates subsequent due commands until success or a bounded timeout. */
    /** Per-route directing policy; all motion is produced by native controller inputs. */
    @ModApi public record RouteMotion(StartAction startingAction, TravelSpeed travelSpeed,
                                     EndingAction endingAction, EndingFacing endingFacing) {
        public static final RouteMotion DEFAULT = new RouteMotion(StartAction.WALK, TravelSpeed.WALK,
                EndingAction.GENTLE_STOP, EndingFacing.INHERIT);
        /** Ordinary hand-authored intents retain their native-speed countersteering semantics. */
        public static final RouteMotion NATIVE = new RouteMotion(StartAction.WALK, TravelSpeed.RUN,
                EndingAction.HARD_STOP, EndingFacing.INHERIT);
        public RouteMotion {
            require(startingAction != null && travelSpeed != null && endingAction != null && endingFacing != null,
                    "Route motion settings are required");
            require(startingAction != StartAction.SPINDASH || travelSpeed == TravelSpeed.RUN,
                    "Spindash requires Run travel speed; its native launch exceeds the Walk stroll cap");
        }
    }
    @ModApi public enum StartAction { WALK, JUMP, SPINDASH }
    @ModApi public enum TravelSpeed { WALK, RUN }
    @ModApi public enum EndingAction { GENTLE_STOP, HARD_STOP, OVERSHOOT }
    @ModApi public enum EndingFacing { INHERIT, LEFT, RIGHT }

    @ModApi
    public record PuppetCommand(String id, int frame, PuppetCommandKind kind, double x, double y,
                                double urgency, int holdFrames, ObjectTarget target,
                                WaitCondition condition, int timeoutFrames, RouteMotion motion) {
        /** Fixed authored charge window; native physics owns charge and release speed. */
        public static final int SPINDASH_CHARGE_FRAMES = 24;
        public PuppetCommand(String id, int frame, PuppetCommandKind kind, double x, double y,
                             double urgency, int holdFrames) {
            this(id, frame, kind, x, y, urgency, holdFrames, ObjectTarget.none(), WaitCondition.NONE, 0, RouteMotion.NATIVE);
        }
        public PuppetCommand(String id, int frame, PuppetCommandKind kind, double x, double y,
                             double urgency, int holdFrames, ObjectTarget target,
                             WaitCondition condition, int timeoutFrames) {
            this(id, frame, kind, x, y, urgency, holdFrames, target, condition, timeoutFrames, RouteMotion.NATIVE);
        }
        public PuppetCommand {
            id = identifier(id);
            range(frame, 0, MAX_DURATION_FRAMES - 1, "puppet command frame");
            require(kind != null && target != null && condition != null && motion != null, "Puppet intent fields are required");
            coordinate(x, "puppet destination x"); coordinate(y, "puppet destination y");
            finiteRange(urgency, 0, 1, "puppet urgency");
            range(timeoutFrames, 0, MAX_DURATION_FRAMES, "timeoutFrames");
            boolean movement = kind == PuppetCommandKind.MOVE_TO || kind == PuppetCommandKind.FLY_TO
                    || kind == PuppetCommandKind.GLIDE_TO;
            if (kind == PuppetCommandKind.SPINDASH) require(urgency > 0 && holdFrames == SPINDASH_CHARGE_FRAMES, "Spindash uses the fixed 24-tick charge");
            else if (movement) require(urgency > 0 && holdFrames == 0, "Movement requires urgency and no hold");
            else {
                require(x == 0 && y == 0 && urgency == 0, "Only movement has a destination and urgency");
                if (kind == PuppetCommandKind.JUMP) range(holdFrames, 1, 60, "jump hold frames");
                else if (kind == PuppetCommandKind.HOVER || kind == PuppetCommandKind.CROUCH
                        || kind == PuppetCommandKind.LOOK_UP) range(holdFrames, 1, 3600, "intent hold frames");
                else require(holdFrames == 0, "This intent has no hold");
            }
            require(kind == PuppetCommandKind.WAIT ? condition != WaitCondition.NONE && timeoutFrames > 0
                    : condition == WaitCondition.NONE, "WAIT requires a condition and finite timeout");
            require(condition != WaitCondition.TARGET_AVAILABLE || target.layoutIndex() != -1,
                    "TARGET_AVAILABLE requires a placed-object identity");
            require(movement || kind == PuppetCommandKind.WAIT || target.layoutIndex() == -1,
                    "Object targets apply to movement or waits");
        }
    }

    /** Stable ROM placement identity, with expected type/subtype and an authored local offset. */
    @ModApi
    public record ObjectTarget(int layoutIndex, int objectId, int subtype, double offsetX, double offsetY) {
        public ObjectTarget {
            range(layoutIndex, -1, 65535, "placement layoutIndex");
            range(objectId, 0, 255, "placement objectId"); range(subtype, 0, 255, "placement subtype");
            coordinate(offsetX, "target offsetX"); coordinate(offsetY, "target offsetY");
            require(layoutIndex != -1 || objectId == 0 && subtype == 0 && offsetX == 0 && offsetY == 0,
                    "Absent target must use the canonical empty identity");
        }
        public static ObjectTarget none() { return new ObjectTarget(-1, 0, 0, 0, 0); }
    }
    @ModApi public enum WaitCondition { NONE, ARRIVED, ON_OBJECT, GROUNDED, AIRBORNE, HURT, RECOVERED, RING_COLLECTED, TARGET_AVAILABLE }
    @ModApi public enum PuppetCommandKind { MOVE_TO, STOP, JUMP, WAIT, FLY_TO, HOVER, GLIDE_TO, RELEASE, CROUCH, LOOK_UP, ROLL, SPINDASH, FACE_LEFT, FACE_RIGHT }

    /** Following uses real actor positions; camera keys add authored framing-centre offsets. */
    @ModApi
    public record CameraFollow(List<String> actors, double deadZoneX, double deadZoneY,
                               double lookAhead, double smoothing, double offsetX, double offsetY) {
        public CameraFollow {
            actors = boundedCopy(actors, 32, "camera follow actors");
            Set<String> unique = new HashSet<>();
            for (String actor : actors) require(unique.add(identifier(actor)), "Duplicate follow actor");
            finiteRange(deadZoneX, 0, 960, "deadZoneX"); finiteRange(deadZoneY, 0, 540, "deadZoneY");
            finiteRange(lookAhead, 0, 60, "lookAhead"); finiteRange(smoothing, 0.001, 1, "smoothing");
            coordinate(offsetX, "follow offsetX"); coordinate(offsetY, "follow offsetY");
        }
        public static CameraFollow none() { return new CameraFollow(List.of(), 0, 0, 0, 1, 0, 0); }
    }

    /**
     * Camera position is the top-left stage coordinate; one is native scale.
     * All worlds interpolate framing centres and zoom with the chosen easing.
     */
    @ModApi
    public record CameraKey(int frame, double x, double y, double zoom, Interpolation interpolation, CameraFollow follow) {
        public CameraKey(int frame, double x, double y, double zoom, Interpolation interpolation) {
            this(frame, x, y, zoom, interpolation, CameraFollow.none());
        }
        public CameraKey {
            require(follow != null, "Camera follow is required");
            range(frame, 0, MAX_DURATION_FRAMES - 1, "camera frame");
            coordinate(x, "camera x");
            coordinate(y, "camera y");
            finiteRange(zoom, .05, 64, "zoom");
            require(interpolation != null, "interpolation is required");
        }
    }

    @ModApi
    public enum Interpolation { HOLD, LINEAR, SMOOTH }

    /** A deterministic presentation-only shake on [startFrame,endFrame), with frequency in Hz. */
    @ModApi
    public record CameraEffect(String id, int startFrame, int endFrame, double amplitudeX,
            double amplitudeY, double frequency, int seed) {
        public CameraEffect {
            id = identifier(id);
            range(startFrame, 0, MAX_DURATION_FRAMES - 1, "camera effect start");
            range(endFrame, startFrame + 1, MAX_DURATION_FRAMES, "camera effect end");
            finiteRange(amplitudeX, 0, 4096, "camera effect amplitudeX");
            finiteRange(amplitudeY, 0, 4096, "camera effect amplitudeY");
            finiteRange(frequency, Double.MIN_VALUE, 30, "camera effect frequency");
        }
    }

    @ModApi
    public enum Transition { CUT, FADE_BLACK, FADE_WHITE, WIPE_LEFT, WIPE_RIGHT }

    /**
     * Incoming set starts at frame. Non-cut coverage rises for floor(duration/2) frames
     * before that boundary, then falls for ceil(duration/2) frames after it.
     * Every actor and camera needs an incoming key at frame; interpolation never crosses it.
     */
    @ModApi
    public record SceneChange(String id, int frame, SetSource set, Transition transition, int durationFrames) {
        public SceneChange {
            id = identifier(id);
            range(frame, 1, MAX_DURATION_FRAMES - 1, "scene change frame");
            require(set != null, "scene change set is required");
            require(transition != null, "transition is required");
            if (transition == Transition.CUT) require(durationFrames == 0, "Cuts have zero transition duration");
            else range(durationFrames, 2, 600, "transition duration");
        }
    }

    /** Captions use screen coordinates and a half-open [startFrame,endFrame) interval. */
    @ModApi
    public record Caption(String id, int startFrame, int endFrame, String text, int x, int y, int argb) {
        public Caption {
            id = identifier(id);
            range(startFrame, 0, MAX_DURATION_FRAMES - 1, "caption start");
            range(endFrame, startFrame + 1, MAX_DURATION_FRAMES, "caption end");
            require(text != null && text.length() <= 4096, "Caption text exceeds 4096 characters or is missing");
            coordinate(x, "caption x");
            coordinate(y, "caption y");
        }
    }

    /** Silent grapheme reveal cadence. Zero frames per grapheme reveals the whole text immediately. */
    @ModApi
    public record DialogueTyper(String id, int framesPerGrapheme, int punctuationPauseFrames, int spacePauseFrames) {
        public DialogueTyper {
            id = identifier(id);
            range(framesPerGrapheme, 0, 30, "framesPerGrapheme");
            range(punctuationPauseFrames, 0, 120, "punctuationPauseFrames");
            range(spacePauseFrames, 0, 120, "spacePauseFrames");
        }
    }

    /** Portable panel appearance; decoration PNGs are optional creator assets, never ROM fallbacks. */
    @ModApi
    public record DialogueSkin(String id, int textArgb, int nameArgb, int panelArgb, int borderArgb,
            int padding, String decorationAsset, int decorationBorder, boolean pointer) {
        public DialogueSkin {
            id = identifier(id);
            range(padding, 4, 32, "dialogue padding");
            decorationAsset = pngPath(decorationAsset, "decorationAsset");
            range(decorationBorder, 0, 64, "decorationBorder");
        }
    }

    /** A screen-space panel on [startFrame,endFrame), with optional elapsed reveal offsets per grapheme. */
    @ModApi
    public record DialogueCue(String id, int startFrame, int endFrame, String speakerActor, String speakerName,
            String text, String skinId, String typerId, int x, int y, int width, int height,
            String portraitAsset, List<Integer> revealFrames) {
        public DialogueCue {
            id = identifier(id);
            range(startFrame, 0, MAX_DURATION_FRAMES - 1, "dialogue start");
            range(endFrame, startFrame + 1, MAX_DURATION_FRAMES, "dialogue end");
            speakerActor = optionalIdentifier(speakerActor);
            require(speakerName != null, "speakerName is required");
            if (!speakerName.isEmpty()) speakerName = label(speakerName, "speakerName");
            require(!speakerActor.isEmpty() || !speakerName.isEmpty(), "Offscreen dialogue needs an explicit speaker name");
            require(text != null && !text.isBlank() && text.length() <= 4096, "Dialogue text must contain 1-4096 characters");
            require(text.chars().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'),
                    "Dialogue text contains unsupported control characters");
            skinId = optionalIdentifier(skinId);
            typerId = optionalIdentifier(typerId);
            range(x, 0, 1920, "dialogue x");
            range(y, 0, 1080, "dialogue y");
            range(width, 80, 1920, "dialogue width");
            range(height, 36, 1080, "dialogue height");
            portraitAsset = pngPath(portraitAsset, "portraitAsset");
            revealFrames = boundedCopy(revealFrames, 4096, "revealFrames");
            if (!revealFrames.isEmpty()) {
                require(revealFrames.size() == StageplayDialogue.graphemeCount(text),
                        "revealFrames needs one offset per dialogue grapheme: " + id);
                int previous = -1;
                for (int frame : revealFrames) {
                    require(frame >= 0 && frame >= previous && frame < endFrame - startFrame,
                            "Reveal offsets must ascend within dialogue duration: " + id);
                    previous = frame;
                }
            }
        }
    }

    /** Audio is dispatched by forward playback, never by pure timeline sampling. */
    @ModApi
    public record AudioCue(String id, int frame, AudioKind kind, String gameId, int soundId,
            String asset, double gain) {
        public AudioCue {
            id = identifier(id);
            range(frame, 0, MAX_DURATION_FRAMES - 1, "audio frame");
            require(kind != null, "audio kind is required");
            gameId = game(gameId);
            range(soundId, -1, 65535, "soundId");
            finiteRange(gain, 0, 1, "gain");
            asset = relativePath(asset, "asset", kind != AudioKind.VOICE);
            if (kind == AudioKind.VOICE) {
                String extension = asset.toLowerCase(Locale.ROOT);
                require(extension.endsWith(".wav") || extension.endsWith(".ogg"),
                        "Voice assets must be WAV or OGG files");
            } else {
                require(asset.isEmpty(), "Only voice cues reference creator assets");
            }
            if (kind == AudioKind.ROM_MUSIC || kind == AudioKind.ROM_SFX) {
                require(soundId >= 0, "ROM audio cues require a soundId");
                require(gain == 1, "ROM cue gain must be 1; gain is available for voice clips");
            }
        }
    }

    @ModApi
    public enum AudioKind { ROM_MUSIC, ROM_SFX, VOICE, MUSIC_STOP }

    private static String game(String value) {
        require("s1".equals(value) || "s2".equals(value) || "s3k".equals(value), "Unknown gameId: " + value);
        return value;
    }

    private static String identifier(String value) {
        require(value != null && value.length() <= 64 && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"),
                "Ids must contain 1-64 letters, digits, dots, underscores or hyphens");
        return value;
    }

    private static String optionalIdentifier(String value) {
        require(value != null, "Optional id must be empty or an identifier");
        return value.isEmpty() ? value : identifier(value);
    }

    private static String pngPath(String value, String field) {
        value = relativePath(value, field, true);
        require(value.isEmpty() || value.toLowerCase(Locale.ROOT).endsWith(".png"), field + " must reference a PNG file");
        return value;
    }

    private static String label(String value, String field) {
        require(value != null && !value.isBlank() && value.length() <= 256
                && value.chars().noneMatch(Character::isISOControl), field + " must contain 1-256 printable characters");
        return value;
    }

    private static String relativePath(String value, String field, boolean allowEmpty) {
        require(value != null && value.length() <= 1024, field + " is missing or too long");
        if (allowEmpty && value.isEmpty()) return value;
        require(!value.isEmpty() && !value.startsWith("/") && !value.contains("\\") && !value.contains(":")
                && value.chars().noneMatch(Character::isISOControl), field + " must be a safe portable relative path");
        for (String component : value.split("/", -1)) {
            require(!component.isEmpty() && !component.equals(".") && !component.equals("..")
                    && !component.endsWith(".") && !component.endsWith(" "),
                    field + " contains an unsafe path component");
        }
        return value;
    }

    private static <T> List<T> boundedCopy(List<T> values, int maximum, String field) {
        require(values != null && values.size() <= maximum, field + " is missing or exceeds " + maximum);
        require(values.stream().noneMatch(value -> value == null), field + " contains null");
        return List.copyOf(values);
    }

    private static void coordinate(double value, String field) {
        finiteRange(value, -MAX_COORDINATE, MAX_COORDINATE, field);
    }

    private static void finiteRange(double value, double minimum, double maximum, String field) {
        require(Double.isFinite(value) && value >= minimum && value <= maximum, field + " is out of range");
    }

    private static void range(int value, int minimum, int maximum, String field) {
        require(value >= minimum && value <= maximum, field + " is out of range");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
