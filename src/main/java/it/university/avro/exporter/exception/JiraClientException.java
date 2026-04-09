package it.university.avro.exporter.exception;

public final class JiraClientException extends RuntimeException {

    public JiraClientException(final String message) {
        super(message);
    }

    public JiraClientException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
