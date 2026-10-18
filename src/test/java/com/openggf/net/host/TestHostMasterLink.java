package com.openggf.net.host;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TestHostMasterLink {
 @Test void localCloseNotifiesRoomExactlyOnce() {
  List<HubConnection> connected=new ArrayList<>(),disconnected=new ArrayList<>();
  var link=new HostMasterLink(connected::add,(c,t)->{},(c,b)->{},disconnected::add,new HostMasterLink.MessageSink(){
   public void sendControl(ControlMessage m){} public void sendBinary(byte[] b){}
  });
  link.onMasterControl(new ControlMessage.RelayGuestOpen(7));
  connected.getFirst().close("sanction");
  link.onMasterControl(new ControlMessage.RelayGuestClose(7,"closed"));
  assertEquals(connected,disconnected);
 }
}
