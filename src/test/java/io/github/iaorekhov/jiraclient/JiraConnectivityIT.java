package io.github.iaorekhov.jiraclient;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.iaorekhov.jiraclient.config.Config;
import io.github.iaorekhov.jiraclient.config.ConfigValidator;

public class JiraConnectivityIT {

    private static JiraClient client;
    private static Config cfg;

    @BeforeAll
    static void init() throws Exception {
        // 1) Путь к конфигу: сначала -Dconfig, затем env JIRA_TEST_CONFIG
        String sys = System.getProperty("config");
        String env = System.getenv("JIRA_TEST_CONFIG");
        String path = (sys != null && !sys.isBlank()) ? sys : env;

        System.out.println("[INFO] Using test config: " + path);

        assumeTrue(path != null && !path.isBlank(),
                "Provide config path via -Dconfig=<path> or env JIRA_TEST_CONFIG");

        Path p = Path.of(path);
        
        System.out.println("[INFO] Using test config: " + p);

        assumeTrue(Files.isRegularFile(p) && Files.isReadable(p),
                "Test config not found or not readable: " + p.toAbsolutePath());

        System.out.println("[INFO] Using test config: " + p.toAbsolutePath());

        // 2) Читаем как App.Config (тот же формат, что и у приложения)
        ObjectMapper om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        cfg = om.readValue(new File(path), Config.class);

        // 3) Валидируем и нормализуем тем же валидатором, что и приложение
        //    Если конфиг некорректен — пусть инициализация завалится сразу.
        new ConfigValidator().validateAndNormalize(cfg);

        // 4) Инициализация JiraClient (Bearer PAT)
        client = new JiraClient(cfg.jira.baseUrl, cfg.jira.token);
    }

    @Test
    @DisplayName("GET /myself returns 200 and basic fields")
    void testMyself(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Map<String, Object> me = client.getMyself();
        assertNotNull(me, "myself is null");
        // Для Jira DC обычно присутствуют поля key и name
        assertTrue(me.containsKey("key") || me.containsKey("name"),
                "Expected 'key' or 'name' in /myself payload");
    }

    @Test
    @DisplayName("Epic Link field is present in /field list")
    void testEpicLinkFieldPresent(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        String epicFieldId = client.findEpicLinkFieldId();
        assertNotNull(epicFieldId, "Epic Link field id not found");
        assertTrue(epicFieldId.startsWith("customfield_"), "Unexpected Epic Link id: " + epicFieldId);
    }

    @Test
    @DisplayName("Search tasks by Epic returns list (0..N)")
    void testSearchTasksInEpic(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        String jql = "issuetype = Task AND 'Epic Link' = " + cfg.operation.sourceEpicKey;
        List<Map<String, Object>> issues = client.searchJql(jql, List.of("summary"), 100);
        assertNotNull(issues, "Issues list is null");
        // допустим 0..N — просто проверим структуру
        for (Map<String, Object> it : issues) {
            assertTrue(it.containsKey("key"), "Issue without key");
        }
    }

    @Test
    @DisplayName("Invalid token leads to 401 Unauthorized")
    void testInvalidTokenUnauthorized(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        JiraClient bad = new JiraClient(cfg.jira.baseUrl, "INVALID_TOKEN");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try {
                bad.getMyself();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        String msg = ex.getMessage();
        assertTrue(msg.contains("401") || msg.contains("Unauthorized"),
                "Expected 401, got: " + msg);
    }
}
