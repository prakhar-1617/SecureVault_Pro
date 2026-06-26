package com.securevault.decorators;

import com.securevault.encryption.EncryptionStrategy;

/**
 * Abstract base decorator that wraps another {@link EncryptionStrategy}.
 *
 * <p><b>Design Pattern:</b> Decorator — attaches additional responsibilities
 * to an object dynamically. Decorators provide a flexible alternative to
 * subclassing for extending functionality.
 *
 * <p><b>Pipeline construction example:</b>
 * <pre>
 *   EncryptionStrategy pipeline =
 *       new ChecksumDecorator(          // Step 3: add checksum
 *           new AESDecorator(           // Step 2: AES encrypt
 *               new CompressionDecorator(  // Step 1: compress
 *                   new AESStrategy()
 *               )
 *           )
 *       );
 * </pre>
 *
 * <p><b>Interview Q:</b> "How is this different from inheritance?"
 * — Inheritance is compile-time and rigid. Decorators compose behavior at
 * runtime from reusable building blocks (Open-Closed Principle in action).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public abstract class EncryptionDecorator implements EncryptionStrategy {

    /** The inner strategy being decorated. */
    protected final EncryptionStrategy inner;

    /**
     * @param inner the wrapped strategy or decorator
     */
    protected EncryptionDecorator(EncryptionStrategy inner) {
        this.inner = inner;
    }

    // Subclasses MUST override encrypt() and decrypt() to add their behavior,
    // then delegate to inner.encrypt() / inner.decrypt() as appropriate.
}
