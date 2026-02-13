package io.github.iaorekhov.jiraclient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Unit test for simple App.
 */
public class AppTest {
    @Test 
    @DisplayName("basic")
    void basic(TestInfo testInfo) { 
        System.out.println("\n=== Test: " + testInfo.getDisplayName() + " ===");
        assertTrue(true, "AppTest.basic() invoked");         
    }
}


