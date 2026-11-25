package org.mockbukkit.mockbukkit.scheduler.paper;

import com.google.common.base.Preconditions;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.exception.UnimplementedOperationException;

import java.util.function.Consumer;

/**
 * Test-only replacement for MockBukkit 4.116.1's class of the same name (test classes come first on the
 * classpath; nothing ships). The original throws from {@code cancel()}, so the ImpeeriumAPI 1.24 runtime's
 * shutdown fails inside {@code MockBukkit.unmock()} and every later test reports "Already mocking". This copy
 * keeps the original constructor, owner and run behaviour and implements cancellation as a flag: a cancelled
 * task no longer runs. Added for the Finnish rollout (2026-11-23) so the suite runs on the real 1.24 runtime.
 */
public class PaperScheduledTask implements ScheduledTask {
    private final Plugin plugin;
    private final Consumer<ScheduledTask> consumer;
    private volatile boolean cancelled;

    public PaperScheduledTask(Plugin plugin, Consumer<ScheduledTask> consumer) {
        Preconditions.checkNotNull(consumer, "Consumer cannot be null");
        this.plugin = plugin;
        this.consumer = consumer;
    }

    @Override public Plugin getOwningPlugin() { return plugin; }

    @Override public boolean isRepeatingTask() { throw new UnimplementedOperationException(); }

    public void run() {
        if (!cancelled) consumer.accept(this);
    }

    @Override public CancelledState cancel() {
        if (cancelled) return CancelledState.CANCELLED_ALREADY;
        cancelled = true;
        return CancelledState.CANCELLED_BY_CALLER;
    }

    @Override public ExecutionState getExecutionState() {
        return cancelled ? ExecutionState.CANCELLED : ExecutionState.IDLE;
    }
}
