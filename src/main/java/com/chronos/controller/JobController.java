package com.chronos.controller;

import com.chronos.domain.User;
import com.chronos.dto.CreateJobRequest;
import com.chronos.dto.DlqEntryResponse;
import com.chronos.dto.JobResponse;
import com.chronos.dto.PageResponse;
import com.chronos.dto.UpdateJobRequest;
import com.chronos.service.DlqService;
import com.chronos.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final DlqService dlqService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@AuthenticationPrincipal User currentUser,
                                 @Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(currentUser.getId(), request);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@AuthenticationPrincipal User currentUser,
                              @PathVariable UUID id) {
        return jobService.getJob(currentUser.getId(), id);
    }

    @GetMapping
    public PageResponse<JobResponse> listJobs(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return jobService.listJobs(currentUser.getId(), pageable);
    }

    @PutMapping("/{id}")
    public JobResponse updateJob(@AuthenticationPrincipal User currentUser,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody UpdateJobRequest request) {
        return jobService.updateJob(currentUser.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelJob(@AuthenticationPrincipal User currentUser,
                          @PathVariable UUID id) {
        jobService.cancelJob(currentUser.getId(), id);
    }

    @GetMapping("/{id}/dlq")
    public List<DlqEntryResponse> getDlqEntries(@AuthenticationPrincipal User currentUser,
                                                 @PathVariable UUID id) {
        return dlqService.getDlqEntries(currentUser.getId(), id);
    }

    @PostMapping("/{id}/dlq/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retryFromDlq(@AuthenticationPrincipal User currentUser,
                              @PathVariable UUID id) {
        dlqService.retryFromDlq(currentUser.getId(), id);
    }
}
