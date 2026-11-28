// Logica de la pantalla de autoservicio (asistente por pasos). Habla con /kiosko/api/**.
const Kiosko = (() => {
  const $ = (id) => document.getElementById(id);

  const REGLAS = {
    DNI: { min: 8, max: 8, mensaje: 'dniInvalido' },
    CE: { min: 9, max: 12, mensaje: 'ceInvalido' },
    PASAPORTE: { min: 6, max: 12, mensaje: 'pasaporteInvalido' },
  };
  const IDIOMAS = {
    es: 'Español', qu: 'Runasimi', ay: 'Aymara', cni: 'Asháninka',
    en: 'English', 'pt-BR': 'Português (Brasil)',
  };
  const rutaIdiomas = document.currentScript.dataset.i18nUrl;
  const cargas = new Map();
  let base = {};
  let traducciones = {};
  let idiomaActual = 'es';
  let solicitudIdioma = 0;
  let resultadoReimpresion = null;

  const cargarIdioma = (codigo) => {
    if (!cargas.has(codigo)) {
      const carga = fetch(rutaIdiomas + codigo + '.json')
        .then((respuesta) => {
          if (!respuesta.ok) throw new Error('i18n');
          return respuesta.json();
        })
        .catch((error) => {
          cargas.delete(codigo);
          throw error;
        });
      cargas.set(codigo, carga);
    }
    return cargas.get(codigo);
  };

  const t = (clave, valores = {}) => (traducciones[clave] ?? base[clave] ?? '')
    .replace(/\{(\w+)\}/g, (marca, nombre) => valores[nombre] ?? marca);

  const aplicarIdioma = () => {
    document.documentElement.lang = idiomaActual;
    document.title = t('titulo');
    $('idioma-actual').textContent = IDIOMAS[idiomaActual];
    $('idioma-actual').lang = idiomaActual;
    for (const el of document.querySelectorAll('[data-i18n]')) {
      const clave = el.dataset.i18n;
      el.textContent = t(clave);
      el.lang = traducciones[clave] ? idiomaActual : 'es';
    }
    for (const el of document.querySelectorAll('[data-i18n-placeholder]')) {
      el.placeholder = t(el.dataset.i18nPlaceholder);
    }
    for (const el of document.querySelectorAll('[data-i18n-aria]')) {
      el.setAttribute('aria-label', t(el.dataset.i18nAria));
    }
    if (estado.tipoDocumento) $('doc-tipo-actual').textContent = t(estado.tipoDocumento);
    refrescarCampos();
    if (ticketActual) rellenarTicket(ticketActual);
    if (resultadoReimpresion) mostrarResultadoReimpresion(resultadoReimpresion);
    const aviso = $('aviso-idioma');
    const parcial = Object.keys(base).some((clave) => !(clave in traducciones));
    aviso.textContent = parcial ? t('sinTraduccion') : '';
    aviso.classList.toggle('d-none', !parcial);
  };

  const estado = { tipoDocumento: null, documento: '', telefono: '', telefonoOmitido: false };
  let ticketActual = null;
  let ticketReimpreso = null;
  let campoActivo = 'documento';
  let docReimpresion = '';
  let enviando = false;
  let temporizadorRetorno = null;
  let temporizadorInactividad = null;

  const mostrar = (idPaso) => {
    document.querySelectorAll('.paso').forEach((el) => el.classList.add('d-none'));
    $(idPaso).classList.remove('d-none');
  };

  const enviarJson = (url, cuerpo) =>
    fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(cuerpo) })
      .then((r) => r.json().catch(() => ({ ok: false, estado: 'ERROR', codigoMensaje: 'conexion' })));

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
    bc.dataset.i18nAria = 'limpiar';
    cont.appendChild(bc);
    const b0 = document.createElement('button');
    b0.textContent = '0'; b0.type = 'button'; b0.onclick = () => onDigito(0);
    cont.appendChild(b0);
    const bb = document.createElement('button');
    bb.type = 'button'; bb.innerHTML = '<i class="bi bi-backspace"></i>'; bb.onclick = onBorrar;
    bb.dataset.i18nAria = 'borrar';
    cont.appendChild(bb);
  };

  const reglaDocumento = () => REGLAS[estado.tipoDocumento] || { min: 6, max: 12, mensaje: 'documentoInvalido' };

  const validarDocumentoEnVivo = () => {
    const regla = reglaDocumento();
    $('error-documento').textContent =
      estado.documento.length > 0 && estado.documento.length < regla.min ? t(regla.mensaje) : '';
  };

  const refrescarCampos = () => {
    $('input-documento').value = estado.documento;
    $('input-telefono').value = estado.telefono;
    $('estado-telefono').textContent = estado.telefonoOmitido
      ? t('sinTelefono') : '';
    validarDocumentoEnVivo();
  };

  const resetReimpresion = () => {
    docReimpresion = '';
    ticketReimpreso = null;
    resultadoReimpresion = null;
    $('input-doc-reimpresion').value = '';
    $('resultado-reimpresion').innerHTML = '';
  };

  const volverAlInicio = () => {
    clearInterval(temporizadorRetorno);
    estado.tipoDocumento = null; estado.documento = ''; estado.telefono = ''; estado.telefonoOmitido = false;
    ticketActual = null;
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

  const enviarTicketSms = async (boton, ticket, documento, telefono) => {
    boton.disabled = true;
    try {
      const respuesta = await enviarJson('/kiosko/api/ticket/sms', {
        idTurno: ticket.idTurno, numeroDocumento: documento, telefono,
      });
      const codigo = respuesta.codigoMensaje || 'conexion';
      alert(t(codigo));
      boton.disabled = codigo === 'smsSolicitado' || codigo === 'smsYaSolicitado';
    } catch {
      alert(t('conexion'));
      boton.disabled = false;
    }
  };

  const imprimirTicket = (ticket) => {
    $('imp-codigo').textContent = ticket.codigo;
    $('imp-tipo').textContent = t(ticket.tipo === 'PREFERENCIAL' ? 'atencionPreferencial' : 'atencionGeneral');
    $('imp-hora').textContent = `${ticket.fecha} ${ticket.hora}`;
    $('imp-posicion').textContent = `#${ticket.posicion}`;
    window.print();
  };

  const rellenarTicket = (ticket) => {
    $('ticket-header').className = `ticket-header ${ticket.tipo.toLowerCase()} hnal-flex-between px-3`;
    $('ticket-tipo').textContent = t(ticket.tipo === 'PREFERENCIAL' ? 'preferencial' : 'general');
    $('ticket-hora').textContent = ticket.hora;
    $('ticket-codigo').textContent = ticket.codigo;
    $('ticket-posicion').textContent = `#${ticket.posicion}`;
    $('ticket-gracia').textContent = t('minutos', { cantidad: ticket.graciaMinutos });
    $('ticket-nota').textContent = estado.telefono ? t('notaSms', estado) : t('seguirTv');
    $('ticket-enviar-sms').classList.toggle('d-none', !estado.telefono);
    const aviso = $('ticket-contingencia');
    aviso.classList.toggle('d-none', !ticket.contingencia);
    aviso.textContent = ticket.contingencia ? t('identidadContingencia') : '';
  };

  const mostrarTicket = (ticket) => {
    rellenarTicket(ticket);
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
    resultadoReimpresion = r;
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
        detalle.textContent = t('llamado', { modulo: r.modulo });
      } else {
        detalle.textContent = t('posicionNumero', r.ticket);
        ticketReimpreso = r.ticket;
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'btn btn-warning w-100';
        boton.innerHTML = '<i class="bi bi-printer"></i> ';
        boton.append(t('imprimir'));
        boton.onclick = () => { imprimirTicket(ticketReimpreso); volverAlInicio(); };
        tarjeta.appendChild(boton);
      }
      const form = document.createElement('form');
      form.className = 'mt-2';
      const telefono = document.createElement('input');
      telefono.className = 'form-control mb-2';
      telefono.type = 'tel';
      telefono.inputMode = 'numeric';
      telefono.maxLength = 15;
      telefono.autocomplete = 'off';
      telefono.required = true;
      telefono.placeholder = t('telefono');
      telefono.setAttribute('aria-label', t('telefono'));
      const sms = document.createElement('button');
      sms.type = 'submit';
      sms.className = 'btn btn-outline-warning w-100';
      sms.textContent = t('ticketSms');
      form.append(telefono, sms);
      form.addEventListener('submit', (evento) => {
        evento.preventDefault();
        enviarTicketSms(sms, r.ticket, r.documento, telefono.value.trim());
      });
      tarjeta.appendChild(form);
      caja.appendChild(tarjeta);
      return;
    }

    const aviso = document.createElement('p');
    aviso.className = 'text-warning small mb-0';
    aviso.innerHTML = '<i class="bi bi-exclamation-circle"></i> ';
    aviso.append(t(r.codigoMensaje || 'noEncontrado'));
    caja.appendChild(aviso);
  };

  document.addEventListener('DOMContentLoaded', () => {
    verificarJornada();
    setInterval(verificarJornada, 10000);

    $('input-documento').addEventListener('click', () => { campoActivo = 'documento'; });
    $('input-telefono').addEventListener('click', () => { campoActivo = 'telefono'; });
    $('input-documento').addEventListener('input', ({ target }) => {
      estado.documento = target.value.replace(/[^a-z0-9]/gi, '').slice(0, 12);
      refrescarCampos();
    });
    $('input-doc-reimpresion').addEventListener('input', ({ target }) => {
      docReimpresion = target.value.replace(/[^a-z0-9]/gi, '').slice(0, 12);
      target.value = docReimpresion;
    });

    construirTeclado('keypad',
      (n) => {
        const regla = reglaDocumento();
        if (campoActivo === 'documento') {
          if (estado.documento.length >= regla.max) return;
          estado.documento += n;
        } else {
          estado.telefonoOmitido = false;
          estado.telefono = (estado.telefono + n).slice(0, 15);
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
    cargarIdioma('es').then((catalogo) => {
      base = catalogo;
      if (solicitudIdioma === 0) {
        traducciones = base;
        aplicarIdioma();
      }
      for (const codigo of Object.keys(IDIOMAS)) cargarIdioma(codigo).catch(() => {});
    }).catch(() => {});
  });

  return {
    ir(paso) {
      clearInterval(temporizadorRetorno);
      if (paso === 'paso-inicio') {
        volverAlInicio();
        return;
      }
      if (paso === 'paso-reimpresion') resetReimpresion();
      mostrar(paso);
    },

    elegirDocumento(tipo) {
      estado.tipoDocumento = tipo;
      estado.documento = ''; estado.telefono = ''; estado.telefonoOmitido = false;
      campoActivo = 'documento';
      $('input-documento').readOnly = tipo !== 'PASAPORTE';
      $('doc-tipo-actual').textContent = t(tipo);
      refrescarCampos();
      mostrar('paso-datos');
    },

    omitirTelefono() {
      if (!confirm(t('confirmarOmitir'))) return;
      estado.telefono = '';
      estado.telefonoOmitido = true;
      refrescarCampos();
    },

    avanzarADatos() {
      const regla = reglaDocumento();
      if (estado.documento.length < regla.min || estado.documento.length > regla.max) {
        alert(t(regla.mensaje));
        return;
      }
      if (!estado.telefono && !estado.telefonoOmitido) {
        alert(t('pedirTelefono'));
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
            alert(t(resp.codigoMensaje || 'registroError') || resp.mensaje);
            mostrar('paso-inicio');
            return;
          }
          $('ticket-enviar-sms').disabled = false;
          ticketActual = { ...resp.ticket, contingencia: resp.contingencia };
          mostrarTicket(ticketActual);
        })
        .catch(() => {
          alert(t('conexion'));
          mostrar('paso-inicio');
        })
        .finally(() => { enviando = false; });
    },

    enviarTicketActual(boton) {
      if (ticketActual && estado.telefono) {
        enviarTicketSms(boton, ticketActual, estado.documento, estado.telefono);
      }
    },

    imprimir() {
      if (ticketActual) imprimirTicket(ticketActual);
    },

    buscarReimpresion() {
      if (docReimpresion.length < 6) {
        mostrarResultadoReimpresion({ estado: 'ERROR', codigoMensaje: 'documentoInvalido' });
        return;
      }
      const documento = docReimpresion;
      enviarJson('/kiosko/api/reimpresion', { numeroDocumento: documento })
        .then((respuesta) => mostrarResultadoReimpresion({ ...respuesta, documento }))
        .catch(() => mostrarResultadoReimpresion({ estado: 'ERROR', codigoMensaje: 'conexion' }));
    },

    async idioma(codigo) {
      if (!Object.hasOwn(IDIOMAS, codigo)) return;
      const solicitud = ++solicitudIdioma;
      try {
        const [catalogoBase, catalogo] = await Promise.all([cargarIdioma('es'), cargarIdioma(codigo)]);
        if (solicitud !== solicitudIdioma) return;
        base = catalogoBase;
        traducciones = catalogo;
        idiomaActual = codigo;
        aplicarIdioma();
      } catch {
        if (solicitud !== solicitudIdioma) return;
        const aviso = $('aviso-idioma');
        aviso.textContent = t('idiomaError') || 'No se pudo cargar el idioma. Intente nuevamente.';
        aviso.classList.remove('d-none');
      }
    },
  };
})();
