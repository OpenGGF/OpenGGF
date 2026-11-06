package com.openggf.net.host;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.Protocol;
import com.openggf.net.protocol.AuthenticationTranscript;
import com.openggf.net.client.ClientHandshake;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.flow.WorkBudget;
import com.openggf.net.host.jdk.JdkRaceHostServer;
import com.openggf.net.host.jdk.JdkHostLimits;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.TrackValidationProfileSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@Timeout(30)
class TestHostMasterLink {
 @TempDir Path dir;
 private static ControlMessage.RelayGuestOpen open(int guestId) {
  return new ControlMessage.RelayGuestOpen(guestId, "room", "instance",
    "guest-fingerprint", "grant", 1, 1);
 }
 @Test void localCloseNotifiesRoomExactlyOnce() {
  List<HubConnection> connected=new ArrayList<>(),disconnected=new ArrayList<>();
  var link=new HostMasterLink(connected::add,(c,t)->{},(c,b)->{},disconnected::add,new HostMasterLink.MessageSink(){
   public void sendControl(ControlMessage m){} public void sendBinary(byte[] b){}
  });
  link.onMasterControl(open(7));
  connected.getFirst().close("sanction");
  link.onMasterControl(new ControlMessage.RelayGuestClose(7,"closed"));
  assertEquals(connected,disconnected);
 }

 @Test void masterLossRetiresEveryTunnelOnceAndRejectsLateOpen() {
  List<HubConnection> connected=new ArrayList<>(),disconnected=new ArrayList<>();
  var link=new HostMasterLink(connected::add,(c,t)->{},(c,b)->{},disconnected::add,new HostMasterLink.MessageSink(){
   public void sendControl(ControlMessage m){} public void sendBinary(byte[] b){}
  });
  link.onMasterControl(open(7));
  link.onMasterControl(open(8));
  link.onMasterLost();
  link.onMasterLost();
  link.onMasterControl(open(9));
  assertEquals(2, connected.size());
  assertEquals(2, disconnected.size());
  assertEquals(new HashSet<>(connected), new HashSet<>(disconnected));
 }

 @Test void unrelatedProcessSaturationWaitsForTunnelControlsWithoutLosingTheMaster() throws Exception {
  List<HubConnection> connected=new CopyOnWriteArrayList<>(),disconnected=new CopyOnWriteArrayList<>();
  List<ControlMessage> outbound=new CopyOnWriteArrayList<>();
  var link=new HostMasterLink(connected::add,(c,t)->{},(c,b)->{},disconnected::add,
    new HostMasterLink.MessageSink(){
     public void sendControl(ControlMessage m){outbound.add(m);}public void sendBinary(byte[] b){}
    });
  link.onMasterControl(open(7));
  try {
   for(ControlMessage control:List.of(
       new ControlMessage.RoomRouteRetireNotice("room","instance","grant",1,1),open(8))) {
    WorkBudget.Account holder=WorkBudget.PROCESS.account(8192,1);
    WorkBudget.Reservation filled=WorkBudget.PROCESS.tryReserve(holder,holder,
      new WorkBudget.Cost(8192-WorkBudget.PROCESS.retainedItems(),0)).orElseThrow();
    CountDownLatch started=new CountDownLatch(1);
    CompletableFuture<Void> handling=CompletableFuture.runAsync(()->{
     started.countDown();link.onMasterControl(control);
    },command->Thread.ofVirtual().start(command));
    try {
     assertTrue(started.await(3,TimeUnit.SECONDS));
     assertThrows(TimeoutException.class,()->handling.get(100,TimeUnit.MILLISECONDS),
       "an unrelated full process ledger must pause this control instead of dropping it");
     assertTrue(disconnected.isEmpty());
     assertTrue(outbound.stream().noneMatch(m->m instanceof ControlMessage.RelayGuestClose));
    } finally { filled.close(); }
    handling.get(3,TimeUnit.SECONDS);
   }
   assertEquals(2,connected.size());
   assertTrue(disconnected.isEmpty());
  } finally { link.onMasterLost(); }
 }

 @Test void staleTunnelCannotSendToOrCloseItsReplacement() {
  List<HubConnection> connected=new ArrayList<>();
  List<ControlMessage> controls=new ArrayList<>();
  List<byte[]> binary=new ArrayList<>();
  var link=new HostMasterLink(connected::add,(c,t)->{},(c,b)->{},c->{},new HostMasterLink.MessageSink(){
   public void sendControl(ControlMessage m){controls.add(m);} public void sendBinary(byte[] b){binary.add(b);}
  });
  try {
   link.onMasterControl(open(7));
   HubConnection old=connected.getFirst();
   link.onMasterControl(open(7));
   controls.clear();
   old.sendText("late"); old.sendBinary(new byte[]{1}); old.close("late");
   assertTrue(controls.isEmpty(),"a retired incarnation cannot publish to its replacement");
   assertTrue(binary.isEmpty());
   connected.getLast().sendText("current");
   assertEquals(List.of(new ControlMessage.RelayGuestText(7,"current")),controls);
  } finally { link.onMasterLost(); }
 }

