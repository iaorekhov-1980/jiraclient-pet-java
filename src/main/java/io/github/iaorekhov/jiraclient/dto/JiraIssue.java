package io.github.iaorekhov.jiraclient.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JiraIssue {
    private String key;
    private Map<String, Object> fields;
    
    // Конструкторы
    public JiraIssue() {}
    
    public JiraIssue(String key, Map<String, Object> fields) {
        this.key = key;
        this.fields = fields;
    }
    
    // Статический метод для создания из Map (из ответа JIRA API)
    public static JiraIssue fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        JiraIssue issue = new JiraIssue();
        issue.key = (String) map.get("key");
        
        // Безопасное приведение типа для fields
        Object fieldsObj = map.get("fields");
        if (fieldsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldsMap = (Map<String, Object>) fieldsObj;
            issue.fields = fieldsMap;
        } else {
            issue.fields = Map.of();
        }
        
        return issue;
    }
    
    // Методы для безопасного доступа к полям
    
    public String getSummary() {
        if (fields == null) {
            return null;
        }
        Object summary = fields.get("summary");
        return summary != null ? String.valueOf(summary) : null;
    }
    
    public List<Component> getComponents() {
        if (fields == null) {
            return new ArrayList<>();
        }
        
        Object comps = fields.get("components");
        if (comps instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> componentsList = (List<Map<String, Object>>) comps;
            
            return componentsList.stream()
                .map(Component::fromMap)
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
    
    public Priority getPriority() {
        if (fields == null) {
            return null;
        }
        
        Object priority = fields.get("priority");
        if (priority instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> priorityMap = (Map<String, Object>) priority;
            return Priority.fromMap(priorityMap);
        }
        
        return null;
    }
    
    public String getIssueType() {
        if (fields == null) {
            return null;
        }
        
        Object issueType = fields.get("issuetype");
        if (issueType instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typeMap = (Map<String, Object>) issueType;
            Object name = typeMap.get("name");
            return name != null ? String.valueOf(name) : null;
        }
        
        return null;
    }
    
    public String getProjectKey() {
        if (fields == null) {
            return null;
        }
        
        Object project = fields.get("project");
        if (project instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> projectMap = (Map<String, Object>) project;
            Object key = projectMap.get("key");
            return key != null ? String.valueOf(key) : null;
        }
        
        return null;
    }
    
    // Геттеры и сеттеры
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public Map<String, Object> getFields() {
        return fields;
    }
    
    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
    
    // Вспомогательные методы
    public boolean hasComponents() {
        return !getComponents().isEmpty();
    }
    
    public boolean hasPriority() {
        return getPriority() != null;
    }
}