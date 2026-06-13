package com.xiaoc.workbench.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "agents")
public class AgentProfile {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String skills;

    @Column(nullable = false)
    private String domainTags;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Column(nullable = false)
    private String recommendationReason;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected AgentProfile() {
    }

    public AgentProfile(
        String id,
        String name,
        String role,
        String description,
        String skills,
        String domainTags,
        BigDecimal score,
        String recommendationReason,
        int sortOrder
    ) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.description = description;
        this.skills = skills;
        this.domainTags = domainTags;
        this.enabled = true;
        this.score = score;
        this.recommendationReason = recommendationReason;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getDescription() { return description; }
    public String getSkills() { return skills; }
    public String getDomainTags() { return domainTags; }
    public boolean isEnabled() { return enabled; }
    public BigDecimal getScore() { return score; }
    public String getRecommendationReason() { return recommendationReason; }
    public int getSortOrder() { return sortOrder; }
}
