# 单应用整合实验：构建与修改

日期：2026-10-26。此目录是独立源码快照；不是正式仓库，不含 .git，不执行任何上传操作。

## 当前方案

Android 系统只安装 cn.windchaser.unified。高德地图 6.5.0.601323 和 QQ音乐车机版 2.1.1.1 放在 APK 的 assets/unified 中，在本应用 UID 下的私有进程和目录内加载。首次准备不调用系统包安装器。它不是把三个 APK 交给系统依次安装的安装器。

当前 0.4 实验包的 minSdk=17（Android 4.2），targetSdk=24；不再仅限 API19，按平台版本选择兼容分支。两个内置 APK 的原生库均只有 32 位 ARMv7，因此宿主只打包 armeabi-v7a。ARM64 设备必须保留 32 位应用支持；x86/x86_64 设备必须具备可用的 ARMv7 转译。纯 64 位设备及无转译的 x86 设备不能因此兼容。

这不是“Android 4.2 到所有未来版本均已通过”的承诺。Android 4.2、4.4 与 14 的具体测试层级见交付的测试说明，其余版本未逐一实测。整合不承诺减少内存、加快冷启动，也不替代实车验证。

## 宿主构建

本轮使用 Android Studio 自带 JBR、Android SDK platform 35 / build-tools 35.0.0，以及目录内的 Gradle wrapper。设置 JAVA_HOME、ANDROID_HOME；在本目录执行：

```powershell
.\gradlew.bat :app:clean :app:assembleDebug --no-daemon --console=plain
```

依赖已缓存时可以加 --offline。产物是 app/build/outputs/apk/debug/app-debug.apk。不要用旧实验 :host 模块代替 :app。

SDK 路径与 Gradle 缓存不在源码包里，需使用本机环境。默认调试签名由 Android 工具创建；此源码包不含任何签名私钥。

## 修改运行库

实际运行库：experiments/card-runtime/upstream/project/Libraries/DroidPlugin。
宿主新增类在 app/src/main/java/cn/hydrogen/launcher/Unified*.java。
新增运行库逻辑在 com/morgoo/droidplugin/unified，并有 PackageManager、任务组件与广播等适配；全部对应源码均提供。

继承的实验适配记录与固定上游版本见 experiments/card-runtime/THIRD_PARTY_NOTICES.md。本次修改以 UNIFIED_NOTICES.md 为准。COPYING.LESSER / COPYING 和上游逐文件许可证均保留。

## 用自己的签名重建完整测试包

只编译宿主可验证修改后的运行库，但内置模块的自有信任代码和宿主回执要求双方真实证书一致。要换用自己的证书测试完整关联流程，需要同时重签两个内置 APK；不要把签名校验改成无条件通过。

1. 复制 app/src/main/unifiedAssets/unified 下的两个 APK 作为刷新输入。
2. 将 experiments/amap65-bridge/src/cn/windchaser/mapbridge/AdapterTrust.java 中 CERT_SHA256 改成自己证书的 SHA256。
3. tools/refresh_companions.py 提供 --keystore / --key-alias；密码通过 WINDCHASER_KEYSTORE_PASSWORD / WINDCHASER_KEY_PASSWORD 环境变量读取。
4. 该工具原本为同签名增量发布设计。当前地图输入已含 isTrustedVirtualPackageManager 适配，刷新时应保留已有这段插入，只替换自有 helper；并将工具的“输入/输出证书必须相同”断言改为核对自己的预期输出证书。首次从未插入该适配的输入重建则保留插入步骤。不要重复插入。
5. 刷新后核对商业原生库、原资源与 manifest 保持不变；回填 modules.json 的 bytes、sha256、certificate，并使用同一自己的证书签名宿主。

这些限制来自本项目自有适配代码，相关源码完整提供，接收者可以修改、重新链接运行库及组合应用。原软件的商业源码、服务授权与原生身份检查不属于 LGPL 源码；不附带登录凭据或私钥。

## 测试边界

