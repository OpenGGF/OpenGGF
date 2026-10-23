package com.openggf.game.preview;

import com.openggf.game.preview.TitlePreviewProvider.Image;
import com.openggf.game.preview.TitlePreviewProvider.Sequence;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/** Lazily composes decoded CPU art once per presentation token, without retaining a ROM. */
public final class GeneratedTitlePreviewSequence implements Sequence {
    private final int width;
    private final int height;
    private final int stillTick;
    private final IntUnaryOperator tokenFunction;
    private final IntFunction<Image> imageFactory;
    private final Map<Integer, Image> cache = new HashMap<>();

    public GeneratedTitlePreviewSequence(int width, int height, int stillTick,
            IntUnaryOperator tokenFunction, IntFunction<Image> imageFactory) {
        if (width <= 0 || height <= 0 || stillTick < 0) {
            throw new IllegalArgumentException("Invalid preview dimensions or still tick");
        }
        this.width = width;
        this.height = height;
        this.stillTick = stillTick;
        this.tokenFunction = Objects.requireNonNull(tokenFunction);
        this.imageFactory = Objects.requireNonNull(imageFactory);
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public int stillTick() { return stillTick; }

    @Override
    public int frameTokenAt(int tick) {
        return tokenFunction.applyAsInt(Math.max(0, tick));
    }

    @Override
    public Image imageAt(int tick) {
        return cache.computeIfAbsent(frameTokenAt(tick), token -> {
            Image image = Objects.requireNonNull(imageFactory.apply(token));
            if (image.width() != width || image.height() != height) {
                throw new IllegalArgumentException("Preview frame dimensions must remain fixed");
            }
            return image;
        });
    }
}
