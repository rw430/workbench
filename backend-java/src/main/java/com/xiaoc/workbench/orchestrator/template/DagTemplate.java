package com.xiaoc.workbench.orchestrator.template;

import java.util.List;

public record DagTemplate(
        String id,
        String name,
        String mode,
        String domain,
        List<Node> nodes
) {
    public record Node(
            String id,
            String name,
            String kind,
            String role,
            List<String> dependsOn
    ) {
    }
}
