package com.openggf.stageplay.runtime;

import com.openggf.mods.scene.*;
import com.openggf.mods.scene.art.AnimationSampling;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import java.util.Objects;

/** Pure presentation of an evaluated frame; drawing never advances a transport or fires audio. */
public final class StageplayRenderer {
    private final StageplayResources resources;
    public StageplayRenderer(StageplayResources resources) { this.resources = Objects.requireNonNull(resources); }

    /** Mapping frame shared by staged rendering and optional native player direction. */
    public int mappingFrame(StageplayTimeline.ActorPose actor) {
        var set = resources.character(actor.gameId(), actor.character());
        if (actor.pose() >= 0) return actor.pose();
        int[] frames = set.animationFrames(actor.animation());
        if (frames.length == 0) throw new IllegalArgumentException("Actor animation is unavailable: " + actor.actorId());
        int delay = AnimationSampling.Timing.rom(6).delay(set, actor.animation());
        int frame = frames[Math.floorMod(actor.animationTick() / delay, frames.length)];
        return frame >= 0xF0 ? frames[0] : frame;
    }

    public void draw(StageplayDocument document, StageplayTimeline.Frame frame, SceneCanvas canvas) {
        if (frame.puppets().stream().anyMatch(value -> value.status() == StageplayTimeline.PuppetStatus.UNRESOLVED)
                || document.puppets().stream().anyMatch(track -> frame.puppets().stream()
                    .noneMatch(value -> value.actorId().equals(track.actorId()))))
            throw new IllegalArgumentException("Resolve physics puppet frames before rendering");
        if (frame.world() != null) {
            var image = resources.livePreviewImage();
            if (image == null) throw new IllegalStateException("The offline host must draw the native world before its overlay");
            canvas.draw(image, 0, 0, SceneDraw.plain());
            drawLiveOverlay(document, frame, canvas);
            return;
        }
        resources.prepareDialogue(document);
        var camera = frame.camera(); var kit = resources.set(frame.set() == null ? document.set() : frame.set());
        float zoom = (float) camera.zoom();
        canvas.clear(kit.palette()[0]);
        canvas.clip(0, 0, document.width(), document.height());
        backdrop(canvas, kit.backdrop(), camera.x(), camera.y(), zoom, frame.frame());
        int size = kit.blockSize();
        int left = Math.max(0, (int) Math.floor(camera.x() / size));
        int top = Math.max(0, (int) Math.floor(camera.y() / size));
        int right = Math.min(kit.columns(), (int) Math.ceil((camera.x() + document.width() / zoom) / size));
        int bottom = Math.min(kit.rows(), (int) Math.ceil((camera.y() + document.height() / zoom) / size));
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            int id = kit.block(x, y);
            if (id >= 0 && id < kit.blockCount()) canvas.draw(kit.blockImage(id), (float) ((x * size - camera.x()) * zoom),
                    (float) ((y * size - camera.y()) * zoom), SceneDraw.plain().withScale(zoom));
        }
        drawPerformersAndOverlay(document, frame, canvas, zoom, false);
        canvas.unclip();
    }

    /** Native world already drew terrain/objects/physical cast through the priority renderer. */
    public void drawLiveOverlay(StageplayDocument document, StageplayTimeline.Frame frame, SceneCanvas canvas) {
        resources.prepareDialogue(document);
        canvas.clip(0, 0, document.width(), document.height());
        drawPerformersAndOverlay(document, frame, canvas, 1, true);
        canvas.unclip();
    }

    private void drawPerformersAndOverlay(StageplayDocument document, StageplayTimeline.Frame frame,
                                         SceneCanvas canvas, float zoom, boolean nativeWorld) {
        var camera = frame.camera();
        for (var actor : frame.actors()) {
            if (!actor.visible() || nativeWorld && document.puppets().stream().anyMatch(p -> p.actorId().equals(actor.actorId()))) continue;
            var set = resources.character(actor.gameId(), actor.character());
            var sprite = set.frame(mappingFrame(actor));
            if (sprite == null) throw new IllegalArgumentException("Actor animation is unavailable: " + actor.actorId());
            float x = (float) ((actor.x() - camera.x()) * zoom), y = (float) ((actor.y() - camera.y()) * zoom);
            var style = SceneDraw.plain().withScale((float) actor.scale() * zoom).withFlipX(actor.flipX()).withFlipY(actor.flipY());
            var accessory = accessory(document, frame, actor);
            if (accessory != null) {
                canvas.draw(accessory.sprite(), x, y,
                        style.withFlipX(accessory.flipX()).withFlipY(accessory.flipY()));
            }
            canvas.draw(sprite, x, y, style);
        }
        for (var caption : frame.captions()) {
            int y = caption.y();
            for (String line : caption.text().split("\n", -1)) {
                canvas.text(line, caption.x(), y, caption.argb()); y += 12;
            }
        }
        StageplayDialogueRenderer.draw(document, frame, canvas, resources);
        transition(canvas, frame.transition(), document.width(), document.height());
    }

    private SceneAccessoryPose accessory(StageplayDocument document, StageplayTimeline.Frame frame,
                                          StageplayTimeline.ActorPose actor) {
        var rom = resources.rom(actor.gameId());
        var nativePose = frame.puppets().stream().filter(value -> value.actorId().equals(actor.actorId())).findFirst();
        if (nativePose.isPresent()) {
            var tail = nativePose.get().accessory();
            if (tail == null) return null;
            var art = rom.characterAccessory(actor.character());
            if (art == null || tail.mappingFrame() < 0 || tail.mappingFrame() >= art.frameCount())
                throw new IllegalArgumentException("Missing native accessory mapping for " + actor.actorId());
            return new SceneAccessoryPose(art.frame(tail.mappingFrame()), tail.flipX(), tail.flipY());
        }
        if (rom.characterAccessory(actor.character()) == null) return null;
        long tick = rom.characterAccessorySampleTick(actor.character(), actor.animation(), actor.pose(), actor.animationTick());
        if (tick < 0 || tick > actor.animationTick()) throw new IllegalArgumentException("Invalid accessory sample time");
        int at = Math.max(0, frame.frame() - (int) (actor.animationTick() - tick));
        int first = 0, end = document.durationFrames();
        for (var change : document.sceneChanges()) {
            if (change.frame() <= frame.frame()) first = change.frame();
            else { end = change.frame(); break; }
        }
        at = Math.max(first, at);
        var sample = at == frame.frame() ? actor : actorAt(document, actor, at);
        double velocityX = 0, velocityY = 0;
        // Sample within this shot only: a cut's position jump is never interpreted as speed.
        if (at + 1 < end) {
            var next = actorAt(document, actor, at + 1);
            velocityX = next.x() - sample.x(); velocityY = next.y() - sample.y();
        } else if (at > first) {
            var previous = actorAt(document, actor, at - 1);
            velocityX = sample.x() - previous.x(); velocityY = sample.y() - previous.y();
        }
        return rom.characterAccessoryPose(actor.character(), actor.animation(), actor.pose(), actor.animationTick(),
                sample.flipX(), velocityX, velocityY);
    }

    private static StageplayTimeline.ActorPose actorAt(StageplayDocument document,
            StageplayTimeline.ActorPose fallback, int frame) {
        return StageplayTimeline.sample(document, frame).actors().stream()
                .filter(actor -> actor.actorId().equals(fallback.actorId())).findFirst().orElse(fallback);
    }

    private static void transition(SceneCanvas canvas, StageplayTimeline.TransitionPose transition, int width, int height) {
        double coverage = transition.coverage();
        if (coverage <= 0) return;
        switch (transition.transition()) {
            case CUT -> { }
            case FADE_BLACK, FADE_WHITE -> {
                int color = transition.transition() == StageplayDocument.Transition.FADE_WHITE ? 0xFFFFFF : 0;
                canvas.fill(0, 0, width, height, ((int) Math.round(coverage * 255) << 24) | color);
            }
            case WIPE_LEFT, WIPE_RIGHT -> {
                int covered = (int) Math.ceil(width * coverage);
                boolean fromLeft = (transition.transition() == StageplayDocument.Transition.WIPE_LEFT) == transition.revealing();
                canvas.fill(fromLeft ? 0 : width - covered, 0, covered, height, 0xFF000000);
            }
        }
    }

    private static void backdrop(SceneCanvas canvas, SceneBackdrop backdrop, double cameraX,
                                 double cameraY, float zoom, int tick) {
        var image = backdrop.image();
        // Repeat the detached background vertically, applying its ROM-provided horizontal bands.
        int firstY = (int) Math.floor(cameraY / image.height()) * image.height();
        double endY = cameraY + canvas.height() / zoom;
        for (int repeat = firstY; repeat < endY; repeat += image.height()) {
            for (var band : backdrop.bands()) {
                int sourceX = backdrop.column(band, cameraX, tick);
                float y = (float) ((repeat + band.top() - cameraY) * zoom);
                if (y >= canvas.height() || y + band.height() * zoom <= 0) continue;
                for (float x = -sourceX * zoom; x < canvas.width(); x += image.width() * zoom)
                    canvas.drawRegion(image, 0, band.top(), image.width(), band.height(), x, y,
                            image.width() * zoom, band.height() * zoom, SceneDraw.plain());
            }
        }
    }
}
