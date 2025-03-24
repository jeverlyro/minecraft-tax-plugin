package com.taxPlugin;

import com.taxPlugin.storage.TaxStorage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class TaxPlugin extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ = null;
    private BukkitTask taxTask = null;
    private TaxStorage taxStorage;
    
    // Config values with defaults
    private double taxRate = 0.04;
    private long taxIntervalHours = 2;
    private double minimumTaxableBalance = 100.0;
    private boolean notifyPlayers = true;
    private boolean useServerAccount = false;
    private String serverAccount = "server_bank";
    private boolean debug = false;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize tax storage
        taxStorage = new TaxStorage(this);
        
        // Load configuration
        loadConfig();
        
        // Setup Vault
        if (!setupEconomy()) {
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register command executor
        getCommand("tax").setExecutor(this);

        // Start the tax scheduler
        startTaxScheduler();

        getLogger().info("TaxPlugin has been enabled! Players will be taxed " + (taxRate * 100) + "% every " + 
                         taxIntervalHours + " hours.");
    }

    @Override
    public void onDisable() {
        if (taxTask != null) {
            taxTask.cancel();
        }
        // Save tax data before shutdown
        taxStorage.saveData();
        getLogger().info("TaxPlugin has been disabled! Total taxes collected: " + econ.format(taxStorage.getTotalCollected()));
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        
        taxRate = config.getDouble("tax-rate", 4.0) / 100.0; // Convert percentage to decimal
        taxIntervalHours = config.getLong("tax-interval", 2);
        minimumTaxableBalance = config.getDouble("minimum-taxable-balance", 100.0);
        notifyPlayers = config.getBoolean("notify-players", true);
        useServerAccount = config.getBoolean("use-server-account", false);
        serverAccount = config.getString("server-account", "server_bank");
        debug = config.getBoolean("debug", false);
        
        if (debug) {
            getLogger().info("Loaded configuration: tax rate=" + (taxRate * 100) + "%, interval=" + taxIntervalHours + 
                            "h, min balance=" + minimumTaxableBalance);
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private void startTaxScheduler() {
        // Convert hours to ticks (20 ticks = 1 second)
        long intervalTicks = taxIntervalHours * 60 * 60 * 20;
        
        taxTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            collectTaxes(null, false);
        }, intervalTicks, intervalTicks);
        
        if (debug) {
            getLogger().info("Tax scheduler started. Next collection in " + taxIntervalHours + " hours.");
        }
    }

    private double collectTaxes(Player collector, boolean manualCollection) {
        double sessionTaxes = 0.0;
        int onlinePlayersCount = 0;
        int offlinePlayersCount = 0;
        int exemptCount = 0;
        int belowMinimumCount = 0;
        
        if (debug) {
            getLogger().info("Starting tax collection...");
        }
        
        // First, tax online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip players with op or admin permission
            if (player.isOp() || player.hasPermission("taxplugin.exempt") || player.hasPermission("taxplugin.admin")) {
                exemptCount++;
                if (debug) {
                    getLogger().info("Player " + player.getName() + " is exempt from taxes (OP or has admin permission).");
                }
                continue;
            }

            double balance = econ.getBalance(player);
            
            if (balance < minimumTaxableBalance) {
                belowMinimumCount++;
                if (debug) {
                    getLogger().info("Player " + player.getName() + " has balance below minimum taxable amount.");
                }
                continue;
            }
            
            double taxAmount = balance * taxRate;

            if (taxAmount <= 0) {
                continue;
            }

            // Withdraw the tax amount
            econ.withdrawPlayer(player, taxAmount);
            
            // Add to session taxes
            sessionTaxes += taxAmount;
            
            // Add to statistics
            taxStorage.recordTax(player.getUniqueId(), taxAmount, false);
            
            onlinePlayersCount++;
            
            // Deposit to server account if configured and not manually collected
            if (useServerAccount && !manualCollection) {
                econ.depositPlayer(serverAccount, taxAmount);
            }

            // Notify the player if enabled
            if (notifyPlayers) {
                player.sendMessage(ChatColor.RED + "You have been taxed " + 
                    ChatColor.GOLD + econ.format(taxAmount) + 
                    ChatColor.RED + " (" + (taxRate * 100) + "% of your balance)");
            }
            
            if (debug) {
                getLogger().info("Taxed player " + player.getName() + " for " + econ.format(taxAmount));
            }
        }
        
        // Now tax offline players
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            // Skip players who are currently online (already processed)
            if (offlinePlayer.isOnline()) {
                continue;
            }
            
            // Skip players who haven't played before
            if (!offlinePlayer.hasPlayedBefore()) {
                continue;
            }
            
            // Check if they're exempt (we'll use hasPermission for OfflinePlayer which works if permission plugin supports it)
            if (offlinePlayer.isOp() || hasOfflinePermission(offlinePlayer, "taxplugin.exempt") || 
                hasOfflinePermission(offlinePlayer, "taxplugin.admin")) {
                exemptCount++;
                if (debug) {
                    getLogger().info("Offline player " + offlinePlayer.getName() + " is exempt from taxes.");
                }
                continue;
            }

            double balance = econ.getBalance(offlinePlayer);
            
            if (balance < minimumTaxableBalance) {
                belowMinimumCount++;
                if (debug) {
                    getLogger().info("Offline player " + offlinePlayer.getName() + " has balance below minimum taxable amount.");
                }
                continue;
            }
            
            double taxAmount = balance * taxRate;

            if (taxAmount <= 0) {
                continue;
            }

            // Withdraw the tax amount from offline player
            econ.withdrawPlayer(offlinePlayer, taxAmount);
            
            // Add to session taxes if this is a manual collection
            if (manualCollection) {
                sessionTaxes += taxAmount;
                taxStorage.recordTax(offlinePlayer.getUniqueId(), taxAmount, false); // Count as manually collected
            } else {
                // Record as offline tax (to be collected later)
                taxStorage.recordTax(offlinePlayer.getUniqueId(), taxAmount, true);
            }
            
            offlinePlayersCount++;
            
            if (debug) {
                getLogger().info("Taxed offline player " + offlinePlayer.getName() + " for " + econ.format(taxAmount));
            }
        }
        
        // If manual collection and there's a collector, deposit the taxes to them
        if (manualCollection && collector != null && sessionTaxes > 0) {
            econ.depositPlayer(collector, sessionTaxes);
            collector.sendMessage(ChatColor.GREEN + "You received " + ChatColor.GOLD + 
                                 econ.format(sessionTaxes) + ChatColor.GREEN + " from taxes!");
        }
        
        // Save tax data after collection
        taxStorage.saveData();
        
        getLogger().info("Tax collection complete. Collected from " + onlinePlayersCount + " online players and " + 
                         offlinePlayersCount + " offline players. " +
                         exemptCount + " exempt, " + belowMinimumCount + " below minimum balance.");
        
        return sessionTaxes;
    }
    
    private boolean hasOfflinePermission(OfflinePlayer player, String permission) {
        // Try to check permission with LuckPerms if available
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                // This is a safe way to check without direct dependency
                // LuckPerms API would typically be used here, but we'll rely on Bukkit's permission system
                return player.isOp() || Bukkit.getServer().getPluginManager()
                    .getPermission(permission).getDefault().getValue(player.isOp());
            } catch (Exception e) {
                getLogger().warning("Error checking LuckPerms permissions for offline player: " + e.getMessage());
            }
        }
        
        // Fallback to using Bukkit's permission system
        return player.isOp();
    }
    
    public boolean collectOfflineTaxes(CommandSender sender, String targetPlayerName) {
        if (taxStorage.getOfflineCollected() <= 0) {
            sender.sendMessage(ChatColor.RED + "There are no offline taxes to collect!");
            return false;
        }
        
        // If target is specified, try to deposit to their account
        Player targetPlayer = null;
        if (targetPlayerName != null && !targetPlayerName.isEmpty()) {
            targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "Player " + targetPlayerName + " is not online!");
                return false;
            }
        } else if (sender instanceof Player) {
            // Default to sender if they're a player
            targetPlayer = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Please specify a target player to receive the collected taxes!");
            return false;
        }
        
        double amount = taxStorage.getOfflineCollected();
        
        // Deposit to the target player
        econ.depositPlayer(targetPlayer, amount);
        
        // Clear the offline collected amount
        taxStorage.withdrawOfflineCollected(amount);
        
        // Notify about the transaction
        sender.sendMessage(ChatColor.GREEN + "Successfully collected " + ChatColor.GOLD + econ.format(amount) + 
                          ChatColor.GREEN + " in offline taxes and deposited to " + targetPlayer.getName() + "!");
        
        if (sender != targetPlayer) {
            targetPlayer.sendMessage(ChatColor.GREEN + "You received " + ChatColor.GOLD + econ.format(amount) + 
                                    ChatColor.GREEN + " in collected offline taxes!");
        }
        
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tax")) {
            return false;
        }

        // Check permissions - Only OPs or players with taxplugin.admin permission can use these commands
        if (!sender.isOp() && !sender.hasPermission("taxplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }
        
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {            
            sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Info ===");
            sender.sendMessage(ChatColor.YELLOW + "Tax rate: " + ChatColor.WHITE + (taxRate * 100) + "%");
            sender.sendMessage(ChatColor.YELLOW + "Tax interval: " + ChatColor.WHITE + taxIntervalHours + " hours");
            sender.sendMessage(ChatColor.YELLOW + "Minimum taxable balance: " + ChatColor.WHITE + 
                               econ.format(minimumTaxableBalance));
            sender.sendMessage(ChatColor.YELLOW + "Total taxes collected: " + ChatColor.WHITE + 
                               econ.format(taxStorage.getTotalCollected()));
            sender.sendMessage(ChatColor.YELLOW + "Offline taxes waiting for collection: " + ChatColor.WHITE +
                              econ.format(taxStorage.getOfflineCollected()));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("collect")) {
            sender.sendMessage(ChatColor.YELLOW + "Manually collecting taxes...");
            
            if (sender instanceof Player) {
                Player player = (Player) sender;
                double collected = collectTaxes(player, true);
                sender.sendMessage(ChatColor.GREEN + "Tax collection completed! You received " + 
                                 ChatColor.GOLD + econ.format(collected) + ChatColor.GREEN + " in taxes.");
            } else {
                collectTaxes(null, false);
                sender.sendMessage(ChatColor.GREEN + "Tax collection completed! Taxes were stored for manual collection.");
            }
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            // Reload configuration
            reloadConfig();
            loadConfig();
            
            // Restart tax scheduler
            if (taxTask != null) {
                taxTask.cancel();
            }
            startTaxScheduler();
            
            sender.sendMessage(ChatColor.GREEN + "TaxPlugin configuration reloaded!");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("stats")) {
            sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Statistics ===");
            sender.sendMessage(ChatColor.YELLOW + "Total taxes collected: " + ChatColor.WHITE + 
                               econ.format(taxStorage.getTotalCollected()));
            sender.sendMessage(ChatColor.YELLOW + "Offline taxes waiting for collection: " + ChatColor.WHITE +
                              econ.format(taxStorage.getOfflineCollected()));
            sender.sendMessage(ChatColor.YELLOW + "Top taxpayers:");
            
            // Display top 5 taxpayers
            taxStorage.getPlayerTaxData().entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    sender.sendMessage(ChatColor.WHITE + " - " + playerName + ": " + 
                                     ChatColor.GOLD + econ.format(entry.getValue()));
                });
            
            return true;
        }
        
        if (args[0].equalsIgnoreCase("offlinetax")) {
            sender.sendMessage(ChatColor.GREEN + "=== Offline Tax Info ===");
            sender.sendMessage(ChatColor.YELLOW + "Offline taxes waiting for collection: " + ChatColor.WHITE +
                              econ.format(taxStorage.getOfflineCollected()));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("collect-offline")) {
            String targetPlayer = (args.length > 1) ? args[1] : "";
            collectOfflineTaxes(sender, targetPlayer);
            return true;
        }

        showHelp(sender);
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Commands ===");
        
        if (sender.isOp() || sender.hasPermission("taxplugin.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/tax info " + ChatColor.WHITE + "- Display tax configuration and stats");
            sender.sendMessage(ChatColor.YELLOW + "/tax collect " + ChatColor.WHITE + "- Manually collect taxes now and receive them");
            sender.sendMessage(ChatColor.YELLOW + "/tax reload " + ChatColor.WHITE + "- Reload plugin configuration");
            sender.sendMessage(ChatColor.YELLOW + "/tax stats " + ChatColor.WHITE + "- Show detailed tax statistics");
            sender.sendMessage(ChatColor.YELLOW + "/tax offlinetax " + ChatColor.WHITE + "- Show offline tax information");
            sender.sendMessage(ChatColor.YELLOW + "/tax collect-offline [player] " + ChatColor.WHITE + "- Collect offline taxes to specified player or yourself");
        } else {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use TaxPlugin commands!");
        }
    }
}