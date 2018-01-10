package it.menzani.stellarpool.serialization.horizon;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.jetbrains.annotations.NotNull;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class Problem {
    private final String message;
    private final Status status;

    public Problem(@NotNull String message) {
        this.message = message;
        this.status = Status.ERROR;
    }
}
