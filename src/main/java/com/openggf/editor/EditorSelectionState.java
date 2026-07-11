package com.openggf.editor;

@com.openggf.game.ModApi
public record EditorSelectionState(
        Integer selectedBlock,
        Integer selectedChunk
) {
    public static EditorSelectionState empty() {
        return new EditorSelectionState(null, null);
    }
}
