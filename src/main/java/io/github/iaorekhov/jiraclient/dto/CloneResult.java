package io.github.iaorekhov.jiraclient.dto;

public class CloneResult {
    private boolean success;
    private String clonedIssueKey;
    private String errorMessage;
    private JiraIssue sourceIssue;
    private JiraIssue clonedIssue;
    
    public CloneResult() {}
    
    public CloneResult(boolean success, String clonedIssueKey) {
        this.success = success;
        this.clonedIssueKey = clonedIssueKey;
    }
    
    public static CloneResult success(String clonedIssueKey, JiraIssue sourceIssue, JiraIssue clonedIssue) {
        CloneResult result = new CloneResult(true, clonedIssueKey);
        result.sourceIssue = sourceIssue;
        result.clonedIssue = clonedIssue;
        return result;
    }
    
    public static CloneResult failure(String errorMessage, JiraIssue sourceIssue) {
        CloneResult result = new CloneResult();
        result.success = false;
        result.errorMessage = errorMessage;
        result.sourceIssue = sourceIssue;
        return result;
    }
    
    // Геттеры и сеттеры
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getClonedIssueKey() {
        return clonedIssueKey;
    }
    
    public void setClonedIssueKey(String clonedIssueKey) {
        this.clonedIssueKey = clonedIssueKey;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public JiraIssue getSourceIssue() {
        return sourceIssue;
    }
    
    public void setSourceIssue(JiraIssue sourceIssue) {
        this.sourceIssue = sourceIssue;
    }
    
    public JiraIssue getClonedIssue() {
        return clonedIssue;
    }
    
    public void setClonedIssue(JiraIssue clonedIssue) {
        this.clonedIssue = clonedIssue;
    }
}