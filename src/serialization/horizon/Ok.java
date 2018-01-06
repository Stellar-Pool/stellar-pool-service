package it.menzani.stellarpool.serialization.horizon;

import org.jetbrains.annotations.NotNull;

public final class Ok {
    private final Object value;
    private final Status status;

    public Ok(@NotNull Object value) {
        this.value = value;
        this.status = Status.SUCCESS;
    }
}
