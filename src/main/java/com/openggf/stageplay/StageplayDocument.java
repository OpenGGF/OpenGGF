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
        List<PuppetTrack> puppets) {
    public static final int VERSION = 4;
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
        require(!camera.isEmpty(), "camera must start at frame zero");
        Set<String> ids = new HashSet<>();
        int totalKeys = camera.size();
        for (Actor actor : actors) {
            require(ids.add(actor.id()), "Duplicate id: " + actor.id());
            totalKeys += actor.keys().size();
            require(actor.keys().getLast().frame() < durationFrames, "Actor key exceeds scene duration");
        }
        require(totalKeys <= MAX_KEYS, "Scene has too many keys");
        int previousFrame = -1;
        for (CameraKey key : camera) {
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
                else require(key.x() == anchor.x() && key.y() == anchor.y() && key.flipX() == anchor.flipX(),
                        "Physics puppet position/facing keys are only allowed at shot boundaries: " + actor.id());
            }
            for (PuppetCommand command : puppet.commands()) {
                require(ids.add(command.id()), "Duplicate id: " + command.id());
                require(command.frame() < durationFrames, "Puppet command exceeds scene duration");
                if (command.kind() == PuppetCommandKind.JUMP) {
                    int end = command.frame() + command.holdFrames();
                    require(end <= durationFrames, "Jump hold exceeds scene duration");
                    for (SceneChange change : sceneChanges)
                        require(change.frame() <= command.frame() || change.frame() >= end,
                                "Jump hold crosses a scene boundary");
                }
            }
            commandCount += puppet.commands().size();
        }
        require(commandCount <= 4096, "Scene has too many puppet commands");
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

    /** Source-compatible version-one constructor. New authored documents use the current version. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio) {
        this(migratedVersion(version), name, durationFrames, width, height, set, actors, camera, captions, audio,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Source-compatible version-two constructor, retaining its camera and scene-change tracks. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio,
            List<CameraEffect> cameraEffects, List<SceneChange> sceneChanges) {
        this(migratedVersion(version), name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, List.of(), List.of(), List.of(), List.of());
    }

    /** Source-compatible version-three constructor: existing choreography remains manual. */
    public StageplayDocument(int version, String name, int durationFrames, int width, int height,
            SetSource set, List<Actor> actors, List<CameraKey> camera, List<Caption> captions, List<AudioCue> audio,
            List<CameraEffect> cameraEffects, List<SceneChange> sceneChanges,
            List<DialogueTyper> dialogueTypers, List<DialogueSkin> dialogueSkins, List<DialogueCue> dialogue) {
        this(migratedVersion(version), name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, List.of());
    }

    private static int migratedVersion(int version) {
        require(version >= 1 && version <= VERSION, "Unsupported Stageplay version: " + version);
        return VERSION;
    }

    /** A ten-second scene with an empty cast and a stationary camera. */
    public static StageplayDocument empty(String gameId, int zone, int act, double x, double y,
            int width, int height) {
        return new StageplayDocument(VERSION, "Untitled", 600, width, height,
                new SetSource(gameId, zone, act, "", ""), List.of(),
                List.of(new CameraKey(0, x, y, 1, Interpolation.HOLD)), List.of(), List.of());
    }

    public StageplayDocument withName(String value) {
        return new StageplayDocument(version, value, durationFrames, width, height, set, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    /** Shortening a scene rejects remaining keys/cues beyond its new end. */
    public StageplayDocument withDurationFrames(int value) {
        return new StageplayDocument(version, name, value, width, height, set, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withSize(int newWidth, int newHeight) {
        return new StageplayDocument(version, name, durationFrames, newWidth, newHeight, set, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withSet(SetSource value) {
        return new StageplayDocument(version, name, durationFrames, width, height, value, actors, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withActors(List<Actor> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, value, camera, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withCamera(List<CameraKey> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, value, captions, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withCaptions(List<Caption> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, value, audio, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withAudio(List<AudioCue> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, value, cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withCameraEffects(List<CameraEffect> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio, value, sceneChanges, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    /** Changes and their required actor/camera anchors must be published as one valid document. */
    public StageplayDocument withSceneChanges(List<SceneChange> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio, cameraEffects, value, dialogueTypers, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withDialogueTypers(List<DialogueTyper> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, value, dialogueSkins, dialogue, puppets);
    }

    public StageplayDocument withDialogueSkins(List<DialogueSkin> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, value, dialogue, puppets);
    }

    public StageplayDocument withDialogue(List<DialogueCue> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, value, puppets);
    }

    public StageplayDocument withPuppets(List<PuppetTrack> value) {
        return new StageplayDocument(version, name, durationFrames, width, height, set, actors, camera, captions, audio,
                cameraEffects, sceneChanges, dialogueTypers, dialogueSkins, dialogue, value);
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

    /** Interpolation applies from this key to the next; discrete properties change at the key. */
    @ModApi
    public record ActorKey(int frame, double x, double y, int animation, int pose, boolean flipX,
            boolean visible, double scale, Interpolation interpolation) {
        public ActorKey {
            range(frame, 0, MAX_DURATION_FRAMES - 1, "actor frame");
            coordinate(x, "actor x");
            coordinate(y, "actor y");
            range(animation, 0, 65535, "animation");
            range(pose, -1, 65535, "pose");
            finiteRange(scale, .01, 64, "scale");
            require(interpolation != null, "interpolation is required");
        }
    }

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

    /** Goals persist until the next movement command or shot. Jump is an independent native button hold. */
    @ModApi
    public record PuppetCommand(String id, int frame, PuppetCommandKind kind, double x, double y,
                                double urgency, int holdFrames) {
        public PuppetCommand {
            id = identifier(id);
            range(frame, 0, MAX_DURATION_FRAMES - 1, "puppet command frame");
            require(kind != null, "puppet command kind is required");
            coordinate(x, "puppet destination x"); coordinate(y, "puppet destination y");
            finiteRange(urgency, 0, 1, "puppet urgency");
            if (kind == PuppetCommandKind.MOVE_TO) {
                require(urgency > 0 && holdFrames == 0, "Move requires urgency in (0,1] and no button hold");
            } else {
                require(x == 0 && y == 0 && urgency == 0, "Only move commands have a destination and urgency");
                if (kind == PuppetCommandKind.JUMP) range(holdFrames, 1, 60, "jump hold frames");
                else require(holdFrames == 0, "Stop has no button hold");
            }
        }
    }

    @ModApi
    public enum PuppetCommandKind { MOVE_TO, STOP, JUMP }

    /**
     * Camera position is the top-left stage coordinate; interpolation applies only to x/y.
     * Zoom is held until the next key to preserve nearest-neighbour pixel stability; one is native scale.
     */
    @ModApi
    public record CameraKey(int frame, double x, double y, double zoom, Interpolation interpolation) {
        public CameraKey {
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
