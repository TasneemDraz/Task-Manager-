package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.auth.CurrentUser;
import com.tasneem.taskmanager.common.PageResponse;
import com.tasneem.taskmanager.task.dto.CreateTaskRequest;
import com.tasneem.taskmanager.task.dto.TaskFilter;
import com.tasneem.taskmanager.task.dto.TaskResponse;
import com.tasneem.taskmanager.task.dto.TaskSummaryResponse;
import com.tasneem.taskmanager.task.dto.UpdateStatusRequest;
import com.tasneem.taskmanager.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal CurrentUser user,
                                               @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.create(user.id(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public PageResponse<TaskResponse> list(@AuthenticationPrincipal CurrentUser user,
                                           @ParameterObject @ModelAttribute TaskFilter filter,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
                                           @RequestParam(defaultValue = TaskSorting.DEFAULT) String sort) {
        return taskService.list(user.id(), filter, page, size, sort);
    }

    @GetMapping("/summary")
    public TaskSummaryResponse summary(@AuthenticationPrincipal CurrentUser user) {
        return taskService.summary(user.id());
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return taskService.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return taskService.updateStatus(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        taskService.delete(user.id(), id);
    }
}
