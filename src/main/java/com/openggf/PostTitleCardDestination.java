package com.openggf;

/**
 * Where to transition after a title card completes.
 * Normally LEVEL, but bonus stage entry routes through title card first.
 */
enum PostTitleCardDestination {
    /** Normal: title card -> LEVEL mode (default) */
    LEVEL,
    /** Bonus stage entry: title card -> BONUS_STAGE mode */
    BONUS_STAGE
}
