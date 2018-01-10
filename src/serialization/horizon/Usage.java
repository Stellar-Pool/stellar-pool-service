package it.menzani.stellarpool.serialization.horizon;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class Usage {
    private final int maxParallelism;
    private final Network network;

    public Usage(int maxParallelism, @NotNull Network network) {
        this.maxParallelism = maxParallelism;
        this.network = network;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Network {
        private final long accountsCount;
        private final long circulatingSupply;
        private final long totalVotes;
        private final long votersCount;
        private final Summary summary;

        public Network(long accountsCount, long circulatingSupply, long totalVotes, long votersCount, @NotNull Summary summary) {
            this.accountsCount = accountsCount;
            this.circulatingSupply = circulatingSupply;
            this.totalVotes = totalVotes;
            this.votersCount = votersCount;
            this.summary = summary;
        }

        @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
        public static final class Summary {
            private final long totalRequests;
            private final long totalTime;
            private final TimeUnit totalTimeUnit;

            public Summary(long totalRequests, long totalTime, @NotNull TimeUnit totalTimeUnit) {
                this.totalRequests = totalRequests;
                this.totalTime = totalTime;
                this.totalTimeUnit = totalTimeUnit;
            }
        }
    }
}
