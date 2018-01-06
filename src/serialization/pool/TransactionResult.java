package it.menzani.stellarpool.serialization.pool;

import it.menzani.stellarpool.serialization.ResourceKt;

public final class TransactionResult {
    private String status;
    private String error;
    private String exception;

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getException() {
        return exception;
    }

    @Override
    public String toString() {
        return ResourceKt.createJson(this);
    }
}