package app.collide.control.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.collide.control.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * API-level tests for /execute: auth gate + request validation. Runs against a real
 * Postgres (skips without Docker, per {@link PostgresIntegrationTest}) because the whole
 * Spring context — including the existing JWT auth this endpoint reuses — needs to boot.
 * Deliberately doesn't assert on real compiler/interpreter output here; that's
 * {@link ExecutionServiceIT}'s job, run without a Spring context at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExecutionControllerTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private String accessToken;

    @BeforeEach
    void signUpAUser() throws Exception {
        accessToken = signUpAndGetAccessToken();
    }

    @Test
    void executeWithoutATokenIsUnauthorized() throws Exception {
        mvc.perform(post("/execute").contentType(JSON).content("{\"language\":\"python\",\"sourceCode\":\"print(1)\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnUnsupportedLanguage() throws Exception {
        String body = "{\"language\":\"cobol\",\"sourceCode\":\"print 1\"}";

        mvc.perform(post("/execute").header("Authorization", "Bearer " + accessToken).contentType(JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsBlankSourceCode() throws Exception {
        String body = "{\"language\":\"python\",\"sourceCode\":\"   \"}";

        mvc.perform(post("/execute").header("Authorization", "Bearer " + accessToken).contentType(JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meStillRequiresATokenAfterAddingExecuteRoute() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void executeReturnsAcceptedWithAPendingExecutionId() throws Exception {
        String body = "{\"language\":\"javascript\",\"sourceCode\":\"console.log(1)\"}";

        mvc.perform(post("/execute").header("Authorization", "Bearer " + accessToken).contentType(JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.executionId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void statusAndResultRoundTripToACompletedNodeExecution() throws Exception {
        String body = "{\"language\":\"javascript\",\"sourceCode\":\"console.log('hello')\"}";
        MvcResult submitted = mvc.perform(
                        post("/execute").header("Authorization", "Bearer " + accessToken).contentType(JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String executionId = extract(submitted, "executionId");

        String terminalStatus = pollUntilTerminal(executionId);

        assertEquals("COMPLETED", terminalStatus);
        mvc.perform(get("/result/" + executionId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stdout", Matchers.containsString("hello")));
    }

    @Test
    void historyContainsACompletedExecutionAfterItFinishes() throws Exception {
        String body = "{\"language\":\"javascript\",\"sourceCode\":\"console.log('history check')\"}";
        MvcResult submitted = mvc.perform(
                        post("/execute").header("Authorization", "Bearer " + accessToken).contentType(JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String executionId = extract(submitted, "executionId");
        assertEquals("COMPLETED", pollUntilTerminal(executionId));

        mvc.perform(get("/history").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(executionId))
                .andExpect(jsonPath("$.content[0].stdout", Matchers.containsString("history check")));
    }

    @Test
    void historyIsScopedToTheCallingUser() throws Exception {
        String body = "{\"language\":\"javascript\",\"sourceCode\":\"console.log('mine')\"}";
        MvcResult submitted = mvc.perform(
                        post("/execute").header("Authorization", "Bearer " + accessToken).contentType(JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String myExecutionId = extract(submitted, "executionId");
        assertEquals("COMPLETED", pollUntilTerminal(myExecutionId));

        String otherToken = signUpAndGetAccessToken();
        mvc.perform(get("/history").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.id=='" + myExecutionId + "')]").doesNotExist());
    }

    private String signUpAndGetAccessToken() throws Exception {
        String email = "exec-" + System.nanoTime() + "@example.com";
        String body = """
                {"email":"%s","username":"exec%d","name":"Exec Tester","password":"Str0ng!Pass"}"""
                .formatted(email, System.nanoTime());
        MvcResult signup = mvc.perform(post("/api/auth/signup").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extract(signup, "accessToken");
    }

    private String pollUntilTerminal(String executionId) throws Exception {
        Set<String> terminal = Set.of("COMPLETED", "FAILED", "TIMEOUT", "CANCELLED");
        String lastStatus = "PENDING";
        for (int i = 0; i < 50 && !terminal.contains(lastStatus); i++) {
            Thread.sleep(100);
            MvcResult result = mvc.perform(get("/status/" + executionId).header("Authorization", "Bearer " + accessToken))
                    .andReturn();
            lastStatus = extract(result, "status");
        }
        return lastStatus;
    }

    private static String extract(MvcResult result, String field) throws Exception {
        Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(result.getResponse().getContentAsString());
        if (!matcher.find()) {
            throw new IllegalStateException("field not found in response: " + field);
        }
        return matcher.group(1);
    }
}
