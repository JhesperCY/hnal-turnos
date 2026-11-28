package CY.SGTHNAL.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import CY.SGTHNAL.integration.notificaciones.EnvioSms;
import CY.SGTHNAL.model.Notificacion;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.enums.TipoIntento;
import CY.SGTHNAL.repository.NotificacionRepository;
import CY.SGTHNAL.repository.TurnoRepository;

@Service
public class AvisoLlamadoService {
    private static final Logger LOG = LoggerFactory.getLogger(AvisoLlamadoService.class);

    record SmsPendiente(Integer idNotificacion, String mensaje, String destino) {
    }

    private final ObjectProvider<EnvioSms> proveedor;
    private final Executor ejecutor;
    private final NotificacionRepository notificaciones;
    private final TurnoRepository turnos;
    private final ApplicationEventPublisher eventos;
    private final boolean vozSimulada;

    enum RespuestaSimulada { NINGUNA, PRIMERA, SEGUNDA }

    private final RespuestaSimulada respuestaSimulada;

    public AvisoLlamadoService(ObjectProvider<EnvioSms> proveedor,
            @Qualifier("applicationTaskExecutor") Executor ejecutor,
            NotificacionRepository notificaciones, TurnoRepository turnos, ApplicationEventPublisher eventos,
            @Value("${app.integraciones.sms.proveedor:}") String modoSms,
            @Value("${app.integraciones.voz.simulada:false}") boolean vozSimulada,
            @Value("${app.integraciones.voz.confirmacion:NINGUNA}") RespuestaSimulada respuestaSimulada) {
        this.proveedor = proveedor;
        this.ejecutor = ejecutor;
        this.notificaciones = notificaciones;
        this.turnos = turnos;
        this.eventos = eventos;
        this.vozSimulada = vozSimulada || "simulado".equals(modoSms);
        this.respuestaSimulada = respuestaSimulada;
        if (modoSms.isBlank()) {
            LOG.info("Notificaciones deshabilitadas: falta APP_INTEGRACIONES_SMS_PROVEEDOR");
        } else if (!this.vozSimulada) {
            LOG.warn("CU06 deshabilitado: falta integrar voz o habilitar APP_INTEGRACIONES_VOZ_SIMULADA para una prueba");
        }
    }

    boolean habilitado() {
        return vozSimulada && proveedor.getIfAvailable() != null;
    }

    // La cola ya está ordenada y la jornada sigue bloqueada por quien actualizó el turno.
    void revisarCola(List<Turno> cola) {
        if (cola.size() >= 4) {
            simularLlamada(cola.get(3), TipoIntento.LLAMADA_1, RespuestaSimulada.PRIMERA);
        }
        if (cola.size() >= 3) {
            Turno turno = cola.get(2);
            boolean primeraSinConfirmar = notificaciones.existsByTurnoAndTipoIntento(turno, TipoIntento.LLAMADA_1)
                    && !notificaciones.existsByTurnoAndEstado(turno, EstadoNotificacion.CONFIRMADO);
            if (primeraSinConfirmar) {
                simularLlamada(turno, TipoIntento.LLAMADA_2, RespuestaSimulada.SEGUNDA);
            }
        }
    }

    private void simularLlamada(Turno turno, TipoIntento tipo, RespuestaSimulada paso) {
        String telefono = turno.getTelefonoVisita();
        if (telefono == null || telefono.isBlank() || notificaciones.existsByTurnoAndTipoIntento(turno, tipo)) {
            return;
        }
        String destino = normalizarNumero(telefono);
        EstadoNotificacion estado = destino == null ? EstadoNotificacion.NUMERO_NO_DISPONIBLE
                : respuestaSimulada == paso ? EstadoNotificacion.CONFIRMADO : EstadoNotificacion.SIN_RESPUESTA;
        registrarIntento(turno, tipo, estado);
        if (tipo == TipoIntento.LLAMADA_2 && estado != EstadoNotificacion.CONFIRMADO) {
            var estadoSms = destino == null ? EstadoNotificacion.NUMERO_NO_DISPONIBLE : EstadoNotificacion.PENDIENTE;
            Integer id = registrarIntento(turno, TipoIntento.SMS_FINAL, estadoSms);
            if (destino != null) {
                eventos.publishEvent(new SmsPendiente(id, "HNAL: su turno " + turno.getCodigoTurno()
                        + " está próximo. Acérquese al área de atención.", destino));
            }
        }
    }

