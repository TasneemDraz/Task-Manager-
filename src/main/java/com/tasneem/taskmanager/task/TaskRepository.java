package com.tasneem.taskmanager.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    /** Ownership check built into the lookup: someone else's task is simply "not found". */
    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerIdAndStatusNot(Long ownerId, TaskStatus status);

    long countByOwnerIdAndDueDateBeforeAndStatusNot(Long ownerId, LocalDate date, TaskStatus status);

    @Query("select t.status as status, count(t) as total from Task t where t.owner.id = :ownerId group by t.status")
    List<StatusCount> countByStatus(Long ownerId);

    @Query("select t.priority as priority, count(t) as total from Task t where t.owner.id = :ownerId group by t.priority")
    List<PriorityCount> countByPriority(Long ownerId);

    interface StatusCount {
        TaskStatus getStatus();

        long getTotal();
    }

    interface PriorityCount {
        Priority getPriority();

        long getTotal();
    }
}
