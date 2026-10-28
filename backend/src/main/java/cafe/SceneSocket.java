package cafe;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

@Component
public class SceneSocket extends TextWebSocketHandler {
    private final ObjectMapper json = new ObjectMapper();
    private final Bailian api = new Bailian();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String,Client> clients = new ConcurrentHashMap<>();
    private final Semaphore capacity = new Semaphore(24);
    static final class Client {
        final WebSocketSession socket;
        Turn current;
        final List<Map<String,String>> history = new ArrayList<>();
        int count;
        Client(WebSocketSession socket) { this.socket=socket; }
        void commit(Turn t, boolean interrupted) {
            if (t == null || t.committed || t.delivered.isEmpty()) return;
            String heard = String.join("",t.delivered.subList(0,Math.min(t.acknowledged+1,t.delivered.size())));
            if (interrupted) heard += "[回复被打断，后续内容未确认听完]";
            if (!heard.isBlank()) history.add(Map.of("role","assistant","content",heard));
            t.committed=true;
            while(history.size()>20) history.removeFirst();
        }
    }
    public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
        socket.setTextMessageSizeLimit(100_000);
        if(clients.size()>=32) { socket.close(CloseStatus.SERVICE_OVERLOAD); return; }
        Client c = new Client(socket); clients.put(socket.getId(),c);
        synchronized(c) { send(c,null,"hello",Map.of("mode",Application.live()?"live":"mock")); }
    }
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws Exception {
        Client c=clients.get(socket.getId()); if(c==null) return;
        synchronized(c) {
            try {
                JsonNode n=json.readTree(message.getPayload()); String type=n.path("type").asText(); long id=n.path("turnId").asLong(-1);
                if(id<1 || id>9_000_000_000L) return;
                if(type.equals("begin")) {
                    if(c.current!=null && id<=c.current.id) return;
                    c.commit(c.current,true); if(c.current!=null)c.current.cancel();
                    Turn t = new Turn(id); c.current=t;
                    String input=n.path("text").asText("").strip();
                    boolean voice=n.path("voice").asBoolean();
                    if(!voice && (input.isBlank() || input.length()>1000)) { send(c,t,"error",Map.of("message","请输入 1–1000 字。")); return; }
                    send(c,t,"state",Map.of("state",voice?"listening":"thinking"));
                    if(!capacity.tryAcquire()) { send(c,t,"error",Map.of("message","当前会话繁忙，请稍后重试。")); return; }
                    // Register cancellation inside the running task so queued cancellation cannot leak a permit.
                    workers.submit(() -> { try { t.onCancel(Thread.currentThread()::interrupt); run(c,t,input,voice,n.path("fault").asText()); } finally { capacity.release(); } });
                } else if(c.current!=null && c.current.id==id && c.current.active()) {
                    Turn t=c.current;
                    switch(type) {
                        case "audio" -> {
                            if(t.recording.isDone()) return;
                            byte[] chunk=Base64.getDecoder().decode(n.path("data").asText());
                            if(t.input.size()+chunk.length>960_000) { t.cancel(); send(c,null,"error",Map.of("turnId",id,"message","录音最长 30 秒，请重新开始。")); }
                            else t.input.writeBytes(chunk);
                        }
                        case "end" -> t.recording.complete(t.input.toByteArray());
                        case "cancel" -> { c.commit(t,true); t.cancel(); }
                        case "played" -> { int index=n.path("index").asInt(-1); if(index==t.acknowledged+1 && index<t.delivered.size()) t.acknowledged=index; }
                        case "complete" -> { if(t.acknowledged==t.delivered.size()-1)c.commit(t,false); }
                    }
                }
            } catch(Exception e) { send(c,c.current,"error",Map.of("message","请求格式不正确，请重新开始。")); }
        }
    }
    private void run(Client c,Turn t,String input,boolean voice,String fault) {
        try {
            t.check();
            if(voice) {
                byte[] pcm=t.recording.get(35,TimeUnit.SECONDS); t.check();
                if(pcm.length<3200) throw new IllegalArgumentException("录音太短，请再说一句完整的话。");
                if(Application.live()) input=api.recognize(pcm,t);
                else input="给我看看旅行照片";
                emit(c,t,"transcript",Map.of("text",input,"simulated",!Application.live()));
                emit(c,t,"state",Map.of("state","thinking"));
            }
            List<Map<String,String>> history; int count;
            synchronized(c) { t.check(); history=List.copyOf(c.history); count=c.count; }
            if(!Application.live() && fault.equals("timeout")) { Thread.sleep(1800); throw new TimeoutException(); }
            Direction d;
            if(Application.live()) d=api.reply(input,history,t);
            else { Thread.sleep(650); d=Direction.mock(input,count); }
            synchronized(c) { t.check(); if(c.current!=t)return; c.history.add(Map.of("role","user","content",input)); c.count++; }
            List<String> parts=Application.live()?Arrays.stream(d.text().split("(?<=[。！？])")).filter(s->!s.isBlank()).toList():List.of(d.text());
            for(int i=0;i<parts.size();i++) {
                t.check(); String part=parts.get(i); var event=new HashMap<String,Object>();
                event.put("index",i); event.put("text",part); event.put("emotion",d.emotion()); event.put("action",i==0?d.action():"none"); event.put("scene",i==0?d.scene():"keep");
                if(Application.live()) {
                    try { event.put("audio",Base64.getEncoder().encodeToString(api.synthesize(part,t))); }
                    catch(CancellationException e) { throw e; }
                    catch(Exception e) { t.check(); event.put("warning","语音暂不可用，本轮改为字幕。可继续输入重试。"); }
                } else {
                    event.put("audioUrl","/audio/"+d.clip()+".wav");
                    if(fault.equals("media")) { event.remove("audioUrl"); event.put("warning","模拟媒体加载失败：已切换字幕与静态角色。"); event.put("mediaFailed",true); }
                    if(fault.equals("late")) Thread.sleep(4000);
                }
                synchronized(c) { t.check(); if(c.current!=t)return; t.delivered.add(part); send(c,t,"segment",event); }
                if(d.action().equals("photo") && i==0) {
                    emit(c,t,"video",Map.of("url","/videos/lighthouse.mp4","caption","海的另一边，也有光。"));
                }
            }
            emit(c,t,"done",Map.of());
        } catch(CancellationException|InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(Exception e) {
            String msg=e instanceof TimeoutException?"等待超时，请重试或换个话题。": e instanceof IllegalArgumentException || e instanceof IllegalStateException?e.getMessage():"服务暂时不可用，请重试；也可切换 Mock 体验。";
            emit(c,t,"error",Map.of("message",msg));
        }
    }
    private void emit(Client c,Turn t,String type,Map<String,Object> data) { synchronized(c) { try { send(c,t,type,data); } catch(Exception ignored) {} } }
    private void send(Client c,Turn t,String type,Map<String,Object> data) throws Exception {
        if(!c.socket.isOpen() || t!=null && (c.current!=t || !t.active()))return;
        var event=new HashMap<>(data); event.put("type",type); if(t!=null)event.put("turnId",t.id);
        c.socket.sendMessage(new TextMessage(json.writeValueAsString(event)));
    }
    public void afterConnectionClosed(WebSocketSession socket,CloseStatus status) { Client c=clients.remove(socket.getId()); if(c!=null)synchronized(c){ if(c.current!=null)c.current.cancel(); } }
    public void handleTransportError(WebSocketSession socket,Throwable e) throws Exception { socket.close(CloseStatus.SERVER_ERROR); afterConnectionClosed(socket,CloseStatus.SERVER_ERROR); }
    @PreDestroy void stop() { clients.values().forEach(c->{synchronized(c){if(c.current!=null)c.current.cancel();}}); workers.shutdownNow(); }
}
