package com.shweit.expendablebackpacks.items;

import org.bukkit.Material;

/**
 * Enum defining all backpack tiers with their properties.
 * NOTE: Replace PLACEHOLDER texture values with actual Base64 encoded textures.
 */
public enum BackpackTier {
    DIRT(0, "Mochila de Terra", 9,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvNWFlNmE5MTFmYTJiOWY0Y2IyYTM2NWVjYThkMGI2MGNjZDFiMGUzYmE"
        + "0ODA5ZDkzNDUzNzUxODMwOWMxYjRmYyJ9fX0=",
        Material.DIRT, "§7"),
    LEATHER(1, "Mochila de Couro", 9,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvNDBiMWI1MzY3NDkxODM5MWEwN2E5ZDAwNTgyYzA1OGY5MjgwYmM1Mj"
        + "ZhNzE2Yzc5NmVlNWVhYjRiZTEwYTc2MCJ9fX0=",
        Material.LEATHER, "§7"),
    COPPER(2, "Mochila de Cobre", 18,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvMWU1ODNjYjc3MTU4MWQzYjI3YjIzZjYxN2M3YjhhNDNkY2Q3MjIwND"
        + "Q3ZmY5NWZmMTk2MDQxNGQyMzUwYmRiOSJ9fX0=",
        Material.COPPER_INGOT, "§6"),
    IRON(3, "Mochila de Ferro", 27,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvZGRhZjhlZGMzMmFmYjQ2MWFlZTA3MTMwNTgwMjMxMDFmOTI0ZTJhN2"
        + "VmYTg4M2RhZTcyZDVkNTdkNGMwNTNkNyJ9fX0=",
        Material.IRON_INGOT, "§f"),
    GOLD(4, "Mochila de Ouro", 36,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvY2Y4NzUyNWFkODRlZmQxNjgwNmEyNmNhMDE5ODRiMjgwZTViYTY0MD"
        + "M1MDViNmY2Yzk4MDNjMjQ2NDJhYmZjNyJ9fX0=",
        Material.GOLD_INGOT, "§e"),
    DIAMOND(5, "Mochila de Diamante", 45,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvMTBkMWIwNzMyYmY3YTcwZGU0ZGMwMTU1OWNjNWM5ODExMDY4ZWY3Yj"
        + "YwOTUwMTAzODI3MDlmOTQwOTM5MjdmNiJ9fX0=",
        Material.DIAMOND, "§b"),
    NETHERITE(6, "Mochila de Netherite", 54,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvODM1ZDdjYzA5ZmZmYmNhM2UxYzAwZDQyMWFmYWE0MzJjZjcxZmNiMD"
        + "k1NTVmNTQ1MjNlNTIyMGQxYWYwZjk3ZCJ9fX0=",
        Material.NETHERITE_INGOT, "§8"),
    ENDERPACK(7, "Mochila do Fim", 27,
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQ"
        + "ubmV0L3RleHR1cmUvOGQ1OWY0ZGRiYjJhMDk0YzFkYTc2NTRjMWViZjlkODUxNWEyYjc1YT"
        + "IyMGZkNzNhNjUxMzUwNzJlMzhkNGY2MCJ9fX0=",
        Material.ENDER_PEARL, "§5");

    private final int level;
    private final String displayName;
    private final int slots;
    private final String textureValue;
    private final Material upgradeMaterial;
    private final String colorCode;

    /**
     * Constructor for BackpackTier.
     *
     * @param level the tier level.
     * @param displayName the display name.
     * @param slots the number of slots.
     * @param textureValue the texture value.
     * @param upgradeMaterial the upgrade material.
     * @param colorCode the color code.
     */
    BackpackTier(int level, String displayName, int slots,
        String textureValue, Material upgradeMaterial, String colorCode) {
        this.level = level;
        this.displayName = displayName;
        this.slots = slots;
        this.textureValue = textureValue;
        this.upgradeMaterial = upgradeMaterial;
        this.colorCode = colorCode;
    }

    /**
     * Get the tier level.
     *
     * @return the level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Get the display name with color code.
     *
     * @return the display name.
     */
    public String getDisplayName() {
        return colorCode + displayName;
    }

    /**
     * Get the raw display name without color code.
     *
     * @return the raw display name.
     */
    public String getRawDisplayName() {
        return displayName;
    }

    /**
     * Get the number of slots.
     *
     * @return the number of slots.
     */
    public int getSlots() {
        return slots;
    }

    /**
     * Get the number of rows.
     *
     * @return the number of rows.
     */
    public int getRows() {
        return slots / 9;
    }

    /**
     * Get the texture value.
     *
     * @return the texture value.
     */
    public String getTextureValue() {
        return textureValue;
    }

    /**
     * Get the upgrade material.
     *
     * @return the upgrade material.
     */
    public Material getUpgradeMaterial() {
        return upgradeMaterial;
    }

    /**
     * Get the color code.
     *
     * @return the color code.
     */
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Get tier by level number.
     *
     * @param level the level number.
     * @return the tier, or null if not found.
     */
    public static BackpackTier fromLevel(int level) {
        for (BackpackTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Get tier by name (case-insensitive).
     *
     * @param name the tier name.
     * @return the tier, or null if not found.
     */
    public static BackpackTier fromName(String name) {
        for (BackpackTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)
                || tier.displayName.equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Check if this tier can be upgraded to the next tier.
     *
     * @return true if upgradeable, false otherwise.
     */
    public boolean hasNextTier() {
        return level < NETHERITE.level;
    }

    /**
     * Get the next tier (for upgrades).
     *
     * @return the next tier, or null if at max level.
     */
    public BackpackTier getNextTier() {
        if (level >= NETHERITE.level) {
            return null;
        }
        return fromLevel(level + 1);
    }

    /**
     * Check if this is an Enderpack.
     *
     * @return true if this is an Enderpack, false otherwise.
     */
    public boolean isEnderpack() {
        return this == ENDERPACK;
    }
}
