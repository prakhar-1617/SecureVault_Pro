package com.securevault.events;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton publish-subscribe event bus that wires the application together
 * without tight coupling between modules.
 *
 * <p><b>Design Pattern:</b> Observer (Publish-Subscribe variant) + Singleton.
 *
 * <p><b>How it works:</b>
 * <pre>
 *   AuthenticationService.login() succeeds
 *       → eventBus.publish(LoginSuccess event)
 *           → AuditLogger.onEvent()      // writes log
 *           → AnalyticsObserver.onEvent() // updates stats
 * </pre>
 *
 * <p><b>Thread Safety:</b> Uses {@code synchronized} on subscribe/publish to prevent
 * concurrent modification of the listener map. For high-throughput systems,
 * a {@code CopyOnWriteArrayList} per event type would reduce lock contention.
 *
 * <p><b>Interview talking point:</b> "How is this different from a direct method call?"
 * — The publisher doesn't know or depend on its observers. Adding a new observer
 * (e.g., a dashboard) requires zero changes to existing producers.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class EventBus {

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    private static volatile EventBus instance;

    private EventBus() {}

    public static EventBus getInstance() {
        if (instance == null) {
            synchronized (EventBus.class) {
                if (instance == null) {
                    instance = new EventBus();
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Listener registry
    // ------------------------------------------------------------------ //

    /**
     * Maps event types to their registered listeners.
     * {@link EnumMap} is used for O(1) lookup by enum key.
     */
    @SuppressWarnings("rawtypes")
    private final Map<SecureVaultEvent.Type, List<EventListener>> listeners =
            new EnumMap<>(SecureVaultEvent.Type.class);

    // ------------------------------------------------------------------ //
    //  Subscribe
    // ------------------------------------------------------------------ //

    /**
     * Registers a listener for a specific event type.
     *
     * @param type     the event type to listen for
     * @param listener the callback to invoke when that event fires
     */
    @SuppressWarnings("unchecked")
    public synchronized void subscribe(SecureVaultEvent.Type type, EventListener<SecureVaultEvent> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    /**
     * Registers a listener for ALL event types (wildcard subscription).
     * Useful for the AuditLogger which records every event.
     *
     * @param listener the callback to invoke for any event
     */
    public synchronized void subscribeAll(EventListener<SecureVaultEvent> listener) {
        for (SecureVaultEvent.Type type : SecureVaultEvent.Type.values()) {
            subscribe(type, listener);
        }
    }

    // ------------------------------------------------------------------ //
    //  Publish
    // ------------------------------------------------------------------ //

    /**
     * Publishes an event, notifying all registered listeners synchronously.
     *
     * <p>Listener exceptions are caught and printed to avoid cascading failures.
     *
     * @param event the event to publish
     */
    @SuppressWarnings("unchecked")
    public synchronized void publish(SecureVaultEvent event) {
        List<EventListener> eventListeners = listeners.get(event.getType());
        if (eventListeners == null || eventListeners.isEmpty()) return;

        for (EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                System.err.println("[EventBus] Listener error for " + event.getType() + ": " + e.getMessage());
            }
        }
    }
}
