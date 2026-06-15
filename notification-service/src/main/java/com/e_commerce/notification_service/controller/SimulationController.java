package com.e_commerce.notification_service.controller;

import com.e_commerce.notification_service.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/simulate")
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/notification/fail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void triggerNotificationFailure() {
        simulationService.setFailMode(true);
    }

    @PostMapping("/notification/recover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recoverNotification() {
        simulationService.setFailMode(false);
    }
}
