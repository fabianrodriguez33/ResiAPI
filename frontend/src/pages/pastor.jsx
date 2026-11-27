import { useCallback, useState } from 'react'
import {
  ArrowLeft, BookOpen, ChartColumn, Check, ChevronRight, ClipboardCheck, Download, Lock, LockOpen, Trash2, UserCheck, X,
} from 'lucide-react'
import { api, pagina } from '../api'
import { DetallePermiso } from '../permisos'
import {
  Bloque, Boton, Cargando, Cifras, ConDialogo, ConMotivo, Estado, Formulario, Franja, LectorQr, Pagina, Pestanas, Tabla, Vacio, VerFoto,
  nombre, useAccion, useDatos,
} from '../ui'
import { decimal, fecha, fechaHora, hoy } from '../formatos'

/* ---------- inicio ---------- */

export function Inicio() {
  const permisos = useDatos(pagina('/permisos/solicitudes?estado=PENDIENTE_PRECEPTOR', 1), { cada: 15000 })
  const ampliaciones = useDatos(pagina('/permisos/ampliaciones/pendientes', 1), { cada: 15000 })
  const justificaciones = useDatos(pagina('/permisos/justificaciones/pendientes', 1), { cada: 30000 })
  const reservas = useDatos(`/reservas/del-dia?fecha=${hoy()}`, { cada: 30000 })
  const faltas = useDatos('/habilitacion/faltas-por-justificar', { cada: 60000 })
  // Primero lo que vence antes: permisos y ampliaciones tienen hora de salida o de retorno.
  const fuentes = [
    ['#/permisos', 'Permisos por revisar', permisos, permisos.datos?.totalElementos],
    ['#/permisos/ampliaciones', 'Ampliaciones por revisar', ampliaciones, ampliaciones.datos?.totalElementos],
    ['#/reservas', 'Reservas de hoy sin responder', reservas, reservas.datos?.filter((r) => r.estado === 'PENDIENTE').length],
    ['#/permisos/justificaciones', 'Justificaciones por revisar', justificaciones, justificaciones.datos?.totalElementos],
    ['#/faltas', 'Faltas por justificar', faltas, faltas.datos?.length],
  ]
  const cargando = fuentes.some(([, , d]) => d.cargando)
  const fallo = fuentes.find(([, , d]) => d.error && !d.datos)?.[2]
  const pendientes = fuentes.filter(([, , , n]) => n > 0)

  return (
    <Pagina titulo="Inicio" sub="Lo que espera tu respuesta">
      {fallo ? <Cargando datos={fallo} /> : cargando ? <p className="vacio">Cargando…</p> : pendientes.length === 0 ? (
        <Vacio accion={<a href="#/cultos">Ir a cultos</a>}>No tienes nada pendiente por revisar.</Vacio>
      ) : (
        <ul className="pendientes">
          {pendientes.map(([enlace, titulo, , n]) => (
            <li key={enlace}><a href={enlace}><strong>{n}</strong><span>{titulo}</span><ChevronRight size={20} /></a></li>
          ))}
        </ul>
      )}
    </Pagina>
  )
}

/* ---------- permisos ---------- */

function Resumen({ id }) {
  const r = useDatos(`/permisos/solicitudes/${id}/resumen-residente`)
  return (
    <Cargando datos={r}>
      {(d) => <Cifras items={[
        ['Asistencias', `${d.asistencias} de ${d.cultosRealizados}`, `últimos ${d.dias} días`],
        ['Tardanzas', d.despuesDelCorte],
        ['Limpieza', decimal(d.promedioLimpieza), `${d.evaluaciones} evaluaciones`],
      ]} />}
    </Cargando>
  )
}

