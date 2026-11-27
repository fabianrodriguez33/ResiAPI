import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { api } from './api'

/* ---------- datos ---------- */

/** Carga `ruta` y, si se indica, la vuelve a pedir cada `cada` ms (los estados cambian desde otros roles). */
export function useDatos(ruta, { cada } = {}) {
  const [estado, setEstado] = useState({ datos: null, error: null, cargando: true })
  const vigente = useRef(ruta)
  vigente.current = ruta

  const recargar = useCallback(async () => {
    if (!ruta) return setEstado({ datos: null, error: null, cargando: false })
    try {
      const datos = await api.get(ruta)
      if (vigente.current === ruta) setEstado({ datos, error: null, cargando: false })
    } catch (error) {
      if (vigente.current === ruta) setEstado((e) => ({ ...e, error, cargando: false }))
    }
  }, [ruta])

  useEffect(() => {
    setEstado({ datos: null, error: null, cargando: true })
    recargar()
    if (!cada) return undefined
    const t = setInterval(() => !document.hidden && recargar(), cada)
    return () => clearInterval(t)
  }, [recargar, cada])

  return { ...estado, recargar }
}

/* ---------- avisos y confirmaciones ---------- */

const Avisos = createContext(() => {})
export const useAviso = () => useContext(Avisos)

export function ProveedorAvisos({ children }) {
  const [aviso, setAviso] = useState(null)
  const mostrar = useCallback((texto, tipo = 'ok') => setAviso({ texto, tipo, id: Date.now() }), [])
  useEffect(() => {
    if (!aviso) return undefined
    const t = setTimeout(() => setAviso(null), 4500)
    return () => clearTimeout(t)
  }, [aviso])
  return (
    <Avisos.Provider value={mostrar}>
      {children}
      <div className="avisos" role="status" aria-live="polite">
        {aviso && <div className={`aviso aviso-${aviso.tipo}`}>{aviso.texto}</div>}
      </div>
    </Avisos.Provider>
  )
}

/** Ejecuta una acción, avisa del resultado y recarga. */
export function useAccion() {
  const aviso = useAviso()
  const [ocupado, setOcupado] = useState(false)
  const ejecutar = useCallback(async (accion, exito, despues) => {
    setOcupado(true)
    try {
      const r = await accion()
      if (exito) aviso(exito)
      await despues?.(r)
      return true
    } catch (e) {
      aviso(e.message, 'error')
      return false
    } finally {
      setOcupado(false)
    }
  }, [aviso])
  return [ejecutar, ocupado]
}

/* ---------- piezas ---------- */

export function Pagina({ titulo, sub, acciones, children }) {
  return (
    <section className="pagina">
      <header className="pagina-cabecera">
        <div>
          <h1>{titulo}</h1>
          {sub && <p className="sub">{sub}</p>}
        </div>
        {acciones && <div className="acciones">{acciones}</div>}
      </header>
      {children}
    </section>
  )
}

export function Boton({ variante = 'principal', children, ...resto }) {
  return <button type="button" className={`boton boton-${variante}`} {...resto}>{children}</button>
}

export function Bloque({ titulo, acciones, children }) {
  return (
    <div className="bloque">
      {(titulo || acciones) && (
        <div className="bloque-cabecera">
          <h2>{titulo}</h2>
          <div className="acciones">{acciones}</div>
        </div>
      )}
      {children}
    </div>
  )
}

export function Cargando({ datos, children, vacio = 'Todavía no hay nada aquí.' }) {
  if (datos.cargando) return <p className="vacio">Cargando…</p>
  if (datos.error && !datos.datos) {
    return (
      <p className="vacio error">
        {datos.error.message} <Boton variante="enlace" onClick={datos.recargar}>Reintentar</Boton>
      </p>
    )
  }
  // Solo las respuestas paginadas traen la lista en `contenido` (el QR también tiene un campo con ese nombre).
  const lista = Array.isArray(datos.datos?.contenido) ? datos.datos.contenido : datos.datos
  if (Array.isArray(lista) && lista.length === 0) return <p className="vacio">{vacio}</p>
  if (datos.datos == null) return null
  return children(lista)
}

