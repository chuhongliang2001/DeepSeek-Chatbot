# DeepSeek Chatbot

一款非官方、开源的 DeepSeek API 安卓客户端。使用自己的 API Key，即可在 Android 8.0 及以上设备体验多轮对话、深度思考、联网搜索、文件与图片输入、语音输入和朗读等功能。

本项目基于 DeepSeek 官方开源的 [chat-demo](https://github.com/deepseek-ai/chat-demo) 修改，保留其 MIT 许可证与版权声明，并增加原生安卓适配、可拖动 Q 版人物及移动端手势。本项目不是 DeepSeek 官方应用。

## 项目背景

DeepSeek 官方 App 在算力资源紧张时可能会限制对话额度。本项目通过 DeepSeek 开放 API 提供另一种使用方式：不需要部署额外服务器，只需输入自己的 API Key，就可以在安卓设备上构建并使用个人 Chatbot。

## 功能

- 流式多轮对话、深度思考、联网搜索、链接阅读与来源引用
- 图片及文档上传、语音输入、回答朗读
- 多会话、自动标题、编辑、重新生成、继续生成、历史导入导出
- Markdown、代码高亮、数学公式和 Mermaid 图表
- 浅色、深色及跟随系统主题
- 可自由开关的模型内容安全校验
- 右滑打开历史侧栏、左滑关闭、两次返回退出
- 可拖动和点击互动的 Q 版人物，回答时切换为思考状态，并适配软键盘
- API Key 首次验证与更换、本地加密保存、禁止系统备份

## 使用

1. 安装并打开应用。
2. 在 [DeepSeek 开放平台](https://platform.deepseek.com/api_keys) 创建自己的 API Key。
3. 在首次启动页输入 API Key；应用通过 `GET /models` 验证后进入主界面。
4. 之后可在「侧边栏 → 设置 → 数据管理」中更换 API Key。

模型安全选项位于「设置 → 通用设置 → 模型内容安全」：

- 开启时发送 `safety_check: {"level":"high"}`。
- 关闭时不发送 `safety_check` 参数。
- 默认开启。如对外提供服务，请阅读 DeepSeek 的合规说明并履行相应义务。

API 调用由你的设备直接连接 `https://api.deepseek.com`，费用计入你的 DeepSeek 账户。联网工具、文件和语音等功能的可用性与计费以官方文档为准。

## 构建 Android 应用

### Android Studio

1. 安装 Android Studio、JDK 17 和 Android SDK 35。
2. 用 Android Studio 打开项目根目录并完成 Gradle 同步。
3. 连接设备或启动模拟器，点击 **Run**。

也可以直接构建调试 APK：

```bash
./gradlew assembleDebug
```

Windows 命令提示符或 PowerShell 使用：

```powershell
.\gradlew.bat assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

仓库已包含网页功能层的编译产物，因此正常构建 Android 应用不需要安装 Node.js。`local.properties`、SDK 路径和签名文件不会提交到仓库。

## 修改网页功能层

网页功能层位于 `web/`，使用 React、TypeScript 和 Rspack。需要 Node.js 22.12 或更高版本。

```bash
npm --prefix web install
npm --prefix web run dev
```

开发服务器默认位于 `http://localhost:8090`。

```bash
npm --prefix web run type
npm --prefix web run lint
npm --prefix web run build
```

修改后，将 `web/dist/` **里面的全部内容**复制到 `app/src/main/assets/chat/`，再重新构建 Android 应用。

```bash
rm -rf app/src/main/assets/chat
mkdir -p app/src/main/assets/chat
cp -R web/dist/. app/src/main/assets/chat/
```

## 验证

```bash
npm --prefix web run type
npm --prefix web run lint
./gradlew assembleDebug lint
```

## 数据与隐私

- API Key 使用 Android Keystore 和 AES-GCM 加密保存，内置网页端不将密钥写入普通 localStorage。
- 会话、附件和设置保存在本机 WebView 存储中。
- 对话、上传文件及语音内容会直接发送给 DeepSeek；本项目没有额外第三方后端。
- 应用禁止系统云备份和设备迁移，避免本地密钥及会话随备份离开设备。
- 清除应用数据或卸载应用可能删除本地历史；重要记录请先导出。

## 项目结构

```text
app/                    原生 Android 外壳、系统栏、密钥、权限与文件适配
web/                    基于官方 chat-demo 修改的聊天功能层
web/src/assets/         Q 版人物素材
app/src/main/assets/    打入 APK 的网页编译产物
```

## 开源许可与致谢

本项目采用 [MIT License](LICENSE)。

特别感谢 DeepSeek 开源 [chat-demo](https://github.com/deepseek-ai/chat-demo)。该项目同样采用 MIT 协议，原版权声明为 `Copyright (c) 2026 DeepSeek`。原始许可证保留在 [web/LICENSE](web/LICENSE)，打包后的网页资源中也包含该许可证。更多信息见 [NOTICE](NOTICE)。

网页端使用的 React、Rspack、KaTeX、Mermaid 等依赖遵循各自的开源许可证。DeepSeek 名称及相关标识的权利归其权利人所有。

本项目为Vibe Coding作品，由Kimi K3.1 Pro在Kimi Code下完成，感谢AI的帮助，让想法变为现实。
