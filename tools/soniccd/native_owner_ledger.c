/* Private owner-ledger, CD-02, 2026-10-23. All mutable observer bytes are
 * ECL_INVISIBLE, excluded by Waterbox's actual state contract. Hooks only read
 * owners. They call no device handlers, CPU runners or managed callbacks.
 * Overflow is sticky and never changes emulation or writes beyond the ledger.
 */
#include "shared.h"
#include <emulibc.h>
#include <stdint.h>
#include "native_owner_ledger.h"

#define OWNER_WORDS 64
#define OWNER_CAPACITY 8192
typedef struct { uint32_t word[OWNER_WORDS]; } owner_event;
typedef char owner_event_size_must_be_256[(sizeof(owner_event) == 256) ? 1 : -1];
static struct {
  uint32_t enabled, limit, count, fault, dropped;
  uint32_t call, reset_epoch, scheduler_epoch, cd_epoch, main_epoch, pcm_epoch, gfx_operation;
  uint64_t sequence;
  owner_event events[OWNER_CAPACITY];
} owner ECL_INVISIBLE;

static uint32_t word_offset(const void *pointer)
{
  uintptr_t value = (uintptr_t)pointer, base = (uintptr_t)scd.word_ram_2M;
  return (value >= base && value <= base + sizeof(scd.word_ram_2M)) ? value-base : UINT32_MAX;
}
static uint32_t map_kind(const void *pointer)
{
  uintptr_t p = (uintptr_t)pointer;
  if (p >= (uintptr_t)scd.word_ram_2M && p < (uintptr_t)(scd.word_ram_2M + sizeof(scd.word_ram_2M))) return 1;
  if (p >= (uintptr_t)scd.word_ram[0] && p < (uintptr_t)(scd.word_ram[0] + sizeof(scd.word_ram[0]))) return 2;
  if (p >= (uintptr_t)scd.word_ram[1] && p < (uintptr_t)(scd.word_ram[1] + sizeof(scd.word_ram[1]))) return 3;
  return 0;
}
void native_owner_emit(uint32_t kind, uint32_t cpu, uint32_t width,
                       uint32_t address, uint32_t value, uint32_t aux0, uint32_t aux1)
{
  owner_event *e;
  uint32_t *w;
  if (!owner.enabled) return;
  if (owner.count >= owner.limit) { owner.fault = 1; owner.dropped++; return; }
  e = &owner.events[owner.count++];
  memset(e, 0, sizeof(*e));
  w = e->word;
  owner.sequence++;
  w[0] = owner.sequence; w[1] = owner.sequence >> 32;
  w[2] = owner.call; w[3] = kind; w[4] = cpu; w[5] = width; w[6] = address; w[7] = value;
  w[8] = owner.reset_epoch; w[9] = owner.scheduler_epoch; w[10] = owner.cd_epoch;
  w[11] = owner.main_epoch; w[12] = owner.pcm_epoch; w[13] = owner.gfx_operation;
  w[14] = m68k.cycles; w[15] = s68k.cycles; w[16] = scd.cycles;
  w[17] = gfx.cycles; w[18] = gfx.cyclesPerLine;
  w[19] = cdc.cycles[0]; w[20] = cdc.cycles[1]; w[21] = scd.pcm_hw.cycles;
  w[22] = cdd.cycles; w[23] = cdd.latency; w[24] = cdd.lba; w[25] = cdd.status; w[26] = cdd.pending;
  w[27] = scd.regs[0x02>>1].w; w[28] = scd.dmna;
  for (int n = 0; n < 8; n++) w[29+n] = scd.regs[(0x58>>1)+n].w;
  w[37] = word_offset(gfx.tracePtr); w[38] = word_offset(gfx.mapPtr); w[39] = gfx.bufferStart;
  w[40] = cdc.ifstat; w[41] = cdc.ifctrl; w[42] = cdc.dbc.w; w[43] = cdc.dma_w != 0;
  w[44] = scd.pending; w[45] = scd.timer; w[46] = scd.stopwatch;
  w[47] = map_kind(m68k.memory_map[scd.cartridge.boot+0x20].base);
  w[48] = map_kind(s68k.memory_map[0x08].base);
  w[49] = m68k.memory_map[scd.cartridge.boot+0x20].read8 != 0;
  w[50] = s68k.memory_map[0x08].read8 != 0;
  w[51] = aux0; w[52] = aux1;
}
void native_owner_mark(uint32_t kind, uint32_t argument)
{
  if (!owner.enabled) return;
  switch (kind) {
    case OWNER_FRAME_BEGIN: owner.call++; break;
    case OWNER_SCHEDULER_AFTER: owner.scheduler_epoch++; break;
    case OWNER_CD_AFTER: owner.cd_epoch++; break;
    case OWNER_MAIN_AFTER: owner.main_epoch++; break;
    case OWNER_PCM_AFTER: owner.pcm_epoch++; break;
    case OWNER_RESET_AFTER: owner.reset_epoch++; break;
    case OWNER_GFX_START: owner.gfx_operation++; break;
  }
  native_owner_emit(kind, 0, 0, 0, 0, argument, 0);
}
static uint32_t selected_write(uint32_t cpu, uint32_t width, uint32_t address)
{
  /* m68kcpu.h dispatches through the 24-bit CPU map. mem68k.c's main
   * control page contains all four $40-byte gate aliases. scd_init maps
   * every $xF page to scd_write_byte/word; bit 15 selects gates, otherwise
   * PCM sees (address >> 1) & $1fff. Only PCM indices 0..8 are writable
   * registers; reserved indices and wave RAM are outside this selection.
   */
  if (cpu == 1) return (address & 0xffff00) == 0xa12000;
  if (cpu != 2 || (address & 0xf0000) != 0xf0000) return 0;
  if (address & 0x8000) return 1;
  return ((address >> 1) & 0x1fff) <= 8 && (width == 2 || (address & 1));
}
void native_owner_access(uint32_t kind, uint32_t cpu, uint32_t width,
                         uint32_t address, uint32_t value, uint32_t cpu_cycles)
{
  if (!owner.enabled) return;
  /* CPU is a literal supplied by each CPU compilation unit. Mirrored sub-CPU
   * gates are selected using that CPU's real map, not a last-executing latch.
   * Reads are deliberately outside ABI 1; every selected write is retained.
   */
  /* A native long invokes two independently mapped word handlers, including
   * page crossings and 24-bit wrap. Select their union, preserving the
   * original address/value and width 4 on the surrounding access pair.
   */
  if (((width == 1 || width == 2) && selected_write(cpu, width, address)) ||
      (width == 4 && (selected_write(cpu, 2, address) || selected_write(cpu, 2, address + 2))))
    native_owner_emit(kind, cpu, width, address, value, cpu_cycles, 0);
}
void native_owner_wordram(uint32_t handler, uint32_t width, uint32_t address,
                          uint32_t value, uint32_t previous_mode, uint32_t previous_dmna)
{
  if (!owner.enabled) return;
  if (width != 1 && width != 2) return;
  if (handler == 1) {
    if ((address & 0xffff00) != 0xa12000 ||
        (address & (width == 1 ? 0x3f : 0x3e)) != (width == 1 ? 3 : 2)) return;
  } else if (handler == 2) {
    if ((address & 0xf0000) != 0xf0000 || !(address & 0x8000) || (address & 0x1fe) != 2) return;
  } else return;
  /* WP, PRG bank and dot-priority bits are not Word-RAM ownership commits. */
  if (((previous_mode ^ scd.regs[0x02>>1].w) & 7) || previous_dmna != scd.dmna)
    /* handler is source ownership, not a guessed caller CPU identity */
    native_owner_emit(OWNER_WORDRAM_COMMIT, 0, width, address, value, handler, previous_mode | (previous_dmna << 16));
}
ECL_EXPORT uint32_t native_owner_abi_version(void) { return 1; }
ECL_EXPORT uint32_t native_owner_event_size(void) { return sizeof(owner_event); }
ECL_EXPORT uint32_t native_owner_capacity(void) { return OWNER_CAPACITY; }
ECL_EXPORT uint32_t native_owner_fault(void) { return owner.fault; }
ECL_EXPORT uint32_t native_owner_dropped(void) { return owner.dropped; }
ECL_EXPORT uint32_t native_owner_count(void) { return owner.count; }
ECL_EXPORT int native_owner_configure(uint32_t enabled, uint32_t limit)
{
  if (enabled > 1 || limit < 1 || limit > OWNER_CAPACITY) return -1;
  memset(&owner, 0, sizeof(owner));
  owner.enabled = enabled; owner.limit = limit;
  return 0;
}
ECL_EXPORT int native_owner_poll(void *destination, uint32_t bytes)
{
  uint32_t count = owner.count;
  if (!destination || bytes != OWNER_CAPACITY * sizeof(owner_event)) return -1;
  memcpy(destination, owner.events, count * sizeof(owner_event));
  owner.count = 0;
  return count;
}
