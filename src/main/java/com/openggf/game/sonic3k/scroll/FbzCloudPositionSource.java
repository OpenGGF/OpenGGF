package com.openggf.game.sonic3k.scroll;

/** Read-only bridge from FBZ CloudDeform to the ten screen-space cloud objects. */
public interface FbzCloudPositionSource {
    SwScrlFbz.CloudPosition cloudPositionAtAddressSlot(int addressSlot);
}
