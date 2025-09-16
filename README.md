# 📱 Xiaomi Garnet Device Tree

A comprehensive device tree for Xiaomi Garnet (Redmi Note 13 Pro 5G / Poco X6 5G), enabling the development and building of custom Android ROMs such as LineageOS and other AOSP-based distributions.

## 🪧 Attencion

This branch is the initial release is recommended to switch to Branch 2.0.11

## ✨ Features

This device tree is meticulously crafted to provide a robust foundation for custom Android development on the Xiaomi Garnet, offering a range of essential features:

*   🚀 **Full Device Compatibility:** Ensures all core hardware components of the Xiaomi Garnet are properly recognized and function seamlessly with AOSP-based ROMs.

## ⚙️ Installation Guide

To set up your build environment and integrate this device tree, follow these steps. This guide assumes you have a working AOSP/LineageOS build environment already configured.

### Prerequisites

*   A Linux-based operating system (Ubuntu 20.04+ LTS recommended).
*   `repo` tool installed and configured.
*   Sufficient disk space (250GB+ recommended).
*   AOSP/LineageOS Source.

### Step-by-Step Installation

1.  **Navigate to your AOSP/LineageOS source directory:**

    ```bash
    cd ~/android/lineage
    ```

    _(Replace `~/android/lineage` with your actual source directory path)_

2.  **Clone the `android_device_xiaomi_garnet` repository:**

    ```bash
    git clone https://github.com/android_device_xiaomi_garnet.git device/xiaomi/garnet
    ```

    This will place the device tree in the correct location for your build system to recognize it.

## 🚀 Usage Examples

Once the device tree is installed and proprietary files are extracted, you can use it to build a custom ROM for your Xiaomi Garnet.

1.  **Initialize the build environment:**

    ```bash
    source build/envsetup.sh
    ```

2.  **Select the device configuration:**

    ```bash
    lunch lineage_garnet-userdebug
    ```

3.  **Start the build process:**

    ```bash
    m -j$(nproc --all)
    ```

    This command will compile the entire ROM for your Xiaomi Garnet. The resulting `.zip` file, which can be flashed via a custom recovery, will be located in the `out/target/product/garnet/` directory upon successful completion.

**Tree Credits:**
AdarshGrewal
