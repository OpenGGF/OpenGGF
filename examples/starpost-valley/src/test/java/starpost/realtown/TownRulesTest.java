package starpost.realtown;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import starpost.core.*;
import starpost.people.*;
import starpost.valley.Pickups;
import starpost.festivals.Festivals;

class TownRulesTest {
    private TownSession town() {
        Catalog catalog=new Catalog();
        Game game=Game.fresh(catalog,17,"sonic");
        game.sections.add(new People()); game.sections.add(new Pickups());
        game.sections.add(new Skills()); game.sections.add(new Festivals());
        game.calendar.set(1,0,2,9*60);
        TownSession town=new TownSession(); town.bind(game,TownLayout.placeholder(),null);
        return town;
    }
    private void step(TownSession town,boolean act,boolean confirm,boolean choice) {
        town.input(act,false,confirm,choice,false,-1); town.tick(440,173,true); town.clearInput();
    }
    @Test void dialogueReusesPictureSpeechAndTranslator() {
        TownSession town=town(); town.game().inventory.select(11);
        town.talk("dandel");
        assertTrue(town.modal()); assertTrue(town.speech().inPictures());
        assertEquals(People.TALK_POINTS,town.people().bond("dandel").points);
        int time=town.game().calendar.minutes();
        for (int i=0;i<500;i++) step(town,false,false,false);
        assertEquals(time,town.game().calendar.minutes());
        step(town,true,false,false);
        assertFalse(town.modal());
        town.game().flags.add(People.TRANSLATOR); town.talk("dandel");
        assertFalse(town.speech().inPictures());
        assertEquals(People.TALK_POINTS,town.people().bond("dandel").points,"talk once daily");
    }
    @Test void giftsAskAndUseExistingRulesAndInventory() {
        TownSession town=town();
        town.game().inventory.set(0,"ring_radish",2); town.game().inventory.select(0);
        town.talk("dandel"); assertTrue(town.askingGift());
        step(town,true,false,false);
        assertEquals(1,town.game().inventory.total("ring_radish"));
        assertEquals(People.points(Taste.LOVE),town.people().bond("dandel").points);
        assertNotNull(town.speech());
        for (int i=0;i<60;i++) step(town,false,false,false);
        step(town,true,false,false);
        town.talk("dandel"); assertFalse(town.askingGift(),"one gift per day");
    }
    @Test void decliningGiftTalksWithoutRemovingIt() {
        TownSession town=town(); town.game().inventory.set(0,"ring_radish",1);
        town.talk("dandel"); step(town,false,false,true); step(town,true,false,false);
        assertEquals(1,town.game().inventory.total("ring_radish"));
        assertEquals(People.TALK_POINTS,town.people().bond("dandel").points);
    }
    @Test void pickupsShareRewardsAndFullBagDoesNotConsumeForage() {
        TownSession town=town(); Pickups pickups=town.pickups();
        var items=pickups.today(town.game(),town.layout().ground,town.layout().springX,town.layout().loopX);
        var ring=items.stream().filter(p->p.item()==null).findFirst().orElseThrow();
        town.game().momentum=10; int wallet=town.game().rings;
        assertTrue(pickups.collect(ring.index(),ring.item(),town.game()));
        assertFalse(pickups.collect(ring.index(),ring.item(),town.game()));
        assertEquals(wallet+1,town.game().rings); assertEquals(11,town.game().momentum);
        for (int i=0;i<town.game().inventory.size();i++) town.game().inventory.set(i,"ring_radish",999);
        var forage=items.stream().filter(p->p.item()!=null).findFirst().orElseThrow();
        assertFalse(pickups.collect(forage.index(),forage.item(),town.game()));
        assertFalse(pickups.taken(forage.index()));
    }
    @Test void immutableSnapshotRestoresLiveIdentityClockFractionRngAndDialogue() {
        TownSession town=town(); Game game=town.game(); People people=town.people();
        game.calendar.setDayMinutes(14);
        for (int i=0;i<419;i++) step(town,false,false,false);
        var before=town.capture();
        town.game().inventory.select(11); town.talk("dandel");
        game.rings+=20; game.flags.add(People.TRANSLATOR); game.rng.nextInt(100); game.waterCharges+=10;
        town.request("inn",null,896,173);
        town.restore(before);
        assertSame(game,town.game()); assertSame(people,town.people());
        assertEquals(before,town.capture(),"every captured value restores, not just totals");
        step(town,false,false,false);
        assertEquals(before.calendar().minutes()+10,game.calendar.minutes(),"partial clock tick restored");
        town.game().inventory.select(11); town.talk("dandel");
        var dialog=town.capture();
        step(town,true,false,false); town.restore(dialog);
        assertEquals(dialog,town.capture());
        assertThrows(UnsupportedOperationException.class,()->before.flags().add("fake"));
    }
    @Test void admissionAnchorsElectOneRewindableDirectorPerVisit() {
        TownSession town=town();
        var empty=town.capture();
        assertTrue(town.claimController(3)); assertFalse(town.claimController(2));
        var elected=town.capture();
        town.restore(empty); assertTrue(town.claimController(2));
        town.restore(elected); assertTrue(town.ownsController(3)); assertFalse(town.claimController(2));
        town.bind(town.game(),town.layout(),town.presentation());
        assertTrue(town.claimController(8)); assertFalse(town.claimController(3));
    }
    @Test void skyRowsTranslateArcsAndFloorPickupsWithoutChangingDailyIdentity() {
        TownSession town=town(); var layout=town.layout();
        var before=java.util.List.copyOf(town.pickups().today(town.game(),layout.ground,layout.springX,layout.loopX));
        town.pickups().collect(0,null,town.game());
        var bits=town.pickups().capture();
        var shifted=new starpost.valley.Ground() {
            public int left() {return layout.ground.left();}
            public int right() {return layout.ground.right();}
            public int originY() {return 128;}
            public boolean solid(int x,int y) {return layout.ground.solid(x,y-128);}
            public int floorBelow(int x,int y) {return layout.ground.floorBelow(x,Math.max(0,y-128))+128;}
        };
        town.pickups().placeOnGround(town.game(),shifted,layout.springX,layout.loopX);
        var after=town.pickups().today(town.game(),shifted,layout.springX,layout.loopX);
        assertEquals(bits,town.pickups().capture()); assertEquals(before.size(),after.size());
        for(int i=0;i<before.size();i++) {
            assertEquals(before.get(i).x(),after.get(i).x());
            assertEquals(before.get(i).y()+128,after.get(i).y(),0.0001f);
            assertEquals(before.get(i).item(),after.get(i).item());
        }
    }
    @Test void handBackIsLatchedAndConsumedOnce() {
        TownSession town=town(); var before=town.capture();
        town.request("inn",null,896,173); town.request("ruins",null,3000,173);
        assertEquals("inn",town.handBack().place());
        assertEquals("896",town.handBack().payload().get("town.returnX"));
        var pending=town.capture(); town.consumeHandBack(); assertNull(town.consumeHandBack());
        town.restore(pending); assertEquals(pending.handBack(),town.handBack());
        town.restore(before); assertNull(town.handBack()); assertFalse(town.modal());
    }
    @Test void festivalInvitationUsesGatheringAndOncePerArrival() {
        TownSession town=town(); town.game().calendar.set(1,0,13,9*60);
        town.tick(town.layout().anchor("plaza"),173,true); assertTrue(town.invited());
        town.input(false,false,false,true,false,-1); town.tick(712,173,true); town.clearInput();
        town.input(true,false,false,false,false,-1); town.tick(712,173,true); town.clearInput();
        assertFalse(town.modal()); town.tick(712,173,true); assertFalse(town.invited());
        town.tick(440,173,true); town.tick(712,173,true); assertTrue(town.invited());
        town.input(true,false,false,false,false,-1); town.tick(712,173,true);
        assertEquals("festival",town.handBack().place());
    }
    @Test void separateSessionsAndDailyPickupsStayIsolated() {
        TownSession one=town(), two=town();
        one.talk("dandel"); assertFalse(two.people().bond("dandel").met);
        assertFalse(two.modal());
        one.pickups().collect(0,null,one.game());
        assertFalse(two.pickups().taken(0));
        one.consumeHandBack(); one.bind(one.game(),one.layout(),null);
        assertTrue(one.pickups().taken(0),"same-day return preserves collected rings");
        one.pickups().nextDay(one.game()); one.game().calendar.nextDay();
        one.bind(one.game(),one.layout(),null); assertFalse(one.pickups().taken(0));
    }
    @Test void inputFilterReservesPadBAndBlocksNativeMovementDuringDialogue() {
        TownSession town=town(); TownInput input=new TownInput(town);
        var raw=com.openggf.control.PlayerInputState.of(8,8,7,7,true,true);
        var filtered=input.filter(raw);
        assertEquals(raw.heldMask(),filtered.heldMask()); assertEquals(5,filtered.actionHeldMask());
        assertEquals(5,filtered.actionPressedMask()); assertTrue(filtered.startPressed());
        var onlyB=com.openggf.control.PlayerInputState.of(8,8,2,2,false,false);
        assertEquals(8,input.filter(onlyB).heldMask(),"pad B loses the native jump union");
        town.game().inventory.select(11); town.talk("dandel");
        assertEquals(com.openggf.control.PlayerInputState.neutral(),input.filter(raw));
        town.request("inn",null,896,173); town.consumeHandBack();
        assertSame(raw,input.filter(raw),"suspended town must not filter other acts");
    }

    @Test void realGroundReprojectsPickupsWithoutRespawningCollectedItems() {
        TownSession town=town(); town.pickups().collect(0,null,town.game());
        var ground=new starpost.valley.Ground() {
            public boolean solid(int x,int y) { return y>=208; }
            public int floorBelow(int x,int from) { return 208; }
            public int left() { return 0; }
            public int right() { return town.layout().ground.right(); }
        };
        town.pickups().placeOnGround(town.game(),ground,town.layout().springX,town.layout().loopX);
        assertEquals(192,town.pickups().today(town.game(),ground,town.layout().springX,town.layout().loopX).get(0).y());
        assertTrue(town.pickups().taken(0));
    }

}
