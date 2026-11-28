package CY.SGTHNAL.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;

import CY.SGTHNAL.integration.notificaciones.EnvioSms;
import CY.SGTHNAL.integration.notificaciones.SimuladorSms;
import CY.SGTHNAL.model.Notificacion;
import CY.SGTHNAL.model.Jornada;
import CY.SGTHNAL.model.Paciente;
import CY.SGTHNAL.model.enums.EstadoJornada;
import CY.SGTHNAL.model.enums.EstadoTurno;
import CY.SGTHNAL.model.Turno;
import CY.SGTHNAL.model.enums.EstadoNotificacion;
import CY.SGTHNAL.model.enums.TipoIntento;
import CY.SGTHNAL.repository.NotificacionRepository;
import CY.SGTHNAL.repository.TurnoRepository;

class AvisoLlamadoServiceTest {
    private final NotificacionRepository intentos = mock(NotificacionRepository.class);
    private final TurnoRepository turnos = mock(TurnoRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final StaticListableBeanFactory simuladores = new StaticListableBeanFactory();
    private final Turno adelante = new Turno();
    private final Turno objetivo = new Turno();

    AvisoLlamadoServiceTest() {
        simuladores.addBean("sms", new SimuladorSms());
        objetivo.setTelefonoVisita("911111111");
        objetivo.setCodigoTurno("G-004");
        when(intentos.save(any(Notificacion.class))).thenAnswer(invocacion -> {
            Notificacion intento = invocacion.getArgument(0);
            intento.setIdNotificacion(1);
            return intento;
        });
    }

    @Test
    void enviaSmsSoloCuandoNoHayConfirmacionTrasLaSegundaLlamada() {
        var avisos = servicio(AvisoLlamadoService.RespuestaSimulada.NINGUNA);
        avisos.revisarCola(List.of(adelante, adelante, adelante, objetivo));
        verifyNoInteractions(eventos);
        var primera = ArgumentCaptor.forClass(Notificacion.class);
        verify(intentos).save(primera.capture());
        assertEquals(TipoIntento.LLAMADA_1, primera.getValue().getTipoIntento());
        assertEquals(EstadoNotificacion.SIN_RESPUESTA, primera.getValue().getEstado());

        when(intentos.existsByTurnoAndTipoIntento(objetivo, TipoIntento.LLAMADA_1)).thenReturn(true);
        avisos.revisarCola(List.of(adelante, adelante, objetivo));
        verify(eventos).publishEvent(new AvisoLlamadoService.SmsPendiente(1,
                "HNAL: su turno G-004 está próximo. Acérquese al área de atención.", "+51911111111"));
    }

    @Test
    void laConfirmacionEnLaSegundaLlamadaCancelaElSms() {
        var avisos = servicio(AvisoLlamadoService.RespuestaSimulada.SEGUNDA);
        when(intentos.existsByTurnoAndTipoIntento(objetivo, TipoIntento.LLAMADA_1)).thenReturn(true);
        avisos.revisarCola(List.of(adelante, adelante, objetivo));

        var segunda = ArgumentCaptor.forClass(Notificacion.class);
        verify(intentos).save(segunda.capture());
        assertEquals(TipoIntento.LLAMADA_2, segunda.getValue().getTipoIntento());
        assertEquals(EstadoNotificacion.CONFIRMADO, segunda.getValue().getEstado());
        verifyNoInteractions(eventos);
    }

    @Test
    void elTicketExigeElTelefonoRegistradoYSeProgramaSoloUnaVez() {
        var jornada = new Jornada();
        jornada.setEstado(EstadoJornada.ACTIVA);
        objetivo.setJornada(jornada);
        objetivo.setEstado(EstadoTurno.EN_ESPERA);
        var paciente = new Paciente();
        paciente.setNumeroDocumento("01234567");
        objetivo.setPaciente(paciente);
        when(turnos.findByIdForUpdate(12)).thenReturn(Optional.of(objetivo));
        var avisos = servicio(AvisoLlamadoService.RespuestaSimulada.NINGUNA);

        assertEquals("smsTelefonoNoCoincide", avisos.solicitarTicket(12, "01234567", "922222222"));
        verifyNoInteractions(eventos);
        assertEquals("smsSolicitado", avisos.solicitarTicket(12, "01234567", "51911111111"));
        verify(eventos).publishEvent(new AvisoLlamadoService.SmsPendiente(1,
                "HNAL: su ticket de atención es G-004. Consulte la pantalla de turnos para conocer el llamado.",
                "+51911111111"));
        when(intentos.existsByTurnoAndTipoIntento(objetivo, TipoIntento.SMS_TICKET)).thenReturn(true);
        assertEquals("smsYaSolicitado", avisos.solicitarTicket(12, "01234567", "911111111"));
    }

    private AvisoLlamadoService servicio(AvisoLlamadoService.RespuestaSimulada confirmacion) {
        return new AvisoLlamadoService(simuladores.getBeanProvider(EnvioSms.class), Runnable::run,
                intentos, turnos, eventos, "simulado", false, confirmacion);
    }
}
