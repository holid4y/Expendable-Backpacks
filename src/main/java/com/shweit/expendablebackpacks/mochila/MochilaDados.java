package com.shweit.expendablebackpacks.mochila;

import com.shweit.expendablebackpacks.items.BackpackTier;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Per-player progress of the personal backpack. Mutated on the main thread only. */
public final class MochilaDados {

    /** Loot containers remembered per player; past this, the count no longer matters. */
    static final int MAX_BAUS = 500;

    int nivelMaximo = 1;
    int peixes;
    int bebidas;
    int discos;
    BackpackTier capacidade = BackpackTier.LEATHER;
    final Set<String> baus = new LinkedHashSet<>();
    final Set<Melhoria> desligadas = EnumSet.noneOf(Melhoria.class);
    final Set<Melhoria> avisadas = EnumSet.noneOf(Melhoria.class);

    /**
     * Value of the statistic a node counts.
     *
     * @param e the statistic
     * @return its current value
     */
    public int valor(Melhoria.Estatistica e) {
        return switch (e) {
            case PEIXES -> peixes;
            case BAUS -> baus.size();
            case BEBIDAS -> bebidas;
            case DISCOS -> discos;
            case NENHUMA -> 0;
        };
    }

    /**
     * Remembers a loot container; returns true the first time it is seen.
     *
     * @param chave world and position, or entity id
     * @return true if new
     */
    boolean registrarBau(String chave) {
        if (baus.size() >= MAX_BAUS || !baus.add(chave)) {
            return false;
        }
        return true;
    }

    public int nivelMaximo() {
        return nivelMaximo;
    }

    public BackpackTier capacidade() {
        return capacidade;
    }
}
