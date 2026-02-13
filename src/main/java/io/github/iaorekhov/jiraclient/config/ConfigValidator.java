package io.github.iaorekhov.jiraclient.config;

public final class ConfigValidator {

    private static final java.util.regex.Pattern PROJECT_RE = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9]+$");
    private static final java.util.regex.Pattern ISSUE_RE = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9]+-\\d+$");

    public void validateAndNormalize(Config c) {
        java.util.List<String> errors = new java.util.ArrayList<>();

        if (c == null) {
            errors.add("config is null");
            throw new ConfigValidationException(errors);
        }

        // jira
        if (c.jira == null) {
            errors.add("jira is missing");
        } else {
            validateJira(c.jira, errors);
        }

        // operation
        if (c.operation == null) {
            errors.add("operation is missing");
        } else {
            validateOperation(c.operation, errors);
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(errors);
        }

        // Нормализация — только после успешной валидации
        normalize(c);
    }

    private void validateJira(Config.Jira j, java.util.List<String> errors) {
        if (isBlank(j.baseUrl)) {
            errors.add("jira.baseUrl is missing");
        } else if (!isValidHttpUrl(j.baseUrl)) {
            errors.add("jira.baseUrl is invalid: '" + j.baseUrl + "'");
        }

        if (isBlank(j.auth)) {
            errors.add("jira.auth is missing (must be 'bearer')");
        } else if (!"bearer".equals(j.auth.toLowerCase(java.util.Locale.ROOT))) {
            errors.add("jira.auth must be 'bearer', got: '" + j.auth + "'");
        }

        if (isBlank(j.token)) {
            errors.add("jira.token is missing");
        }
    }

    private void validateOperation(Config.Operation o, java.util.List<String> errors) {
        if (isBlank(o.projectKey)) {
            errors.add("operation.projectKey is missing");
        } else if (!PROJECT_RE.matcher(o.projectKey).matches()) {
            errors.add("operation.projectKey is invalid: '" + o.projectKey + "'");
        }

        if (isBlank(o.sourceEpicKey)) {
            errors.add("operation.sourceEpicKey is missing");
        } else if (!ISSUE_RE.matcher(o.sourceEpicKey).matches()) {
            errors.add("operation.sourceEpicKey is invalid: '" + o.sourceEpicKey + "'");
        }

        if (isBlank(o.targetEpicKey)) {
            errors.add("operation.targetEpicKey is missing");
        } else if (!ISSUE_RE.matcher(o.targetEpicKey).matches()) {
            errors.add("operation.targetEpicKey is invalid: '" + o.targetEpicKey + "'");
        }

        if (!isBlank(o.singleIssueKey) && !ISSUE_RE.matcher(o.singleIssueKey).matches()) {
            errors.add("operation.singleIssueKey is invalid: '" + o.singleIssueKey + "'");
        }

        if (isBlank(o.issueTypeName)) {
            errors.add("operation.issueTypeName is missing");
        }
        if (isBlank(o.summaryPrefix)) {
            errors.add("operation.summaryPrefix is missing");
        }
        if (isBlank(o.description)) {
            errors.add("operation.description is missing");
        }

        if (o.remoteLink == null) {
            errors.add("operation.remoteLink is missing");
        } else {
            if (isBlank(o.remoteLink.url)) {
                errors.add("operation.remoteLink.url is missing");
            } else if (!isValidHttpUrl(o.remoteLink.url)) {
                errors.add("operation.remoteLink.url is invalid: '" + o.remoteLink.url + "'");
            }
            if (isBlank(o.remoteLink.title)) {
                errors.add("operation.remoteLink.title is missing");
            }
            // relationship — опционально, допускаем пустым
        }

        if (o.reporter == null) {
            errors.add("operation.reporter is missing");
        } else {
            boolean hasAccountId = !isBlank(o.reporter.accountId);
            boolean hasUsername = !isBlank(o.reporter.username);
            if (!hasAccountId && !hasUsername) {
                errors.add("operation.reporter: at least one of username or accountId must be provided");
            }
        }

        if (o.linkToOriginal != null && isBlank(o.linkToOriginal.typeName)) {
            errors.add("operation.linkToOriginal.typeName is empty");
        }
        // o.dryRun — примитив boolean, уже по умолчанию true

        if (o.architect == null) {
            errors.add("operation.architect is missing");
        } else {
            boolean hasAccountId = !isBlank(o.architect.accountId);
            boolean hasUsername = !isBlank(o.architect.username);
            if (!hasAccountId && !hasUsername) {
                errors.add("operation.architect: at least one of username or accountId must be provided");
            }
        }
    }

    private void normalize(Config c) {
        // baseUrl: убрать завершающие /, auth в lower-case
        if (c.jira != null && c.jira.baseUrl != null) {
            c.jira.baseUrl = c.jira.baseUrl.replaceAll("/+$", "");
        }
        if (c.jira != null && c.jira.auth != null) {
            c.jira.auth = c.jira.auth.toLowerCase(java.util.Locale.ROOT);
        }

        // linkToOriginal.typeName по умолчанию "Cloners"
        if (c.operation != null) {
            if (c.operation.linkToOriginal == null) {
                c.operation.linkToOriginal = new Config.LinkToOriginal();
            }
            if (isBlank(c.operation.linkToOriginal.typeName)) {
                c.operation.linkToOriginal.typeName = "Cloners";
            }
            if (c.operation.architectComponent == null) {
                c.operation.architectComponent = "";
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isValidHttpUrl(String s) {
        try {
            java.net.URI u = java.net.URI.create(s);
            if (u.getScheme() == null) {
                return false;
            }
            String scheme = u.getScheme().toLowerCase(java.util.Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && u.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }
}