package com.openggf.game.soniccd.objects.common;

import com.openggf.camera.CameraPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestCdHudAnchor {
    @org.junit.jupiter.api.BeforeEach void reset() { com.openggf.tests.TestEnvironment.resetAll(); }
    @org.junit.jupiter.api.AfterEach void cleanup() { com.openggf.tests.TestEnvironment.resetAll(); }

    @ParameterizedTest
    @CsvSource({"320,EDGE,DEFAULT", "320,EDGE,LEFT_EDGE", "320,NATIVE,DEFAULT", "320,NATIVE,LEFT_EDGE",
            "528,EDGE,DEFAULT", "528,EDGE,LEFT_EDGE", "528,NATIVE,DEFAULT", "528,NATIVE,LEFT_EDGE"})
    void everyHudPieceUsesSharedCameraRelativeOrigin(int width, String mode, String anchor) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        config.setSessionOverride(SonicConfiguration.WIDESCREEN_DRAW_MODE, mode);
        config.setSessionOverride(SonicConfiguration.DISPLAY_HUD_ANCHOR, anchor);
        var camera = GameServices.camera();
        camera.refreshViewportDimensions(config);
        var graphics = mock(com.openggf.graphics.GraphicsManager.class);
        when(graphics.getProjectionWidth()).thenReturn(width);
        var hud = new HudRenderManager(graphics, camera, null);
        hud.setViewportWidth(width);
        HudProfileAccess.applyDisplaySettings(hud, config);
        var originMethod = HudRenderManager.class.getDeclaredMethod("nativeOrigin");
        originMethod.setAccessible(true);
        int origin = (int) originMethod.invoke(hud);
        assertEquals(("LEFT_EDGE".equals(anchor) ? 0 : (width - 320) / 2)
                - CameraPresentationAccess.inset(camera), origin);

        assertEquals((width - 320) / 2 - CameraPresentationAccess.inset(camera),
                CdScreenPosition.centredOrigin(new StubObjectServices() {
                    @Override public com.openggf.camera.Camera camera() { return camera; }
                    @Override public com.openggf.graphics.GraphicsManager graphicsManager() { return graphics; }
                    @Override public com.openggf.configuration.SonicConfigurationService configuration() { return config; }
                }), "cards and tallies centre independently of the HUD anchor");

        var renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        var renders = mock(ObjectRenderManager.class);
        when(renders.getRenderer(anyString())).thenReturn(renderer);
        var services = mock(ObjectServices.class);
        when(services.configuration()).thenReturn(config);
        when(services.camera()).thenReturn(camera);
        when(services.graphicsManager()).thenReturn(graphics);
        when(services.renderManager()).thenReturn(renders);
        var donorLife = mock(com.openggf.sprites.render.PlayerSpriteRenderer.class);
        var art = mock(com.openggf.game.soniccd.objects.art.CdObjectArtProvider.class);
        when(art.image()).thenReturn(mock(com.openggf.game.soniccd.level.CdStageImage.class));
        when(art.donorLifeRenderer()).thenReturn(donorLife);
        var module = mock(com.openggf.game.GameModule.class);
        when(module.getGameService(com.openggf.game.soniccd.objects.art.CdObjectArtProvider.class)).thenReturn(art);
        when(services.gameModule()).thenReturn(module);
        for (var kind : CdHud.Kind.values()) {
            clearInvocations(renderer);
            var piece = ObjectConstructionContext.construct(services, () -> new CdHud(kind));
            piece.setServices(services);
            piece.appendRenderCommands(new ArrayList<>());
            verify(renderer).drawFrameIndex(kind.frame, camera.getX() + origin + 16,
                    camera.getY() + kind.screenY, false, false, 0);
        }
        // A donated character's life icon belongs to the lives piece and follows the same anchor.
        verify(donorLife).drawFrame(0, camera.getX() + origin + 16,
                camera.getY() + CdHud.Kind.LIVES.screenY, false, false);
        verifyNoMoreInteractions(donorLife);
        clearInvocations(renderer);
        var icon = ObjectConstructionContext.construct(services, CdTimeIcon::new);
        icon.setServices(services);
        var visible = CdTimeIcon.class.getDeclaredField("visible");
        visible.setAccessible(true);
        visible.setBoolean(icon, true);
        icon.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawFrameIndex(anyInt(), eq(camera.getX() + origin + CdTimeIcon.SCREEN_X),
                eq(camera.getY() + CdTimeIcon.SCREEN_Y), eq(false), eq(false), anyInt());

        int centre = (width - 320) / 2 - CameraPresentationAccess.inset(camera);
        var title = ObjectConstructionContext.construct(services, CdTitleCard::parent);
        var titleRoutine = CdTitleCard.class.getDeclaredField("routine");
        titleRoutine.setAccessible(true);
        titleRoutine.set(title, titleRoutine.getType().getEnumConstants()[1]);
        var results = ObjectConstructionContext.construct(services, () -> new CdResults(0, 0, 0));
        setInt(results, "routine", 6);
        var gameOver = ObjectConstructionContext.construct(services,
                () -> new CdGameOver(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        setInt(gameOver, "routine", 2);
        for (var card : new CdObject[] {title, results, gameOver}) {
            card.setServices(services);
            clearInvocations(renderer);
            card.appendRenderCommands(new ArrayList<>());
            verify(renderer).drawFrameIndex(anyInt(), eq(camera.getX() + centre + card.getX() - 128),
                    anyInt(), eq(false), eq(false), anyInt());
        }
    }
    private static void setInt(Object target, String name, int value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
