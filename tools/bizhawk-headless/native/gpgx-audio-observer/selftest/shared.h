#ifndef GPGX_AUDIO_TRACE_SELFTEST_SHARED_H
#define GPGX_AUDIO_TRACE_SELFTEST_SHARED_H
#include <stdint.h>
typedef struct { uint8_t *base; void *read8; void *read16; } cpu_memory_map;
struct selftest_m68k { cpu_memory_map memory_map[256]; };
extern struct selftest_m68k m68k;
extern uint8_t zram[0x2000];
extern uint8_t work_ram[0x10000];
extern uint32_t selftest_m68k_a7;
#define M68K_REG_A7 15
static inline uint32_t m68k_get_reg(int reg)
{
  return reg == M68K_REG_A7 ? selftest_m68k_a7 : 0;
}
#define READ_BYTE(base, address) ((base)[(address) ^ 1])
#endif