    @Transactional
    public String solicitarTicket(Integer idTurno, String documento, String telefono) {
        if (proveedor.getIfAvailable() == null) {
            return "smsNoDisponible";
        }
        String destino = normalizarNumero(telefono);
        if (destino == null) {
            return "smsTelefonoInvalido";
        }
        Turno turno = turnos.findByIdForUpdate(idTurno).orElse(null);
        if (turno == null || turno.getJornada().getEstado() != EstadoJornada.ACTIVA
                || (turno.getEstado() != EstadoTurno.EN_ESPERA && turno.getEstado() != EstadoTurno.LLAMADO)
                || !turno.getPaciente().getNumeroDocumento().equalsIgnoreCase(documento)) {
            return "smsTurnoNoDisponible";
        }
        String telefonoVisita = turno.getTelefonoVisita();
        if (telefonoVisita != null && !telefonoVisita.isBlank()
                && !destino.equals(normalizarNumero(telefonoVisita))) {
            return "smsTelefonoNoCoincide";
        }
        if (notificaciones.existsByTurnoAndTipoIntento(turno, TipoIntento.SMS_TICKET)) {
            return "smsYaSolicitado";
        }
        Integer id = registrarIntento(turno, TipoIntento.SMS_TICKET, EstadoNotificacion.PENDIENTE);
        eventos.publishEvent(new SmsPendiente(id,
                "HNAL: su ticket de atención es " + turno.getCodigoTurno()
                        + ". Consulte la pantalla de turnos para conocer el llamado.", destino));
        return "smsSolicitado";
    }

    private Integer registrarIntento(Turno turno, TipoIntento tipo, EstadoNotificacion estado) {
        Notificacion intento = new Notificacion();
        intento.setTurno(turno);
        intento.setTipoIntento(tipo);
        intento.setEstado(estado);
        intento.setHoraEvento(LocalDateTime.now());
        return notificaciones.save(intento).getIdNotificacion();
    }

    @TransactionalEventListener
    public void alProgramarSms(SmsPendiente pendiente) {
        EnvioSms sms = proveedor.getObject();
        try {
            ejecutor.execute(() -> enviar(pendiente, sms));
        } catch (RejectedExecutionException e) {
            LOG.warn("No se pudo programar el intento de SMS {}", pendiente.idNotificacion());
        }
    }

    private void enviar(SmsPendiente pendiente, EnvioSms sms) {
        try {
            Integer id = pendiente.idNotificacion();
            if (notificaciones.cambiarEstado(id, EstadoNotificacion.PENDIENTE,
                    EstadoNotificacion.PROCESANDO) == 0) {
                return;
            }
            EstadoNotificacion estado;
            try {
                estado = sms.enviar(pendiente.destino(), pendiente.mensaje());
            } catch (RuntimeException e) {
                LOG.warn("Falló el intento de SMS {}", id);
                estado = EstadoNotificacion.FALLIDO;
            }

            notificaciones.cambiarEstado(id, EstadoNotificacion.PROCESANDO, estado);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo guardar el estado del intento de SMS {}", pendiente.idNotificacion());
        }
    }

    private static String normalizarNumero(String telefono) {
        if (telefono.matches("9[0-9]{8}")) {
            return "+51" + telefono;
        }
        return telefono.matches("[1-9][0-9]{9,14}") ? "+" + telefono : null;
    }
}
