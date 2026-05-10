/*
 * Copyright (C) 2024 LibreMobileOS Foundation
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "CameraProviderExtension.h"

#include <algorithm>
#include <fstream>

#define TORCH_BRIGHTNESS "brightness"
#define TORCH_MAX_BRIGHTNESS "max_brightness"

#define TOGGLE_SWITCH "/sys/devices/platform/soc/c42d000.qcom,spmi/spmi-0/0-05/c42d000.qcom,spmi:qcom,pm6150l@5:qcom,leds@d300/leds/led:switch_2/brightness"

static std::string kTorchLedPath =
        "/sys/devices/platform/soc/c42d000.qcom,spmi/spmi-0/0-05/"
        "c42d000.qcom,spmi:qcom,pm6150l@5:qcom,leds@d300/leds/led:torch_0";

static bool sTorchEnabled = false;
static bool sInitialized = false;

template <typename T>
static void set(const std::string& path, const T& value) {
    std::ofstream file(path);
    file << value;
}

template <typename T>
static T get(const std::string& path, const T& def) {
    std::ifstream file(path);
    T result;
    file >> result;
    return file.fail() ? def : result;
}

/*
 * Sync sTorchEnabled with actual sysfs state on first call.
 * Prevents stale flag after HAL process restart while switch
 * remains physically open in the kernel.
 */
static void ensureInitialized() {
    if (sInitialized) return;
    int switchVal = get<int>(TOGGLE_SWITCH, 0);
    if (switchVal != 0) {
        set(TOGGLE_SWITCH, 0);
        auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;
        set(node, 0);
    }
    sTorchEnabled = false;
    sInitialized = true;
}

bool supportsTorchStrengthControlExt() {
    return true;
}

bool supportsSetTorchModeExt() {
    return true;
}

int32_t getTorchDefaultStrengthLevelExt() {
    return 59;
}

int32_t getTorchMaxStrengthLevelExt() {
    auto node = kTorchLedPath + "/" + TORCH_MAX_BRIGHTNESS;
    return get(node, 255);
}

int32_t getTorchStrengthLevelExt() {
    auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;
    return get(node, 0);
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    ensureInitialized();

    auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;

    if (!enabled || torchStrength <= 0) {
        if (sTorchEnabled) {
            set(node, 0);
            set(TOGGLE_SWITCH, 0);
            sTorchEnabled = false;
        }
        return;
    }

    torchStrength = std::min(
            torchStrength,
            getTorchMaxStrengthLevelExt());

    /*
     * Always close and reopen the switch around brightness writes.
     * This ensures the PMIC sees a clean enable sequence and prevents
     * the switch staying open from a prior session desynchronizing
     * the Camera HAL torch state on Android 16.
     * Pattern borrowed from pm8350c implementation.
     */
    set(TOGGLE_SWITCH, 0);
    set(node, torchStrength);
    set(TOGGLE_SWITCH, 255);
    sTorchEnabled = true;
}

void setTorchModeExt(bool enabled) {
    if (!enabled) {
        setTorchStrengthLevelExt(0, false);
        return;
    }

    setTorchStrengthLevelExt(
            getTorchDefaultStrengthLevelExt(),
            true);
}
