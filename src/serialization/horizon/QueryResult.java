package it.menzani.stellarpool.serialization.horizon;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class QueryResult {
    private final Object result;
    private final long queryTime;
    private final TimeUnit queryTimeUnit;

    public QueryResult(@NotNull Object result, long queryTime, @NotNull TimeUnit queryTimeUnit) {
        this.result = result;
        this.queryTime = queryTime;
        this.queryTimeUnit = queryTimeUnit;
    }
}
