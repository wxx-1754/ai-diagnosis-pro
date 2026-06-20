package com.wuxx.diagnosis.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.wuxx.diagnosis.service.DiagnoseEventService;
import org.junit.jupiter.api.Test;

class DiagnoseSseManagerTest {

    @Test
    void replaysOnlyEventsAfterRequestedCursor() {
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        when(eventService.findAfter("DIAG-1", 7L)).thenReturn(List.of());
        DiagnoseSseManager manager = new DiagnoseSseManager(eventService);

        manager.subscribe("DIAG-1", 7L);

        verify(eventService).findAfter("DIAG-1", 7L);
        manager.complete("DIAG-1");
    }

    @Test
    void persistsEventBeforeBroadcast() {
        DiagnoseEventService eventService = mock(DiagnoseEventService.class);
        DiagnoseSseManager manager = new DiagnoseSseManager(eventService);
        DiagnoseEvent event = DiagnoseEvent.builder()
                .taskNo("DIAG-2")
                .eventType(DiagnoseEventType.TASK_CREATED.name())
                .build();

        manager.send("DIAG-2", event);

        verify(eventService).save(event);
    }
}
