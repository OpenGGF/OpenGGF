package com.openggf.game.profiles.touchresponse;

import com.openggf.level.objects.TouchResponseProvider;

public final class TouchResponseProfileAdapter {
    private TouchResponseProfileAdapter() {
    }

    public static TouchResponseProfile fromProvider(TouchResponseProvider provider) {
        return TouchResponseProfile.fromProvider(provider);
    }
}
