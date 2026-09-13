package com.openggf.tests.route;

/**
 * One authored pad-input run: {@code mask} held for {@code frames} frames.
 * Masks use the {@code AbstractPlayableSprite.INPUT_*} bits.
 *
 * <p>Route primitives shared by headless route tests (LTS-04 helper). Extracted
 * from the FBZ2 native route controller; see
 * {@code docs/architecture/research/2026-09-13-live-state-route-controllers.md}.
 */
public record InputRun(int frames, int mask) { }