 @Test void blockedTunnelOwnerRefusesExcessRetainedBytesBeforeItsItemLimit() throws Exception {
  try(var f=new Fixture(dir)) {
   f.admit();
   CountDownLatch release=f.block();
   try {
    String payload="x".repeat(Protocol.MAX_CONTROL_BYTES);
    for(int i=0;i<129;i++) f.link.onMasterControl(new ControlMessage.RelayGuestText(7,
      new String(payload.toCharArray())));
    assertTrue(f.outbound.stream().anyMatch(ControlMessage.RelayGuestClose.class::isInstance),
      "the byte limit must retire the tunnel before 129 maximum-sized owner actions accumulate");
    assertTrue(WorkBudget.PROCESS.retainedBytes()<=8L*1024*1024);
   } finally { release.countDown(); }
  }
 }

 @Test void oneFloodingTunnelCannotConsumeAnotherGuestsOwnerCapacity() throws Exception {
  try(var f=new Fixture(dir)) {
   f.admit();
   f.link.onMasterControl(f.open(8));
   CountDownLatch release=f.block();
   try {
    for(int i=0;i<513;i++) f.link.onMasterControl(new ControlMessage.RelayGuestText(7,"queued"));
    assertTrue(f.outbound.stream().anyMatch(message -> message instanceof ControlMessage.RelayGuestClose close
      && close.guestId()==7),"the flooding guest must retire");
    f.link.onMasterControl(new ControlMessage.RelayGuestText(8,"one message"));
    assertFalse(f.outbound.stream().anyMatch(message -> message instanceof ControlMessage.RelayGuestClose close
      && close.guestId()==8),"another guest retains its own owner capacity");
   } finally { release.countDown(); }
  }
 }

 @Test void fullDirectRoomMailboxDefersAnInnocentTunnelGuestUntilDrain() throws Exception {
  try(var f=new Fixture(dir)) {
   f.admit();
   CountDownLatch release=f.block();
   try {
    int queued=0;
    while(queued<512) {
     try { f.host.execute(()->{}); queued++; }
     catch(IllegalStateException full) { break; }
    }
    assertEquals(511,queued,"one executing action and 511 queued direct actions fill the room");
    f.link.onMasterControl(new ControlMessage.RelayGuestText(7,
      f.codec.encode(f.token,new ControlMessage.Chat("tunnel survives"))));
    assertFalse(f.outbound.stream().anyMatch(message -> message instanceof ControlMessage.RelayGuestClose close
      && close.guestId()==7),"shared direct-peer congestion cannot retire the tunnel guest");
   } finally { release.countDown(); }
   assertEquals("tunnel survives",f.await(ControlMessage.ChatBroadcast.class).text());
  }
 }

 @Test void tunnelDisconnectKeepsItsPreallocatedOwnerCellWhenTheProcessIsFull() throws Exception {
  try(var f=new Fixture(dir)) {
   f.admit();
   CountDownLatch release=f.block();
   CompletableFuture<Integer> observed=new CompletableFuture<>();
   f.host.execute(()->observed.complete(f.host.room().playerCount()));
   List<WorkBudget.Reservation> occupied=new ArrayList<>();
   try {
    var filler=WorkBudget.PROCESS.account(8192,1);
    for(;;){var r=WorkBudget.PROCESS.tryReserve(filler,filler,new WorkBudget.Cost(1,0));
     if(r.isEmpty())break; occupied.add(r.get());}
    assertDoesNotThrow(()->f.link.onMasterControl(new ControlMessage.RelayGuestClose(7,"left")));
    release.countDown();
    assertEquals(0,observed.get(3,TimeUnit.SECONDS));
   } finally { release.countDown(); occupied.forEach(WorkBudget.Reservation::close); }
  }
 }

 @Test void hostCloseDiscardsChargedTunnelPayloadBeforeTheOwnerYields() throws Exception {
  try(var f=new Fixture(dir)) {
   f.admit();
   CountDownLatch release=f.block();
   try {
    String payload="x".repeat(Protocol.MAX_CONTROL_BYTES);
    for(int i=0;i<16;i++)f.link.onMasterControl(new ControlMessage.RelayGuestText(7,
      new String(payload.toCharArray())));
    assertTrue(WorkBudget.PROCESS.retainedBytes()>0,"queued tunnel payload must remain charged");
    f.host.close();
    assertEquals(0,WorkBudget.PROCESS.retainedBytes(),"discard must release without running the callback");
   } finally { release.countDown(); }
  }
 }

