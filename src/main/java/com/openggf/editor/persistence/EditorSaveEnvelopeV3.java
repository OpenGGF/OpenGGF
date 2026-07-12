package com.openggf.editor.persistence;

/** Version-3 editor envelope kept separate so historical v1/v2 DTOs remain byte-stable. */
public record EditorSaveEnvelopeV3(int version, String gameCode, int zone, int act,
                                   String savedAt, EditorSavePayloadV3 payload, String hash) {}
