/* Minimal stand-in for Genesis Plus GX's core/shared.h so that the pinned external core/sound/psg.c compiles alone
 * inside the native-compatible PCM vector generator (gpgx_pcm_vectors.c). OpenGGF code: it declares only the integer
 * types psg.c uses, psg.h, the `snd.blips[0]` buffer and `config.hq_psg` it reads, and the save/load macros its
 * unused state functions expand. Originating task: round-2 lane pcm-output, 2026-10-16. */
#ifndef OPENGGF_PCM_VECTOR_SHARED_STUB_H
#define OPENGGF_PCM_VECTOR_SHARED_STUB_H
#include <stdint.h>
#include <string.h>
typedef uint8_t uint8;
typedef uint16_t uint16;
typedef uint32_t uint32;
typedef int8_t int8;
typedef int16_t int16;
typedef int32_t int32;
#include "blip_buf.h"
#include "psg.h"
struct pcmout_snd { blip_t *blips[4]; };
extern struct pcmout_snd snd;
struct pcmout_config { int hq_psg; };
extern struct pcmout_config config;
#define save_param(param, size) { memcpy(&state[bufferptr], param, size); bufferptr += size; }
#define load_param(param, size) { memcpy(param, &state[bufferptr], size); bufferptr += size; }
#endif