测试使用专门的 API17/API19 ARM 模拟器，以及具备 Houdini 转译的 API34 模拟器。应用是普通 UID。没有为应用授予 Root 或系统签名权限。首次协议页由使用者自己确认；不能用修改同意标志代替确认。导航、登录、歌曲播放、蓝牙通道、实车倒车影像避让等需要使用者在对应环境验证。

## 新系统权限

进入准备页后可由用户打开悬浮窗授权、定位与文件权限请求及应用系统设置。只请求/跳转，不自动允许权限。targetSdk24 会使用运行时权限；从旧版升级保留的权限不能代表全新安装已授权。首次协议和隐私提示由使用者阅读并决定是否同意。

## 依赖

新增 AndroidHiddenApiBypass 6.1 AAR，版本、来源和校验信息见 COMPAT_DEPENDENCIES.json，完整 Apache 2.0 文本见 docs/licenses/AndroidHiddenApiBypass-LICENSE.txt。运行库全部对应源码随本快照保留，历史 upstream-changes.patch 仅记录原实验改动，当前源码与 UNIFIED_NOTICES.md 为本轮改动依据。

## 0.3 去启动图与卡死修复

修改详情见 FREEZE_FIX_NOTES.md。宿主构建前，两内置模块已用 tools/build_unified_modules_no_artwork.py 刷新自有启动 helper；修改 helper 所在 DEX 和 QQ 专用启动 PNG；manifest、原生库及其余 APK entry 均与输入逐字节核对。地图 cover 是 main_map_view 中专用 ImageView：只替换它的启动背景，不关闭覆盖层生命周期、不隐藏 SurfaceView 或地图按钮。

复现去启动图刷新：设置 JAVA_HOME、ANDROID_HOME 后执行 python tools/build_unified_modules_no_artwork.py --work <短路径工作目录> --apktool <apktool.jar>。该工具使用本机标准 debug.keystore 对内置模块签名；如用自己的不同证书，按上面的同签名说明同步自有信任代码和 modules.json，不能只单独重签宿主。源码交付不含私钥。

最终本地检查使用 :app:clean :runtime:clean :app:assembleDebug :app:lintDebug :runtime:lintDebug。Android 4.2.2 的本轮测试止于原软件提示页，Android14 则实际验证播放及地图操作；不能把模拟器结果视为所有系统和实车认证。

QQ 主题引擎会在初始化后重新设置背景；本轮还将专供启动页面的 splash_logo / splash_logo_light 各密度 PNG 改为同尺寸透明图。只替换 aapt 资源表精确列出的启动 PNG；保留资源 ID、布局和主题逻辑，主界面图标不变。审计记录列出这些 PNG 和 helper DEX；除此之外的 manifest、native libs、其他 DEX 与资源 entry 逐字节核对不变。

## 进入桌面前初始化

UnifiedInitService 是此单包实验入口的初始化所有者，接入 UnifiedBootstrapActivity 和 MainActivity。自动关联开启时，先用 LinkedAppCoordinator / RuntimeProbe 核查地图、QQ；两接口复核通过才显示主桌面。首次协议、权限和网络加载保持原实现；完整边界见 COMPAT_TEST_NOTES.md。原三 APK 的正式源码及发布不受此实验修改影响。


## 0.4 启动显示源码

宿主入口 UnifiedBootstrapActivity 显示统一启动画面；UnifiedInitService 仍以真实接口准入桌面，首次准备完成记录位于 unified-native-initialization 偏好中。主题设置的自动启动开关保留。

统一绘制类在 experiments/startup-ui/src，应用内启动层在 experiments/dual-splash/src/cn/windchaser/startup/StartupCurtain.java；后者通过 Activity 生命周期添加普通 View，未改变地图的系统悬浮卡片类型。正常手动打开高德和 QQ 不附加此启动会话。权限与协议由系统和用户处理。

修改这些 helper 后先执行 tools/build_unified_modules_no_artwork.py，再构建宿主。该脚本需要配套模块、SDK/JDK、自己的匹配签名，以及短 --work 目录；它保留基线并逐项检查商业 APK 非目标 entry 未被改动。最终交付不包含签名私钥。
