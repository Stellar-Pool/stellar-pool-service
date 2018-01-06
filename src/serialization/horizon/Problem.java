package it.menzani.stellarpool.serialization.horizon;

import org.jetbrains.annotations.NotNull;

public final class Problem {
    private final String message;
    private final Status status;

    public Problem(@NotNull String message) {
        this.message = message;
        this.status = Status.ERROR;
    }
}
