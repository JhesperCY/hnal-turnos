// Pantalla publica de llamados. Consulta /tv/estado cada 2 s y anuncia cada llamado nuevo (o repetido).
(() => {
  const $ = (id) => document.getElementById(id);

  let audioActivo = false;
  let primeraCarga = true;
  let ultimaClave = null;

  const etiquetaTipo = (tipo) => (tipo === 'PREFERENCIAL' ? 'Preferencial' : 'General');

  const campanilla = () => {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      [880, 660].forEach((frecuencia, i) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.value = frecuencia;
        osc.connect(gain);
        gain.connect(ctx.destination);
        const t0 = ctx.currentTime + i * 0.35;
        gain.gain.setValueAtTime(0.0001, t0);
        gain.gain.exponentialRampToValueAtTime(0.4, t0 + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.33);
        osc.start(t0);
        osc.stop(t0 + 0.35);
      });
    } catch (e) { /* sin audio disponible */ }
  };

  const hablar = (texto) => {
    if (!('speechSynthesis' in window)) return;
    const enunciado = new SpeechSynthesisUtterance(texto);
    const voz = window.speechSynthesis.getVoices()
      .find((v) => v.lang && v.lang.toLowerCase().indexOf('es') === 0);
    if (voz) { enunciado.voice = voz; enunciado.lang = voz.lang; } else { enunciado.lang = 'es-ES'; }
    enunciado.rate = 0.9;
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(enunciado);
  };

  const anunciar = (llamado) => {
    if (!audioActivo || !llamado) return;
    const numero = parseInt(llamado.codigo.split('-')[1], 10);
    const texto = `Turno ${etiquetaTipo(llamado.tipo).toLowerCase()} ${numero}, módulo ${llamado.modulo}`;
    campanilla();
    setTimeout(() => hablar(texto), 800);
  };

  const llenarLista = (contenedor, filas, vacio) => {
    contenedor.innerHTML = '';
    if (filas.length === 0) {
      const d = document.createElement('div');
      d.className = 'text-white-50';
      d.textContent = vacio;
      contenedor.appendChild(d);
      return;
    }
    filas.forEach((fila) => contenedor.appendChild(fila));
  };

  const filaSimple = (texto) => {
    const d = document.createElement('div');
    d.textContent = texto;
    return d;
  };

  const filaLlamado = (llamado) => {
    const d = document.createElement('div');
    d.className = 'hnal-flex-between border-top border-light pt-1 mt-1';
    const codigo = document.createElement('span');
    codigo.textContent = llamado.codigo;
    const modulo = document.createElement('small');
    modulo.className = 'text-white-50';
    modulo.textContent = `Mód. ${llamado.modulo}`;
    d.appendChild(codigo);
    d.appendChild(modulo);
    return d;
  };

  const pintar = (e) => {
    $('overlay-cerrado').classList.toggle('d-none', e.jornadaActiva);
    $('total-general').textContent = e.totalGenerales;
    $('total-preferencial').textContent = e.totalPreferenciales;
    llenarLista($('lista-general'), e.generales.map((c, i) => filaSimple(`#${i + 1} ${c}`)), 'Sin turnos');
    llenarLista($('lista-preferencial'), e.preferenciales.map((c, i) => filaSimple(`#${i + 1} ${c}`)), 'Sin turnos');
    llenarLista($('lista-llamados'), e.recientes.slice(0, 3).map(filaLlamado), 'Aún no hay llamados');

    const actual = e.actual;
    if (actual) {
      $('llamado-tipo').textContent = (actual.tipo === 'PREFERENCIAL' ? 'Atención preferencial' : 'Atención general')
        + ' · Diríjase al módulo';
      $('llamado-codigo').textContent = actual.codigo;
      $('llamado-modulo').innerHTML = '';
      const span = document.createElement('span');
      span.className = 'fs-5';
      span.textContent = `Módulo ${actual.modulo}`;
      $('llamado-modulo').appendChild(span);
    } else {
      $('llamado-tipo').textContent = 'Esperando turnos...';
      $('llamado-codigo').textContent = '—';
      $('llamado-modulo').innerHTML = '';
    }
  };

  const procesar = (e) => {
    pintar(e);
    const clave = e.actual ? `${e.actual.idTurno}|${e.actual.horaUltimoLlamado}` : null;
    if (clave !== ultimaClave) {
      if (!primeraCarga && clave !== null) {
        anunciar(e.actual);
      }
      ultimaClave = clave;
    }
    primeraCarga = false;
  };

  const consultar = () => {
    fetch('/tv/estado', { cache: 'no-store' })
      .then((r) => {
        if (!r.ok) throw new Error(`estado ${r.status}`);
        return r.json();
      })
      .then((e) => { $('sin-conexion').classList.add('d-none'); procesar(e); })
      .catch(() => $('sin-conexion').classList.remove('d-none'));
  };

  const reloj = () => {
    const ahora = new Date();
    $('reloj').textContent = ahora.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    $('fecha').textContent = ahora.toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  };

  document.addEventListener('DOMContentLoaded', () => {
    $('btn-audio').addEventListener('click', () => {
      audioActivo = true;
      $('overlay-audio').classList.add('d-none');
      hablar('Audio activado');
    });
    if ('speechSynthesis' in window) {
      window.speechSynthesis.onvoiceschanged = () => window.speechSynthesis.getVoices();
    }
    reloj();
    setInterval(reloj, 1000);
    consultar();
    setInterval(consultar, 2000);
  });
})();
