package cafe;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;

final class Bailian {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private String key() {
        String key = Application.env("DASHSCOPE_API_KEY", "");
        if (key.isBlank()) throw new IllegalStateException("请在服务端配置 DASHSCOPE_API_KEY，或切换 Mock 模式。");
        return key;
    }
    Direction reply(String input, List<Map<String,String>> history, Turn turn) throws Exception {
        var messages = new ArrayList<Map<String,String>>();
        messages.add(Map.of("role","system","content", """
            你是原创成年角色Mira，26岁旅行摄影师，穿琥珀色雨衣、戴银色星星发夹。雨夜在即将打烊的咖啡馆与用户聊天。
            你在等妹妹的来信，旅行照片是一张海边灯塔。保持自然、温柔、有一点悬念，不声称是真人。
            根据用户的话推进剧情，别重复自我介绍。每次回复2到3句，最多110个汉字。
            只输出JSON对象：{"text":"台词","emotion":"calm|happy|worried","action":"none|window|photo","scene":"keep|rain|clear"}。
            用户询问等谁时可以透露妹妹并看向窗外；询问照片时展示照片；获得安慰或谈到雨停时让雨停。
            emotion只能为calm/happy/worried，action只能为none/window/photo，scene只能为keep/rain/clear。
            历史里的[回复被打断]意味着用户没听完，不假设用户知道未说出的内容。
            """));
        messages.addAll(history);
        messages.add(Map.of("role","user","content",input));
        var request = HttpRequest.newBuilder(URI.create(Application.env("DASHSCOPE_CHAT_URL","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")))
            .timeout(Duration.ofSeconds(25)).header("Authorization","Bearer " + key()).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("model",Application.env("CHAT_MODEL","qwen-plus"),
                "messages",messages,"temperature",0.8,"max_tokens",500,"enable_thinking",false,"response_format",Map.of("type","json_object"))))).build();
        var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        turn.onCancel(() -> pending.cancel(true));
        var response = pending.get(30,TimeUnit.SECONDS); turn.check();
        if (response.statusCode() != 200) throw new IllegalStateException("对话服务返回 " + response.statusCode() + "，请检查地域、模型权限和配额。");
        return Direction.parse(json,json.readTree(response.body()).path("choices").path(0).path("message").path("content").asText());
    }
    String recognize(byte[] pcm, Turn turn) throws Exception {
        if (pcm.length < 3200) throw new IllegalArgumentException("录音太短，请再说一句完整的话。");
        try (AudioTask task = new AudioTask(turn)) {
            task.start("asr", "recognition", Application.env("ASR_MODEL","paraformer-realtime-v2"),
                Map.of("format","pcm","sample_rate",16000,"language_hints",List.of("zh")));
            for (int offset=0; offset<pcm.length; offset+=3200) {
                turn.check();
                task.ws.sendBinary(ByteBuffer.wrap(Arrays.copyOfRange(pcm,offset,Math.min(offset+3200,pcm.length))),true).get(5,TimeUnit.SECONDS);
            }
            task.command("finish-task",Map.of("input",Map.of()));
            task.done.get(25,TimeUnit.SECONDS); turn.check();
            String result = String.join("",task.sentences.values()).strip();
            if (result.isBlank()) throw new IllegalArgumentException("没有听清，请靠近麦克风再试一次。");
            return result;
        }
    }
    byte[] synthesize(String text, Turn turn) throws Exception {
        try (AudioTask task = new AudioTask(turn)) {
            task.start("tts","SpeechSynthesizer",Application.env("TTS_MODEL","cosyvoice-v2"),
                Map.of("text_type","PlainText","voice",Application.env("TTS_VOICE","longxiaochun_v2"),"format","mp3","sample_rate",24000,"rate",1.0));
            task.command("continue-task",Map.of("input",Map.of("text",text)));
            task.command("finish-task",Map.of("input",Map.of()));
            task.done.get(25,TimeUnit.SECONDS); turn.check();
            byte[] result = task.audio.toByteArray();
            if (result.length == 0) throw new IllegalStateException("语音服务未返回音频。");
            return result;
        }
    }
    private final class AudioTask implements WebSocket.Listener, AutoCloseable {
        final String id = UUID.randomUUID().toString();
        final CompletableFuture<Void> ready = new CompletableFuture<>(), done = new CompletableFuture<>();
        final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        final TreeMap<Integer,String> sentences = new TreeMap<>();
        final StringBuilder fragments = new StringBuilder();
        final Turn turn;
        WebSocket ws;
        AudioTask(Turn turn) throws Exception {
            this.turn = turn;
            var connect = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).header("Authorization","Bearer " + key())
                .buildAsync(URI.create(Application.env("DASHSCOPE_AUDIO_URL","wss://dashscope.aliyuncs.com/api-ws/v1/inference")),this);
            turn.onCancel(() -> connect.cancel(true));
            ws = connect.get(12,TimeUnit.SECONDS);
            turn.onCancel(this::close); turn.check();
        }
        void start(String task, String function, String model, Map<String,Object> parameters) throws Exception {
            command("run-task",Map.of("task_group","audio","task",task,"function",function,"model",model,"parameters",parameters,"input",Map.of()));
            ready.get(12,TimeUnit.SECONDS); turn.check();
        }
        void command(String action, Map<String,Object> payload) throws Exception {
            turn.check();
            ws.sendText(json.writeValueAsString(Map.of("header",Map.of("action",action,"task_id",id,"streaming","duplex"),"payload",payload)),true).get(5,TimeUnit.SECONDS);
        }
        public void onOpen(WebSocket socket) { socket.request(1); }
        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            fragments.append(text);
            if (last) {
                try {
                    JsonNode n = json.readTree(fragments.toString()); fragments.setLength(0);
                    switch(n.path("header").path("event").asText()) {
                        case "task-started" -> ready.complete(null);
                        case "task-finished" -> done.complete(null);
                        case "task-failed" -> fail(new IllegalStateException("语音 API 请求失败，请检查模型、音色和权限。"));
                        case "result-generated" -> {
                            JsonNode sentence = n.path("payload").path("output").path("sentence");
                            if (sentence.has("text")) sentences.put(sentence.path("sentence_id").asInt(),sentence.path("text").asText());
                        }
                    }
                } catch(Exception e) { fail(e); }
            }
            socket.request(1); return null;
        }
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer bytes, boolean last) {
            if (audio.size() + bytes.remaining() > 8_000_000) { fail(new IllegalStateException("音频超过大小限制")); close(); }
            else { byte[] chunk = new byte[bytes.remaining()]; bytes.get(chunk); audio.writeBytes(chunk); }
            socket.request(1); return null;
        }
        void fail(Throwable e) { ready.completeExceptionally(e); done.completeExceptionally(e); }
        public void onError(WebSocket socket, Throwable e) { fail(e); }
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { if (!done.isDone()) fail(new IllegalStateException("语音服务连接中断")); return null; }
        public void close() { if (ws != null) ws.abort(); fail(new CancellationException()); }
    }
}
