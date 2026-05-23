# CellSentinelApp

CellSentinelApp 是一款面向 **AI 智能体时代** 的 Android 移动网络智能感知、自治路测与现场分析平台。项目以端侧无线网络数据采集为基础，将 LTE、5G NR、Wi-Fi、GPS、设备状态、信令事件日志、地图轨迹与 AI Agent 分析能力连接起来，形成从“现场感知、数据沉淀、智能分析、报告生成”到“优化建议输出”的闭环。

它不仅是传统蜂窝网络路测工具，更是面向 AI 网络运维的端侧数据入口。通过实时采集 RSRP、RSRQ、SINR、PCI、TAC、CI、EARFCN/NRARFCN、频段、邻区、TA、CQI、NR CSI 指标、网络制式变化、位置轨迹和测速结果，CellSentinelApp 可以为 AI 智能体提供高质量现场上下文，支撑弱覆盖识别、切换异常研判、网络质量评分、巡检任务辅助、故障归因、优化建议生成和 PDF 分析报告输出。

## 项目亮点

- **AI 智能体分析**：内置 Agent 对话界面，可接入 Hermes / OpenAI-compatible Chat Completions 接口，对路测数据和信令事件进行专业网络优化分析。
- **AI 报告生成**：支持将智能体分析结论、原始上下文和当前网络信息导出为 PDF 报告，便于巡检归档、问题复盘和工程交付。
- **蜂窝网络实时感知**：支持 LTE、5G NR、WCDMA、GSM、CDMA 等网络信息采集，展示关键无线指标和小区参数。
- **多 SIM 支持**：按设备能力识别多 SIM 卡网络状态，分别展示运营商、制式、信号质量和小区信息。
- **自治路测能力**：基于 GPS 记录移动轨迹和信号采样点，支持 RSRP、SINR、RSRQ 颜色分级渲染。
- **地图覆盖可视化**：集成 osmdroid，支持 OSM、ESRI 卫星、Google 卫星、高德卫星等图层，展示服务小区、邻区、轨迹和 Wi-Fi 点位。
- **数据导出与回放**：路测记录支持 CSV / KML 导出，支持 CSV 路测文件回放，便于复盘移动过程中的网络变化。
- **信令事件日志**：基于 Android Telephony API 记录服务状态、RAT 变化、PCI 变化、信号强度变化等事件，并支持 CSV 导出和 AI 分析。
- **现场巡检工具箱**：提供 CI/eNB 解析、EARFCN/NRARFCN 频点频段换算、TA 距离估算、方位角计算等工程工具。
- **网络检测能力**：集成 Wi-Fi 信息、位置状态、设备信息和公网/自定义服务器测速。
- **服务端对接**：支持主服务器与备用服务器配置，可对接 RuoYi 扩展接口上传信号快照和路测记录。

## 应用截图

| 信号监测 | 路测地图 |
| --- | --- |
| ![Signal Monitoring](app/src/main/res/drawable/author/Screenshot_20260523_113557_CellSentinelApp.jpg) | ![Drive Test Map](app/src/main/res/drawable/author/Screenshot_20260523_113618_CellSentinelApp.jpg) |

| 数据回放 | AI 智能分析 |
| --- | --- |
| ![Drive Test Playback](app/src/main/res/drawable/author/Screenshot_20260523_113641_CellSentinelApp.jpg) | ![AI Agent Analysis](app/src/main/res/drawable/author/Screenshot_20260523_113928_CellSentinelApp.jpg) |

## 功能模块

### 信号监测

- 实时展示 LTE / 5G NR / WCDMA / GSM / CDMA 网络状态。
- 展示 RSRP、RSRQ、SINR、PCI、TAC、CI、MCC/MNC、EARFCN、NRARFCN、频段等信息。
- 支持 LTE TA、CQI、带宽以及 NR SS-RSRP、SS-RSRQ、SS-SINR、CSI-RSRP、CSI-RSRQ、CSI-SINR 等扩展指标。
- 使用仪表盘和历史曲线展示信号质量变化。
- 展示邻区信息，辅助现场覆盖和干扰判断。

### 路测与地图

- 基于 GPS 采集路测轨迹和信号采样点。
- 根据 RSRP、SINR、RSRQ 对轨迹进行颜色分级。
- 支持地图缩放、定位、测距、图层切换和服务小区/邻区覆盖展示。
- 支持 Wi-Fi 图层，将扫描到的 Wi-Fi 热点叠加到当前位置。
- 支持 CSV / KML 导出、服务端上传、记录清除和路测回放。
- 可将路测数据一键发送给 AI 智能体进行覆盖质量、切换、弱覆盖和优化建议分析。

### AI 智能体

