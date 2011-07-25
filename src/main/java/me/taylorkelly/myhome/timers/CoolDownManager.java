package me.taylorkelly.myhome.timers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import me.taylorkelly.myhome.HomePermissions;

import me.taylorkelly.myhome.HomeSettings;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages cooldown for a command. Subclasses determine specific settings that
 * determine cooldown timings and applicability.
 */
public abstract class CoolDownManager{
    private static final int SERVER_TICKS_PER_SEC = 20;

    private final ConcurrentHashMap<String, PlayerTaskDetails> players =
            new ConcurrentHashMap<String, PlayerTaskDetails>();

    /**
     * Activates cooldown for the specified player, using the current
     * confuration settings. Players that are configured to bypass cooldown will
     * be ignored.
     * 
     * @param player
     *            Player for whom cooldown is active.
     * @param plugin
     *            Plugin invoking the cooldown.
     */
    public void addPlayer(Player player, Plugin plugin) {
        if (isCoolingBypassed(player)) {
            return;
        }
        int timer = getTimer(player);
        
        if(timer > 0) {
            if (players.containsKey(player.getName())) {
                plugin.getServer().getScheduler().cancelTask(
                        players.get(player.getName()).getTaskIndex());
            }

            int taskIndex = plugin.getServer().getScheduler().scheduleSyncDelayedTask(
                    plugin, new CoolTask(player, this), timer * SERVER_TICKS_PER_SEC);
            players.put(
                    player.getName(),
                    new PlayerTaskDetails(
                            taskIndex, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timer)));
        }
    }

    /**
     * Whether or not the specified player has cooled down.
     * 
     * @param player
     *            Player to check.
     * @return True if the player has cooled down, otherwise false.
     */
    public boolean playerHasCooled(Player player) {
        return !players.containsKey(player.getName());
    }

	/**
	 * Calculates and returns the estimated time left for the specified player's
	 * cooldown. If the player is not currently cooling down, returns 0.
	 * 
	 * @param player
	 *            Player for whom the remaining cooldown is calculated and
	 *            returned.
	 * @return Estimated remaining cooldown, in seconds, or zero if no cooldown
	 *         remains. Note that small discrepancies in timing between the
	 *         expected and actual cooldown expiry mean that negative values
	 *         cannot be ruled out.
	 */
    public int calcEstimatedTimeLeft(Player player) {
        PlayerTaskDetails taskDetails = players.get(player.getName());
        if (taskDetails == null) {
            return 0;
        } else {
            int secondsLeft =(int) TimeUnit.MILLISECONDS.toSeconds(
                    players.get(player.getName()).getFinishTime() - System.currentTimeMillis());
            return (secondsLeft > 0) ? secondsLeft : 0;
        }
    }

    /**
     * Gets the total cooldown currently configured for the specified player,
     * including global cooldown and group/player-specific time.
     * 
     * @param player
     *            Player for whom cooldown is returned.
     * @return Total cooldown in seconds for the specified player.
     */
    public int getTimer(Player player) {
        int timer = 0;
        if (HomeSettings.timerByPerms) {
            timer = HomePermissions.integer(player, getCoolDownPermissionName(), getCoolDownSetting());
            if (HomeSettings.additionalTime) {
                timer += getCoolDownSetting();
            }
        } else {
            timer = getCoolDownSetting();
        }
        return timer;
    }

    /**
     * @return  Currently configured cooldown time.
     */
    protected abstract int getCoolDownSetting();
    
    /**
     * @return  Permission that controls the cooldown.
     */
    protected abstract String getCoolDownPermissionName();
    
    /**
     * Removes the player with the specified name from the cooldown list.
     * 
     * @param playerName
     *            Name of the player to remove.
     */
    protected void removePlayer(String playerName) {
        players.remove(playerName);
    }
    
    /**
     * Returns true if cooldown is bypassed for the specified player, otherwise
     * false.
     * 
     * @param player
     *            Player to check for cooldown bypass.
     * @return True if cooldown is bypassed, otherwise false.
     */
    protected abstract boolean isCoolingBypassed(Player player);

    /**
     * Invoked when cooldown has expired for the specified player, before the player is removed from cooldown list.
     * 
     * @param player
     *            Player for whom the cooldown has expired.
     */
    protected void onCoolDownExpiry(Player player) {}

    /**
     * Task used for scheduling, invoked when cooldown has expired.
     */
    private static class CoolTask implements Runnable {

        protected final Player player;
        
        protected final CoolDownManager coolDownManager;

        public CoolTask(Player player, CoolDownManager coolDownManager) {
            this.player = player;
            this.coolDownManager = coolDownManager;
        }

        public void run() {
            coolDownManager.onCoolDownExpiry(player);
            coolDownManager.removePlayer(player.getName());
        }
    }
}
