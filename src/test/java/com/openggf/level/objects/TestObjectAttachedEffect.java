package com.openggf.level.objects;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestObjectAttachedEffect {
    @Test void attachedEffectKeepsBodyOwnershipAndRestoresTheEnclosingSubjectOnFailure() {
        var graphics = GraphicsManager.getInstance();
        var services = new TestObjectServices() {
            @Override public GraphicsManager graphicsManager() { return graphics; }
        };
        var body = new SpritePresentation.Body(SpritePresentation.Role.BADNIK, 400, 200);
        var outer = new SpritePresentation.Subject("guard:7", SpritePresentation.Part.OBJECT_BODY, true, 200, null, body);
        var before = SpritePresentation.subject(graphics);
        SpritePresentation.withSubject(graphics, outer, () -> {
            assertThrows(IllegalStateException.class, () -> services.renderAttachedEffect(() -> {
                var effect = SpritePresentation.subject(graphics);
                assertEquals(outer.id(), effect.id()); assertTrue(effect.suppressed());
                assertSame(body, effect.body()); assertEquals(100, effect.headScalePercent()); assertNull(effect.head());
                assertEquals(SpritePresentation.Part.ATTACHED_EFFECT, effect.part());
                throw new IllegalStateException("renderer failed");
            }));
            assertSame(outer, SpritePresentation.subject(graphics));
        });
        assertSame(before, SpritePresentation.subject(graphics));
    }
}
