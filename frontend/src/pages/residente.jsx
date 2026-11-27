import qrcode from 'qrcode-generator'
import { api, pagina } from '../api'
import { DetallePermiso, FormularioPermiso } from '../permisos'
import { Bloque, Boton, Cargando, Cifras, ConDialogo, Estado, Formulario, Pagina, Tabla, VerFoto, nombre, useAccion, useDatos } from '../ui'
import { decimal, fecha, fechaHora, hoy, instante } from '../formatos'

function CodigoQr({ contenido }) {
  const qr = qrcode(0, 'M')
  qr.addData(contenido)
  qr.make()
  return <div className="qr" role="img" aria-label="Mi código QR" dangerouslySetInnerHTML={{ __html: qr.createSvgTag({ scalable: true, margin: 2 }) }} />
}

export function Inicio() {
  const yo = useDatos('/residentes/yo')
  const hab = useDatos('/residentes/yo/habilitacion', { cada: 30000 })
  const mes = useDatos('/residentes/yo/resumen-mensual')
  const qr = useDatos('/residentes/yo/qr')
  return (
    <Pagina titulo={yo.datos ? `Hola, ${yo.datos.nombreCompleto.split(' ')[0]}` : 'Inicio'} sub={yo.datos?.habitacion ? `Habitación ${yo.datos.habitacion.numero}` : undefined}>
      {hab.datos && (
        <p className={`franja ${hab.datos.habilitado ? 'franja-bien' : 'franja-mal'}`}>
          {hab.datos.habilitado
            ? 'Esta semana estás habilitado para pedir permisos.'
            : `Esta semana no puedes pedir permisos: ${hab.datos.motivo}.`}
        </p>
      )}
      <div className="columnas">
        <Bloque titulo="Este mes">
          <Cargando datos={mes}>
            {(m) => (
              <Cifras items={[
                ['Asistencia a cultos', m.porcentajeAsistencia == null ? null : `${Math.round(m.porcentajeAsistencia)} %`, m.diasConCulto != null ? `${m.diasAsistidos} de ${m.diasConCulto} días` : null],
                ['Nota de cultos', m.notaCultos],
                ['Limpieza', decimal(m.promedioLimpieza), 'promedio sobre 20'],
              ]} />
            )}
          </Cargando>
        </Bloque>
        <Bloque titulo="Mi código QR">
          <Cargando datos={qr}>{(q) => <><CodigoQr contenido={q.contenido} /><p className="tenue">Para garita y para registrar tu asistencia a los cultos.</p></>}</Cargando>
        </Bloque>
      </div>
    </Pagina>
  )
}

export function Permisos() {
  const lista = useDatos(pagina('/permisos/solicitudes/mias'), { cada: 10000 })
  const [ejecutar] = useAccion()
  return (
    <Pagina
      titulo="Permisos"
      acciones={<ConDialogo texto="Pedir permiso" variante="principal">{(cerrar) => <FormularioPermiso alTerminar={() => { cerrar(); lista.recargar() }} />}</ConDialogo>}
    >
      <Cargando datos={lista} vacio="Aún no has pedido permisos.">
        {(permisos) => permisos.map((p) => (
          <DetallePermiso key={p.id} p={p} acciones={['PENDIENTE_APODERADO', 'PENDIENTE_PRECEPTOR', 'APROBADO'].includes(p.estado) && (
            <>
              {p.estado !== 'APROBADO' || !p.garitaSalida ? (
                <Boton variante="peligro" onClick={() => window.confirm('¿Cancelar esta solicitud?') && ejecutar(() => api.post(`/permisos/solicitudes/${p.id}/cancelar`), 'Solicitud cancelada', lista.recargar)}>Cancelar</Boton>
              ) : null}
              {p.estado === 'APROBADO' && (
                <ConDialogo texto="Pedir ampliación">
                  {(cerrar) => (
                    <Formulario
                      campos={[{ n: 'retornoNuevo', t: 'Nuevo retorno', tipo: 'datetime-local', requerido: true }, { n: 'motivo', t: 'Motivo', tipo: 'area', requerido: true }]}
                      enviar={(v) => api.post(`/permisos/solicitudes/${p.id}/ampliaciones`, { motivo: v.motivo, retornoNuevo: instante(v.retornoNuevo) })}
                      boton="Pedir ampliación"
                      exito="Ampliación enviada al pastor"
                      alTerminar={() => { cerrar(); lista.recargar() }}
                    />
                  )}
                </ConDialogo>
              )}
            </>
          )} />
        ))}
      </Cargando>
    </Pagina>
  )
}

