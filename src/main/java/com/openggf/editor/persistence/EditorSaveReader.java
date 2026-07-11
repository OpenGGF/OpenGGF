package com.openggf.editor.persistence;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
interface EditorSaveReader {
    JsonNode read(Path file) throws IOException;
}
