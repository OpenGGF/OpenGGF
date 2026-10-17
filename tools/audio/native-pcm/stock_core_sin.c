/* Executes the stock BizHawk GPGX core's own musl `sin` for every 16-bit equaliser cutoff.
 *
 * Purpose: ground truth for com.openggf.audio.synth.MuslSin (the ThreeBand cutoff sin). Host libms differ
 * (glibc moves the 880 Hz cutoff by one ulp), so the reference is the routine inside the shipped core.
 * Inputs: the decompressed stock core ELF (`zstd -d dll/gpgx.wbx.zst`; BizHawk 2.11 stock sha256 of the .zst
 * c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12), the `sin` symbol address
 * (`nm gpgx.wbx | grep ' sin$'`, 0x36f00004e14 for that core), the address of the pi constant the inlined
 * init_3band_state multiplies by (the `mulsd` operand in `audio_reset`, 0x36f001c8a08 for that core) and an
 * output path. The core's PT_LOAD segments are mapped at their fixed addresses; sin/__sin/__cos/__rem_pio2 are
 * pure leaf code. It writes little-endian (x, sin(x)) double pairs for x = (f / 44100.0) * pi, f =
 * -32768..32767, with the core's own pi: that build sees no libc M_PI, so GPGX core/macros.h:36-38 supplies the
 * float literal 3.14159265358979323846264338327f (0x400921FB60000000), not the double M_PI. It prints the 880 Hz
 * and 5000 Hz cutoffs. TestNativeCompatiblePcmOutput pins the dump's SHA-256.
 * Build/run (compiler and runtime leases per AGENTS.md):
 *   clang -O2 -o stock_core_sin stock_core_sin.c && ./stock_core_sin gpgx.wbx 0x36f00004e14 0x36f001c8a08 out.bin
 * Originating task: OpenGGF round-2 lane pcm-output, 2026-10-16; core pi corrected 2026-10-17
 * (feature/ai-s3k-r2-pcm-native-output).
 */
#include <elf.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <sys/mman.h>
int main(int argc, char **argv) {
  if (argc != 5) return 2;
  FILE *f = fopen(argv[1], "rb"); if (!f) return 3;
  fseek(f, 0, SEEK_END); long size = ftell(f); fseek(f, 0, SEEK_SET);
  unsigned char *img = malloc(size); if (fread(img, 1, size, f) != (size_t) size) return 3; fclose(f);
  Elf64_Ehdr *eh = (Elf64_Ehdr *) img;
  for (int i = 0; i < eh->e_phnum; i++) {
    Elf64_Phdr *ph = (Elf64_Phdr *) (img + eh->e_phoff + i * eh->e_phentsize);
    if (ph->p_type != PT_LOAD) continue;
    uintptr_t start = ph->p_vaddr & ~0xFFFUL, end = (ph->p_vaddr + ph->p_memsz + 0xFFF) & ~0xFFFUL;
    void *m = mmap((void *) start, end - start, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
    if (m == MAP_FAILED || (uintptr_t) m != start) { perror("mmap"); return 4; }
    memcpy((void *) ph->p_vaddr, img + ph->p_offset, ph->p_filesz);
    int prot = ((ph->p_flags & PF_R) ? PROT_READ : 0) | ((ph->p_flags & PF_W) ? PROT_WRITE : 0) | ((ph->p_flags & PF_X) ? PROT_EXEC : 0);
    if (mprotect(m, end - start, prot) != 0) { perror("mprotect"); return 5; }
  }
  double (*core_sin)(double) = (double (*)(double)) (uintptr_t) strtoull(argv[2], NULL, 0);
  double core_pi; memcpy(&core_pi, (const void *) (uintptr_t) strtoull(argv[3], NULL, 0), 8);
  FILE *out = fopen(argv[4], "wb"); if (!out) return 6;
  long n = 0;
  for (int freq = -32768; freq <= 32767; freq++) {
    double x = ((double) freq / (double) 44100) * core_pi;
    double s = core_sin(x);
    fwrite(&x, 8, 1, out); fwrite(&s, 8, 1, out); n++;
  }
  fclose(out);
  double lf = core_sin(((double) 880 / (double) 44100) * core_pi), hf = core_sin(((double) 5000 / (double) 44100) * core_pi);
  lf = lf + lf; hf = hf + hf; /* 2 * sin(...), an addsd in the core */
  uint64_t pb, lb, hb; memcpy(&pb, &core_pi, 8); memcpy(&lb, &lf, 8); memcpy(&hb, &hf, 8);
  printf("evaluated=%ld pi=%016llx lf880=%016llx hf5000=%016llx\n", n, (unsigned long long) pb, (unsigned long long) lb,
         (unsigned long long) hb);
  return 0;
}
