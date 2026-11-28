package CY.SGTHNAL.integration.notificaciones;

import CY.SGTHNAL.model.enums.EstadoNotificacion;

public interface EnvioSms {
    EstadoNotificacion enviar(String numero, String texto);
}
