package com.shweit.expendablebackpacks;

import com.shweit.expendablebackpacks.commands.BackpackCommand;
import com.shweit.expendablebackpacks.items.BackpackItem;
import com.shweit.expendablebackpacks.listeners.BackpackBlockListener;
import com.shweit.expendablebackpacks.listeners.BackpackCraftingListener;
import com.shweit.expendablebackpacks.listeners.BackpackInteractionListener;
import com.shweit.expendablebackpacks.listeners.BackpackProtectionListener;
import com.shweit.expendablebackpacks.listeners.BackpackSmithingListener;
import com.shweit.expendablebackpacks.listeners.PlayerJoinListener;
import com.shweit.expendablebackpacks.mochila.MochilaModulo;
import com.shweit.expendablebackpacks.recipes.BackpackRecipes;
import com.shweit.expendablebackpacks.storage.BackpackManager;
import com.shweit.expendablebackpacks.util.BackpackBlockUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Expendable Backpacks.
 */
public class ExpendableBackpacks extends JavaPlugin {

    private BackpackManager backpackManager;
    private MochilaModulo mochila;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();

        // Initialize BackpackItem factory
        BackpackItem.initialize(this);

        // Initialize BackpackBlockUtil for block operations
        BackpackBlockUtil.initialize(this);

        // Initialize BackpackManager (storage)
        backpackManager = new BackpackManager(this);
        backpackManager.loadAllBackpacks();

        // FeatherFlux runs one personal backpack per player: crafting is off unless re-enabled.
        boolean recipes = getConfig().getBoolean("recipes-enabled", false);
        if (recipes) {
            BackpackRecipes backpackRecipes = new BackpackRecipes(this);
            backpackRecipes.registerAll();
        }

        // Register listeners
        if (recipes) {
            getServer().getPluginManager().registerEvents(
                new BackpackCraftingListener(backpackManager, this), this);
            getServer().getPluginManager().registerEvents(
                new BackpackSmithingListener(backpackManager), this);
        }
        getServer().getPluginManager().registerEvents(
            new BackpackInteractionListener(backpackManager), this);
        getServer().getPluginManager().registerEvents(
            new BackpackProtectionListener(this, backpackManager), this);
        getServer().getPluginManager().registerEvents(
            new BackpackBlockListener(getConfig().getBoolean("placement-enabled", false)), this);
        getServer().getPluginManager().registerEvents(
            new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(
            new com.shweit.expendablebackpacks.gui.BackpackGuideGUI(), this);

        // Register commands
        BackpackCommand backpackCommand = new BackpackCommand(backpackManager);
        getCommand("backpack").setExecutor(backpackCommand);
        getCommand("backpack").setTabCompleter(backpackCommand);

        // No bStats in this fork: it would report a modified build under the upstream author's id.
        mochila = new MochilaModulo(this, backpackManager);
        mochila.ligar();

        getLogger().info("Expendable Backpacks (FeatherFlux) has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving all backpack inventories...");

        if (mochila != null) {
            mochila.desligar();
        }
        // Save all inventories
        if (backpackManager != null) {
            backpackManager.saveAllInventories();
        }

        getLogger().info("Expendable Backpacks has been disabled. All data saved successfully.");
    }

    /**
     * Get the BackpackManager instance.
     *
     * @return the backpack manager instance
     */
    @SuppressWarnings("EI_EXPOSE_REP")
    public BackpackManager getBackpackManager() {
        return backpackManager;
    }
}