function ListaPermisos({ estado }) {
  const lista = useDatos(pagina(`/permisos/solicitudes?estado=${estado}`), { cada: 10000 })
  const [ejecutar, ocupado] = useAccion()
  return (
    <Cargando datos={lista} vacio="No hay permisos en esta bandeja." accion={estado !== 'PENDIENTE_PRECEPTOR' ? null : <a href="#/inicio">Ver otros pendientes</a>}>
      {(l) => l.map((p) => (
        <DetallePermiso key={p.id} p={p} conNombre acciones={(
          <>
            <ConDialogo texto="Ver asistencias y limpieza" titulo={p.residenteNombre} variante="enlace" icono={ChartColumn}>{() => <Resumen id={p.id} />}</ConDialogo>
            {p.estado === 'PENDIENTE_PRECEPTOR' && (
              <>
                <Boton icono={Check} disabled={ocupado} onClick={() => ejecutar(() => api.post(`/permisos/solicitudes/${p.id}/decision`, { aprobar: true }), 'Permiso aprobado', lista.recargar)}>Aprobar</Boton>
                <ConMotivo texto="Rechazar" etiqueta="Comentario" enviar={(comentario) => api.post(`/permisos/solicitudes/${p.id}/decision`, { aprobar: false, comentario })} alTerminar={lista.recargar} />
              </>
            )}
            {p.estado === 'APROBADO' && !p.garitaSalida && (
              <ConMotivo texto="Anular" enviar={(motivo) => api.post(`/permisos/solicitudes/${p.id}/anular`, { motivo })} alTerminar={lista.recargar} />
            )}
          </>
        )} />
      ))}
    </Cargando>
  )
}

function Revisiones({ ruta, vacio }) {
  const lista = useDatos(pagina(`/permisos/${ruta}/pendientes`), { cada: 15000 })
  const [ejecutar] = useAccion()
  const decidir = (id, aprobar) => ejecutar(() => api.post(`/permisos/${ruta}/${id}/decision`, { aprobar, comentario: aprobar ? '' : 'No procede' }), 'Listo', lista.recargar)
  return (
    <Cargando datos={lista} vacio={vacio}>
      {(l) => l.map((x) => (
        <article className="item" key={x.id}>
          <header><strong>{x.residenteNombre} · {x.residenteCodigo}</strong><Estado valor={x.estado} /></header>
          <p>{x.motivo}</p>
          {x.retornoNuevo && <p className="tenue">Retorno: {fechaHora(x.retornoOriginal)} → {fechaHora(x.retornoNuevo)}</p>}
          <div className="acciones">
            <VerFoto id={x.evidenciaId} />
            <Boton icono={Check} onClick={() => decidir(x.id, true)}>Aprobar</Boton>
            <Boton variante="peligro" icono={X} onClick={() => decidir(x.id, false)}>Rechazar</Boton>
          </div>
        </article>
      ))}
    </Cargando>
  )
}

export function Permisos({ apartado }) {
  const [vista, setVista] = useState(apartado || 'PENDIENTE_PRECEPTOR')
  return (
    <Pagina titulo="Permisos">
      <Pestanas valor={vista} onCambio={setVista} opciones={[
        ['PENDIENTE_PRECEPTOR', 'Por revisar'], ['APROBADO', 'Aprobados'], ['PENDIENTE_APODERADO', 'Esperan apoderado'],
        ['RECHAZADO', 'Rechazados'], ['ampliaciones', 'Ampliaciones'], ['justificaciones', 'Justificaciones'],
      ]} />
      {vista === 'ampliaciones' || vista === 'justificaciones'
        ? <Revisiones key={vista} ruta={vista} vacio="Nada pendiente." />
        : <ListaPermisos key={vista} estado={vista} />}
    </Pagina>
  )
}

/* ---------- cultos ---------- */

