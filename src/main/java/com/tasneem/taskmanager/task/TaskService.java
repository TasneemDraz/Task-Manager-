package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.common.PageResponse;
import com.tasneem.taskmanager.common.exception.BadRequestException;
import com.tasneem.taskmanager.common.exception.ConflictException;
import com.tasneem.taskmanager.common.exception.NotFoundException;
import com.tasneem.taskmanager.task.dto.CreateTaskRequest;
import com.tasneem.taskmanager.task.dto.TaskFilter;
import com.tasneem.taskmanager.task.dto.TaskResponse;
import com.tasneem.taskmanager.task.dto.TaskSummaryResponse;
import com.tasneem.taskmanager.task.dto.UpdateStatusRequest;
import com.tasneem.taskmanager.task.dto.UpdateTaskRequest;
import com.tasneem.taskmanager.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** All task business rules live here; the controller only translates HTTP. */
@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    static final int MAX_OPEN_TASKS = 200;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final Clock clock;

    public TaskResponse create(Long ownerId, CreateTaskRequest request) {
        if (taskRepository.countByOwnerIdAndStatusNot(ownerId, TaskStatus.DONE) >= MAX_OPEN_TASKS) {
            throw new ConflictException("Open task limit of " + MAX_OPEN_TASKS + " reached");
        }
        requireNotInPast(request.dueDate());

        Task task = new Task();
        task.setOwner(userRepository.getReferenceById(ownerId));
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        task.setDueDate(request.dueDate());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long ownerId, Long taskId) {
        return taskMapper.toResponse(findOwnedTask(ownerId, taskId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long ownerId, TaskFilter filter, int page, int size, String sort) {
        Specification<Task> spec = TaskSpecifications.ownedBy(ownerId)
                .and(TaskSpecifications.matching(filter, LocalDate.now(clock)));
        return search(spec, page, size, sort);
    }

    /** Admin view: every user's tasks, same filters, read-only. */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listAll(TaskFilter filter, int page, int size, String sort) {
        return search(TaskSpecifications.matching(filter, LocalDate.now(clock)), page, size, sort);
    }

    @Transactional(readOnly = true)
    public TaskSummaryResponse summary(Long ownerId) {
        Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        Arrays.stream(TaskStatus.values()).forEach(s -> byStatus.put(s, 0L));
        taskRepository.countByStatus(ownerId).forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));

        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        Arrays.stream(Priority.values()).forEach(p -> byPriority.put(p, 0L));
        taskRepository.countByPriority(ownerId).forEach(c -> byPriority.put(c.getPriority(), c.getTotal()));

        long overdue = taskRepository.countByOwnerIdAndDueDateBeforeAndStatusNot(
                ownerId, LocalDate.now(clock), TaskStatus.DONE);
        return new TaskSummaryResponse(byStatus, byPriority, overdue);
    }

    public TaskResponse update(Long ownerId, Long taskId, UpdateTaskRequest request) {
        Task task = findOwnedTask(ownerId, taskId);
        requireVersion(task, request.version());
        if (task.getStatus() == TaskStatus.DONE) {
            throw new ConflictException("A DONE task cannot be edited; reopen it first");
        }
        if (!Objects.equals(task.getDueDate(), request.dueDate())) {
            requireNotInPast(request.dueDate());
        }

        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    public TaskResponse updateStatus(Long ownerId, Long taskId, UpdateStatusRequest request) {
        Task task = findOwnedTask(ownerId, taskId);
        requireVersion(task, request.version());
        TaskStatus target = request.status();
        if (!task.getStatus().canMoveTo(target)) {
            throw new ConflictException("Cannot move task from " + task.getStatus() + " to " + target);
        }

        task.setStatus(target);
        task.setCompletedAt(target == TaskStatus.DONE ? clock.instant() : null);
        return taskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    public void delete(Long ownerId, Long taskId) {
        taskRepository.delete(findOwnedTask(ownerId, taskId));
    }

    private PageResponse<TaskResponse> search(Specification<Task> spec, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, TaskSorting.parse(sort));
        return PageResponse.from(taskRepository.findAll(spec, pageable), taskMapper::toResponse);
    }

    /** Returns 404 for both missing tasks and other users' tasks, so ids are not leaked. */
    private Task findOwnedTask(Long ownerId, Long taskId) {
        return taskRepository.findByIdAndOwnerId(taskId, ownerId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
    }

    /**
     * The client must send the version it last read. A stale version means someone else
     * changed the task in between. A race between two requests that both pass this check
     * is still caught by JPA's @Version on flush.
     */
    private static void requireVersion(Task task, Long clientVersion) {
        if (!task.getVersion().equals(clientVersion)) {
            throw new ConflictException("Task was modified by another request; reload and try again");
        }
    }

    private void requireNotInPast(LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.now(clock))) {
            throw new BadRequestException("dueDate", "must be a date in the present or in the future");
        }
    }
}
