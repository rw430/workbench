package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.RunAlreadyLockedException;
import com.xiaoc.workbench.orchestrator.service.RunnerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "rabbit")
public class RunWorker {
    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private final RunnerService runnerService;
    private final RunConcurrencyGuard runConcurrencyGuard;

    public RunWorker(RunnerService runnerService, RunConcurrencyGuard runConcurrencyGuard) {
        this.runnerService = runnerService;
        this.runConcurrencyGuard = runConcurrencyGuard;
    }

    @RabbitListener(queues = "${xiaoc.rabbitmq.run-start-queue}")
    public void handle(RunStartMessage message) {
        try {
            runConcurrencyGuard.runWithLock(message.runId(), () -> {
                runnerService.startRun(message.runId());
                return null;
            });
        } catch (RunAlreadyLockedException exception) {
            log.info("Skipping locked run {}", message.runId());
        }
    }
}