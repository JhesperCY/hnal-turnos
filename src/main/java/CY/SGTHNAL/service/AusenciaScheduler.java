package CY.SGTHNAL.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cada 15 segundos revisa si algun turno llamado vencio su tiempo de gracia (CU07). */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class AusenciaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AusenciaScheduler.class);

    private final TurnoService turnoService;

    public AusenciaScheduler(TurnoService turnoService) {
        this.turnoService = turnoService;
    }

    @Scheduled(fixedDelay = 15000)
    public void revisar() {
        try {
            int marcados = turnoService.procesarAusencias(LocalDateTime.now());
            if (marcados > 0) {
                log.info("Turnos marcados como ausentes: {}", marcados);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar las ausencias: {}", e.getMessage());
        }
    }
}
