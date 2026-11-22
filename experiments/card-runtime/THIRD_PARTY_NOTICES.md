# Third-party notices / modifications

DroidPlugin — https://github.com/DroidPluginTeam/DroidPlugin

- Fixed upstream commit: `c6ebf652e0f73aa0e5746766e117e51efaf41dbd`.
- Copyright 2015 Andy Zhang and respective source-file authors.
- License: GNU LGPL version 3 or, where stated in source, any later version. `COPYING.LESSER` is copied verbatim from the upstream LICENSE. LGPL incorporates GPL version 3, provided as `COPYING`.
- The upstream source tree and all per-file notices are included. Some helper/reflection files have their own copyright/license notices, retained in full.
- Hydrogen changes, 2026-10-23: Gradle 9 / AGP 9 compatibility; minimal lab permission list replacing sample's broad requests; translucent stub themes for all resource variants; noHistory removal and task affinity per process; correction of two erroneous `startsWith("")` component-name checks; hooks in PluginInstrumentation and IWindowSessionInvokeHandle; added CardWindowAdapter, lab host, test APKs, test runner and build/package documentation.
- `prepare_runtime.py` records repeatable transformations of upstream files. `upstream-changes.patch` records differences against the pinned upstream archive. Added lab source files are also supplied in full.
- Application source/rebuild inputs are supplied so recipients can inspect, modify and rebuild the combined experimental APK with a modified library. The lab is independent of the existing HydrogenLauncher production APK.

Gradle wrapper files retain their original copyright and Apache-2.0 notices. Android platform components are provided separately by the Android SDK; no Android system image or emulator binary is included in this distribution.

User-provided original Amap and QQ Music APKs retain their respective owners' terms, signatures and hashes. They are neither modified nor redistributed as part of this lab APK/source archive.
