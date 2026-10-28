package cafe;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocketIntegrationTest {
 @LocalServerPort int port;
 final ObjectMapper json=new ObjectMapper();
 class Peer implements WebSocket.Listener,AutoCloseable {
   final BlockingQueue<JsonNode> events=new LinkedBlockingQueue<>(); final StringBuilder buffer=new StringBuilder();WebSocket ws;
   Peer() throws Exception {ws=HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create("ws://localhost:"+port+"/ws"),this).get(5,TimeUnit.SECONDS);await("hello",0);}
   public void onOpen(WebSocket ws){ws.request(1);}
   public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){buffer.append(data);if(last){try{events.add(json.readTree(buffer.toString()));buffer.setLength(0);}catch(Exception e){throw new RuntimeException(e);}}ws.request(1);return null;}
   void send(String text) throws Exception {ws.sendText(text,true).get(2,TimeUnit.SECONDS);}
   JsonNode await(String type,long id) throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<deadline){JsonNode n=events.poll(200,TimeUnit.MILLISECONDS);if(n!=null&&n.path("type").asText().equals(type)&&(id==0||n.path("turnId").asLong()==id))return n;}throw new AssertionError("Missing event "+type+" "+id);}
   public void close(){ws.abort();}
 }
 @Test void dialogueTriggersPhotoAndCarriesSameTurn() throws Exception {try(Peer p=new Peer()){p.send("{\"type\":\"begin\",\"turnId\":1,\"text\":\"看看照片\"}");JsonNode n=p.await("segment",1);assertEquals("photo",n.path("action").asText());assertEquals("/audio/photo.wav",n.path("audioUrl").asText());p.await("done",1);}}
 @Test void cancellingDelayedReplyNeverEmitsOldMedia() throws Exception {try(Peer p=new Peer()){p.send("{\"type\":\"begin\",\"turnId\":1,\"text\":\"照片\",\"fault\":\"late\"}");p.await("state",1);Thread.sleep(900);p.send("{\"type\":\"begin\",\"turnId\":2,\"text\":\"我陪你等雨停\"}");assertEquals("clear",p.await("segment",2).path("scene").asText());p.await("done",2);Thread.sleep(4200);assertTrue(p.events.stream().noneMatch(n->n.path("turnId").asLong()==1&&n.path("type").asText().equals("segment")));}}
 @Test void timeoutCanRecoverAndMediaFailureRetainsText() throws Exception {try(Peer p=new Peer()){p.send("{\"type\":\"begin\",\"turnId\":1,\"text\":\"你好\",\"fault\":\"timeout\"}");p.await("error",1);p.send("{\"type\":\"begin\",\"turnId\":2,\"text\":\"照片\",\"fault\":\"media\"}");JsonNode n=p.await("segment",2);assertTrue(n.path("mediaFailed").asBoolean());assertFalse(n.has("audioUrl"));assertFalse(n.path("text").asText().isBlank());}}
 @Test void shortRecordingReturnsRecoverableError() throws Exception {try(Peer p=new Peer()){p.send("{\"type\":\"begin\",\"turnId\":1,\"voice\":true}");p.await("state",1);p.send("{\"type\":\"end\",\"turnId\":1}");assertTrue(p.await("error",1).path("message").asText().contains("录音太短"));}}
}
