package com.shweit.expendablebackpacks.mochila;

import com.shweit.expendablebackpacks.items.BackpackTier;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.joml.Vector3f;

/** The {@code mochila} section of config.yml, read once per (re)load. */
public record MochilaConfig(
        boolean ativa,
        int slot,
        boolean bloquearShulkers,
        Map<BackpackTier, Integer> capacidade,
        Map<Melhoria, Requisito> requisitos,
        double velocidadeAdega,
        boolean visualAtivo,
        boolean visualParaDono,
        Vector3f escala,
        Vector3f deslocamento,
        float giro) {

    /** Minimum origin level plus the plugin statistic a node needs. */
    public record Requisito(int nivel, int quantidade) {
    }

    /** Capacity tiers in order, from the starting backpack to the largest. */
    public static final List<BackpackTier> NIVEIS = List.of(BackpackTier.LEATHER,
        BackpackTier.COPPER, BackpackTier.IRON, BackpackTier.GOLD, BackpackTier.DIAMOND,
        BackpackTier.NETHERITE);

    /**
     * Reads the settings, falling back to the shipped defaults for anything missing.
     *
     * @param c the plugin config
     * @return the settings
     */
    public static MochilaConfig ler(FileConfiguration c) {
        Map<BackpackTier, Integer> capacidade = new EnumMap<>(BackpackTier.class);
        int[] padrao = {1, 3, 5, 7, 9, 10};
        for (int i = 0; i < NIVEIS.size(); i++) {
            BackpackTier t = NIVEIS.get(i);
            capacidade.put(t, Math.clamp(c.getInt("mochila.capacity." + t.name(), padrao[i]), 1, 10));
        }
        Map<Melhoria, Requisito> requisitos = new EnumMap<>(Melhoria.class);
        requisitos.put(Melhoria.COLETOR, req(c, "coletor", 2, null, 0));
        requisitos.put(Melhoria.REABASTECER, req(c, "reabastecer", 4, null, 0));
        requisitos.put(Melhoria.BOLSO_DE_PESCA, req(c, "bolso-de-pesca", 3, "fish", 15));
        requisitos.put(Melhoria.SAQUEADOR, req(c, "saqueador", 3, "chests", 10));
        requisitos.put(Melhoria.ADEGA, req(c, "adega", 5, "drinks", 5));
        requisitos.put(Melhoria.TOCA_DISCOS, req(c, "toca-discos", 2, "discs", 1));
        return new MochilaConfig(
            c.getBoolean("mochila.enabled", true),
            Math.clamp(c.getInt("mochila.slot", 8), 0, 8),
            c.getBoolean("mochila.block-shulker-boxes", true),
            capacidade,
            requisitos,
            Math.clamp(c.getDouble("mochila.upgrades.adega.speed", 0.5), 0.0, 2.0),
            c.getBoolean("mochila.visual.enabled", true),
            c.getBoolean("mochila.visual.show-to-owner", true),
            vetor(c, "mochila.visual.scale", new Vector3f(0.9f, 1.0f, 0.55f)),
            vetor(c, "mochila.visual.offset", new Vector3f(0.0f, -0.4f, -0.27f)),
            (float) c.getDouble("mochila.visual.yaw", 0.0));
    }

    private static Requisito req(FileConfiguration c, String chave, int nivel, String estat, int qtd) {
        ConfigurationSection s = c.getConfigurationSection("mochila.upgrades." + chave);
        if (s == null) {
            return new Requisito(nivel, qtd);
        }
        return new Requisito(Math.clamp(s.getInt("level", nivel), 1, 10),
            estat == null ? 0 : Math.max(0, s.getInt(estat, qtd)));
    }

    private static Vector3f vetor(FileConfiguration c, String caminho, Vector3f padrao) {
        List<Double> v = c.getDoubleList(caminho);
        if (v.size() != 3) {
            return padrao;
        }
        return new Vector3f(v.get(0).floatValue(), v.get(1).floatValue(), v.get(2).floatValue());
    }

    /**
     * Largest capacity tier the given origin level unlocks.
     *
     * @param nivel highest origin level
     * @return the tier
     */
    public BackpackTier nivelPara(int nivel) {
        BackpackTier melhor = BackpackTier.LEATHER;
        for (BackpackTier t : NIVEIS) {
            if (nivel >= capacidade.get(t)) {
                melhor = t;
            }
        }
        return melhor;
    }

    /**
     * The next capacity tier after the given one, or null at the top.
     *
     * @param atual the current tier
     * @return the next tier or null
     */
    public BackpackTier proximo(BackpackTier atual) {
        int i = NIVEIS.indexOf(atual);
        return i < 0 || i + 1 >= NIVEIS.size() ? null : NIVEIS.get(i + 1);
    }
}
