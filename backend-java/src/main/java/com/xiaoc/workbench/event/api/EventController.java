package com.xiaoc.workbench.event.api;

import com.xiaoc.workbench.event.service.RuntimeEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final RuntimeEventStreamService streamService;

    public EventController(RuntimeEventStreamService streamService) {
        this.streamService = streamService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam("run_id") String runId,
            @RequestParam(value = "after_id", required = false) String afterId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId
    ) {
        return streamService.open(runId, firstNonBlank(afterId, lastEventId));
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
