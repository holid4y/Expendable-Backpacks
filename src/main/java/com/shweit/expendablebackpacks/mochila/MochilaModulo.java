package com.shweit.expendablebackpacks.mochila;

import com.shweit.expendablebackpacks.storage.BackpackManager;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Wires the personal backpack, its upgrade tree and the plugin integrations. */
public final class MochilaModulo {

    private final MochilaServico servico;
    private VisualCostas visual;

    /**
     * Creates the module; nothing runs until {@link #ligar()}.
     *
     * @param plugin the plugin
     * @param mochilas backpack storage
     */
    public MochilaModulo(JavaPlugin plugin, BackpackManager mochilas) {
        this.servico = new MochilaServico(plugin, mochilas);
    }

    /** Registers listeners, the command and the periodic tasks. */
    public void ligar() {
        JavaPlugin plugin = servico.plugin();
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new MochilaListener(servico), plugin);
        pm.registerEvents(new EfeitosListener(servico), plugin);
        pm.registerEvents(new ArvoreGui(servico), plugin);
        Integracoes integracoes = new Integracoes(servico);
        integracoes.registrar();
        if (pm.isPluginEnabled("packetevents")) {
            visual = new VisualCostas(servico);
            pm.registerEvents(visual, plugin);
            visual.ligar();
        } else {
            plugin.getLogger().info("packetevents not found: backpacks work, but are not shown on players' backs.");
        }
        MochilaComando comando = new MochilaComando(servico, () -> {
            if (visual != null) {
                visual.refazer();
            }
        });
        Objects.requireNonNull(plugin.getCommand("mochila")).setExecutor(comando);
        Objects.requireNonNull(plugin.getCommand("mochila")).setTabCompleter(comando);
        // Every two seconds: put back a backpack that was cleared, moved or duplicated by creative.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                servico.garantir(p);
            }
        }, 40L, 40L);
        // Every five seconds: pick up origin levels and disc counts published by the other plugins.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                servico.atualizar(p);
            }
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, servico::salvar, 600L, 600L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            servico.atualizar(p);
            servico.garantir(p);
        }
    }

    /** Removes displays and writes everything. */
    public void desligar() {
        if (visual != null) {
            visual.desligar();
        }
        servico.salvar();
    }
}
