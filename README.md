# XiaoAiOperitBridge

**将超级小爱的请求转发给本地 Operit AI 执行，实时注入回小爱对话界面。**

一个 LSPosed / Xposed 模块，Hook `com.miui.voiceassist`。它不替换模型，而是把小爱的请求拦截下来，通过 HTTP 转发给本机运行的 Operit AI（`com.ai.assistance.operit`），再把 Operit 的回答实时注入回小爱的语音播报、对话卡片和历史记录里。

---

## 一、工作原理

原生小爱的请求链路：用户说话 → ASR 识别 → 小米云端模型作答 → 语音+卡片+历史记录。

模块在这条链路上插入一个转发层：

```
用户对小爱说话
       │
       ▼
超级小爱 ASR 识别 → 得到文本
       │
       ▼
[Hook] OperationManager.setQueryInfo() 拦截
  ├─ 判定是否由模块接管（全局拦截 / 兜底检测 / 正则匹配）
  └─ 命中 → OperitBridge.chatStream() 发送 SSE 请求
       │
       ▼
Operit AI (localhost:8080) 处理并流式返回
       │
       ▼
模块将结果注入小爱的四路输出:
  ├─ TTS 语音播报（ToastStreamPlayer）
  ├─ 对话结果卡片（FlowTemplateToastCard）
  ├─ SpeakContentManager 文本
  └─ SQLite 历史记录
```

---

## 二、接管策略

模块不是所有话都拦，而是按策略决定是否接管：

| 策略 | 触发条件 | 说明 |
|------|----------|------|
| **全局拦截** | `fullIntercept = true` | 所有请求全部转发给 Operit，小爱原生能力完全旁路 |
| **兜底接管** | 小爱回复「小爱暂不支持该功能」 | 等小爱先尝试，检测到兜底话术后接管，改由 Operit 回答 |
| **强制接管** | 问话匹配 `interceptPattern` 正则 | 自定义正则，命中即接管 |
| **Operit 前缀** | 「使用 operit 执行 xxx」/「让 operit 做 xxx」 | 显式指定走 Operit |
| **白名单跳过** | 问话匹配 `skipTakeoverPattern` 正则 | 命中则完全不接管，走小爱原生流程 |

放行词（`jumpAllowWords`）控制跳转类指令：含「打开」「启动」「去」等词的指令默认放行给小爱处理，避免误拦「打开微信」「导航去公司」这类正常操作。

---

## 三、四路输出覆盖

模块接管时四条输出路全部覆盖，确保语音、卡片、文本、历史记录一致：

| 输出路 | 机制 | 说明 |
|--------|------|------|
| **TTS 语音播报** | 静音小爱音轨 → `la0.n1.speakTts()` 播报 Operit 答案 | 复用 ToastStreamPlayer，音色与原生一致 |
| **对话结果卡片** | 创建/更新 `FlowTemplateToastCard`，注入到活跃卡片容器 | 支持 App 内对话和悬浮窗两种渲染 sink |
| **SpeakContentManager** | 通过 `sendStreamData` 注入 ToastStream JSON | 卡片上喇叭按钮重播读的是它，必须同步更新 |
| **SQLite 历史记录** | 通过 `ChatDbManager.recordToSpeak()` 写入 | 回 App 看历史也能看到 Operit 的回答 |

---

## 四、配套 App

模块自带 iOS 风格界面（Compose），三个标签页：

- **首页** — 模块激活状态、Operit 连接信息、连接测试按钮
- **记录** — 每一次转发请求的完整明细（发送/完成/错误），保留最近 200 条
- **设置** — Operit 连接配置、接管策略、播报开关、放行词、自定义正则

配置通过 `ContentProvider` 跨进程下发到小爱进程里的 Hook；运行记录反向经同一座桥回写进模块私有存储。

---

## 五、配置项一览

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `host` | `127.0.0.1` | Operit 运行地址 |
| `port` | `8080` | Operit WebUI 端口 |
| `apiPath` | `/api/external-chat` | Operit API 路径 |
| `token` | `""` | Bearer Token，在 Operit 设置中配置 |
| `model` | `""` | 模型名称（留空使用 Operit 默认） |
| `enabled` | `false` | 是否启用 Operit 转发 |
| `fullIntercept` | `false` | 全局拦截（所有请求走 Operit） |
| `speakAnswer` | `true` | 是否播报 Operit 答案 |
| `blockViewJump` | `true` | 拦截查看类跳转（如「查看电量」跳设置页） |
| `blockWebSearch` | `true` | 拦截搜索兜底（跳全局搜索） |
| `systemPrompt` | 见下方 | 自定义系统提示词 |
| `jumpAllowWords` | `打开,开启,进入,去,跳转,启动` | 放行词（含这些词的指令不拦截） |
| `webSearchAllowWords` | `搜索,搜一下,搜下,搜搜,百度,上网搜,网上搜` | 搜索放行词 |
| `interceptPattern` | `""` | 自定义接管正则（命中即接管） |
| `skipTakeoverPattern` | `""` | 白名单正则（命中则完全跳过） |

默认系统提示词：

> 你是运行在这台 Android 设备上的本地智能助手，通过 LSPosed 模块接管了系统原有的「小爱同学」语音入口。回答要口语化、简洁、可直接听懂，不要使用 markdown 格式。

---

## 六、环境要求

- Android 13+（API 33+）
- HyperOS，超级小爱 `com.miui.voiceassist`
- LSPosed（Xposed API 93+）
- Root 权限
- Operit AI 已安装并运行，启用了 HTTP/WebUI，配置了 bearer token

作用域勾选 `com.miui.voiceassist` + `com.example.xiaoaioperit`（自身进程用于检测模块是否激活）。

---

## 七、构建

```bash
./gradlew :app:assembleDebug
```

AGP 9.3 / Kotlin 2.4 / compileSdk 37 / OkHttp 4.12。

---

## 八、说明

模块依赖大量小爱内部的混淆类名（`la0.n1`、`jb0.vd`、`z10.a`、`kh0.s0` 等），这些名字随小爱版本变化。所有 Hook 点在 `HookEntry.kt` 里集中声明，并注明了用途与实测依据，升级失配时从那里对照修正。

本模块不替换小爱的模型、不绕过任何鉴权或计费机制，只是将请求转发给用户自己部署的本地 Operit AI。Operit 的回答能力取决于 Operit 本身配置的模型和服务。

任何因使用该模块导致的设备问题作者概不负责。

## 九、开源协议

本项目采用 [GNU General Public License v3.0](LICENSE)。

第三方依赖均为 Apache-2.0（Xposed API、Jetpack Compose、OkHttp、kotlinx-coroutines、Haze），与本协议兼容。
