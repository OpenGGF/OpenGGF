package com.openggf.level.objects;

/** Verified live fields retained across native partial code writes, never trace/bootstrap RAM. */
public record NativeSstState(int renderFlags, int xFraction, int yFraction,
        int mappingFrame, int animationFrame, int animationTimer, int flags38,
        int status, int priority, int artTile) {
    public static final NativeSstState CLEARED=new NativeSstState(0,0,0,0,0,0,0,0,0,0);
    public NativeSstState {
        renderFlags&=255;xFraction&=65535;yFraction&=65535;
        mappingFrame&=255;animationFrame&=255;animationTimer&=255;flags38&=255;
        status&=255;priority&=65535;artTile&=65535;
    }
}
