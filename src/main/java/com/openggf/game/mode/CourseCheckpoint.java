package com.openggf.game.mode;

import com.openggf.game.ModApi;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.level.Level;

/** Opaque immutable retained course value. It never contains mode/match adapters. */
@ModApi
public final class CourseCheckpoint {
    final Object owner;
    final Level level;
    final int zone;
    final int act;
    final String character;
    final CompositeSnapshot snapshot;
    final long layoutVersion;
    CourseCheckpoint(Object owner, Level level, int zone, int act, String character,
                     CompositeSnapshot snapshot, long layoutVersion) {
        this.owner = owner; this.level = level; this.zone = zone; this.act = act;
        this.character = character; this.snapshot = snapshot; this.layoutVersion = layoutVersion;
    }
}
