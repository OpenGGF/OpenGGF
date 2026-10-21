package com.openggf.stageplay;

import com.openggf.game.ModApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.stageplay.StageplayDocument.*;

/** Pure random-access visual evaluation. Sampling never emits sounds or story outcomes. */
@ModApi
public final class StageplayTimeline {
    private static final TransitionPose NO_TRANSITION = new TransitionPose(Transition.CUT, 0, true);
    private StageplayTimeline() { }

    public static Frame sample(StageplayDocument document, int frame) {
        Objects.requireNonNull(document, "document");
        if (frame < 0 || frame >= document.durationFrames()) {
            throw new IllegalArgumentException("Frame is outside the scene duration: " + frame);
        }
        SetSource set = document.set();
        int sceneStart = 0, nextScene = -1;
        TransitionPose transition = NO_TRANSITION;
        for (SceneChange change : document.sceneChanges()) {
            if (change.frame() <= frame) { set = change.set(); sceneStart = change.frame(); }
            else if (nextScene < 0) nextScene = change.frame();
            int before = change.durationFrames() / 2, after = (change.durationFrames() + 1) / 2;
            if (change.transition() != Transition.CUT && frame >= change.frame() - before
                    && frame < change.frame() + after) {
                boolean revealing = frame >= change.frame();
                double coverage = revealing ? 1 - (double) (frame - change.frame()) / after
                        : (double) (frame - change.frame() + before) / before;
                transition = new TransitionPose(change.transition(), coverage, revealing);
            }
        }
        List<ActorPose> actors = new ArrayList<>(document.actors().size());
        for (Actor actor : document.actors()) {
            List<ActorKey> keys = actor.keys();
            int index = actorKeyAt(keys, frame);
            ActorKey key = keys.get(index);
            ActorKey next = keys.get(Math.min(index + 1, keys.size() - 1));
            double weight = next.frame() == nextScene ? 0 : weight(key.interpolation(), key.frame(), next.frame(), frame);
            // Movement, facing, visibility and scale edits preserve the running animation.
            // A changed animation or explicit pose starts a new performance segment.
            int phaseStart = index;
            while (phaseStart > 0) {
                ActorKey previous = keys.get(phaseStart - 1);
                if (previous.frame() < sceneStart || previous.animation() != key.animation() || previous.pose() != key.pose()) break;
                phaseStart--;
            }
            actors.add(new ActorPose(actor.id(), actor.gameId(), actor.character(),
                    lerp(key.x(), next.x(), weight), lerp(key.y(), next.y(), weight), key.animation(),
                    frame - keys.get(phaseStart).frame(), key.pose(), key.flipX(), key.visible(),
                    lerp(key.scale(), next.scale(), weight)));
        }
        List<CameraKey> cameraKeys = document.camera();
        int cameraIndex = cameraKeyAt(cameraKeys, frame);
        CameraKey camera = cameraKeys.get(cameraIndex);
        CameraKey nextCamera = cameraKeys.get(Math.min(cameraIndex + 1, cameraKeys.size() - 1));
        double weight = nextCamera.frame() == nextScene ? 0 : weight(camera.interpolation(), camera.frame(), nextCamera.frame(), frame);
        CameraPose baseCamera = new CameraPose(lerp(camera.x(), nextCamera.x(), weight),
                lerp(camera.y(), nextCamera.y(), weight), camera.zoom());
        double shakeX = 0, shakeY = 0;
        for (CameraEffect effect : document.cameraEffects()) {
            if (frame < effect.startFrame() || frame >= effect.endFrame()) continue;
            double angle = 2 * StrictMath.PI * (frame - effect.startFrame()) * effect.frequency() / FRAMES_PER_SECOND;
            shakeX += effect.amplitudeX() * StrictMath.sin(angle + phase(effect.seed()));
            shakeY += effect.amplitudeY() * StrictMath.sin(angle + phase(effect.seed() ^ 0x6a09e667));
        }
        CameraPose cameraPose = new CameraPose(baseCamera.x() + shakeX, baseCamera.y() + shakeY, baseCamera.zoom());
        List<Caption> captions = document.captions().stream()
                .filter(caption -> caption.startFrame() <= frame && frame < caption.endFrame()).toList();
        List<DialoguePose> dialogue = document.dialogue().stream()
                .filter(cue -> cue.startFrame() <= frame && frame < cue.endFrame())
                .map(cue -> new DialoguePose(cue, StageplayDialogue.speakerName(document, cue),
                        StageplayDialogue.visibleText(document, cue, frame - cue.startFrame()))).toList();
        List<PuppetSample> puppets = new ArrayList<>();
        for (PuppetTrack track : document.puppets()) {
            String command = "";
            for (PuppetCommand cue : track.commands()) {
                if (cue.frame() > frame) break;
                if (cue.frame() >= sceneStart && cue.kind() != PuppetCommandKind.JUMP) command = cue.id();
            }
            puppets.add(new PuppetSample(track.actorId(), command, PuppetStatus.UNRESOLVED,
                    0, 0, 0, false, 0, track.collisionPath()));
        }
        return new Frame(frame, actors, cameraPose, captions, set, transition, baseCamera, dialogue, puppets);
    }

