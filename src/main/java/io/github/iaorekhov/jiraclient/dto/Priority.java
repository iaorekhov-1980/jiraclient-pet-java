package io.github.iaorekhov.jiraclient.dto;

import java.util.Map;

public class Priority {
    private String id;
    private String name;
    
    public Priority() {}
    
    public Priority(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    // Статический метод для создания из Map (из ответа JIRA API)
    public static Priority fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        Priority priority = new Priority();
        
        Object idObj = map.get("id");
        if (idObj != null) {
            priority.id = String.valueOf(idObj);
        }
        
        Object nameObj = map.get("name");
        if (nameObj != null) {
            priority.name = String.valueOf(nameObj);
        }
        
        return priority;
    }
    
    // Геттеры и сеттеры
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}