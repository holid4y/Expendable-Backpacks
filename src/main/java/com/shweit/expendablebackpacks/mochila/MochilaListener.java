package com.shweit.expendablebackpacks.mochila;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Keeps the personal backpack in its slot and opens it. */
final class MochilaListener implements Listener {

    private final MochilaServico servico;

    MochilaListener(MochilaServico servico) {
        this.servico = servico;
    }

    private void depois(Player p) {
        servico.plugin().getServer().getScheduler().runTask(servico.plugin(), () -> {
            if (p.isOnline()) {
                servico.atualizar(p);
                servico.garantir(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entrar(PlayerJoinEvent e) {
        depois(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void renascer(PlayerRespawnEvent e) {
        depois(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void mundo(PlayerChangedWorldEvent e) {
        depois(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void modoDeJogo(PlayerGameModeChangeEvent e) {
        depois(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void fechar(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            depois(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void sair(PlayerQuitEvent e) {
        Inventory inv = servico.gerenciador().getLoadedInventory(
            MochilaServico.idDaMochila(e.getPlayer().getUniqueId()));
        if (inv != null) {
            servico.gerenciador().saveInventoryNow(
                MochilaServico.idDaMochila(e.getPlayer().getUniqueId()), inv);
        }
        servico.salvar();
    }

    /** Never part of the death drops: the backpack (and what's in it) stays with the player. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void morrer(PlayerDeathEvent e) {
        e.getDrops().removeIf(servico::ehPessoal);
        e.getItemsToKeep().removeIf(servico::ehPessoal);
        PlayerInventory inv = e.getEntity().getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (servico.ehPessoal(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }

    /** A backpack item can never exist on the ground. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void itemNoChao(ItemSpawnEvent e) {
        if (servico.ehPessoal(e.getEntity().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pegar(EntityPickupItemEvent e) {
        if (servico.ehPessoal(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void soltar(PlayerDropItemEvent e) {
        if (servico.ehPessoal(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    /** Runs after OriginsPaper's crouch+F skill, which cancels the swap itself when it fires. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trocarMao(PlayerSwapHandItemsEvent e) {
        if (servico.ehPessoal(e.getMainHandItem()) || servico.ehPessoal(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void colocar(BlockPlaceEvent e) {
        if (servico.ehPessoal(e.getItemInHand())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void entidade(PlayerInteractEntityEvent e) {
        Entity alvo = e.getRightClicked();
        ItemStack mao = e.getPlayer().getInventory().getItem(e.getHand());
        if (servico.ehPessoal(mao)
            && (alvo instanceof ItemFrame || alvo instanceof ArmorStand || alvo instanceof Allay)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void suporte(PlayerArmorStandManipulateEvent e) {
        if (servico.ehPessoal(e.getPlayerItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void clicar(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean envolve = servico.ehPessoal(e.getCurrentItem()) || servico.ehPessoal(e.getCursor());
        if (!envolve && e.getClick() == ClickType.NUMBER_KEY) {
            envolve = servico.ehPessoal(p.getInventory().getItem(e.getHotbarButton()));
        }
        if (!envolve && e.getClick() == ClickType.SWAP_OFFHAND) {
            envolve = servico.ehPessoal(p.getInventory().getItemInOffHand());
        }
        // The slot itself is reserved even while the backpack is briefly missing from it.
        boolean reservado = servico.config().ativa() && p.hasPermission("mochila.usar");
        if (!envolve && reservado && e.getClickedInventory() instanceof PlayerInventory
            && e.getSlot() == servico.config().slot()) {
            envolve = true;
        }
        if (!envolve && reservado && e.getClick() == ClickType.NUMBER_KEY
            && e.getHotbarButton() == servico.config().slot()) {
            envolve = true;
        }
        if (envolve) {
            e.setCancelled(true);
        }
    }

    private boolean ehMochila(Inventory inv) {
        return inv != null && servico.gerenciador().findInventoryUUID(inv) != null;
    }

    /** Shulker boxes never go into any backpack, whichever way the player tries. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void shulkerNaMochila(InventoryClickEvent e) {
        Inventory topo = e.getView().getTopInventory();
        if (!ehMochila(topo) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        boolean noTopo = e.getClickedInventory() == topo;
        ItemStack movido = null;
        if (noTopo) {
            movido = e.getClick() == ClickType.NUMBER_KEY ? p.getInventory().getItem(e.getHotbarButton())
                : e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : e.getCursor();
        } else if (e.isShiftClick()) {
            movido = e.getCurrentItem();
        }
        if (servico.bloqueado(movido)) {
            e.setCancelled(true);
            p.sendMessage(MochilaServico.PREFIXO + "Caixas de shulker não entram na mochila.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void arrastar(InventoryDragEvent e) {
        if (servico.ehPessoal(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }
        if (servico.bloqueado(e.getOldCursor()) && ehMochila(e.getView().getTopInventory())
            && e.getRawSlots().stream().anyMatch(r -> r < e.getView().getTopInventory().getSize())) {
            e.setCancelled(true);
            return;
        }
        if (!servico.config().ativa() || !e.getWhoClicked().hasPermission("mochila.usar")) {
            return;
        }
        InventoryView view = e.getView();
        for (int raw : e.getRawSlots()) {
            if (view.getInventory(raw) instanceof PlayerInventory
                && view.convertSlot(raw) == servico.config().slot()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Paper flags this as approximate (a few blocks only react in some states); good enough here. */
    @SuppressWarnings("deprecation")
    private static boolean usavel(Block bloco) {
        return bloco.getType().isInteractable();
    }

    /**
     * Right-click opens the backpack, crouching opens the upgrade tree. Doors, chests and other
     * blocks you can use still work when the backpack is in hand. LOWEST and fully cancelled so no
     * other plugin acts on the item first (the goat origin would otherwise eat it for food).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void usar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !servico.ehPessoal(e.getItem())) {
            return;
        }
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.useItemInHand() == Event.Result.DENY && e.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        Player p = e.getPlayer();
        Block bloco = e.getClickedBlock();
        if (a == Action.RIGHT_CLICK_BLOCK && bloco != null && usavel(bloco) && !p.isSneaking()) {
            e.setUseItemInHand(Event.Result.DENY);
            return;
        }
        e.setCancelled(true);
        if (p.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (p.isSneaking()) {
            ArvoreGui.abrir(servico, p);
        } else {
            servico.abrir(p);
        }
    }
}
