package com.tasneem.taskmanager.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Ownership check built into the lookup: someone else's task is simply "not found". */
    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);
}
