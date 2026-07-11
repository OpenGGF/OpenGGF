package com.openggf.audio.rewind;

@com.openggf.game.ModApi
public record AudioTimelineEntry(long frame, int order, AudioCommand command) {
}
