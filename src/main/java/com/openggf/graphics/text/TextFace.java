package com.openggf.graphics.text;

/**
 * Host text roles. {@link #COMPACT} is the light 6x8 menu lettering and its fallbacks;
 * {@link #LABEL} is the bold 9x10 lettering and its fallbacks, drawn at integer scales.
 */
public enum TextFace {
    /** Original fixed 6x8 cells for transport and trace overlays. */
    FIXED, COMPACT, LABEL
}
