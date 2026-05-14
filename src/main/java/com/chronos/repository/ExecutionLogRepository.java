package com.chronos.repository;

import com.chronos.domain.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, UUID> {
    List<ExecutionLog> findByJobId(UUID jobId);
}
