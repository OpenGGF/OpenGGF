package com.openggf.editor.palette;

/** Internal authoring capability; creator API and ownership contracts remain unchanged. */
public interface EditorPalettePolicyProvider {
    boolean isPaletteCellEditable(int line, int color);
}
