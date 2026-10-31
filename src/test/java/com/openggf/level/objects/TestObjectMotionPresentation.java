package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.*;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePresentationRenderer;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestObjectMotionPresentation {
    private GraphicsManager graphics;
    @BeforeEach void setup() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance(); graphics.initHeadless();
    }
    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.destroyForReinit(); }
    @Test void dispatcherUsesTheObjectsNativePositionAndCreationIdentity() {
        for (ObjectInstance object : new ObjectInstance[]{new Ordinary(), new CullOnly(), new Screen()}) {
            var services = mock(ObjectServices.class);
            var manager = mock(ObjectManager.class);
            var query = mock(ObjectQuery.class);
            var id = ObjectRefId.dynamic(3, 0, 7);
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.objectManager()).thenReturn(manager);
            when(manager.objectQuery()).thenReturn(query);
            when(query.identityOf(object)).thenReturn(Optional.of(id));
            var capture = SpriteMotionInternalAccess.collect(graphics, 100, 200,
                    () -> SpritePresentationRenderer.prepare(graphics, 100, 200,
                            () -> ObjectMotionPresentation.draw(object, services,
                                    () -> graphics.renderPatternWithId(3, new PatternDesc(), 120, 230))));
            var owner = capture.motion().owner(capture.frame().tiles().getFirst());
            if (object instanceof Screen) {
                assertNull(owner);
                assertTrue(capture.motion().screen(capture.frame().tiles().getFirst()));
            } else {
                assertEquals(new MotionPacket.ObjectKey(id), owner.key());
                assertEquals(120, owner.x()); assertEquals(230, owner.y());
                assertEquals(MotionPacket.Anchoring.FOLLOW, owner.anchoring());
            }
        }
    }
    private static class Ordinary implements ObjectInstance {
        public ObjectSpawn getSpawn() { return new ObjectSpawn(120, 230, 1, 0, 0, false, 0); }
        public void update(int count, com.openggf.game.PlayableEntity player) { }
        public void appendRenderCommands(java.util.List<GLCommand> commands) { }
        public boolean isHighPriority() { return false; }
        public boolean isDestroyed() { return false; }
    }
    @CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
    private static class CullOnly extends Ordinary { }
    @CameraRelativeDraw(CameraRelativeDraw.Use.SCREEN_POSITION)
    private static class Screen extends Ordinary { }
}
