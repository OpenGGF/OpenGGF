package com.openggf.net.protocol;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TestCompetitionIntegrationWire {
    @Test void startDelayAndTaintRoundTripAndInvalidDelayFuzz() {
        var codec = new ControlJsonCodec();
        for(int delay : List.of(5,10,15)) {
            var message = new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig("s3k",0,0,60,"OPEN",null,"ARENA","COMPETITION","ITEMS",60,delay));
            assertEquals(message,codec.decode(codec.encode(null,message)).message());
        }
        var valid = codec.encode(null,new ControlMessage.RoundConfigure(new ControlMessage.RoundConfig("s3k",0,0,60,"OPEN",null,"ARENA","COMPETITION","ITEMS",60,5)));
        for(int delay=-10;delay<100;delay++) {
            if(delay==5||delay==10||delay==15) continue;
            String corrupted = valid.replace("\"startInSeconds\":5", "\"startInSeconds\":"+delay);
            assertThrows(ProtocolViolationException.class,()->codec.decode(corrupted));
        }
        var tainted = new ControlMessage.StandingsPage(List.of(new ControlMessage.StandingsRow(0,"P","sonic",100,1,"UNVERIFIED","FINISHED",20,List.of(20,20,20,20,20),true)),0,1);
        assertEquals(tainted,codec.decode(codec.encode(null,tainted)).message());
        var fallback = new ControlMessage.TeleportNoPartner(1234,1,50);
        assertEquals(fallback,codec.decode(codec.encode(null,fallback)).message());
    }
}
