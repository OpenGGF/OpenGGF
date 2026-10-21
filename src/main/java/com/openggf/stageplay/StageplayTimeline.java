package com.openggf.stageplay;

import com.openggf.game.ModApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.stageplay.StageplayDocument.*;

/** Pure random-access visual evaluation. Sampling never emits sounds or story outcomes. */
@ModApi
public final class StageplayTimeline {
    private StageplayTimeline() { }

    public static Frame sample(StageplayDocument document, int frame) {
        Objects.requireNonNull(document, "document");
        if (frame < 0 || frame >= document.durationFrames()) {
            throw new IllegalArgumentException("Frame is outside the scene duration: " + frame);
        }
        List<ActorPose> actors = new ArrayList<>(document.actors().size());
        for (Actor actor : document.actors()) {
            List<ActorKey> keys = actor.keys();
            int index = actorKeyAt(keys, frame);
            ActorKey key = keys.get(index);
            ActorKey next = keys.get(Math.min(index + 1, keys.size() - 1));
            double weight = weight(key.interpolation(), key.frame(), next.frame(), frame);
            // Movement, facing, visibility and scale edits preserve the running animation.
            // A changed animation or explicit pose starts a new performance segment.
            int phaseStart = index;
            while (phaseStart > 0) {
                ActorKey previous = keys.get(phaseStart - 1);
                if (previous.animation() != key.animation() || previous.pose() != key.pose()) break;
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
        double weight = weight(camera.interpolation(), camera.frame(), nextCamera.frame(), frame);
        CameraPose cameraPose = new CameraPose(lerp(camera.x(), nextCamera.x(), weight),
                lerp(camera.y(), nextCamera.y(), weight), lerp(camera.zoom(), nextCamera.zoom(), weight));
        List<Caption> captions = document.captions().stream()
                .filter(caption -> caption.startFrame() <= frame && frame < caption.endFrame()).toList();
        return new Frame(frame, actors, cameraPose, captions);
    }

    @ModApi
    public record Frame(int frame, List<ActorPose> actors, CameraPose camera, List<Caption> captions) {
        public Frame {
            actors = List.copyOf(actors);
            Objects.requireNonNull(camera, "camera");
            captions = List.copyOf(captions);
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
}
