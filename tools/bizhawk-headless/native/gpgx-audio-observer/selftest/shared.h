#ifndef GPGX_AUDIO_TRACE_SELFTEST_SHARED_H
#define GPGX_AUDIO_TRACE_SELFTEST_SHARED_H
#include <stdint.h>
typedef struct { uint8_t *base; void *read8; void *read16; } cpu_memory_map;
struct selftest_m68k { cpu_memory_map memory_map[256]; };
extern struct selftest_m68k m68k;
extern uint8_t zram[0x2000];
#define READ_BYTE(base, address) ((base)[(address) ^ 1])
#endif
