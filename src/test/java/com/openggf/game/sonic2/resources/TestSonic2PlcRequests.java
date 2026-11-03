package com.openggf.game.sonic2.resources;

import com.openggf.game.GameModule;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectServices;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2PlcRequests {
    @Test void engineBugsAreNotReportedAsRetryableSubmissions() throws IOException {
        var services = services();
        var provider = (Sonic2ObjectArtProvider) services.gameModule().getObjectArtProvider();
        var failure = new IllegalStateException("art owner failed");
        when(provider.preparePlcs(any(int[].class))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Sonic2PlcRequests.append(services, 1)));
    }

    @Test void unreadableRomIsNotReportedAsRetryableSubmission() throws IOException {
        var services = services();
        var provider = (Sonic2ObjectArtProvider) services.gameModule().getObjectArtProvider();
        var failure = new IOException("ROM read failed");
        when(provider.preparePlcs(any(int[].class))).thenThrow(failure);
        assertSame(failure, assertThrows(UncheckedIOException.class,
                () -> Sonic2PlcRequests.append(services, 1)).getCause());
    }

    @Test void onlyCapacityRejectionRequestsARetryAndDoesNotPublishArt() throws IOException {
        var services = services();
        var module = services.gameModule();
        var plc = module.getGameService(Sonic2PlcService.class);
        var capacity = mock(Sonic2PlcService.QueueCapacityException.class);
        doThrow(capacity).when(plc).transact(any(Sonic2PlcService.Operation[].class));
        assertFalse(Sonic2PlcRequests.append(services, 1));
        verify((Sonic2ObjectArtProvider) module.getObjectArtProvider(), never()).publishPreparedPlc(any());
        verify(services.levelManager(), never()).refreshObjectArtPatterns();
    }

    @Test void rendererFailureIsNotReportedAsRetryableSubmission() throws IOException {
        var services = services();
        var provider = (Sonic2ObjectArtProvider) services.gameModule().getObjectArtProvider();
        when(provider.publishPreparedPlc(any())).thenReturn(true);
        var failure = new IllegalStateException("renderer owner failed after publication");
        var level = services.levelManager();
        doThrow(failure).when(level).refreshObjectArtPatterns();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> Sonic2PlcRequests.append(services, 1)));
    }

    private static ObjectServices services() throws IOException {
        var services = mock(ObjectServices.class);
        var module = mock(GameModule.class);
        var provider = mock(Sonic2ObjectArtProvider.class);
        when(services.gameModule()).thenReturn(module);
        when(services.levelManager()).thenReturn(mock(LevelManager.class));
        when(module.getObjectArtProvider()).thenReturn(provider);
        when(module.getGameService(Sonic2PlcService.class)).thenReturn(mock(Sonic2PlcService.class));
        when(provider.preparePlcs(any(int[].class))).thenReturn(new Sonic2ObjectArtProvider.PreparedPlc(List.of()));
        return services;
    }
}
