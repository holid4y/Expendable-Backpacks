package com.shweit.expendablebackpacks.mochila;

import org.bukkit.Material;

/**
 * Nodes of the backpack upgrade tree. Capacity is not here: it follows the origin level directly.
 * Each node needs a minimum origin level, optionally a parent node and a statistic tracked from
 * another plugin. Nodes whose plugin is missing are shown as unavailable instead of hidden.
 */
public enum Melhoria {
    COLETOR("coletor", "Coletor", Material.HOPPER, null, null, Estatistica.NENHUMA,
        "Com o inventário cheio, o que você pega do chão vai para a mochila."),
    REABASTECER("reabastecer", "Reabastecer", Material.DROPPER, null, COLETOR, Estatistica.NENHUMA,
        "Quando um stack da hotbar acaba (blocos, comida, flechas, ferramentas), a mochila repõe com o mesmo item."),
    BOLSO_DE_PESCA("bolso-de-pesca", "Bolso de Pesca", Material.TROPICAL_FISH, "EvenMoreFish", null,
        Estatistica.PEIXES, "Os peixes do EvenMoreFish que você pesca vão direto para a mochila."),
    SAQUEADOR("saqueador", "Saqueador", Material.CHEST_MINECART, "Lootin", null, Estatistica.BAUS,
        "Abra um baú de loot segurando a mochila e o loot vai direto para ela."),
    ADEGA("adega", "Adega", Material.BARREL, "BreweryX", null, Estatistica.BEBIDAS,
        "Bebidas da cervejaria guardadas na mochila envelhecem como num barril de carvalho, mais devagar."),
    TOCA_DISCOS("toca-discos", "Toca-discos", Material.JUKEBOX, "FeatherDiscos", null, Estatistica.DISCOS,
        "Com /mochila tocar, um disco da comunidade guardado na mochila toca e o som vai junto com você.");

    /** What a node counts besides the origin level. */
    public enum Estatistica {
        NENHUMA(""), PEIXES("Peixes do EvenMoreFish pescados"), BAUS("Baús de loot diferentes abertos"),
        BEBIDAS("Bebidas da cervejaria tomadas"), DISCOS("Discos criados com /disco");

        private final String rotulo;

        Estatistica(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    private final String chave;
    private final String nome;
    private final Material icone;
    private final String plugin;
    private final Melhoria pai;
    private final Estatistica estatistica;
    private final String descricao;

    Melhoria(String chave, String nome, Material icone, String plugin, Melhoria pai,
        Estatistica estatistica, String descricao) {
        this.chave = chave;
        this.nome = nome;
        this.icone = icone;
        this.plugin = plugin;
        this.pai = pai;
        this.estatistica = estatistica;
        this.descricao = descricao;
    }

    public String chave() {
        return chave;
    }

    public String nome() {
        return nome;
    }

    public Material icone() {
        return icone;
    }

    /** Plugin this node depends on, or null for vanilla behaviour. */
    public String plugin() {
        return plugin;
    }

    public Melhoria pai() {
        return pai;
    }

    public Estatistica estatistica() {
        return estatistica;
    }

    public String descricao() {
        return descricao;
    }

    /** Toca-discos acts through a command, so there is nothing to switch off. */
    public boolean alternavel() {
        return this != TOCA_DISCOS;
    }

    /**
     * Looks a node up by its config key.
     *
     * @param chave the config key
     * @return the node, or null
     */
    public static Melhoria porChave(String chave) {
        for (Melhoria m : values()) {
            if (m.chave.equalsIgnoreCase(chave)) {
                return m;
            }
        }
        return null;
    }
}
