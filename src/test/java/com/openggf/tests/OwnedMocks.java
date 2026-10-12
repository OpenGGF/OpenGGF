package com.openggf.tests;

import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

/**
 * Mocks owned by one fixture invocation. Closing clears each owned mock's inline
 * state (including stubbings and recorded arguments) without invalidating other
 * fixtures' mocks. Mockito disables cleared mocks: they must not be inspected or
 * invoked after close. Closing again is harmless; this owner can track fresh mocks.
 * Origin: 2026-10-07 ordinary-suite retained-memory investigation.
 */
public final class OwnedMocks implements AutoCloseable {
    private final List<Object> mocks = new ArrayList<>();

    public <T> T mock(Class<T> type) {
        return mock(type, Mockito.withSettings());
    }

    public <T> T mock(Class<T> type, MockSettings settings) {
        T mock = Mockito.mock(type, settings);
        mocks.add(mock);
        return mock;
    }

    public <T> T mock(Class<T> type, Answer<?> answer) {
        return mock(type, Mockito.withSettings().defaultAnswer(answer));
    }

    public <T> T spy(T instance) {
        T spy = Mockito.spy(instance);
        mocks.add(spy);
        return spy;
    }

    @Override
    public void close() {
        try {
            for (Object mock : mocks) {
                Mockito.framework().clearInlineMock(mock);
            }
        } finally {
            mocks.clear();
        }
    }
}
