package com.openggf.editor.route;

/** Shared semantic colours for the two route frontends; RGB, independent of GL/ImGui. */
public final class RouteEditorStyle {
    private RouteEditorStyle() { }
    public static int playerColour(String player) {
        return switch (player) { case "P1" -> 0x4C9AFF; case "P2" -> RouteTapeOverlay.SIDEKICK; default -> 0x61E5ED; };
    }
    /** Half saturation and reduced luminance distinguish immutable inputs from editable bars. */
    public static int lockedColour(int rgb) {
        int r=rgb>>16&255, g=rgb>>8&255, b=rgb&255, grey=(r+g+b)/3;
        return ((r+grey)*3/10)<<16 | ((g+grey)*3/10)<<8 | (b+grey)*3/10;
    }
}
