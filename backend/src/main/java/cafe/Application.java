package cafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import java.util.Map;

@SpringBootApplication
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
    static String env(String key, String fallback) { String v = System.getProperty("raincafe."+key,System.getenv(key)); return v == null || v.isBlank() ? fallback : v; }
    static boolean live() { return env("APP_MODE", "mock").equals("live"); }
    @RestController static class Health {
        @GetMapping("/api/health") Map<String,Object> health() {
            return Map.of("status", "ok", "mode", live() ? "live" : "mock", "provider","dashscope","configured", !live() || !env("DASHSCOPE_API_KEY", "").isBlank());
        }
    }
    @Configuration @EnableWebSocket static class SocketConfig implements WebSocketConfigurer {
        private final SceneSocket handler;
        SocketConfig(SceneSocket handler) { this.handler = handler; }
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            // Spring's default same-origin policy; no wildcard credential-bearing socket access.
            registry.addHandler(handler, "/ws");
        }
    }
}
