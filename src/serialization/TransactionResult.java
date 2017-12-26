package it.menzani.stellarpool.serialization;

public final class TransactionResult {
    private String status;
    private String exception;

    public String getStatus() {
        return status;
    }

    public String getException() {
        return exception;
    }
}