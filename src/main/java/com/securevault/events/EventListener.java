package com.securevault.events;

/**
 * Functional interface representing an Observer in the Observer Pattern.
 *
 * <p><b>Design Pattern:</b> Observer — decouples event producers from consumers.
 * When an event fires (e.g., "login succeeded"), the {@link EventBus} notifies
 * all registered listeners without the producer knowing who they are.
 *
 * <p><b>Why a functional interface?</b> Allows lambda registration:
 * <pre>
 *   eventBus.subscribe(EventType.LOGIN_SUCCESS, event -&gt;
 *       auditLogger.log(event.getUserId(), "LOGIN", event.getDetail()));
 * </pre>
 *
 * @param <T> the event type this listener handles
 * @author SecureVault Pro
 * @version 1.0.0
 */
@FunctionalInterface
public interface EventListener<T extends SecureVaultEvent> {

    /**
     * Called by the {@link EventBus} when a relevant event is published.
     *
     * @param event the event that was fired
     */
    void onEvent(T event);
}