function Sesion({ id, volver }) {
  const sesion = useDatos(`/cultos/sesiones/${id}`, { cada: 10000 })
  const presentes = useDatos(`/cultos/sesiones/${id}/presentes`, { cada: 10000 })
  const [codigo, setCodigo] = useState('')
  const [buscado, setBuscado] = useState('')
  const [ejecutar, ocupado] = useAccion()
  const recargar = useCallback(() => { sesion.recargar(); presentes.recargar() }, [sesion.recargar, presentes.recargar])

  const registrar = useCallback((cuerpo) =>
    ejecutar(() => api.post(`/cultos/sesiones/${id}/asistencias`, cuerpo), 'Asistencia registrada', () => { setCodigo(''); recargar() }),
  [ejecutar, id, recargar])
  const porQr = useCallback((qr) => registrar({ qr }), [registrar])

  const s = sesion.datos
  const abierta = s?.estado === 'ABIERTA'
  const filtrados = presentes.datos?.filter((a) => `${a.nombreCompleto} ${a.codigo}`.toLowerCase().includes(buscado.toLowerCase())) ?? []

  return (
    <Pagina
      titulo={s ? `Culto ${nombre(s.tipo).toLowerCase()} · ${fecha(s.fecha)}` : 'Culto'}
      sub={s && `${s.presentes} presentes · ${abierta ? 'abierto' : 'cerrado'}`}
      acciones={(
        <>
          <Boton variante="enlace" icono={ArrowLeft} onClick={volver}>Volver</Boton>
          <Boton variante="secundario" icono={Download} onClick={() => ejecutar(() => api.descargar(`/cultos/sesiones/${id}/exportar`, `culto-${s.fecha}.csv`))}>Exportar</Boton>
          {s && <Boton variante={abierta ? 'peligro' : 'secundario'} icono={abierta ? Lock : LockOpen} onClick={() => ejecutar(() => api.post(`/cultos/sesiones/${id}/${abierta ? 'cerrar' : 'reabrir'}`), abierta ? 'Culto cerrado' : 'Culto reabierto', recargar)}>{abierta ? 'Cerrar culto' : 'Reabrir'}</Boton>}
        </>
      )}
    >
      {abierta && (
        <Bloque titulo="Registrar asistencia">
          <form className="fila" onSubmit={(e) => { e.preventDefault(); if (codigo.trim()) registrar({ codigo: codigo.trim() }) }}>
            <input aria-label="Código del residente" placeholder="Código del residente" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
            <button type="submit" className="boton boton-principal" disabled={ocupado}><UserCheck size={17} />Registrar</button>
          </form>
          <LectorQr onCodigo={porQr} />
        </Bloque>
      )}
      <Bloque titulo="Presentes">
        <input type="search" aria-label="Buscar presente" placeholder="Buscar por nombre o código" value={buscado} onChange={(e) => setBuscado(e.target.value)} />
        {filtrados.length === 0 ? <Vacio>{buscado ? 'Nadie coincide con la búsqueda.' : 'Aún no hay presentes. Registra por código o QR.'}</Vacio> : (
          <Tabla filas={filtrados} columnas={[
            ['Residente', (a) => a.nombreCompleto], ['Código', (a) => a.codigo], ['Hora', (a) => fechaHora(a.registradoEn)],
            ['Puntualidad', (a) => (a.despuesDelCorte ? 'Tarde' : 'A tiempo')],
            ['', (a) => <ConMotivo texto="Anular" titulo="Anular asistencia" variante="enlace" enviar={(motivo) => api.post(`/cultos/asistencias/${a.id}/anular`, { motivo })} alTerminar={recargar} />],
          ]} />
        )}
      </Bloque>
    </Pagina>
  )
}

export function Cultos() {
  const [dia, setDia] = useState(hoy())
  const [abierta, setAbierta] = useState(null)
  const sesiones = useDatos(`/cultos/sesiones?fecha=${dia}`, { cada: 15000 })
  const [ejecutar, ocupado] = useAccion()
  const abrir = (tipo) => ejecutar(() => api.post('/cultos/sesiones', { tipo }), 'Culto abierto', (s) => { setDia(hoy()); setAbierta(s.id); sesiones.recargar() })

  if (abierta) return <Sesion id={abierta} volver={() => { setAbierta(null); sesiones.recargar() }} />
  return (
    <Pagina titulo="Cultos" acciones={(
      <>
        <Boton icono={BookOpen} disabled={ocupado} onClick={() => abrir('MATUTINO')}>Abrir matutino</Boton>
        <Boton icono={BookOpen} disabled={ocupado} onClick={() => abrir('VESPERTINO')}>Abrir vespertino</Boton>
      </>
    )}>
      <label className="campo campo-corto"><span>Fecha</span><input type="date" value={dia} max={hoy()} onChange={(e) => setDia(e.target.value)} /></label>
      <Cargando datos={sesiones} vacio="No hubo cultos ese día. Abre uno con los botones de arriba o cambia la fecha.">
        {(l) => l.map((s) => (
          <article className="item" key={s.id}>
            <header><strong>Culto {nombre(s.tipo).toLowerCase()}</strong><Estado valor={s.estado} /></header>
            <p className="tenue">{s.presentes} presentes · inició {fechaHora(s.inicio)}</p>
            <div className="acciones"><Boton variante="secundario" icono={ClipboardCheck} onClick={() => setAbierta(s.id)}>Ver lista</Boton></div>
          </article>
        ))}
      </Cargando>
    </Pagina>
  )
}

