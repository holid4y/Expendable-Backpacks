package com.shweit.expendablebackpacks.mochila;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Lootin (Saqueador) and BreweryX (Adega) through their public API classes, looked up at runtime so
 * this plugin still loads and works when either is missing.
 */
final class Integracoes implements Listener {

    /** Adega ages the backpack's drinks every five minutes. */
    static final long INTERVALO_ADEGA_TICKS = 5L * 60L * 20L;

    private final MochilaServico servico;
    private Class<?> lootinTela;
    private Field lootinAlvo;
    private Method breweryEhBebida;
    private Method breweryObter;
    private Method breweryEnvelhecer;
    private Object madeiraCarvalho;
    private float minutosPorAno = 20f;

    Integracoes(MochilaServico servico) {
        this.servico = servico;
    }

    void registrar() {
        Plugin lootin = Bukkit.getPluginManager().getPlugin("Lootin");
        if (lootin != null && lootin.isEnabled()) {
            try {
                // Lootin's own open event only fires on some loot generations, so watch its
                // per-player screen instead: every open of a loot container goes through it.
                lootinTela = Class.forName("com.github.sachin.lootin.utils.LootinGui", true,
                    lootin.getClass().getClassLoader());
                lootinAlvo = lootinTela.getDeclaredField("lootable");
                lootinAlvo.setAccessible(true);
                Bukkit.getPluginManager().registerEvents(new Listener() {
                    // MONITOR and ignoreCancelled: nothing can cancel the opening after the loot
                    // has moved, which would leave it both in the chest and in the backpack.
                    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                    public void abriu(InventoryOpenEvent e) {
                        lootin(e);
                    }
                }, servico.plugin());
                servico.plugin().getLogger().info("Lootin hooked: Saqueador and loot chest counting are on.");
            } catch (ReflectiveOperationException | ClassCastException e) {
                servico.plugin().getLogger().log(Level.WARNING, "Lootin found but its API changed; Saqueador is off", e);
            }
        }
        Plugin emf = Bukkit.getPluginManager().getPlugin("EvenMoreFish");
        if (emf != null && emf.isEnabled()) {
            try {
                Class<? extends Event> pescou = Class.forName("com.oheers.fish.api.events.EMFFishCaughtEvent",
                    true, emf.getClass().getClassLoader()).asSubclass(Event.class);
                Method jogador = pescou.getMethod("getPlayer");
                Bukkit.getPluginManager().registerEvent(pescou, this, EventPriority.MONITOR, (l, e) -> {
                    try {
                        if (jogador.invoke(e) instanceof Player p) {
                            servico.peixePescado(p);
                        }
                    } catch (ReflectiveOperationException ex) {
                        servico.plugin().getLogger().log(Level.WARNING, "Could not count a fish", ex);
                    }
                }, servico.plugin(), true);
                servico.plugin().getLogger().info("EvenMoreFish hooked: Bolso de Pesca and fish counting are on.");
            } catch (ReflectiveOperationException | ClassCastException e) {
                servico.plugin().getLogger().log(Level.WARNING, "EvenMoreFish found but its API changed; Bolso de Pesca is off", e);
            }
        }
        Plugin brewery = Bukkit.getPluginManager().getPlugin("BreweryX");
        if (brewery != null && brewery.isEnabled()) {
            try {
                ClassLoader cl = brewery.getClass().getClassLoader();
                Class<?> brew = Class.forName("com.dre.brewery.Brew", true, cl);
                Class<?> madeira = Class.forName("com.dre.brewery.BarrelWoodType", true, cl);
                breweryEhBebida = brew.getMethod("isBrew", ItemStack.class);
                breweryObter = brew.getMethod("get", ItemStack.class);
                breweryEnvelhecer = brew.getMethod("age", ItemStack.class, float.class, madeira);
                madeiraCarvalho = madeira.getField("OAK").get(null);
                Class<? extends Event> beber = Class.forName(
                    "com.dre.brewery.api.events.brew.BrewDrinkEvent", true, cl).asSubclass(Event.class);
                Method jogador = beber.getMethod("getPlayer");
                Bukkit.getPluginManager().registerEvent(beber, this, EventPriority.MONITOR,
                    (l, e) -> bebeu(jogador, e), servico.plugin(), true);
                File cfg = new File(brewery.getDataFolder(), "config.yml");
                if (cfg.isFile()) {
                    double anos = YamlConfiguration.loadConfiguration(cfg).getDouble("agingYearDuration", 20.0);
                    minutosPorAno = (float) Math.clamp(anos, 1.0, 1440.0);
                }
                Bukkit.getScheduler().runTaskTimer(servico.plugin(), this::adega,
                    INTERVALO_ADEGA_TICKS, INTERVALO_ADEGA_TICKS);
                servico.plugin().getLogger().info("BreweryX hooked: Adega and drink counting are on (a year is "
                    + minutosPorAno + " min).");
            } catch (ReflectiveOperationException | ClassCastException e) {
                servico.plugin().getLogger().log(Level.WARNING, "BreweryX found but its API changed; Adega is off", e);
            }
        }
    }

