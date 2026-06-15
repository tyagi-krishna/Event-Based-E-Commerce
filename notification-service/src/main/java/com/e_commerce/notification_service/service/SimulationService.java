package com.e_commerce.notification_service.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SimulationService {

    private final AtomicBoolean notificationShouldFail = new AtomicBoolean(false);

    public void setFailMode(boolean fail) {
        notificationShouldFail.set(fail);
    }

    public boolean shouldFail() {
        return notificationShouldFail.get();
    }
}
