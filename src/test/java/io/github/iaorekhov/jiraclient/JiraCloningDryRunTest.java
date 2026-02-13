package io.github.iaorekhov.jiraclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.iaorekhov.jiraclient.config.Config;
import io.github.iaorekhov.jiraclient.dto.ReportEntry;
import io.github.iaorekhov.jiraclient.service.JiraCloningService;

class JiraCloningServiceDryRunTest {

    private Config config;
    private JiraCloningService service;
    private TestJiraClient testJiraClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EPIC_LINK_FIELD_ID = "customfield_10001";

    /**
     * Тестовый клиент JIRA - полностью контролируемая реализация
     */
    static class TestJiraClient extends JiraClient {

        private final Map<String, Object> mockIssues = new HashMap<>();
        private List<Map<String, Object>> mockSearchResults = new ArrayList<>();
        private String createdIssueKey;
        private Map<String, Object> createdIssueFields;
        private List<LinkCall> linkCalls = new ArrayList<>();
        private List<RemoteLinkCall> remoteLinkCalls = new ArrayList<>();
        private boolean epicLinkFieldRequested = false;

        public TestJiraClient() {
            super("https://test.jira.com", "test-token");
        }

        @Override
        public String findEpicLinkFieldId() {
            epicLinkFieldRequested = true;
            System.out.println("      [TestJiraClient] findEpicLinkFieldId() called -> " + EPIC_LINK_FIELD_ID);
            return EPIC_LINK_FIELD_ID;
        }

        @Override
        public Map<String, Object> getIssue(String issueKey, String fields) {
            System.out.println("      [TestJiraClient] getIssue(" + issueKey + ") called");
            Object issue = mockIssues.get(issueKey);
            if (issue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) issue;
                return result;
            }
            return createDefaultIssue(issueKey, "Default Test Issue");
        }

        @Override
        public List<Map<String, Object>> searchJql(String jql, List<String> fields, int maxResults) {
            System.out.println("      [TestJiraClient] searchJql() called with jql: " + jql);
            System.out.println("      [TestJiraClient] returning " + mockSearchResults.size() + " results");
            return mockSearchResults;
        }

        @Override
        public String createIssue(Map<String, Object> fields) {
            this.createdIssueFields = fields;
            this.createdIssueKey = "TEST-" + System.currentTimeMillis();
            System.out.println("      [TestJiraClient] createIssue() called - would create: " + createdIssueKey);
            System.out.println("      [TestJiraClient] Fields: " + fields.keySet());
            return this.createdIssueKey;
        }

        @Override
        public void linkCloners(String sourceKey, String cloneKey, String linkType) {
            linkCalls.add(new LinkCall(sourceKey, cloneKey, linkType));
            System.out.println("      [TestJiraClient] linkCloners(" + sourceKey + " -> " + cloneKey + ", type: " + linkType + ")");
        }

        @Override
        public void addRemoteLinkConfluence(String issueKey, String url, String title, String relationship) {
            remoteLinkCalls.add(new RemoteLinkCall(issueKey, url, title, relationship));
            System.out.println("      [TestJiraClient] addRemoteLinkConfluence(" + issueKey + ", " + title + ")");
        }

        /**
         * Создание задачи с базовыми полями
         */
        private Map<String, Object> createDefaultIssue(String key, String summary) {
            Map<String, Object> issue = new HashMap<>();
            issue.put("key", key);

            Map<String, Object> fields = new HashMap<>();
            fields.put("summary", summary);

            List<Map<String, Object>> components = new ArrayList<>();
            Map<String, Object> component = new HashMap<>();
            component.put("id", "10000");
            component.put("name", "Backend");
            components.add(component);
            fields.put("components", components);

            Map<String, Object> priority = new HashMap<>();
            priority.put("id", "3");
            priority.put("name", "Medium");
            fields.put("priority", priority);

            issue.put("fields", fields);
            return issue;
        }

        /**
         * Добавление mock-задачи
         */
        public void addMockIssue(String key, String summary) {
            mockIssues.put(key, createDefaultIssue(key, summary));
            System.out.println("      [TestJiraClient] Added mock issue: " + key + " - " + summary);
        }

