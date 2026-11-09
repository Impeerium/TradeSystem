package de.codingair.tradesystem.spigot.trade;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import de.codingair.tradesystem.spigot.TradeSystem;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread affinity for trades on Folia/Canvas. There every player belongs to exactly one region
 * thread and the global region owns no player at all, so menu, inventory and drop work for a
 * trader must run on that trader's own scheduler. Without regions (Paper) callers keep
 * TradeSystem's original main-thread scheduling; branch on {@link #regionized()}.
 */
public final class TradeThreads {
    private TradeThreads() {
    }

    /**
     * @return True on Folia and its forks (Canvas), where players are ticked by separate region threads.
     */
    public static boolean regionized() {
        return UniversalScheduler.isFolia;
    }

    /**
     * @param player The player to touch.
     * @return True if the current thread may touch the player. Always true without regions.
     */
    public static boolean owns(@NotNull Player player) {
        return !regionized() || Bukkit.isOwnedByCurrentRegion(player);
    }

    /**
     * Runs the task on the player's own thread after the delay (at least one tick).
     *
     * @return False if the player has already left and the task will never run.
     */
    public static boolean later(@NotNull Player player, @NotNull Runnable task, long delay) {
        return player.getScheduler().execute(TradeSystem.getInstance(), task, null, Math.max(1, delay));
    }

    /**
     * Runs the task now if the current thread owns the player, otherwise on the player's next tick.
     */
    public static void now(@NotNull Player player, @NotNull Runnable task) {
        if (owns(player)) task.run();
        else later(player, task, 1);
    }

    /**
     * Repeats the task on the player's own thread, starting with the next tick.
     *
     * @return The task, or null if the player has already left.
     */
    @Nullable
    public static ScheduledTask repeat(@NotNull Player player, @NotNull Runnable task, long period) {
        return player.getScheduler().runAtFixedRate(TradeSystem.getInstance(), scheduled -> task.run(), null, 1, Math.max(1, period));
    }
}
