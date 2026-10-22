package com.workpilot.ai.service.ai;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embed(List<String> inputs);
}
