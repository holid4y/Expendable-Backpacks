package com.shweit.expendablebackpacks.mochila;

import com.shweit.expendablebackpacks.items.BackpackItem;
import com.shweit.expendablebackpacks.items.BackpackTier;
import com.shweit.expendablebackpacks.storage.BackpackManager;
import com.shweit.expendablebackpacks.util.TextUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The personal backpack: one per player, locked in a hotbar slot, sized by the highest origin level
 * and extended by the upgrade tree. Item identity is the owner UUID in the item's data; the storage
 * id is derived from the owner, so the same contents follow the player whatever happens to the item.
 */
public final class MochilaServico {

    static final String PREFIXO = "§6[Mochila] §f";

    private final JavaPlugin plugin;
    private final BackpackManager mochilas;
    private final MochilaArmazem armazem;
    private final NamespacedKey chaveDono;
    private final NamespacedKey chaveNivelOrigem = new NamespacedKey("originspaper", "max_level");
    private final NamespacedKey chaveDiscos = new NamespacedKey("featherdiscos", "discos_criados");
    private MochilaConfig config;
    /** Server tick of each player's last EvenMoreFish catch. */
    private final java.util.Map<UUID, Integer> ultimoPeixe = new java.util.HashMap<>();

    MochilaServico(JavaPlugin plugin, BackpackManager mochilas) {
        this.plugin = plugin;
        this.mochilas = mochilas;
        this.armazem = new MochilaArmazem(new File(plugin.getDataFolder(), "mochilas.yml"),
            plugin.getLogger());
        this.chaveDono = new NamespacedKey(plugin, "mochila_dono");
        this.config = MochilaConfig.ler(plugin.getConfig());
        armazem.carregar();
    }

    MochilaConfig config() {
        return config;
    }

    void recarregar() {
        plugin.reloadConfig();
        config = MochilaConfig.ler(plugin.getConfig());
    }

    JavaPlugin plugin() {
        return plugin;
    }

    MochilaDados dados(UUID jogador) {
        return armazem.de(jogador);
    }

    MochilaDados dadosExistentes(UUID jogador) {
        return armazem.existente(jogador);
    }

    void marcar() {
        armazem.marcar();
    }

    void salvar() {
        armazem.salvarSePreciso();
        mochilas.snapshotOpen();
        mochilas.flush();
    }

