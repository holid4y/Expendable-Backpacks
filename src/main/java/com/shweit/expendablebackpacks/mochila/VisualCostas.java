package com.shweit.expendablebackpacks.mochila;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Shows the backpack on the player's back.
 *
 * <p>A real, non-persistent ItemDisplay is kept near the player (so the server tracks it normally),
 * and only the clients are told it rides the player, through SET_PASSENGERS packets. The server never
 * sees a passenger, so teleports, mounts and plugins that seat players on each other are untouched.
 * The client positions passengers itself every frame, which keeps the backpack glued to the body.
 * Bedrock players (Floodgate ids) are left out: Geyser has no display entities.
 */
final class VisualCostas implements Listener {

    private final MochilaServico servico;
    private final Map<UUID, ItemDisplay> exibicoes = new HashMap<>();
    /** Wearer entity id to display entity id, read from netty threads. */
    private final Map<Integer, Integer> porEntidade = new ConcurrentHashMap<>();
    private final PacketListenerAbstract pacotes;
    private BukkitTask tarefa;

    VisualCostas(MochilaServico servico) {
        this.servico = servico;
        this.pacotes = new PacketListenerAbstract(PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketSend(PacketSendEvent e) {
                if (e.getPacketType() != PacketType.Play.Server.SET_PASSENGERS) {
                    return;
                }
                WrapperPlayServerSetPassengers w = new WrapperPlayServerSetPassengers(e);
                Integer exibicao = porEntidade.get(w.getEntityId());
                if (exibicao == null || bedrock(e.getUser().getUUID())) {
                    return;
                }
                int[] atuais = w.getPassengers();
                for (int id : atuais) {
                    if (id == exibicao) {
                        return;
                    }
                }
                int[] novos = Arrays.copyOf(atuais, atuais.length + 1);
                novos[atuais.length] = exibicao;
                w.setPassengers(novos);
                e.markForReEncode(true);
            }
        };
    }

    private static boolean bedrock(UUID id) {
        return id != null && id.getMostSignificantBits() == 0L;
    }

    void ligar() {
        PacketEvents.getAPI().getEventManager().registerListener(pacotes);
        tarefa = Bukkit.getScheduler().runTaskTimer(servico.plugin(), this::tick, 1L, 1L);
    }

    void desligar() {
        if (tarefa != null) {
            tarefa.cancel();
        }
        PacketEvents.getAPI().getEventManager().unregisterListener(pacotes);
        for (ItemDisplay d : exibicoes.values()) {
            d.remove();
        }
        exibicoes.clear();
        porEntidade.clear();
    }

    /** Rebuilds every display, e.g. after /mochila recarregar. */
    void refazer() {
        for (UUID id : List.copyOf(exibicoes.keySet())) {
            remover(id);
        }
    }

    private void remover(UUID jogador) {
        ItemDisplay d = exibicoes.remove(jogador);
        if (d != null) {
            porEntidade.values().remove(d.getEntityId());
            d.remove();
        }
    }

    private boolean deveMostrar(Player p) {
        if (!servico.config().visualAtivo() || !servico.config().ativa() || !p.hasPermission("mochila.usar")
            || p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isInvisible()
            || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return false;
        }
        Pose pose = p.getPose();
        return pose == Pose.STANDING || pose == Pose.SNEAKING;
    }

