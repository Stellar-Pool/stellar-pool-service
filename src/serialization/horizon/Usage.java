package it.menzani.stellarpool.serialization.horizon;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class Usage {
    private final int maxParallelism;
    private final List<Endpoint> endpoints;

    public Usage(int maxParallelism, @NotNull List<Endpoint> endpoints) {
        this.maxParallelism = maxParallelism;
        this.endpoints = endpoints;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Endpoint {
        private final String name;
        private final Descriptor descriptor;

        public Endpoint(@NotNull String name, @NotNull Descriptor descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static final class Descriptor {
            private final long totalRequests;
            private Long totalTime;
            private TimeUnit totalTimeUnit;

            public Descriptor(long totalRequests) {
                this.totalRequests = totalRequests;
            }

            @NotNull
            public Descriptor setTotalTime(long totalTime, @NotNull TimeUnit totalTimeUnit) {
                this.totalTime = totalTime;
                this.totalTimeUnit = totalTimeUnit;
                return this;
            }
        }
    }
}