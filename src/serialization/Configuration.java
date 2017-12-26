package it.menzani.stellarpool.serialization;

import it.menzani.stellarpool.Account;
import it.menzani.stellarpool.Percent;
import it.menzani.stellarpool.StellarCurrency;
import org.jetbrains.annotations.NotNull;

public final class Configuration {
    private String pool;
    private String bank;
    private double fee;
    private String feeCollector;
    private boolean noop;
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
    public Percent getFee() {
        return new Percent(fee);
    }

    @NotNull
    public Account getFeeCollector() {
        assert feeCollector != null && !feeCollector.isEmpty() : "feeCollector field in configuration must not be null nor empty";
        return new Account(feeCollector, null);
    }

    public boolean shouldNotExecutePayments() {
        return noop || getTests().getMode() == Tests.Mode.TEST_POOL;
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