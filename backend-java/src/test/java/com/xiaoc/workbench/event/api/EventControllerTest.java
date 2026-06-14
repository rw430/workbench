package com.xiaoc.workbench.event.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoc.workbench.common.security.SecurityConfig;
import com.xiaoc.workbench.common.web.ApiExceptionHandler;
import com.xiaoc.workbench.event.service.RuntimeEventStreamService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(EventController.class)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class EventControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuntimeEventStreamService streamService;

    @Test
    void opensEventStreamWithoutAuthenticationAndUsesLastEventId() throws Exception {
        SseEmitter emitter = new SseEmitter();
        emitter.send(SseEmitter.event()
                .id("event-2")
                .name("task.completed")
                .data(Map.of("id", "event-2")));
        emitter.complete();
        when(streamService.open("run-1", "event-1")).thenReturn(emitter);

        MvcResult result = mockMvc.perform(get("/api/events/stream")
                        .param("run_id", "run-1")
                        .header("Last-Event-ID", "event-1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")))
                .andExpect(content().string(containsString("event-2")));
    }
}
