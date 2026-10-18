package com.shweit.expendablebackpacks.mochila;

import com.shweit.expendablebackpacks.items.BackpackTier;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** mochilas.yml: progress for every player, written atomically like backpacks.yml. */
final class MochilaArmazem {

    private final File arquivo;
    private final Logger log;
    private final Map<UUID, MochilaDados> dados = new HashMap<>();
    private boolean sujo;

    MochilaArmazem(File arquivo, Logger log) {
        this.arquivo = arquivo;
        this.log = log;
    }

    void carregar() {
        dados.clear();
        if (!arquivo.exists()) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(arquivo);
        } catch (IOException | InvalidConfigurationException e) {
            File quebrado = new File(arquivo.getParentFile(),
                arquivo.getName() + ".corrupt-" + System.currentTimeMillis());
            boolean movido = arquivo.renameTo(quebrado);
            log.log(Level.SEVERE, "mochilas.yml could not be read; "
                + (movido ? "kept as " + quebrado.getName() : "left in place") + ".", e);
            return;
        }
        for (String chave : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(chave);
            UUID id;
            try {
                id = UUID.fromString(chave);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (s == null) {
                continue;
            }
            MochilaDados d = new MochilaDados();
            d.nivelMaximo = Math.max(1, s.getInt("nivel-maximo", 1));
            d.peixes = Math.max(0, s.getInt("peixes"));
            d.bebidas = Math.max(0, s.getInt("bebidas"));
            d.discos = Math.max(0, s.getInt("discos"));
            try {
                d.capacidade = BackpackTier.valueOf(s.getString("capacidade", "LEATHER"));
            } catch (IllegalArgumentException e) {
                d.capacidade = BackpackTier.LEATHER;
            }
            for (String b : s.getStringList("baus")) {
                d.registrarBau(b);
            }
            for (String m : s.getStringList("desligadas")) {
                Melhoria melhoria = Melhoria.porChave(m);
                if (melhoria != null) {
                    d.desligadas.add(melhoria);
                }
            }
            for (String m : s.getStringList("avisadas")) {
                Melhoria melhoria = Melhoria.porChave(m);
                if (melhoria != null) {
                    d.avisadas.add(melhoria);
                }
            }
            dados.put(id, d);
        }
    }

    MochilaDados de(UUID jogador) {
        return dados.computeIfAbsent(jogador, k -> new MochilaDados());
    }

    MochilaDados existente(UUID jogador) {
        return dados.get(jogador);
    }

    void marcar() {
        sujo = true;
    }

    void salvarSePreciso() {
        if (sujo) {
            salvar();
        }
    }

    void salvar() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of("Mochila pessoal do FeatherFlux: progresso por jogador.",
            "O conteúdo das mochilas fica em backpacks.yml."));
        for (Map.Entry<UUID, MochilaDados> e : dados.entrySet()) {
            MochilaDados d = e.getValue();
            String p = e.getKey() + ".";
            yml.set(p + "nivel-maximo", d.nivelMaximo);
            yml.set(p + "capacidade", d.capacidade.name());
            yml.set(p + "peixes", d.peixes);
            yml.set(p + "bebidas", d.bebidas);
            yml.set(p + "discos", d.discos);
            yml.set(p + "baus", new ArrayList<>(d.baus));
            yml.set(p + "desligadas", d.desligadas.stream().map(Melhoria::chave).toList());
            yml.set(p + "avisadas", d.avisadas.stream().map(Melhoria::chave).toList());
        }
        Path alvo = arquivo.toPath();
        Path tmp = alvo.resolveSibling(arquivo.getName() + ".tmp");
        try {
            Files.writeString(tmp, yml.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp, alvo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            sujo = false;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save mochilas.yml", e);
        }
    }
}
