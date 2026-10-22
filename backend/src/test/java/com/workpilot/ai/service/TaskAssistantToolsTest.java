package com.workpilot.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.ai.domain.ToolCallStatus;
import com.workpilot.ai.dto.CreateTaskRequest;
import com.workpilot.ai.exception.ForbiddenException;
import com.workpilot.ai.service.ai.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAssistantToolsTest {

    private final TaskService taskService = org.mockito.Mockito.mock(TaskService.class);
    private final TaskAssistantTools tools = new TaskAssistantTools(taskService, new ObjectMapper());
    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void rejectsIdentityFieldsAndMissingTitlesWithoutCreatingATask() {
        TaskAssistantTools.ToolHandling foreign = tools.handle(new AiToolCall(
                "call-1", "create_task", "{\"title\":\"Report\",\"organizationId\":\"" + UUID.randomUUID() + "\"}"),
                organizationId, userId, false);
        assertEquals(ToolCallStatus.FAILED, foreign.outcome().status());
        assertEquals("Unsupported argument.", foreign.outcome().errorMessage());

        TaskAssistantTools.ToolHandling missing = tools.handle(
                new AiToolCall("call-2", "create_task", "{\"priority\":\"HIGH\"}"), organizationId, userId, false);
        assertEquals(ToolCallStatus.FAILED, missing.outcome().status());
        assertEquals("{}", missing.outcome().argumentsJson());

        TaskAssistantTools.ToolHandling sql = tools.handle(
                new AiToolCall("call-3", "run_sql", "{\"query\":\"drop table tasks\"}"), organizationId, userId, false);
        assertEquals("{}", sql.outcome().argumentsJson());
        assertEquals("That action is not available.", sql.outcome().errorMessage());
        verify(taskService, never()).create(any(), any());
    }

    @Test
    void permissionFailureDoesNotReturnASavedTask() {
        when(taskService.create(eq(organizationId), any(CreateTaskRequest.class)))
                .thenThrow(new ForbiddenException("You do not have permission to perform this action"));
        TaskAssistantTools.ToolHandling handling = tools.executeConfirmed(
                "{\"title\":\"Report\",\"priority\":\"HIGH\"}", organizationId);
        assertEquals(ToolCallStatus.FAILED, handling.outcome().status());
        assertNull(handling.outcome().resultJson());
        assertEquals("You do not have permission to perform this action", handling.outcome().errorMessage());
    }

    @Test
    void lookupUsesTheAuthenticatedUserWhenMineIsRequested() {
        when(taskService.lookupForAssistant(organizationId, null, null, userId, true, null)).thenReturn(java.util.List.of());
        tools.handle(new AiToolCall("call-4", "list_tasks", "{\"overdue\":true,\"mine\":true,\"assigneeId\":\"" + UUID.randomUUID() + "\"}"),
                organizationId, userId, false);
        verify(taskService, never()).lookupForAssistant(any(), any(), any(), any(), eq(true), any());

        tools.handle(new AiToolCall("call-5", "list_tasks", "{\"overdue\":true,\"mine\":true}"), organizationId, userId, false);
        verify(taskService).lookupForAssistant(organizationId, null, null, userId, true, null);
    }
}
