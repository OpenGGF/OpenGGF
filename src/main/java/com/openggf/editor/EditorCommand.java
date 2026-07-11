package com.openggf.editor;

@com.openggf.game.ModApi
public interface EditorCommand {
    void apply();
    void undo();
}
