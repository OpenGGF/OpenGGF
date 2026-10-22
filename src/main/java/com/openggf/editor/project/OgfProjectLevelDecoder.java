package com.openggf.editor.project;

import com.openggf.level.resources.AuthoredLevelDecoder;

import java.io.IOException;

/** Publishes the editor's strict file format as terrain through the level-resource provider port. */
public final class OgfProjectLevelDecoder implements AuthoredLevelDecoder {
    @Override public String format() { return "ogfproject"; }

    @Override public Decoded decode(byte[] bytes) throws IOException {
        var project = new EditorProjectStore().decode(bytes);
        return new Decoded(project.metadata().gameId().code(), project.snapshot());
    }
}
