package io.github.iaorekhov.jiraclient.config;

public class ConfigValidationException extends RuntimeException {

    private final java.util.List errors;

    public ConfigValidationException(java.util.List<String> errors) {
        super(buildMessage(errors));
        this.errors = errors == null ? java.util.List.of() : java.util.List.copyOf(errors);
    }

    public java.util.List<String> getErrors() {
        return java.util.Collections.unmodifiableList(errors);
    }

    private static String buildMessage(java.util.List<String> errors) {
        StringBuilder sb = new StringBuilder("Configuration validation failed (")
                .append(errors == null ? 0 : errors.size())
                .append(" errors):");
        if (errors != null) {
            for (String e : errors) {
                sb.append(System.lineSeparator()).append(" - ").append(e);
            }
        }
        return sb.toString();
    }
}