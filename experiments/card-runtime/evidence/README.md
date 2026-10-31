# 证据对应关系

最终探针构建：`96d022c48d15e86f64367da74da7886ea932a79dce41a1fc940ad910c2325ab1`。

- `probe-results.json`、`probe-commands.txt`、`probe-logcat.txt`、`probe-window.txt`、`probe-activity.txt`、`probe-package.txt` 与 `visual-review.json` 对应最终运行 `probe-daadc66fc6db488c9c0eb940f59fb6bb`。
- 最终运行截图为 `probe-host.png`、`probe-dual-before.png`、`probe-dual-interactive.png`、`probe-second-activity.png`、`probe-dialog.png`、`probe-input-left.png`、`probe-input-right.png`、`probe-final.png`。
- `original-apk-abi-guard*` 来自退出清理补丁之前的构建，精确 APK 哈希当时未记录；只证明该轮导入器识别并拒绝 ARMv7 原 APK 在 x86 环境启动。最终构建补跑时模拟器离线，没有新增通过记录。此项不证明两款原应用的兼容性。
- 其他 PNG 是早期尝试或早期视觉合成图，不作为最终通过依据。

没有原版高德 / QQ 音乐在 ARM API 19 下运行、整屏比例缩放、软键盘、多指手势和未 Root 真车的验收记录。
