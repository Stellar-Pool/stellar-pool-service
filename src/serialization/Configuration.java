package it.menzani.stellarpool.serialization;

import it.menzani.stellarpool.Account;
import it.menzani.stellarpool.Percent;
import it.menzani.stellarpool.StellarCurrency;
import org.jetbrains.annotations.NotNull;

public final class Configuration {
    private String pool;
    private String bank;
    private Messages messages;
    private FeeDescriptor[] feeSchedule;
    private String feeCollector;
    private Tests tests;
    private SafetyThresholds safetyThresholds;

    @NotNull
    public Account getPool() {
        assert pool != null && !pool.isEmpty() : "pool field in configuration must not be null nor empty";
        return new Account(pool, null);
    }

    @NotNull
    public String getBank() {
        assert bank != null && !bank.isEmpty() : "bank field in configuration must not be null nor empty";
        return bank;
    }

    @NotNull
    public Messages getMessages() {
        assert messages != null : "messages field in configuration must not be null";
        return messages;
    }

    @NotNull
    public FeeDescriptor[] getFeeSchedule() {
        assert feeSchedule != null && feeSchedule.length != 0 :
                "feeSchedule field in configuration must not be null and it must contain at least one element";
        return feeSchedule;
    }

    @NotNull
    public Account getFeeCollector() {
        assert feeCollector != null && !feeCollector.isEmpty() : "feeCollector field in configuration must not be null nor empty";
        return new Account(feeCollector, null);
    }

    @NotNull
    public Tests getTests() {
        assert tests != null : "tests field in configuration must not be null";
        return tests;
    }

    @NotNull
    public SafetyThresholds getSafetyThresholds() {
        assert safetyThresholds != null : "safetyThresholds field in configuration must not be null";
        return safetyThresholds;
    }

    public static final class Messages {
        private String distribution;
        private String inflation;

        @NotNull
        public String getDistribution() {
            assert distribution != null && !distribution.isEmpty() && distribution.getBytes().length <= 28 :
                    "messages.distribution field in configuration must not be null, nor empty, nor greater than 28 bytes in size";
            return distribution;
        }

        @NotNull
        public String getInflation() {
            assert inflation != null && !inflation.isEmpty() && inflation.getBytes().length <= 28 :
                    "messages.inflation field in configuration must not be null, nor empty, nor greater than 28 bytes in size";
            return inflation;
        }
    }

    public static final class FeeDescriptor {
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
    }

    public static final class Tests {
        private Mode mode;
        private String testPool;

        @NotNull
        public Mode getMode() {
            assert mode != null : "tests.mode field in configuration must not be null";
            return mode;
        }

        @NotNull
        public Account getTestPool() {
            assert testPool != null && !testPool.isEmpty() : "tests.testPool field in configuration must not be null nor empty";
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