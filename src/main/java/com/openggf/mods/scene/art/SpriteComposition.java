package com.openggf.mods.scene.art;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import java.util.Objects;

/** Ordered detached sprite geometry. Recipes, animation and target selection stay with the mod. */
@com.openggf.game.ModApi
public final class SpriteComposition {
    @com.openggf.game.ModApi
    public record Part(SceneSprite sprite, float x, float y, boolean flipX) {
        public Part {
            Objects.requireNonNull(sprite, "sprite");
            coordinate(x); coordinate(y);
        }
    }

    @com.openggf.game.ModApi
    public record Bounds(float left, float top, float right, float bottom) {
        public Bounds {
            coordinate(left); coordinate(top); coordinate(right); coordinate(bottom);
            if (right < left || bottom < top) throw new IllegalArgumentException("Inverted sprite bounds");
        }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    @com.openggf.game.ModApi
    public record DrawPart(SceneSprite sprite, float x, float y, SceneDraw style) {
        public DrawPart {
            Objects.requireNonNull(sprite, "sprite"); Objects.requireNonNull(style, "style");
            coordinate(x); coordinate(y);
            if (!Float.isFinite(style.scaleX()) || !Float.isFinite(style.scaleY())
                    || style.scaleX() <= 0 || style.scaleY() <= 0)
                throw new IllegalArgumentException("Sprite scale must be finite and positive");
            bounds(sprite, x, y, style);
        }
    }

    @com.openggf.game.ModApi
    public record Placement(List<DrawPart> parts, Bounds bounds) {
        public Placement {
            parts = List.copyOf(parts); Objects.requireNonNull(bounds, "bounds");
        }
        /** Emits only prepared values; no geometry or gameplay state is published by drawing. */
        public void draw(SceneCanvas canvas) {
            Objects.requireNonNull(canvas, "canvas");
            for (DrawPart part : parts) canvas.draw(part.sprite(), part.x(), part.y(), part.style());
        }
    }

    private final List<Part> parts;
    private final Bounds bounds;

    public SpriteComposition(List<Part> parts) {
        this.parts = List.copyOf(parts);
        this.bounds = union(this.parts.stream().map(p -> bounds(p.sprite(), p.x(), p.y(),
                SceneDraw.plain().withFlipX(p.flipX()))).toList());
    }

    public List<Part> parts() { return parts; }
    public Bounds bounds() { return bounds; }

    /** Style scale/flip applies to the entire composition around (x,y), including part offsets. */
    public Placement place(float x, float y, SceneDraw style) {
        Objects.requireNonNull(style, "style"); coordinate(x); coordinate(y);
        if (!Float.isFinite(style.scaleX()) || !Float.isFinite(style.scaleY())
                || style.scaleX() <= 0 || style.scaleY() <= 0)
            throw new IllegalArgumentException("Composition scale must be finite and positive");
        List<DrawPart> prepared = parts.stream().map(p -> new DrawPart(p.sprite(),
                x + p.x() * style.scaleX() * (style.flipX() ? -1 : 1),
                y + p.y() * style.scaleY() * (style.flipY() ? -1 : 1),
                style.withFlipX(p.flipX() != style.flipX()))).toList();
        return new Placement(prepared, union(prepared.stream().map(p -> bounds(p.sprite(), p.x(), p.y(),
                p.style())).toList()));
    }

    private static Bounds bounds(SceneSprite sprite, float x, float y, SceneDraw style) {
        float left = x - (style.flipX() ? sprite.width() - sprite.originX() : sprite.originX()) * style.scaleX();
        float top = y - (style.flipY() ? sprite.height() - sprite.originY() : sprite.originY()) * style.scaleY();
        return new Bounds(left, top, left + sprite.width() * style.scaleX(), top + sprite.height() * style.scaleY());
    }

    private static Bounds union(List<Bounds> bounds) {
        if (bounds.isEmpty()) return new Bounds(0, 0, 0, 0);
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (Bounds b : bounds) {
            left = Math.min(left, b.left()); top = Math.min(top, b.top());
            right = Math.max(right, b.right()); bottom = Math.max(bottom, b.bottom());
        }
        return new Bounds(left, top, right, bottom);
    }

    private static void coordinate(float value) {
        if (!Float.isFinite(value) || value < -Integer.MAX_VALUE || value >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Sprite coordinate exceeds the finite pixel range");
    }
}
