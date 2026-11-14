/* CD-06 native PCM block observations, 2026-10-23. No emulation writes. */
#ifndef PCM_NATIVE_OBSERVER_H
#define PCM_NATIVE_OBSERVER_H
#include <stdint.h>
void pcm_observe_frame(void);
void pcm_observe_begin(uint32_t length);
void pcm_observe_loop(uint32_t channel);
void pcm_observe_sample(uint32_t index, int left, int right, int previous_left, int previous_right);
void pcm_observe_disabled(uint32_t length, int previous_left, int previous_right);
void pcm_observe_end(void);
#endif
