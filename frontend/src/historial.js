// La lista principal muestra lo generado en las últimas 72 horas y lo que sigue vigente; «Historial», todo.
const RECIENTE_MS = 72 * 60 * 60 * 1000

const esReciente = (creado, ahora) => ahora - new Date(creado) <= RECIENTE_MS

/** Pendiente, o aprobado sin terminar: sin retorno en garita y todavía en plazo (o fuera de la residencia). */
export const permisoVigente = (p, ahora = new Date()) =>
  p.estado === 'PENDIENTE_APODERADO' || p.estado === 'PENDIENTE_PRECEPTOR'
  || (p.estado === 'APROBADO' && !p.garitaEntrada && (new Date(p.retornoAutorizado) > ahora || !!p.garitaSalida))

export const masNuevoPrimero = (lista, campo) => [...lista].sort((a, b) => new Date(b[campo]) - new Date(a[campo]))

export const permisosPrincipales = (lista, ahora = new Date()) =>
  masNuevoPrimero(lista.filter((p) => permisoVigente(p, ahora) || esReciente(p.creadoEn, ahora)), 'creadoEn')

/** `hoy` es la fecha de Lima (aaaa-mm-dd): las reservas de hoy en adelante nunca salen de la lista. */
export const reservasPrincipales = (lista, hoy, ahora = new Date()) =>
  masNuevoPrimero(lista.filter((r) => r.fecha >= hoy || esReciente(r.creadaEn, ahora)), 'creadaEn')
