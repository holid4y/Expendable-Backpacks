package com.shweit.expendablebackpacks.mochila;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Coletor, Reabastecer and Bolso de Pesca. */
final class EfeitosListener implements Listener {

    private final MochilaServico servico;

    EfeitosListener(MochilaServico servico) {
        this.servico = servico;
    }

    // ---- Coletor -------------------------------------------------------------------------------

    /**
     * Only when nothing of the stack fits in the inventory, so vanilla pickup always comes first.
     * The pickup event is cancelled and handled here; changing the stack inside a pickup that
     * vanilla then completes would duplicate it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void coletar(PlayerAttemptPickupItemEvent e) {
        Player p = e.getPlayer();
        Item chao = e.getItem();
        ItemStack pilha = chao.getItemStack();
        if (e.getRemaining() < pilha.getAmount() || p.getGameMode() == GameMode.SPECTATOR
            || chao.getPickupDelay() > 0 || !chao.canPlayerPickup()
            || (chao.getOwner() != null && !chao.getOwner().equals(p.getUniqueId()))
            || !servico.ativa(p, Melhoria.COLETOR)) {
            return;
        }
        int resto = servico.guardar(p.getUniqueId(), pilha);
        int guardado = pilha.getAmount() - resto;
        if (guardado <= 0) {
            return;
        }
        e.setCancelled(true);
        p.playPickupItemAnimation(chao, guardado);
        if (resto == 0) {
            chao.remove();
        } else {
            ItemStack sobra = pilha.clone();
            sobra.setAmount(resto);
            chao.setItemStack(sobra);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 0.8f);
    }

    // ---- Reabastecer ---------------------------------------------------------------------------

    private void repor(Player p, EquipmentSlot mao, ItemStack usado, boolean porTipo) {
        if (usado == null || usado.isEmpty() || servico.ehPessoal(usado)
            || !servico.ativa(p, Melhoria.REABASTECER)) {
            return;
        }
        ItemStack modelo = usado.clone();
        UUID id = p.getUniqueId();
        servico.plugin().getServer().getScheduler().runTask(servico.plugin(), () -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            ItemStack agora = p.getInventory().getItem(mao);
            if (agora != null && !agora.isEmpty()) {
                return;
            }
            Inventory bolsa = servico.armazenamento(id);
            for (int i = 0; i < bolsa.getSize(); i++) {
                ItemStack it = bolsa.getItem(i);
                if (it == null || it.isEmpty()) {
                    continue;
                }
                boolean serve = porTipo ? it.getType() == modelo.getType() : it.isSimilar(modelo);
                if (serve) {
                    bolsa.setItem(i, null);
                    p.getInventory().setItem(mao, it);
                    servico.gerenciador().saveInventory(MochilaServico.idDaMochila(id), bolsa);
                    p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.6f, 1.0f);
                    return;
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void colocou(BlockPlaceEvent e) {
        if (e.getItemInHand().getAmount() == 1) {
            repor(e.getPlayer(), e.getHand(), e.getItemInHand(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void comeu(PlayerItemConsumeEvent e) {
        if (e.getItem().getAmount() == 1) {
            repor(e.getPlayer(), e.getHand(), e.getItem(), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void lancou(PlayerLaunchProjectileEvent e) {
        ItemStack usado = e.getItemStack();
        if (usado.getAmount() != 1 || !e.shouldConsume()) {
            return;
        }
        Player p = e.getPlayer();
        EquipmentSlot mao = usado.isSimilar(p.getInventory().getItemInMainHand())
            ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        repor(p, mao, usado, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quebrou(PlayerItemBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack quebrado = e.getBrokenItem();
        EquipmentSlot mao;
        if (quebrado.equals(p.getInventory().getItemInMainHand())) {
            mao = EquipmentSlot.HAND;
        } else if (quebrado.equals(p.getInventory().getItemInOffHand())) {
            mao = EquipmentSlot.OFF_HAND;
        } else {
            return; // armour: nothing to refill
        }
        repor(p, mao, quebrado, true);
    }

    // ---- Bolso de Pesca ------------------------------------------------------------------------

    /**
     * EvenMoreFish swaps the caught item in its own listener and fires its catch event, which
     * {@link Integracoes} turns into {@link MochilaServico#pescouAgora}; look a tick later.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pescou(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item peixe)) {
            return;
        }
        Player p = e.getPlayer();
        servico.plugin().getServer().getScheduler().runTask(servico.plugin(), () -> {
            if (!peixe.isValid() || !p.isOnline() || !servico.pescouAgora(p)
                || !servico.ativa(p, Melhoria.BOLSO_DE_PESCA)) {
                return;
            }
            ItemStack pilha = peixe.getItemStack();
            int resto = servico.guardar(p.getUniqueId(), pilha);
            if (resto == pilha.getAmount()) {
                return;
            }
            if (resto == 0) {
                peixe.remove();
            } else {
                ItemStack sobra = pilha.clone();
                sobra.setAmount(resto);
                peixe.setItemStack(sobra);
            }
            p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.7f, 1.2f);
            p.sendActionBar(net.kyori.adventure.text.Component.text("Peixe guardado na mochila"));
        });
    }
}
