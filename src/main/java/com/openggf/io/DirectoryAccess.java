package com.openggf.io;

/** Explicit trust boundary for unpacked mod asset directories. */
@com.openggf.game.ModApi
public enum DirectoryAccess {
    /** Packed jars are required in production; directory roots are refused. */
    PRODUCTION,
    /** Explicit creator development through tools such as {@code ggfmod run}. */
    DEVELOPMENT,
    /** Test fixtures with a trusted source directory. */
    TEST;

    void requireDirectoryAllowed() {
        if (this == PRODUCTION) {
            throw new IllegalArgumentException("directory mod roots are development/test only");
        }
    }
}
