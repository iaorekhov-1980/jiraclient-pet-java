package io.github.iaorekhov.jiraclient.config;

public class Config {

    public Jira jira;
    public Operation operation;

    public static class Jira {

        public String baseUrl;
        public String auth;
        public String token;
    }

    public static class Operation {

        public String projectKey;
        public String sourceEpicKey;
        public String targetEpicKey;
        public String issueTypeName;
        public String summaryPrefix;
        public String description;
        public RemoteLink remoteLink;
        public Reporter reporter;
        public LinkToOriginal linkToOriginal;
        public boolean dryRun = true;
        public String singleIssueKey; // если задан — обрабатываем только его

        // NEW: архитектор обязателен (аналогично reporter)
        public Reporter architect;

        // NEW: опционален, по умолчанию пустая строка
        public String architectComponent = "";
    }

    public static class RemoteLink {

        public String url;
        public String title;
        public String relationship;
    }

    public static class Reporter {

        public String accountId;
        public String username;
    }

    public static class LinkToOriginal {

        public String typeName;
    }
}
