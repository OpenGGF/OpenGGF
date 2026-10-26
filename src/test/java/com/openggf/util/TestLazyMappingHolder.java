package com.openggf.util;

import com.openggf.game.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameServices;
import com.openggf.game.RomDataSource;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.level.LevelManager;
import com.openggf.level.render.SpriteMappingFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestLazyMappingHolder {

    @BeforeEach
    public void clearGameplaySession() {
        SessionManager.clear();
    }

    @Test
    public void testReturnsEmptyListWhenLevelManagerNull() {
        LazyMappingHolder holder = new LazyMappingHolder();
        List<SpriteMappingFrame> result = holder.get(0x1234,
                (reader, addr) -> { throw new AssertionError("should not be called"); },
                "TestObj");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void romlessSourceDoesNotLatchBeforeCartridgeResourcesAreMounted() throws Exception {
        LevelManager manager = mock(LevelManager.class);
        WorldSession world = mock(WorldSession.class);
        when(manager.getGame()).thenReturn(mock(Game.class));
        when(manager.getWorldSession()).thenReturn(world);
        GameDataSource romless = mock(GameDataSource.class);
        when(romless.rom()).thenReturn(Optional.empty());
        when(world.getDataSource()).thenReturn(romless);
        var frames = List.of(new SpriteMappingFrame(List.of()));
        AtomicInteger attempts = new AtomicInteger();
        LazyMappingHolder holder = new LazyMappingHolder();
        LazyMappingHolder.MappingLoader loader = (reader, addr) -> {
            attempts.incrementAndGet();
            assertEquals(1, addr);
            assertEquals(0x34, reader.readU8(addr));
            return frames;
        };
        try (Rom rom = Rom.fromReader(RomByteReader.fromBytes(new byte[]{0x12, 0x34}), "mapping fixture");
             var services = mockStatic(GameServices.class, invocation -> {
                 if (invocation.getMethod().getName().equals("levelOrNull")) return manager;
                 throw new AssertionError("Mapping load touched ambient " + invocation.getMethod().getName());
             })) {
            assertTrue(holder.get(1, loader, "TestObj").isEmpty());
            assertEquals(0, attempts.get());
            when(world.getDataSource()).thenReturn(new RomDataSource(rom, "rom:mapping-fixture"));

            assertSame(frames, holder.get(1, loader, "TestObj"));
            assertSame(frames, holder.get(1, loader, "TestObj"));
            assertEquals(1, attempts.get(), "A real mounted-source load is cached once");
        }
    }

    @Test
    void failedMountedSourceLoadIsNotRetried() throws Exception {
        LevelManager manager = mock(LevelManager.class);
        WorldSession world = mock(WorldSession.class);
        when(manager.getGame()).thenReturn(mock(Game.class));
        when(manager.getWorldSession()).thenReturn(world);
        AtomicInteger attempts = new AtomicInteger();
        try (Rom rom = Rom.fromReader(RomByteReader.fromBytes(new byte[]{0x12, 0x34}), "mapping fixture");
             var services = mockStatic(GameServices.class)) {
            when(world.getDataSource()).thenReturn(new RomDataSource(rom, "rom:mapping-fixture"));
            services.when(GameServices::levelOrNull).thenReturn(manager);
            LazyMappingHolder holder = new LazyMappingHolder();
            LazyMappingHolder.MappingLoader loader = (reader, addr) -> {
                attempts.incrementAndGet();
                throw new IOException("unreadable mappings");
            };

            assertTrue(holder.get(1, loader, "TestObj").isEmpty());
            assertTrue(holder.get(1, loader, "TestObj").isEmpty());
            assertEquals(1, attempts.get());
        }
    }
}
