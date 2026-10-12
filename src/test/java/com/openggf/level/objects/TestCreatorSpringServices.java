package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestCreatorSpringServices {
    @Test void inclusiveRectangleWorksForMainAndSidekickAndUsesExistingSpringState() {
        for(String code:new String[]{"sonic","tails"}) {
            AbstractPlayableSprite player=mock(AbstractPlayableSprite.class);
            when(player.getCode()).thenReturn(code);when(player.getCentreX()).thenReturn((short)16);
            when(player.getCentreY()).thenReturn((short)8);when(player.getYSpeed()).thenReturn((short)256);
            assertTrue(SpringBounceHelper.tryLaunchUp(player,0,0,16,8,-0xA00,15));
            verify(player).setYSpeed((short)-0xA00);verify(player).setAir(true);verify(player).setOnObject(false);
            verify(player).setHurt(false);verify(player).setSpringing(15);
        }
    }
    @Test void outsideRisingDeadAndInvalidContactsCannotPartiallyLaunch() {
        AbstractPlayableSprite player=mock(AbstractPlayableSprite.class);
        when(player.getYSpeed()).thenReturn((short)256);when(player.getCentreX()).thenReturn((short)17);
        assertFalse(SpringBounceHelper.tryLaunchUp(player,0,0,16,8,-0xA00,15));
        when(player.getCentreX()).thenReturn((short)0);when(player.getYSpeed()).thenReturn((short)-1);
        assertFalse(SpringBounceHelper.tryLaunchUp(player,0,0,16,8,-0xA00,15));
        when(player.getYSpeed()).thenReturn((short)256);when(player.getDead()).thenReturn(true);
        assertFalse(SpringBounceHelper.tryLaunchUp(player,0,0,16,8,-0xA00,15));
        assertThrows(IllegalArgumentException.class,()->SpringBounceHelper.tryLaunchUp(player,0,0,257,8,-0xA00,15));
        assertThrows(IllegalArgumentException.class,()->SpringBounceHelper.launchUp(player,1,15));
        verify(player,never()).setYSpeed(anyShort());
    }
    @Test void detachmentAcceptsOnlyTheCurrentCheckpointObjectAndParticipant() {
        ObjectManager manager=mock(ObjectManager.class);ObjectSolidContactController contacts=mock(ObjectSolidContactController.class);
        when(manager.solidContacts()).thenReturn(contacts);
        ObjectInstance owner=mock(ObjectInstance.class);PlayableEntity main=mock(PlayableEntity.class),foreign=mock(PlayableEntity.class);
        ObjectSolidExecutionContext context=mock(ObjectSolidExecutionContext.class);when(context.object()).thenReturn(owner);
        var side=new PlayerSolidContactResult(ContactKind.SIDE,false,false,true,true,PreContactState.ZERO,PostContactState.ZERO,0);
        when(context.lastCheckpoint()).thenReturn(new SolidCheckpointBatch(owner,Map.of(main,side)));
        ObjectServices services=new StubObjectServices() {
            @Override public ObjectManager objectManager(){return manager;}
            @Override public ObjectSolidExecutionContext solidExecution(){return context;}
        };
        assertFalse(services.detachCurrentSolidContact(foreign));verifyNoInteractions(manager,contacts,foreign);
        assertTrue(services.detachCurrentSolidContact(main));verify(contacts).releaseObjectPushLatch(main,owner);
        verify(manager).clearRidingObjectForJump(main);verify(main).setPushing(false);clearInvocations(manager,contacts,main);
        when(context.lastCheckpoint()).thenReturn(new SolidCheckpointBatch(mock(ObjectInstance.class),Map.of(main,side)));
        assertFalse(services.detachCurrentSolidContact(main));verifyNoInteractions(manager,contacts,main);
    }
}
