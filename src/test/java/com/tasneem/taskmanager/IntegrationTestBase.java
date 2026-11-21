package com.tasneem.taskmanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application against a real PostgreSQL 16 in Docker (Testcontainers).
 * The container is started once and shared by every integration test class.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=aW50ZWdyYXRpb24tdGVzdC1zZWNyZXQtYXQtbGVhc3QtMzItYnl0ZXM=",
        "ADMIN_EMAIL=" + IntegrationTestBase.ADMIN_EMAIL,
        "ADMIN_PASSWORD=" + IntegrationTestBase.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "Admin12345";
    protected static final String PASSWORD = "secret123";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A fresh email per call, since all tests share one database. */
    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected ResultActions register(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fullName", "Test User", "email", email, "password", PASSWORD))));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    /** Registers a new user and returns an "Authorization" header value for them. */
    protected String newUserToken() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isCreated());
        return tokenFor(email, PASSWORD);
    }

    protected String tokenFor(String email, String password) throws Exception {
        JsonNode body = read(login(email, password).andExpect(status().isOk()));
        return "Bearer " + body.get("accessToken").asText();
    }

    protected JsonNode createTask(String auth, Map<String, Object> body) throws Exception {
        return read(mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated()));
    }
}
