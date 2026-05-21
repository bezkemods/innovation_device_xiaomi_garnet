/*
 * Copyright (C) 2024 LibreMobileOS Foundation
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include "CameraProviderExtension.h"

#include <fstream>

#define TORCH_BRIGHTNESS "brightness"
#define TORCH_MAX_BRIGHTNESS "max_brightness"

static std::string kTorchLedPath =
    "/sys/devices/platform/soc/c42d000.qcom,spmi/spmi-0/0-05/"
    "c42d000.qcom,spmi:qcom,pm6150l@5:qcom,leds@d300/"
    "leds/led:torch_0";

/**
 * Write value to path and close file.
 */
template <typename T>
static void set(const std::string& path, const T& value) {
    std::ofstream file(path);
    file << value;
}

/**
 * Read value from the path and close file.
 */
template <typename T>
static T get(const std::string& path, const T& def) {
    std::ifstream file(path);
    T result;

    file >> result;
    return file.fail() ? def : result;
}

bool supportsTorchStrengthControlExt() {
    return true;
}

bool supportsSetTorchModeExt() {
    return true;
}

int32_t getTorchDefaultStrengthLevelExt() {
    // Safe default
    return 59;
}

int32_t getTorchMaxStrengthLevelExt() {
    // Safe cap
    return 255;
}

int32_t getTorchStrengthLevelExt() {
    auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;
    return get(node, 0);
}

void setTorchStrengthLevelExt(int32_t torchStrength, bool enabled) {
    auto node = kTorchLedPath + "/" + TORCH_BRIGHTNESS;

    if (!enabled)
        torchStrength = 0;

    set(node, torchStrength);
}

void setTorchModeExt(bool enabled) {
    int32_t strength = getTorchDefaultStrengthLevelExt();
    setTorchStrengthLevelExt(strength, enabled);
}
