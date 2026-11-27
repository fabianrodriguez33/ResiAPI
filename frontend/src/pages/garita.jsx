import { useCallback, useState } from 'react'
import { api, pagina } from '../api'
import { Bloque, Boton, Cargando, LectorQr, Pagina, Tabla, nombre, useAccion, useDatos } from '../ui'
import { DetallePermiso } from '../permisos'
import { fechaHora } from '../formatos'

export function Verificar() {
  const [codigo, setCodigo] = useState('')
  const [consulta, setConsulta] = useState(null) // { qr, permisos, verificacion }
  const [ejecutar, ocupado] = useAccion()

  const buscar = useCallback((qr) => {
    const limpio = qr.trim()
    if (!limpio) return
    setCodigo(limpio)
    setConsulta(null)
    ejecutar(() => api.post('/garita/permisos-del-residente', { qr: limpio }), null, (r) => setConsulta({ qr: limpio, ...r }))
  }, [ejecutar])

  const verificar = (solicitudId) =>
    ejecutar(() => api.post('/garita/verificaciones', { qr: consulta.qr, solicitudId }), null, (v) => setConsulta((c) => ({ ...c, verificacion: v, solicitudId })))

  const registrar = () => {
    const v = consulta.verificacion
    ejecutar(
      () => api.post('/garita/movimientos', { qr: consulta.qr, tipo: v.movimientoPermitido, solicitudId: v.solicitudId }),
      v.movimientoPermitido === 'SALIDA' ? 'Salida registrada' : 'Retorno registrado',
      () => { setConsulta(null); setCodigo('') },
    )
  }

  const v = consulta?.verificacion
  return (
    <Pagina titulo="Garita" sub="Escanea el QR del residente o escribe su código">
      <Bloque>
        <form className="fila" onSubmit={(e) => { e.preventDefault(); buscar(codigo) }}>
          <input aria-label="Código del residente" placeholder="Código o QR del residente" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
          <button type="submit" className="boton boton-principal" disabled={ocupado}>Buscar</button>
        </form>
        <LectorQr onCodigo={buscar} />
      </Bloque>

      {consulta && (
        <Bloque titulo={consulta.residente ? `${consulta.residente.nombreCompleto} · ${consulta.residente.codigo}` : 'Resultado'}>
          {consulta.mensaje && <p>{consulta.mensaje}</p>}
          {consulta.permisos?.map((p) => (
            <article className="item" key={p.id}>
              <header><strong>{p.destino}</strong>{p.salidaAbierta && <span className="estado estado-espera">Está fuera</span>}</header>
              <p>{nombre(p.tipo)} · {fechaHora(p.salida)} → {fechaHora(p.retorno)}</p>
              <p className="tenue">{p.motivo}</p>
              <div className="acciones"><Boton variante="secundario" onClick={() => verificar(p.id)}>Verificar este permiso</Boton></div>
            </article>
          ))}
          {v && (
            <div className={`franja ${v.movimientoPermitido ? 'franja-bien' : 'franja-mal'}`}>
              <strong>{nombre(v.resultado)}</strong>
              <p>{v.mensaje}</p>
              {v.movimientoPermitido && <Boton disabled={ocupado} onClick={registrar}>Registrar {v.movimientoPermitido === 'SALIDA' ? 'salida' : 'retorno'}</Boton>}
            </div>
          )}
        </Bloque>
      )}
    </Pagina>
  )
}

export function Aprobados() {
  const lista = useDatos(pagina('/garita/permisos'), { cada: 10000 })
  return (
    <Pagina titulo="Permisos aprobados">
      <Cargando datos={lista} vacio="No hay permisos aprobados.">
        {(l) => l.map((p) => <DetallePermiso key={p.id} p={p} conNombre />)}
      </Cargando>
    </Pagina>
  )
}

export function Historial() {
  const lista = useDatos(pagina('/garita/movimientos'), { cada: 10000 })
  return (
    <Pagina titulo="Historial de garita">
      <Cargando datos={lista} vacio="Sin movimientos.">
        {(l) => <Tabla filas={l} columnas={[
          ['Hora', (m) => fechaHora(m.registradoEn)], ['Residente', (m) => m.nombreCompleto], ['Código', (m) => m.codigo],
          ['Movimiento', (m) => nombre(m.tipo)], ['Tardanza', (m) => (m.minutosTarde ? `${m.minutosTarde} min` : '—')],
          ['Estado', (m) => (m.anulado ? `Anulado: ${m.motivoAnulacion}` : 'Válido')],
        ]} />}
      </Cargando>
    </Pagina>
  )
}
