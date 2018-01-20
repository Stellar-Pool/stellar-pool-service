package it.menzani.stellarpool.serialization.pool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.menzani.stellarpool.distribution.Account;
import it.menzani.stellarpool.distribution.Percent;
import it.menzani.stellarpool.distribution.StellarCurrency;
import it.menzani.stellarpool.serialization.MalformedConfigurationException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

@JsonIgnoreProperties("Comments")
public final class Configuration {
    private Core core;
    private Horizon horizon;
    private String pool;
    private String bank;
    private Messages messages;
    private FeeDescriptor[] feeSchedule;
    private String feeCollector;
    private Tests tests;
    private SafetyThresholds safetyThresholds;

    @NotNull
    public Core getCore() {
        if (core == null) throw new MalformedConfigurationException("core", "must not be null");
        return core;
    }

    @NotNull
    public Horizon getHorizon() {
        if (horizon == null) throw new MalformedConfigurationException("horizon", "must not be null");
        return horizon;
    }

    @NotNull
    public Account getPool() {
        if (pool == null || pool.isEmpty())
            throw new MalformedConfigurationException("pool", "must not be null nor empty");
        return new Account(pool, null);
    }

    @NotNull
    public String getBank() {
        if (bank == null || bank.isEmpty())
            throw new MalformedConfigurationException("bank", "must not be null nor empty");
        return bank;
    }

    @NotNull
    public Messages getMessages() {
        if (messages == null) throw new MalformedConfigurationException("messages", "must not be null");
        return messages;
    }

    @NotNull
    public FeeDescriptor[] getFeeSchedule() {
        if (feeSchedule == null || feeSchedule.length == 0)
            throw new MalformedConfigurationException("feeSchedule", "must not be null and it must contain at least one element");
        return feeSchedule;
    }

    @NotNull
    public Account getFeeCollector() {
        if (feeCollector == null || feeCollector.isEmpty())
            throw new MalformedConfigurationException("feeCollector", "must not be null nor empty");
        return new Account(feeCollector, null);
    }

    @NotNull
    public Tests getTests() {
        if (tests == null) throw new MalformedConfigurationException("tests", "must not be null");
        return tests;
    }

    @NotNull
    public SafetyThresholds getSafetyThresholds() {
        if (safetyThresholds == null)
            throw new MalformedConfigurationException("safetyThresholds", "must not be null");
        return safetyThresholds;
    }

    public static final class Core {
        private String host;
        private String database;
        private String user;
        private String password;

        @NotNull
        public String getHost() {
            if (host == null || host.isEmpty())
                throw new MalformedConfigurationException("core.host", "must not be null nor empty");
            return host;
        }

        @NotNull
        public String getDatabase() {
            if (database == null || database.isEmpty())
                throw new MalformedConfigurationException("core.database", "must not be null nor empty");
            return database;
        }

        @NotNull
        public String getUser() {
            if (user == null || user.isEmpty())
                throw new MalformedConfigurationException("core.user", "must not be null nor empty");
            return user;
        }

        @NotNull
        public String getPassword() {
            if (password == null) throw new MalformedConfigurationException("core.password", "must not be null");
            return password;
        }
    }

    public static final class Horizon {
        private short port;
        private int backlog;
        private int parallelism;
        private String password;
        private Certificate certificate;

        public int getPort() {
            if (port < 0) throw new MalformedConfigurationException("horizon.port", "must be positive");
            return port;
        }

        public int getBacklog() {
            if (backlog < 0) throw new MalformedConfigurationException("horizon.backlog", "must be positive");
            return backlog;
        }

        public int getParallelism() {
            if (parallelism < 0) throw new MalformedConfigurationException("horizon.parallelism", "must be positive");
            return parallelism;
        }

        @NotNull
        public String getPassword() {
            if (password == null) throw new MalformedConfigurationException("horizon.password", "must not be null");
            return password;
        }

        @NotNull
        public Certificate getCertificate() {
            if (certificate == null)
                throw new MalformedConfigurationException("horizon.certificate", "must not be null");
            return certificate;
        }

        public static final class Certificate {
            private boolean sslEnabled;
            private String file;
            private String type;
            private String password;

            public boolean isSslEnabled() {
                return sslEnabled;
            }

            @NotNull
            public Path getFile() {
                if (file == null || file.isEmpty())
                    throw new MalformedConfigurationException("horizon.certificate.file", "must not be null nor empty");
                return Paths.get(file);
            }

            @NotNull
            public String getType() {
                if (type == null || type.isEmpty())
                    throw new MalformedConfigurationException("horizon.certificate.type", "must not be null nor empty");
                return type;
            }

            @NotNull
            public char[] getPassword() {
                if (password == null || password.isEmpty())
                    throw new MalformedConfigurationException("horizon.certificate.password", "must not be null nor empty");
                return password.toCharArray();
            }
        }
    }

    public static final class Messages {
        private String distribution;
        private String inflation;

        @NotNull
        public String getDistribution() {
            if (distribution == null || distribution.isEmpty() || distribution.getBytes().length > 28)
                throw new MalformedConfigurationException("messages.distribution", "must not be null, nor empty, nor greater than 28 bytes in size");
            return distribution;
        }

        @NotNull
        public String getInflation() {
            if (inflation == null || inflation.isEmpty() || inflation.getBytes().length > 28)
                throw new MalformedConfigurationException("messages.inflation", "must not be null, nor empty, nor greater than 28 bytes in size");
            return inflation;
        }
    }

    public static final class FeeDescriptor implements Comparable<FeeDescriptor> {
        private double threshold;
        private double fee;

        @NotNull
        public StellarCurrency getThreshold() {
            return StellarCurrency.Companion.ofLumens(threshold);
        }

        @NotNull
        public Percent getFee() {
            return new Percent(fee);
        }

        @Override
        public int compareTo(@NotNull FeeDescriptor other) {
            return Double.compare(threshold, other.threshold);
        }
    }

    public static final class Tests {
        private Mode mode;
        private String testPool;

        @NotNull
        public Mode getMode() {
            if (mode == null) throw new MalformedConfigurationException("tests.mode", "must not be null");
            return mode;
        }

        @NotNull
        public Account getTestPool() {
            if (testPool == null || testPool.isEmpty())
                throw new MalformedConfigurationException("tests.testPool", "must not be null nor empty");
            return new Account(testPool, null);
        }

        public enum Mode {
            PRODUCTION,
            TEST_POOL,
            MAINNET_PAYMENT
        }
    }

    public static final class SafetyThresholds {
        private double rewardsExceedPrize;
        private double rewardExceedsAmount;
        private double rewardExceedsPercentOfPrize;

        @NotNull
        public StellarCurrency getRewardsExceedPrize() {
            return StellarCurrency.Companion.ofLumens(rewardsExceedPrize);
        }

        @NotNull
        public StellarCurrency getRewardExceedsAmount() {
            return StellarCurrency.Companion.ofLumens(rewardExceedsAmount);
        }

        @NotNull
        public Percent getRewardExceedsPercentOfPrize() {
            return new Percent(rewardExceedsPercentOfPrize);
        }
    }
}