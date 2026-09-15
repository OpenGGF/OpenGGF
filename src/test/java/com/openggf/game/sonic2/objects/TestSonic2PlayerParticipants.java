package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2PlayerParticipants {
    @Test
    void absentFallbackIsFirstAndNewListRemainsMutableWithoutChangingSource() {
        PlayableEntity first = player("first"), second = player("second"), fallback = player("fallback");
        List<PlayableEntity> original = List.of(first, second);
        List<PlayableEntity> result = Sonic2PlayerParticipants.prependIfAbsent(original, fallback);
        assertEquals(List.of(fallback, first, second), result);
        result.removeFirst();
        assertEquals(List.of(first, second), original);
    }

    @Test
    void nullAndEqualFallbackReturnOriginalListWithoutIdentityDeduplication() {
        PlayableEntity first = player("same"), equal = player("same");
        List<PlayableEntity> original = List.of(first);
        assertNotSame(first, equal);
        assertSame(original, Sonic2PlayerParticipants.prependIfAbsent(original, null));
        assertSame(original, Sonic2PlayerParticipants.prependIfAbsent(original, first));
        assertSame(original, Sonic2PlayerParticipants.prependIfAbsent(original, equal));
    }

    private static PlayableEntity player(String name) {
        return (PlayableEntity) Proxy.newProxyInstance(PlayableEntity.class.getClassLoader(),
                new Class<?>[]{PlayableEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> name;
                    case "hashCode" -> name.hashCode();
                    case "equals" -> args[0] != null && name.equals(args[0].toString());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
