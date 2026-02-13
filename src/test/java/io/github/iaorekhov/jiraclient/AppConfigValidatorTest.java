package io.github.iaorekhov.jiraclient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.iaorekhov.jiraclient.config.Config;
import io.github.iaorekhov.jiraclient.config.ConfigValidationException;
import io.github.iaorekhov.jiraclient.config.ConfigValidator;

class AppConfigValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

// Загружает конфиг из -Dconfig или env JIRA_TEST_CONFIG. Если не задано — тест будет пропущен.
    private static Config loadExternalConfigOrSkip() {
        String path = System.getProperty("config");
        if (path == null || path.trim().isEmpty()) {
            path = System.getenv("JIRA_TEST_CONFIG");
        }
        Assumptions.assumeTrue(path != null && !path.trim().isEmpty(),
                "Provide config path via -Dconfig=<path> or env JIRA_TEST_CONFIG");

        Path p = Path.of(path);
        Assumptions.assumeTrue(Files.isRegularFile(p) && Files.isReadable(p),
                "Config file is not readable: " + p.toAbsolutePath());

        System.out.println("[INFO] Using external config for tests: " + p.toAbsolutePath());
        try {
            byte[] json = Files.readAllBytes(p);
            return MAPPER.readValue(json, Config.class);
        } catch (Exception e) {
            fail("Failed to read/parse external config: " + e.getMessage());
            return null; // недостижимо
        }
    }

