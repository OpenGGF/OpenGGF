package com.openggf.level.objects;

/**
 * Engine-internal access to the S3K {@code Seek_Object_Manager} cursor reposition for zone code
 * that writes the camera X directly, such as the Doomsday Zone level wrap
 * ({@code loc_81726}, sonic3k.asm:173350-173370). Not part of the Mod API.
 */
public final class ObjectPlacementSeek {
    private ObjectPlacementSeek() {
    }

    public static void seek(ObjectManager objectManager, int cameraX) {
        if (objectManager != null) {
            objectManager.seekPlacementCursors(cameraX & 0xFFFF);
        }
    }
}
