package com.tasneem.taskmanager.task;

import com.tasneem.taskmanager.common.exception.BadRequestException;
import com.tasneem.taskmanager.common.exception.ConflictException;
import com.tasneem.taskmanager.common.exception.NotFoundException;
import com.tasneem.taskmanager.task.dto.CreateTaskRequest;
import com.tasneem.taskmanager.task.dto.TaskFilter;
import com.tasneem.taskmanager.task.dto.TaskResponse;
import com.tasneem.taskmanager.task.dto.TaskSummaryResponse;
import com.tasneem.taskmanager.task.dto.UpdateStatusRequest;
import com.tasneem.taskmanager.task.dto.UpdateTaskRequest;
import com.tasneem.taskmanager.user.User;
import com.tasneem.taskmanager.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final long OWNER_ID = 1L;
    private static final long TASK_ID = 42L;
    private static final long CURRENT_VERSION = 3L;

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, userRepository, new TaskMapper(CLOCK), CLOCK);
    }

    private Task existingTask(TaskStatus status) {
        Task task = new Task();
        task.setId(TASK_ID);
        task.setTitle("Write tests");
        task.setStatus(status);
        task.setPriority(Priority.MEDIUM);
        task.setVersion(CURRENT_VERSION);
        if (status == TaskStatus.DONE) {
            task.setCompletedAt(NOW.minusSeconds(3600));
        }
        return task;
    }

    private void givenOwnedTask(Task task) {
        when(taskRepository.findByIdAndOwnerId(TASK_ID, OWNER_ID)).thenReturn(Optional.of(task));
    }

    private void givenSaveReturnsEntity() {
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UpdateTaskRequest update(LocalDate dueDate, long version) {
        return new UpdateTaskRequest("New title", "New description", Priority.HIGH, dueDate, version);
    }

    @Nested
    @DisplayName("Rule 1: ownership")
    class Ownership {

        @Test
        void returnsNotFoundForAnotherUsersTask() {
            when(taskRepository.findByIdAndOwnerId(TASK_ID, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.get(OWNER_ID, TASK_ID))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void cannotUpdateAnotherUsersTask() {
            when(taskRepository.findByIdAndOwnerId(TASK_ID, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.update(OWNER_ID, TASK_ID, update(null, CURRENT_VERSION)))
                    .isInstanceOf(NotFoundException.class);
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void returnsOwnTask() {
            givenOwnedTask(existingTask(TaskStatus.TODO));

            TaskResponse response = taskService.get(OWNER_ID, TASK_ID);

            assertThat(response.id()).isEqualTo(TASK_ID);
        }
    }

    @Nested
    @DisplayName("Rules 2 and 3: status moves and completedAt")
    class StatusMoves {

        @ParameterizedTest(name = "{0} -> {1} is allowed")
        @CsvSource({"TODO, IN_PROGRESS", "IN_PROGRESS, DONE", "TODO, DONE", "DONE, IN_PROGRESS"})
        void allowsValidMoves(TaskStatus from, TaskStatus to) {
            givenOwnedTask(existingTask(from));
            givenSaveReturnsEntity();

            TaskResponse response = taskService.updateStatus(OWNER_ID, TASK_ID,
                    new UpdateStatusRequest(to, CURRENT_VERSION));

            assertThat(response.status()).isEqualTo(to);
        }

        @ParameterizedTest(name = "{0} -> {1} is rejected")
        @CsvSource({"IN_PROGRESS, TODO", "DONE, TODO", "TODO, TODO", "IN_PROGRESS, IN_PROGRESS", "DONE, DONE"})
        void rejectsInvalidMoves(TaskStatus from, TaskStatus to) {
            givenOwnedTask(existingTask(from));

            assertThatThrownBy(() -> taskService.updateStatus(OWNER_ID, TASK_ID,
                    new UpdateStatusRequest(to, CURRENT_VERSION)))
                    .isInstanceOf(ConflictException.class);
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void setsCompletedAtWhenDone() {
            givenOwnedTask(existingTask(TaskStatus.IN_PROGRESS));
            givenSaveReturnsEntity();

            TaskResponse response = taskService.updateStatus(OWNER_ID, TASK_ID,
                    new UpdateStatusRequest(TaskStatus.DONE, CURRENT_VERSION));

            assertThat(response.completedAt()).isEqualTo(NOW);
        }

        @Test
        void clearsCompletedAtWhenReopened() {
            givenOwnedTask(existingTask(TaskStatus.DONE));
            givenSaveReturnsEntity();

            TaskResponse response = taskService.updateStatus(OWNER_ID, TASK_ID,
                    new UpdateStatusRequest(TaskStatus.IN_PROGRESS, CURRENT_VERSION));

            assertThat(response.completedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Rule 4: DONE tasks are locked")
    class DoneLock {

        @Test
        void rejectsEditingDoneTask() {
            givenOwnedTask(existingTask(TaskStatus.DONE));

            assertThatThrownBy(() -> taskService.update(OWNER_ID, TASK_ID, update(null, CURRENT_VERSION)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("reopen");
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void allowsEditingOpenTask() {
            givenOwnedTask(existingTask(TaskStatus.IN_PROGRESS));
            givenSaveReturnsEntity();

            TaskResponse response = taskService.update(OWNER_ID, TASK_ID, update(TODAY.plusDays(3), CURRENT_VERSION));

            assertThat(response.title()).isEqualTo("New title");
            assertThat(response.priority()).isEqualTo(Priority.HIGH);
            assertThat(response.dueDate()).isEqualTo(TODAY.plusDays(3));
        }
    }

    @Nested
    @DisplayName("Rule 5: due date cannot be in the past")
    class DueDate {

        @Test
        void rejectsPastDueDateOnCreate() {
            var request = new CreateTaskRequest("Write tests", null, null, TODAY.minusDays(1));

            assertThatThrownBy(() -> taskService.create(OWNER_ID, request))
                    .isInstanceOf(BadRequestException.class);
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void acceptsTodayOnCreate() {
            givenSaveReturnsEntity();
            var request = new CreateTaskRequest("Write tests", null, null, TODAY);

            assertThat(taskService.create(OWNER_ID, request).dueDate()).isEqualTo(TODAY);
        }

        @Test
        void rejectsChangingDueDateToThePast() {
            givenOwnedTask(existingTask(TaskStatus.TODO));

            assertThatThrownBy(() -> taskService.update(OWNER_ID, TASK_ID, update(TODAY.minusDays(1), CURRENT_VERSION)))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void allowsKeepingAnUnchangedPastDueDate() {
            Task task = existingTask(TaskStatus.TODO);
            task.setDueDate(TODAY.minusDays(5));   // was valid when set, now overdue
            givenOwnedTask(task);
            givenSaveReturnsEntity();

            TaskResponse response = taskService.update(OWNER_ID, TASK_ID, update(TODAY.minusDays(5), CURRENT_VERSION));

            assertThat(response.title()).isEqualTo("New title");
            assertThat(response.overdue()).isTrue();
        }
    }

    @Nested
    @DisplayName("Rule 6: at most 200 open tasks")
    class OpenTaskLimit {

        @Test
        void rejectsCreateWhenLimitReached() {
            when(taskRepository.countByOwnerIdAndStatusNot(OWNER_ID, TaskStatus.DONE))
                    .thenReturn((long) TaskService.MAX_OPEN_TASKS);

            assertThatThrownBy(() -> taskService.create(OWNER_ID, new CreateTaskRequest("Write tests", null, null, null)))
                    .isInstanceOf(ConflictException.class);
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void allowsCreateBelowLimit() {
            when(taskRepository.countByOwnerIdAndStatusNot(OWNER_ID, TaskStatus.DONE))
                    .thenReturn((long) TaskService.MAX_OPEN_TASKS - 1);
            givenSaveReturnsEntity();

            assertThat(taskService.create(OWNER_ID, new CreateTaskRequest("Write tests", null, null, null)))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Rule 7: hard delete by owner only")
    class Delete {

        @Test
        void deletesOwnTask() {
            Task task = existingTask(TaskStatus.TODO);
            givenOwnedTask(task);

            taskService.delete(OWNER_ID, TASK_ID);

            verify(taskRepository).delete(task);
        }

        @Test
        void doesNotDeleteAnotherUsersTask() {
            when(taskRepository.findByIdAndOwnerId(TASK_ID, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.delete(OWNER_ID, TASK_ID))
                    .isInstanceOf(NotFoundException.class);
            verify(taskRepository, never()).delete(any(Task.class));
        }
    }

    @Nested
    @DisplayName("Rule 10: optimistic locking")
    class OptimisticLocking {

        @Test
        void rejectsUpdateWithStaleVersion() {
            givenOwnedTask(existingTask(TaskStatus.TODO));

            assertThatThrownBy(() -> taskService.update(OWNER_ID, TASK_ID, update(null, CURRENT_VERSION - 1)))
                    .isInstanceOf(ConflictException.class);
            verify(taskRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsStatusChangeWithStaleVersion() {
            givenOwnedTask(existingTask(TaskStatus.TODO));

            assertThatThrownBy(() -> taskService.updateStatus(OWNER_ID, TASK_ID,
                    new UpdateStatusRequest(TaskStatus.IN_PROGRESS, CURRENT_VERSION - 1)))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class Create {

        @Test
        void appliesDefaultsAndOwner() {
            User owner = new User();
            when(userRepository.getReferenceById(OWNER_ID)).thenReturn(owner);
            givenSaveReturnsEntity();

            taskService.create(OWNER_ID, new CreateTaskRequest("  Write tests  ", "desc", null, null));

            ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getOwner()).isSameAs(owner);
            assertThat(saved.getValue().getTitle()).isEqualTo("Write tests");
            assertThat(saved.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
            assertThat(saved.getValue().getPriority()).isEqualTo(Priority.MEDIUM);
        }
    }

    @Nested
    class ListAndSummary {

        @Test
        void listsWithRequestedPagingAndSort() {
            Task task = existingTask(TaskStatus.TODO);
            when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenAnswer(inv -> new PageImpl<>(List.of(task), inv.getArgument(1), 11));
            var filter = new TaskFilter(List.of(TaskStatus.TODO), null, null, null, null, "tests");

            var page = taskService.list(OWNER_ID, filter, 1, 5, "dueDate,asc");

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(taskRepository).findAll(any(Specification.class), pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
            assertThat(pageable.getValue().getSort().getOrderFor("dueDate").getDirection()).isEqualTo(Sort.Direction.ASC);
            assertThat(page.content()).hasSize(1);
            assertThat(page.totalElements()).isEqualTo(11);
            assertThat(page.totalPages()).isEqualTo(3);
        }

        @Test
        void rejectsUnknownSortField() {
            var filter = new TaskFilter(null, null, null, null, null, null);

            assertThatThrownBy(() -> taskService.list(OWNER_ID, filter, 0, 10, "password,asc"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        void userListOmitsOwner() {
            Task task = existingTask(TaskStatus.TODO);
            when(taskRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));

            var page = taskService.list(OWNER_ID, new TaskFilter(null, null, null, null, null, null), 0, 10, TaskSorting.DEFAULT);

            assertThat(page.content().getFirst().owner()).isNull();
        }

        @Test
        void adminListIncludesOwner() {
            User owner = new User("Jane Doe", "jane@example.com", "hash");
            owner.setId(OWNER_ID);
            Task task = existingTask(TaskStatus.TODO);
            task.setOwner(owner);
            Page<Task> result = new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1);
            when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(result);

            var page = taskService.listAll(new TaskFilter(null, null, null, null, true, null), 0, 10, TaskSorting.DEFAULT);

            assertThat(page.content().getFirst().owner()).isEqualTo(new TaskResponse.Owner(OWNER_ID, "jane@example.com"));
            assertThat(page.size()).isEqualTo(10);
        }

        @Test
        void summaryFillsMissingCountsWithZero() {
            when(taskRepository.countByStatus(OWNER_ID)).thenReturn(List.of(statusCount(TaskStatus.TODO, 4)));
            when(taskRepository.countByPriority(OWNER_ID)).thenReturn(List.of(priorityCount(Priority.HIGH, 4)));
            when(taskRepository.countByOwnerIdAndDueDateBeforeAndStatusNot(eq(OWNER_ID), eq(TODAY), eq(TaskStatus.DONE)))
                    .thenReturn(2L);

            TaskSummaryResponse summary = taskService.summary(OWNER_ID);

            assertThat(summary.byStatus()).containsEntry(TaskStatus.TODO, 4L)
                    .containsEntry(TaskStatus.IN_PROGRESS, 0L)
                    .containsEntry(TaskStatus.DONE, 0L);
            assertThat(summary.byPriority()).containsEntry(Priority.HIGH, 4L)
                    .containsEntry(Priority.LOW, 0L)
                    .containsEntry(Priority.MEDIUM, 0L);
            assertThat(summary.overdue()).isEqualTo(2L);
        }

        private TaskRepository.StatusCount statusCount(TaskStatus status, long total) {
            return new TaskRepository.StatusCount() {
                public TaskStatus getStatus() { return status; }
                public long getTotal() { return total; }
            };
        }

        private TaskRepository.PriorityCount priorityCount(Priority priority, long total) {
            return new TaskRepository.PriorityCount() {
                public Priority getPriority() { return priority; }
                public long getTotal() { return total; }
            };
        }
    }

    @Nested
    class Overdue {

        @Test
        void isOverdueOnlyWhenPastDueAndNotDone() {
            Task task = existingTask(TaskStatus.IN_PROGRESS);
            task.setDueDate(TODAY.minusDays(1));
            assertThat(task.isOverdue(TODAY)).isTrue();

            task.setStatus(TaskStatus.DONE);
            assertThat(task.isOverdue(TODAY)).isFalse();

            task.setStatus(TaskStatus.TODO);
            task.setDueDate(TODAY);
            assertThat(task.isOverdue(TODAY)).isFalse();
        }
    }
}
