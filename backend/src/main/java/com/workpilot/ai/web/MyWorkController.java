package com.workpilot.ai.web;

import com.workpilot.ai.dto.MyWorkResponse;
import com.workpilot.ai.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations/{organizationId}/my-work")
@Tag(name = "My work")
public class MyWorkController {

    private final TaskService taskService;

    public MyWorkController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "List the signed-in member's open assigned tasks")
    public MyWorkResponse myWork(@PathVariable UUID organizationId) {
        return taskService.myWork(organizationId);
    }
}
