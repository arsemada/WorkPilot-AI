package com.workpilot.ai.service;

import com.workpilot.ai.domain.ActivityEvent;
import com.workpilot.ai.domain.ActivityType;
import com.workpilot.ai.domain.Organization;
import com.workpilot.ai.domain.User;
import com.workpilot.ai.repository.ActivityEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class ActivityRecorder {

    private static final int MAX_SUMMARY_LENGTH = 300;

    private final ActivityEventRepository activityEventRepository;

    public ActivityRecorder(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    public void record(Organization organization, User actor, UUID taskId, ActivityType type, String summary) {
        Instant now = Instant.now();
        ActivityEvent event = new ActivityEvent();
        event.setOrganization(organization);
        event.setActor(actor);
        event.setTaskId(taskId);
        event.setType(type);
        event.setSummary(limit(summary));
        event.setOccurredAt(now);
        event.setOccurredOn(LocalDate.ofInstant(now, ZoneOffset.UTC));
        activityEventRepository.save(event);
    }

    private static String limit(String summary) {
        if (summary.length() <= MAX_SUMMARY_LENGTH) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY_LENGTH);
    }
}
