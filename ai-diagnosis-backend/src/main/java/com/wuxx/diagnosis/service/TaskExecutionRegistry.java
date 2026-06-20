package com.wuxx.diagnosis.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class TaskExecutionRegistry {

    private final Set<String> activeTaskNos = ConcurrentHashMap.newKeySet();

    public void register(String taskNo) {
        activeTaskNos.add(taskNo);
    }

    public void unregister(String taskNo) {
        activeTaskNos.remove(taskNo);
    }

    public boolean isActive(String taskNo) {
        return activeTaskNos.contains(taskNo);
    }
}