export function Asistencias() {
  const asistencias = useDatos(pagina('/residentes/yo/asistencias'), { cada: 20000 })
  const movimientos = useDatos(pagina('/residentes/yo/movimientos', 30))
  return (
    <Pagina titulo="Asistencias y movimientos">
      <Bloque titulo="Cultos">
        <Cargando datos={asistencias} vacio="Sin asistencias registradas.">
          {(l) => <Tabla filas={l} columnas={[
            ['Fecha', (a) => fecha(a.fecha)], ['Culto', (a) => nombre(a.tipo)],
            ['Registrado', (a) => fechaHora(a.registradoEn)], ['Puntualidad', (a) => (a.despuesDelCorte ? 'Tarde' : 'A tiempo')],
          ]} />}
        </Cargando>
      </Bloque>
      <Bloque titulo="Salidas y retornos por garita">
        <Cargando datos={movimientos} vacio="Sin movimientos.">
          {(l) => <Tabla filas={l} columnas={[
            ['Movimiento', (m) => nombre(m.tipo)], ['Hora', (m) => fechaHora(m.registradoEn)],
            ['Tardanza', (m) => (m.minutosTarde ? `${m.minutosTarde} min` : '—')], ['Estado', (m) => (m.anulado ? 'Anulado' : 'Válido')],
          ]} />}
        </Cargando>
      </Bloque>
    </Pagina>
  )
}

export function Limpieza() {
  const resumen = useDatos('/residentes/yo/limpieza/resumen')
  const lista = useDatos(pagina('/residentes/yo/limpieza'), { cada: 20000 })
  return (
    <Pagina titulo="Limpieza">
      <Cargando datos={resumen}>
        {(r) => <Cifras items={[['Promedio', decimal(r.promedio), 'sobre 20'], ['Última nota', r.ultimoTotal, fecha(r.ultimaFecha)], ['Evaluaciones', r.evaluaciones]]} />}
      </Cargando>
      <Cargando datos={lista} vacio="Aún no te han evaluado.">
        {(l) => <Tabla filas={l} columnas={[
          ['Fecha', (e) => fecha(e.fecha)], ['Total', (e) => `${e.total} / ${e.maximo}`],
          ['Detalle', (e) => Object.entries(e.puntajes).map(([c, n]) => `${nombre(c)} ${n}`).join(' · ')],
          ['Observación', (e) => e.observacion || '—'],
        ]} />}
      </Cargando>
    </Pagina>
  )
}

export function Reservas() {
  const oferta = useDatos('/reservas/oferta', { cada: 15000 })
  const mias = useDatos(pagina('/reservas/mias', 30), { cada: 15000 })
  const [ejecutar] = useAccion()
  const recargar = () => { oferta.recargar(); mias.recargar() }
  const cancelar = (id) => window.confirm('¿Cancelar la reserva?') && ejecutar(() => api.post(`/reservas/${id}/cancelar`), 'Reserva cancelada', recargar)

  return (
    <Pagina titulo="Reservas de comida">
      <Bloque titulo="Disponible">
        <Cargando datos={oferta}>
          {(o) => o.comidas.map((c) => (
            <article className="item" key={`${c.tipo}-${c.fecha}`}>
              <header><strong>{nombre(c.tipo)} · {fecha(c.fecha)}</strong>{c.reservaActiva && <Estado valor={c.reservaActiva.estado} />}</header>
              <p className="tenue">{c.puedeReservar ? `Se reserva hasta ${fechaHora(c.cierra)}` : c.motivoNoDisponible || c.motivoCierre}</p>
              {c.advertenciaPermiso && <p className="nota">{c.advertenciaPermiso.mensaje}</p>}
              <div className="acciones">
                {c.puedeReservar && !c.reservaActiva && (
                  <ConDialogo texto="Reservar" titulo={`Reservar ${nombre(c.tipo)}`} variante="principal">
                    {(cerrar) => (
                      <Formulario
                        campos={[
                          { n: 'motivo', t: 'Motivo', tipo: 'area' },
                          { n: 'curso', t: 'Curso', si: () => c.tipo !== 'DESAYUNO_FRIO' },
                          { n: 'observacion', t: 'Observación' },
                          { n: 'foto', t: 'Foto de sustento', tipo: 'file' },
                        ]}
                        enviar={async ({ foto, ...v }) => {
                          const evidenciaId = foto ? (await api.subir(foto)).id : null
                          return api.post('/reservas', { ...v, tipo: c.tipo, fecha: c.fecha, evidenciaId })
                        }}
                        boton="Reservar"
                        exito="Reserva enviada"
                        alTerminar={() => { cerrar(); recargar() }}
                      />
                    )}
                  </ConDialogo>
                )}
                {c.reservaActiva && <Boton variante="peligro" onClick={() => cancelar(c.reservaActiva.id)}>Cancelar reserva</Boton>}
              </div>
            </article>
          ))}
        </Cargando>
      </Bloque>
      <Bloque titulo="Mis reservas">
        <Cargando datos={mias} vacio="No tienes reservas.">
          {(l) => <Tabla filas={l} columnas={[
            ['Fecha', (r) => fecha(r.fecha)], ['Comida', (r) => nombre(r.tipo)], ['Estado', (r) => <Estado valor={r.estado} />],
            ['Nota', (r) => r.motivoCancelacion || r.motivo || '—'],
            ['', (r) => <><VerFoto id={r.evidenciaId} />{r.fecha >= hoy() && ['PENDIENTE', 'RESERVADA'].includes(r.estado) && <Boton variante="enlace" onClick={() => cancelar(r.id)}>Cancelar</Boton>}</>],
          ]} />}
        </Cargando>
      </Bloque>
    </Pagina>
  )
}
