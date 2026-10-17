package com.openggf.audio;

/**
 * Internal profile-owned rule for native mailbox request ids that a sound driver routes to a
 * music-table entry from outside its ordinary music id range. Profiles without the rule keep the
 * driver's own SFX/command classification for every native request.
 */
public interface NativeMailboxMusicRoutes {
    /** @return the engine music id the driver loads for {@code nativeRequestId}, or -1 for none. */
    int musicForNativeRequest(int nativeRequestId);
}
