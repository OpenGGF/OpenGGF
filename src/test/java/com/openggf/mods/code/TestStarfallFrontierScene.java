package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Native ROM decoding plus real scene input and owner boundaries; model tests live with the mod. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarfallFrontierScene {
    @TempDir Path work;
    @Test void realRomHeroPanelsCavernsAndWardensDrawWithoutOwnerFaults() throws Exception {
        SharedLevel level=SharedLevel.load(SonicGame.SONIC_3K,0,0);
        try(ExampleModHarness harness=ExampleModHarness.build(Path.of("examples/starfall-frontier"),work.resolve("build"))) {
            harness.open(harness.apply(GameServices.module()),work.resolve("saves"),528,224);
            harness.host().draw(null,null);
            harness.press(GLFW_KEY_ENTER);
            assertEquals("PLAY",harness.scene().getClass().getMethod("screen").invoke(harness.scene()));
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);
            for(int i=0;i<90;i++)harness.tick();
            harness.input().handleKeyEvent(GLFW_KEY_RIGHT,GLFW_RELEASE);harness.tick();
            Object world=harness.scene().getClass().getMethod("world").invoke(harness.scene());
            var type=world.getClass();
            @SuppressWarnings("unchecked") var foes=(java.util.List<Object>)type.getField("enemies").get(world);
            var enemy=type.getClassLoader().loadClass("starfall.World$Enemy").getConstructor(double.class,double.class,int.class);
            double x=type.getField("x").getDouble(world),y=type.getField("y").getDouble(world);
            for(int kind=0;kind<3;kind++)foes.add(enemy.newInstance(x+35+kind*22,y,kind));
            for(int i=0;i<30;i++){harness.tick();harness.host().draw(null,null);}

            for(String command:List.of("craft","inventory","journal","map","cavern","warden","victory")) {
                assertTrue(harness.debugJump(command));for(int i=0;i<15;i++)harness.tick();
                harness.host().draw(null,null);
                assertFalse(harness.host().recordedFrame().isEmpty());
            }
            var vf=harness.scene().getClass().getDeclaredField("view");vf.setAccessible(true);
            Object view=vf.get(harness.scene());var af=view.getClass().getDeclaredField("art");af.setAccessible(true);
            Object art=af.get(view);
            var banksField=view.getClass().getDeclaredField("biomes");banksField.setAccessible(true);
            Object[][] banks=(Object[][])banksField.get(view);
            for(String name:List.of("angel_island","marble_garden","mushroom_hill","carnival_night","icecap",
                    "sandopolis","launch_base","hydrocity","lava_reef","hidden_palace","sky_sanctuary")) {
                assertTrue(harness.debugJump("biome-"+name));
                harness.host().draw(null,null);
                assertEquals(name.toUpperCase(java.util.Locale.ROOT),type.getMethod("region").invoke(world).toString());
                assertFalse(harness.host().recordedFrame().isEmpty());
                Object region=type.getMethod("region").invoke(world);
                int ordinal=((Enum<?>)region).ordinal();
                Object bank=banks[ordinal][0];
                var backdrop=(com.openggf.mods.scene.SceneBackdrop)(bank==null?
                        art.getClass().getField("backdrop").get(art):bank.getClass().getField("backdrop").get(bank));
                assertNotNull(backdrop,name+" has native scenery");
                if(name.equals("icecap"))assertTrue(java.util.Arrays.stream(backdrop.image().pixels()).anyMatch(pixel->
                        (pixel>>>16&255)>200&&(pixel>>>8&255)>200&&(pixel&255)>200),"outdoor snowy mountains use the intro palette");
                assertTrue(java.util.Arrays.stream(backdrop.image().pixels()).distinct().count()>1,name+" is not a flat placeholder");
                assertTrue(harness.host().recordedFrame().stream().anyMatch(op->op.image()==backdrop.image()),
                        name+" renders the decoded ROM background");
            }
            // Exercise the real update/draw path while crossing a surface boundary.
            harness.debugJump("biome-angel_island");
            type.getField("x").setDouble(world,80*12+6);
            type.getField("y").setDouble(world,((int)type.getMethod("surface",int.class).invoke(world,80)-2)*12);
            for(int n=0;n<22;n++)harness.tick();
            var gardenBackdrop=(com.openggf.mods.scene.SceneBackdrop)banks[1][0].getClass().getField("backdrop").get(banks[1][0]);
            harness.host().draw(null,null);
            var partial=harness.host().recordedFrame().stream().filter(op->op.image()==gardenBackdrop.image()).toList();
            assertFalse(partial.isEmpty());
            assertTrue(harness.host().recordedFrame().stream().filter(op->op.image()!=null&&op.image().width()==16&&op.image().height()==16)
                    .allMatch(op->op.tint()>>>24==255),"terrain tiles do not inherit the background fade");
            for(var op:partial)assertTrue((op.tint()>>>24)>100&&(op.tint()>>>24)<150,"mid-fade incoming alpha");
            var blendField=view.getClass().getDeclaredField("background");blendField.setAccessible(true);
            Object blend=blendField.get(view);var weight=blend.getClass().getDeclaredMethod("weight",int.class);weight.setAccessible(true);
            double before=(double)weight.invoke(blend,2);
            for(int n=0;n<20;n++)harness.host().draw(null,null);
            assertEquals(before,(double)weight.invoke(blend,2),"draws never advance the fade");
            harness.press(GLFW_KEY_P);for(int n=0;n<20;n++)harness.tick();
            assertEquals(before,(double)weight.invoke(blend,2),"pause freezes the transition");
            harness.press(GLFW_KEY_BACKSPACE);for(int n=0;n<45;n++)harness.tick();
            assertEquals(1,(double)weight.invoke(blend,2));
            // Check the actual rendered UVs, with velocity deliberately opposing target direction.
            x=type.getField("x").getDouble(world);y=type.getField("y").getDouble(world);
            for(int kind=0;kind<4;kind++) {
                var sprites=(com.openggf.mods.scene.SceneSpriteSet)art.getClass().getField(
                        new String[]{"rhinobot","monkeys","blooms","ship"}[kind]).get(art);
                var images=new java.util.HashSet<com.openggf.mods.scene.SceneImage>();
                for(int n=0;n<sprites.frameCount();n++)images.add(sprites.frame(n).image());
                for(int side:new int[]{-1,1}) {
                    foes.clear();Object foe=enemy.newInstance(x+side*35,y,kind);
                    foe.getClass().getField("vx").setDouble(foe,-side*2);
                    if(kind==3)foe.getClass().getField("shrine").setInt(foe,0);
                    foes.add(foe);
                    var phase=harness.scene().getClass().getDeclaredField("presentation");phase.setAccessible(true);
                    for(int tick=0;tick<48;tick++) {
                        phase.setInt(harness.scene(),tick);harness.host().draw(null,null);
                        var rendered=harness.host().recordedFrame().stream().filter(op->images.contains(op.image())).toList();
                        assertFalse(rendered.isEmpty());
                        for(var op:rendered) {
                            assertEquals(side<0,op.u0()>op.u1(),"native left pose flips only toward the right");
                            if(kind==0)assertTrue(op.image()==sprites.frame(0).image()||op.image()==sprites.frame(1).image(),
                                    "Rhinobot's reverse-facing frame 3 is not a locomotion pose");
                        }
                    }
                }
            }
            assertTrue(harness.findings().isEmpty(),harness.findings()::toString);
        }finally{level.dispose();}
    }
}
