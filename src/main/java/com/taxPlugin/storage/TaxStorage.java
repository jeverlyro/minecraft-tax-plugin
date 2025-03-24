package com.taxPlugin.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;

public class TaxStorage {
    private final JavaPlugin plugin;
    private final Map<UUID, Double> playerTaxData = new ConcurrentHashMap<>();
    private double totalCollected = 0.0;
    private final File dataFile;
    private YamlConfiguration config;

    public TaxStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tax_data.yml");
        
        // Create data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // Load existing data if available
        loadData();
    }
    
    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create tax data file!");
                e.printStackTrace();
                return;
            }
        }
        
        config = YamlConfiguration.loadConfiguration(dataFile);
        
        totalCollected = config.getDouble("total_collected", 0.0);
        
        if (config.contains("player_data")) {
            for (String key : config.getConfigurationSection("player_data").getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    double amount = config.getDouble("player_data." + key, 0.0);
                    playerTaxData.put(playerId, amount);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in tax data: " + key);
                }
            }
        }
        
        plugin.getLogger().info("Loaded tax data for " + playerTaxData.size() + " players. Total collected: " + totalCollected);
    }
    
    public void saveData() {
        config.set("total_collected", totalCollected);
        
        // Save player data
        for (Map.Entry<UUID, Double> entry : playerTaxData.entrySet()) {
            config.set("player_data." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save tax data!");
            e.printStackTrace();
        }
    }
    
    public void recordTax(UUID playerId, double amount) {
        playerTaxData.put(playerId, playerTaxData.getOrDefault(playerId, 0.0) + amount);
        totalCollected += amount;
        
        // Save periodically to prevent data loss
        if (Math.random() < 0.1) { // 10% chance to save on each tax
            saveData();
        }
    }
    
    public double getTotalCollected() {
        return totalCollected;
    }
    
    public Map<UUID, Double> getPlayerTaxData() {
        return new HashMap<>(playerTaxData);
    }
    
    public double getPlayerTaxAmount(UUID playerId) {
        return playerTaxData.getOrDefault(playerId, 0.0);
    }
}