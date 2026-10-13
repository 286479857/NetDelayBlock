# 启动联网延迟 Root — v7 默认设置版

## 图标与默认设置

新增“Wi-Fi + 红色禁止符号”启动图标，寓意禁止联网。提供全部常见屏幕密度 PNG，
并加入 Android 8.0+ 自适应启动图标资源。Manifest 已引用图标。
保留图标与 Root 防火墙实现，界面/Provider/Hook 三处的前台模式默认值均设为 false。
原始图标和生成描述附在 artwork 目录。

## 保留 v5 的 AIDE 桥接结构

这 22 个错误都源于 AIDE 未把上一版 provided 依赖加入 Java 编译路径。
把 Xposed API 直接放进 app/libs 又会让它进入 APK，导致 LSPosed 拒绝加载。
v5 使用一个仅含本模块启动 Hook 的预编译 JAR：app/libs/netdelay-hooks.jar。
它含 4 个 com.example.netdelay.DelayHook 类，不含 Xposed API 的实现/桩类。
app/src/main/java 不再引用 de.robv.android.xposed，AIDE 无需编译期 API 依赖。

界面、配置和 Root 防火墙 Java 源码仍由 AIDE 编译，秒数和前台模式可在界面设置。
Hook 完整源码位于 hook-source/src，其 API 位于 hook-source/api。
该目录不属于 app 源目录，AIDE 构建时只打包已编译的 Hook JAR。
这是预编译桥接方式，并非所有 Java 源码都在 AIDE 里重新编译：
如果修改启动 Hook 源码，需要用桌面 JDK/Python 重建 JAR，再回到 AIDE 打包。
所有源码均附带，没有隐藏 Hook 源码。

## 手机 AIDE Pro 编译

1. 完整解压到全新目录，打开本版 app/build.gradle 对应的工程。
2. 不要复制旧版本的 DelayHook.java、Xposed API、libs、build 或 bin 缓存过来。
3. 确认 app/libs 只有 netdelay-hooks.jar，不应出现 xposed-api-82.jar。
4. 点击构建。依赖采用 AIDE 常见的 compile files 本地 JAR 方式。
5. 本版仍使用 SDK 28、Build Tools 28.0.3、Gradle 插件 3.2.1、Java 7。
   对应构建组件需已在 AIDE 安装。工程无 Gradle Wrapper。

本次交付仅含源码与 AIDE 所需的 Hook JAR，不包含 APK。
默认延迟 5 秒，默认不勾选从后台重新打开时重新计时。
覆盖安装会保留此前保存的设置；需要调整现有安装时，请手动设为 5 秒并取消勾选后保存。

## 使用

1. 在 LSPosed 启用模块，勾选目标应用，不勾选系统框架/关键系统服务。
2. 重启手机，打开模块，点“授权 Root 并检测 / 启用”，永久允许 Root。
3. 看到 OK root + IPv4/IPv6 owner match available 后保存秒数。
4. 先设置 30 秒，强行停止目标应用，再打开测试。
5. LSPosed 日志中的 NetDelayRoot: startup detector installed 表示 Hook 已加载；
   OK blocked uid=... IPv4+IPv6 表示双栈防火墙规则安装。
6. 窗口内点“查看规则计数与清理日志”，DROP 的 packets/bytes 增加可证明丢包。

默认 5 秒，可设 0～300 秒。0 会关闭并尝试清理已有规则。
默认不从后台返回重新计时。若手动勾选该选项，所有 Activity 停止超过 1 秒后重新打开才再次计时。
“停止启用，并解除全部拦截”只清理本模块规则，不清空系统 OUTPUT 链。

## 防火墙机制和限制

LSPosed 在 Application.attach 触发模块 Provider；Provider 按 Binder 调用方 UID
安装 iptables/ip6tables OUTPUT 首部 DROP 规则。目标应用无需 Root 授权。
调用方不能传入其他 UID 或任意 shell 命令。模块没有互联网权限。

规则安装后，该 UID 的 Java/原生 TCP、UDP、QUIC 出站包均受控，包括已有连接。
任何地址族安装失败就回滚。正常计时从双栈安装成功开始。
有普通超时删除与安装前独立超时清理器，异常时可能延长至设置秒数 + 15 秒。
Root 撤权、后台 Root 任务被杀或 netfilter 异常可能影响清理；可重新授权后解除，
或重启清除临时规则。先解除，再卸载或清除模块数据。

规则安装前的极早流量、WebView 隔离 UID、系统 DNS/其他 UID 代理可能绕过。
同 UID 后台进程和共享 UID 的其他应用一起受控；已缓存/收到的数据仍可能显示。
这是出站 UID 控制，不承诺任何应用启动第一条指令起都完全无流量。
前后台检测可能受多窗口/特殊生命周期影响。到时解除不保证应用自动重试。
首次授权须提前在模块界面完成；启动 Root 操作等待上限 8 秒。

## 源码与重建工具

app/src/main/java：MainActivity、RootFirewall、SettingsProvider。
app/src/main/assets/firewall-v3.sh：规则、定时清理、回滚、状态。
hook-source/src/com/example/netdelay/DelayHook.java：完整 Hook 源码。
app/libs/netdelay-hooks.jar：只含本模块 Hook 的 4 个类。

桌面安装 JDK（建议 8～17）和 Python 3，准备 Android API 28 的 android.jar 后运行：
python3 tools/rebuild_hooks.py /path/to/android.jar
然后重新在 AIDE 构建。不要把 hook-source/api 的 API JAR 放入 app/libs。

APK 检查：python3 tools/check_apk.py /path/to/module.apk
检查真实 DEX 类定义，不把合法的 Xposed API 引用误判为 API 类被打包。

## 验证

app Java 文件在没有 Xposed API 的编译路径下编译通过。
Hook JAR 单独编译并检查，只含本模块类。本版仅交付源码，未生成 APK；编译用 Hook JAR 不含 Xposed API 类定义。
Root 防火墙逻辑沿用已确认可用的版本，本次仅调整默认设置。
没有真实 AIDE Pro 或 LSPosed 手机可供实测，因此不承诺已实机验证所有行为。

API 来源：https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar
