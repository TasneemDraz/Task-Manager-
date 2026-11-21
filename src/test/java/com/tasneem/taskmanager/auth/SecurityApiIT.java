package com.tasneem.taskmanager.auth;

import com.tasneem.taskmanager.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityApiIT extends IntegrationTestBase {

    @Test
    void missingOrInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        register(email);

        login(email, "wrong-password1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        // emails are unique case-insensitively
        register(email.toUpperCase())
                .andExpect(status().isConflict());
    }

    @Test
    void currentUserProfile() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", tokenFor(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void nonAdminGets403OnAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", newUserToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void seededAdminCanListUsersAndTasksWithOwner() throws Exception {
        String email = uniqueEmail();
        register(email);
        String user = tokenFor(email, PASSWORD);
        createTask(user, Map.of("title", "Owned by " + email));
        String admin = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mockMvc.perform(get("/api/v1/admin/tasks").header("Authorization", admin).param("q", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].owner.email").value(email));

        // the owner's own view does not repeat the owner
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].owner").doesNotExist());
    }

    @Test
    void adminAccountCannotBeDeactivated() throws Exception {
        String admin = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminId = read(mockMvc.perform(get("/api/v1/users/me").header("Authorization", admin)))
                .get("id").asText();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/deactivate", adminId).header("Authorization", admin))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedUserIsLockedOutImmediately() throws Exception {
        String email = uniqueEmail();
        String userId = read(register(email)).get("id").asText();
        String userToken = tokenFor(email, PASSWORD);
        String admin = tokenFor(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/deactivate", userId).header("Authorization", admin))
                .andExpect(status().isNoContent());

        // the token issued before deactivation is rejected
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", userToken))
                .andExpect(status().isUnauthorized());
        // and logging in again gives the generic message
        login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
}
