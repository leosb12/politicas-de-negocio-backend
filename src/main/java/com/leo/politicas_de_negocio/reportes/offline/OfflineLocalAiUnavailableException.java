package com.leo.politicas_de_negocio.reportes.offline;

public class OfflineLocalAiUnavailableException extends RuntimeException {
    public OfflineLocalAiUnavailableException(String message) {
        super(message);
    }
    public OfflineLocalAiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