    private static String chave(Object alvo) {
        if (alvo instanceof BlockState b) {
            return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        }
        if (alvo instanceof Entity en) {
            return en.getUniqueId().toString();
        }
        return null;
    }

    /**
     * Counts distinct loot containers and, when the chest is opened with the backpack in hand and
     * Saqueador on, empties the loot into the backpack. Not on crouch: Lootin hands crouched clicks
     * to vanilla so blocks can be placed against loot chests.
     */
    private void lootin(InventoryOpenEvent e) {
        Object tela = e.getInventory().getHolder(false);
        if (!lootinTela.isInstance(tela) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        try {
            String chave = chave(lootinAlvo.get(tela));
            MochilaDados d = servico.dados(p.getUniqueId());
            if (chave != null && d.registrarBau(chave)) {
                servico.marcar();
                servico.atualizar(p);
            }
        } catch (IllegalAccessException ex) {
            servico.plugin().getLogger().log(Level.WARNING, "Could not read a Lootin container", ex);
        }
        if (!servico.ehPessoal(p.getInventory().getItemInMainHand()) || !servico.ativa(p, Melhoria.SAQUEADOR)) {
            return;
        }
        // Lootin saves whatever is left in its screen when it closes, so taking items out here is
        // what the chest remembers for this player.
        Inventory tela2 = e.getInventory();
        int guardados = 0;
        for (int i = 0; i < tela2.getSize(); i++) {
            ItemStack it = tela2.getItem(i);
            if (it == null || it.isEmpty() || servico.bloqueado(it)) {
                continue;
            }
            int resto = servico.guardar(p.getUniqueId(), it);
            guardados += it.getAmount() - resto;
            if (resto == 0) {
                tela2.setItem(i, null);
            } else if (resto < it.getAmount()) {
                ItemStack sobra = it.clone();
                sobra.setAmount(resto);
                tela2.setItem(i, sobra);
            }
        }
        if (guardados > 0) {
            p.sendMessage(MochilaServico.PREFIXO + "Saqueador: " + guardados + " itens foram para a mochila.");
            p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 0.9f);
        }
    }

    private void bebeu(Method jogador, Event e) {
        try {
            if (jogador.invoke(e) instanceof Player p) {
                servico.dados(p.getUniqueId()).bebidas++;
                servico.marcar();
                servico.atualizar(p);
            }
        } catch (ReflectiveOperationException ex) {
            servico.plugin().getLogger().log(Level.WARNING, "Could not count a drink", ex);
        }
    }

    /** Ages the drinks in online players' backpacks as an oak barrel would, at the configured speed. */
    private void adega() {
        double velocidade = servico.config().velocidadeAdega();
        if (velocidade <= 0) {
            return;
        }
        float anos = (float) ((INTERVALO_ADEGA_TICKS / 20.0 / 60.0) / minutosPorAno * velocidade);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!servico.ativa(p, Melhoria.ADEGA)) {
                continue;
            }
            Inventory bolsa = servico.armazenamento(p.getUniqueId());
            boolean mudou = false;
            for (int i = 0; i < bolsa.getSize(); i++) {
                ItemStack it = bolsa.getItem(i);
                if (it == null || it.isEmpty()) {
                    continue;
                }
                try {
                    if (!(boolean) breweryEhBebida.invoke(null, it)) {
                        continue;
                    }
                    Object bebida = breweryObter.invoke(null, it);
                    if (bebida != null) {
                        breweryEnvelhecer.invoke(bebida, it, anos, madeiraCarvalho);
                        bolsa.setItem(i, it);
                        mudou = true;
                    }
                } catch (ReflectiveOperationException ex) {
                    servico.plugin().getLogger().log(Level.WARNING, "Adega could not age a drink", ex);
                    return;
                }
            }
            if (mudou) {
                servico.gerenciador().saveInventory(MochilaServico.idDaMochila(p.getUniqueId()), bolsa);
            }
        }
    }
}
