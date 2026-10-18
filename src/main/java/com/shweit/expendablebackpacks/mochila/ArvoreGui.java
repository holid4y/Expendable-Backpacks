package com.shweit.expendablebackpacks.mochila;

import com.shweit.expendablebackpacks.items.BackpackItem;
import com.shweit.expendablebackpacks.items.BackpackTier;
import com.shweit.expendablebackpacks.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** The upgrade tree screen: capacity row, utility branch, plugin branch. */
final class ArvoreGui implements Listener {

    private static final int[] SLOTS_CAPACIDADE = {10, 11, 12, 13, 14, 15};
    private static final int SLOT_COLETOR = 29;
    private static final int SLOT_SETA = 30;
    private static final int SLOT_REABASTECER = 31;
    private static final int[] SLOTS_PLUGINS = {37, 39, 41, 43};
    private static final Melhoria[] PLUGINS = {Melhoria.BOLSO_DE_PESCA, Melhoria.SAQUEADOR,
        Melhoria.ADEGA, Melhoria.TOCA_DISCOS};
    private static final int SLOT_ABRIR = 48;
    private static final int SLOT_FECHAR = 50;

    /** Marks the inventory as this screen and remembers whose tree it shows. */
    static final class Tela implements InventoryHolder {
        private final UUID dono;
        private Inventory inventario;

        Tela(UUID dono) {
            this.dono = dono;
        }

        @Override
        public Inventory getInventory() {
            return inventario;
        }
    }

    private final MochilaServico servico;

    ArvoreGui(MochilaServico servico) {
        this.servico = servico;
    }

    static void abrir(MochilaServico servico, Player p) {
        Tela tela = new Tela(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(tela, 54, TextUtil.fromLegacy("Árvore da Mochila"));
        tela.inventario = inv;
        desenhar(servico, p, inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.4f);
    }

    private static ItemStack icone(Material m, String nome, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(TextUtil.fromLegacy(nome).decoration(
            net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(l -> TextUtil.fromLegacy(l).decoration(
            net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private static void desenhar(MochilaServico servico, Player p, Inventory inv) {
        MochilaDados d = servico.dados(p.getUniqueId());
        MochilaConfig cfg = servico.config();
        ItemStack fundo = icone(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, fundo);
        }
        inv.setItem(4, icone(Material.BOOK, "§6§lÁrvore da Mochila", List.of(
            "§7Seu maior nível de origem: §e" + d.nivelMaximo(),
            "§7A capacidade cresce com o nível de qualquer origem.",
            "§7As outras melhorias vêm de outros sistemas do servidor.",
            "§7Clique numa melhoria liberada para ligar ou desligar.")));
        for (int i = 0; i < MochilaConfig.NIVEIS.size(); i++) {
            BackpackTier t = MochilaConfig.NIVEIS.get(i);
            int nivel = cfg.capacidade().get(t);
            boolean tem = d.capacidade().getSlots() >= t.getSlots();
            ItemStack cabeca = tem ? BackpackItem.createBackpack(t, UUID.nameUUIDFromBytes(t.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = cabeca.getItemMeta();
            meta.displayName(TextUtil.fromLegacy((tem ? "§a" : "§7") + t.getRawDisplayName()).decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                TextUtil.fromLegacy("§8" + t.getSlots() + " slots"),
                TextUtil.fromLegacy((tem ? "§a✔ " : "§c✘ ") + "§7Nível de origem " + nivel)));
            cabeca.setItemMeta(meta);
            inv.setItem(SLOTS_CAPACIDADE[i], cabeca);
        }
        inv.setItem(SLOT_COLETOR, no(servico, d, Melhoria.COLETOR));
        inv.setItem(SLOT_SETA, icone(Material.ARROW, "§7→", List.of("§7Reabastecer vem depois do Coletor.")));
        inv.setItem(SLOT_REABASTECER, no(servico, d, Melhoria.REABASTECER));
        for (int i = 0; i < PLUGINS.length; i++) {
            inv.setItem(SLOTS_PLUGINS[i], no(servico, d, PLUGINS[i]));
        }
        inv.setItem(SLOT_ABRIR, icone(Material.CHEST, "§eAbrir a mochila", List.of("§7Ou clique direito com ela na mão.")));
        inv.setItem(SLOT_FECHAR, icone(Material.BARRIER, "§cFechar", List.of()));
    }

    private static ItemStack no(MochilaServico servico, MochilaDados d, Melhoria m) {
        MochilaConfig.Requisito r = servico.config().requisitos().get(m);
        boolean presente = servico.pluginPresente(m);
        boolean liberada = servico.liberada(d, m);
        boolean ligada = liberada && !d.desligadas.contains(m);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + m.descricao());
        if (m.plugin() != null) {
            lore.add("§8Vem do " + m.plugin() + ".");
        }
        lore.add("");
        if (!presente) {
            lore.add("§8Indisponível: o plugin não está no servidor.");
        } else {
            lore.add(marca(d.nivelMaximo() >= r.nivel()) + "Nível de origem " + Math.min(d.nivelMaximo(), r.nivel())
                + "/" + r.nivel());
            if (m.estatistica() != Melhoria.Estatistica.NENHUMA) {
                int v = d.valor(m.estatistica());
                lore.add(marca(v >= r.quantidade()) + m.estatistica().rotulo() + ": "
                    + Math.min(v, r.quantidade()) + "/" + r.quantidade());
            }
            if (m.pai() != null) {
                lore.add(marca(servico.liberada(d, m.pai())) + "Depois de: " + m.pai().nome());
            }
            lore.add("");
            if (!liberada) {
                lore.add("§c🔒 Bloqueada");
            } else if (!m.alternavel()) {
                lore.add("§a✔ Liberada §7(use /mochila tocar)");
            } else {
                lore.add(ligada ? "§a✔ Ligada §8(clique para desligar)" : "§e✔ Desligada §8(clique para ligar)");
            }
        }
        String cor = !presente ? "§8" : !liberada ? "§7" : ligada ? "§a" : "§e";
        ItemStack it = icone(liberada ? m.icone() : Material.GRAY_DYE, cor + m.nome(), lore);
        if (ligada) {
            it.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return it;
    }

    private static String marca(boolean ok) {
        return ok ? "§a✔ §7" : "§c✘ §7";
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void clicar(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Tela tela)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(tela.dono)
            || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        int slot = e.getSlot();
        if (slot == SLOT_FECHAR) {
            p.closeInventory();
            return;
        }
        if (slot == SLOT_ABRIR) {
            servico.abrir(p);
            return;
        }
        Melhoria m = slot == SLOT_COLETOR ? Melhoria.COLETOR : slot == SLOT_REABASTECER ? Melhoria.REABASTECER : null;
        for (int i = 0; i < SLOTS_PLUGINS.length && m == null; i++) {
            if (SLOTS_PLUGINS[i] == slot) {
                m = PLUGINS[i];
            }
        }
        MochilaDados d = servico.dados(p.getUniqueId());
        if (m == null || !m.alternavel() || !servico.liberada(d, m)) {
            return;
        }
        if (!d.desligadas.remove(m)) {
            d.desligadas.add(m);
        }
        servico.marcar();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, d.desligadas.contains(m) ? 0.8f : 1.4f);
        desenhar(servico, p, e.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void arrastar(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Tela) {
            e.setCancelled(true);
        }
    }
}
