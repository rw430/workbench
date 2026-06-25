package com.xiaoc.workbench.orchestrator.queue;

public interface RateLimitService {
    void checkAllowed(String actorId, String action);
}
