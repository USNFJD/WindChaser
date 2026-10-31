# 关联返回与首次默认视角检查

本目录是项目自有JVM测试替身，不打包进APK。脚本直接编译仓库中的四个生产Java类，并运行33个启动返回场景和6个默认地图视角场景，共165项断言。

运行方式见仓库README中的`tools/check_startup_return.py`示例。依赖JDK、Android SDK 35的android.jar和org.json的JVM实现（例如json-20240303.jar）。编译结果、输出日志与JSON结果默认放在被Git忽略的`.tooling/startup-return-check/`。

替身提供可控时钟、事件队列、任务列表、PendingIntent与Messenger等边界行为；它们不模拟商业引擎、真实Android调度、ROM后台限制或车机倒车硬件。测试通过仍需实车验收。
