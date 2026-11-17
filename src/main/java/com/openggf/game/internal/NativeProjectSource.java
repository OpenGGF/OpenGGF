package com.openggf.game.internal;

import java.util.Objects;

/** Bounded source identity for authored views which require a fresh native stage load.
 * This contains no asset paths or executable provider names. A host must validate all
 * fields against its opened media before materializing the stage.
 */
public record NativeProjectSource(String mediaIdentity, String stage, int zone, int act,
                                  String period, String program, int blockGridSide) {
    public NativeProjectSource {
        Objects.requireNonNull(mediaIdentity, "mediaIdentity");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(program, "program");
        if (mediaIdentity.isBlank() || mediaIdentity.length() > 512
                || !stage.matches("[A-Za-z0-9_-]{1,64}")
                || !period.matches("[A-Za-z0-9_-]{1,32}")
                || !program.matches("[A-Za-z0-9_.;-]{1,64}")
                || zone < 0 || zone > 255 || act < 0 || act > 255
                || (blockGridSide != 8 && blockGridSide != 16))
            throw new IllegalArgumentException("Native project source exceeds its bounded identity format");
    }
}
