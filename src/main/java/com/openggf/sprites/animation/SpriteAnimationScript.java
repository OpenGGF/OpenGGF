package com.openggf.sprites.animation;

import java.util.List;

@com.openggf.game.ModApi
public record SpriteAnimationScript(
        int delay,
        List<Integer> frames,
        SpriteAnimationEndAction endAction,
        int endParam
) {
}
