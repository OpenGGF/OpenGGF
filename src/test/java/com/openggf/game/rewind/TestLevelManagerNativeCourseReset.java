package com.openggf.game.rewind;

import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit host course metadata, never a gameplay/source/geometry ingress.
 * The same-World ACT allowance observes LevelManager's committed course position.
 * Reflection keeps the missing additive API a test failure on the pre-fix source.
 */
class TestLevelManagerNativeCourseReset {
    @BeforeEach void reset() { TestEnvironment.resetAll(); }

    @Test void existingNoArgumentResetKeepsItsZeroCourseContract() {
        var manager=GameServices.level();var world=TestEnvironment.activeGameplayMode().getWorldSession();
        manager.setApparentAct(9);manager.resetState();
        assertEquals(0,manager.getCurrentZone());assertEquals(0,manager.getCurrentAct());
        assertEquals(0,manager.getApparentAct());assertEquals(0,world.getCurrentZone());
        assertEquals(0,world.getCurrentAct());assertEquals(0,world.getApparentAct());
        assertNull(manager.getCurrentLevel());assertNull(world.getCurrentLevel());
    }

    @Test void admittedFreshCourseResetPublishesBothManagerAndSameWorld() throws Exception {
        var manager=GameServices.level();var world=TestEnvironment.activeGameplayMode().getWorldSession();
        resetTo(manager,3,2);
        assertSame(world,TestEnvironment.activeGameplayMode().getWorldSession());
        assertSame(manager,TestEnvironment.activeGameplayMode().getLevelManager());
        assertEquals(3,manager.getCurrentZone());assertEquals(3,world.getCurrentZone());
        assertEquals(2,manager.getCurrentAct());assertEquals(2,world.getCurrentAct());
        assertEquals(2,manager.getApparentAct());assertEquals(2,world.getApparentAct());
        assertNull(manager.getCurrentLevel());assertNull(world.getCurrentLevel());
    }

    @Test void actualCommittedCourseRefillsActAllowanceOnceWithoutRestoringSpentTime() throws Exception {
        var manager=GameServices.level();var world=TestEnvironment.activeGameplayMode().getWorldSession();
        var budget=new LiveRewindBudget();budget.configure("act","10");
        budget.observeMain(world,"engine",manager.getCurrentZone(),manager.getCurrentAct(),-1);
        budget.spend(180);assertEquals(420,budget.remainingFrames());
        resetTo(manager,0,2);
        budget.observeMain(world,"engine",manager.getCurrentZone(),manager.getCurrentAct(),-1);
        assertEquals(600,budget.remainingFrames(),"A genuine new act is a new allowance boundary");
        budget.spend(60);resetTo(manager,0,2);
        budget.observeMain(world,"engine",manager.getCurrentZone(),manager.getCurrentAct(),-1);
        assertEquals(540,budget.remainingFrames(),"Re-entering the same reached act grants no refill");
    }

    @Test void invalidCourseFailsBeforeAnyResetOrMetadataWrite() {
        var manager=GameServices.level();var world=TestEnvironment.activeGameplayMode().getWorldSession();
        manager.setApparentAct(9);
        for(int[] pair:new int[][]{{-1,2},{0,-1}}) {
            assertThrows(IllegalArgumentException.class,()->resetTo(manager,pair[0],pair[1]));
            assertEquals(0,manager.getCurrentZone());assertEquals(0,world.getCurrentZone());
            assertEquals(0,manager.getCurrentAct());assertEquals(0,world.getCurrentAct());
            assertEquals(9,manager.getApparentAct());assertEquals(9,world.getApparentAct());
        }
    }

    private static void resetTo(LevelManager manager,int zone,int act) throws Exception {
        java.lang.reflect.Method method;
        try { method=Class.forName("com.openggf.level.NativeLevelCourseReset").getMethod("reset",LevelManager.class,int.class,int.class); }
        catch(ClassNotFoundException | NoSuchMethodException missing) { throw new AssertionError("Missing manager-owned native course reset",missing); }
        assertTrue(method.isAnnotationPresent(com.openggf.game.EngineOnly.class),"Course assembly is engine-owned lifecycle plumbing, outside the creator API");
        try { method.invoke(null,manager,zone,act); }
        catch(InvocationTargetException stopped) {
            if(stopped.getCause() instanceof RuntimeException failure)throw failure;
            if(stopped.getCause() instanceof Error failure)throw failure;
            throw stopped;
        }
    }
}
