package cafe;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class TurnTest {
 @Test void cancellationIsIdempotentAndCoversLateResources(){Turn t=new Turn(1);AtomicInteger calls=new AtomicInteger();t.onCancel(calls::incrementAndGet);t.cancel();t.cancel();t.onCancel(calls::incrementAndGet);assertEquals(2,calls.get());assertFalse(t.active());assertTrue(t.recording.isCancelled());assertThrows(java.util.concurrent.CancellationException.class,t::check);}
 @Test void invalidModelActionsCannotBecomeMediaUrls(){Direction d=Direction.parse(new ObjectMapper(),"{\"text\":\"你好\",\"emotion\":\"evil\",\"action\":\"https://untrusted.invalid\",\"scene\":\"javascript:alert(1)\"}");assertEquals("calm",d.emotion());assertEquals("none",d.action());assertEquals("keep",d.scene());}
 @Test void brokenJsonIsNotReadAloud(){Direction d=Direction.parse(new ObjectMapper(),"{bad json");assertFalse(d.text().contains("{"));}
 @Test void onlyAcknowledgedSegmentsEnterHistory(){var c=new SceneSocket.Client(null);Turn t=new Turn(1);t.delivered.add("听完的一句。");t.delivered.add("未播放的一句。");t.acknowledged=0;c.commit(t,true);assertTrue(c.history.getFirst().get("content").contains("听完的一句"));assertFalse(c.history.getFirst().get("content").contains("未播放的一句"));assertTrue(c.history.getFirst().get("content").contains("回复被打断"));c.commit(t,true);assertEquals(1,c.history.size());}
 @Test void mockEventsDependOnInput(){assertEquals("photo",Direction.mock("可以看看照片吗",0).action());assertEquals("worried",Direction.mock("你在等谁",1).emotion());assertEquals("clear",Direction.mock("我陪你等雨停",2).scene());}
}
