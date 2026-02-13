package io.github.iaorekhov.jiraclient.dto;

import java.util.ArrayList;
import java.util.List;

public class ReportEntry {

    private String sourceKey;
    private String sourceSummary;
    private String cloneSummary;
    private String cloneKey; // null в dry-run
    private String status;   // planned|created|failed
    private List<String> warnings = new ArrayList<>();
    private String error;

    // NEW: кто назначен (отображаем либо username, либо accountId, что использовали)
    private String assignee;

// NEW: причина назначения (например: "component match: 'ArchitectSupernova'" или "no matching component → reporter")
    private String assignmentReason;

// GET/SET для новых полей
    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getAssignmentReason() {
        return assignmentReason;
    }

    public void setAssignmentReason(String assignmentReason) {
        this.assignmentReason = assignmentReason;
    }

    // Конструкторы
    public ReportEntry() {
    }

    public ReportEntry(String sourceKey, String sourceSummary, String cloneSummary) {
        this.sourceKey = sourceKey;
        this.sourceSummary = sourceSummary;
        this.cloneSummary = cloneSummary;
    }

    // Геттеры и сеттеры
    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getSourceSummary() {
        return sourceSummary;
    }

    public void setSourceSummary(String sourceSummary) {
        this.sourceSummary = sourceSummary;
    }

    public String getCloneSummary() {
        return cloneSummary;
    }

    public void setCloneSummary(String cloneSummary) {
        this.cloneSummary = cloneSummary;
    }

    public String getCloneKey() {
        return cloneKey;
    }

    public void setCloneKey(String cloneKey) {
        this.cloneKey = cloneKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
