package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.objects.badniks.Sonic3kBadnikConfig;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Subtype-zero freed-animal component inlet on ROM AIZ2 terrain; no replay/trace bootstrap or state seeding. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreedAnimalRenderLifetime {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    @Test void naturalPopLandingFlightRetiresFromPreviousRenderThenReusesHoleAndReplaysTwice() throws Exception {
        var fixture=fixture(0x0D80,0x02DA);
        var manager=GameServices.level().getObjectManager();
        var lower=manager.createDynamicObject(() -> new Witness(1));
        var tail=manager.createDynamicObject(() -> new Witness(0));
        int released=lower.getSlotIndex();
        scan(fixture,3,true);
        assertNull(NativeSstSlotAccess.currentOwner(manager,released));
        int expectedFree=manager.firstFreeDynamicSlot();
        var animal=animal(manager,0x0E10,0x033C);
        int slot=animal.getSlotIndex();
        assertEquals(expectedFree,slot,"ordinary global allocation consumes a naturally free hole");
        assertTrue(slot<tail.getSlotIndex(),"later ordinary owner remains above the hole");
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry=fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot birth=registry.capture();
        boolean landed=false,inside=false;int passes=0;
        for(;passes<180;passes++) {
            scan(fixture,4+passes,true);
            var live=(AnimalObjectInstance)NativeSstSlotAccess.currentOwner(manager,slot);
            assertNotNull(live,"the natural pop/landing must reach its render edge");
            String state=field(live,"state").toString();
            landed|=state.equals("FLY");
            if(!nativeVisible(live,fixture))break;
            inside=true;
        }
        assertTrue(passes<180 && landed && inside,"ordinary pop must land and fly naturally through the left edge");
        int edgePasses=passes+1;
        CompositeSnapshot edge=registry.capture();
        AnimalObjectInstance edgeOwner=(AnimalObjectInstance)NativeSstSlotAccess.currentOwner(manager,slot);
        boolean edgeFlag=(Boolean)field(edgeOwner,"romRenderOnScreen");
        int edgeX=edgeOwner.getX();
        int edgeDx=(short)(edgeX-fixture.camera().getXCopy());
        scan(fixture,4+edgePasses,true);
        boolean retired=NativeSstSlotAccess.currentOwner(manager,slot)==null;
        int reused=retired?reuse(manager,slot):-1;
        CompositeSnapshot after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(birth);same(birth,registry.capture());
            for(int pass=0;pass<edgePasses+1;pass++)scan(fixture,4+pass,true);
            if(NativeSstSlotAccess.currentOwner(manager,slot)==null)reuse(manager,slot);
            same(after,registry.capture());
            registry.restore(edge);same(edge,registry.capture());
            scan(fixture,4+edgePasses,true);
            if(NativeSstSlotAccess.currentOwner(manager,slot)==null)reuse(manager,slot);
            same(after,registry.capture());
        }
        System.out.printf("ANIMAL_LIFETIME slot=%d edgePass=%d edgeX=%04X dx=%d cached=%s retired=%s reused=%d%n",slot,edgePasses,edgeX,edgeDx,edgeFlag,retired,reused);
        assertAll(
                ()->assertTrue(edgeDx < -8,"natural flight crosses the native width8 boundary"),
                ()->assertFalse(edgeFlag,"Render_Sprites must publish the outside flag before the next animal pass"),
                ()->assertTrue(retired,"loc_2CAD0 consumes previous bit7, not a fresh64-pixel margin"),
                ()->assertEquals(slot,reused,"the retired animal's actual hole is available to normal allocation"));
    }

    @Test void copiedRenderWordBoundsKeepHorizontalEdgesZeroHeightAndWrapThenMainPollsBeforeMotion() throws Exception {
        var fixture=fixture(0x7F00,0x0780);
        var manager=GameServices.level().getObjectManager();
        var animal=animal(manager,0x7FFC,0x07FC);
        int slot=animal.getSlotIndex();
        scan(fixture,3,true); // First own init draws and returns before any lifetime poll.
        assertSame(animal,NativeSstSlotAccess.currentOwner(manager,slot));
        record Case(String name,int dx,int dy,int wrap,boolean visible) { }
        List<Case> cases=List.of(
                new Case("left right-edge zero",-8,0,0,true),
                new Case("left outside",-9,0,0,false),
                new Case("right inside",327,0,0,true),
                new Case("right left-edge320",328,0,0,false),
                new Case("above zero-height",0,-1,0,false),
                new Case("top zero-height",0,0,0,true),
                new Case("bottom inside",0,223,0,true),
                new Case("bottom excluded",0,224,0,false),
                new Case("wrapped centre",0,0x800+12,0x800,true),
                new Case("negative wrapped centre",0,-0x800+12,0x800,true),
                new Case("wrapped outside",0,0x800-1,0x800,false),
                new Case("unwrapped same centre",0,0x800+12,0,false));
        List<org.junit.jupiter.api.function.Executable> checks=new ArrayList<>();
        for(Case c:cases) {
            fixture.camera().setVerticalWrapEnabled(c.wrap()!=0,c.wrap());
            fixture.camera().setXCopy((short)(animal.getX()-c.dx()));
            fixture.camera().setYCopy((short)(animal.getY()-c.dy()));
            // Deliberately different live words: Render_Sprites consumes the published copies.
            fixture.camera().setXAfterRenderCopy((short)(fixture.camera().getXCopy()+0x200));
            fixture.camera().setYAfterRenderCopy((short)(fixture.camera().getYCopy()+0x100));
            manager.refreshPostCameraRenderState();
            boolean actual=(Boolean)field(animal,"romRenderOnScreen");
            checks.add(()->assertEquals(c.visible(),actual,c.name()));
        }
        fixture.camera().setVerticalWrapEnabled(false);
        fixture.camera().setXCopy((short)animal.getX());
        fixture.camera().setYCopy((short)(animal.getY()+1));
        manager.refreshPostCameraRenderState(); // Native height_pixels=0 makes this outside.
        int x=animal.getX(),y=animal.getY();
        // Moving the camera back without a render does not rewrite the prior flag.
        fixture.camera().setX((short)0x7F00);fixture.camera().setY((short)0x0780);
        scan(fixture,4,false);
        checks.add(()->assertNull(NativeSstSlotAccess.currentOwner(manager,slot),"MAIN polls prior bit before motion"));
        checks.add(()->assertEquals(x,animal.getX(),"MAIN outside branch precedes X motion"));
        checks.add(()->assertEquals(y,animal.getY(),"MAIN outside branch precedes pop gravity/motion"));
        assertAll(checks);
    }

    private static HeadlessTestFixture fixture(int cameraX,int cameraY)throws Exception {
        SonicConfigurationService.getInstance().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,true);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(0,1).build();
        fixture.camera().setX((short)cameraX);fixture.camera().setY((short)cameraY);
        scan(fixture,1,true);scan(fixture,2,true);
        return fixture;
    }
    private static AnimalObjectInstance animal(ObjectManager manager,int x,int y) {
        return manager.createDynamicObject(()->(AnimalObjectInstance)Sonic3kBadnikConfig.destruction().animalFactory()
                .create(new ObjectSpawn(x,y,0x28,0,0,false,0),manager.getObjectServices()));
    }
    private static void scan(HeadlessTestFixture fixture,int pass,boolean render) {
        var manager=GameServices.level().getObjectManager();
        manager.update(fixture.camera().getX(),fixture.sprite(),List.of(),pass);
        if(render)manager.refreshPostCameraRenderState();
    }
    private static boolean nativeVisible(AnimalObjectInstance animal,HeadlessTestFixture fixture) {
        int dx=(short)(animal.getX()-fixture.camera().getXCopy());
        int dy=(animal.getY()-fixture.camera().getYCopy())&fixture.camera().screenYWrapValue();
        return dx+8>=0 && dx-8<320 && dy<224;
    }
    private static int reuse(ObjectManager manager,int hole) {
        for(int count=0;count<94;count++) {
            var witness=manager.createDynamicObject(()->new Witness(0));
            if(witness.getSlotIndex()>=hole)return witness.getSlotIndex();
        }
        throw new AssertionError("normal allocation never reached the retired hole");
    }
    private static Object field(Object owner,String name)throws Exception {
        var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.keyEquals(key,expected.get(key),actual.get(key)),
                ()->key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
    private static final class Witness extends AbstractObjectInstance implements SpawnRewindRecreatable {
        Witness(int kind){this(new ObjectSpawn(0,0,0,kind,0,false,0));}
        public Witness(ObjectSpawn spawn){super(spawn,"AnimalLifetimeWitness");setRomWorldPositioned(false);}
        @Override public boolean isPersistent(){return true;}
        @Override public void update(int frame,PlayableEntity player){if(getSpawn().subtype()==1)setDestroyed(true);}
        @Override public void appendRenderCommands(List<GLCommand> commands){ }
    }
}
