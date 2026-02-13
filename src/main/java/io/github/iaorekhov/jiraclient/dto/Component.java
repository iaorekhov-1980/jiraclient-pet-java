package io.github.iaorekhov.jiraclient.dto;

import java.util.Map;

public class Component {
    private String id;
    private String name;
    
    public Component() {}
    
    public Component(String id, String name) {
        this.id = id;
        this.name = name;
    }
    
    // Статический метод для создания из Map (из ответа JIRA API)
    public static Component fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        Component component = new Component();
        
        // JIRA API может возвращать id как строку или число
        Object idObj = map.get("id");
        if (idObj != null) {
            component.id = String.valueOf(idObj);
        }
        
        Object nameObj = map.get("name");
        if (nameObj != null) {
            component.name = String.valueOf(nameObj);
        }
        
        return component;
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