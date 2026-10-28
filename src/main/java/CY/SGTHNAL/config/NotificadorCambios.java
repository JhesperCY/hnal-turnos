package CY.SGTHNAL.config;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import CY.SGTHNAL.service.EventosService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Listener de las entidades que cambian lo que ven las pantallas (turno, sesion, modulo, incidencia, jornada).
 * Avisa solo cuando la transaccion se confirma (commit): si se revierte, nadie se entera, y las pantallas que
 * pidan datos despues del aviso ya ven el cambio guardado.
 */
public class NotificadorCambios {

    private static final Object CLAVE = new Object();

    @PostPersist
    @PostUpdate
    @PostRemove
    public void alCambiar(Object entidad) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            EventosService.cambio();
            return;
        }
        if (TransactionSynchronizationManager.hasResource(CLAVE)) {
            return; // ya se registro el aviso de esta transaccion
        }
        TransactionSynchronizationManager.bindResource(CLAVE, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                TransactionSynchronizationManager.unbindResourceIfPossible(CLAVE);
                if (estado == STATUS_COMMITTED) {
                    EventosService.cambio();
                }
            }
        });
    }
}