    /**
     * Storage id of a player's personal backpack.
     *
     * @param jogador the owner
     * @return the backpack UUID used by backpacks.yml
     */
    public static UUID idDaMochila(UUID jogador) {
        return UUID.nameUUIDFromBytes(("featherflux-mochila:" + jogador).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * True for any player's personal backpack item.
     *
     * @param item an item, possibly null
     * @return true if it is a personal backpack
     */
    public boolean ehPessoal(ItemStack item) {
        return dono(item) != null;
    }

    UUID dono(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        String valor = item.getItemMeta().getPersistentDataContainer().get(chaveDono,
            PersistentDataType.STRING);
        if (valor == null) {
            return null;
        }
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    boolean bloqueado(ItemStack item) {
        return config.bloquearShulkers() && item != null && Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    boolean pluginPresente(Melhoria m) {
        return m.plugin() == null || Bukkit.getPluginManager().isPluginEnabled(m.plugin());
    }

    /**
     * Whether the node's requirements are met, parents included.
     *
     * @param d player progress
     * @param m the node
     * @return true if unlocked
     */
    boolean liberada(MochilaDados d, Melhoria m) {
        if (!pluginPresente(m)) {
            return false;
        }
        MochilaConfig.Requisito r = config.requisitos().get(m);
        if (d.nivelMaximo < r.nivel() || d.valor(m.estatistica()) < r.quantidade()) {
            return false;
        }
        return m.pai() == null || liberada(d, m.pai());
    }

    boolean ativa(Player p, Melhoria m) {
        if (!config.ativa() || !p.hasPermission("mochila.usar")) {
            return false;
        }
        MochilaDados d = dados(p.getUniqueId());
        return liberada(d, m) && !d.desligadas.contains(m);
    }

    /**
     * The storage inventory of a player's backpack at its current size.
     *
     * @param jogador the owner
     * @return the inventory shared by every viewer
     */
    Inventory armazenamento(UUID jogador) {
        BackpackTier t = dados(jogador).capacidade;
        return mochilas.getInventory(idDaMochila(jogador), TextUtil.fromLegacy(t.getDisplayName()),
            t.getSlots());
    }

    BackpackManager gerenciador() {
        return mochilas;
    }

    /**
     * Puts an item into the player's backpack, respecting blocked items.
     *
     * @param jogador the owner
     * @param item the stack to store (not modified)
     * @return how many items did not fit
     */
    int guardar(UUID jogador, ItemStack item) {
        if (item == null || item.isEmpty() || bloqueado(item) || ehPessoal(item)) {
            return item == null ? 0 : item.getAmount();
        }
        Inventory inv = armazenamento(jogador);
        Map<Integer, ItemStack> sobra = inv.addItem(item.clone());
        int resto = sobra.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (resto < item.getAmount()) {
            mochilas.saveInventory(idDaMochila(jogador), inv);
        }
        return resto;
    }

    ItemStack criarItem(UUID jogador, MochilaDados d) {
        BackpackTier t = d.capacidade;
        ItemStack item = BackpackItem.createBackpack(t, idDaMochila(jogador));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        // A head is wearable; this one must stay in its hotbar slot.
        item.unsetData(DataComponentTypes.EQUIPPABLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.fromLegacy("§6" + t.getRawDisplayName()));
        List<String> lore = new ArrayList<>();
        lore.add("§8" + t.getSlots() + " slots");
        BackpackTier prox = config.proximo(t);
        if (prox != null) {
            lore.add("§7Cresce para §f" + prox.getRawDisplayName() + " §7(" + prox.getSlots()
                + " slots) no nível §e" + config.capacidade().get(prox) + " §7de origem.");
        }
        lore.add("");
        lore.add("§7Clique direito: §fabrir");
        lore.add("§7Agachado + clique direito: §fárvore de melhorias");
        meta.lore(TextUtil.fromLegacy(lore));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(chaveDono, PersistentDataType.STRING, jogador.toString());
        item.setItemMeta(meta);
        return item;
    }

    private boolean ehDoJogadorAtual(ItemStack item, Player p) {
        UUID dono = dono(item);
        BackpackTier t = BackpackItem.getBackpackTier(item);
        return p.getUniqueId().equals(dono) && t == dados(p.getUniqueId()).capacidade;
    }

    /**
     * Puts the player's backpack in its slot, removes stray copies and moves whatever was in the
     * slot to the inventory, the backpack itself or, last, the floor.
     *
     * @param p the player
     */
    void garantir(Player p) {
        if (!config.ativa() || !p.hasPermission("mochila.usar") || p.isDead()) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        int slot = config.slot();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != slot && ehPessoal(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        ItemStack atual = inv.getItem(slot);
        if (ehDoJogadorAtual(atual, p)) {
            return;
        }
        inv.setItem(slot, criarItem(p.getUniqueId(), dados(p.getUniqueId())));
        if (atual == null || atual.isEmpty() || ehPessoal(atual)) {
            return;
        }
        int resto = inv.addItem(atual).values().stream().mapToInt(ItemStack::getAmount).sum();
        if (resto > 0) {
            ItemStack sobra = atual.clone();
            sobra.setAmount(resto);
            resto = guardar(p.getUniqueId(), sobra);
            if (resto > 0) {
                sobra.setAmount(resto);
                p.getWorld().dropItemNaturally(p.getLocation(), sobra);
                p.sendMessage(PREFIXO + "O slot " + (slot + 1) + " é da mochila e seu inventário está cheio: "
                    + "um item caiu no chão.");
            } else {
                p.sendMessage(PREFIXO + "O slot " + (slot + 1) + " é da mochila; o item que estava lá foi para dentro dela.");
            }
        }
    }

    /** Called from EvenMoreFish's catch event: counts the fish and marks this tick's catch. */
    void peixePescado(Player p) {
        dados(p.getUniqueId()).peixes++;
        ultimoPeixe.put(p.getUniqueId(), Bukkit.getCurrentTick());
        marcar();
        atualizar(p);
    }

    /** True right after an EvenMoreFish catch, so the item on the hook is a custom fish. */
    boolean pescouAgora(Player p) {
        Integer tick = ultimoPeixe.get(p.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick <= 2;
    }

    void abrir(Player p) {
        if (!config.ativa()) {
            return;
        }
        p.openInventory(armazenamento(p.getUniqueId()));
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.1f);
    }

    /**
     * Pulls the latest origin level and disc count from the player's data, grows the backpack and
     * announces nodes unlocked since the last check.
     *
     * @param p the player
     */
    void atualizar(Player p) {
        if (!config.ativa()) {
            return;
        }
        MochilaDados d = dados(p.getUniqueId());
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Integer nivel = pdc.get(chaveNivelOrigem, PersistentDataType.INTEGER);
        if (nivel != null && nivel > d.nivelMaximo) {
            d.nivelMaximo = Math.min(nivel, 10);
            marcar();
        }
        Integer discos = pdc.get(chaveDiscos, PersistentDataType.INTEGER);
        if (discos != null && discos > d.discos) {
            d.discos = discos;
            marcar();
        }
        BackpackTier novo = config.nivelPara(d.nivelMaximo);
        if (novo.getSlots() > d.capacidade.getSlots()) {
            // Close any open view of the old size first: the inventory is replaced on resize.
            Inventory antigo = mochilas.getLoadedInventory(idDaMochila(p.getUniqueId()));
            if (antigo != null) {
                for (var viewer : List.copyOf(antigo.getViewers())) {
                    viewer.closeInventory();
                }
            }
            d.capacidade = novo;
            marcar();
            armazenamento(p.getUniqueId());
            garantir(p);
            p.sendMessage(PREFIXO + "Sua mochila cresceu: §e" + novo.getRawDisplayName() + " §f("
                + novo.getSlots() + " slots).");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        }
        for (Melhoria m : Melhoria.values()) {
            if (!d.avisadas.contains(m) && liberada(d, m)) {
                d.avisadas.add(m);
                marcar();
                p.sendMessage(PREFIXO + "Melhoria liberada: §a" + m.nome() + "§f. " + m.descricao()
                    + " §7(/mochila arvore)");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        }
    }
}
