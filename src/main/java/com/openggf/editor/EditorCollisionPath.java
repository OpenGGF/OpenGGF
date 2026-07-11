package com.openggf.editor;

public enum EditorCollisionPath {
    PRIMARY,
    SECONDARY;

    public EditorCollisionPath other() {
        return this == PRIMARY ? SECONDARY : PRIMARY;
    }
}