 @Test void hostCloseDiscardsPendingTunnelOpenAndItsCleanupCell() throws Exception {
  try(var f=new Fixture(dir)) {
   CountDownLatch release=f.block();
   try {
    f.link.onMasterControl(f.open(7));
    assertEquals(f.before+5,WorkBudget.PROCESS.retainedItems(),
      "executing action, tunnel open, cleanup cell, and host retirement/profile cells are charged");
    f.host.close();
    assertEquals(f.before+2,WorkBudget.PROCESS.retainedItems(),
      "the executing owner action and profile cell remain until owner retirement");
   } finally { release.countDown(); }
  }
 }

 private static final class Fixture implements AutoCloseable {
  final BlockingQueue<ControlMessage> outbound=new LinkedBlockingQueue<>();
  final List<CountDownLatch> releases=new ArrayList<>();
  final ControlJsonCodec codec=new ControlJsonCodec();
  final JdkRaceHostServer host;
  final HostMasterLink link;
  final PlayerIdentity guest;
  String token;
  final int before=WorkBudget.PROCESS.retainedItems();
  Fixture(Path dir)throws Exception {
   PlayerIdentity owner=PlayerIdentity.loadOrCreate(dir.resolve("host"));
   guest=PlayerIdentity.loadOrCreate(dir.resolve("guest"));
   var d=JdkHostLimits.defaults();
   var limits=new JdkHostLimits(d.maxConnectionsPerIp(),d.maxConnections(),d.handshakeTimeoutMillis(),
     d.idleTimeoutMillis(),d.tickMillis(),d.closeGraceMillis(),100,d.maxRequestLineBytes(),
     d.maxRequestHeaderBytes(),d.maxFramePayloadBytes(),d.maxMessageBytes(),d.maxBinaryMessageBytes(),
     d.maxQueuedOutboundBytes());
   host=JdkRaceHostServer.start(0,new RoomHostConfig("Room","s3k",0,0,"OPEN",null,8,"build"),
     owner,TrackValidationProfileSource.none(),System::currentTimeMillis,false,
     limits);
   link=HostMasterLink.forServer(host,new HostMasterLink.MessageSink(){
    public void sendControl(ControlMessage m){outbound.add(m);}public void sendBinary(byte[] b){}
   });
   HostLinkAuthentication.bind(link,()->AuthenticationTranscript.LOCAL_PLAINTEXT);
  }
  void admit()throws Exception {
   var handshake=new ClientHandshake(guest,"Guest","","build",null,
     AuthenticationTranscript.Role.ROOM_RELAY,()->AuthenticationTranscript.LOCAL_PLAINTEXT);
   link.onMasterControl(open(7));
   link.onMasterControl(new ControlMessage.RelayGuestText(7,codec.encode(null,handshake.hello())));
   var welcome=await(ControlMessage.Welcome.class);
   link.onMasterControl(new ControlMessage.RelayGuestText(7,codec.encode(null,handshake.onWelcome(welcome))));
   ControlMessage.JoinAccepted accepted=await(ControlMessage.JoinAccepted.class);
   assertEquals(0,accepted.playerSlot());
   token=accepted.sessionToken();
  }
  ControlMessage.RelayGuestOpen open(int guestId) {
   return new ControlMessage.RelayGuestOpen(guestId,"room",host.room().instanceId(),
     guest.fingerprint(),"grant",1,1);
  }
  <T extends ControlMessage>T await(Class<T> type)throws Exception {
   for(int i=0;i<32;i++){
    ControlMessage envelope=outbound.poll(3,TimeUnit.SECONDS);
    assertNotNull(envelope,"missing "+type.getSimpleName());
    if(envelope instanceof ControlMessage.RelayGuestText text){
     ControlMessage message=codec.decode(text.text()).message();
     if(type.isInstance(message))return type.cast(message);
    }
   }
   throw new AssertionError("reply budget exceeded for "+type.getSimpleName());
  }
  CountDownLatch block()throws Exception {
   CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
   releases.add(release);
   host.execute(()->{
    entered.countDown(); boolean interrupted=false;
    while(release.getCount()!=0)try{release.await();}catch(InterruptedException ignored){interrupted=true;}
    if(interrupted)Thread.currentThread().interrupt();
   });
   assertTrue(entered.await(3,TimeUnit.SECONDS));return release;
  }
  public void close()throws Exception {
   releases.forEach(CountDownLatch::countDown);
   link.onMasterLost();host.close();
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
   while(!host.retirementReceipt().authorityRetired()&&System.nanoTime()<deadline)
    TimeUnit.MILLISECONDS.sleep(10);
   assertTrue(host.retirementReceipt().authorityRetired());
   assertEquals(before,WorkBudget.PROCESS.retainedItems());
  }
 }
}
