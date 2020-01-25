package net.stellarpool.serialization.horizon;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.jetbrains.annotations.NotNull;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class Ok {
    private final Object value;
    private final Status status;

    public Ok(@NotNull Object value) {
        this.value = value;
        this.status = Status.SUCCESS;
    }
}
