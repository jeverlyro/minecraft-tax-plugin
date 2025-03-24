# TaxPlugin

A PaperMC plugin that applies a configurable tax to players at regular intervals.

## Prerequisites
- PaperMC Server 1.19.x
- [Vault](https://www.spigotmc.org/resources/vault.34315/) and a compatible economy plugin (such as EssentialsX)

## Installation
1. Download the latest JAR file from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart the server
4. Ensure that Vault and an economy plugin are installed and working properly
5. Configure the plugin settings in the `config.yml` file that's generated on first run

## Features
- Deducts a configurable percentage from players' economy balance at configurable intervals
- Only taxes players who are online
- Minimum balance threshold before players are taxed
- Option to deposit taxes to a server account
- Players with the `taxplugin.exempt` permission will not be taxed
- Persistent tax statistics tracking
- Admin commands for managing the plugin

## Configuration
The plugin will generate a `config.yml` file with the following default settings:

```yaml
# Tax rate as a percentage (4.0 = 4%)
tax-rate: 4.0

# Tax collection interval in hours
tax-interval: 2

# Minimum balance required before a player is taxed
minimum-taxable-balance: 100.0

# Should players be notified when taxed?
notify-players: true

# Should collected taxes go to a server bank account?
use-server-account: false
server-account: "server_bank"

# Debug mode (more verbose logging)
debug: false
```

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