package com.shweit.expendablebackpacks.mochila;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** /mochila [arvore | tocar | parar | ver | info | recarregar]. */
final class MochilaComando implements TabExecutor {

    /** Sound playing for each player and the world it plays in, to stop it later. */
    private record Tocando(String som, UUID mundo) {
    }

    private final MochilaServico servico;
    private final Runnable aoRecarregar;
    private final Map<UUID, Tocando> tocando = new HashMap<>();
    private final Map<UUID, Integer> proximaFaixa = new HashMap<>();
    private final Map<UUID, Long> ultimoToque = new HashMap<>();

    MochilaComando(MochilaServico servico, Runnable aoRecarregar) {
        this.servico = servico;
        this.aoRecarregar = aoRecarregar;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ver", "info", "recarregar" -> {
                if (!s.hasPermission("mochila.admin")) {
                    s.sendMessage(MochilaServico.PREFIXO + "Comando de administrador.");
                    return true;
                }
                admin(s, sub, a);
                return true;
            }
            default -> {
            }
        }
        if (!(s instanceof Player p)) {
            s.sendMessage(MochilaServico.PREFIXO + "Use dentro do jogo. Console: /mochila info|ver|recarregar.");
            return true;
        }
        if (!servico.config().ativa()) {
            p.sendMessage(MochilaServico.PREFIXO + "A mochila está desligada no servidor.");
            return true;
        }
        switch (sub) {
            case "" , "abrir" -> servico.abrir(p);
            case "arvore", "árvore" -> ArvoreGui.abrir(servico, p);
            case "tocar" -> tocar(p);
            case "parar" -> {
                if (!parar(p)) {
                    p.sendMessage(MochilaServico.PREFIXO + "Nada tocando.");
                }
            }
            default -> p.sendMessage(MochilaServico.PREFIXO
                + "Use /mochila, /mochila arvore, /mochila tocar ou /mochila parar.");
        }
        return true;
    }

    private void admin(CommandSender s, String sub, String[] a) {
        if (sub.equals("recarregar")) {
            servico.recarregar();
            aoRecarregar.run();
            s.sendMessage(MochilaServico.PREFIXO + "Configuração da mochila recarregada.");
            return;
        }
        if (a.length < 2) {
            s.sendMessage(MochilaServico.PREFIXO + "Use /mochila " + sub + " <jogador>.");
            return;
        }
        OfflinePlayer alvo = Bukkit.getOfflinePlayerIfCached(a[1]);
        if (alvo == null || servico.dadosExistentes(alvo.getUniqueId()) == null) {
            s.sendMessage(MochilaServico.PREFIXO + "Esse jogador ainda não tem mochila.");
            return;
        }
        MochilaDados d = servico.dados(alvo.getUniqueId());
        if (sub.equals("info")) {
            s.sendMessage(MochilaServico.PREFIXO + alvo.getName() + ": " + d.capacidade().getRawDisplayName()
                + " · nível de origem " + d.nivelMaximo() + " · peixes " + d.peixes + " · baús " + d.baus.size()
                + " · bebidas " + d.bebidas + " · discos " + d.discos);
            List<String> liberadas = new ArrayList<>();
            for (Melhoria m : Melhoria.values()) {
                if (servico.liberada(d, m)) {
                    liberadas.add(m.nome() + (d.desligadas.contains(m) ? " (desligada)" : ""));
                }
            }
            s.sendMessage(MochilaServico.PREFIXO + "Melhorias: "
                + (liberadas.isEmpty() ? "nenhuma" : String.join(", ", liberadas)));
            return;
        }
        if (!(s instanceof Player admin)) {
            s.sendMessage(MochilaServico.PREFIXO + "Use dentro do jogo para abrir a mochila de alguém.");
            return;
        }
        admin.openInventory(servico.armazenamento(alvo.getUniqueId()));
    }

    // ---- Toca-discos ---------------------------------------------------------------------------

    /** JEXT keeps the disc id under a key named jext.namespace_id, whatever namespace JEXT uses. */
    private static String disco(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey k : pdc.getKeys()) {
            if (k.getKey().equals("jext.namespace_id")) {
                String id = pdc.get(k, PersistentDataType.STRING);
                return id != null && id.matches("[a-z0-9_.-]{1,64}") ? id : null;
            }
        }
        return null;
    }

    private void tocar(Player p) {
        if (!servico.ativa(p, Melhoria.TOCA_DISCOS)) {
            p.sendMessage(MochilaServico.PREFIXO + "O Toca-discos ainda está bloqueado. Veja em /mochila arvore.");
            return;
        }
        long agora = System.currentTimeMillis();
        Long antes = ultimoToque.get(p.getUniqueId());
        if (antes != null && agora - antes < 3000) {
            p.sendMessage(MochilaServico.PREFIXO + "Espere um pouco antes de trocar de disco.");
            return;
        }
        ultimoToque.put(p.getUniqueId(), agora);
        Inventory bolsa = servico.armazenamento(p.getUniqueId());
        List<String> discos = new ArrayList<>();
        for (ItemStack it : bolsa.getContents()) {
            String id = disco(it);
            if (id != null && !discos.contains(id)) {
                discos.add(id);
            }
        }
        if (discos.isEmpty()) {
            p.sendMessage(MochilaServico.PREFIXO + "Guarde um disco da comunidade na mochila para tocar.");
            return;
        }
        int i = Math.floorMod(proximaFaixa.getOrDefault(p.getUniqueId(), 0), discos.size());
        proximaFaixa.put(p.getUniqueId(), i + 1);
        parar(p);
        String som = "minecraft:" + discos.get(i);
        p.getWorld().playSound(p, som, SoundCategory.RECORDS, 4f, 1f);
        tocando.put(p.getUniqueId(), new Tocando(som, p.getWorld().getUID()));
        p.sendMessage(MochilaServico.PREFIXO + "Tocando o disco " + (i + 1) + " de " + discos.size()
            + ". /mochila tocar passa para o próximo, /mochila parar para.");
    }

    private boolean parar(Player p) {
        Tocando t = tocando.remove(p.getUniqueId());
        if (t == null) {
            return false;
        }
        var mundo = Bukkit.getWorld(t.mundo());
        if (mundo != null) {
            for (Player ouvinte : mundo.getPlayers()) {
                ouvinte.stopSound(t.som(), SoundCategory.RECORDS);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (a.length == 1) {
            List<String> opcoes = new ArrayList<>(List.of("arvore", "tocar", "parar"));
            if (s.hasPermission("mochila.admin")) {
                opcoes.addAll(List.of("ver", "info", "recarregar"));
            }
            return opcoes.stream().filter(o -> o.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && s.hasPermission("mochila.admin") && List.of("ver", "info").contains(a[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