/* ---------- limpieza ---------- */

const GENERALES = [['basura', 'Basura'], ['olores', 'Olores']]
const PERSONALES = [['ropero', 'Ropero', 5], ['cama', 'Cama', 3], ['escritorio', 'Escritorio', 3], ['piso', 'Piso', 3]]

function Nota({ etiqueta, max, valor, onCambio }) {
  return (
    <label className="campo campo-nota">
      <span>{etiqueta} (0 a {max})</span>
      <input type="number" required min={0} max={max} value={valor ?? ''} onChange={(e) => onCambio(e.target.value)} />
    </label>
  )
}

function Evaluacion({ habitacion, dia, alTerminar }) {
  const previa = useDatos(habitacion.evaluados > 0 ? `/limpieza/habitaciones/${habitacion.habitacionId}/evaluacion?fecha=${dia}` : null)
  if (previa.cargando) return <p className="vacio">Cargando…</p>
  return <FormularioEvaluacion habitacion={habitacion} dia={dia} previa={previa.datos} alTerminar={alTerminar} />
}

function FormularioEvaluacion({ habitacion, dia, previa, alTerminar }) {
  const [generales, setGenerales] = useState({ basura: previa?.basura ?? '', olores: previa?.olores ?? '' })
  const [personas, setPersonas] = useState(() => habitacion.residentes.map((r) => {
    const antes = previa?.residentes.find((p) => p.residenteId === r.residenteId)
    return { residenteId: r.residenteId, nombre: r.nombreCompleto, ...Object.fromEntries(PERSONALES.map(([k]) => [k, antes?.[k] ?? ''])) }
  }))
  const [ejecutar, ocupado] = useAccion()
  const corregir = previa != null

  const guardar = (e) => {
    e.preventDefault()
    const cuerpo = {
      fecha: dia,
      basura: Number(generales.basura),
      olores: Number(generales.olores),
      residentes: personas.map((p) => ({ residenteId: p.residenteId, ...Object.fromEntries(PERSONALES.map(([k]) => [k, Number(p[k])])) })),
    }
    ejecutar(() => api.post(`/limpieza/habitaciones/${habitacion.habitacionId}/${corregir ? 'reemplazar' : 'evaluaciones'}`, cuerpo),
      corregir ? 'Cambios guardados' : 'Evaluación registrada', alTerminar)
  }

  return (
    <form onSubmit={guardar} className="formulario">
      <fieldset>
        <legend>Habitación</legend>
        {GENERALES.map(([k, t]) => <Nota key={k} etiqueta={t} max={3} valor={generales[k]} onCambio={(v) => setGenerales((g) => ({ ...g, [k]: v }))} />)}
      </fieldset>
      {personas.map((p, i) => (
        <fieldset key={p.residenteId}>
          <legend>{p.nombre}</legend>
          {PERSONALES.map(([k, t, max]) => <Nota key={k} etiqueta={t} max={max} valor={p[k]} onCambio={(v) => setPersonas((a) => a.map((x, j) => (j === i ? { ...x, [k]: v } : x)))} />)}
        </fieldset>
      ))}
      <button type="submit" className="boton boton-principal" disabled={ocupado}><Check size={17} />{corregir ? 'Guardar cambios' : 'Registrar evaluación'}</button>
    </form>
  )
}

