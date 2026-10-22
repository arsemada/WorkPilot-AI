package com.workpilot.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workpilot.ai.domain.TaskPriority;
import com.workpilot.ai.domain.TaskStatus;
import com.workpilot.ai.domain.ToolCallStatus;
import com.workpilot.ai.dto.CreateTaskRequest;
import com.workpilot.ai.dto.TaskResponse;
import com.workpilot.ai.exception.BadRequestException;
import com.workpilot.ai.exception.ForbiddenException;
import com.workpilot.ai.service.ai.AiToolCall;
import com.workpilot.ai.service.ai.AiToolSpec;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskAssistantTools {

    static final String LIST_TASKS = "list_tasks";
    static final String CREATE_TASK = "create_task";
    private static final Set<String> LIST_FIELDS = Set.of("overdue", "mine", "status", "priority", "query");
    private static final Set<String> CREATE_FIELDS = Set.of("title", "description", "priority", "dueDate");
    private static final int ARGUMENT_LIMIT = 4000;

    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskAssistantTools(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    public List<AiToolSpec> specs() {
        return List.of(
                new AiToolSpec(LIST_TASKS, "Look up tasks in the signed-in user's organization. Does not create or change tasks.", listSchema()),
                new AiToolSpec(CREATE_TASK, "Prepare a new to-do task. The task is not saved until the user confirms.", createSchema()));
    }

    public ToolHandling handle(AiToolCall call, UUID organizationId, UUID userId, boolean duplicate) {
        if (call.arguments() != null && call.arguments().length() > ARGUMENT_LIMIT) {
            return failed(call, "{}", "The tool arguments could not be read.");
        }
        if (LIST_TASKS.equals(call.name())) {
            return list(call, organizationId, userId, duplicate);
        }
        if (CREATE_TASK.equals(call.name())) {
            return propose(call, duplicate);
        }
        return failed(call, "{}", "That action is not available.");
    }

    public ToolHandling executeConfirmed(String argumentsJson, UUID organizationId) {
        ParsedCreate parsed = parseCreate(argumentsJson);
        if (parsed.error() != null) {
            return new ToolHandling(false, false, errorJson(parsed.error()), null, parsed.error());
        }
        try {
            TaskResponse saved = taskService.create(organizationId, parsed.request());
            String result = taskJson(saved);
            return new ToolHandling(false, false, result, new AgentToolOutcome(
                    null, CREATE_TASK, parsed.argumentsJson(), ToolCallStatus.COMPLETED, result, null), null);
        } catch (ForbiddenException exception) {
            return new ToolHandling(false, false, errorJson(exception.getMessage()), new AgentToolOutcome(
                    null, CREATE_TASK, parsed.argumentsJson(), ToolCallStatus.FAILED, null, exception.getMessage()), exception.getMessage());
        } catch (BadRequestException exception) {
            return new ToolHandling(false, false, errorJson(exception.getMessage()), new AgentToolOutcome(
                    null, CREATE_TASK, parsed.argumentsJson(), ToolCallStatus.FAILED, null, limit(exception.getMessage())), exception.getMessage());
        }
    }

    private ToolHandling list(AiToolCall call, UUID organizationId, UUID userId, boolean duplicate) {
        ParsedList parsed = parseList(call.arguments());
        if (parsed.error() != null) {
            return failed(call, "{}", parsed.error());
        }
        if (duplicate) {
            return new ToolHandling(false, false, "{\"error\":\"This lookup was already performed.\"}", null, null);
        }
        try {
            List<TaskResponse> tasks = taskService.lookupForAssistant(
                    organizationId,
                    parsed.status(),
                    parsed.priority(),
                    parsed.mine() ? userId : null,
                    parsed.overdue(),
                    parsed.query());
            String result = tasksJson(tasks);
            return new ToolHandling(false, false, result, new AgentToolOutcome(
                    UUID.randomUUID(), LIST_TASKS, parsed.argumentsJson(), ToolCallStatus.COMPLETED, result, null), null);
        } catch (ForbiddenException exception) {
            return failed(call, parsed.argumentsJson(), exception.getMessage());
        } catch (BadRequestException exception) {
            return failed(call, parsed.argumentsJson(), limit(exception.getMessage()));
        }
    }

    private ToolHandling propose(AiToolCall call, boolean duplicate) {
        if (duplicate) {
            return new ToolHandling(false, false, errorJson("Only one task can be prepared at a time."), null, null);
        }
        ParsedCreate parsed = parseCreate(call.arguments());
        if (parsed.error() != null) {
            return failed(call, "{}", parsed.error());
        }
        return new ToolHandling(true, false, null, new AgentToolOutcome(
                UUID.randomUUID(), CREATE_TASK, parsed.argumentsJson(), ToolCallStatus.PROPOSED, null, null), proposalText(parsed.request()));
    }

    private ToolHandling failed(AiToolCall call, String argumentsJson, String message) {
        return new ToolHandling(false, false, errorJson(message), new AgentToolOutcome(
                UUID.randomUUID(), safeName(call.name()), argumentsJson, ToolCallStatus.FAILED, null, limit(message)), null);
    }

    private ParsedList parseList(String arguments) {
        JsonNode node = objectNode(arguments);
        if (node == null) {
            return ParsedList.error("The tool arguments could not be read.");
        }
        if (unknown(node, LIST_FIELDS)) {
            return ParsedList.error("Unsupported argument.");
        }
        Boolean overdue = bool(node, "overdue");
        Boolean mine = bool(node, "mine");
        if (node.has("overdue") && !node.get("overdue").isNull() && overdue == null) {
            return ParsedList.error("overdue must be true or false.");
        }
        if (node.has("mine") && !node.get("mine").isNull() && mine == null) {
            return ParsedList.error("mine must be true or false.");
        }
        TaskStatus status = enumValue(node, "status", TaskStatus.class);
        if (node.hasNonNull("status") && status == null) {
            return ParsedList.error("Status must be TODO, IN_PROGRESS, DONE, or CANCELLED.");
        }
        TaskPriority priority = enumValue(node, "priority", TaskPriority.class);
        if (node.hasNonNull("priority") && priority == null) {
            return ParsedList.error("Priority must be LOW, MEDIUM, HIGH, or URGENT.");
        }
        String query = text(node, "query");
        if (query != null && query.length() > 200) {
            return ParsedList.error("Search text must be at most 200 characters.");
        }
        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("overdue", Boolean.TRUE.equals(overdue));
        stored.put("mine", Boolean.TRUE.equals(mine));
        if (status != null) {
            stored.put("status", status.name());
        }
        if (priority != null) {
            stored.put("priority", priority.name());
        }
        if (query != null && !query.isBlank()) {
            stored.put("query", query.trim());
        }
        return new ParsedList(null, Boolean.TRUE.equals(overdue), Boolean.TRUE.equals(mine), status, priority, query, stored.toString());
    }

    private ParsedCreate parseCreate(String arguments) {
        JsonNode node = objectNode(arguments);
        if (node == null) {
            return ParsedCreate.error("The tool arguments could not be read.");
        }
        if (unknown(node, CREATE_FIELDS)) {
            return ParsedCreate.error("Unsupported argument.");
        }
        String title = text(node, "title");
        if (title == null || title.isBlank()) {
            return ParsedCreate.error("Title is required. Ask the user what the task should be called.");
        }
        title = title.trim();
        if (title.length() > 200) {
            return ParsedCreate.error("Title must be at most 200 characters.");
        }
        String description = text(node, "description");
        if (description != null && description.trim().length() > 5000) {
            return ParsedCreate.error("Description must be at most 5000 characters.");
        }
        description = description == null || description.isBlank() ? null : description.trim();
        TaskPriority priority = null;
        if (node.has("priority") && !node.get("priority").isNull()) {
            priority = enumValue(node, "priority", TaskPriority.class);
            if (priority == null) {
                return ParsedCreate.error("Priority must be LOW, MEDIUM, HIGH, or URGENT.");
            }
        }
        LocalDate dueDate = null;
        if (node.has("dueDate") && !node.get("dueDate").isNull()) {
            String raw = text(node, "dueDate");
            if (raw == null || raw.isBlank()) {
                dueDate = null;
            } else {
                try {
                    dueDate = LocalDate.parse(raw.trim());
                } catch (DateTimeParseException exception) {
                    return ParsedCreate.error("The due date must use YYYY-MM-DD.");
                }
            }
        }
        CreateTaskRequest request = new CreateTaskRequest(title, description, null, priority, dueDate, null);
        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("title", title);
        if (description != null) {
            stored.put("description", description);
        }
        if (priority != null) {
            stored.put("priority", priority.name());
        }
        if (dueDate != null) {
            stored.put("dueDate", dueDate.toString());
        }
        return new ParsedCreate(null, request, stored.toString());
    }

    public static String proposalText(CreateTaskRequest request) {
        StringBuilder builder = new StringBuilder("Please confirm this task before it is saved: ");
        builder.append(request.title());
        if (request.priority() != null) {
            builder.append(" (").append(request.priority().name()).append(')');
        }
        if (request.dueDate() != null) {
            builder.append(", due ").append(request.dueDate());
        }
        builder.append('.');
        return builder.toString();
    }

    private JsonNode objectNode(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(arguments);
            return node.isObject() ? node : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean unknown(JsonNode node, Set<String> allowed) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                return true;
            }
        }
        return false;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isBoolean()) {
            return null;
        }
        return value.booleanValue();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : null;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> type) {
        String raw = text(node, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String tasksJson(List<TaskResponse> tasks) {
        ArrayNode array = objectMapper.createArrayNode();
        for (TaskResponse task : tasks) {
            array.add(taskNode(task));
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.set("tasks", array);
        return root.toString();
    }

    private String taskJson(TaskResponse task) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("task", taskNode(task));
        return root.toString();
    }

    private ObjectNode taskNode(TaskResponse task) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", task.id().toString());
        node.put("title", task.title());
        node.put("status", task.status().name());
        node.put("priority", task.priority().name());
        if (task.dueDate() != null) {
            node.put("dueDate", task.dueDate().toString());
        } else {
            node.putNull("dueDate");
        }
        return node;
    }

    private String errorJson(String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("error", limit(message));
        return node.toString();
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String trimmed = name.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40);
    }

    private static String limit(String message) {
        if (message == null) {
            return "The action could not be completed.";
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private JsonNode listSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("overdue").put("type", "boolean");
        properties.putObject("mine").put("type", "boolean")
                .put("description", "Limit the lookup to tasks assigned to the signed-in user.");
        ObjectNode status = properties.putObject("status");
        status.put("type", "string");
        status.set("enum", objectMapper.createArrayNode().add("TODO").add("IN_PROGRESS").add("DONE").add("CANCELLED"));
        ObjectNode priority = properties.putObject("priority");
        priority.put("type", "string");
        priority.set("enum", objectMapper.createArrayNode().add("LOW").add("MEDIUM").add("HIGH").add("URGENT"));
        properties.putObject("query").put("type", "string");
        return schema;
    }

    private JsonNode createSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("title").put("type", "string");
        properties.putObject("description").put("type", "string");
        ObjectNode priority = properties.putObject("priority");
        priority.put("type", "string");
        priority.set("enum", objectMapper.createArrayNode().add("LOW").add("MEDIUM").add("HIGH").add("URGENT"));
        properties.putObject("dueDate").put("type", "string").put("description", "YYYY-MM-DD");
        schema.putArray("required").add("title");
        return schema;
    }

    public record ToolHandling(
            boolean propose,
            boolean stop,
            String modelContent,
            AgentToolOutcome outcome,
            String proposalText
    ) {
    }

    private record ParsedList(
            String error,
            boolean overdue,
            boolean mine,
            TaskStatus status,
            TaskPriority priority,
            String query,
            String argumentsJson
    ) {
        static ParsedList error(String message) {
            return new ParsedList(message, false, false, null, null, null, "{}");
        }
    }

    private record ParsedCreate(String error, CreateTaskRequest request, String argumentsJson) {
        static ParsedCreate error(String message) {
            return new ParsedCreate(message, null, "{}");
        }
    }
}
