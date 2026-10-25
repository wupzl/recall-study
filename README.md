# 回想 / Recall

一个离线学习与休息计时器，提供原生 Android App 和 Windows 桌面 EXE。无需注册，手机与电脑独立使用。

## 下载与使用

[**打开最新版本下载页**](https://github.com/wupzl/recall-study/releases/latest)

| 平台 | 安装包 | 使用方式 |
| --- | --- | --- |
| Android 8.0+ | [下载 APK](https://github.com/wupzl/recall-study/releases/download/v1.1.0/Recall-Study.apk) | 传到手机直接安装 |
| Windows 10 / 11 | [下载 Windows 压缩包](https://github.com/wupzl/recall-study/releases/download/v1.1.0/Recall-Windows.zip) | 解压，双击 `Recall-Study.exe` |

源码下载不包含安装包，请从上面的 Release 下载。自己构建时，产物在 `dist/`：

- `Recall-Study.apk`：Android 8.0 及以上，直接安装。
- `Recall-Windows.zip`：Windows 10 / 11，解压后运行 `Recall-Study.exe`，无需安装程序。
- `Recall-Study.exe`：可直接运行的 Windows 程序。旁边的 `.config` 提供运行时和高清屏设置，建议一起保留。

### 界面预览

![Windows 学习界面](docs/screenshots/windows.png)

<details>
<summary>查看 Android 界面</summary>

<img src="docs/screenshots/android.png" alt="Android 学习界面" width="300" />

</details>

### Android

1. 把 APK 传到手机，打开安装；按手机提示允许该来源安装应用。
2. 打开“回想”，设置本次总时长（1～90 分钟），再设置学习范围和休息范围。默认学习 20～30 分钟、休息 5～8 分钟。
3. 首次点击开始时，允许通知，并按引导允许不受电池优化限制；返回应用再次点击开始。
4. 点击“试听”，调节应用音量与手机**闹钟音量**。应用使用闹钟音频通道，勿扰设置仍可能影响声音。
5. 开始后可以锁屏或切换应用。通知栏可查看状态、暂停、继续或结束。

锁屏提醒采用前台服务和会话期间的 CPU 唤醒锁。Android 的省电模式会限制后台执行，电池优化豁免可让应用在休眠期间持有唤醒锁，参考 [Android 官方说明](https://developer.android.com/training/monitoring-device-state/doze-standby)。部分品牌还需要在系统设置中允许后台运行、自启动，或锁定后台任务。强行停止应用、系统主动终止服务或关机后无法继续提醒。这里不承诺绕过手机厂商的后台限制。

### Windows

1. 解压 `Recall-Windows.zip`，双击 `Recall-Study.exe`。
2. 设置本次总时长、学习范围和休息范围，试听并调节音量，点击“开始学习”。声音同时受 Windows 的音量与输出设备设置影响。
3. 最小化窗口或在会话期间点击关闭按钮，会收起到系统托盘，学习继续运行。双击托盘图标打开窗口。
4. 托盘右键菜单提供暂停、继续、结束和退出。

学习期间会阻止电脑自动进入睡眠，允许显示器熄灭。用户手动睡眠时自动暂停；唤醒后点击继续。程序为本地生成的未签名 EXE，Windows 可能显示“未知发布者”。Windows 版本使用系统通常自带的 .NET Framework 4.8，不需要安装 .NET 10 或额外下载依赖。

## 计时与设置

- 默认每轮随机学习 **20～30 分钟**，然后随机安静休息 **5～8 分钟**；每轮独立重新抽取时长，以毫秒为单位均匀随机。
- 用户可以修改学习下限、学习上限、休息下限、休息上限。每项填写整数 **1～90 分钟**，同一范围下限不能大于上限。
- 上下限填相同值即为固定时长，例如学习 25～25、休息 5～5。有效设置自动保存在本机。
- 开始学习后本次范围锁定，暂停时也不能修改；结束后可修改下一次节奏。
- 学习结束播放休息提示音，休息结束播放另一种提示音并自动继续学习。休息阶段无需刻意回忆。
- 本次总时长范围 1～90 分钟，默认 45 分钟；**包含休息，暂停除外**。例如设置 60 分钟，学习与休息累计满 60 分钟结束。
- 总时长到点优先结束：最后一段学习或休息可能被截短。如果总时长短于首次学习间隔，本次会话不会进入休息。
- 暂停冻结当前阶段和总计时，继续时保留当前剩余时间，不重新抽取时长。
- 提前结束保存已经计时的会话时间，自然结束播放完成提示音。
- 如果系统延迟回调，不连续补播提醒；尚未到总时长时，新阶段从实际触发时开始计时。
- 随机抽样可能自然产生相同或接近的间隔，不强行去重。

这些默认值是可调整的使用节奏，不代表已被研究证明为适用于所有人的最优学习方案。

## 从 v1.0.0 更新

Android 使用相同签名、版本号升级为 1.1.0，可以直接安装新版 APK 覆盖更新，无需先卸载。Windows 先从托盘退出旧版，再解压新版运行。原有记录保留，新增范围设置初始化为 20～30 / 5～8 分钟。

## 本地数据

记录会话开始时间、会话用时（含休息）、计划时长、间歇次数、完成状态，保留最近 500 条。

- Windows：`%LOCALAPPDATA%\RecallStudy\data.json`，使用原子替换保存，保留上一版 `.bak`。
- Android：应用私有 SharedPreferences，未申请网络权限，未启用系统备份。
- 活动会话每 5 秒保存检查点。异常退出后，再次打开会把上次会话保存为“中断”，不自动补播提醒或重新开始。异常退出的记录最多可能少计最近约 5 秒。
- 手机与电脑之间不自动同步。卸载 Android 应用会清除该设备记录。

## 源码与构建

```text
android/       Java 原生 Android 界面、前台服务、计时引擎
windows/       C# WPF 界面、托盘、本地记录、计时引擎
tests/         两端共享行为轨迹与实际运行检查
build.py       本地构建、生成提示音与图标、APK 签名
dist/          交付产物
qa/            本机验证截图与结果，不包含在分发包
.keys/         本地 APK 签名密钥，不包含在分发包
```

构建环境：Windows、Python 3 + Pillow、.NET Framework 4.8 编译器、JDK 17+、Android SDK Platform 35 / Build Tools 35.0.0。设置 `JAVA_HOME` 与 `ANDROID_HOME` 后运行：

```powershell
python build.py
python build.py --only tests
python build.py --only windows
python build.py --only android
```

无需 Gradle、Maven 或 npm 下载依赖。Android 通过 aapt2、javac、D8、zipalign、apksigner 构建，采用 APK v2/v3 签名。前台服务按 [Android 14+ 的类型要求](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)声明 `specialUse`。目前交付方式为直接安装，不涉及应用商店上架。

**请私下备份 `.keys/` 文件夹**。后续 APK 更新必须使用同一密钥；不要将密钥上传公开仓库或随安装包分发。

## 验证

`build.py` 对 Java 和 C# 引擎运行相同的行为轨迹，各包含 20,062 个断言：默认学习与休息随机上下界、每轮重新随机、自定义范围、固定时长、非法范围、会话中禁止改节奏、两阶段暂停、总时长截断、迟到回调、提前结束与 90 分钟限制。测试使用可注入时钟覆盖默认长时长，无需等待真实 30 分钟。

`tests/android_smoke.py` 在隔离的 Android 15 模拟器上使用用户可设置的“学习 1～1 分钟、休息 1～1 分钟”，执行真实 3 分钟会话。检查覆盖安装更新、默认范围、输入校验、设置跨进程保存、暂停、继续、锁屏与深度休眠、学习/休息提醒、到点结束和完成记录，输出 `qa/android-result.json`。

`tests/WindowsIntegration.cs` 驱动实际 WPF 界面，以相同的 1 分钟学习 / 1 分钟休息设置执行真实 3 分钟会话，检查旧格式数据迁移、输入校验、设置保存、两阶段暂停、托盘运行、自动继续和完成记录。验证数据隔离在 `qa/windows-data-*`。运行 `tests/windows_smoke.ps1` 后查看 `qa/windows-result.json` 与日志。

短配置的实际运行检查与默认长配置的时钟测试相结合；不将模拟器检查视为手机真机对扬声器及厂商省电策略的验证。首次手机使用时请先试听并完成一轮锁屏测试。
