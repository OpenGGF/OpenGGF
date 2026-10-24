package com.openggf;

import com.openggf.editor.LevelEditorController;
import com.openggf.editor.project.EditorProjectDocument;
import com.openggf.editor.project.EditorProjectMetadata;
import com.openggf.game.GameModule;
import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.metadata.ObjectTableNameProvider;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestEngineEditorProjectObjectNames {
    @AfterEach
    void cleanup() { SessionManager.clear(); }

    @Test
    void absentTableMetadataKeepsDecoratedNamesAndDoesNotResolveRootRegistry() throws Exception {
        GameModule root = mock(GameModule.class);
        assertEquals("decorated", names(root, null).apply(0x3B));
        verify(root, never()).createObjectRegistry();
    }

    @Test
    void explicitTableWithoutRootCapabilityKeepsDecoratedNames() throws Exception {
        GameModule root = mock(GameModule.class);
        when(root.createObjectRegistry()).thenReturn(mock(ObjectRegistry.class));
        assertEquals("decorated", names(root, ModObjectZoneSet.SKL).apply(0x3B));
    }

    @Test
    void explicitTableUsesTheInternalRootCapabilityWithoutRequiringANativeConcreteClass() throws Exception {
        ObjectRegistry registry = mock(ObjectRegistry.class, withSettings().extraInterfaces(ObjectTableNameProvider.class));
        var tables = (ObjectTableNameProvider) registry;
        when(tables.objectTableName(0x3B, ModObjectZoneSet.SKL)).thenReturn("selected root table");
        GameModule root = mock(GameModule.class);
        when(root.createObjectRegistry()).thenReturn(registry);
        assertEquals("selected root table", names(root, ModObjectZoneSet.SKL).apply(0x3B));
        verify(tables).objectTableName(0x3B, ModObjectZoneSet.SKL);
    }

    @ParameterizedTest
    @EnumSource(ModObjectZoneSet.class)
    void explicitNativeTableIgnoresTheOppositeActiveLevelTable(ModObjectZoneSet table) throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        Sonic3kLevel level = mock(Sonic3kLevel.class);
        when(level.getObjectZoneSet()).thenReturn(table == ModObjectZoneSet.SKL ? S3kZoneSet.S3KL : S3kZoneSet.SKL);
        SessionManager.getCurrentGameplayMode().getWorldSession().setCurrentLevel(level);
        GameModule root = mock(GameModule.class);
        when(root.createObjectRegistry()).thenReturn(new Sonic3kObjectRegistry());
        assertEquals(table == ModObjectZoneSet.SKL ? "SOZLoopFallthrough" : "HCZWaterWall",
                names(root, table).apply(0x3B));
    }

    private IntFunction<String> names(GameModule root, ModObjectZoneSet table) throws Exception {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        GameModule base = mock(GameModule.class);
        ObjectRegistry decorated = mock(ObjectRegistry.class);
        when(decorated.getPrimaryName(anyInt())).thenReturn("decorated");
        when(base.createObjectRegistry()).thenReturn(decorated);
        LevelEditorController controller = mock(LevelEditorController.class);
        set(engine, "editorProjectRootModule", root);
        set(engine, "editorProjectBaseModule", base);
        set(engine, "levelEditorController", controller);
        EditorProjectDocument document = mock(EditorProjectDocument.class);
        EditorProjectMetadata metadata = mock(EditorProjectMetadata.class);
        when(document.metadata()).thenReturn(metadata);
        when(metadata.objectZoneSet()).thenReturn(table);
        var configure = Engine.class.getDeclaredMethod("configureEditorProjectSpawnEditing", EditorProjectDocument.class);
        configure.setAccessible(true);
        configure.invoke(engine, document);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<IntFunction<String>> names = ArgumentCaptor.forClass(IntFunction.class);
        verify(controller).configureSpawnEditing(eq(decorated), isNull(), names.capture());
        return names.getValue();
    }

    private static void set(Engine engine, String name, Object value) throws Exception {
        var field = Engine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }
}
