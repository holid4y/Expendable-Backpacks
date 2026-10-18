package com.shweit.expendablebackpacks.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Manages backpack inventory storage and persistence.
 */
public class BackpackManager {

    private final Plugin plugin;
    private final File backpacksFile;
    private YamlConfiguration backpacksConfig;
    private final Map<UUID, Inventory> loadedInventories;
    private boolean dirty;

    /**
     * Create a new BackpackManager.
     *
     * @param plugin the plugin instance
     */
    @SuppressWarnings("EI_EXPOSE_REP2")
    public BackpackManager(Plugin plugin) {
        this.plugin = plugin;
        this.loadedInventories = new HashMap<>();

        // Create data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            boolean created = plugin.getDataFolder().mkdirs();
            if (!created) {
                plugin.getLogger().warning("Could not create data folder!");
            }
        }

        this.backpacksFile = new File(plugin.getDataFolder(), "backpacks.yml");
        loadBackpacksFile();
    }

    /**
     * Load or create the backpacks.yml file.
     */
    private void loadBackpacksFile() {
        if (!backpacksFile.exists()) {
            try {
                backpacksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create backpacks.yml!", e);
            }
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(backpacksFile);
        } catch (IOException | InvalidConfigurationException e) {
            // Never overwrite an unreadable file with an empty one: set it aside for recovery.
            File broken = new File(backpacksFile.getParentFile(),
                backpacksFile.getName() + ".corrupt-" + System.currentTimeMillis());
            boolean moved = backpacksFile.renameTo(broken);
            plugin.getLogger().log(Level.SEVERE, "backpacks.yml could not be read; "
                + (moved ? "kept as " + broken.getName() : "left in place") + ".", e);
            loaded = new YamlConfiguration();
        }
        backpacksConfig = loaded;
    }

    /**
     * Save the backpacks.yml file atomically: written to a temporary file and moved over the old
     * one, so a power cut mid-write can never leave every backpack truncated.
     */
    private void saveBackpacksFile() {
        Path target = backpacksFile.toPath();
        Path tmp = target.resolveSibling(backpacksFile.getName() + ".tmp");
        try {
            Files.writeString(tmp, backpacksConfig.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save backpacks.yml!", e);
        }
    }

    /** Copies the contents of every backpack someone has open, for the periodic autosave. */
    public void snapshotOpen() {
        for (Map.Entry<UUID, Inventory> entry : loadedInventories.entrySet()) {
            if (!entry.getValue().getViewers().isEmpty()) {
                saveInventory(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Writes backpacks.yml if anything changed since the last write. */
    public void flush() {
        if (dirty) {
            saveBackpacksFile();
        }
    }

    /**
     * Get or create an inventory for a backpack UUID.
     *
     * @param backpackUuid the backpack UUID
     * @param title the inventory title
     * @param slots the number of slots
     * @return the inventory
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public Inventory getInventory(UUID backpackUuid, Component title, int slots) {
        UUID backpackUUID = backpackUuid;
        // Check if already loaded in memory
        if (loadedInventories.containsKey(backpackUUID)) {
            Inventory cached = loadedInventories.get(backpackUUID);

            // Check if size matches (in case of upgrade)
            if (cached.getSize() == slots) {
                return cached;
            } else {
                // Size changed (upgraded) - need to resize inventory
                plugin.getLogger().info("Resizing backpack " + backpackUUID + " from "
                    + cached.getSize() + " to " + slots + " slots");

                // Create new inventory with new size
                Inventory newInventory = Bukkit.createInventory(null, slots, title);

                // Copy old contents (will fit since upgrades only increase size)
                ItemStack[] oldContents = cached.getContents();
                if (oldContents != null) {
                    for (int i = 0; i < oldContents.length && i < slots; i++) {
                        if (oldContents[i] != null) {
                            newInventory.setItem(i, oldContents[i]);
                        }
                    }
                }

                // Update cache and save
                loadedInventories.put(backpackUUID, newInventory);
                saveInventory(backpackUUID, newInventory);
                return newInventory;
            }
        }

        // Create new inventory
        Inventory inventory = Bukkit.createInventory(null, slots, title);

        // Load saved contents if they exist
        String path = backpackUUID.toString() + ".contents";
        if (backpacksConfig.contains(path)) {
            Object contentsObj = backpacksConfig.get(path);
            if (contentsObj != null) {
                @SuppressWarnings("unchecked")
                ItemStack[] contents = ((java.util.List<ItemStack>) contentsObj)
                    .toArray(new ItemStack[0]);

                // Copy contents (handle size mismatch if saved size differs)
                for (int i = 0; i < contents.length && i < slots; i++) {
                    if (contents[i] != null) {
                        inventory.setItem(i, contents[i]);
                    }
                }
            }
        }

        // Cache in memory
        loadedInventories.put(backpackUUID, inventory);

        // Save immediately to YAML so it appears in tab completions
        saveInventory(backpackUUID, inventory);

        return inventory;
    }

    /**
     * Save a backpack inventory.
     *
     * @param backpackUuid the backpack UUID
     * @param inventory the inventory to save
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void saveInventory(UUID backpackUuid, Inventory inventory) {
        UUID backpackUUID = backpackUuid;
        String path = backpackUUID.toString() + ".contents";
        ItemStack[] contents = inventory.getContents();
        if (contents != null) {
            backpacksConfig.set(path, java.util.Arrays.asList(contents));
        }
        // Update cache; the file is written by flush() (autosave, close, quit, disable).
        loadedInventories.put(backpackUUID, inventory);
        dirty = true;
    }

    /**
     * Records the contents and writes the file right away (closing a backpack, quitting).
     *
     * @param backpackUuid the backpack UUID
     * @param inventory the inventory to save
     */
    public void saveInventoryNow(UUID backpackUuid, Inventory inventory) {
        saveInventory(backpackUuid, inventory);
        flush();
    }

    /**
     * Clear a backpack's contents.
     *
     * @param backpackUuid the backpack UUID
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public void clearInventory(UUID backpackUuid) {
        UUID backpackUUID = backpackUuid;
        // Remove from file
        backpacksConfig.set(backpackUUID.toString(), null);
        saveBackpacksFile();

        // Remove from cache
        loadedInventories.remove(backpackUUID);
    }

    /**
     * Save all loaded inventories (called on plugin disable).
     */
    public void saveAllInventories() {
        for (Map.Entry<UUID, Inventory> entry : loadedInventories.entrySet()) {
            String path = entry.getKey().toString() + ".contents";
            ItemStack[] contents = entry.getValue().getContents();
            if (contents != null) {
                backpacksConfig.set(path, java.util.Arrays.asList(contents));
            }
        }
        saveBackpacksFile();
        plugin.getLogger().info("Saved " + loadedInventories.size() + " backpack inventories");
    }

    /**
     * Load all backpacks from file (called on plugin enable).
     */
    public void loadAllBackpacks() {
        loadBackpacksFile();
        int count = backpacksConfig.getKeys(false).size();
        plugin.getLogger().info("Loaded data for " + count + " backpacks");
    }

    /**
     * Check if a backpack UUID exists in storage.
     *
     * @param backpackUuid the backpack UUID
     * @return true if it exists
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public boolean backpackExists(UUID backpackUuid) {
        UUID backpackUUID = backpackUuid;
        return backpacksConfig.contains(backpackUUID.toString() + ".contents");
    }

    /**
     * Get a loaded inventory (without loading from disk).
     *
     * @param backpackUuid the backpack UUID
     * @return the loaded inventory, or null
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public Inventory getLoadedInventory(UUID backpackUuid) {
        UUID backpackUUID = backpackUuid;
        return loadedInventories.get(backpackUUID);
    }

    /**
     * Get all saved backpack UUIDs from file.
     * All backpacks are immediately saved when created, so file is always up-to-date.
     *
     * @return list of all backpack UUIDs
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public List<UUID> getAllBackpackUUIDs() {

        List<UUID> uuids = new ArrayList<>();

        for (String key : backpacksConfig.getKeys(false)) {
            try {
                // Try to parse the key directly as UUID
                UUID uuid = UUID.fromString(key);
                uuids.add(uuid);
            } catch (IllegalArgumentException e) {
                // Skip invalid UUID keys
            }
        }

        return uuids;
    }

    /**
     * Find the UUID for a given inventory instance.
     *
     * @param inventory the inventory to find
     * @return the UUID or null if not found
     */
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public UUID findInventoryUUID(Inventory inventory) {
        for (Map.Entry<UUID, Inventory> entry : loadedInventories.entrySet()) {
            if (entry.getValue().equals(inventory)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
