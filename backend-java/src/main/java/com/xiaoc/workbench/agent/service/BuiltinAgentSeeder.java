package com.xiaoc.workbench.agent.service;

import com.xiaoc.workbench.agent.domain.AgentProfile;
import com.xiaoc.workbench.agent.repository.AgentProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class BuiltinAgentSeeder {
    private final AgentProfileRepository repository;

    public BuiltinAgentSeeder(AgentProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void seedBuiltinAgents() {
        for (AgentProfile agent : builtinAgents()) {
            if (!repository.existsById(agent.getId())) {
                repository.save(agent);
            }
        }
    }

    private List<AgentProfile> builtinAgents() {
        return List.of(
            new AgentProfile(
                "agent-pd-default",
                "需求分析分身",
                "PD",
                "负责目标澄清、PRD 草稿、活动配置边界和验收标准。",
                "PRD,需求分析,验收标准,活动配置,审批流程",
                "银行,信用卡,分期活动,产品研发",
                new BigDecimal("0.9500"),
                "命中信用卡分期活动配置、审批流程和 PRD 生成能力。",
                0
            ),
            new AgentProfile(
                "agent-dev-default",
                "研发实现分身",
                "DEV",
                "负责技术拆解、接口设计、状态机、幂等和中间件落地建议。",
                "Java,Spring Boot,接口设计,状态机,Redis,RabbitMQ",
                "后端研发,系统设计,信用卡活动配置",
                new BigDecimal("0.9000"),
                "命中活动配置、审批流、灰度发布和后端工程落地能力。",
                1
            ),
            new AgentProfile(
                "agent-qa-default",
                "质量保障分身",
                "QA",
                "负责测试策略、规则校验、审批流回归范围和质量总结。",
                "测试计划,回归验证,规则校验,审批流测试,质量评估",
                "测试,质量,信用卡分期,验收",
                new BigDecimal("0.8800"),
                "命中规则校验、审批流、灰度发布和质量评审能力。",
                2
            ),
            new AgentProfile(
                "agent-risk-default",
                "风险合规评审分身",
                "RISK",
                "负责识别金融业务边界、敏感数据约束、操作审计和合规风险。",
                "风险评审,合规边界,敏感数据,操作审计,金融业务",
                "银行,信用卡,风险合规,审计",
                new BigDecimal("0.8700"),
                "命中银行信用卡业务的风险边界、合规确认和审计要求。",
                3
            ),
            new AgentProfile(
                "agent-pmo-default",
                "交付管理分身",
                "PMO",
                "负责排期、阶段汇总、交付物整理和任务复盘。",
                "排期,交付管理,复盘,项目推进",
                "项目管理,协同,复盘",
                new BigDecimal("0.8600"),
                "命中交付汇总、跨角色协同和复盘能力。",
                4
            )
        );
    }
}
