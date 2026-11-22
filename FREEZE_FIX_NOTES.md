# 0.3 本轮修改定位

实验目录独立于正式 HydrogenLauncher 仓库，未提交或上传。

- MyActivityManagerService.java：停止原主动 GC 误回收 QQPlayerProcess / 已运行服务。
- PackageParserApi21Plus.java：16 项 flags 缓存，Parcel 深复制避免路径修正污染。
- PluginProcessManager.java：每包 FutureTask 管理 Application 初始化，防止丢通知与重入等待。
- RunningProcessList.java：候选快照与跨进程签名检查分开。
- IActivityManagerHookHandle.java：新版服务绑定入口、两种服务连接回调签名、解绑/失败释放引用、ProviderHolder.mLocal 兼容复制。
- GuestInitializers.java：Provider 先取归一化组件信息，按进程和启用状态安装，之后仅调用一次 Application.onCreate。
- INotificationManagerHookHandle.java：通知包名与操作包名都使用宿主身份。
- UnifiedRuntime.java：状态报告在锁外通知监听器。
- DualSplash.java：NO_ARTWORK，仅取消专用启动图，初始化和原使用提示保留。高德 cover 仅处理 cover ID 的 ImageView 图片/背景；QQ AppStarter 只处理 root_view FrameLayout 的背景，保留全部子 View 和协议弹窗。
- tools/build_unified_modules_no_artwork.py：重建自有 helper DEX、按 entry 核查其他内容不变、重签并刷新模块哈希。

本轮验证：两模块私有导入；Android14 QQ 在线歌曲播放/暂停/恢复的系统音频状态；桌面歌曲、封面与歌词；高德完整首页、卡片搜索页面与附近结果；Android4.2.2 基础启动到原软件协议/提示页。详细状态以交付验证记录为准。

应用为普通 UID。未自动接受协议、未自动授予权限、未修改系统软件或正式包。未逐版本验证 Android4.3/5-13/15及以后，也未进行真实车机冷启动、倒车影像、导航语音、登录与长时稳定性测试。

QQ 主题引擎会在初始化后重新设置背景；本轮还将专供启动页面的 splash_logo / splash_logo_light 各密度 PNG 改为同尺寸透明图。只替换 aapt 资源表精确列出的启动 PNG；保留资源 ID、布局和主题逻辑，主界面图标不变。审计记录列出这些 PNG 和 helper DEX；除此之外的 manifest、native libs、其他 DEX 与资源 entry 逐字节核对不变。

高德启动回执仅由真正的 MainMapActivity 发出；UsbFillActivity 前置引导页即使收到 PREPARE 也不视为已完成，避免先返回桌面后又被真正地图页覆盖。保留原回执重试时序。

## 显式广播与进入桌面前初始化

- VirtualBroadcasts.java：显式命令使用受签名权限保护的专用内部投递通道，原 Intent 的 URI、MIME、分类、组件与数据完整保存在封装中，到准确接收器后还原；普通包范围广播继续按原过滤器匹配。原接收器的 classloader、有序广播结果和成对注销保留。
- PluginProcessManager.registerStaticReceiver：每个清单组件只创建一个接收器实例，另注册独立的显式投递过滤器；普通过滤器保留声明的接收权限。
- IActivityManagerHookHandle：广播封装时清除过期 resolvedType，广播 PendingIntent 同步替换路由字段，保留广播类型。
- UnifiedInitService.java：单一初始化所有者，真实接口依次就绪且复核成功后才准入主桌面；冷启动、复用和重试不重复发起启动。超时不自动准入主桌面。
- UnifiedBootstrapActivity / MainActivity / StartupApps：初始化提前到桌面内容创建前，准备页状态与重试、入口重验和避免重复预热；监听与 Handler 随生命周期释放。
- RuntimeProbe / DualSplash：QQ 就绪需有真实首页实例与布局，回执附带当前原生页面的焦点状态，避免完成返回时盖住其他前台窗口。保留原生协议和权限提示。

Android14 最终包冷启动、热复用、预开 QQ 场景均记录接口回执在进入主桌面之前；最终包系统音频记录为 started → paused → started，进度在原生 QQ 页面核对。API17 最终包只确认安装及 QQ 协议页；高德抓图超时，不能记作完整通过。
