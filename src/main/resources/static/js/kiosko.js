// Logica de la pantalla de autoservicio (asistente por pasos). Habla con /kiosko/api/**.
const Kiosko = (() => {
  const $ = (id) => document.getElementById(id);

  const REGLAS = {
    DNI: { min: 8, max: 8, mensaje: 'El DNI debe tener 8 dígitos.' },
    CE: { min: 9, max: 12, mensaje: 'El Carnet de Extranjería debe tener entre 9 y 12 dígitos.' },
    PASAPORTE: { min: 6, max: 12, mensaje: 'El Pasaporte debe tener entre 6 y 12 dígitos.' },
  };
  const NOMBRES = { DNI: 'DNI', CE: 'Carnet de Extranjería', PASAPORTE: 'Pasaporte' };

  const estado = { tipoDocumento: null, documento: '', telefono: '', telefonoOmitido: false };
  let ticketActual = null;
  let ticketReimpreso = null;
  let campoActivo = 'documento';
  let docReimpresion = '';
  let enviando = false;
  let temporizadorRetorno = null;
  let temporizadorInactividad = null;
  let temporizadorAviso = null;

  const mostrar = (idPaso) => {
    document.querySelectorAll('.paso').forEach((el) => el.classList.add('d-none'));
    $(idPaso).classList.remove('d-none');
  };

  const enviarJson = (url, cuerpo) =>
    fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo) })
      .then((r) => r.json().catch(() => ({ ok: false, estado: 'ERROR', mensaje: 'No se pudo completar la operación. Intente nuevamente.' })));

  const construirTeclado = (contenedorId, onDigito, onBorrar, onLimpiar) => {
    const cont = $(contenedorId);
    cont.innerHTML = '';
    for (let n = 1; n <= 9; n++) {
      const b = document.createElement('button');
      b.textContent = n;
      b.type = 'button';
      b.onclick = () => onDigito(n);
      cont.appendChild(b);
    }
    const bc = document.createElement('button');
    bc.textContent = 'C'; bc.type = 'button'; bc.className = 'tecla-c'; bc.onclick = onLimpiar;
    cont.appendChild(bc);
    const b0 = document.createElement('button');
    b0.textContent = '0'; b0.type = 'button'; b0.onclick = () => onDigito(0);
    cont.appendChild(b0);
    const bb = document.createElement('button');
    bb.type = 'button'; bb.innerHTML = '<i class="bi bi-backspace"></i>'; bb.onclick = onBorrar;
    cont.appendChild(bb);
  };

  const reglaDocumento = () => REGLAS[estado.tipoDocumento] || { min: 6, max: 12, mensaje: 'Documento no válido.' };

  const validarDocumentoEnVivo = () => {
    const regla = reglaDocumento();
    $('error-documento').textContent =
      estado.documento.length > 0 && estado.documento.length < regla.min ? regla.mensaje : '';
  };

  const refrescarCampos = () => {
    $('input-documento').value = estado.documento;
    $('input-telefono').value = estado.telefono;
    $('estado-telefono').textContent = estado.telefonoOmitido
      ? 'Sin teléfono registrado (omitido por el usuario).' : '';
    validarDocumentoEnVivo();
  };

  const resetReimpresion = () => {
    docReimpresion = '';
    ticketReimpreso = null;
    $('input-doc-reimpresion').value = '';
    $('resultado-reimpresion').innerHTML = '';
  };

  const volverAlInicio = () => {
    clearInterval(temporizadorRetorno);
    estado.tipoDocumento = null; estado.documento = ''; estado.telefono = ''; estado.telefonoOmitido = false;
    resetReimpresion();
    mostrar('paso-inicio');
  };

  // Si nadie toca la pantalla durante 60 s se borran los datos y se vuelve al inicio.
  const reiniciarInactividad = () => {
    clearTimeout(temporizadorInactividad);
    temporizadorInactividad = setTimeout(volverAlInicio, 60000);
  };

  const verificarJornada = () => {
    fetch('/kiosko/api/estado', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error('estado'))))
      .then((e) => $('overlay-cerrado').classList.toggle('d-none', e.disponible))
      .catch(() => { /* sin conexion: se mantiene lo que ya se muestra */ });
  };

  const imprimirTicket = (t) => {
    $('imp-codigo').textContent = t.codigo;
    $('imp-tipo').textContent = t.tipo === 'PREFERENCIAL' ? 'Atención preferencial' : 'Atención general';
    $('imp-hora').textContent = `${t.fecha} ${t.hora}`;
    $('imp-posicion').textContent = `#${t.posicion}`;
    window.print();
  };

  const mostrarTicket = (t) => {
    $('ticket-header').className = `ticket-header ${t.tipo.toLowerCase()} hnal-flex-between px-3`;
    $('ticket-tipo').textContent = t.tipo === 'PREFERENCIAL' ? 'Preferencial' : 'General';
    $('ticket-hora').textContent = t.hora;
    $('ticket-codigo').textContent = t.codigo;
    $('ticket-posicion').textContent = `#${t.posicion}`;
    $('ticket-gracia').textContent = `${t.graciaMinutos} minutos`;
    $('ticket-nota').textContent = estado.telefono
      ? `Recibirá una notificación por llamada y SMS al número ${estado.telefono} cuando su turno esté próximo.`
      : 'No registró teléfono: siga la pantalla de TV para conocer cuándo será llamado.';
    mostrar('paso-ticket');

    let segundos = 15;
    const cuenta = $('cuenta-regresiva');
    cuenta.textContent = `(${segundos})`;
    clearInterval(temporizadorRetorno);
    temporizadorRetorno = setInterval(() => {
      segundos -= 1;
      cuenta.textContent = segundos > 0 ? `(${segundos})` : '';
      if (segundos <= 0) { volverAlInicio(); }
    }, 1000);
  };

  const mostrarResultadoReimpresion = (r) => {
    const caja = $('resultado-reimpresion');
    caja.innerHTML = '';
    ticketReimpreso = null;

    if (r.estado === 'EN_ESPERA' || r.estado === 'LLAMADO') {
      const tarjeta = document.createElement('div');
      tarjeta.className = 'ticket-box p-3 text-center';
      const codigo = document.createElement('div');
      codigo.className = 'fw-bold mb-1';
      codigo.textContent = r.ticket.codigo;
      tarjeta.appendChild(codigo);
      const detalle = document.createElement('div');
      detalle.className = 'small text-muted mb-2';
      tarjeta.appendChild(detalle);

      if (r.estado === 'LLAMADO') {
        detalle.textContent = r.mensaje;
      } else {
        detalle.textContent = `Posición en cola: #${r.ticket.posicion}`;
        ticketReimpreso = r.ticket;
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'btn btn-warning w-100';
        boton.innerHTML = '<i class="bi bi-printer"></i> Imprimir ticket';
        boton.onclick = () => { imprimirTicket(ticketReimpreso); volverAlInicio(); };
        tarjeta.appendChild(boton);
      }
      caja.appendChild(tarjeta);
      return;
    }

    const aviso = document.createElement('p');
    aviso.className = 'text-warning small mb-0';
    aviso.innerHTML = '<i class="bi bi-exclamation-circle"></i> ';
    aviso.appendChild(document.createTextNode(r.mensaje || 'No se encontró un turno activo con ese documento.'));
    caja.appendChild(aviso);
  };

  document.addEventListener('DOMContentLoaded', () => {
    verificarJornada();
    setInterval(verificarJornada, 10000);

    $('input-documento').addEventListener('click', () => { campoActivo = 'documento'; });
    $('input-telefono').addEventListener('click', () => { campoActivo = 'telefono'; });

    construirTeclado('keypad',
      (n) => {
        const regla = reglaDocumento();
        if (campoActivo === 'documento') {
          if (estado.documento.length >= regla.max) return;
          estado.documento += n;
        } else {
          estado.telefonoOmitido = false;
          estado.telefono = (estado.telefono + n).slice(0, 9);
        }
        refrescarCampos();
      },
      () => { estado[campoActivo] = estado[campoActivo].slice(0, -1); refrescarCampos(); },
      () => { estado[campoActivo] = ''; refrescarCampos(); }
    );

    construirTeclado('keypad-reimpresion',
      (n) => { docReimpresion = (docReimpresion + n).slice(0, 12); $('input-doc-reimpresion').value = docReimpresion; },
      () => { docReimpresion = docReimpresion.slice(0, -1); $('input-doc-reimpresion').value = docReimpresion; },
      () => { docReimpresion = ''; $('input-doc-reimpresion').value = ''; }
    );

    ['click', 'touchstart', 'keydown'].forEach((evento) => document.addEventListener(evento, reiniciarInactividad));
    reiniciarInactividad();
  });

  return {
    ir(paso) {
      clearInterval(temporizadorRetorno);
      if (paso === 'paso-reimpresion') resetReimpresion();
      mostrar(paso);
    },

    elegirDocumento(tipo) {
      estado.tipoDocumento = tipo;
      estado.documento = ''; estado.telefono = ''; estado.telefonoOmitido = false;
      campoActivo = 'documento';
      $('doc-tipo-actual').textContent = NOMBRES[tipo];
      refrescarCampos();
      mostrar('paso-datos');
    },

    omitirTelefono() {
      if (!confirm('¿Desea registrar su turno sin número de teléfono? No recibirá notificaciones por SMS/llamada.')) return;
      estado.telefono = '';
      estado.telefonoOmitido = true;
      refrescarCampos();
    },

    avanzarADatos() {
      const regla = reglaDocumento();
      if (estado.documento.length < regla.min || estado.documento.length > regla.max) {
        alert(regla.mensaje);
        return;
      }
      if (!estado.telefono && !estado.telefonoOmitido) {
        alert('Ingrese un teléfono de contacto o presione "Omitir teléfono" si no desea registrarlo.');
        return;
      }
      mostrar('paso-atencion');
    },

    registrar(tipoAtencion) {
      if (enviando) return;
      enviando = true;
      enviarJson('/kiosko/api/turno', {
        tipoDocumento: estado.tipoDocumento,
        numeroDocumento: estado.documento,
        telefono: estado.telefono,
        tipoAtencion,
      })
        .then((resp) => {
          if (!resp.ok) {
            alert(resp.mensaje || 'No se pudo registrar el turno.');
            mostrar('paso-inicio');
            return;
          }
          ticketActual = resp.ticket;
          mostrarTicket(resp.ticket);
        })
        .catch(() => {
          alert('No hay conexión con el sistema. Avise al personal.');
          mostrar('paso-inicio');
        })
        .finally(() => { enviando = false; });
    },

    imprimir() {
      if (ticketActual) imprimirTicket(ticketActual);
    },

    buscarReimpresion() {
      if (docReimpresion.length < 6) {
        mostrarResultadoReimpresion({ estado: 'ERROR', mensaje: 'Ingrese su número de documento.' });
        return;
      }
      enviarJson('/kiosko/api/reimpresion', { numeroDocumento: docReimpresion })
        .then(mostrarResultadoReimpresion)
        .catch(() => mostrarResultadoReimpresion({ estado: 'ERROR', mensaje: 'No hay conexión con el sistema. Avise al personal.' }));
    },

    // Por ahora solo el español esta disponible; los demas idiomas llegaran con el servicio de traduccion (RF021).
    idioma(nombre) {
      const aviso = $('aviso-idioma');
      clearTimeout(temporizadorAviso);
      if (nombre === 'Español') {
        aviso.classList.add('d-none');
        return;
      }
      aviso.textContent = `La traducción a ${nombre} estará disponible próximamente. Se mantiene el idioma Español.`;
      aviso.classList.remove('d-none');
      temporizadorAviso = setTimeout(() => aviso.classList.add('d-none'), 5000);
    },
  };
})();
