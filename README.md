# 启动联网延迟 Root · NetDelayBlock

<p align="center"><img src="artwork/network-block-icon.png" width="160" alt="禁止联网图标"></p>

**给应用启动留出一段离线时间。** NetDelayBlock 是一个 LSPosed 模块，通过 Root 防火墙暂时限制作用域应用的出站网络。默认延迟 **5 秒**，可自定义为 **0～300 秒**。

## 功能

- IPv4 / IPv6 双栈 UID 防火墙，规则安装后覆盖匹配 UID 的 Java 与原生出站请求。
- 默认只在进程启动时触发；“从后台重新打开时也重新计时”默认关闭，可自行启用。
- 到时自动删除规则，提供手动解除、Root 检测、规则计数和清理日志。
- 支持 AIDE Pro 打包，附带完整 Hook 源码与重建工具。
- 禁止联网主题图标，支持常见屏幕密度与 Android 自适应图标。
- 模块自身没有互联网权限。

## 使用条件

Android 5.0+；已配置 Root；支持传统 Xposed 模块的 LSPosed；内核与工具支持 iptables、ip6tables 和 owner UID 匹配。

## 手机编译

1. 下载源码压缩包，完整解压到新目录。
2. 用 AIDE Pro 打开 `app/build.gradle` 所属工程。
3. 安装所需 SDK 28、Build Tools 28.0.3 和 Gradle 插件 3.2.1，然后构建。
4. `app/libs/netdelay-hooks.jar` 是本模块 Hook 入口，不能替换成 Xposed API JAR。

AIDE 直接编译界面、配置与防火墙 Java 源码。Hook 入口使用附带的预编译 JAR；其完整源码在 `hook-source/src`。修改 Hook 后，需在桌面使用 JDK/Python 重建：

```bash
python3 tools/rebuild_hooks.py /path/to/android.jar
```

工程没有 Gradle Wrapper。本次公开发布仅提供源码，不提供 APK。

## 启用

1. 在 LSPosed 启用模块，选择需要限制的应用。
2. 打开模块，点击“授权 Root 并检测 / 启用”，允许 Root。
3. 检测通过后设置秒数；默认 5 秒、后台返回重新计时关闭。
4. 强行停止目标应用后重新打开。首次测试可设为 30 秒，方便观察规则计数。

覆盖安装会保留此前保存的设置。卸载或清除数据前请先解除模块规则。

## 实现与边界

LSPosed 在 `Application.attach` 检测启动，模块 Provider 校验调用方 UID，再由模块进程执行 Root 防火墙命令。在 OUTPUT 链首部安装专属双栈 DROP 规则，并启动独立超时清理器。双栈安装失败会回滚，手动清理只处理模块登记的规则。

- 规则安装前的极早初始化流量可能漏过。
- WebView 隔离 UID、系统 DNS 或其他 UID 代发的流量可能绕过。
- 共享 UID 的应用、同 UID 的后台服务一起受控。
- 已收到的数据或缓存可能继续显示；规则解除后由应用自行重试。
- Root 撤权或清理进程被终止可能影响恢复，可重新授权后解除或重启。

因此，本模块不承诺从应用第一条指令起完全阻断所有网络。请避免对关键系统应用使用。

## 源码结构

| 路径 | 用途 |
| --- | --- |
| `app/src/main/java` | 设置界面、配置通道、Root 执行 |
| `app/src/main/assets/firewall-v3.sh` | 双栈规则、超时删除和回滚 |
| `hook-source/src` | 完整 LSPosed 启动 Hook 源码 |
| `app/libs/netdelay-hooks.jar` | AIDE 构建使用的 Hook 桥接 |
| `tools` | Hook 重建和 APK 类定义检查 |
| `tests` | 防火墙行为模拟测试 |
| `artwork` | 图标原图 |

## 验证

Java 编译检查、Hook JAR 排除框架 API 检查和防火墙行为模拟已完成；此前版本已有用户反馈功能正常。不同设备的 AIDE、Root 与内核环境仍需自行验证。

更多细节见 [中文说明](README_中文.md)，本次发布内容见 [发布说明](RELEASE_NOTES.md)。

## 第三方依赖

`hook-source/api/xposed-api-82.jar` 来自 [官方 Xposed API](https://api.xposed.info/)，仅用于重建 Hook，不进入 APK。相关依赖权利与许可归其原作者所有。
