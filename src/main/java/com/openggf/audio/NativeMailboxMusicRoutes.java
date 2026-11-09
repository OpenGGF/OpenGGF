package com.openggf.audio;

/**
 * Internal profile-owned translation between engine playback ids and native music mailbox bytes.
 * Also names music reached through raw native requests, without changing their classification.
 */
public interface NativeMailboxMusicRoutes {
    /** Engine playback id to ROM request byte; unknown ids (including commands) pass through. */
    default int nativeRequestForMusic(int musicId) {
        return musicId;
    }

    /** @return the engine music id the driver loads for {@code nativeRequestId}, or -1 for none. */
    int musicForNativeRequest(int nativeRequestId);
}
