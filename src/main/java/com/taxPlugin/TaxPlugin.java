package com.taxPlugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public class TaxPlugin extends JavaPlugin {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ = null;
    private BukkitTask taxTask = null;
    private static final double TAX_RATE = 0.04; // 4% tax rate
    private static final long TAX_INTERVAL = 2 * 60 * 60 * 20; // 2 hours in ticks (20 ticks = 1 second)

    @Override
    public void onEnable() {
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

        getLogger().info("TaxPlugin has been enabled! Players will be taxed " + (TAX_RATE * 100) + "% every 2 hours.");
    }

    @Override
    public void onDisable() {
        if (taxTask != null) {
            taxTask.cancel();
        }
        getLogger().info("TaxPlugin has been disabled!");
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
        taxTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            collectTaxes();
        }, TAX_INTERVAL, TAX_INTERVAL); // First run after 2 hours, then every 2 hours
    }

    private void collectTaxes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("taxplugin.exempt")) {
                continue; // Skip players with tax exemption
            }

            double balance = econ.getBalance(player);
            double taxAmount = balance * TAX_RATE;

            if (taxAmount <= 0) {
                continue; // Skip if player has no money or negative balance
            }

            // Withdraw the tax amount
            econ.withdrawPlayer(player, taxAmount);

            // Notify the player
            player.sendMessage(ChatColor.RED + "You have been taxed " + 
                ChatColor.GOLD + econ.format(taxAmount) + 
                ChatColor.RED + " (" + (TAX_RATE * 100) + "% of your balance)");
            
            getLogger().info("Taxed player " + player.getName() + " for " + econ.format(taxAmount));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("tax")) {
            return false;
        }

        if (!sender.hasPermission("taxplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(ChatColor.GREEN + "=== TaxPlugin Info ===");
            sender.sendMessage(ChatColor.YELLOW + "Tax rate: " + ChatColor.WHITE + (TAX_RATE * 100) + "%");
            sender.sendMessage(ChatColor.YELLOW + "Tax interval: " + ChatColor.WHITE + "2 hours");
            return true;
        }

        return false;
    }
} 