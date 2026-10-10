package com.openggf.sprites;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

/** A sprite's bound renderer context cannot drift with the application root or leak to a creator caller. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestSpriteGraphicsAccess {
    @TempDir Path temp;
    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    @Test void existingAndNewSpritesKeepTheirOwnBoundContextsAfterEngineRebinding() {
        var original = EngineServices.current();
        var first = new Sonic("sonic", (short) 32, (short) 48);
        var alternate = org.mockito.Mockito.mock(GraphicsManager.class);
        try {
            EngineServices.configure(new EngineContext(original.configuration(), alternate, original.audio(),
                    original.roms(), original.profiler(), original.debugOverlay(), original.playbackDebug(),
                    original.romDetection(), original.crossGameFeatures(), original.moduleResolutionService()));
            var second = new Sonic("sonic", (short) 32, (short) 48);
            assertSame(original.graphics(), SpriteGraphicsAccess.graphics(first));
            assertSame(alternate, SpriteGraphicsAccess.graphics(second));
            Function<AbstractSprite, GraphicsManager> hostReference = SpriteGraphicsAccess::graphics;
            assertSame(original.graphics(), hostReference.apply(first));
            Function<AbstractSprite, GraphicsManager> hostLambda = sprite -> SpriteGraphicsAccess.graphics(sprite);
            assertSame(original.graphics(), hostLambda.apply(first));
            assertSame(original.graphics(), HostNested.graphics(first));
        } finally {
            EngineServices.configure(original);
        }
    }

    private static final class HostNested {
        static GraphicsManager graphics(AbstractSprite sprite) { return SpriteGraphicsAccess.graphics(sprite); }
    }

    @Test void directAndHiddenCreatorCallsCannotAcquireNativeGraphics() throws Exception {
        var source = temp.resolve("SpriteGraphicsProbe.java");
        Files.writeString(source, """
                package foreign;
                import com.openggf.sprites.AbstractSprite;
                import com.openggf.sprites.SpriteGraphicsAccess;
                import com.openggf.graphics.GraphicsManager;
                import java.util.function.Function;
                public final class SpriteGraphicsProbe {
                    public static GraphicsManager direct(AbstractSprite sprite) {
                        return SpriteGraphicsAccess.graphics(sprite);
                    }
                    public static GraphicsManager nested(AbstractSprite sprite) { return Nested.graphics(sprite); }
                    private static final class Nested {
                        static GraphicsManager graphics(AbstractSprite sprite) { return SpriteGraphicsAccess.graphics(sprite); }
                    }
                    public static Function<AbstractSprite, GraphicsManager> lambda() {
                        return sprite -> SpriteGraphicsAccess.graphics(sprite);
                    }
                    public static Function<AbstractSprite, GraphicsManager> hidden() {
                        return SpriteGraphicsAccess::graphics;
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-classpath", System.getProperty("java.class.path"),
                "-d", temp.toString(), source.toString()));
        var sprite = new Sonic("sonic", (short) 32, (short) 48);
        try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, getClass().getClassLoader())) {
            var probe = Class.forName("foreign.SpriteGraphicsProbe", true, loader);
            assertSame(loader, probe.getClassLoader());
            var denied = assertThrows(InvocationTargetException.class,
                    () -> probe.getMethod("direct", AbstractSprite.class).invoke(null, sprite));
            assertInstanceOf(SecurityException.class, denied.getCause());
            var nested = assertThrows(InvocationTargetException.class,
                    () -> probe.getMethod("nested", AbstractSprite.class).invoke(null, sprite));
            assertInstanceOf(SecurityException.class, nested.getCause());
            @SuppressWarnings("unchecked")
            var lambda = (Function<AbstractSprite, GraphicsManager>) probe.getMethod("lambda").invoke(null);
            assertThrows(SecurityException.class, () -> lambda.apply(sprite));
            @SuppressWarnings("unchecked")
            var hidden = (Function<AbstractSprite, GraphicsManager>) probe.getMethod("hidden").invoke(null);
            assertThrows(SecurityException.class, () -> hidden.apply(sprite));
        }
    }
}
