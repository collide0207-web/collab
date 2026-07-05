package app.collide.control.auth;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.collide.control.support.PostgresIntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end auth flow against a real Postgres + the full Spring context (security,
 * Flyway, JPA). Exercises the signup -> me -> refresh(rotate) -> reuse-detection path,
 * which is the heart of the token design. Skipped when Docker is unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
    void signupThenMeThenRefreshRotationAndReuseDetection() throws Exception {
        String body = """
                {"email":"ada@example.com","username":"ada","name":"Ada Lovelace","password":"Str0ng!Pass"}""";

        MvcResult signup = mvc.perform(post("/api/auth/signup").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.email").value("ada@example.com"))
                .andReturn();

        String access = extract(signup, "accessToken");
        String refresh1 = extract(signup, "refreshToken");

        // Access token authorises /me.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("ada"));

        // Refresh rotates the token.
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String refresh2 = extract(refreshed, "refreshToken");
        assertNotEquals(refresh1, refresh2, "refresh must rotate the token");

        // Reusing the OLD token is detected as theft → 401.
        mvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"" + refresh1 + "\"}"))
                .andExpect(status().isUnauthorized());

        // Reuse revoked the whole family, so the successor is dead too.
        mvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"" + refresh2 + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        String signup = """
                {"email":"grace@example.com","username":"grace","name":"Grace","password":"Str0ng!Pass"}""";
        mvc.perform(post("/api/auth/signup").contentType(JSON).content(signup))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"grace@example.com\",\"password\":\"WrongPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("invalid credentials"));
    }

    @Test
    void duplicateSignupIsConflict() throws Exception {
        String signup = """
                {"email":"dup@example.com","username":"dupuser","name":"Dup","password":"Str0ng!Pass"}""";
        mvc.perform(post("/api/auth/signup").contentType(JSON).content(signup))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").contentType(JSON).content(signup))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        String signup = """
                {"email":"weak@example.com","username":"weakuser","name":"Weak","password":"weak"}""";
        mvc.perform(post("/api/auth/signup").contentType(JSON).content(signup))
                .andExpect(status().isBadRequest());
    }

    private static String extract(MvcResult result, String field) throws Exception {
        String content = result.getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
        if (!m.find()) {
            throw new AssertionError("field '" + field + "' not found in response: " + content);
        }
        return m.group(1);
    }
}
