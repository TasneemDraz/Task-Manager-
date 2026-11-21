package com.tasneem.taskmanager.admin;

import com.tasneem.taskmanager.common.PageResponse;
import com.tasneem.taskmanager.task.TaskService;
import com.tasneem.taskmanager.task.TaskSorting;
import com.tasneem.taskmanager.task.dto.TaskFilter;
import com.tasneem.taskmanager.task.dto.TaskResponse;
import com.tasneem.taskmanager.user.UserService;
import com.tasneem.taskmanager.user.dto.UserResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (enforced in SecurityConfig): read-only views plus user deactivation. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final TaskService taskService;

    @GetMapping("/users")
    public PageResponse<UserResponse> listUsers(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return userService.list(page, size);
    }

    @PatchMapping("/users/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateUser(@PathVariable Long id) {
        userService.deactivate(id);
    }

    @GetMapping("/tasks")
    public PageResponse<TaskResponse> listTasks(@ModelAttribute TaskFilter filter,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
                                                @RequestParam(defaultValue = TaskSorting.DEFAULT) String sort) {
        return taskService.listAll(filter, page, size, sort);
    }
}
