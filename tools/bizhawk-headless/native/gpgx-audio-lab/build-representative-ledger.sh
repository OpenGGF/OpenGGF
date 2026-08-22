#!/usr/bin/env bash
set -euo pipefail

game=${1:?usage: build-representative-ledger.sh s1|s2 INPUT OUTPUT}
input=${2:?usage: build-representative-ledger.sh s1|s2 INPUT OUTPUT}
output=${3:?usage: build-representative-ledger.sh s1|s2 INPUT OUTPUT}
[[ "$game" == s1 || "$game" == s2 ]] || { echo 'game must be s1 or s2' >&2; exit 2; }
[[ -f "$input" && ! -e "$output" ]] || { echo 'input must exist and output must not' >&2; exit 2; }

gawk -F '\t' -v OFS='\t' -v game="$game" '
function hex(value) { return strtonum(value) }
function z80_conditional(op) {
  return op == 0x10 || op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38 \
    || op == 0xc0 || op == 0xc8 || op == 0xd0 || op == 0xd8 \
    || op == 0xe0 || op == 0xe8 || op == 0xf0 || op == 0xf8 \
    || op == 0xc2 || op == 0xca || op == 0xd2 || op == 0xda \
    || op == 0xe2 || op == 0xea || op == 0xf2 || op == 0xfa \
    || op == 0xc4 || op == 0xcc || op == 0xd4 || op == 0xdc \
    || op == 0xe4 || op == 0xec || op == 0xf4 || op == 0xfc
}
function z80_length(op) {
  if (op == 0x10 || op == 0x18 || op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38) return 2
  if (op == 0xc2 || op == 0xca || op == 0xd2 || op == 0xda || op == 0xe2 || op == 0xea || op == 0xf2 || op == 0xfa || op == 0xc3 \
      || op == 0xc4 || op == 0xcc || op == 0xd4 || op == 0xdc || op == 0xe4 || op == 0xec || op == 0xf4 || op == 0xfc || op == 0xcd) return 3
  return 1
}
function flow_kind(op, pc, high) {
  if (game == "s2") {
    if (op == 0xcd || op == 0xc4 || op == 0xcc || op == 0xd4 || op == 0xdc || op == 0xe4 || op == 0xec || op == 0xf4 || op == 0xfc \
        || op == 0xc7 || op == 0xcf || op == 0xd7 || op == 0xdf || op == 0xe7 || op == 0xef || op == 0xf7 || op == 0xff) return "call"
    if (op == 0xc9 || op == 0xc0 || op == 0xc8 || op == 0xd0 || op == 0xd8 || op == 0xe0 || op == 0xe8 || op == 0xf0 || op == 0xf8) return "return"
    if (op == 0x10 || op == 0x18 || op == 0x20 || op == 0x28 || op == 0x30 || op == 0x38 \
        || op == 0xc2 || op == 0xca || op == 0xd2 || op == 0xda || op == 0xe2 || op == 0xea || op == 0xf2 || op == 0xfa || op == 0xc3) return "branch"
    return "linear"
  }
  high = and(rshift(op, 8), 0xff)
  if (high == 0x61 || and(op, 0xffc0) == 0x4e80) return "call"
  if (op == 0x4e75 || op == 0x4e73 || op == 0x4e77) return "return"
  if ((high >= 0x60 && high <= 0x6f) || and(op, 0xf0f8) == 0x50c8) return "branch"
  return "linear"
}
function conditional(op, high) {
  if (game == "s2") return z80_conditional(op)
  high = and(rshift(op, 8), 0xff)
  return (high >= 0x62 && high <= 0x6f) || and(op, 0xf0f8) == 0x50c8
}
function sequential_pc(op, pc, low, high) {
  if (game == "s2") return pc + z80_length(op)
  high = and(rshift(op, 8), 0xff); low = and(op, 0xff)
  if (high >= 0x60 && high <= 0x6f) return pc + (low == 0 ? 4 : (low == 0xff ? 6 : 2))
  if (and(op, 0xf0f8) == 0x50c8) return pc + 4
  if (and(op, 0xffc0) == 0x4e80) return pc + 2
  return pc + 2
}
function roles(op, pc, flow, result) {
  result = "source_path"
  if (game == "s1") {
    if (pc >= 0x7272e && pc <= 0x7278e) result = result ",busy_poll"
    if ((pc == 0x72788 || pc == 0x72752) && op == 0x13c1) result = result ",ym_write"
  } else {
    if (pc >= 0x8 && pc <= 0x31) result = result ",busy_poll"
    if ((pc == 0xe34 && op == 0x7e) || ((pc == 0xe46 || pc == 0xe52 || pc == 0xe79) && op == 0x4e)) result = result ",bank_wait_3t"
    if ((pc == 0x31 || pc == 0x21) && op == 0x32) result = result ",ym_write"
  }
  if (flow == "call" || flow == "return") result = result ",call_return"
  return result
}
NR == 1 { next }
$2 == 0 { n++; frame[n]=$1; after[n]=$3; cpu[n]=$4; pc_text[n]=$5; op_text[n]=$6; cycle[n]=$7; pc[n]=hex($5); op[n]=hex($6) }
END {
  citation = game == "s1" \
    ? "s1.sounddriver.asm:436-456,1680-1769,2080-2140,2313-2375" \
    : "s2.sounddriver.asm:343-389,947-1012,2088-2138,2837-2860,3271-3432"
  print "occurrence_ordinal","frame","after_source_ordinal","cpu","pc","opcode","start_master_cycle","next_pc","delta_to_next_start","flow","branch_outcome","roles","source"
  for (i=1; i<=n; i++) {
    flow = flow_kind(op[i], pc[i]); outcome = "n/a"; next_pc_value = i<n ? pc_text[i+1] : "key_on"
    delta = i<n ? cycle[i+1]-cycle[i] : "key_on"
    if (conditional(op[i])) outcome = (i<n && pc[i+1] == sequential_pc(op[i], pc[i])) ? "not_taken" : "taken"
    else if (flow != "linear") outcome = "target=" next_pc_value
    print i-1,frame[i],after[i],cpu[i],pc_text[i],op_text[i],cycle[i],next_pc_value,delta,flow,outcome,roles(op[i],pc[i],flow),citation
  }
}' "$input" > "$output"
