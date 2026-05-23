# CellSentinelApp

## 中文说明

CellSentinelApp 是一款面向 Android 设备的蜂窝网络监测、路测和现场排障工具。它可以读取手机当前的移动网络状态，展示 LTE / 5G NR / WCDMA / GSM 等网络的关键无线指标，并结合地图、GPS、Wi-Fi、测速和日志能力，帮助用户观察网络覆盖、信号质量和移动过程中的网络变化。

### 主要功能

- 实时蜂窝信号监测：展示 RSRP、RSRQ、SINR、PCI、CI、TAC、MCC/MNC、EARFCN/NRARFCN、频段等信息。
- 多 SIM 卡支持：根据设备能力展示不同 SIM 卡的网络与信号数据。
- 路测记录：基于 GPS 记录移动轨迹和信号采样点，支持按 RSRP、SINR、RSRQ 进行颜色分级显示。
- 地图展示：集成 osmdroid，支持 OSM、ESRI 卫星、Google 卫星、高德卫星等图层。
- 数据导出与回放：路测数据可导出 CSV / KML，并支持 CSV 路测记录回放。
- Wi-Fi 信息：查看当前连接信息、扫描周边 Wi-Fi，并可在路测地图上叠加 Wi-Fi 点位。
- 设备与位置信息：查看 Android 设备、定位和网络相关信息。
- 信令日志：记录关键网络变化事件，并支持 CSV 导出。
- 网络测速：支持公网测速和自定义服务器测速。
- 服务端对接：支持配置主服务器和备用服务器，并可上传信号快照、路测记录等数据。

### 技术栈

- Android Java
- Gradle Kotlin DSL
- AndroidX AppCompat / Material Components
- OkHttp
- osmdroid
- JUnit / Espresso

### 构建与运行

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle 同步完成。
3. 连接 Android 设备或启动模拟器。
4. 运行 `app` 模块。

也可以使用命令行构建：

```powershell
.\gradlew.bat assembleDebug
```

### 权限说明

应用会请求位置、电话状态、网络访问、Wi-Fi 状态等权限。这些权限用于读取蜂窝网络信息、记录路测轨迹、扫描 Wi-Fi、访问地图瓦片、测速和上传数据。

## English

CellSentinelApp is an Android field tool for cellular network monitoring, drive testing, and on-site troubleshooting. It reads the current mobile network state from the device, displays key radio metrics for LTE, 5G NR, WCDMA, and GSM, and combines maps, GPS, Wi-Fi, speed tests, and logs to help users understand coverage, signal quality, and network changes while moving.

### Features

- Real-time cellular signal monitoring: RSRP, RSRQ, SINR, PCI, CI, TAC, MCC/MNC, EARFCN/NRARFCN, band information, and more.
- Multi-SIM support: displays signal and network data for supported SIM cards.
- Drive testing: records GPS tracks and signal samples, with color grading by RSRP, SINR, or RSRQ.
- Map view: powered by osmdroid, with OSM, ESRI satellite, Google satellite, and Amap satellite layers.
- Export and playback: export drive-test records to CSV / KML and replay CSV records on the map.
- Wi-Fi tools: view current Wi-Fi details, scan nearby access points, and overlay Wi-Fi markers on the drive-test map.
- Device and location panels: inspect Android device, location, and network information.
- Signaling log: record important network-change events and export them to CSV.
- Speed test: supports public Cloudflare speed testing and custom server testing.
- Server integration: configurable primary and backup server URLs for uploading signal snapshots and drive-test records.

### Tech Stack

- Android Java
- Gradle Kotlin DSL
- AndroidX AppCompat / Material Components
- OkHttp
- osmdroid
- JUnit / Espresso

### Build And Run

1. Open the project root in Android Studio.
2. Wait for Gradle sync to finish.
3. Connect an Android device or start an emulator.
4. Run the `app` module.

Command-line debug build:

```powershell
.\gradlew.bat assembleDebug
```

### Permissions

The app requests location, phone state, internet, and Wi-Fi permissions. These permissions are used to read cellular network data, record drive-test tracks, scan Wi-Fi, access map tiles, run speed tests, and upload data.

## 联系作者 / Contact

微信二维码 / WeChat QR Code:

![WeChat QR Code](app/src/main/res/author/authorwechat.png)

## 开源声明 / Open Source Notice

本项目采用 Apache License 2.0 开源。任何人都可以自由使用、复制、修改、分发和用于学习、研究或商业场景，但请遵守仓库中的 `LICENSE` 文件。

This project is open source under the Apache License 2.0. Anyone may use, copy, modify, distribute, and apply it for learning, research, or commercial purposes, subject to the `LICENSE` file in this repository.
