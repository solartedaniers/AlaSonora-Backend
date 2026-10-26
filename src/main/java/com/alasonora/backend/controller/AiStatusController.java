package com.alasonora.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alasonora.backend.ai.AiEngineReadiness;
import com.alasonora.backend.ai.AiEngineWakeUpService;
import com.alasonora.backend.dto.AiStatusDto;

/**
 * Estado del motor de IA: el frontend lo consulta al iniciar sesión y en Grabar. Respondo al instante
 * con el último estado conocido y, si no está listo, disparo el despertar en segundo plano.
 * Requiere sesión, así nadie anónimo despierta el motor; nunca expone su URL ni su clave.
 */
@RestController
@RequestMapping("/api/ai")
public class AiStatusController {

    private final AiEngineReadiness readiness;
    private final AiEngineWakeUpService wakeUpService;

    public AiStatusController(AiEngineReadiness readiness, AiEngineWakeUpService wakeUpService) {
        this.readiness = readiness;
        this.wakeUpService = wakeUpService;
    }

    @GetMapping("/status")
    public AiStatusDto status() {
        boolean ready = readiness.isReady();
        if (!ready) wakeUpService.wakeUp();
        return new AiStatusDto(ready);
    }
}