    @ModApi
    public record Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions,
                        SetSource set, TransitionPose transition, CameraPose baseCamera, List<DialoguePose> dialogue,
                        List<PuppetSample> puppets, WorldSample world) {
        public Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions,
                     SetSource set, TransitionPose transition, CameraPose baseCamera, List<DialoguePose> dialogue,
                     List<PuppetSample> puppets) {
            this(frame, actors, camera, captions, set, transition, baseCamera, dialogue, puppets, null);
        }
        public Frame {
            actors = List.copyOf(actors);
            Objects.requireNonNull(camera, "camera");
            captions = List.copyOf(captions);
            Objects.requireNonNull(transition, "transition");
            Objects.requireNonNull(baseCamera, "baseCamera");
            dialogue = List.copyOf(dialogue);
            puppets = List.copyOf(puppets);
        }

        /** Source-compatible version-three frame, without physics puppet observations. */
        public Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions,
                     SetSource set, TransitionPose transition, CameraPose baseCamera, List<DialoguePose> dialogue) {
            this(frame, actors, camera, captions, set, transition, baseCamera, dialogue, List.of());
        }

        /** Source-compatible version-two frame, without dialogue. */
        public Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions,
                     SetSource set, TransitionPose transition, CameraPose baseCamera) {
            this(frame, actors, camera, captions, set, transition, baseCamera, List.of());
        }

        /** Legacy manually sampled frames use the renderer's document set. */
        public Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions) {
            this(frame, actors, camera, captions, null, NO_TRANSITION, camera);
        }
    }

    /**
     * Screen coverage in [0,1]. Wipe-left covers from the right before the switch and
     * reveals toward the left after it; wipe-right mirrors those masks. Fades cover uniformly.
     */
    @ModApi
    public record TransitionPose(Transition transition, double coverage, boolean revealing) {
        public TransitionPose {
            Objects.requireNonNull(transition, "transition");
            if (!Double.isFinite(coverage) || coverage < 0 || coverage > 1
                    || (transition == Transition.CUT && coverage != 0)) {
                throw new IllegalArgumentException("Invalid transition coverage");
            }
        }
    }

    @ModApi
    public record ActorPose(String actorId, String gameId, String character, double x, double y,
            int animation, int animationTick, int pose, boolean flipX, boolean visible, double scale, boolean flipY) {
        public ActorPose(String actorId, String gameId, String character, double x, double y,
                         int animation, int animationTick, int pose, boolean flipX, boolean visible, double scale) {
            this(actorId, gameId, character, x, y, animation, animationTick, pose, flipX, visible, scale, false);
        }
    }

    /** Actual native velocities use stage pixels per 60 Hz tick. UNRESOLVED values are not simulation results. */
    @ModApi
    public record PuppetSample(String actorId, String commandId, PuppetStatus status,
                               double velocityX, double velocityY, double groundSpeed,
                               boolean airborne, int angle, int collisionPath, PuppetAccessory accessory) {
        public PuppetSample {
            Objects.requireNonNull(actorId); Objects.requireNonNull(commandId); Objects.requireNonNull(status);
        }
        public PuppetSample(String actorId, String commandId, PuppetStatus status,
                            double velocityX, double velocityY, double groundSpeed,
                            boolean airborne, int angle, int collisionPath) {
            this(actorId, commandId, status, velocityX, velocityY, groundSpeed, airborne, angle, collisionPath, null);
        }
    }

    /** Stored native appendage mapping and flips, independent of draw order and repeated rendering. */
    @ModApi
    public record PuppetAccessory(int mappingFrame, boolean flipX, boolean flipY) { }

    @ModApi
    public enum PuppetStatus { UNRESOLVED, INACTIVE, IDLE, MOVING, STOPPING, ARRIVED, BLOCKED, WAITING, SATISFIED, TIMED_OUT, TARGET_UNAVAILABLE, DEAD, PERFORMING }

    /** Native observations, sampled after the one shared world tick. No comparison data drives simulation. */
    @ModApi public record WorldSample(int levelFrame, int vIntRunCount, List<LiveActor> actors,
                                      List<LiveObject> objects, List<LiveRing> rings, List<LiveEvent> events) {
        public WorldSample { actors = List.copyOf(actors); objects = List.copyOf(objects); rings = List.copyOf(rings); events = List.copyOf(events); }
    }
    @ModApi public record LiveEvent(int frame, String actorId, String commandId, String kind, String detail) { }
    @ModApi public record LiveActor(String actorId, int rings, int ringDelta, boolean hurt, boolean dead,
                                   boolean onObject, int ridingLayoutIndex, String ability, int abilityState,
                                   int abilityTimer, int animation, int mappingFrame, String targetStatus) { }
    @ModApi public record LiveObject(int layoutIndex, int objectId, int subtype, int x, int y, String type) { }
    @ModApi public record LiveRing(int placementId, int x, int y, boolean collected, int sparkleStartFrame) {
        public LiveRing(int x, int y, boolean collected, int sparkleStartFrame) { this(-1, x, y, collected, sparkleStartFrame); }
    }

    @ModApi
    public record CameraPose(double x, double y, double zoom) { }

    /** Active cue and a silent, grapheme-complete prefix; the speaker name is immediately available. */
    @ModApi
    public record DialoguePose(DialogueCue cue, String speakerName, String visibleText) {
        public DialoguePose {
            Objects.requireNonNull(cue, "cue");
            Objects.requireNonNull(speakerName, "speakerName");
            Objects.requireNonNull(visibleText, "visibleText");
        }
    }

    private static int actorKeyAt(List<ActorKey> keys, int frame) {
        int low = 0, high = keys.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (keys.get(middle).frame() <= frame) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static int cameraKeyAt(List<CameraKey> keys, int frame) {
        int low = 0, high = keys.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (keys.get(middle).frame() <= frame) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static double weight(Interpolation interpolation, int start, int end, int frame) {
        if (start == end || interpolation == Interpolation.HOLD) return 0;
        double progress = (double) (frame - start) / (end - start);
        return interpolation == Interpolation.SMOOTH ? progress * progress * (3 - 2 * progress) : progress;
    }

    private static double lerp(double start, double end, double weight) {
        return start + (end - start) * weight;
    }

    private static double phase(int seed) {
        // Integer mixing gives each axis a stable phase without mutable random state.
        int mixed = seed;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        mixed ^= mixed >>> 16;
        return Integer.toUnsignedLong(mixed) * (2 * StrictMath.PI / 0x1_0000_0000L);
    }
}
