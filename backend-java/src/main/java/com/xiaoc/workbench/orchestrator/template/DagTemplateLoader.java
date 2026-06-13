package com.xiaoc.workbench.orchestrator.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Service
public class DagTemplateLoader {
    private final Path templateDirectory;

    public DagTemplateLoader() {
        this("../templates/dags");
    }

    public DagTemplateLoader(String templateDirectory) {
        this.templateDirectory = Path.of(templateDirectory);
    }

    public DagTemplate load(String templateId) {
        Path templatePath = resolveTemplatePath(templateId);
        if (!Files.exists(templatePath)) {
            throw new IllegalArgumentException("DAG template not found: " + templateId);
        }

        try (InputStream input = Files.newInputStream(templatePath)) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> yaml)) {
                throw new IllegalArgumentException("DAG template is not a YAML object: " + templateId);
            }
            return parseTemplate(templateId, yaml);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read DAG template: " + templateId, exception);
        }
    }

    private Path resolveTemplatePath(String templateId) {
        Path candidate = templateDirectory.resolve(templateId + ".yaml");
        if (Files.exists(candidate)) {
            return candidate;
        }
        Path rootRelative = Path.of("templates", "dags", templateId + ".yaml");
        if (Files.exists(rootRelative)) {
            return rootRelative;
        }
        return candidate;
    }

    private DagTemplate parseTemplate(String requestedTemplateId, Map<?, ?> yaml) {
        String id = requiredString(yaml, "id");
        if (!requestedTemplateId.equals(id)) {
            throw new IllegalArgumentException("DAG template id mismatch: " + requestedTemplateId);
        }

        List<DagTemplate.Node> nodes = parseNodes(yaml.get("nodes"));
        validateNodes(id, nodes);
        return new DagTemplate(
                id,
                requiredString(yaml, "name"),
                requiredString(yaml, "mode"),
                requiredString(yaml, "domain"),
                List.copyOf(nodes));
    }

    private List<DagTemplate.Node> parseNodes(Object value) {
        if (!(value instanceof List<?> rawNodes)) {
            throw new IllegalArgumentException("DAG template nodes must be a list");
        }

        List<DagTemplate.Node> nodes = new ArrayList<>();
        for (Object rawNode : rawNodes) {
            if (!(rawNode instanceof Map<?, ?> node)) {
                throw new IllegalArgumentException("DAG template node must be an object");
            }
            nodes.add(new DagTemplate.Node(
                    requiredString(node, "id"),
                    requiredString(node, "name"),
                    requiredString(node, "kind"),
                    requiredString(node, "role"),
                    dependsOn(node.get("depends_on"))));
        }
        return nodes;
    }

    private List<String> dependsOn(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> rawDependencies)) {
            throw new IllegalArgumentException("DAG template depends_on must be a list");
        }
        return rawDependencies.stream()
                .map(Object::toString)
                .toList();
    }

    private void validateNodes(String templateId, List<DagTemplate.Node> nodes) {
        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (DagTemplate.Node node : nodes) {
            if (!nodeIds.add(node.id())) {
                duplicates.add(node.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("DAG template has duplicate node ids: " + templateId);
        }

        for (DagTemplate.Node node : nodes) {
            for (String dependency : node.dependsOn()) {
                if (!nodeIds.contains(dependency)) {
                    throw new IllegalArgumentException(
                            "DAG template node " + node.id() + " has unknown dependency: " + dependency);
                }
            }
        }
    }

    private String requiredString(Map<?, ?> yaml, String key) {
        Object value = yaml.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("DAG template missing required field: " + key);
        }
        return value.toString();
    }
}
