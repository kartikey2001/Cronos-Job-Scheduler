package com.chronos.repository;

import com.chronos.domain.DlqEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DlqRepository extends JpaRepository<DlqEntry, UUID> {
    List<DlqEntry> findByJobId(UUID jobId);
}