- 支持配置 Hermes / OpenAI-compatible API 地址、Token 和模型。
- 支持模型列表拉取和连接测试。
- 支持普通对话和带上下文的网络数据分析。
- 可从路测模块和信令日志模块直接触发 AI 分析。
- 支持 Markdown 风格结果展示。
- 支持将 AI 分析结论导出为 PDF 报告。

### 信令事件日志

- 记录服务状态变化，例如在网、无服务、仅限紧急呼叫、飞行模式。
- 记录 RAT 变化，例如 LTE、NR、UMTS、HSPA、GSM 等网络类型切换。
- 基于 PCI 变化推断切换事件。
- 记录显著 RSRP 变化事件。
- 支持按事件类型和 SIM 卡过滤展示。
- 支持 CSV 导出与 AI 智能分析。

### 检测与工具

- Wi-Fi 信息：查看当前连接、周边扫描结果、RSSI、信道和 BSSID。
- 位置信息：查看 GPS/网络定位结果。
- 设备信息：查看 Android 设备和系统信息。
- 测速：支持 Cloudflare 公网测速和自定义服务端测速。
- 工具箱：支持 CI 解析、EARFCN/NRARFCN 计算、TA 距离估算和方位角计算。

## 技术栈

- Android Java
- Gradle Kotlin DSL
- Android Gradle Plugin 9.2.1
- minSdk 24 / targetSdk 36 / compileSdk 36.1
- AndroidX AppCompat
- Material Components
- ViewPager / RecyclerView
- OkHttp
- osmdroid
- Android PdfDocument
- JUnit / Espresso

## 项目结构

```text
app/src/main/java/com/asun/cellsentinelapp/
├── activity/     # MainActivity、LoginActivity
├── fragment/     # 信号、路测、工具、检测、AI 智能体、信令日志等页面
├── manager/      # 蜂窝信号、路测、信令日志、缓存管理
├── model/        # LTE/NR 小区和路测数据模型
├── network/      # Hermes AI、RuoYi、CellSentinel 服务端接口
├── util/         # 设置、频点解码、Markdown、PDF 报告工具
└── view/         # 信号仪表盘、曲线图、小区扇区绘制
```

## 构建与运行

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle 同步完成。
3. 连接 Android 真机或启动模拟器。
4. 运行 `app` 模块。

命令行构建 Debug 包：

```powershell
.\gradlew.bat assembleDebug
```

命令行构建 Release 包：

```powershell
.\gradlew.bat assembleRelease
```

## 权限说明

应用会请求以下权限：

- 位置权限：用于读取蜂窝小区信息、记录 GPS 路测轨迹、展示地图定位。
- 电话状态权限：用于读取 SIM、网络制式、小区身份和信号强度。
- 网络权限：用于地图瓦片、测速、AI 接口调用和服务端数据上传。
- Wi-Fi 权限：用于读取当前 Wi-Fi 信息和扫描周边热点。
- 外部存储写入权限：用于旧版本 Android 上导出 CSV/KML/PDF 文件。

## 能力边界

当前“信令事件日志”记录的是 Android 系统公开接口可获得的网络状态事件，例如服务状态变化、LTE/NR/UMTS/GSM 等网络类型变化、基于 PCI 变化推断的切换事件，以及 RSRP 变化事件。

它不是完整的 LTE/NR 协议栈信令解析器，不能解析完整 RRC、NAS、MAC、RLC、PDCP 或基带原始信令消息。如需完整 LTE/NR 信令分析，通常需要工程机、root/厂商权限、Qualcomm DIAG/QXDM/QCAT、modem log、Radio HAL/vendor 私有接口，或基站侧/测试仪表日志等更底层的数据源。

## English Overview

CellSentinelApp is an Android-based intelligent mobile network sensing, autonomous drive-test, and AI Agent analysis platform. It combines LTE, 5G NR, Wi-Fi, GPS, device telemetry, signaling event logs, map visualization, server upload, and AI-powered reporting into a complete field workflow.

The app collects radio metrics such as RSRP, RSRQ, SINR, PCI, TAC, CI, EARFCN/NRARFCN, bands, neighbor cells, TA, CQI, NR CSI metrics, RAT changes, location tracks, and speed-test results. These structured field data can be sent to an AI Agent for weak-coverage detection, handover analysis, network quality scoring, issue diagnosis, optimization suggestions, and PDF report generation.

## Contact

WeChat QR Code:

![WeChat QR Code](app/src/main/res/drawable/author/authorwechat.png)

## Open Source Notice

本项目采用 Apache License 2.0 开源。任何人都可以自由使用、复制、修改、分发，并可用于学习、研究或商业场景。使用时请遵守仓库中的 `LICENSE` 文件。

This project is open source under the Apache License 2.0. Anyone may use, copy, modify, distribute, and apply it for learning, research, or commercial purposes, subject to the `LICENSE` file in this repository.
