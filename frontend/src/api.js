// Cliente de la API. El token vive en sessionStorage: se borra al cerrar la pestaña.
const CLAVE = 'resiup.sesion'

export const leerSesion = () => {
  try { return JSON.parse(sessionStorage.getItem(CLAVE)) } catch { return null }
}
export const guardarSesion = (s) =>
  s ? sessionStorage.setItem(CLAVE, JSON.stringify(s)) : sessionStorage.removeItem(CLAVE)

export class ErrorApi extends Error {
  constructor(status, mensaje, campos) {
    super(mensaje)
    this.status = status
    this.campos = campos || {}
  }
}

async function pedir(ruta, { metodo = 'GET', cuerpo, formulario, acepta } = {}) {
  const sesion = leerSesion()
  const cabeceras = {}
  if (sesion && ruta !== '/auth/login') cabeceras.Authorization = `Bearer ${sesion.token}`
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'
  if (acepta) cabeceras.Accept = acepta
  let r
  try {
    r = await fetch(`/api/v1${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: formulario || (cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined),
    })
  } catch {
    throw new ErrorApi(0, 'No hay conexión con el servidor. Revisa tu red e inténtalo otra vez.')
  }
  if (!r.ok) {
    if (r.status === 401 && ruta !== '/auth/login') window.dispatchEvent(new Event('resiup:expirada'))
    const e = await r.json().catch(() => ({}))
    const campos = e.validationErrors || {}
    const mensaje = Object.values(campos)[0] || e.message || `El servidor respondió ${r.status}`
    throw new ErrorApi(r.status, mensaje, campos)
  }
  return r
}

async function json(ruta, opciones) {
  const r = await pedir(ruta, opciones)
  const texto = await r.text()
  return texto ? JSON.parse(texto) : null
}

export const api = {
  get: (ruta) => json(ruta),
  post: (ruta, cuerpo) => json(ruta, { metodo: 'POST', cuerpo }),
  put: (ruta, cuerpo) => json(ruta, { metodo: 'PUT', cuerpo }),
  patch: (ruta, cuerpo) => json(ruta, { metodo: 'PATCH', cuerpo }),
  borrar: (ruta) => json(ruta, { metodo: 'DELETE' }),
  async subir(archivo) {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return json('/evidencias', { metodo: 'POST', formulario })
  },
  /** Las imágenes piden token, así que se bajan y se muestran como URL local. */
  async urlDeEvidencia(id) {
    const r = await pedir(`/evidencias/${id}`)
    return URL.createObjectURL(await r.blob())
  },
  async descargar(ruta, nombre, tipo = 'text/csv') {
    const r = await pedir(ruta, { acepta: tipo })
    const url = URL.createObjectURL(await r.blob())
    const a = Object.assign(document.createElement('a'), { href: url, download: nombre })
    a.click()
    URL.revokeObjectURL(url)
  },
}

/** Las listas paginadas del servidor: se piden 100, suficiente para una residencia. */
export const pagina = (ruta, tamanio = 100) =>
  `${ruta}${ruta.includes('?') ? '&' : '?'}pagina=0&tamanio=${tamanio}`