    private float escalaDe(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.SCALE);
        return a == null ? 1f : (float) Math.clamp(a.getValue(), 0.0625, 16.0);
    }

    /**
     * The client draws the head of an item display as a 0.5 block cube hanging below the display's
     * origin, already turned so its face looks backwards; the origin sits on the player's passenger
     * point (the top of the hitbox). Both scale with the player's size attribute.
     */
    private Transformation transformacao(float escala) {
        MochilaConfig c = servico.config();
        Vector3f desloc = new Vector3f(c.deslocamento()).mul(escala);
        Vector3f tam = new Vector3f(c.escala()).mul(escala);
        return new Transformation(desloc, new AxisAngle4f(), tam, new AxisAngle4f());
    }

    private ItemStack itemDe(Player p) {
        ItemStack i = p.getInventory().getItem(servico.config().slot());
        return servico.ehPessoal(i) ? i.clone() : null;
    }

    private void criar(Player p, ItemStack item) {
        Location loc = p.getLocation();
        float escala = escalaDe(p);
        ItemDisplay d = p.getWorld().spawn(loc, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(transformacao(escala));
            e.setTeleportDuration(2);
            e.setShadowRadius(0f);
            e.setRotation(p.getBodyYaw() + servico.config().giro(), 0f);
        });
        if (!servico.config().visualParaDono()) {
            p.hideEntity(servico.plugin(), d);
        }
        exibicoes.put(p.getUniqueId(), d);
        porEntidade.put(p.getEntityId(), d.getEntityId());
        anunciar(p, d, 2L);
    }

    /** Tells every client that sees both entities that the display rides the player. */
    private void anunciar(Player dono, ItemDisplay d, long atraso) {
        Bukkit.getScheduler().runTaskLater(servico.plugin(), () -> {
            if (!dono.isOnline() || !d.isValid()) {
                return;
            }
            enviar(dono, dono, d);
            for (Player viewer : dono.getTrackedBy()) {
                enviar(viewer, dono, d);
            }
        }, atraso);
    }

    private void enviar(Player viewer, Player dono, ItemDisplay d) {
        if (bedrock(viewer.getUniqueId()) || (viewer.equals(dono) && !servico.config().visualParaDono())) {
            return;
        }
        List<Entity> reais = dono.getPassengers();
        int[] ids = new int[reais.size() + 1];
        for (int i = 0; i < reais.size(); i++) {
            ids[i] = reais.get(i).getEntityId();
        }
        ids[reais.size()] = d.getEntityId();
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
            new WrapperPlayServerSetPassengers(dono.getEntityId(), ids));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rastrear(PlayerTrackEntityEvent e) {
        Entity alvo = e.getEntity();
        Player viewer = e.getPlayer();
        for (Map.Entry<UUID, ItemDisplay> en : exibicoes.entrySet()) {
            ItemDisplay d = en.getValue();
            Player dono = Bukkit.getPlayer(en.getKey());
            if (dono != null && (alvo.equals(d) || alvo.equals(dono))) {
                Bukkit.getScheduler().runTaskLater(servico.plugin(), () -> {
                    if (viewer.isOnline() && dono.isOnline() && d.isValid()) {
                        enviar(viewer, dono, d);
                    }
                }, 2L);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morrer(PlayerDeathEvent e) {
        remover(e.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void sair(PlayerQuitEvent e) {
        remover(e.getPlayer().getUniqueId());
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemDisplay d = exibicoes.get(p.getUniqueId());
            ItemStack item = deveMostrar(p) ? itemDe(p) : null;
            if (item == null) {
                if (d != null) {
                    remover(p.getUniqueId());
                }
                continue;
            }
            if (d == null || !d.isValid() || !d.getWorld().equals(p.getWorld())) {
                remover(p.getUniqueId());
                criar(p, item);
                continue;
            }
            if (!item.isSimilar(d.getItemStack())) {
                d.setItemStack(item);
            }
            Transformation t = transformacao(escalaDe(p));
            if (!t.equals(d.getTransformation())) {
                d.setTransformation(t);
            }
            Location loc = d.getLocation();
            float yaw = p.getBodyYaw() + servico.config().giro();
            if (loc.distanceSquared(p.getLocation()) > 4.0) {
                Location nova = p.getLocation();
                nova.setYaw(yaw);
                nova.setPitch(0f);
                d.teleport(nova);
            } else if (Math.abs(Location.normalizeYaw(loc.getYaw() - yaw)) > 1.5f) {
                d.setRotation(yaw, 0f);
            }
        }
        for (UUID id : List.copyOf(exibicoes.keySet())) {
            if (Bukkit.getPlayer(id) == null) {
                remover(id);
            }
        }
    }
}
