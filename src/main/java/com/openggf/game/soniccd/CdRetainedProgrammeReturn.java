package com.openggf.game.soniccd;
import java.util.Objects;
import java.util.OptionalInt;
/** Shared retained programme return semantics. No World, clock, destination switch or invocation owner.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff General:651-658 ClearPLCs DBF leaves BYTEFF;
 * Palette Fade:120-139 ends its cleared-queue fade with the same BYTEFF; General:665-666
 * leaves an empty queue untouched. Misc:45-52 and Main:683/714 preserve it. IPX:1373-1385 stores it,
 * requests STOP_FM once and performs one display-disable blank with no ReadControllers/Finish.
 * Actual private callers own the reached return, complete state, prefix faults and capture lifetime. */
final class CdRetainedProgrammeReturn {
 private CdRetainedProgrammeReturn(){}
 interface Port {void returnByte(int value);void stopFm();void blank();}
 static int clearedPlcReturnByte(){return 0xFF;}
 /** Returned semantic BYTE knowledge only: unknown is a reached write with an unknown value,
  * not NO WRITE, zero, a cleared-PLC proof or authority to enter any actual programme. */
 record ReturnKnowledge(OptionalInt value){
  ReturnKnowledge{Objects.requireNonNull(value);if(value.isPresent()&&(value.getAsInt()&~255)!=0)throw new IllegalArgumentException("Returned BYTE width");}
  static ReturnKnowledge unknown(){return new ReturnKnowledge(OptionalInt.empty());}
  static ReturnKnowledge known(int value){return new ReturnKnowledge(OptionalInt.of(value));}
 }
 interface KnowledgePort {void returnByte(ReturnKnowledge knowledge);void stopFm();void blank();}
 /** Legacy cleared-queue callers keep their exact Port/BYTEFF API and use the one tail. */
 static void enter(Port port){
  Objects.requireNonNull(port);
  enter(new KnowledgePort(){
   public void returnByte(ReturnKnowledge knowledge){port.returnByte(knowledge.value().orElseThrow());}
   public void stopFm(){port.stopFm();}public void blank(){port.blank();}
  },ReturnKnowledge.known(clearedPlcReturnByte()));
 }
 /** RunMMD IPX.asm:1373-1387. Exactly one publication -> FM-stop -> represented blank.
  * No effect replay, retry, clock, sound ledger or CPU-return-register reconstruction.
  * Faults propagate unchanged and leave only callbacks that actually completed. */
 static void enter(KnowledgePort port,ReturnKnowledge knowledge){
  Objects.requireNonNull(port);Objects.requireNonNull(knowledge);
  port.returnByte(knowledge);port.stopFm();port.blank();
 }
}
