// Pantalla publica de llamados. Consulta /tv/estado cada 2 s y anuncia cada llamado nuevo (o repetido).
(() => {
  const $ = (id) => document.getElementById(id);
  const CLAVE_VOZ = 'hnal.tv.voz';

  let audioActivo = false;
  let primeraCarga = true;
  let ultimaClave = null;

  const etiquetaTipo = (tipo) => (tipo === 'PREFERENCIAL' ? 'Preferencial' : 'General');

  // ---------- voz del anuncio ----------
  const leerVozGuardada = () => { try { return localStorage.getItem(CLAVE_VOZ); } catch (e) { return null; } };
  const guardarVoz = (nombre) => { try { localStorage.setItem(CLAVE_VOZ, nombre); } catch (e) { /* sin almacenamiento */ } };

  const vocesEspanol = () => ('speechSynthesis' in window ? window.speechSynthesis.getVoices() : [])
    .filter((v) => v.lang && v.lang.toLowerCase().replace('_', '-').indexOf('es') === 0);

  const esNeuronal = (voz) => /natural|neural/i.test(voz.name);

  // Prefiere voces neuronales (en Edge: "Microsoft ... Online (Natural)"), luego las de la region (Peru, Mexico, EE. UU.).
  const puntaje = (voz) => {
    let p = esNeuronal(voz) ? 100 : 0;
    const nombre = voz.name.toLowerCase();
    if (nombre.indexOf('online') >= 0) p += 10;
    if (nombre.indexOf('google') >= 0) p += 5;
    const regiones = { 'es-pe': 30, 'es-mx': 20, 'es-us': 15, 'es-419': 15, 'es-es': 10 };
    p += regiones[voz.lang.toLowerCase().replace('_', '-')] || 0;
    return p;
  };

  // Orden: voz pedida en la direccion (?voz=Camila), voz guardada con el selector, y si no la mejor puntuada.
  const elegirVoz = () => {
    const voces = vocesEspanol();
    const pedida = new URLSearchParams(window.location.search).get('voz') || leerVozGuardada();
    if (pedida) {
      const exacta = voces.find((v) => v.name === pedida);
      const parcial = voces.find((v) => v.name.toLowerCase().indexOf(pedida.toLowerCase()) >= 0);
      if (exacta || parcial) return exacta || parcial;
    }
    return voces.slice().sort((a, b) => puntaje(b) - puntaje(a))[0] || null;
  };

  const llenarSelector = () => {
    const select = $('select-voz');
    if (!select) return;
    const voces = vocesEspanol().slice().sort((a, b) => puntaje(b) - puntaje(a));
    const elegida = elegirVoz();
    select.innerHTML = '';
    if (voces.length === 0) {
      const opcion = document.createElement('option');
      opcion.textContent = 'Voz predeterminada del navegador';
      select.appendChild(opcion);
      return;
    }
    voces.forEach((voz) => {
      const opcion = document.createElement('option');
      opcion.value = voz.name;
      opcion.textContent = `${esNeuronal(voz) ? '★ ' : ''}${voz.name} (${voz.lang})`;
      if (elegida && voz.name === elegida.name) opcion.selected = true;
      select.appendChild(opcion);
    });
    if (!voces.some(esNeuronal)) {
      $('ayuda-voz').textContent = 'Este navegador no ofrece voces neuronales. Abra esta pantalla en Microsoft Edge para usar las voces "Natural".';
    }
  };

  const hablar = (texto) => {
    if (!('speechSynthesis' in window)) return;
    const enunciado = new SpeechSynthesisUtterance(texto);
    const voz = elegirVoz();
    if (voz) { enunciado.voice = voz; enunciado.lang = voz.lang; } else { enunciado.lang = 'es-ES'; }
    enunciado.rate = 0.95;
    enunciado.pitch = 1;
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(enunciado);
  };

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

  const textoAnuncio = (llamado) => {
    const numero = parseInt(llamado.codigo.split('-')[1], 10);
    return `Turno ${etiquetaTipo(llamado.tipo).toLowerCase()} ${numero}. Módulo ${llamado.modulo}.`;
  };

  const anunciar = (llamado) => {
    if (!audioActivo || !llamado) return;
    campanilla();
    setTimeout(() => hablar(textoAnuncio(llamado)), 800);
  };

  // ---------- pintar la pantalla ----------
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

    const tarjeta = $('card-llamado');
    const actual = e.actual;
    if (actual) {
      // Naranja = llamado, esperando al usuario. Verde = ya esta en atencion.
      tarjeta.style.background = actual.enAtencion ? 'linear-gradient(135deg, #198754, #146c43)' : '';
      $('llamado-tipo').textContent = actual.enAtencion
        ? 'En atención'
        : (actual.tipo === 'PREFERENCIAL' ? 'Atención preferencial' : 'Atención general') + ' · Diríjase al módulo';
      $('llamado-codigo').textContent = actual.codigo;
      $('llamado-modulo').innerHTML = '';
      const span = document.createElement('span');
      span.className = 'fs-5';
      span.textContent = `Módulo ${actual.modulo}`;
      $('llamado-modulo').appendChild(span);
    } else {
      tarjeta.style.background = '';
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
    llenarSelector();
    if ('speechSynthesis' in window) {
      window.speechSynthesis.onvoiceschanged = llenarSelector;
    }
    $('select-voz').addEventListener('change', (evento) => guardarVoz(evento.target.value));
    $('btn-probar-voz').addEventListener('click', () => {
      guardarVoz($('select-voz').value);
      hablar('Turno preferencial 12. Módulo 3.');
    });
    $('btn-audio').addEventListener('click', () => {
      guardarVoz($('select-voz').value);
      audioActivo = true;
      $('overlay-audio').classList.add('d-none');
      hablar('Audio activado');
    });
    reloj();
    setInterval(reloj, 1000);
    consultar();
    setInterval(consultar, 2000);
  });
})();
