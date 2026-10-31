# WindChaser 单应用整合实验：第三方声明与修改说明

本实验日期为 2026-10-26，独立于已发布的追风桌面。应用 ID 为 cn.windchaser.unified。

## DroidPlugin

固定上游提交 c6ebf652e0f73aa0e5746766e117e51efaf41dbd，Copyright 2015 Andy Zhang 及源文件各作者。
保留上游所有逐文件声明。主要许可证为 GNU LGPL v3；适用时含“或更新版本”。COPYING.LESSER 与 COPYING 随 APK 和对应源码提供。部分辅助文件有独立声明，以各源文件为准。

在原 2026-10-23 实验适配基础上，本次增加单包宿主、私有模块导入及校验、宿主专用 authority、广播目标路由及接收者 classloader、主线程 Application/Provider 初始化、带 flags 的 Provider 元数据查询、同包已安装 Provider 隔离、宿主与私有模块间的真实证书集合比较，以及已知本地占位 Activity 的任务组件映射（不映射系统或外部窗口）。单包模式关闭原 CardWindowAdapter 的窗口裁切，继续使用项目已有高德原生悬浮桥接。

对应源码与构建说明随测试交付，接收者可修改和重新构建运行库及宿主；不需要签名私钥即可用自己的调试签名重建测试宿主。配套模块中自有适配代码的本地信任证书核查也需要与签名选择保持一致。

## 高德地图和 QQ音乐

APK 内含用户提供的配套高德 6.5.0.601323、QQ音乐车机版 2.1.1.1，作为私有运行模块。两者品牌、商业代码、资源与服务权利归原权利人，使用仍须遵守各自条款。它们不因置于同一 APK 中成为开源软件。

本次只刷新项目自有启动、返回和地图桥接适配，使其指向实验宿主。高德额外限定核查同 UID、匹配本地签名的实验包管理代理；保留其应用身份及原生签名检查。商业源码、登录凭据和签名私钥不随对应源码包提供。原有首次使用和隐私确认页面保留。

Conscrypt、BoringSSL 等其他组件声明仍见原应用许可证和 docs/licenses。

## 0.2 兼容适配（2026-10-26）

在 0.1 / API19 实验快照上增加 Android 4.2 起的分支适配，保持独立应用 ID，不修改正式发布目录。
修改范围：API17 Bundle Binder 兼容、缺失的 Context/ContentResolver 字段检查、多层 Context 解包、旧系统启动组件；API21+ 包解析与证书读取；API28+ 反射访问；API29+ ActivityTaskManager；Android 9 起 Activity 启动事务；API31+ 真实调用方 AttributionSource；新系统 IAppOpsService/AlarmManager 缓存与调用包名；现代 WindowSession；Provider/Application 初始化顺序；运行进程表同步；API34 私有 DEX 文件只读。权限仍由系统检查，未修改系统权限、UID 或原软件协议同意状态。

宿主 minSdk=17、targetSdk=24，ABI 固定为 armeabi-v7a。移除 API19 专用拦截表示允许进入兼容分支，不表示每个 Android 版本都已经验证成功。完整实测边界以交付的测试说明为准。

## AndroidHiddenApiBypass 6.1

来源：LSPosed / AndroidHiddenApiBypass，Apache License 2.0。官方依赖坐标 org.lsposed.hiddenapibypass:hiddenapibypass:6.1，文件保存在 app/libs/hiddenapibypass-6.1.aar。保留官方 LICENSE 原文于 docs/licenses/AndroidHiddenApiBypass-LICENSE.txt，并打包入 APK。
上游源码参考提交：4efc64fcada7df15e7b5ba068e774343b760270d。此依赖只用于实验宿主访问平台兼容接口，不授予 Root、系统签名或其他权限。
