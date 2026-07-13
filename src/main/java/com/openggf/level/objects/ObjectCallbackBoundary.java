package com.openggf.level.objects;

import java.util.function.Supplier;

/** Engine-owned boundary contract for creator object callbacks. */
public interface ObjectCallbackBoundary {
    <T> T call(String owner, Supplier<T> callback);
}
