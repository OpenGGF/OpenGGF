package com.openggf.game.sonic3k.render;

import com.openggf.game.rewind.RewindSnapshottable;

/** Runtime rewind projection; the durable world DATA owns the state and snapshot identity. */
final class S3kPhysicalDisplayRewindAdapter implements RewindSnapshottable<Object> {
    private final S3kPhysicalDisplayWorld data;

    S3kPhysicalDisplayRewindAdapter(S3kPhysicalDisplayWorld data) {
        this.data = data;
    }

    @Override public String key() { return S3kPhysicalDisplayWorld.KEY; }
    @Override public Object capture() { return data.capture(); }
    @Override public void restore(Object snapshot) { data.restore(snapshot); }
}