// Глубокое копирование через Jackson, чтобы безопасно мутировать копию
    private static Config deepCopy(Config src) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(src);
            return MAPPER.readValue(json, Config.class);
        } catch (Exception e) {
            throw new RuntimeException("Deep copy failed: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("Valid external config passes validation")
    void validExternalConfigIsAccepted(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        assertDoesNotThrow(() -> new ConfigValidator().validateAndNormalize(cfg));
    }

    @Test
    @DisplayName("Normalization trims baseUrl slashes, lower-cases auth, and defaults link type to 'Cloners'")
    void normalizationOnTopOfExternalConfig(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);

        mutated.jira.baseUrl = mutated.jira.baseUrl + "///";
        mutated.jira.auth = "BEARER";
        mutated.operation.linkToOriginal = null; // проверить дефолт "Cloners"

        assertDoesNotThrow(() -> new ConfigValidator().validateAndNormalize(mutated));
        assertFalse(mutated.jira.baseUrl.endsWith("/"), "baseUrl trailing slashes should be trimmed");
        assertEquals("bearer", mutated.jira.auth, "auth should be normalized to 'bearer'");
        assertNotNull(mutated.operation.linkToOriginal, "linkToOriginal should be initialized");
        assertEquals("Cloners", mutated.operation.linkToOriginal.typeName, "typeName should default to 'Cloners'");
    }

    @Test
    @DisplayName("Null config throws ConfigValidationException")
    void nullConfigThrows(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(null)
        );
        assertTrue(ex.getMessage().contains("config is null"));
        assertTrue(ex.getErrors().contains("config is null"));
    }

    @Test
    @DisplayName("Missing Jira or Operation sections are reported")
    void missingJiraOrOperationThrows(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config base = loadExternalConfigOrSkip();

        Config cfg1 = deepCopy(base);
        cfg1.jira = null;
        ConfigValidationException ex1 = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(cfg1)
        );
        assertTrue(ex1.getErrors().contains("jira is missing"));

        Config cfg2 = deepCopy(base);
        cfg2.operation = null;
        ConfigValidationException ex2 = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(cfg2)
        );
        assertTrue(ex2.getErrors().contains("operation is missing"));
    }

    @Test
    @DisplayName("Invalid Jira fields: baseUrl scheme, auth type and missing token")
    void invalidJiraFields(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.jira.baseUrl = "htp://bad";
        mutated.jira.auth = "basic";
        mutated.jira.token = "  ";

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("jira.baseUrl is invalid: 'htp://bad'"));
        assertTrue(ex.getErrors().contains("jira.auth must be 'bearer', got: 'basic'"));
        assertTrue(ex.getErrors().contains("jira.token is missing"));
    }

    @Test
    @DisplayName("Invalid project and issue keys are detected")
    void invalidProjectAndIssueKeys(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.projectKey = "prj";
        mutated.operation.sourceEpicKey = "PRJ-";
        mutated.operation.targetEpicKey = "prj-1";
        mutated.operation.singleIssueKey = "PRJ-";

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.projectKey is invalid: 'prj'"));
        assertTrue(ex.getErrors().contains("operation.sourceEpicKey is invalid: 'PRJ-'"));
        assertTrue(ex.getErrors().contains("operation.targetEpicKey is invalid: 'prj-1'"));
        assertTrue(ex.getErrors().contains("operation.singleIssueKey is invalid: 'PRJ-'"));
    }

    @Test
    @DisplayName("Missing required operation fields are aggregated")
    void missingRequiredOperationFieldsAggregated(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.issueTypeName = null;
        mutated.operation.summaryPrefix = null;
        mutated.operation.description = null;
        mutated.operation.remoteLink = null;
        mutated.operation.reporter = null;

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.issueTypeName is missing"));
        assertTrue(ex.getErrors().contains("operation.summaryPrefix is missing"));
        assertTrue(ex.getErrors().contains("operation.description is missing"));
        assertTrue(ex.getErrors().contains("operation.remoteLink is missing"));
        assertTrue(ex.getErrors().contains("operation.reporter is missing"));
    }

    @Test
    @DisplayName("RemoteLink URL and title validation")
    void remoteLinkUrlAndTitleValidation(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.remoteLink.url = "htp://bad";
        mutated.operation.remoteLink.title = "   ";

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.remoteLink.url is invalid: 'htp://bad'"));
        assertTrue(ex.getErrors().contains("operation.remoteLink.title is missing"));
    }

    @Test
    @DisplayName("Reporter must have at least one identifier")
    void reporterMustHaveAtLeastOneIdentifier(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.reporter = new Config.Reporter(); // оба пустые

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.reporter: at least one of username or accountId must be provided"));
    }

    @Test
    @DisplayName("LinkToOriginal empty typeName fails validation")
    void linkToOriginalEmptyTypeNameFails(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.linkToOriginal = new Config.LinkToOriginal();
        mutated.operation.linkToOriginal.typeName = "   ";

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.linkToOriginal.typeName is empty"));
    }

    @Test
    @DisplayName("Architect is required")
    void architectIsRequired(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.architect = null;

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.architect is missing"));
    }

    @Test
    @DisplayName("Architect must have at least one identifier")
    void architectMustHaveAtLeastOneIdentifier(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.architect = new Config.Reporter(); // оба поля пустые

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.architect: at least one of username or accountId must be provided"));
    }

    @Test
    @DisplayName("architectComponent defaults to empty string when null")
    void architectComponentDefaultsToEmptyStringWhenNull(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.architectComponent = null;

        assertDoesNotThrow(() -> new ConfigValidator().validateAndNormalize(mutated));
        assertNotNull(mutated.operation.architectComponent, "architectComponent should be initialized");
        assertEquals("", mutated.operation.architectComponent, "architectComponent should default to empty string");
    }

    @Test
    @DisplayName("architectComponent empty string is preserved (no normalization)")
    void architectComponentEmptyStringPreserved(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.architectComponent = "";

        assertDoesNotThrow(() -> new ConfigValidator().validateAndNormalize(mutated));
        assertEquals("", mutated.operation.architectComponent, "architectComponent empty string should be preserved");
    }

    @Test
    @DisplayName("Both architect and reporter missing are aggregated")
    void architectAndReporterMissingAreAggregated(TestInfo testInfo) throws Exception {
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        Config cfg = loadExternalConfigOrSkip();
        Config mutated = deepCopy(cfg);
        mutated.operation.reporter = null;
        mutated.operation.architect = null;

        ConfigValidationException ex = assertThrows(
                ConfigValidationException.class,
                () -> new ConfigValidator().validateAndNormalize(mutated)
        );
        assertTrue(ex.getErrors().contains("operation.reporter is missing"));
        assertTrue(ex.getErrors().contains("operation.architect is missing"));
    }
}
