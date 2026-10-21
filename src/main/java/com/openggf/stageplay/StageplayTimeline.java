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
                lerp(camera.y(), nextCamera.y(), weight), lerp(camera.zoom(), nextCamera.zoom(), weight));
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
        return new Frame(frame, actors, cameraPose, captions, set, transition, baseCamera);
    }

    @ModApi
    public record Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions,
                        SetSource set, TransitionPose transition, CameraPose baseCamera) {
        public Frame {
            actors = List.copyOf(actors);
            Objects.requireNonNull(camera, "camera");
            captions = List.copyOf(captions);
            Objects.requireNonNull(transition, "transition");
            Objects.requireNonNull(baseCamera, "baseCamera");
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
            int animation, int animationTick, int pose, boolean flipX, boolean visible, double scale) { }

    @ModApi
    public record CameraPose(double x, double y, double zoom) { }

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
