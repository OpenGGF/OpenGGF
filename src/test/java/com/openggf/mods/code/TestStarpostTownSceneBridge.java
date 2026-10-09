package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import com.openggf.level.objects.ObjectServices;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the E1-independent binder/hand-back dispatcher; it does not simulate startAct/resume.
 * Origin: Starpost real-town lane, 2026-10-09. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostTownSceneBridge {
    @TempDir Path work;
    SharedLevel level;
    @org.junit.jupiter.api.AfterEach void dispose() { if (level!=null) level.dispose(); }
    @Test void doorsRetainTheLiveGameAndAcceptedFestivalStartsWithoutAnotherQuestion() throws Exception {
        level=SharedLevel.load(SonicGame.SONIC_3K,0,0);
        try (ExampleModHarness harness=ExampleModHarness.build(Path.of("examples/starpost-valley"),work.resolve("build"))) {
            var effective=harness.apply(GameServices.module());
            harness.open(effective,work.resolve("saves"),400,224);
            for (String door:List.of("seed_stall","inn","workshop","museum","robomart","ruins","capsule","lake","board","farm_gate","inventory")) {
                assertTrue(harness.debugJump("new sonic"),"Sonic 1 must be supplied");
                assertTrue(harness.debugJump("valley 896"));
                Object scene=harness.scene();
                Object town=TestStarpostTownAct.call(scene,"prepareTownAct");
                Object game=TestStarpostTownAct.call(town,"game");
                if (door.equals("museum")) {
                    assertTrue(harness.debugJump("museum fill relics 5"));
                    render(town,"museum-restored",1283);
                    render(town,"board-and-shelf",900);
                }
                TestStarpostTownAct.call(town,"request",door,null,896,173);
                TestStarpostTownAct.call(scene,"resumeTownAct");
                assertSame(game,TestStarpostTownAct.call(town,"game"),door+" retains the live game");
                assertNull(TestStarpostTownAct.call(town,"handBack"));
                TestStarpostTownAct.call(scene,"resumeTownAct"); // consumed request is harmless
                play(harness,35);
                assertEquals(Map.of(),harness.findings(),door);
            }
            assertTrue(harness.debugJump("new sonic"));
            assertTrue(harness.debugJump("festival day ring_hunt"));
            Object scene=harness.scene(),town=TestStarpostTownAct.call(scene,"prepareTownAct");
            render(town,"festival-dressing",712);
            TestStarpostTownAct.call(town,"request","festival",null,712,173);
            TestStarpostTownAct.call(scene,"resumeTownAct"); play(harness,40);
            var shellField=scene.getClass().getDeclaredField("shell"); shellField.setAccessible(true);
            Object shell=shellField.get(scene);
            assertEquals("RingHuntScreen",TestStarpostTownAct.call(shell,"screen").getClass().getSimpleName(),
                "accepted level invitation starts the existing event, not a second Ask");
            assertEquals(Map.of(),harness.findings()); assertEquals(List.of(),harness.exits());
        }
    }
    /** ROM facade/board/festival CPU witnesses through the real bridge's presentation. */
    private void render(Object town,String name,int x) throws Exception {
        var graphics=new TestStarpostTownAct.RectangleWitness();
        var camera=GameServices.camera(); camera.setX((short)(x-200)); camera.setY((short)0);
        ObjectServices services=(ObjectServices)java.lang.reflect.Proxy.newProxyInstance(
            ObjectServices.class.getClassLoader(),new Class<?>[]{ObjectServices.class},(proxy,method,args)->switch(method.getName()) {
                case "camera" -> camera; case "graphicsManager" -> graphics;
                default -> throw new AssertionError("Unexpected presentation service: "+method.getName());
            });
        Object presentation=TestStarpostTownAct.call(town,"presentation"),before=TestStarpostTownAct.call(town,"capture");
        graphics.paint.setColor(new java.awt.Color(0x5068C0)); graphics.paint.fillRect(0,0,400,224);
        graphics.paint.setColor(new java.awt.Color(0x306830)); graphics.paint.fillRect(0,192,400,32);
        TestStarpostTownAct.call(presentation,"decoration",services,town,false);
        var doors=(List<?>)TestStarpostTownAct.field(TestStarpostTownAct.call(town,"layout"),"doors");
        for (Object door:doors) TestStarpostTownAct.call(presentation,"door",services,town,door);
        TestStarpostTownAct.call(presentation,"decoration",services,town,true);
        TestStarpostTownAct.call(presentation,"draw",services,town);
        assertTrue(graphics.rectangles>100);
        assertEquals(before,TestStarpostTownAct.call(town,"capture"),"repeated dressing draw is rule-neutral");
        String external=System.getProperty("starpost.town.capture.dir");
        Path directory=external==null?work.resolve("pictures").toAbsolutePath():Path.of(external);
        assertTrue(directory.isAbsolute()); java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(graphics.image,"png",directory.resolve(name+".png").toFile()); graphics.paint.dispose();
    }
    private static void play(ExampleModHarness harness,int count) {
        for (int i=0;i<count;i++) { harness.tick(); harness.host().draw(null,null); }
    }
}
