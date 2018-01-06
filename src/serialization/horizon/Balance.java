package it.menzani.stellarpool.serialization.horizon;

import it.menzani.stellarpool.StellarCurrency;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public final class Balance {
    @NotNull
    public static Balance fromCurrency(@NotNull StellarCurrency currency) {
        return new Balance(currency.getLumens(), currency.getStroops(), currency.toString());
    }

    private final BigDecimal lumens;
    private final long stroops;
    private final String formatted;

    private Balance(double lumens, long stroops, @NotNull String formatted) {
        this.lumens = new BigDecimal(lumens);
        this.stroops = stroops;
        this.formatted = formatted;
    }
}