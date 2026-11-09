package de.codingair.tradesystem.spigot.trade;

import com.github.Anon8281.universalScheduler.UniversalRunnable;
import de.codingair.tradesystem.spigot.TradeSystem;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The trade countdown. Paper runs it on the main thread as before. Folia runs it on the
 * initiating trader's own scheduler: UniversalScheduler would put a plain timer on the global
 * region, which may not close or fill any player's inventory, so every finish failed there.
 */
abstract class TradeCountdown extends UniversalRunnable {
    private ScheduledTask regionTask;
    private boolean regionCancelled;

    /**
     * @param anchor The initiating trader, whose thread runs the countdown on Folia.
     * @param period The interval in ticks.
     */
    final void start(@Nullable Player anchor, long period) {
        if (!TradeThreads.regionized()) {
            runTaskTimer(TradeSystem.getInstance(), 0, period);
            return;
        }

        regionTask = anchor == null ? null : TradeThreads.repeat(anchor, this, period);
        if (regionTask == null) regionCancelled = true;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        if (!TradeThreads.regionized()) {
            super.cancel();
            return;
        }

        regionCancelled = true;
        if (regionTask != null) regionTask.cancel();
    }

    @Override
    public synchronized boolean isCancelled() throws IllegalStateException {
        if (!TradeThreads.regionized()) return super.isCancelled();
        return regionCancelled;
    }
}
