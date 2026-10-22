package com.openggf.level.metadata;

/** Internal authoring capability; creator API and ownership contracts remain unchanged. */
public interface EditorPalettePolicyProvider {
    boolean isPaletteCellEditable(int line, int color);
}