        /**
         * Добавление mock-задачи с компонентами
         */
        public void addMockIssueWithComponents(String key, String summary, List<Map<String, String>> components) {
            Map<String, Object> issue = new HashMap<>();
            issue.put("key", key);

            Map<String, Object> fields = new HashMap<>();
            fields.put("summary", summary);

            // Преобразуем List<Map<String, String>> в List<Map<String, Object>>
            List<Map<String, Object>> componentList = new ArrayList<>();
            for (Map<String, String> comp : components) {
                Map<String, Object> component = new HashMap<>();
                component.put("id", comp.get("id"));
                component.put("name", comp.get("name"));
                componentList.add(component);
            }
            fields.put("components", componentList);

            Map<String, Object> priority = new HashMap<>();
            priority.put("id", "3");
            priority.put("name", "Medium");
            fields.put("priority", priority);

            issue.put("fields", fields);

            mockIssues.put(key, issue);
            System.out.println("      [TestJiraClient] Added mock issue with " + components.size() + " components: " + key);
        }

        /**
         * Установка результатов поиска по JQL
         */
        public void setMockSearchResults(List<Map<String, Object>> results) {
            this.mockSearchResults = results;
            System.out.println("      [TestJiraClient] Set " + results.size() + " mock search results");
        }

        /**
         * Сброс состояния клиента
         */
        public void reset() {
            createdIssueKey = null;
            createdIssueFields = null;
            linkCalls.clear();
            remoteLinkCalls.clear();
            epicLinkFieldRequested = false;
            mockSearchResults.clear();
            System.out.println("      [TestJiraClient] Reset state");
        }

        // Геттеры для проверок
        public String getCreatedIssueKey() {
            return createdIssueKey;
        }

        public Map<String, Object> getCreatedIssueFields() {
            return createdIssueFields;
        }

        public List<LinkCall> getLinkCalls() {
            return linkCalls;
        }

        public List<RemoteLinkCall> getRemoteLinkCalls() {
            return remoteLinkCalls;
        }

        public boolean wasEpicLinkFieldRequested() {
            return epicLinkFieldRequested;
        }

        // DTO для вызовов
        static class LinkCall {

            public final String sourceKey;
            public final String cloneKey;
            public final String linkType;

            LinkCall(String sourceKey, String cloneKey, String linkType) {
                this.sourceKey = sourceKey;
                this.cloneKey = cloneKey;
                this.linkType = linkType;
            }
        }

        static class RemoteLinkCall {

            public final String issueKey;
            public final String url;
            public final String title;
            public final String relationship;

