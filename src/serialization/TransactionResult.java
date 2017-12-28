package it.menzani.stellarpool.serialization;

import com.google.gson.Gson;

public final class TransactionResult {
    private static final Gson gson = new Gson();

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
        return gson.toJson(this);
    }
}