"""Compile the real ledger against pinned native CPU/handler decoders.

2026-10-23 R4 alias regression. No ROM, CPU execution or gameplay is invented:
controlled scalar state drives the complete native write functions and CPU map
dispatch. Other services are inert. Oracle markers are inserted at the native
gate switches and PCM writable cases, independently of the ledger selector.
All generated C/shared objects live in a temporary directory outside the repo.
Set NATIVE_OWNER_PINNED_SOURCE to a complete pinned-r2/source when relocated.
"""
import ctypes
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


TOOLS = Path(__file__).resolve().parent
SOURCE = Path(os.environ.get('NATIVE_OWNER_PINNED_SOURCE',
              Path.home() / 'Downloads/Sonic-CD/native/owner-ledger/pinned-r2/source'))
CORE = 'waterbox/gpgx/Genesis-Plus-GX/core/'
PINS = {
    'mem68k.c': 'a93ae416fccba106db6d27f9a6eacc421d37f820e07ac19b9947c961dd9683ea',
    'cd_hw/scd.c': '864e7776642c83028f4b81e86a319f30893b3937a2977c599c7b66888a828a48',
    'cd_hw/pcm.c': '64a657104fb7f958601c75587de37cf81ccf1f5d51d7d54f3138462a474ba3a6',
    'genesis.c': '5a62ca4bede8c52fcedba86c6e2f5b747be07d1289a9b760b6f08d2cee6a7084',
    'm68k/m68kcpu.h': '756cae3a5a2d2d0d943b21680b4880aaaf217ec88903cf8468d2ad49d928f051',
}


def function(text, signature):
    """Extract one complete native function, with cardinality checked."""
    needle = signature + '\n{'
    if text.count(needle) != 1:
        raise ValueError('Native function cardinality: ' + signature)
    start = text.index(needle)
    # These pinned functions have their closing brace alone in column zero.
    return text[start:text.index('\n}', start) + 2]


SHARED = r'''
#ifndef OWNER_ALIAS_SHARED
#define OWNER_ALIAS_SHARED
#include <stdint.h>
#include <string.h>
typedef unsigned int uint;
typedef uint8_t uint8;
typedef uint16_t uint16;
typedef union { uint16 w; struct { uint8 l,h; } byte; } reg16_t;
typedef struct { uint8 *base; unsigned (*read8)(unsigned), (*read16)(unsigned);
  void (*write8)(unsigned,unsigned), (*write16)(unsigned,unsigned); } cpu_memory_map;
static struct { unsigned cycles, stopped, pc; cpu_memory_map memory_map[256]; } m68k,s68k;
static struct { unsigned (*read)(unsigned); void (*write)(unsigned,unsigned); } zbank_memory_map[256];
static struct { unsigned addr, st; reg16_t ls,fd; uint8 env,pan; } channels[8];
static struct { uint8 *bank; uint8 enabled,status,index,ram[0x10000]; unsigned cycles; } pcm;
#define chan channels
static struct { reg16_t regs[256]; unsigned dmna,cycles,pending; int timer,stopwatch,type;
  uint8 prg_ram[0x80000],word_ram_2M[0x40000],word_ram[2][0x20000];
  struct { unsigned boot; } cartridge; struct { unsigned cycles; } pcm_hw; } scd;
static struct { unsigned cycles,cyclesPerLine,bufferStart; uint8 *tracePtr,*mapPtr; } gfx;
static struct { unsigned cycles[2],ifstat,ifctrl,ar_mask; reg16_t dbc;
  void (*dma_w)(unsigned), (*halted_dma_w)(unsigned); } cdc;
static struct { unsigned cycles,latency,lba,status,pending,fader[2]; } cdd;
static struct { struct { void (*time_w)(unsigned,unsigned); } hw; } cart;
static struct { unsigned bios; } config;
static struct { struct { struct { struct { unsigned h; } byte; } gr[16]; unsigned emu_status; } ssp1601; } svp_data,*svp=&svp_data;
#define SSP_XST 0
#define SSP_PM0 1
#define SSP_WAIT_PM0 1
#define SYSTEM_MCD 1
#define CD_TYPE_WONDERMEGA 2
#define CD_TYPE_WONDERMEGA_M2 3
static unsigned system_hw=SYSTEM_MCD,oracle_selected;
#define SCYCLES_PER_LINE 795
#define MCYCLES_PER_LINE 3420
#define TIMERS_SCYCLES_RATIO 1536
#define PCM_SCYCLES_RATIO 1536
#define CDC_MAIN_CPU_ACCESS 0
#define CDC_SUB_CPU_ACCESS 1
#define INLINE static inline
#define ADDRESS_68K(a) ((a)&0xffffff)
#define WRITE_BYTE(b,a,v) ((b)[(a)^1]=(v))
#define m68ki_set_fc(a)
#define m68ki_check_address_error(a,b,c)
#define m68k_poll_sync(a) ((void)0)
#define s68k_poll_sync(a) ((void)0)
#define s68k_sync() ((void)0)
#define s68k_pulse_reset() ((void)0)
#define s68k_pulse_halt() ((void)0)
#define s68k_clear_halt() ((void)0)
#define s68k_clear_wait() ((void)0)
#define s68k_update_irq(a) ((void)0)
#define io_68k_write(a,b) ((void)0)
#define gen_zbusreq_w(a,b) ((void)0)
#define gen_zreset_w(a,b) ((void)0)
#define gen_bankswitch_w(a) ((void)0)
#define gen_tmss_w(a,b) ((void)0)
#define cdc_dma_update(a) ((void)0)
#define gfx_update(a) ((void)0)
#define gfx_start(a,b) ((void)0)
#define cdc_dma_init() ((void)0)
#define cdc_host_r(a) ((void)0)
#define cdc_reg_w(a) ((void)0)
#define cdd_process() ((void)0)
#define scd_reset(a) ((void)0)
#define pcm_run(a) ((void)0)
static unsigned bus(unsigned a) { return 0; }
static void unused(unsigned a,unsigned b) { }
static void dma(unsigned a) { }
#define m68k_read_bus_8 bus
#define m68k_read_bus_16 bus
#define s68k_read_bus_8 bus
#define s68k_read_bus_16 bus
#define scd_read_byte bus
#define scd_read_word bus
#define s68k_lockup_r_8 bus
#define s68k_lockup_r_16 bus
#define zbank_unused_r bus
#define m68k_unused_8_w unused
#define m68k_unused_16_w unused
#define m68k_lockup_w_8 unused
#define m68k_lockup_w_16 unused
#define s68k_unused_8_w unused
#define s68k_unused_16_w unused
#define s68k_lockup_w_8 unused
#define s68k_lockup_w_16 unused
#define zbank_unused_w unused
#define prg_ram_dma_w dma
#define word_ram_2M_dma_w dma
'''
for _prefix in ['cell_ram_0', 'cell_ram_1', 'dot_ram_0', 'dot_ram_1']:
    for _op in ['read8', 'read16', 'write8', 'write16']:
        SHARED += f'#define {_prefix}_{_op} {"bus" if _op.startswith("read") else "unused"}\n'
