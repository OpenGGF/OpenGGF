package com.openggf.graphics;

/** Internal bridge kept outside the published creator API. */
public final class TilemapTextureInternalAccess {
    private TilemapTextureInternalAccess() {
    }

    public static void setUploadOps(TilemapTexture texture, TilemapTextureUploadOps uploadOps) {
        texture.setUploadOps(uploadOps);
    }
}
