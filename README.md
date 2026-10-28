# 雨停之前 · Rain Cafe

移动端优先的角色互动网页：雨夜咖啡馆、26 岁旅行摄影师 Mira、琥珀色雨衣与银色星星发夹。通过文字或连续语音交谈，表情、动作、窗外天气和灯塔照片随对话变化。

![桌面预览](verification/rollback-desktop.png)

## 启动

默认 Mock，无需密钥。需要 Java 21+、Maven 3.9+、Node.js 22+。

```powershell
# Windows，构建、测试并启动
./start.ps1
```

```sh
# macOS / Linux
sh start.sh
# 或 Docker
 docker compose up --build
```

打开 http://localhost:8080 。源码启动会把前端资源打入 Java 服务；Docker 配方已提供，本机未验证 Docker 运行。使用打包交付版时也可执行 `./run.ps1`。

## 百炼 API 配置

仓库不包含密钥、本地 .env 或凭据备份。复制 `.env.example` 为 `.env`，自行设置 `APP_MODE=live` 和 `DASHSCOPE_API_KEY`。也可以通过服务端环境变量配置。密钥只由后端读取。

| 功能 | 默认配置 |
| --- | --- |
| 对话模型 | `qwen-plus` |
| 对话接口 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| 识别模型 | `paraformer-realtime-v2` |
| 合成模型 / 音色 | `cosyvoice-v2` / `longxiaochun_v2` |
| 语音 WebSocket | `wss://dashscope.aliyuncs.com/api-ws/v1/inference` |

这些默认值对应百炼北京地域通用 API。业务空间专属域名可通过 `DASHSCOPE_CHAT_URL` 和 `DASHSCOPE_AUDIO_URL` 覆盖，模型和音色也可配置。

参考：[官方接入域名](https://help.aliyun.com/zh/model-studio/base-url)、[Paraformer](https://help.aliyun.com/en/model-studio/paraformer-real-time-speech-recognition-java-sdk)、[CosyVoice](https://help.aliyun.com/zh/model-studio/cosyvoice-websocket-api/)、[音色列表](https://help.aliyun.com/zh/model-studio/cosyvoice-voice-list)。

## 连续语音与打断

点击“连续对话”一次开启麦克风，本地 Silero VAD 检测说话和停顿。有效语音约 200ms 后打断旧回应，停顿约 850ms 后自动发送。回复期间继续收音，再开口可打断；点击“结束对话”、切到后台或断线会释放麦克风。单次连续发言超过 28 秒会提示重新开启。

浏览器与 Java 使用同源 WebSocket。Java 通过 HTTP 调用对话模型，通过百炼 WebSocket 调用 ASR/TTS。每轮递增 turnId，音频、字幕和动作绑定轮次；前后端丢弃迟到结果。历史只确认完整播放的片段，被打断回复带标记。

## 技术结构

- React + TypeScript + Vite：场景、字幕、音频播放与手机布局。
- Java 21 + Spring Boot：内存会话、取消传播、角色指令白名单。
- `frontend/src/useContinuousVoice.ts`：持续收音、VAD 与麦克风生命周期。
- `backend/src/main/java/cafe/Bailian.java`：百炼通用 API 接入。
- `SceneSocket.java` / `Turn.java`：轮次协议与会话历史。

角色有待机、倾听、思考、说话状态，以及平静、开心、担忧表情。支持看窗外、展示照片与雨停事件。模型只输出允许的情绪、动作、场景枚举，不能执行任意脚本或媒体地址。

## 验证

```sh
cd frontend
npm ci
npm test
npm run build
cd ../backend
mvn verify
```

在 Mock 服务运行时执行 `node scripts/browser-tests.cjs` 和 `node scripts/continuous-voice-tests.cjs`。可通过 `APP_URL` 指定地址，`CHROME_PATH` 指定 Chrome；也可先在 frontend 执行 `npx playwright install chromium`。

本地测试覆盖轮次隔离、取消、异常恢复、连续语音自动发送、打断与麦克风释放。语音自动化使用中文 WAV 注入真实浏览器 VAD，并非真人麦克风。清除凭据后，当前百炼通用接口未做持有真实密钥的云端联调。`scripts/live-smoke.cjs` 可在自行配置后通过 `RUN_LIVE_TESTS=1` 执行。

## 素材与限制

场景、成年角色和灯塔照片为本项目 SVG/CSS。Mock WAV 由 Windows Microsoft Huihui Desktop 合成；图标 Lucide（ISC）。VAD 使用 vad-web（ISC）、Silero（MIT）及 ONNX Runtime Web（MIT），资源在构建时复制到本地，首次开启麦克风需加载约 17MB。

Mock 的识别结果固定模拟为“给我看看旅行照片”，页面有明确标记。字幕按音频进度估算，不是精确逐字时间戳或口型同步。浏览器请求回声消除，外放和噪声效果仍需实际设备确认，建议耳机体验。远程手机录音需要 HTTPS。

无账号、数据库或会话持久化；断线重连开始新会话。真实手机、Safari、Docker 尚未人工验收。旧录屏仅代表历史版本；当前外观以本页截图为准。
