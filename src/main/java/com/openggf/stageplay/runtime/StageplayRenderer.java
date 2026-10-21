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
        var camera = frame.camera(); var kit = resources.set(document);
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
        for (var actor : frame.actors()) {
            if (!actor.visible()) continue;
            var set = resources.character(actor.gameId(), actor.character());
            var sprite = set.frame(mappingFrame(actor));
            if (sprite == null) throw new IllegalArgumentException("Actor animation is unavailable: " + actor.actorId());
            float x = (float) ((actor.x() - camera.x()) * zoom), y = (float) ((actor.y() - camera.y()) * zoom);
            var style = SceneDraw.plain().withScale((float) actor.scale() * zoom).withFlipX(actor.flipX());
            var accessory = resources.rom(actor.gameId()).characterAccessory(actor.character());
            if (accessory != null) {
                // Explicit authored accessory cycle; this is staged art, not a claim of native tail simulation.
                int first = actor.gameId().equals("s3k") ? 0x22 : 0;
                int count = Math.min(5, accessory.frameCount() - first);
                if (count > 0) canvas.draw(accessory.frame(first + (actor.animationTick() / 8) % count), x, y, style);
            }
            canvas.draw(sprite, x, y, style);
        }
        for (var caption : frame.captions()) {
            int y = caption.y();
            for (String line : caption.text().split("\n", -1)) {
                canvas.text(line, caption.x(), y, caption.argb()); y += 12;
            }
        }
        canvas.unclip();
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