export function Limpieza() {
  const [dia, setDia] = useState(hoy())
  const habitaciones = useDatos(`/limpieza/habitaciones?fecha=${dia}`, { cada: 20000 })
  return (
    <Pagina titulo="Limpieza" sub="Evaluación por habitación">
      <label className="campo campo-corto"><span>Fecha</span><input type="date" value={dia} max={hoy()} onChange={(e) => setDia(e.target.value)} /></label>
      <Cargando datos={habitaciones} vacio="No hay habitaciones con residentes. La asignación la hace administración.">
        {(l) => (
          <div className="lista-filas">
            {l.map((h) => (
              <article className="item" key={h.habitacionId}>
                <header><strong>Habitación {h.numero}</strong>{h.evaluados > 0 && <Estado valor="APROBADO" texto="Evaluada" />}</header>
                {h.residentes.length === 0 ? <p className="tenue">Cuarto no disponible</p> : (
                  <>
                    <p className="tenue">{h.residentes.map((r) => r.nombreCompleto.split(' ').slice(0, 2).join(' ')).join(', ')}</p>
                    <div className="acciones">
                      <ConDialogo texto={h.evaluados > 0 ? 'Corregir' : 'Evaluar'} icono={ClipboardCheck} titulo={`Habitación ${h.numero}`}>
                        {(cerrar) => <Evaluacion habitacion={h} dia={dia} alTerminar={() => { cerrar(); habitaciones.recargar() }} />}
                      </ConDialogo>
                      {h.evaluados > 0 && <ConMotivo texto="Eliminar" icono={Trash2} variante="enlace" enviar={(motivo) => api.post(`/limpieza/habitaciones/${h.habitacionId}/eliminar`, { fecha: dia, motivo })} alTerminar={habitaciones.recargar} />}
                    </div>
                  </>
                )}
              </article>
            ))}
          </div>
        )}
      </Cargando>
    </Pagina>
  )
}

/* ---------- reservas ---------- */

const GRUPOS = [['DESAYUNO', 'Desayuno', ['DESAYUNO_FRIO']], ['ALMUERZO', 'Almuerzo', ['ALMUERZO']], ['CENA', 'Cena', ['CENA_TAPER', 'CENA_SEGUNDO_TURNO']]]

export function Reservas() {
  const [dia, setDia] = useState(hoy())
  const [grupo, setGrupo] = useState('ALMUERZO')
  const lista = useDatos(`/reservas/del-dia?fecha=${dia}`, { cada: 10000 })
  const aperturas = useDatos(`/reservas/aperturas?fecha=${dia}`)
  const cierres = useDatos(`/reservas/cierres?fecha=${dia}`)
  const [ejecutar, ocupado] = useAccion()
  const tipos = GRUPOS.find((g) => g[0] === grupo)[2]
  const delGrupo = lista.datos?.filter((r) => tipos.includes(r.tipo)) ?? []
  const recargar = () => { lista.recargar(); aperturas.recargar(); cierres.recargar() }
  const cerrada = cierres.datos?.includes(grupo)
  const abierta = aperturas.datos?.includes(grupo)

  return (
    <Pagina titulo="Reservas de comida" acciones={(
      <>
        <Boton variante="secundario" icono={Download} disabled={ocupado} onClick={() => ejecutar(() => api.descargar(`/reservas/exportar?fecha=${dia}&grupo=${grupo}`, `reservas-${grupo.toLowerCase()}-${dia}.csv`))}>Exportar</Boton>
        <Boton variante="secundario" icono={LockOpen} disabled={ocupado} onClick={() => ejecutar(() => api.post('/reservas/abrir', { fecha: dia, grupo }), 'Reservas abiertas', recargar)}>Abrir reservas</Boton>
        <Boton variante="peligro" icono={Lock} disabled={ocupado} onClick={() => window.confirm('Al cerrar se cancelan las pendientes. ¿Continuar?') && ejecutar(() => api.post('/reservas/cerrar', { fecha: dia, grupo }), 'Reservas cerradas', recargar)}>Cerrar reservas</Boton>
      </>
    )}>
      <label className="campo campo-corto"><span>Fecha</span><input type="date" value={dia} onChange={(e) => setDia(e.target.value)} /></label>
      <Pestanas valor={grupo} onCambio={setGrupo} opciones={GRUPOS.map(([v, t]) => [v, t])} />
      <p className="tenue">{cerrada ? 'Cerradas para esta fecha.' : abierta ? 'Abiertas por el pastor.' : 'Según el horario normal.'} {delGrupo.length} registros.</p>
      <Cargando datos={lista} vacio="No hay reservas ese día. Cambia la fecha o abre las reservas.">
        {() => (delGrupo.length === 0 ? <Vacio>No hay reservas de esta comida. Revisa las otras pestañas.</Vacio> : (
          <Tabla filas={delGrupo} columnas={[
            ['Residente', (r) => r.residenteNombre], ['Código', (r) => r.codigo], ['Comida', (r) => nombre(r.tipo)],
            ['Detalle', (r) => [r.motivo, r.curso, r.observacion].filter(Boolean).join(' · ') || '—'],
            ['Estado', (r) => <Estado valor={r.estado} />],
            ['', (r) => (
              <div className="acciones">
                <VerFoto id={r.evidenciaId} />
                {r.estado === 'PENDIENTE' && (
                  <>
                    <Boton icono={Check} onClick={() => ejecutar(() => api.post(`/reservas/${r.id}/decision`, { aprobar: true }), 'Reserva aprobada', lista.recargar)}>Aprobar</Boton>
                    <ConMotivo texto="Rechazar" etiqueta="Comentario" enviar={(comentario) => api.post(`/reservas/${r.id}/decision`, { aprobar: false, comentario })} alTerminar={lista.recargar} />
                  </>
                )}
                {['PENDIENTE', 'RESERVADA'].includes(r.estado) && <ConMotivo texto="Eliminar" icono={Trash2} variante="enlace" enviar={(motivo) => api.post(`/reservas/${r.id}/cancelar-pastor`, { motivo })} alTerminar={lista.recargar} />}
              </div>
            )],
          ]} />
        ))}
      </Cargando>
    </Pagina>
  )
}

