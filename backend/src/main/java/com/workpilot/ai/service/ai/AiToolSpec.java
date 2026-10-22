package com.workpilot.ai.service.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record AiToolSpec(String name, String description, JsonNode parameters) {
}
