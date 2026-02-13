package io.github.iaorekhov.jiraclient.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.iaorekhov.jiraclient.JiraClient;
import io.github.iaorekhov.jiraclient.config.Config;
import io.github.iaorekhov.jiraclient.dto.JiraIssue;
import io.github.iaorekhov.jiraclient.dto.Priority;
import io.github.iaorekhov.jiraclient.dto.ReportEntry;

/**
 * Сервис для клонирования задач JIRA из одного эпика в другой. Содержит
 * основную бизнес-логику приложения.
 */
public class JiraCloningService {

    private final Config config;
    private final JiraClient jiraClient;
    private String epicLinkFieldId;

    // Константы для JQL запросов
    private static final String DEFAULT_ISSUE_FIELDS = "summary,components,priority,issuetype,project";
    private static final int DEFAULT_MAX_RESULTS = 500;
    private static final String DEFAULT_LINK_TYPE = "Cloners";

    public JiraCloningService(Config config, JiraClient jiraClient) {
        this.config = config;
        this.jiraClient = jiraClient;
    }

    /**
     * Основной метод клонирования задач
     */
    public List<ReportEntry> cloneIssues() {
        // Инициализация - получаем ID поля Epic Link
        initializeEpicLinkField();

        // Получаем исходные задачи
        List<JiraIssue> sourceIssues = getSourceIssues();

        // Клонируем каждую задачу
        return sourceIssues.stream()
                .map(this::cloneSingleIssue)
                .collect(Collectors.toList());
    }