            RemoteLinkCall(String issueKey, String url, String title, String relationship) {
                this.issueKey = issueKey;
                this.url = url;
                this.title = title;
                this.relationship = relationship;
            }
        }
    }

    /**
     * Загрузка конфигурации из внешнего файла
     */
    private static Config loadExternalConfigOrSkip(TestInfo testInfo) {
        System.out.println("\n  [Setup] Loading external configuration for test: " + testInfo.getDisplayName());

        String path = System.getProperty("config");
        if (path == null || path.trim().isEmpty()) {
            path = System.getenv("JIRA_TEST_CONFIG");
        }

        Assumptions.assumeTrue(path != null && !path.trim().isEmpty(),
                "Provide config path via -Dconfig=<path> or env JIRA_TEST_CONFIG");

        Path p = Path.of(path);
        Assumptions.assumeTrue(Files.isRegularFile(p) && Files.isReadable(p),
                "Config file is not readable: " + p.toAbsolutePath());

        System.out.println("  [Setup] Using external config: " + p.toAbsolutePath());
        System.out.println("  [Setup] File size: " + p.toFile().length() + " bytes");

        try {
            byte[] json = Files.readAllBytes(p);
            Config config = MAPPER.readValue(json, Config.class);

            // Валидация обязательных полей
            assertNotNull(config.operation, "Config must have operation section");

            System.out.println("  [Setup] Config loaded successfully:");
            System.out.println("  [Setup]   - Project Key: " + config.operation.projectKey);
            System.out.println("  [Setup]   - Source Epic: " + config.operation.sourceEpicKey);
            System.out.println("  [Setup]   - Target Epic: " + config.operation.targetEpicKey);
            System.out.println("  [Setup]   - Single Issue: " + (config.operation.singleIssueKey != null ? config.operation.singleIssueKey : "none"));
            System.out.println("  [Setup]   - Dry Run: " + config.operation.dryRun);

            return config;
        } catch (Exception e) {
            System.err.println("  [Setup] FAILED to load config: " + e.getMessage());
            throw new AssertionError("Failed to read/parse external config: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        System.out.println("\n=================================================================");
        System.out.println("Setting up test: " + testInfo.getDisplayName());
        System.out.println("=================================================================");

        // Загружаем конфигурацию из файла
        config = loadExternalConfigOrSkip(testInfo);

        // Принудительно включаем dry-run режим для тестов
        if (config.operation != null) {
            config.operation.dryRun = true;
            System.out.println("  [Setup] Dry-run mode: ENABLED (forced for tests)");
        }

        // Создаем тестовый клиент
        testJiraClient = new TestJiraClient();

        // Настраиваем мок-данные на основе конфига
        String testIssueKey = config.operation.singleIssueKey != null
                ? config.operation.singleIssueKey
                : config.operation.sourceEpicKey;

        testJiraClient.addMockIssue(testIssueKey, "Test Issue from Config");

        // Настраиваем поиск по эпику
        List<Map<String, Object>> epicIssues = new ArrayList<>();

        Map<String, Object> issue1 = new HashMap<>();
        issue1.put("key", config.operation.sourceEpicKey + "-1");
        Map<String, Object> fields1 = new HashMap<>();
        fields1.put("summary", "Epic Issue 1");
        issue1.put("fields", fields1);
        epicIssues.add(issue1);

        Map<String, Object> issue2 = new HashMap<>();
        issue2.put("key", config.operation.sourceEpicKey + "-2");
        Map<String, Object> fields2 = new HashMap<>();
        fields2.put("summary", "Epic Issue 2");
        issue2.put("fields", fields2);
        epicIssues.add(issue2);

        testJiraClient.setMockSearchResults(epicIssues);

        service = new JiraCloningService(config, testJiraClient);

        System.out.println("  [Setup] Complete\n");
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        System.out.println("\n  [Cleanup] Resetting test state for: " + testInfo.getDisplayName());
        if (testJiraClient != null) {
            testJiraClient.reset();
        }
        System.out.println("=================================================================\n");
    }

    @Test
    @DisplayName("DRY RUN: Should not create any issues or links")
    void dryRunShouldNotCreateAnyIssues(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that in dry-run mode, no actual JIRA operations are performed");
        System.out.println("  Configuration: dryRun = " + config.operation.dryRun);

        // Act
        System.out.println("  [Action] Executing cloneIssues() in dry-run mode...");
        List<ReportEntry> results = service.cloneIssues();

        // Assert
        System.out.println("  [Assert] Checking that no issues were created...");
        assertNull(testJiraClient.getCreatedIssueFields(),
                "❌ createIssue() should not be called in dry-run mode");
        System.out.println("  ✅ createIssue() was not called");

        System.out.println("  [Assert] Checking that no links were created...");
        assertTrue(testJiraClient.getLinkCalls().isEmpty(),
                "❌ linkCloners() should not be called in dry-run mode");
        System.out.println("  ✅ linkCloners() was not called");

        System.out.println("  [Assert] Checking that no remote links were created...");
        assertTrue(testJiraClient.getRemoteLinkCalls().isEmpty(),
                "❌ addRemoteLinkConfluence() should not be called in dry-run mode");
        System.out.println("  ✅ addRemoteLinkConfluence() was not called");

        System.out.println("  [Assert] Verifying results...");
        assertNotNull(results, "Results should not be null");
        System.out.println("  ✅ Results received: " + results.size() + " entries");

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: All entries should have 'planned' status and null clone keys")
    void dryRunShouldReturnPlannedStatusForAllIssues(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that all report entries have correct dry-run status");

        // Act
        System.out.println("  [Action] Executing cloneIssues()...");
        List<ReportEntry> results = service.cloneIssues();

        // Assert
        System.out.println("  [Assert] Checking results count...");
        assertFalse(results.isEmpty(), "❌ Should have at least one issue to clone");
        System.out.println("  ✅ Found " + results.size() + " issues to clone");

        System.out.println("  [Assert] Verifying each report entry...");
        for (int i = 0; i < results.size(); i++) {
            ReportEntry entry = results.get(i);
            System.out.println("    Entry " + (i + 1) + ": " + entry.getSourceKey());

            assertEquals("planned", entry.getStatus(),
                    "❌ Entry should have 'planned' status in dry-run");
            assertNull(entry.getCloneKey(),
                    "❌ Clone key should be null in dry-run");
            assertNull(entry.getError(),
                    "❌ No error should be present in dry-run");
        }
        System.out.println("  ✅ All " + results.size() + " entries verified");

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Clone summary should include configured prefix")
    void dryRunShouldGenerateCloneSummaryWithPrefix(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that clone summaries are correctly prefixed");

        String expectedPrefix = config.operation.summaryPrefix != null
                ? config.operation.summaryPrefix : "";
        System.out.println("  Configuration: summaryPrefix = '" + expectedPrefix + "'");

        // Act
        System.out.println("  [Action] Executing cloneIssues()...");
        List<ReportEntry> results = service.cloneIssues();

        // Assert
        assertFalse(results.isEmpty());
        ReportEntry entry = results.get(0);

        System.out.println("  [Assert] Checking source issue...");
        assertNotNull(entry.getSourceKey(), "Source key should not be null");
        assertNotNull(entry.getSourceSummary(), "Source summary should not be null");
        System.out.println("    Source: " + entry.getSourceKey() + " - " + entry.getSourceSummary());

        System.out.println("  [Assert] Checking clone summary format...");
        String expectedSummary = expectedPrefix + entry.getSourceSummary();
        assertEquals(expectedSummary, entry.getCloneSummary(),
                "❌ Clone summary should have prefix from config");
        System.out.println("    Clone: " + entry.getCloneSummary());
        System.out.println("    Expected: " + expectedSummary);
        System.out.println("  ✅ Clone summary format verified");

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Single issue mode - only processes configured issue")
    void dryRunWithSingleIssueKeyShouldOnlyProcessThatIssue(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that singleIssueKey mode works correctly");

        // Проверяем, что в конфиге задан singleIssueKey
        Assumptions.assumeTrue(config.operation.singleIssueKey != null
                && !config.operation.singleIssueKey.isBlank(),
                "Skipping test: singleIssueKey not configured");

        System.out.println("  Configuration: singleIssueKey = " + config.operation.singleIssueKey);

        // Сбрасываем поисковые результаты
        testJiraClient.setMockSearchResults(new ArrayList<>());
        System.out.println("  [Setup] Cleared mock search results to verify single issue mode");

        // Act
        System.out.println("  [Action] Executing cloneIssues() in single issue mode...");
        List<ReportEntry> results = service.cloneIssues();

        // Assert
        System.out.println("  [Assert] Checking result count...");
        assertEquals(1, results.size(),
                "❌ Should only process one issue when singleIssueKey is set");
        System.out.println("  ✅ Processed 1 issue as expected");

        System.out.println("  [Assert] Checking issue key...");
        assertEquals(config.operation.singleIssueKey, results.get(0).getSourceKey(),
                "❌ Source key should match singleIssueKey from config");
        System.out.println("  ✅ Issue key matches: " + results.get(0).getSourceKey());

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Epic mode - searches and processes multiple issues")
    void dryRunWithoutSingleIssueKeyShouldSearchInEpic(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that epic search mode works correctly");

        // Проверяем, что singleIssueKey НЕ задан
        Assumptions.assumeTrue(config.operation.singleIssueKey == null
                || config.operation.singleIssueKey.isBlank(),
                "Skipping test: singleIssueKey is set, this test requires epic search");

        System.out.println("  Configuration: sourceEpicKey = " + config.operation.sourceEpicKey);

        // Act
        System.out.println("  [Action] Executing cloneIssues() in epic search mode...");
        List<ReportEntry> results = service.cloneIssues();

        // Assert
        System.out.println("  [Assert] Checking result count...");
        assertTrue(results.size() > 1,
                "❌ Should process multiple issues when searching by epic");
        System.out.println("  ✅ Processed " + results.size() + " issues from epic");

        System.out.println("  [Assert] Verifying issue keys...");
        for (int i = 0; i < results.size(); i++) {
            System.out.println("    Issue " + (i + 1) + ": " + results.get(i).getSourceKey());
            assertTrue(results.get(i).getSourceKey().startsWith(config.operation.sourceEpicKey),
                    "❌ Issue key should start with epic key");
        }
        System.out.println("  ✅ All issue keys verified");

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Statistics correctly reflect dry-run mode")
    void statisticsShouldReflectDryRunMode(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that operation statistics are correct for dry-run");

        // Act
        System.out.println("  [Action] Executing cloneIssues()...");
        List<ReportEntry> results = service.cloneIssues();

        System.out.println("  [Action] Generating statistics...");
        Map<String, Object> stats = service.getStatistics(results);

        // Assert
        System.out.println("  [Assert] Checking dry-run flag...");
        assertTrue((Boolean) stats.get("dryRun"),
                "❌ Statistics should indicate dry-run mode");
        System.out.println("  ✅ dryRun = true");

        System.out.println("  [Assert] Checking statistics values...");
        System.out.println("    Total: " + stats.get("total"));
        System.out.println("    Planned: " + stats.get("planned"));
        System.out.println("    Created: " + stats.get("created"));
        System.out.println("    Failed: " + stats.get("failed"));

        assertEquals((long) results.size(), stats.get("planned"),
                "❌ All issues should be planned in dry-run");
        assertEquals(0L, stats.get("created"),
                "❌ No issues should be created in dry-run");
        assertEquals(0L, stats.get("failed"),
                "❌ No failures should occur in dry-run");

        System.out.println("  ✅ Statistics verified");
        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Configuration has required fields")
    void dryRunShouldUseConfiguredProjectKey(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that configuration contains all required fields");

        System.out.println("  [Assert] Checking project configuration...");
        assertNotNull(config.operation.projectKey,
                "❌ Project key should be configured");
        System.out.println("    Project Key: " + config.operation.projectKey);

        System.out.println("  [Assert] Checking epic configuration...");
        assertNotNull(config.operation.sourceEpicKey,
                "❌ Source epic key should be configured");
        System.out.println("    Source Epic: " + config.operation.sourceEpicKey);

        assertNotNull(config.operation.targetEpicKey,
                "❌ Target epic key should be configured");
        System.out.println("    Target Epic: " + config.operation.targetEpicKey);

        System.out.println("  [Assert] Checking issue type...");
        assertNotNull(config.operation.issueTypeName,
                "❌ Issue type should be configured");
        System.out.println("    Issue Type: " + config.operation.issueTypeName);

        System.out.println("  ✅ All required fields present");
        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Reporter configuration is valid")
    void dryRunShouldValidateReporterConfiguration(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that reporter is properly configured");

        System.out.println("  [Assert] Checking reporter configuration...");
        assertNotNull(config.operation.reporter,
                "❌ Reporter should be configured");

        boolean hasAccountId = config.operation.reporter.accountId != null
                && !config.operation.reporter.accountId.isBlank();
        boolean hasUsername = config.operation.reporter.username != null
                && !config.operation.reporter.username.isBlank();

        System.out.println("    Account ID present: " + hasAccountId);
        System.out.println("    Username present: " + hasUsername);

        assertTrue(hasAccountId || hasUsername,
                "❌ At least one of accountId or username must be provided for reporter");

        if (hasAccountId) {
            System.out.println("    Reporter Account ID: " + config.operation.reporter.accountId);
        }
        if (hasUsername) {
            System.out.println("    Reporter Username: " + config.operation.reporter.username);
        }

        System.out.println("  ✅ Reporter configuration valid");
        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Handles missing remote link gracefully")
    void dryRunShouldHandleMissingRemoteLink(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that service works correctly without remote link configuration");

        // Сохраняем оригинальный remoteLink
        Config.RemoteLink originalRemoteLink = config.operation.remoteLink;
        System.out.println("  [Setup] Original remoteLink: " + (originalRemoteLink != null ? "present" : "null"));

        try {
            // Временно убираем remoteLink
            config.operation.remoteLink = null;
            System.out.println("  [Setup] RemoteLink set to null for test");

            // Act
            System.out.println("  [Action] Executing cloneIssues() without remoteLink...");
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            System.out.println("  [Assert] Checking results...");
            assertFalse(results.isEmpty(), "❌ Should still process issues");
            System.out.println("  ✅ Processed " + results.size() + " issues");

            System.out.println("  [Assert] Checking remote link calls...");
            assertTrue(testJiraClient.getRemoteLinkCalls().isEmpty(),
                    "❌ No remote link calls should be made");
            System.out.println("  ✅ No remote link calls made");

        } finally {
            // Восстанавливаем оригинальный remoteLink
            config.operation.remoteLink = originalRemoteLink;
            System.out.println("  [Cleanup] RemoteLink restored");
        }

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Handles issues with custom components")
    void dryRunShouldWorkWithCustomComponents(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that issues with components are handled correctly");

        // Добавляем задачу с компонентами
        String testKey = "TEST-123";
        List<Map<String, String>> components = new ArrayList<>();

        Map<String, String> comp1 = new HashMap<>();
        comp1.put("id", "10000");
        comp1.put("name", "Backend");
        components.add(comp1);

        Map<String, String> comp2 = new HashMap<>();
        comp2.put("id", "10001");
        comp2.put("name", "Frontend");
        components.add(comp2);

        Map<String, String> comp3 = new HashMap<>();
        comp3.put("id", "10002");
        comp3.put("name", "Database");
        components.add(comp3);

        testJiraClient.addMockIssueWithComponents(testKey, "Issue with Multiple Components", components);
        System.out.println("  [Setup] Added mock issue with " + components.size() + " components: " + testKey);

        // Временно подменяем singleIssueKey
        String originalSingleKey = config.operation.singleIssueKey;
        config.operation.singleIssueKey = testKey;
        System.out.println("  [Setup] Temporarily set singleIssueKey = " + testKey);

        try {
            // Act
            System.out.println("  [Action] Executing cloneIssues() with component-rich issue...");
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            System.out.println("  [Assert] Checking results...");
            assertEquals(1, results.size(), "❌ Should process exactly one issue");
            System.out.println("  ✅ Processed 1 issue");

            ReportEntry entry = results.get(0);
            assertEquals("planned", entry.getStatus(), "❌ Status should be planned");
            assertEquals(testKey, entry.getSourceKey(), "❌ Source key should match");
            System.out.println("    Issue: " + entry.getSourceKey() + " - " + entry.getSourceSummary());
            System.out.println("    Status: " + entry.getStatus());

        } finally {
            // Восстанавливаем оригинальный ключ
            config.operation.singleIssueKey = originalSingleKey;
            System.out.println("  [Cleanup] Restored singleIssueKey = " + originalSingleKey);
        }

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Validates Epic Link field is requested")
    void dryRunShouldRequestEpicLinkField(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        System.out.println("  Description: Verifies that Epic Link field ID is requested during initialization");

        // Сбрасываем состояние клиента
        testJiraClient.reset();
        System.out.println("  [Setup] Reset test client state");

        // Act
        System.out.println("  [Action] Executing cloneIssues()...");
        List<ReportEntry> results = service.cloneIssues();

        // Assert
        System.out.println("  [Assert] Checking if Epic Link field was requested...");
        assertTrue(testJiraClient.wasEpicLinkFieldRequested(),
                "❌ Epic Link field should be requested during service initialization");
        System.out.println("  ✅ Epic Link field requested: " + EPIC_LINK_FIELD_ID);

        assertFalse(results.isEmpty(), "❌ Should process issues");
        System.out.println("  ✅ Processed " + results.size() + " issues");

        System.out.println("=== Test PASSED: " + testInfo.getDisplayName() + " ===\n");
    }

    @Test
    @DisplayName("DRY RUN: Assigns architect when component matches exactly")
    void dryRunShouldAssignArchitectWhenComponentMatches(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
// Arrange
        String testKey = "TEST-ARCH-1";
        List<Map<String, String>> components = new ArrayList<>();
        Map<String, String> comp = new HashMap<>();
        comp.put("id", "20000");
        comp.put("name", "Backend"); // компонент в задаче
        components.add(comp);
        testJiraClient.addMockIssueWithComponents(testKey, "Issue With Architect Component", components);

        String originalSingle = config.operation.singleIssueKey;
        String originalComp = config.operation.architectComponent;
        Config.Reporter originalArchitect = config.operation.architect;
        try {
            config.operation.singleIssueKey = testKey;
            config.operation.architectComponent = "Backend"; // точное совпадение
            // Делаем архитектора с username только, чтобы проще проверять отчёт
            Config.Reporter a = new Config.Reporter();
            a.username = "arch.user";
            a.accountId = null;
            config.operation.architect = a;

            // Act
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            assertEquals(1, results.size(), "Should process exactly one issue");
            ReportEntry entry = results.get(0);
            assertEquals("planned", entry.getStatus());
            assertNotNull(entry.getAssignee(), "Assignee should be populated in dry-run");
            assertNotNull(entry.getAssignmentReason(), "Assignment reason should be populated in dry-run");
            assertEquals("arch.user", entry.getAssignee(), "Assignee should be architect by username");
            assertTrue(entry.getAssignmentReason().contains("component match"), "Reason should explain component match");
            assertTrue(entry.getAssignmentReason().contains("Backend"), "Reason should include matched component name");
            System.out.println("  ✅ Assigned architect: " + entry.getAssignee() + " (" + entry.getAssignmentReason() + ")");
        } finally {
            config.operation.singleIssueKey = originalSingle;
            config.operation.architectComponent = originalComp;
            config.operation.architect = originalArchitect;
        }
    }

    @Test
    @DisplayName("DRY RUN: Assigns reporter when no matching component")
    void dryRunShouldAssignReporterWhenNoMatchingComponent(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
// Arrange
        String testKey = "TEST-ARCH-2";
        List<Map<String, String>> components = new ArrayList<>();
        Map<String, String> comp = new HashMap<>();
        comp.put("id", "20000");
        comp.put("name", "Backend"); // компонент в задаче
        components.add(comp);
        testJiraClient.addMockIssueWithComponents(testKey, "Issue Without Architect Component", components);

        String originalSingle = config.operation.singleIssueKey;
        String originalComp = config.operation.architectComponent;
        Config.Reporter originalReporter = config.operation.reporter;
        try {
            config.operation.singleIssueKey = testKey;
            config.operation.architectComponent = "NonExisting"; // нет совпадения

            // Упростим проверку отчёта: reporter только username
            Config.Reporter r = new Config.Reporter();
            r.username = "john.reporter";
            r.accountId = null;
            config.operation.reporter = r;

            // Act
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            assertEquals(1, results.size());
            ReportEntry entry = results.get(0);
            assertEquals("planned", entry.getStatus());
            assertEquals("john.reporter", entry.getAssignee(), "Assignee should be reporter when no matching component");
            assertTrue(entry.getAssignmentReason().toLowerCase().contains("reporter"),
                    "Reason should indicate reporter fallback");
            System.out.println("  ✅ Assigned reporter: " + entry.getAssignee() + " (" + entry.getAssignmentReason() + ")");
        } finally {
            config.operation.singleIssueKey = originalSingle;
            config.operation.architectComponent = originalComp;
            config.operation.reporter = originalReporter;
        }
    }

    @Test
    @DisplayName("DRY RUN: Uses exact (case-sensitive) component name matching")
    void dryRunShouldUseExactCaseSensitiveMatching(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
// Arrange
        String testKey = "TEST-ARCH-3";
        List<Map<String, String>> components = new ArrayList<>();
        Map<String, String> comp = new HashMap<>();
        comp.put("id", "20000");
        comp.put("name", "Backend"); // Заглавная B
        components.add(comp);
        testJiraClient.addMockIssueWithComponents(testKey, "Issue Case Sensitivity", components);

        String originalSingle = config.operation.singleIssueKey;
        String originalComp = config.operation.architectComponent;
        Config.Reporter originalReporter = config.operation.reporter;
        try {
            config.operation.singleIssueKey = testKey;
            config.operation.architectComponent = "backend"; // строчная b — не совпадёт

            Config.Reporter r = new Config.Reporter();
            r.username = "john.reporter";
            r.accountId = null;
            config.operation.reporter = r;

            // Act
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            assertEquals(1, results.size());
            ReportEntry entry = results.get(0);
            assertEquals("planned", entry.getStatus());
            assertEquals("john.reporter", entry.getAssignee(),
                    "Assignee should fall back to reporter due to case-sensitive mismatch");
            System.out.println("  ✅ Case-sensitive mismatch → reporter: " + entry.getAssignee());
        } finally {
            config.operation.singleIssueKey = originalSingle;
            config.operation.architectComponent = originalComp;
            config.operation.reporter = originalReporter;
        }
    }

    @Test
    @DisplayName("DRY RUN: When architectComponent is empty, reporter is always assigned")
    void dryRunShouldAssignReporterWhenArchitectComponentIsEmpty(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
// Arrange
        String testKey = "TEST-ARCH-4";
        List<Map<String, String>> components = new ArrayList<>();
        Map<String, String> comp = new HashMap<>();
        comp.put("id", "20000");
        comp.put("name", "Backend");
        components.add(comp);
        testJiraClient.addMockIssueWithComponents(testKey, "Issue Empty Architect Component", components);

        String originalSingle = config.operation.singleIssueKey;
        String originalComp = config.operation.architectComponent;
        Config.Reporter originalReporter = config.operation.reporter;
        try {
            config.operation.singleIssueKey = testKey;
            config.operation.architectComponent = ""; // пустая строка → всегда reporter

            Config.Reporter r = new Config.Reporter();
            r.username = "john.reporter";
            r.accountId = null;
            config.operation.reporter = r;

            // Act
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            assertEquals(1, results.size());
            ReportEntry entry = results.get(0);
            assertEquals("planned", entry.getStatus());
            assertEquals("john.reporter", entry.getAssignee(),
                    "Assignee should be reporter when architectComponent is empty");
            assertTrue(entry.getAssignmentReason().toLowerCase().contains("reporter"),
                    "Reason should indicate reporter fallback");
            System.out.println("  ✅ Empty architectComponent → reporter: " + entry.getAssignee());
        } finally {
            config.operation.singleIssueKey = originalSingle;
            config.operation.architectComponent = originalComp;
            config.operation.reporter = originalReporter;
        }
    }

    @Test
    @DisplayName("DRY RUN: Assignee and reason are populated for all planned entries")
    void dryRunShouldPopulateAssigneeAndReasonForAllEntries(TestInfo testInfo) {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
// Arrange: убрать singleIssueKey, чтобы прошёл путь поиска эпика
        String originalSingle = config.operation.singleIssueKey;
        String originalComp = config.operation.architectComponent;
        try {
            config.operation.singleIssueKey = null; // пусть возьмёт задачи из эпика
// Выставим architectComponent не совпадающий с тестовыми epics из setUp (там нет components)
            config.operation.architectComponent = "Backend"; // epic search моки не содержат components → всегда reporter

            // Упростим проверку: выставим отчётливо видимый reporter
            Config.Reporter r = new Config.Reporter();
            r.username = "john.reporter";
            r.accountId = null;
            config.operation.reporter = r;

            // Act
            List<ReportEntry> results = service.cloneIssues();

            // Assert
            assertFalse(results.isEmpty(), "Should have some planned entries");
            for (ReportEntry entry : results) {
                assertEquals("planned", entry.getStatus());
                assertNotNull(entry.getAssignee(), "Each entry should have assignee filled");
                assertNotNull(entry.getAssignmentReason(), "Each entry should have assignment reason");
                // В режиме epic search наши моки без компонентов → всегда репортёр
                assertEquals("john.reporter", entry.getAssignee(), "Assignee should be reporter for epic-search mocks");
            }
            System.out.println("  ✅ All entries have assignee and reason populated");
        } finally {
            config.operation.singleIssueKey = originalSingle;
            config.operation.architectComponent = originalComp;
        }
    }
}
