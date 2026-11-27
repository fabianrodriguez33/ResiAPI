// Piezas de permisos que comparten residente, apoderado y pastor.
import { api } from './api'
import { Estado, Formulario, nombre } from './ui'
import { fechaHora, instante } from './formatos'

export function DetallePermiso({ p, acciones, conNombre }) {
  return (
    <article className="item">
      <header>
        <strong>{conNombre ? `${p.residenteNombre} · ${p.residenteCodigo}` : p.destino}</strong>
        <Estado valor={p.estado} />
      </header>
      <p>
        {nombre(p.tipo)} · {conNombre && `${p.destino} · `}{fechaHora(p.salidaSolicitada)} → {fechaHora(p.retornoAutorizado ?? p.retornoSolicitado)}
      </p>
      <p className="tenue">{p.motivo}</p>
      {p.responsableNombre && <p className="tenue">Responsable: {p.responsableNombre} ({p.responsableCelular}) · Va con: {p.acompanante}</p>}
      {p.comentarioDecision && <p className="tenue">Respuesta: {p.comentarioDecision}</p>}
      {(p.garitaSalida || p.garitaEntrada) && (
        <p className="tenue">Garita: salió {fechaHora(p.garitaSalida)} · volvió {fechaHora(p.garitaEntrada)}</p>
      )}
      {acciones && <div className="acciones">{acciones}</div>}
    </article>
  )
}

/** Solicitud de permiso. Con `hijos` es el apoderado quien la envía. */
export function FormularioPermiso({ hijos, alTerminar }) {
  const campos = [
    hijos && { n: 'residenteId', t: 'Para quién', requerido: true, opciones: hijos.map((h) => [h.residenteId, h.nombreCompleto]) },
    { n: 'tipo', t: 'Tipo', requerido: true, opciones: [['CORTO', 'Corto (hasta 2 horas)'], ['LARGO', 'Largo (mismo día)'], ['PERNOCTE', 'Pernoctar fuera']] },
    { n: 'destino', t: 'Lugar', requerido: true },
    { n: 'motivo', t: 'Motivo', tipo: 'area', requerido: true },
    { n: 'salida', t: 'Salida', tipo: 'datetime-local', requerido: true },
    { n: 'retorno', t: 'Retorno', tipo: 'datetime-local', requerido: true },
    { n: 'responsableNombre', t: 'Responsable de la casa', si: (v) => v.tipo === 'PERNOCTE', requerido: true },
    { n: 'responsableCelular', t: 'Celular del responsable', si: (v) => v.tipo === 'PERNOCTE', requerido: true },
    { n: 'acompanante', t: 'Con quién va', si: (v) => v.tipo === 'PERNOCTE' },
  ].filter(Boolean)

  const enviar = (v) => {
    const cuerpo = {
      ...v,
      residenteId: v.residenteId ? Number(v.residenteId) : null,
      salida: instante(v.salida),
      retorno: instante(v.retorno),
    }
    return api.post(hijos ? '/familia/solicitudes' : '/permisos/solicitudes', cuerpo)
  }

  return <Formulario campos={campos} enviar={enviar} boton="Enviar solicitud" exito="Permiso registrado" alTerminar={alTerminar} />
}