    /**
     * Инициализация ID поля Epic Link
     */
    private void initializeEpicLinkField() {
        try {
            this.epicLinkFieldId = jiraClient.findEpicLinkFieldId();
            System.out.println("Epic Link ID found [" + this.epicLinkFieldId + "]");
            if (epicLinkFieldId == null) {
                throw new RuntimeException("Epic Link field not found in JIRA instance");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Epic Link field ID: " + e.getMessage(), e);
        }
    }

    /**
     * Получение списка исходных задач для клонирования
     */
    private List<JiraIssue> getSourceIssues() {
        // Если указан singleIssueKey - клонируем только одну задачу
        if (config.operation.singleIssueKey != null
                && !config.operation.singleIssueKey.trim().isEmpty()) {

            try {
                Map<String, Object> issueData = jiraClient.getIssue(
                        config.operation.singleIssueKey,
                        DEFAULT_ISSUE_FIELDS
                );
                JiraIssue issue = JiraIssue.fromMap(issueData);
                return List.of(issue);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to fetch single issue: " + config.operation.singleIssueKey,
                        e
                );
            }
        }

        // Иначе - все задачи из исходного эпика
        String jql = String.format(
                "issuetype = %s AND 'Epic Link' = %s",
                config.operation.issueTypeName,
                config.operation.sourceEpicKey
        );

        try {
            List<Map<String, Object>> issuesData = jiraClient.searchJql(
                    jql,
                    List.of(DEFAULT_ISSUE_FIELDS.split(",")),
                    DEFAULT_MAX_RESULTS
            );

            return issuesData.stream()
                    .map(JiraIssue::fromMap)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to search issues in epic: " + config.operation.sourceEpicKey,
                    e
            );
        }
    }

    /**
     * Клонирование одной задачи
     */
    private ReportEntry cloneSingleIssue(JiraIssue sourceIssue) {
        ReportEntry reportEntry = new ReportEntry();
        reportEntry.setSourceKey(sourceIssue.getKey());
        reportEntry.setSourceSummary(sourceIssue.getSummary());
        
        String summaryPrefix = config.operation.summaryPrefix != null ? config.operation.summaryPrefix : "";
        String cloneSummary = summaryPrefix + sourceIssue.getSummary();
        reportEntry.setCloneSummary(cloneSummary);
        
        // NEW: выбираем assignee и отражаем в отчёте и логах
        Map<String, String> assigneeRef = decideAssigneeAndAnnotate(sourceIssue, reportEntry);
        
        if (config.operation.dryRun) {
            reportEntry.setStatus("planned");
            return reportEntry;
        }
        
        try {
            Map<String, Object> createFields = buildCreateFields(sourceIssue, cloneSummary, assigneeRef); // CHANGED: добавлен assigneeRef
            System.out.println("[Create] fields = " +
                new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(java.util.Map.of("fields", createFields)));
        
            String cloneKey = jiraClient.createIssue(createFields);
            System.out.println("[Create] issue created: " + cloneKey);
        
            reportEntry.setCloneKey(cloneKey);
            reportEntry.setStatus("created");
        
            addIssueLinks(sourceIssue.getKey(), cloneKey);
        
        } catch (Exception e) {
            reportEntry.setStatus("failed");
            reportEntry.setError(e.getMessage());
        }
        
        return reportEntry;
        }
    

    /**
     * Формирование полей для создания задачи
     */

    private Map<String, Object> buildCreateFields(JiraIssue sourceIssue, String cloneSummary, Map<String, String> assigneeRef) {
        Map<String, Object> fields = new LinkedHashMap<>();
        
        fields.put("project", Map.of("key", config.operation.projectKey));
        fields.put("issuetype", Map.of("name", config.operation.issueTypeName));
        fields.put("summary", cloneSummary);
        
        if (config.operation.description != null && !config.operation.description.isBlank()) {
            fields.put("description", config.operation.description);
        }
        
        if (epicLinkFieldId != null) {
            fields.put(epicLinkFieldId, config.operation.targetEpicKey);
        }
        
        List<io.github.iaorekhov.jiraclient.dto.Component> components = sourceIssue.getComponents();
        if (!components.isEmpty()) {
            List<Map<String, String>> componentRefs = components.stream()
                .map(c -> Map.of("id", c.getId()))
                .collect(Collectors.toList());
            fields.put("components", componentRefs);
        }
        
        Priority priority = sourceIssue.getPriority();
        if (priority != null && priority.getId() != null) {
            fields.put("priority", Map.of("id", priority.getId()));
        }
        
        if (config.operation.reporter != null) {
            if (config.operation.reporter.accountId != null && !config.operation.reporter.accountId.isBlank()) {
                fields.put("reporter", Map.of("accountId", config.operation.reporter.accountId));
            } else if (config.operation.reporter.username != null && !config.operation.reporter.username.isBlank()) {
                fields.put("reporter", Map.of("name", config.operation.reporter.username));
            }
        }
        
        if (!fields.containsKey("reporter")) {
            throw new IllegalStateException("Reporter is required but not provided in config (accountId/username)");
        }
        
        // NEW: assignee — обязателен по логике (выбран из architect или reporter)
        if (assigneeRef == null || assigneeRef.isEmpty()) {
            throw new IllegalStateException("Assignee is empty (architect/reporter misconfigured)");
        }
        fields.put("assignee", assigneeRef);
        
        return fields;
        }

    /**
     * Добавление связей между исходной задачей и клоном
     */
    private void addIssueLinks(String sourceKey, String cloneKey) {
        // Линк Cloners
        String linkType = DEFAULT_LINK_TYPE;
        if (config.operation.linkToOriginal != null
                && config.operation.linkToOriginal.typeName != null
                && !config.operation.linkToOriginal.typeName.isBlank()) {
            linkType = config.operation.linkToOriginal.typeName;
        }

        try {
            jiraClient.linkCloners(sourceKey, cloneKey, linkType);
            System.out.println("[Link] Cloners: " + sourceKey + " -> " + cloneKey);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Remote link (Confluence)
        if (config.operation.remoteLink != null
                && config.operation.remoteLink.url != null
                && !config.operation.remoteLink.url.isBlank()
                && config.operation.remoteLink.title != null
                && !config.operation.remoteLink.title.isBlank()) {

            String relationship = config.operation.remoteLink.relationship;
            try {
                jiraClient.addRemoteLinkConfluence(
                        cloneKey,
                        config.operation.remoteLink.url,
                        config.operation.remoteLink.title,
                        relationship
                );
                System.out.println("[RemoteLink] added to " + cloneKey + ": " + config.operation.remoteLink.title);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * Получение статистики по результатам клонирования
     */
    public Map<String, Object> getStatistics(List<ReportEntry> results) {
        Map<String, Object> stats = new HashMap<>();

        long total = results.size();
        long planned = results.stream().filter(r -> "planned".equals(r.getStatus())).count();
        long created = results.stream().filter(r -> "created".equals(r.getStatus())).count();
        long failed = results.stream().filter(r -> "failed".equals(r.getStatus())).count();

        stats.put("total", total);
        stats.put("planned", planned);
        stats.put("created", created);
        stats.put("failed", failed);
        stats.put("dryRun", config.operation.dryRun);

        return stats;
    }

    // NEW: точная проверка наличия компонента с заданным именем

    private boolean hasComponentByName(JiraIssue issue, String componentName) {
        if (componentName == null) {
            return false;
        }
        if (componentName.isEmpty()) {
            return false; // пустая строка — никогда не совпадёт

                }for (io.github.iaorekhov.jiraclient.dto.Component c : issue.getComponents()) {
            if (componentName.equals(c.getName())) { // строгое сравнение
                return true;
            }
        }
        return false;
    }

// NEW: формирование ссылки на пользователя для Jira DC: сначала accountId, иначе name
    private Map<String, String> toUserRef(io.github.iaorekhov.jiraclient.config.Config.Reporter person) {
        if (person == null) {
            return java.util.Map.of(); // на валидации не допустим, но страховка

                }if (person.accountId != null && !person.accountId.isBlank()) {
            return java.util.Map.of("accountId", person.accountId);
        }
        if (person.username != null && !person.username.isBlank()) {
            return java.util.Map.of("name", person.username);
        }
        return java.util.Map.of(); // пустая — пусть упадёт на createIssue
    }

// NEW: человек в человекочитаемом виде для отчёта/лога
    private String userLabel(io.github.iaorekhov.jiraclient.config.Config.Reporter person) {
        if (person == null) {
            return "";
        }
        if (person.username != null && !person.username.isBlank()) {
            return person.username;
        }
        if (person.accountId != null && !person.accountId.isBlank()) {
            return "accountId:" + person.accountId;
        }
        return "";
    }

// NEW: выбор assignee и аннотация ReportEntry
    private Map<String, String> decideAssigneeAndAnnotate(JiraIssue sourceIssue, ReportEntry reportEntry) {
        boolean match = hasComponentByName(sourceIssue, config.operation.architectComponent);
        Map<String, String> assigneeRef;
        String label;
        String reason;

        if (match) {
            assigneeRef = toUserRef(config.operation.architect);
            label = userLabel(config.operation.architect);
            reason = "component match: '" + config.operation.architectComponent + "'";
            System.out.println("[Assignee] using architect: " + label + " (" + reason + ")");
        } else {
            assigneeRef = toUserRef(config.operation.reporter);
            label = userLabel(config.operation.reporter);
            reason = "no matching component → reporter";
            System.out.println("[Assignee] using reporter: " + label + " (" + reason + ")");
        }

        reportEntry.setAssignee(label);
        reportEntry.setAssignmentReason(reason);
        return assigneeRef;
    }
}
