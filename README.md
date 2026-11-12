# VoltWise: Power Consumption and Activity Monitor (Android)
**An Operating Systems & Mobile Systems Engineering Project**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26-34)-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin_1.9.22-blue.svg)](https://kotlinlang.org/)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM_%2B_Clean_Architecture-orange.svg)]()
[![Database](https://img.shields.io/badge/Database-Room_SQLite-green.svg)]()

---

## 1. Executive Summary & Project Objective

Modern mobile operating systems must balance responsiveness and interactive performance against battery endurance. **VoltWise** is an advanced, production-grade Android application designed to:
1. **Identify tasks, processes, and applications consuming battery resources** in real time and over configurable historical observation windows.
2. **Log hardware power telemetry and activity snapshots** continuously using an energy-efficient background foreground service and local Room SQLite database.
3. **Make historical monitoring data available for end-users to review** through rich visual analytics (interactive battery/temperature scrubber charts, discharge sessions, power hog anomaly diagnostics, and one-click CSV export).

---

## 2. Operating System & Power Attribution Concepts

### 2.1 Hardware Power Telemetry (`BatteryManager`)
Android provides hardware-level battery status through the Linux kernel power supply subsystem via the `BatteryManager` API:
* **Instantaneous Current ($I$)**: Measured via `BATTERY_PROPERTY_CURRENT_NOW` in microamperes ($\mu\text{A}$), normalized by VoltWise to signed milliamperes ($\text{mA}$). Negative values indicate discharging; positive values indicate charging.
* **Instantaneous Voltage ($V$)**: Extracted from sticky broadcast `Intent.ACTION_BATTERY_CHANGED` (`EXTRA_VOLTAGE`) in millivolts ($\text{mV}$).
* **Instantaneous Electrical Power ($P$)**:
  $$P(\text{mW}) = V(\text{V}) \times |I(\text{mA})| = \frac{V(\text{mV})}{1000} \times |I(\text{mA})|$$
* **Thermal Telemetry ($T$)**: Battery temperature in tenths of a degree Celsius ($0.1^\circ\text{C}$), essential for thermal throttling detection.
* **Remaining Time Estimation**:
  $$T_{\text{remaining}} = \frac{C_{\text{remaining}}(\text{mAh})}{|I_{\text{drain}}(\text{mA})|}$$

### 2.2 Task & Activity Monitoring (`UsageStatsManager`)
To identify which apps run and how long they occupy system resources without requiring root access, VoltWise utilizes Android's `UsageStatsManager` (`android.permission.PACKAGE_USAGE_STATS`):
* **Foreground Duration ($t_{\text{fg}}$)**: Exact wall-clock duration an application held the interactive display, driving CPU, GPU, display backlighting, and touch digitizer.
* **Standby Buckets (`getAppStandbyBucket`)**: Android OS groups applications into 5 power-management tiers:
  * `ACTIVE` (10): Unrestricted execution, user is actively engaged.
  * `WORKING_SET` (20): Used frequently, mild job deferrals.
  * `FREQUENT` (30): Used periodically; network sync and alarms are throttled.
  * `RARE` (40) / `RESTRICTED` (45): Strict restrictions on background work to safeguard battery life.

### 2.3 Mathematical Power Attribution Engine
Because raw hardware current cannot be isolated on a per-app basis by non-system apps in modern Linux kernels, VoltWise implements a **Scientific Power Attribution Model**:

1. **Total Period Energy Discharge ($E_{\text{total}}$)**:
   $$E_{\text{total}} = \max\left(\frac{\Delta\text{Level}}{100} \times C_{\text{battery}},\, \frac{|I_{\text{avg}}| \times \Delta t}{3600}\right) \quad (\text{mAh})$$
2. **App Weighting ($W_i$)**:
   $$W_i = t_{\text{fg}, i} \times \mu_{\text{bucket}, i}$$
   where $\mu_{\text{bucket}}$ is the standby bucket weight ($1.0$ for `ACTIVE`, $0.85$ for `WORKING_SET`, $0.70$ for `FREQUENT`, $0.50$ for `RARE`, $0.30$ for `RESTRICTED`).
3. **App Energy Attribution ($E_i$)**:
   Subtracting display and base OS kernel baseline fraction ($f_{\text{baseline}} \approx 0.15$):
   $$E_i = (1 - f_{\text{baseline}}) \times E_{\text{total}} \times \left(\frac{W_i}{\sum_j W_j}\right) \quad (\text{mAh})$$
   $$\text{Percentage } P_i(\%) = \frac{E_i}{E_{\text{total}}} \times 100\%$$

---

## 3. Application Architecture

VoltWise is engineered using **MVVM (Model-View-ViewModel)** with **Clean Architecture**:

```
app/src/main/java/com/voltwise/powermonitor/
├── VoltWiseApp.kt                     # Application singleton & initialization
├── battery/
│   ├── BatteryInfo.kt                 # Telemetry domain data model
│   └── BatteryTelemetryManager.kt     # Hardware battery HAL & BatteryManager bridge
├── usage/
│   ├── AppUsageInfo.kt                # Application activity & resource model
│   └── AppUsageManager.kt             # UsageStatsManager & AppOps interface
├── estimator/
│   └── PowerAttributionEngine.kt      # Mathematical energy allocation & power hog detection
├── data/
│   ├── AppDatabase.kt                 # Room SQLite Database definition
│   ├── entity/
│   │   ├── BatteryLogEntity.kt        # Point-in-time telemetry records
│   │   ├── AppActivityLogEntity.kt    # Per-app activity and mAh attribution records
│   │   └── MonitoringSessionEntity.kt # Discharge sessions & cycles
│   ├── dao/
│   │   ├── BatteryDao.kt              # Reactive Flow queries for telemetry
│   │   ├── AppActivityDao.kt          # App power logs queries
│   │   └── MonitoringSessionDao.kt    # Session management queries
│   └── repository/
│       └── PowerRepository.kt         # Clean single-source-of-truth repository
├── service/
│   ├── BatteryMonitorService.kt       # Persistent Foreground Service with WakeLock protection
│   └── BatteryNotificationHelper.kt   # Low-overhead status notification
├── receiver/
│   ├── BootReceiver.kt                # Auto-resumes monitoring on device restart
│   └── BatteryReceiver.kt             # Charger connect/disconnect event logging
├── ui/
│   ├── MainActivity.kt                # Main container & bottom navigation
│   ├── custom/
│   │   ├── BatteryGaugeView.kt        # Animated circular Canvas battery gauge
│   │   └── BatteryTrendChartView.kt   # High-performance Canvas line chart with touch scrubber
│   ├── dashboard/
│   │   ├── DashboardFragment.kt       # Live telemetry & gauge view
│   │   └── DashboardViewModel.kt      # Real-time state flow
│   ├── apps/
│   │   ├── AppsFragment.kt            # Ranked application power breakdown
│   │   ├── AppsViewModel.kt           # Filtering, sorting, and attribution controller
│   │   └── AppUsageAdapter.kt         # Recycler adapter with deep link to settings
│   ├── history/
│   │   ├── HistoryFragment.kt         # Trend charts, session history & CSV export
│   │   ├── HistoryViewModel.kt        # Analytics & export controller
│   │   └── SessionAdapter.kt          # Past discharge sessions adapter
│   ├── diagnostics/
│   │   └── DiagnosticsFragment.kt     # Rogue app detector & Standby Buckets review
│   └── settings/
│       └── SettingsFragment.kt        # Sampling interval & permissions management
└── util/
    ├── FormatUtils.kt                 # Time, date, and duration formatters
    ├── LogExporter.kt                 # Formatted CSV exporter via FileProvider
    └── PreferenceManager.kt           # SharedPreferences wrapper
```

---

## 4. Key Features

| Feature | Description |
| :--- | :--- |
| **Real-Time Telemetry Dashboard** | Custom animated circular gauge displaying battery %, charging state, power draw ($mW$), instantaneous current ($mA$), voltage ($V$), and temperature ($^\circ C$). |
| **Application Power Breakdown** | Ranks all apps by estimated energy consumption ($mAh$ and $\%$ of total discharge). Filter by 1h, 6h, 24h, or 7 days, toggle user apps vs system services, and sort by power, duration, or name. |
| **Interactive Historical Charts** | Custom Canvas graph plotting battery level and temperature over time. Touch and drag across the timeline to inspect point-in-time metrics. |
| **Discharge Session Tracking** | Automatically groups discharge cycles (start time, duration, drop in battery $\%$, peak temperature, average drain rate). |
| **Diagnostics & Power Hog Detection** | Proactively flags rogue background apps consuming abnormal power or violating standby bucket policies. |
| **CSV Historical Data Export** | Generates standard CSV reports containing complete battery snapshots and app attribution records, ready to share via Google Drive, Email, or save locally. |
| **Configurable Sampling & Auto-Boot** | Choose between 1 min, 5 min, or 15 min sampling intervals. Automatically restarts monitoring upon device boot. |

---

## 5. System Permissions & Setup Flow

1. **Usage Access (`PACKAGE_USAGE_STATS`)**:
   * *Purpose*: Allows querying application foreground durations and standby buckets.
   * *How to Grant*: Navigated automatically via the in-app prompt to `Settings > Special app access > Usage access > VoltWise`.
2. **Foreground Service & Notifications (`FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`)**:
   * *Purpose*: Maintains low-overhead background logging without being terminated by Android's OOM killer.
3. **Battery Optimization Exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**:
   * *Purpose*: Prevents OEM aggressive power managers from pausing the logger during deep Doze mode.

---

## 6. How to Build & Run

### Prerequisites
* **Android Studio** (Hedgehog 2023.1.1 or newer recommended) or **Command Line Tools**
* **JDK 17** (configured as Gradle JVM)
* **Android SDK**: Compile SDK `34`, Min SDK `26`

### Building from Android Studio
1. Launch Android Studio and select **Open**.
2. Select the `osproj` folder.
3. Allow Gradle to synchronize dependencies.
4. Connect an Android device (or launch an Emulator running Android 8.0 to 14+).
5. Click **Run > Run 'app'** (`Shift + F10`).

### Building via Command Line
```powershell
# In PowerShell (Windows):
.\gradlew assembleDebug

# To install directly to connected device:
.\gradlew installDebug
```

### Running Unit Tests
```powershell
.\gradlew testDebugUnitTest
```

---

## 7. CSV Export Format Example

The generated CSV export contains two comprehensive sections:

```csv
=== VOLTWISE BATTERY TELEMETRY LOGS ===
Timestamp,DateTime,BatteryLevel(%),Voltage(mV),Temperature(C),Current(mA),Power(mW),Status,PlugType,ScreenActive
1700000000000,"2026-11-12 14:00",85,3920,30.5,-320.0,1254.4,DISCHARGING,BATTERY,true
1700000060000,"2026-11-12 14:01",85,3918,30.6,-315.0,1234.2,DISCHARGING,BATTERY,true

=== APPLICATION POWER & ACTIVITY ATTRIBUTION ===
Timestamp,DateTime,AppName,PackageName,ForegroundDuration(ms),ForegroundFormatted,EstPower(mAh),PercentOfTotal(%),StandbyBucket,IsSystemApp
1700003600000,"2026-11-12 15:00","YouTube","com.google.android.youtube",1800000,"30m 0s",125.4,26.1,ACTIVE,false
1700003600000,"2026-11-12 15:00","Chrome","com.android.chrome",900000,"15m 0s",62.7,13.1,WORKING_SET,false
```
