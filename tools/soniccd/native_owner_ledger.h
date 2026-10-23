/* Private read-only CD-02 observer ABI, 2026-10-23. No guest state writes. */
#ifndef NATIVE_OWNER_LEDGER_H
#define NATIVE_OWNER_LEDGER_H
#include <stdint.h>
enum {
  OWNER_FRAME_BEGIN=1, OWNER_FRAME_END, OWNER_SCHEDULER_BEFORE,
  OWNER_SCHEDULER_AFTER, OWNER_CD_BEFORE, OWNER_CD_AFTER, OWNER_MAIN_BEFORE,
  OWNER_MAIN_AFTER, OWNER_PCM_BEGIN, OWNER_PCM_BEFORE, OWNER_PCM_AFTER,
  OWNER_RESET_BEFORE, OWNER_RESET_AFTER, OWNER_WRITE_BEFORE, OWNER_WRITE_AFTER,
  OWNER_WORDRAM_COMMIT, OWNER_GFX_START, OWNER_GFX_BUSY_CLEAR, OWNER_GFX_RENDERED,
  OWNER_CDD_BEFORE, OWNER_CDD_AFTER, OWNER_COMMAND_BEFORE, OWNER_COMMAND_AFTER,
  OWNER_SEEK_APPLIED, OWNER_LATENCY_EXHAUSTED, OWNER_CDC_TRIGGER_BEFORE,
  OWNER_CDC_TRIGGER_AFTER, OWNER_CDC_COMPLETE, OWNER_TIMER_EXPIRED,
  OWNER_TIMER_PENDING, OWNER_IRQ_BEFORE, OWNER_IRQ_AFTER
};
void native_owner_emit(uint32_t kind, uint32_t cpu, uint32_t width,
                       uint32_t address, uint32_t value, uint32_t aux0, uint32_t aux1);
void native_owner_mark(uint32_t kind, uint32_t argument);
void native_owner_access(uint32_t kind, uint32_t cpu, uint32_t width,
                         uint32_t address, uint32_t value, uint32_t cpu_cycles);
void native_owner_wordram(uint32_t handler, uint32_t width, uint32_t address,
                          uint32_t value, uint32_t previous_mode, uint32_t previous_dmna);
#endif