for _prefix in ['prg_ram', 'word_ram_s68k', 'bram']:
    for _op in ['read_byte', 'read_word', 'write_byte', 'write_word']:
        SHARED += f'#define {_prefix}_{_op} {"bus" if _op.startswith("read") else "unused"}\n'
SHARED += '#endif\n'


def compile_harness(destination):
    originals = {}
    for name, expected in PINS.items():
        data = (SOURCE / CORE / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError('Native decoder source pin differs: ' + name)
        originals[name] = data.decode().replace('\r\n', '\n')
    # Apply the checked-in patch, including its actual handler wrappers, to
    # fresh temporary copies. Never touch the pinned tree or any build tree.
    patch = (TOOLS / 'native_owner_core.patch').read_bytes()
    for name in re.findall(rb'^--- a/(.+)\r?$', patch, re.MULTILINE):
        name = name.decode().strip()
        source = SOURCE / name
        if source.exists():
            out = destination / name
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_bytes(source.read_bytes())
    subprocess.run(['patch', '--binary', '--batch', '--fuzz=0', '-p1'], input=patch,
                   cwd=destination, check=True, capture_output=True)
    texts = {name: (destination / CORE / name).read_text()
             if (destination / CORE / name).exists() else originals[name] for name in PINS}
    c = '#include "shared.h"\n#include "native_owner_ledger.c"\n'
    c += function(originals['cd_hw/scd.c'], 'INLINE void word_ram_switch(uint8 mode)') + '\n'
    pcm_function = function(originals['cd_hw/pcm.c'],
        'void pcm_write(unsigned int address, unsigned char data, unsigned int cycles)')
    # Mark actual writable PCM cases; reserved/wave paths get no marker.
    pcm_function = re.sub(r'(case 0x0[0-8]:[^\n]*\n\s*\{)', r'\1\n      oracle_selected=1;', pcm_function)
    pcm_function = pcm_function.replace('pcm.chan', 'channels')
    c += pcm_function + '\n'
    c += 'static void scd_write_word(unsigned int,unsigned int);\n'
    for file, prefix in [('mem68k.c', 'ctrl_io'), ('cd_hw/scd.c', 'scd')]:
        for suffix in ['byte', 'word']:
            sig = ('static ' if prefix == 'scd' else '') + f'void {prefix}_write_{suffix}(unsigned int address, unsigned int data)'
            body = function(texts[file], sig.replace('(', '_owner_original(', 1))
            body = re.sub(r'(switch \(address & (?:0x3f|0x3e|0x1ff|0x1fe)\))',
                          r'oracle_selected=1;\n  \1', body)
            c += body + '\n' + function(texts[file], sig) + '\n'
    # Both CPU specializations use the real patched width helper bodies.
    for cpu, prefix in [(1, 'main'), (2, 'sub')]:
        c += f'#define NATIVE_OWNER_CPU {cpu}\n#define m68ki_cpu {"m68k" if cpu==1 else "s68k"}\n'
        for bits in [8, 16, 32]:
            body = function(texts['m68k/m68kcpu.h'], f'INLINE void m68ki_write_{bits}(uint address, uint value)')
            c += body.replace(f'm68ki_write_{bits}', f'{prefix}_write_{bits}') + '\n'
        c += '#undef NATIVE_OWNER_CPU\n#undef m68ki_cpu\n'
    # Use native map assignments/switch, not another copy of its mask pattern.
    init = originals['cd_hw/scd.c']
    start = init.index('  for (i=0x00; i<0x100; i++)', init.index('void scd_init(void)'))
    end = init.index('\n  /* Initialize CD hardware */', start)
    mapping = init[start:end]
    genesis = originals['genesis.c']
    main_mapping = '\n'.join(re.findall(r'^[ \t]*m68k.memory_map\[0xa1\].write(?:8|16)[ \t]*= ctrl_io_write_\w+;', genesis, re.MULTILINE))
    if len(main_mapping.splitlines()) != 2:
        raise ValueError('Native main map cardinality')
    c += r'''
static uint8 backing[0x10004];
static uint32_t output[8192*64];
void probe(unsigned cpu,unsigned width,unsigned address,unsigned value,unsigned mode,unsigned dmna,unsigned enabled)
{
  int i;
  memset(&scd,0,sizeof(scd)); memset(&pcm,0,sizeof(pcm)); memset(channels,0,sizeof(channels));
  memset(&m68k,0,sizeof(m68k)); memset(&s68k,0,sizeof(s68k));
  scd.regs[1].w=mode; scd.dmna=dmna; pcm.bank=pcm.ram;
  m68k.cycles=123; s68k.cycles=456; pcm.cycles=456;
  for(i=0;i<256;i++) {
    m68k.memory_map[i].base=s68k.memory_map[i].base=backing;
    m68k.memory_map[i].write8=m68k.memory_map[i].write16=unused;
    s68k.memory_map[i].write8=s68k.memory_map[i].write16=unused;
  }
''' + main_mapping + '\n' + mapping + r'''
  native_owner_configure(enabled,8192); oracle_selected=0;
  if(cpu==1) {
    if(width==1) main_write_8(address,value);
    if(width==2) main_write_16(address,value);
    if(width==4) main_write_32(address,value);
  } else {
    if(width==1) sub_write_8(address,value);
    if(width==2) sub_write_16(address,value);
    if(width==4) sub_write_32(address,value);
  }
}
unsigned selected(void) { return oracle_selected; }
unsigned result_mode(void) { return scd.regs[1].w; }
unsigned result_dmna(void) { return scd.dmna; }
uint32_t *events(void) { native_owner_poll(output,sizeof(output)); return output; }
void helper(unsigned handler,unsigned width,unsigned address,unsigned value,unsigned previous_mode,unsigned previous_dmna)
{ native_owner_configure(1,8192); native_owner_wordram(handler,width,address,value,previous_mode,previous_dmna); }
'''
    (destination / 'shared.h').write_text(SHARED)
    (destination / 'emulibc.h').write_text('#define ECL_EXPORT\n#define ECL_INVISIBLE __attribute__((section(".invis")))\n')
    (destination / 'harness.c').write_text(c)
    subprocess.run(['cc', '-std=c99', '-shared', '-fPIC', '-O1', '-Werror=implicit-function-declaration',
                    '-I', str(destination), '-I', str(TOOLS), str(destination / 'harness.c'),
                    '-o', str(destination / 'harness.so')], check=True, capture_output=True)
    return ctypes.CDLL(str(destination / 'harness.so'))


class NativeAliases(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not SOURCE.is_dir() or not shutil.which('cc') or not shutil.which('patch'):
            raise unittest.SkipTest('Complete pinned native sources and cc/patch required')
        cls.temporary = tempfile.TemporaryDirectory(prefix='native-owner-alias-')
        cls.addClassCleanup(cls.temporary.cleanup)
        try:
            cls.lib = compile_harness(Path(cls.temporary.name))
        except subprocess.CalledProcessError as error:
            raise RuntimeError(error.stderr.decode()[-12000:]) from error
        cls.lib.events.restype = ctypes.POINTER(ctypes.c_uint32)

    def probe(self, cpu, width, address, value=0xa5010001, mode=1, dmna=0, enabled=1):
        self.lib.probe(cpu, width, address, value, mode, dmna, enabled)
        count = self.lib.native_owner_count()
        selected = bool(self.lib.selected())
        p = self.lib.events()
        rows = [list(p[i*64:(i+1)*64]) for i in range(count)]
        return selected, rows

    def assert_access(self, cpu, width, address):
        selected, rows = self.probe(cpu, width, address)
        access = [r for r in rows if r[3] in (14, 15)]
        self.assertEqual(len(access), 2 if selected else 0, (cpu, width, hex(address)))
        for kind, row in zip((14, 15), access):
            self.assertEqual(row[3:8], [kind, cpu, width, address, 0xa5010001])
            self.assertEqual(row[51], 123 if cpu == 1 else 456)

    def test_access_matches_native_decoders_across_mirrors_and_boundaries(self):
        for width in (1, 2, 4):
            for address in range(0xa11ffc, 0xa12105):
                self.assert_access(1, width, address)
            for page in (0x0f0000, 0x1f0000, 0xff0000):
                for offset in (0, 1, 0xe, 0xf, 0x10, 0x11, 0x12, 0x13, 0x1e, 0x1f,
                               0x20, 0x1ffe, 0x2000, 0x3ffe, 0x4000, 0x4001, 0x4011,
                               0x4012, 0x401f, 0x4020, 0x5ffe, 0x6000, 0x7ffc, 0x7ffe,
                               0x7fff, 0x8000, 0x8002, 0x8003, 0x81ff, 0x8200, 0x8203,
                               0x8403, 0xfdff, 0xfe00, 0xfe03, 0xfffe, 0xffff):
                    self.assert_access(2, width, page + offset)
            for cpu, address in [(1,0xaa12003),(1,0xa02003),(1,0xb12003),
                                 (2,0xfe8003),(2,0x0e8003),(2,0x1000000),
                                 (1,0xfffffffe),(2,0xfffffffe)]:
                self.assert_access(cpu, width, address)

    def test_owner_commits_follow_real_mirrored_handler_and_keep_leg_identity(self):
        for cpu, base in [(1,0xa12000),(1,0xa12040),(1,0xa12080),(1,0xa120c0),
                          (2,0x0f8000),(2,0xff8200),(2,0x1ffe00)]:
            for width, offset in [(1,3),(2,2),(2,3),(4,0)]:
                with self.subTest(cpu=cpu,width=width,base=hex(base)):
                    value = 2 if cpu == 1 else 1
                    raw_value = (0xa5010000 | value) if width==4 else value
                    selected, rows = self.probe(cpu,width,base+offset,raw_value,
                                                mode=1 if cpu==1 else 2,dmna=0 if cpu==1 else 1)
                    commits = [r for r in rows if r[3]==16]
                    self.assertTrue(selected)
                    self.assertEqual(len(commits),1)
                    row=commits[0]
                    self.assertEqual(row[4:8],[0,1 if width==1 else 2,
                                              base+(2 if width==4 else offset),value])
                    self.assertEqual(row[27:29],[self.lib.result_mode(),self.lib.result_dmna()])
                    self.assertEqual(row[51],cpu)
                    self.assertEqual([r[3] for r in rows],[14,16,15])

    def test_protection_priority_and_no_change_are_not_ownership(self):
        for cpu,width,address,value,mode in [(1,1,0xa12042,0x55,1),
            (1,2,0xa12082,0x5500,1),(1,1,0xa120c3,0xc0,1),
            (2,1,0xff8203,0x18,1),(2,2,0x1ffe02,0x18,1),
            (2,1,0xff8003,1,1)]:
            with self.subTest(address=hex(address),width=width):
                _,rows=self.probe(cpu,width,address,value,mode)
                self.assertEqual([r[3] for r in rows],[14,15])

    def test_helper_rejects_other_registers_and_invalid_handler_width(self):
        # Artificial before/after difference exercises the guard only; the
        # preceding tests separately exercise actual native handler state.
        self.probe(1,1,0xa12003,mode=1)
        for handler,width,address in [(1,1,0xa12002),(1,2,0xa12102),
            (1,1,0xb12003),(2,1,0xff0003),(2,1,0xfe8003),(2,2,0xff8204),
            (0,1,0xa12003),(3,1,0xff8003),(1,4,0xa12002),(2,4,0xff8002)]:
            self.lib.helper(handler,width,address,0xabcdef01,2,1)
            self.assertEqual(self.lib.native_owner_count(),0,(handler,width,hex(address)))

    def test_disabled_observer_retains_no_alias_events(self):
        for cpu,address in [(1,0xa12043),(2,0xff8203),(2,0x1f4001)]:
            _,rows=self.probe(cpu,1,address,enabled=0)
            self.assertEqual(rows,[])


if __name__ == '__main__':
    unittest.main()
