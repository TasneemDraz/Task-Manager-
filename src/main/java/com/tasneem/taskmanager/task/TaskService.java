package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.common.exception.NotFoundException;
import com.tasneem.taskmanager.task.dto.CreateTaskRequest;
import com.tasneem.taskmanager.task.dto.TaskResponse;
import com.tasneem.taskmanager.task.dto.UpdateStatusRequest;
import com.tasneem.taskmanager.task.dto.UpdateTaskRequest;
import com.tasneem.taskmanager.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;

    public TaskResponse create(Long ownerId, CreateTaskRequest request) {
        Task task = new Task();
        task.setOwner(userRepository.getReferenceById(ownerId));
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setDueDate(request.dueDate());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long ownerId, Long taskId) {
        return taskMapper.toResponse(findOwnedTask(ownerId, taskId));
    }

    public TaskResponse update(Long ownerId, Long taskId, UpdateTaskRequest request) {
        Task task = findOwnedTask(ownerId, taskId);
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    public TaskResponse updateStatus(Long ownerId, Long taskId, UpdateStatusRequest request) {
        Task task = findOwnedTask(ownerId, taskId);
        task.setStatus(request.status());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    public void delete(Long ownerId, Long taskId) {
        taskRepository.delete(findOwnedTask(ownerId, taskId));
    }

    /** Returns 404 for both missing tasks and other users' tasks, so ids are not leaked. */
    private Task findOwnedTask(Long ownerId, Long taskId) {
        return taskRepository.findByIdAndOwnerId(taskId, ownerId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }
}
