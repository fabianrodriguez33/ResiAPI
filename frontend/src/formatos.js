const zona = 'America/Lima'
const fh = new Intl.DateTimeFormat('es-PE', { timeZone: zona, day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
const f = new Intl.DateTimeFormat('es-PE', { timeZone: 'UTC', weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' })

export const fechaHora = (v) => (v ? fh.format(new Date(v)) : '—')
export const fecha = (v) => (v ? f.format(new Date(`${v}T00:00:00Z`)) : '—')
/** Hoy en la zona de la residencia, como AAAA-MM-DD. */
export const hoy = () => new Intl.DateTimeFormat('en-CA', { timeZone: zona }).format(new Date())
/** Valor de un campo datetime-local (hora del equipo) a instante ISO. */
export const instante = (v) => (v ? new Date(v).toISOString() : null)
export const decimal = (v) => (v == null ? null : Number(v).toFixed(1))