/* ---------- faltas y reporte ---------- */

export function Faltas() {
  const lista = useDatos('/habilitacion/faltas-por-justificar')
  return (
    <Pagina titulo="Faltas por justificar" sub="Con dos faltas sin justificar en la semana, el residente queda sin permisos la semana siguiente">
      <Cargando datos={lista} vacio="No hay faltas por justificar." accion={<a href="#/inicio">Ver otros pendientes</a>}>
        {(l) => <Tabla filas={l} clave={(f) => `${f.residenteId}-${f.fecha}`} columnas={[
          ['Residente', (f) => f.nombre], ['Código', (f) => f.codigo], ['Falta', (f) => fecha(f.fecha)], ['Plazo', (f) => fecha(f.plazo)],
          ['', (f) => <ConMotivo texto="Justificar" icono={Check} variante="secundario" enviar={(motivo) => api.post('/habilitacion/faltas/justificar', { residenteId: f.residenteId, fecha: f.fecha, motivo })} alTerminar={lista.recargar} />],
        ]} />}
      </Cargando>
    </Pagina>
  )
}

export function Reporte() {
  const [mes, setMes] = useState(hoy().slice(0, 7))
  const [anio, m] = mes.split('-').map(Number)
  const filas = useDatos(`/reportes/mensual?anio=${anio}&mes=${m}`)
  const [ejecutar] = useAccion()
  return (
    <Pagina titulo="Reporte mensual" acciones={(
      <Boton variante="secundario" icono={Download} onClick={() => ejecutar(() => api.descargar(`/reportes/mensual.xlsx?anio=${anio}&mes=${m}`, `reporte-${mes}.xlsx`, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'))}>Descargar Excel</Boton>
    )}>
      <label className="campo campo-corto"><span>Mes</span><input type="month" value={mes} onChange={(e) => e.target.value && setMes(e.target.value)} /></label>
      <Cargando datos={filas} vacio="No hay datos de ese mes. Elige otro.">
        {(l) => <Tabla filas={l} clave={(f) => f.residenteId} columnas={[
          ['Residente', (f) => f.nombreCompleto], ['Código', (f) => f.codigo], ['Habitación', (f) => f.habitacion || '—'],
          ['Asistencia', (f) => (f.resumen.porcentajeAsistencia == null ? '—' : `${Math.round(f.resumen.porcentajeAsistencia)} %`)],
          ['Nota cultos', (f) => f.resumen.notaCultos || '—'], ['Limpieza', (f) => decimal(f.resumen.promedioLimpieza) ?? '—'],
        ]} />}
      </Cargando>
    </Pagina>
  )
}
