package io.github.iaorekhov.jiraclient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.iaorekhov.jiraclient.config.Config;
import io.github.iaorekhov.jiraclient.config.ConfigValidationException;
import io.github.iaorekhov.jiraclient.config.ConfigValidator;
import io.github.iaorekhov.jiraclient.dto.ReportEntry;
import io.github.iaorekhov.jiraclient.service.JiraCloningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);


    public static void main(String[] args) {
        try {
            // 1. Парсинг пути к конфигурационному файлу
            String configPath = parseConfigPath(args);
            log.debug("Loading config from: {} ", configPath);
            log.debug("Working directory: {}", Paths.get(".").toAbsolutePath().normalize());
            
            // 2. Загрузка конфигурации
            Config config = loadConfig(configPath);
            
            // 3. Валидация конфигурации
            validateConfig(config);
            
            // 4. Инициализация JIRA клиента
            JiraClient jiraClient = initializeJiraClient(config);
            
            // 5. Клонирование задач
            JiraCloningService cloningService = new JiraCloningService(config, jiraClient);
            List<ReportEntry> results = cloningService.cloneIssues();
            
            // 6. Вывод статистики
            printStatistics(cloningService.getStatistics(results));
            
            // 7. Сохранение отчета (временно здесь)
            saveReport(config, results);
            
            log.info("Cloning completed successfully!");
            
        } catch (ConfigValidationException e) {
            log.error(e.getMessage(), e);
            System.exit(2);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
    
    /**
     * Парсинг пути к конфигурационному файлу из аргументов командной строки
     * Поддерживает:
     * - --config path/to/config.json
     * - -Dconfig=path/to/config.json (системное свойство)
     */
    private static String parseConfigPath(String[] args) {
        // 1. Сначала проверяем системное свойство -Dconfig
        String configPath = System.getProperty("config");
        if (configPath != null && !configPath.trim().isEmpty()) {
            return configPath;
        }
        
        // 2. Затем проверяем аргументы --config
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        
        // 3. По умолчанию
        return "config.json";
    }
    
    /**
     * Загрузка конфигурации из JSON файла
     */
    private static Config loadConfig(String path) throws Exception {
        File configFile = new File(path);
        if (!configFile.exists()) {
            throw new ConfigValidationException(
                List.of("Config file not found: " + configFile.getAbsolutePath())
            );
        }
        
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configFile, new TypeReference<Config>(){});
    }
    
    /**
     * Валидация загруженной конфигурации
     */
    private static void validateConfig(Config config) {
        ConfigValidator validator = new ConfigValidator();
        validator.validateAndNormalize(config);
        log.info("Config validation passed!");
    }
    
    /**
     * Инициализация JIRA клиента и проверка подключения
     */
    private static JiraClient initializeJiraClient(Config config) {
        // Проверка типа аутентификации
        if (!"bearer".equalsIgnoreCase(config.jira.auth)) {
            throw new IllegalArgumentException(
                "Only bearer auth supported for Jira DC in this app, got: " + config.jira.auth
            );
        }
        
        JiraClient jiraClient = new JiraClient(config.jira.baseUrl, config.jira.token);
        
        // Проверка подключения
        try {
            jiraClient.getMyself();
            log.info("JIRA connection successful");
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to JIRA: " + e.getMessage(), e);
        }
        
        return jiraClient;
    }
    
    /**
     * Вывод статистики клонирования в консоль
     */
    private static void printStatistics(Map<String, Object> stats) {
        log.info("=== Cloning Statistics ===");
        stats.forEach((key, value) -> log.info("{}: {}", key, value));
        log.info("==========================");
    }
    
    /**
     * Временное сохранение отчета в JSON файл
     * TODO: Вынести в отдельный ReportService
     */
    private static void saveReport(Config config, List<ReportEntry> entries) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String filename = String.format("clone-report-%s-%s.json", 
                config.operation.targetEpicKey, timestamp);
            
            Map<String, Object> report = Map.of(
                "timestamp", timestamp,
                "sourceEpic", config.operation.sourceEpicKey,
                "targetEpic", config.operation.targetEpicKey,
                "projectKey", config.operation.projectKey,
                "dryRun", config.operation.dryRun,
                "count", entries.size(),
                "items", entries
            );
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filename), report);
            
            log.info("Report saved: {}", filename);
            
        } catch (Exception e) {
            log.error("Failed to save report: {}", e.getMessage(), e);
        }
    }

}
