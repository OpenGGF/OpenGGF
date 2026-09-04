#include <algorithm>
#include <array>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "ymfm_opn.h"
extern "C" {
#include "ym3438.h"
}

using Sample = std::array<int, 2>;

struct Nuked {
    ym3438_t chip{};
    Nuked() { OPN2_Reset(&chip); OPN2_SetChipType(ym3438_mode_ym2612); }
    Sample frame() {
        Sample sum{};
        Bit16s pins[2];
        for (int cycle = 0; cycle < 24; cycle++) {
            OPN2_Clock(&chip, pins);
            sum[0] += pins[0];
            sum[1] += pins[1];
        }
        return sum;
    }
    void rawWrite(int port, int value) { OPN2_Write(&chip, port, value); }
    using State = ym3438_t;
    State save() const { return chip; }
    void restore(const State &state) { chip = state; }
};

struct Ymfm {
    ymfm::ymfm_interface interface;
    ymfm::ym2612 chip{interface};
    Ymfm() { chip.reset(); }
    Sample frame() {
        ymfm::ym2612::output_data output;
        chip.generate(&output);
        return {output.data[0], output.data[1]};
    }
    void rawWrite(int port, int value) { chip.write(port, value); }
    using State = std::vector<uint8_t>;
    State save() {
        State bytes;
        ymfm::ymfm_saved_state state(bytes, true);
        chip.save_restore(state);
        return bytes;
    }
    void restore(State &bytes) {
        ymfm::ymfm_saved_state state(bytes, false);
        chip.save_restore(state);
    }
};

template <class Chip>
void clocks(Chip &chip, int count) {
    for (int index = 0; index < count; index++) chip.frame();
}

template <class Chip>
void reg(Chip &chip, int part, int address, int value) {
    chip.rawWrite(part * 2, address);
    chip.frame();
    chip.rawWrite(part * 2 + 1, value);
    chip.frame();
}

template <class Chip>
void program(Chip &chip) {
    reg(chip, 0, 0x22, 0x08); reg(chip, 0, 0x27, 0x00); reg(chip, 0, 0x2b, 0x00);
    for (int part = 0; part < 2; part++) for (int channel = 0; channel < 3; channel++) {
        for (int op = 0; op < 4; op++) {
            int offset = channel + op * 4;
            reg(chip, part, 0x30 + offset, 0x71 + op);
            reg(chip, part, 0x40 + offset, op < 2 ? 0x23 : 0x10);
            reg(chip, part, 0x50 + offset, 0x5f); reg(chip, part, 0x60 + offset, 0x80);
            reg(chip, part, 0x70 + offset, 0x00); reg(chip, part, 0x80 + offset, 0x2a);
            reg(chip, part, 0x90 + offset, 0x00);
        }
        reg(chip, part, 0xb0 + channel, 0x34); reg(chip, part, 0xb4 + channel, 0xf3);
        reg(chip, part, 0xa4 + channel, 0x22 + channel); reg(chip, part, 0xa0 + channel, 0x69 + channel * 7);
    }
    for (int channel = 0; channel < 6; channel++)
        reg(chip, 0, 0x28, 0xf0 | (channel < 3 ? channel : channel + 1));
}

template <class Chip>
long long render(Chip &chip, int frames) {
    long long checksum = 0;
    for (int frame = 0; frame < frames; frame++) {
        Sample sample = chip.frame();
        checksum += std::abs(sample[0]) + std::abs(sample[1]);
    }
    return checksum;
}

struct Result {
    long long checksum;
    int snapshotErrors;
    int negativeChanges;
    std::vector<double> timings;
};

template <class Chip>
Result measure(int frames, int warmups, int iterations) {
    std::vector<double> timings;
    for (int run = -warmups; run < iterations; run++) {
        Chip chip; program(chip);
        auto started = std::chrono::steady_clock::now();
        render(chip, frames);
        auto elapsed = std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - started).count();
        if (run >= 0) timings.push_back(elapsed / frames);
    }
    Chip validation; program(validation);
    long long checksum = render(validation, frames);
    render(validation, std::min(frames, 256));
    auto snapshot = validation.save();
    long long expected = render(validation, 128);
    validation.restore(snapshot);
    long long actual = render(validation, 128);
    validation.restore(snapshot);
    reg(validation, 0, 0x28, 0xf0);
    long long control = render(validation, 128);
    validation.restore(snapshot);
    reg(validation, 0, 0x28, 0x00);
    long long changed = render(validation, 128);
    return {checksum, expected == actual ? 0 : 1, control == changed ? 0 : 1, timings};
}

void printResult(const char *name, const Result &result) {
    std::printf("\"%s\":{\"checksum\":%lld,\"snapshot_errors\":%d,"
                "\"negative_control_changes\":%d,\"nanoseconds_per_frame\":[",
                name, result.checksum, result.snapshotErrors, result.negativeChanges);
    for (size_t index = 0; index < result.timings.size(); index++) {
        if (index) std::printf(",");
        std::printf("%.3f", result.timings[index]);
    }
    std::printf("]}");
}

int main(int argc, char **argv) {
    if (argc != 4) return 2;
    int frames = std::atoi(argv[1]), warmups = std::atoi(argv[2]), iterations = std::atoi(argv[3]);
    if (frames <= 0 || warmups < 0 || iterations <= 0) return 2;
    Result nuked = measure<Nuked>(frames, warmups, iterations);
    Result ymfm = measure<Ymfm>(frames, warmups, iterations);
    std::printf("{\"implementations\":{");
    printResult("c-nuked", nuked); std::printf(","); printResult("cpp-ymfm", ymfm);
    std::printf("}}\n");
    return 0;
}
