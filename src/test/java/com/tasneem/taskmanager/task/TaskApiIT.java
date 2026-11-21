package com.tasneem.taskmanager.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.tasneem.taskmanager.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskApiIT extends IntegrationTestBase {

    private static final String TOMORROW = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

    @Test
    void fullTaskLifecycle() throws Exception {
        // register -> login
        String email = uniqueEmail();
        register(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());
        String auth = tokenFor(email, PASSWORD);

        // create
        JsonNode demo = read(mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Prepare sprint demo", "description", "Slides + live demo",
                                "priority", "HIGH", "dueDate", TOMORROW))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/v1/tasks/\\d+$")))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.version").value(0)));
        long demoId = demo.get("id").asLong();
        mockMvc.perform(get("/api/v1/tasks/{id}", demoId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Prepare sprint demo"));
        createTask(auth, Map.of("title", "Buy groceries"));

        // filter + search
        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", auth)
                        .param("status", "TODO", "IN_PROGRESS")
                        .param("priority", "HIGH")
                        .param("q", "SPRINT")
                        .param("sort", "dueDate,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(demoId))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));

        // update status: TODO -> IN_PROGRESS -> DONE
        changeStatus(auth, demoId, "IN_PROGRESS", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        changeStatus(auth, demoId, "DONE", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt", notNullValue()));

        // a DONE task cannot be edited; reopening clears completedAt
        mockMvc.perform(put("/api/v1/tasks/{id}", demoId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Renamed", "priority", "LOW", "version", 2))))
                .andExpect(status().isConflict());
        changeStatus(auth, demoId, "IN_PROGRESS", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt", nullValue()));

        // summary
        mockMvc.perform(get("/api/v1/tasks/summary").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.TODO").value(1))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.byStatus.DONE").value(0))
                .andExpect(jsonPath("$.byPriority.HIGH").value(1))
                .andExpect(jsonPath("$.byPriority.MEDIUM").value(1))
                .andExpect(jsonPath("$.overdue").value(0));

        // delete
        mockMvc.perform(delete("/api/v1/tasks/{id}", demoId).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tasks/{id}", demoId).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotSeeOrChangeAnotherUsersTask() throws Exception {
        String alice = newUserToken();
        String bob = newUserToken();
        long aliceTaskId = createTask(alice, Map.of("title", "Alice's task")).get("id").asLong();

        mockMvc.perform(get("/api/v1/tasks/{id}", aliceTaskId).header("Authorization", bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks/" + aliceTaskId));
        changeStatus(bob, aliceTaskId, "DONE", 0)
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/tasks/{id}", aliceTaskId).header("Authorization", bob))
                .andExpect(status().isNotFound());

        // and Bob's list does not include it
        mockMvc.perform(get("/api/v1/tasks").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staleVersionReturnsConflict() throws Exception {
        String auth = newUserToken();
        long taskId = createTask(auth, Map.of("title", "Contested task")).get("id").asLong();
        Map<String, Object> update = Map.of("title", "First edit", "priority", "HIGH", "version", 0);

        mockMvc.perform(put("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // second client still holds version 0
        mockMvc.perform(put("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Second edit", "priority", "LOW", "version", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidInputReturnsProblemDetailWithFieldErrors() throws Exception {
        String auth = newUserToken();

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "ab", "dueDate", "2000-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
                .andExpect(jsonPath("$.instance").value("/api/v1/tasks"))
                .andExpect(jsonPath("$.errors.title").value("must be between 3 and 120 characters"))
                .andExpect(jsonPath("$.errors.dueDate", notNullValue()));

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", auth).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size", notNullValue()));

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", auth).param("sort", "owner,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort", notNullValue()));

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", auth).param("status", "FINISHED"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions changeStatus(String auth, long taskId, String status, long version) throws Exception {
        return mockMvc.perform(patch("/api/v1/tasks/{id}/status", taskId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", status, "version", version))));
    }
}
