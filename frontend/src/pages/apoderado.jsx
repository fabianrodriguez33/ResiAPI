import { Check, Plus, X } from 'lucide-react'
import { api, pagina } from '../api'
import { DetallePermiso, FormularioPermiso } from '../permisos'
import { Bloque, Boton, Cargando, ConDialogo, Franja, Pagina, Tabla, Vacio, useAccion, useDatos } from '../ui'

export function Permisos() {
  const hijos = useDatos('/familia/hijos', { cada: 20000 })
  const lista = useDatos(pagina('/familia/solicitudes'), { cada: 10000 })
  const [ejecutar, ocupado] = useAccion()
  const decidir = (id, aprobar) =>
    ejecutar(() => api.post(`/familia/solicitudes/${id}/decision`, { aprobar }), aprobar ? 'Permiso aceptado' : 'Permiso rechazado', lista.recargar)

  const porAceptar = lista.datos?.contenido.filter((p) => p.estado === 'PENDIENTE_APODERADO') ?? []

  const enviarPermiso = hijos.datos?.length > 0 && (
    <ConDialogo texto="Enviar permiso" variante="principal" icono={Plus}>
      {(cerrar) => <FormularioPermiso hijos={hijos.datos} alTerminar={() => { cerrar(); lista.recargar() }} />}
    </ConDialogo>
  )

  return (
    <Pagina titulo="Permisos de mis hijos" acciones={enviarPermiso}>
      {lista.datos && (
        <Franja tono={porAceptar.length ? 'espera' : 'bien'}>
          {porAceptar.length ? `Permisos que esperan tu respuesta: ${porAceptar.length}` : 'No tienes permisos por aceptar.'}
        </Franja>
      )}
      {porAceptar.length > 0 && (
        <Bloque titulo="Esperan tu respuesta">
          {porAceptar.map((p) => (
            <DetallePermiso key={p.id} p={p} conNombre acciones={(
              <>
                <Boton icono={Check} disabled={ocupado} onClick={() => decidir(p.id, true)}>Aceptar</Boton>
                <Boton variante="peligro" icono={X} disabled={ocupado} onClick={() => window.confirm('¿Rechazar este permiso?') && decidir(p.id, false)}>Rechazar</Boton>
              </>
            )} />
          ))}
        </Bloque>
      )}
      <Bloque titulo="Historial">
        <Cargando datos={lista} vacio="Aún no hay permisos. Puedes enviar uno por tu hijo." accion={enviarPermiso}>
          {(l) => (l.length === porAceptar.length ? <Vacio>Todavía no hay permisos resueltos.</Vacio> : l.filter((p) => p.estado !== 'PENDIENTE_APODERADO').map((p) => (
            <DetallePermiso key={p.id} p={p} conNombre acciones={p.estado === 'PENDIENTE_PRECEPTOR' && (
              <Boton variante="peligro" icono={X} onClick={() => window.confirm('¿Cancelar este permiso?') && ejecutar(() => api.post(`/familia/solicitudes/${p.id}/cancelar`), 'Cancelado', lista.recargar)}>Cancelar</Boton>
            )} />
          )))}
        </Cargando>
      </Bloque>
    </Pagina>
  )
}

export function Hijos() {
  const hijos = useDatos('/familia/hijos')
  return (
    <Pagina titulo="Mis hijos">
      <Cargando datos={hijos} vacio="No tienes residentes vinculados. Pide a administración que registre el vínculo.">
        {(l) => <Tabla filas={l} clave={(h) => h.residenteId} columnas={[
          ['Nombre', (h) => h.nombreCompleto], ['Código', (h) => h.codigo],
          ['Habitación', (h) => (h.habitacion ? `${h.habitacion} ${h.cama ?? ''}` : '—')],
          ['Pide solo', (h) => (h.puedeSolicitarSolo ? 'Sí' : 'No, con tu aceptación')],
          ['Pendientes', (h) => h.pendientes],
        ]} />}
      </Cargando>
    </Pagina>
  )
}
