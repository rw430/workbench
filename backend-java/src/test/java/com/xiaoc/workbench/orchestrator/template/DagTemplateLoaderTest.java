package com.xiaoc.workbench.orchestrator.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DagTemplateLoaderTest {
    @Test
    void loadsCreditCardInstallmentTemplate() {
        DagTemplateLoader loader = new DagTemplateLoader("../templates/dags");

        DagTemplate template = loader.load("credit_card_installment_campaign_v1");

        assertThat(template.id()).isEqualTo("credit_card_installment_campaign_v1");
        assertThat(template.mode()).isEqualTo("tasks");
        assertThat(template.nodes()).extracting(DagTemplate.Node::id)
                .containsExactly(
                        "need_analysis",
                        "human_gate_prd",
                        "risk_compliance_review",
                        "tech_design",
                        "test_plan",
                        "delivery_summary",
                        "reflection",
                        "lessons_extract");
        assertThat(template.nodes().get(1).dependsOn()).containsExactly("need_analysis");
    }

    @Test
    void rejectsMissingTemplate() {
        DagTemplateLoader loader = new DagTemplateLoader("../templates/dags");

        assertThatThrownBy(() -> loader.load("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAG template not found");
    }

    @Test
    void rejectsUnknownDependency(@TempDir Path tempDir) throws IOException {
        Files.writeString(
                tempDir.resolve("invalid.yaml"),
                """
                id: invalid
                name: Invalid
                mode: tasks
                domain: banking_credit_card
                nodes:
                  - id: first
                    name: First
                    kind: LLM_PRD_DRAFT
                    role: PD
                    depends_on: [missing]
                """);
        DagTemplateLoader loader = new DagTemplateLoader(tempDir.toString());

        assertThatThrownBy(() -> loader.load("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown dependency");
    }
}
