package com.openggf.level.objects;

public final class TouchResponseProfileAdapter {
    private TouchResponseProfileAdapter() {
    }

    public static TouchResponseProfile fromProvider(TouchResponseProvider provider) {
        return TouchResponseProfile.fromProvider(provider);
    }
}
