package cafe;

import java.util.*;
import java.util.concurrent.*;

/** Cancellation is cooperative; identity checks are still mandatory before every side effect. */
final class Turn {
    final long id;
    private boolean cancelled;
    private final List<Runnable> cleanup = new ArrayList<>();
    final CompletableFuture<byte[]> recording = new CompletableFuture<>();
    final java.io.ByteArrayOutputStream input = new java.io.ByteArrayOutputStream();
    int acknowledged = -1;
    final List<String> delivered = new ArrayList<>();
    boolean committed;
    Turn(long id) { this.id = id; }
    synchronized boolean active() { return !cancelled; }
    synchronized void onCancel(Runnable action) { if (cancelled) action.run(); else cleanup.add(action); }
    synchronized void cancel() {
        if (cancelled) return;
        cancelled = true;
        recording.cancel(true);
        for (Runnable r : cleanup) { try { r.run(); } catch (Exception ignored) {} }
        cleanup.clear();
    }
    void check() { if (!active() || Thread.currentThread().isInterrupted()) throw new CancellationException(); }
}
