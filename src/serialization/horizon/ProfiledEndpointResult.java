package it.menzani.stellarpool.serialization.horizon;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class ProfiledEndpointResult {
    private final Object result;
    private final long executionTime;
    private final TimeUnit executionTimeUnit;

    public ProfiledEndpointResult(@NotNull Object result, long executionTime, @NotNull TimeUnit executionTimeUnit) {
        this.result = result;
        this.executionTime = executionTime;
        this.executionTimeUnit = executionTimeUnit;
    }
}
