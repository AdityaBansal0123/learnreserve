package com.learnreserve.booking;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.completion.enabled", havingValue = "true", matchIfMissing = true)
public class CompletionScheduler {
    private final CompletionService service;
    public CompletionScheduler(CompletionService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${app.completion.delay-ms:60000}")
    public void complete() { service.completeEndedBookings(); }
}
