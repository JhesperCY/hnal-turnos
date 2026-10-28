// Mantiene viva una zona de la pagina: escucha los avisos del servidor (SSE) y vuelve a pedir el fragmento HTML.
// Si el aviso no llega (proxy que retiene la conexion, red caida) un refresco de seguridad cada 20 s evita que
// la pantalla quede vieja. Tras 5 min sin actividad del usuario deja de consultar, para que la sesion pueda caducar.
const TiempoReal = (() => {
  const REPOSO_MS = 5 * 60 * 1000;
  const REFRESCO_SEGURO_MS = 20000;
  const PAUSA_MIN_MS = 500;
  const JITTER_MAX_MS = 400;

  const normalizar = (html) => html.replace(/name="_csrf"[^>]*?value="[^"]*"/g, 'name="_csrf" value=""');

  const iniciar = (zonaId, urlFragmento) => {
    const zona = document.getElementById(zonaId);
    if (!zona) return;

    let fuente = null;
    let ultimaActividad = Date.now();
    let enReposo = false;
    let ocupado = false;
    let pendiente = false;
    let ultimoRefresco = 0;
    let temporizador = null;
    let reintentos = 0;
    let huella = normalizar(zona.innerHTML);

    const aviso = document.createElement('div');
    aviso.className = 'alert alert-secondary text-center py-2 small d-none';
    aviso.textContent = 'Pantalla en reposo por inactividad. Mueva el mouse o toque la pantalla para actualizar.';
    zona.parentNode.insertBefore(aviso, zona);

    const programar = () => {
      if (temporizador !== null) return;
      const espera = Math.max(0, PAUSA_MIN_MS - (Date.now() - ultimoRefresco)) + Math.random() * JITTER_MAX_MS;
      temporizador = setTimeout(() => { temporizador = null; refrescar(); }, espera);
    };

    const refrescar = async () => {
      if (enReposo) return;
      if (document.hidden || ocupado) { pendiente = true; return; }
      ocupado = true;
      pendiente = false;
      try {
        const respuesta = await fetch(urlFragmento, { cache: 'no-store', credentials: 'same-origin', redirect: 'manual' });
        if (respuesta.type === 'opaqueredirect' || [401, 403, 409].includes(respuesta.status)) {
          window.location.reload(); // sesion vencida o el panel cambio de modo: la pagina completa sabe adonde ir
          return;
        }
        if (respuesta.ok) {
          const html = await respuesta.text();
          const nueva = normalizar(html);
          if (nueva !== huella) { zona.innerHTML = html; huella = nueva; }
        }
      } catch (e) {
        // sin red: se reintenta con el siguiente aviso o con el refresco de seguridad
      } finally {
        ocupado = false;
        ultimoRefresco = Date.now();
        if (pendiente) programar();
      }
    };

    const cerrarFuente = () => { if (fuente) { fuente.close(); fuente = null; } };

    const conectar = () => {
      if (!('EventSource' in window) || enReposo) return;
      cerrarFuente();
      fuente = new EventSource('/eventos');
      fuente.addEventListener('cambio', programar);
      fuente.onopen = () => { reintentos = 0; programar(); };
      fuente.onerror = () => {
        if (fuente && fuente.readyState === EventSource.CLOSED) {
          cerrarFuente();
          reintentos += 1;
          setTimeout(conectar, Math.min(30000, 1000 * Math.pow(2, reintentos)));
        }
      };
    };

    const entrarEnReposo = () => { enReposo = true; cerrarFuente(); aviso.classList.remove('d-none'); };
    const reanudar = () => { enReposo = false; aviso.classList.add('d-none'); conectar(); programar(); };
    const actividad = () => { ultimaActividad = Date.now(); if (enReposo) reanudar(); };

    ['mousemove', 'keydown', 'click', 'touchstart', 'scroll'].forEach((evento) =>
      document.addEventListener(evento, actividad, { passive: true }));
    document.addEventListener('visibilitychange', () => { if (!document.hidden) programar(); });
    window.addEventListener('beforeunload', cerrarFuente);

    setInterval(() => { if (!enReposo && Date.now() - ultimaActividad > REPOSO_MS) entrarEnReposo(); }, 15000);
    setInterval(() => { if (!enReposo) refrescar(); }, REFRESCO_SEGURO_MS);
    conectar();
  };

  return { iniciar };
})();
