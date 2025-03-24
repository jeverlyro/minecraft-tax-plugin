# TaxPlugin

A simple PaperMC plugin that applies a 4% tax to players every 2 hours.

## Prerequisites
- PaperMC Server 1.19.x
- [Vault](https://www.spigotmc.org/resources/vault.34315/) and a compatible economy plugin (such as EssentialsX)

## Installation
1. Download the latest JAR file from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart the server
4. Ensure that Vault and an economy plugin are installed and working properly

## Features
- Deducts 4% from players' economy balance every 2 hours
- Only taxes players who are online
- Players with the `taxplugin.exempt` permission will not be taxed
- `/tax` command to view information about the plugin

## Permissions
- `taxplugin.admin` - Access to admin commands of the plugin (granted to ops by default)
- `taxplugin.exempt` - Exempts players from being taxed

## Usage
The `/tax info` command will display information about the tax rate and collection interval.

## Compilation
To build the plugin from source:
```
mvn clean package
```

The compiled plugin will be available in the `target/` folder.

## Notes
This plugin requires Vault and a compatible economy plugin to function properly.