/** Tabla que en pantallas angostas se apila: cada celda lleva el título de su columna. */
export function Tabla({ columnas, filas, clave = (f) => f.id }) {
  return (
    <div className="tabla-marco">
      <table>
        <thead><tr>{columnas.map(([t]) => <th key={t}>{t}</th>)}</tr></thead>
        <tbody>
          {filas.map((f) => (
            <tr key={clave(f)}>
              {columnas.map(([t, celda]) => <td key={t} data-titulo={t}>{celda(f)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Cifras({ items }) {
  return (
    <dl className="cifras">
      {items.map(([titulo, valor, nota]) => (
        <div key={titulo}>
          <dt>{titulo}</dt>
          <dd>{valor ?? '—'}</dd>
          {nota && <small>{nota}</small>}
        </div>
      ))}
    </dl>
  )
}

const TONOS = {
  APROBADO: 'bien', APROBADA: 'bien', RESERVADA: 'bien', ACTIVO: 'bien', ABIERTA: 'bien',
  RECHAZADO: 'mal', RECHAZADA: 'mal', ANULADO: 'mal', VENCIDO: 'mal', INACTIVO: 'mal',
  PENDIENTE: 'espera', PENDIENTE_APODERADO: 'espera', PENDIENTE_PRECEPTOR: 'espera',
}
const NOMBRES = {
  PENDIENTE_APODERADO: 'Espera al apoderado', PENDIENTE_PRECEPTOR: 'En revisión',
  CANCELADA_RESIDENTE: 'Cancelada', CANCELADA_PASTOR: 'Anulada por el pastor', CANCELADA_CIERRE: 'Cerrada sin revisar',
  CENA_TAPER: 'Cena en táper', CENA_SEGUNDO_TURNO: 'Cena segundo turno', DESAYUNO_FRIO: 'Desayuno frío',
  PERNOCTE: 'Pernoctar fuera', PRECEPTOR: 'Pastor o monitor',
}
export const nombre = (v) => NOMBRES[v] ?? (v ? v.charAt(0) + v.slice(1).toLowerCase().replaceAll('_', ' ') : '—')
export const Estado = ({ valor }) => <span className={`estado estado-${TONOS[valor] ?? 'neutro'}`}>{nombre(valor)}</span>

export function Pestanas({ opciones, valor, onCambio }) {
  return (
    <div className="pestanas" role="tablist">
      {opciones.map(([v, texto]) => (
        <button type="button" role="tab" aria-selected={v === valor} key={v} onClick={() => onCambio(v)}>{texto}</button>
      ))}
    </div>
  )
}

export function Dialogo({ titulo, onCerrar, children }) {
  const ref = useRef(null)
  useEffect(() => { ref.current?.showModal() }, [])
  return (
    <dialog ref={ref} onClose={onCerrar}>
      <div className="bloque-cabecera">
        <h2>{titulo}</h2>
        <Boton variante="enlace" onClick={onCerrar}>Cerrar</Boton>
      </div>
      {children}
    </dialog>
  )
}

/**
 * Formulario a partir de una lista de campos: [{ n, t, tipo, opciones, requerido, ayuda, si }].
 * Muestra los errores por campo que devuelve el servidor.
 */
export function Formulario({ campos, inicial = {}, enviar, boton = 'Guardar', exito, alTerminar }) {
  const [valores, setValores] = useState(() =>
    Object.fromEntries(campos.map((c) => [c.n, inicial[c.n] ?? (c.tipo === 'check' ? false : '')])))
  const [errores, setErrores] = useState({})
  const [general, setGeneral] = useState(null)
  const [ocupado, setOcupado] = useState(false)
  const aviso = useAviso()

  const alEnviar = async (ev) => {
    ev.preventDefault()
    setOcupado(true); setErrores({}); setGeneral(null)
    try {
      const r = await enviar(valores)
      if (exito) aviso(exito)
      await alTerminar?.(r)
    } catch (e) {
      setErrores(e.campos || {})
      setGeneral(e.message)
    } finally {
      setOcupado(false)
    }
  }

  const poner = (n, v) => setValores((a) => ({ ...a, [n]: v }))

  return (
    <form onSubmit={alEnviar} className="formulario">
      {campos.filter((c) => !c.si || c.si(valores)).map((c) => {
        const comun = { id: c.n, required: c.requerido, 'aria-invalid': !!errores[c.n] }
        let control
        if (c.opciones) {
          control = (
            <select {...comun} value={valores[c.n]} onChange={(e) => poner(c.n, e.target.value)}>
              <option value="">Elige…</option>
              {c.opciones.map(([v, t]) => <option key={v} value={v}>{t}</option>)}
            </select>
          )
        } else if (c.tipo === 'area') {
          control = <textarea {...comun} rows={3} value={valores[c.n]} onChange={(e) => poner(c.n, e.target.value)} />
        } else if (c.tipo === 'check') {
          control = <input id={c.n} type="checkbox" checked={valores[c.n]} onChange={(e) => poner(c.n, e.target.checked)} />
        } else if (c.tipo === 'file') {
          control = <input {...comun} type="file" accept="image/*" onChange={(e) => poner(c.n, e.target.files[0])} />
        } else {
          control = <input {...comun} type={c.tipo || 'text'} value={valores[c.n]} onChange={(e) => poner(c.n, e.target.value)} />
        }
        return (
          <label key={c.n} className={c.tipo === 'check' ? 'campo campo-check' : 'campo'}>
            <span>{c.t}</span>
            {control}
            {errores[c.n] ? <small className="error">{errores[c.n]}</small> : c.ayuda && <small>{c.ayuda}</small>}
          </label>
        )
      })}
      {general && <p className="error" role="alert">{general}</p>}
      <button type="submit" className="boton boton-principal" disabled={ocupado}>{ocupado ? 'Enviando…' : boton}</button>
    </form>
  )
}

/** Botón que abre un diálogo; `children` recibe la función para cerrarlo. */
export function ConDialogo({ texto, titulo, variante = 'secundario', children }) {
  const [abierto, setAbierto] = useState(false)
  return (
    <>
      <Boton variante={variante} onClick={() => setAbierto(true)}>{texto}</Boton>
      {abierto && <Dialogo titulo={titulo ?? texto} onCerrar={() => setAbierto(false)}>{children(() => setAbierto(false))}</Dialogo>}
    </>
  )
}

/** Acción que exige escribir el motivo (anular, eliminar, justificar). */
export function ConMotivo({ texto, titulo, enviar, alTerminar, variante = 'peligro', etiqueta = 'Motivo' }) {
  return (
    <ConDialogo texto={texto} titulo={titulo} variante={variante}>
      {(cerrar) => (
        <Formulario
          campos={[{ n: 'motivo', t: etiqueta, tipo: 'area', requerido: true }]}
          boton={texto}
          enviar={({ motivo }) => enviar(motivo)}
          exito="Listo"
          alTerminar={() => { cerrar(); alTerminar?.() }}
        />
      )}
    </ConDialogo>
  )
}

export function Foto({ id }) {
  const [url, setUrl] = useState(null)
  useEffect(() => {
    let propia
    api.urlDeEvidencia(id).then((u) => { propia = u; setUrl(u) }).catch(() => setUrl(''))
    return () => propia && URL.revokeObjectURL(propia)
  }, [id])
  if (url === '') return <p className="vacio">No se pudo cargar la imagen.</p>
  return url ? <img className="evidencia" src={url} alt="Evidencia adjunta" /> : <p className="vacio">Cargando imagen…</p>
}

export const VerFoto = ({ id }) =>
  id ? <ConDialogo texto="Ver foto" titulo="Evidencia" variante="enlace">{() => <Foto id={id} />}</ConDialogo> : null

/** Lector de QR con la cámara cuando el navegador lo permite (Chrome en Android y macOS). */
export function LectorQr({ onCodigo }) {
  const video = useRef(null)
  const [activo, setActivo] = useState(false)
  const [fallo, setFallo] = useState(null)

  useEffect(() => {
    if (!activo) return undefined
    let flujo, reloj, cerrado = false
    const detector = new window.BarcodeDetector({ formats: ['qr_code'] })
    navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
      .then(async (f) => {
        if (cerrado) return f.getTracks().forEach((t) => t.stop())
        flujo = f
        video.current.srcObject = f
        await video.current.play()
        reloj = setInterval(async () => {
          const [codigo] = await detector.detect(video.current).catch(() => [])
          if (codigo) { setActivo(false); onCodigo(codigo.rawValue) }
        }, 400)
        return undefined
      })
      .catch((e) => {
        setActivo(false)
        setFallo(e.name === 'NotAllowedError'
          ? 'No diste permiso a la cámara. Actívalo en los ajustes del sitio del navegador o escribe el código.'
          : 'No se pudo abrir la cámara. Escribe el código.')
      })
    return () => { cerrado = true; clearInterval(reloj); flujo?.getTracks().forEach((t) => t.stop()) }
  }, [activo, onCodigo])

  if (!('BarcodeDetector' in window)) {
    return <p className="nota">Este navegador no lee QR con la cámara: escribe el código del residente.</p>
  }
  return (
    <div>
      <Boton variante="secundario" onClick={() => { setFallo(null); setActivo(!activo) }}>
        {activo ? 'Apagar cámara' : 'Escanear con la cámara'}
      </Boton>
      {fallo && <p className="error">{fallo}</p>}
      {activo && <video ref={video} className="camara" muted playsInline />}
    </div>
  )
}
