package de.codingair.tradesystem.spigot.localization;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Queued native chat callbacks must recheck ownership, deadline and one-time consumption. */
final class CallbackGuard {
    private final UUID recipient;
    private final LongSupplier clock;
    private final long started;
    private final long lifetimeNanos;
    private final AtomicBoolean consumed = new AtomicBoolean();

    CallbackGuard(UUID recipient, long lifetimeNanos, LongSupplier clock) {
        this.recipient = recipient;
        this.lifetimeNanos = lifetimeNanos;
        this.clock = clock;
        this.started = clock.getAsLong();
    }

    boolean claim(UUID clickingPlayer) {
        return recipient.equals(clickingPlayer) && clock.getAsLong() - started < lifetimeNanos
                && consumed.compareAndSet(false, true);
    }
}
