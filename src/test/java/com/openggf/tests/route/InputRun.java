package com.openggf.tests.route;

/**
 * One authored pad-input run: {@code mask} held for {@code frames} frames.
 * Masks use the {@code AbstractPlayableSprite.INPUT_*} bits.
 *
 * <p>Shared route primitive for headless route controllers. Extracted from
 * the FBZ2 native route controller in commit 610464952; see
 * {@code docs/architecture/research/2026-09-13-live-state-route-controllers.md}.
 */
public record InputRun(int frames, int mask) { }
