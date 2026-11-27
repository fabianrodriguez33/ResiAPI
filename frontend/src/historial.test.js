import assert from 'node:assert/strict'
import { test } from 'node:test'
import { masNuevoPrimero, permisosPrincipales, reservasPrincipales } from './historial.js'

const AHORA = new Date('2026-11-27T12:00:00Z')
const hace = (horas) => new Date(AHORA - horas * 3600000).toISOString()
const ids = (lista) => lista.map((x) => x.id)
const permiso = (id, horas, extra) => ({ id, creadoEn: hace(horas), retornoAutorizado: hace(horas - 2), estado: 'RECHAZADO', ...extra })

test('un permiso de 71 horas se ve; uno de 73 solo en el historial', () => {
  const lista = [permiso(73, 73), permiso(71, 71)]
  assert.deepEqual(ids(permisosPrincipales(lista, AHORA)), [71])
  assert.deepEqual(ids(masNuevoPrimero(lista, 'creadoEn')), [71, 73])
})

test('un permiso vigente antiguo sigue en la lista y uno terminado no', () => {
  const futuro = hace(-24)
  const lista = [
    permiso(1, 200, { estado: 'PENDIENTE_PRECEPTOR' }),
    permiso(2, 201, { estado: 'PENDIENTE_APODERADO' }),
    permiso(3, 202, { estado: 'APROBADO', retornoAutorizado: futuro }),
    permiso(4, 203, { estado: 'APROBADO', garitaSalida: hace(190) }), // sigue fuera
    permiso(5, 204, { estado: 'APROBADO' }), // venció sin usarse
    permiso(6, 205, { estado: 'APROBADO', retornoAutorizado: futuro, garitaSalida: hace(5), garitaEntrada: hace(4) }),
    permiso(7, 206, { estado: 'CANCELADO', retornoAutorizado: futuro }),
  ]
  assert.deepEqual(ids(permisosPrincipales(lista, AHORA)), [1, 2, 3, 4])
})

test('reservas: 71 horas sí, 73 no, y una antigua de hoy en adelante sigue', () => {
  const reserva = (id, horas, fecha) => ({ id, creadaEn: hace(horas), fecha })
  const lista = [reserva(73, 73, '2026-11-24'), reserva(71, 71, '2026-11-24'), reserva(1, 300, '2026-11-27'), reserva(2, 301, '2026-11-28')]
  assert.deepEqual(ids(reservasPrincipales(lista, '2026-11-27', AHORA)), [71, 1, 2])
})
