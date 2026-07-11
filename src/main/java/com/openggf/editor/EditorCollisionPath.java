package com.openggf.editor;

@com.openggf.game.ModApi
public enum EditorCollisionPath {
    PRIMARY,
    SECONDARY;

    public EditorCollisionPath other() {
        return this == PRIMARY ? SECONDARY : PRIMARY;
    }
}
