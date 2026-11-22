# 氢桌面：应用虚拟化与窗口适配实验 0.1

本实验已经在 API 19 / Android 4.4.2 x86 模拟器、普通应用 UID 下验证两个**独立测试 APK**的真实窗口可以限制在左右区域、独立接收点击、持续更新 SurfaceView、切换 Activity、显示卡片内弹窗及接收文字输入。两款测试 APK 不作为系统应用安装。

**没有验证原版高德、QQ 音乐的完整兼容性；不是正式桌面的嵌入功能更新。** 两款原 APK 仅有 ARMv7 库，当前 x86 镜像不能执行它们。原型采用真实窗口边界与布局适配，尚未实现任意逻辑分辨率的整屏缩放。软键盘、多指手势、地图 OpenGL 渲染、真实音频播放、正常登录、导航和长时间并行运行均未验收。

## 在车机上试用

1. 安装 `HydrogenCardLab-0.1-api19.apk`。应用名为「氢桌面容器实验」，包名 `cn.hydrogen.cardlab`，与已有桌面分开；不用设为默认桌面。本实验运行入口仅对 Android 4.4.x / API 19 开放。
2. 先点击「探针左」「探针右」。这两款很小的测试 APK 已内置，无需另外安装。确认左右计数按钮均能操作、动画仍在更新、第二页面可以返回。
3. 把原版高德和 QQ 音乐 APK 文件放到车机或 U 盘。点击「选高德 APK」「选 QQ APK」，分别选中对应文件。文件选择器需要系统提供 Android 文档选择功能。
4. 导入器检查 APK 信息、原文件 SHA256 和 CPU ABI。文件按原字节复制，保留原签名，不修改或重新签名。提示 `IMPORT ... result=1` 仅代表导入完成，不能作为应用启动或全部功能兼容的证明。
5. 观察地图是否正常出图、能否拖动/搜索，QQ 能否打开首页/歌单及正常播放；同时确认另一侧持续可用。虚拟环境的数据与系统独立安装的数据分开，可能需要原应用的正常初始化或授权。没有任何登录、授权或安全检查绕过。
6. 遇到空白、退出或操作异常时，点击「导出诊断」保存文字文件。部分原应用会覆盖默认崩溃处理器，导出的文件可能没有完整崩溃记录；有 ADB 时可用下述日志命令补充。点击「结束实验」关闭实验内的虚拟应用。

卸载「氢桌面容器实验」可清除本实验的 APK 副本、配置和数据。原版 APK 文件和系统独立安装的原应用不由本实验卸载。

## 工作方式与边界

固定版本 DroidPlugin 加载未修改 APK，guest 使用宿主 UID 下的独立进程和文件目录。新增窗口会话适配约束 guest Activity / Dialog 的位置和尺寸。宿主的占位 Activity 使用透明窗口主题；每个 stub 进程有独立任务 affinity，触摸时使用普通 `REORDER_TASKS` 权限把对应任务移到前面，交给系统处理正常焦点和生命周期。

这是**虚拟运行环境里的独立、有边界的真实应用窗口**，不是将系统独立安装、不同 UID 的 Activity 转成宿主的子 View。两个 guest 也没有被强行同时置于 RESUMED；原应用若在 onPause 停止地图或视频，仍需要继续适配。本测试探针的 SurfaceView 使用 Canvas 绘制，不含第三方 native 库。

首轮曾把 guest 窗口作为宿主的 `TYPE_APPLICATION_PANEL` 子窗口挂入；普通按钮可用，但 API 19 不接受 SurfaceView 再挂到子窗口下，因此未采用该方式作为当前实现。保留顶层应用窗口与原 token 后，SurfaceView 子窗口能正常工作。

没有使用 Root、系统签名、系统窗口管理权限、截图回放、无障碍点击或外部远程屏幕。测试镜像的 adbd 自带 Root；测试 APK 自身 UID 为 10054，未授予系统权限，测试 UI 命令明确降到 shell UID 2000。这个事实不能代替未 Root 真车的最终验收。

## 构建

需要 JDK 21、Android SDK Platform 35 和 Gradle wrapper 所需缓存/网络。

```powershell
.\build.ps1 -JavaHome 'D:\SOFT\Android Studio\jbr' -SdkPath 'C:\Users\Wnnp\AppData\Local\Android\Sdk'
```

输出 `host/build/outputs/apk/debug/host-debug.apk`。两个 probe APK 会自动构建并放入宿主 assets，原版商业 APK 不打包进实验 APK。

## 复现实测

仅在明确指定的项目测试模拟器上运行；脚本要求 API 19、1280×720 和 Python Pillow。不会自动挑选真实车机，不修改默认 HOME，不清空 logcat，不申请系统授权。

```powershell
python run_probe_checks.py --sdk 'C:\Users\Wnnp\AppData\Local\Android\Sdk' --serial emulator-5584
```

证据在 `evidence/probe-results.json`、`probe-logcat.txt`、`probe-commands.txt` 和对应 PNG。脚本还核查「结束实验」后两个 guest 窗口消失。脚本只自动检查可判定项目，弹窗与文字输入另做视觉检查；软件键盘没有自动判为通过。若 adbd 为 Root，脚本要求 UI 命令以 UID 2000 执行，不利用 Root 给应用授权。

补充日志可使用：

```powershell
adb -s 明确设备序列号 logcat -d -v time -s CardLab:I CardProbe:I AndroidRuntime:E '*:S'
adb -s 明确设备序列号 shell run-as cn.hydrogen.cardlab cat files/runtime-crashes.txt
```

## 源码与许可

完整实验对应源码和 Gradle wrapper 随交付提供。DroidPlugin 固定 commit 及下载哈希见 `upstream-provenance.json`；其源文件和许可声明保留在 `upstream/`。本实验新增代码按 LGPL-3.0-or-later 提供；第三方原文件遵循各自声明。详情见 `THIRD_PARTY_NOTICES.md`、`COPYING.LESSER`、`COPYING`。

`update_upstream_patch.py` 可从固定、校验哈希的官方归档重新生成忽略换行符差异的补丁；已有完整修改后源码可直接构建，不必重新下载。`package_release.py` 核验当前 APK、探针结果与视觉复核哈希一致后打包源码和实验交付文件。
