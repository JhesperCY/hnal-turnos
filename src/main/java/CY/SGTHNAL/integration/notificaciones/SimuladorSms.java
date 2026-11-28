package CY.SGTHNAL.integration.notificaciones;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import CY.SGTHNAL.model.enums.EstadoNotificacion;

@Component
@ConditionalOnProperty(name = "app.integraciones.sms.proveedor", havingValue = "simulado")
public class SimuladorSms implements EnvioSms {
    @Override
    public EstadoNotificacion enviar(String numero, String texto) {
        return EstadoNotificacion.SIMULADO;
    }
}
