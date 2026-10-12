package com.openggf.mods.code;

import com.openggf.io.ModAssetRoot;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.ModSceneFactory;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestModTitleEntryRegistration {
    private static final ModSceneFactory SCENE = () -> new ModScene() {
        @Override public void enter(SceneContext context) { }
        @Override public void update(SceneContext context) { }
        @Override public void draw(SceneContext context, SceneCanvas canvas) { }
    };

    @Test
    void anyGameModMayRegisterOnlyATitleEntry() {
        ModContext context = new ModContext("racing", "any", ModAssetRoot.forTests("racing"));
        context.registerTitleEntry("Time Attack", SCENE);
        ModRegistrationPlan plan = assertDoesNotThrow(context::freeze);
        assertFalse(plan.hasContent(), "a title entry is not gameplay content");
        assertNull(plan.startupScene(), "a title entry never replaces a game's title");
        assertEquals("Time Attack", context.titleEntry().label());
        assertSame(SCENE, context.titleEntry().factory());
    }

    @Test
    void patchModMayRegisterATitleEntryBesideItsContent() {
        ModContext context = new ModContext("drills", "s2", ModAssetRoot.forTests("drills"));
        context.registerTitleEntry("  Practice drills  ", SCENE);
        context.registerObject("marker", (spawn, registry) -> null);
        assertDoesNotThrow(context::freeze);
        assertEquals("Practice drills", context.titleEntry().label());
    }

    @Test
    void labelsAreBoundedAndNotBlank() {
        ModContext context = new ModContext("racing", "any", ModAssetRoot.forTests("racing"));
        context.registerTitleEntry("A very long title entry label indeed", SCENE);
        assertEquals(24, context.titleEntry().label().length());
        ModContext blank = new ModContext("racing", "any", ModAssetRoot.forTests("racing"));
        assertThrows(ModRegistrationException.class, () -> blank.registerTitleEntry("   ", SCENE));
    }

    @Test
    void oneEntryPerMod() {
        ModContext context = new ModContext("racing", "any", ModAssetRoot.forTests("racing"));
        context.registerTitleEntry("Time Attack", SCENE);
        assertThrows(ModRegistrationException.class, () -> context.registerTitleEntry("Again", SCENE));
    }

    @Test
    void anyGameModWithNothingRegisteredStillFails() {
        ModContext missing = new ModContext("racing", "any", ModAssetRoot.forTests("racing"));
        assertThrows(ModRegistrationException.class, missing::freeze);
    }
}
