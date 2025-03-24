package com.taxPlugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class TaxPlugin extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ = null;
    private BukkitTask taxTask = null;
    
    private double taxRate = 0.04;
    private long taxIntervalHours = 2;
    private double minimumTaxableBalance = 100.0;
    private boolean notifyPlayers = true;
    private boolean useServerAccount = false;
    private String serverAccount = "server_bank";
    private boolean debug = false;
    
    private double totalTaxesCollected = 0.0;
    private Map<UUID, Double> playerTaxContributions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
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
        getLogger().info("TaxPlugin has been disabled! Total taxes collected: " + econ.format(totalTaxesCollected));
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
            collectTaxes();
        }, intervalTicks, intervalTicks);
        
        if (debug) {
            getLogger().info("Tax scheduler started. Next collection in " + taxIntervalHours + " hours.");
        }
    }

    private void collectTaxes() {
        double sessionTaxes = 0.0;
        int playersCount = 0;
        int exemptCount = 0;
        int belowMinimumCount = 0;
        
        if (debug) {
            getLogger().info("Starting tax collection...");
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("taxplugin.exempt")) {
                exemptCount++;
                if (debug) {
                    getLogger().info("Player " + player.getName() + " is exempt from taxes.");
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
            
            // Add to statistics
            sessionTaxes += taxAmount;
            totalTaxesCollected += taxAmount;
            
            UUID playerId = player.getUniqueId();
            playerTaxContributions.put(playerId, 
                playerTaxContributions.getOrDefault(playerId, 0.0) + taxAmount);
            
            playersCount++;
            
            // Deposit to server account if configured
            if (useServerAccount) {
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
        
        getLogger().info("Tax collection complete. Collected " + econ.format(sessionTaxes) + 
                         " from " + playersCount + " players. " +
                         exemptCount + " exempt, " + belowMinimumCount + " below minimum balance.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tax")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("taxplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
            sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Info ===");
            sender.sendMessage(ChatColor.YELLOW + "Tax rate: " + ChatColor.WHITE + (taxRate * 100) + "%");
            sender.sendMessage(ChatColor.YELLOW + "Tax interval: " + ChatColor.WHITE + taxIntervalHours + " hours");
            sender.sendMessage(ChatColor.YELLOW + "Minimum taxable balance: " + ChatColor.WHITE + 
                               econ.format(minimumTaxableBalance));
            sender.sendMessage(ChatColor.YELLOW + "Total taxes collected: " + ChatColor.WHITE + 
                               econ.format(totalTaxesCollected));
            return true;
        }
        
        if (args[0].equalsIgnoreCase("collect")) {
            if (!sender.hasPermission("taxplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
            sender.sendMessage(ChatColor.YELLOW + "Manually collecting taxes...");
            collectTaxes();
            sender.sendMessage(ChatColor.GREEN + "Tax collection completed!");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("taxplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
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
            if (!sender.hasPermission("taxplugin.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }
            
            sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Statistics ===");
            sender.sendMessage(ChatColor.YELLOW + "Total taxes collected: " + ChatColor.WHITE + 
                               econ.format(totalTaxesCollected));
            sender.sendMessage(ChatColor.YELLOW + "Top taxpayers:");
            
            // Display top 5 taxpayers
            playerTaxContributions.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    sender.sendMessage(ChatColor.WHITE + " - " + playerName + ": " + 
                                     ChatColor.GOLD + econ.format(entry.getValue()));
                });
            
            return true;
        }

        showHelp(sender);
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Commands ===");
        
        if (sender.hasPermission("taxplugin.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/tax info " + ChatColor.WHITE + "- Display tax configuration and stats");
            sender.sendMessage(ChatColor.YELLOW + "/tax collect " + ChatColor.WHITE + "- Manually collect taxes now");
            sender.sendMessage(ChatColor.YELLOW + "/tax reload " + ChatColor.WHITE + "- Reload plugin configuration");
            sender.sendMessage(ChatColor.YELLOW + "/tax stats " + ChatColor.WHITE + "- Show detailed tax statistics");
        } else {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use TaxPlugin commands!");
        }
    }
}