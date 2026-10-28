package cafe;

import com.fasterxml.jackson.databind.*;
import java.util.*;

record Direction(String text, String emotion, String action, String scene, String clip) {
    private static String allowed(JsonNode n, String key, Set<String> choices, String fallback) {
        String s = n.path(key).asText(); return choices.contains(s) ? s : fallback;
    }
    static Direction parse(ObjectMapper json, String raw) {
        try {
            String cleaned = raw.strip().replaceAll("^```(?:json)?\\s*|\\s*```$", "");
            // Extract JSON object if embedded in other text
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1);
            JsonNode n = json.readTree(cleaned);
            String text = n.path("text").asText("").strip();
            if (text.isBlank()) throw new IllegalArgumentException();
            return new Direction(limit(text), allowed(n,"emotion",Set.of("calm","happy","worried"),"calm"),
                allowed(n,"action",Set.of("none","window","photo"),"none"),
                allowed(n,"scene",Set.of("keep","rain","clear"),"keep"), null);
        } catch (Exception e) {
            // Never read malformed JSON/code aloud. Plain prose is a usable fallback.
            String text = raw.strip();
            if (text.startsWith("{") || text.startsWith("[") || text.startsWith("```")) text = "抱歉，我刚刚有些走神。你愿意再说一次吗？";
            return new Direction(limit(text.isBlank() ? "我们慢慢聊。" : text),"calm","none","keep",null);
        }
    }
    static String limit(String text) { return text.substring(0, Math.min(240, text.length())); }
    static Direction mock(String input, int count) {
        if (input.matches(".*(照片|摄影|旅行|看看|相机).*")) return new Direction("给你看这张照片。那天海边的灯塔刚亮起来，我妹妹站在光里。她说，迷路的人也会找到回家的方向。你有没有一张一直舍不得删掉的照片？","happy","photo","keep","photo");
        if (input.matches(".*(等谁|等人|等待|妹妹|约定).*")) return new Direction("其实，我在等妹妹的来信。我们约好每到一座新城市，就寄一张照片。已经很久没收到她的消息了。也许，她只是走到一个没有信号的地方。","worried","window","rain","waiting");
        if (input.matches(".*(雨|天亮|窗外|希望|陪|会好|别担心).*")) return new Direction("你看，雨好像停了。刚才我一直盯着远处，差点错过窗上的第一道光。谢谢你愿意陪我坐一会儿。等天亮，我们去街角拍张照片吧。","happy","window","clear","clear");
        if (input.matches(".*(再见|走了|晚安).*")) return new Direction("晚安。谢谢你把这个陌生的夜晚，变成了一段值得记住的旅程。下次下雨时，也许我们还会在这里碰面。","happy","none","keep","bye");
        if (count == 0 || input.matches(".*(你好|开始|嗨).*")) return new Direction("你也来躲雨吗？坐吧，咖啡还是热的。我叫米拉，是个旅行摄影师。今晚这里很安静，安静得让人忍不住想起一些旧事。","calm","none","keep","hello");
        return new Direction("我喜欢听别人讲旅途里的小事。有时候，一次偶然的停留，比目的地更让人难忘。你想听听我在等谁，还是看看相机里的旅行照片？","calm","none","keep","chat");
    }
}
