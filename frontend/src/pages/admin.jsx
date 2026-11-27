import { useState } from 'react'
import { BedDouble, Check, KeyRound, MailCheck, Pencil, Plus, Power, Users, X } from 'lucide-react'
import { api, pagina } from '../api'
import { Bloque, Boton, Cargando, Cifras, ConDialogo, Estado, Formulario, Franja, Pagina, Tabla, nombre, useAccion, useDatos } from '../ui'
import { fecha, fechaHora } from '../formatos'

const filtrar = (lista, texto, campos) => {
  const t = texto.toLowerCase()
  return lista.filter((x) => campos.map((c) => x[c] ?? '').join(' ').toLowerCase().includes(t))
}

/* ---------- residentes ---------- */

const CAMPOS_PERSONA = [
  { n: 'nombreCompleto', t: 'Nombre completo', requerido: true },
  { n: 'codigo', t: 'Código', requerido: true },
  { n: 'dni', t: 'DNI' },
  { n: 'fechaNacimiento', t: 'Fecha de nacimiento', tipo: 'date' },
  { n: 'telefono', t: 'Celular', ayuda: '9 dígitos, empieza en 9' },
]
const sinVacios = (v) => Object.fromEntries(Object.entries(v).map(([k, x]) => [k, x === '' ? null : x]))

function Apoderados({ id }) {
  const lista = useDatos(`/residentes/${id}/apoderados`)
  return (
    <>
      <Cargando datos={lista} vacio="Sin apoderados registrados.">
        {(l) => <Tabla filas={l} columnas={[['Nombre', (a) => a.nombre], ['Parentesco', (a) => a.parentesco], ['Usuario', (a) => a.usuario || '—'], ['Celular', (a) => a.telefono || '—']]} />}
      </Cargando>
      <h3>Agregar apoderado</h3>
      <Formulario
        campos={[
          { n: 'nombre', t: 'Nombre', requerido: true }, { n: 'parentesco', t: 'Parentesco', requerido: true },
          { n: 'telefono', t: 'Celular' }, { n: 'email', t: 'Correo', tipo: 'email' },
          { n: 'username', t: 'Usuario' }, { n: 'password', t: 'Clave inicial', tipo: 'password' },
          { n: 'principal', t: 'Es el apoderado principal', tipo: 'check' },
        ]}
        enviar={(v) => api.post(`/residentes/${id}/apoderados`, sinVacios(v))}
        exito="Apoderado agregado"
        alTerminar={lista.recargar}
      />
    </>
  )
}

export function Residentes() {
  const lista = useDatos(pagina('/residentes'))
  const habitaciones = useDatos('/habitaciones')
  const [texto, setTexto] = useState('')
  const [ejecutar] = useAccion()
  const recargar = () => { lista.recargar(); habitaciones.recargar() }
  const libres = habitaciones.datos?.filter((h) => h.activa && h.disponibles > 0) ?? []

  return (
    <Pagina titulo="Residentes" sub={lista.datos && `${lista.datos.totalElementos} registrados`} acciones={(
      <ConDialogo texto="Nuevo residente" variante="principal" icono={Plus}>
        {(cerrar) => (
          <Formulario
            campos={[
              ...CAMPOS_PERSONA,
              { n: 'email', t: 'Correo', tipo: 'email' }, { n: 'carrera', t: 'Carrera' }, { n: 'ciclo', t: 'Ciclo', tipo: 'number' },
              { n: 'username', t: 'Usuario', requerido: true }, { n: 'password', t: 'Clave inicial', tipo: 'password', requerido: true },
            ]}
            enviar={(v) => api.post('/residentes', sinVacios(v))}
            exito="Residente creado"
            alTerminar={() => { cerrar(); recargar() }}
          />
        )}
      </ConDialogo>
    )}>
      {lista.datos?.contenido.some((r) => r.activo && !r.habitacion) && (
        <Franja tono="espera">Residentes activos sin habitación: {lista.datos.contenido.filter((r) => r.activo && !r.habitacion).length}</Franja>
      )}
      <input type="search" aria-label="Buscar residente" placeholder="Buscar por nombre o código" value={texto} onChange={(e) => setTexto(e.target.value)} />
      <Cargando datos={lista} vacio="Aún no hay residentes. Crea el primero con «Nuevo residente».">
        {(l) => <Tabla filas={filtrar(l, texto, ['nombreCompleto', 'codigo'])} columnas={[
          ['Residente', (r) => r.nombreCompleto], ['Código', (r) => r.codigo],
          ['Habitación', (r) => (r.habitacion ? `${r.habitacion.numero} ${r.habitacion.cama ?? ''}` : 'Sin asignar')],
          ['Estado', (r) => <Estado valor={r.activo ? 'ACTIVO' : 'INACTIVO'} />],
          ['', (r) => (
            <div className="acciones">
              <ConDialogo texto="Editar" titulo={r.nombreCompleto} icono={Pencil}>
                {(cerrar) => (
                  <Formulario
                    campos={[...CAMPOS_PERSONA, { n: 'puedeSolicitarSolo', t: 'Puede pedir permisos sin apoderado', tipo: 'check' }, { n: 'activo', t: 'Activo', tipo: 'check' }]}
                    inicial={r}
                    enviar={(v) => api.patch(`/residentes/${r.id}`, sinVacios(v))}
                    exito="Cambios guardados"
                    alTerminar={() => { cerrar(); recargar() }}
                  />
                )}
              </ConDialogo>
              <ConDialogo texto="Habitación" titulo={r.nombreCompleto} icono={BedDouble}>
                {(cerrar) => (
                  <>
                    <Formulario
                      campos={[
                        { n: 'habitacionId', t: 'Habitación', requerido: true, opciones: libres.map((h) => [h.id, `${h.numero} (${h.disponibles} libres)`]) },
                        { n: 'cama', t: 'Cama' },
                      ]}
                      enviar={(v) => api.post(`/residentes/${r.id}/asignacion`, { habitacionId: Number(v.habitacionId), cama: v.cama || null })}
                      boton="Asignar"
                      exito="Habitación asignada"
                      alTerminar={() => { cerrar(); recargar() }}
                    />
                    {r.habitacion && <Boton variante="peligro" icono={X} onClick={() => ejecutar(() => api.post(`/residentes/${r.id}/liberar-habitacion`), 'Habitación liberada', () => { cerrar(); recargar() })}>Liberar habitación actual</Boton>}
                  </>
                )}
              </ConDialogo>
              <ConDialogo texto="Apoderados" titulo={r.nombreCompleto} variante="enlace" icono={Users}>{() => <Apoderados id={r.id} />}</ConDialogo>
              {r.email && !r.emailVerificado && (
                <Boton variante="enlace" icono={MailCheck} onClick={() => ejecutar(() => api.post(`/usuarios/${r.usuarioId}/verificar-correo`), 'Correo verificado', lista.recargar)}>Verificar correo</Boton>
              )}
            </div>
          )],
        ]} />}
      </Cargando>
    </Pagina>
  )
}

/* ---------- habitaciones ---------- */

export function Habitaciones() {
  const lista = useDatos('/habitaciones')
  const ocupacion = useDatos('/habitaciones/ocupacion')
  const recargar = () => { lista.recargar(); ocupacion.recargar() }
  const campos = [{ n: 'numero', t: 'Número', requerido: true }, { n: 'capacidad', t: 'Capacidad', tipo: 'number', requerido: true }, { n: 'activa', t: 'Activa', tipo: 'check' }]
  return (
    <Pagina titulo="Habitaciones" acciones={(
      <ConDialogo texto="Nueva habitación" variante="principal" icono={Plus}>
        {(cerrar) => <Formulario campos={campos} inicial={{ activa: true }} enviar={(v) => api.post('/habitaciones', { ...v, capacidad: Number(v.capacidad) })} exito="Habitación creada" alTerminar={() => { cerrar(); recargar() }} />}
      </ConDialogo>
    )}>
      <Cargando datos={ocupacion}>
        {(o) => <Cifras items={[['Habitaciones', o.habitaciones], ['Camas', o.capacidadTotal], ['Ocupadas', o.ocupados], ['Libres', o.disponibles]]} />}
      </Cargando>
      <Cargando datos={lista}>
        {(l) => <Tabla filas={l} columnas={[
          ['Número', (h) => h.numero], ['Capacidad', (h) => h.capacidad], ['Ocupantes', (h) => h.ocupantes], ['Libres', (h) => h.disponibles],
          ['Estado', (h) => <Estado valor={h.activa ? 'ACTIVO' : 'INACTIVO'} />],
          ['', (h) => (
            <ConDialogo texto="Editar" titulo={`Habitación ${h.numero}`} icono={Pencil}>
              {(cerrar) => <Formulario campos={campos} inicial={h} enviar={(v) => api.put(`/habitaciones/${h.id}`, { ...v, capacidad: Number(v.capacidad) })} exito="Cambios guardados" alTerminar={() => { cerrar(); recargar() }} />}
            </ConDialogo>
          )],
        ]} />}
      </Cargando>
    </Pagina>
  )
}

/* ---------- cuentas del personal ---------- */

export function Usuarios() {
  const lista = useDatos(pagina('/usuarios'))
  const [texto, setTexto] = useState('')
  const [ejecutar] = useAccion()
  return (
    <Pagina titulo="Usuarios" acciones={(
      <ConDialogo texto="Nuevo usuario" variante="principal" icono={Plus}>
        {(cerrar) => (
          <Formulario
            campos={[
              { n: 'nombreCompleto', t: 'Nombre completo', requerido: true },
              { n: 'rol', t: 'Rol', requerido: true, opciones: [['PRECEPTOR', 'Pastor o monitor'], ['GARITA', 'Garita'], ['ADMINISTRADOR', 'Administrador']] },
              { n: 'username', t: 'Usuario', requerido: true }, { n: 'password', t: 'Clave inicial', tipo: 'password', requerido: true },
              { n: 'dni', t: 'DNI' },
            ]}
            enviar={(v) => api.post('/usuarios', sinVacios(v))}
            exito="Usuario creado"
            alTerminar={() => { cerrar(); lista.recargar() }}
          />
        )}
      </ConDialogo>
    )}>
      <input type="search" aria-label="Buscar usuario" placeholder="Buscar por nombre o usuario" value={texto} onChange={(e) => setTexto(e.target.value)} />
      <Cargando datos={lista}>
        {(l) => <Tabla filas={filtrar(l, texto, ['nombreCompleto', 'username'])} columnas={[
          ['Nombre', (u) => u.nombreCompleto], ['Usuario', (u) => u.username], ['Rol', (u) => nombre(u.rol)],
          ['Estado', (u) => <Estado valor={u.activo ? 'ACTIVO' : 'INACTIVO'} />],
          ['', (u) => (
            <div className="acciones">
              <Boton variante="enlace" icono={Power} onClick={() => window.confirm(`¿${u.activo ? 'Desactivar' : 'Activar'} a ${u.username}?`) && ejecutar(() => api.patch(`/usuarios/${u.id}`, { activo: !u.activo }), 'Listo', lista.recargar)}>
                {u.activo ? 'Desactivar' : 'Activar'}
              </Boton>
              <ConDialogo texto="Nueva clave" titulo={u.nombreCompleto} variante="enlace" icono={KeyRound}>
                {(cerrar) => <Formulario campos={[{ n: 'password', t: 'Clave nueva', tipo: 'password', requerido: true }]} enviar={(v) => api.patch(`/usuarios/${u.id}`, v)} exito="Clave actualizada" alTerminar={cerrar} />}
              </ConDialogo>
              <Boton variante="enlace" icono={MailCheck} onClick={() => ejecutar(() => api.post(`/usuarios/${u.id}/verificar-correo`), 'Correo verificado')}>Verificar correo</Boton>
            </div>
          )],
        ]} />}
      </Cargando>
    </Pagina>
  )
}

/* ---------- semestre, reglas y auditoría ---------- */

export function Ajustes() {
  const semestres = useDatos('/semestres')
  const config = useDatos('/configuracion')
  const [ejecutar] = useAccion()
  return (
    <Pagina titulo="Semestre y reglas">
      <Bloque titulo="Semestres" acciones={(
        <>
          <ConDialogo texto="Nuevo semestre" icono={Plus}>
            {(cerrar) => (
              <Formulario
                campos={[{ n: 'nombre', t: 'Nombre', requerido: true }, { n: 'inicio', t: 'Inicio', tipo: 'date', requerido: true }, { n: 'fin', t: 'Fin', tipo: 'date', requerido: true }]}
                enviar={(v) => api.post('/semestres', v)}
                exito="Semestre creado"
                alTerminar={() => { cerrar(); semestres.recargar() }}
              />
            )}
          </ConDialogo>
          <Boton variante="peligro" icono={Power} onClick={() => window.confirm('Terminar el semestre afecta a todos los residentes y no se puede deshacer. ¿Continuar?') && ejecutar(() => api.post('/semestres/terminar'), 'Semestre terminado', semestres.recargar)}>Terminar semestre actual</Boton>
        </>
      )}>
        <Cargando datos={semestres} vacio="No hay semestres. Crea uno con «Nuevo semestre».">
          {(l) => <Tabla filas={l} columnas={[['Nombre', (s) => s.nombre], ['Inicio', (s) => fecha(s.inicio)], ['Fin', (s) => fecha(s.fin)], ['Estado', (s) => <Estado valor={s.activo ? 'ACTIVO' : 'INACTIVO'} />]]} />}
        </Cargando>
      </Bloque>
      <Bloque titulo="Reglas">
        <Cargando datos={config}>
          {(l) => <Tabla filas={l} clave={(c) => c.clave} columnas={[
            ['Regla', (c) => c.descripcion || c.clave], ['Valor', (c) => c.valor],
            ['', (c) => (
              <ConDialogo texto="Cambiar" titulo={c.descripcion || c.clave} variante="enlace" icono={Pencil}>
                {(cerrar) => <Formulario campos={[{ n: 'valor', t: 'Valor', requerido: true }]} inicial={c} enviar={(v) => api.put(`/configuracion/${c.clave}`, v)} exito="Regla actualizada" alTerminar={() => { cerrar(); config.recargar() }} />}
              </ConDialogo>
            )],
          ]} />}
        </Cargando>
      </Bloque>
    </Pagina>
  )
}

export function Auditoria() {
  const lista = useDatos(pagina('/auditoria'))
  return (
    <Pagina titulo="Auditoría">
      <Cargando datos={lista} vacio="Todavía no hay acciones registradas.">
        {(l) => <Tabla filas={l} columnas={[
          ['Fecha', (a) => fechaHora(a.creadoEn)], ['Acción', (a) => nombre(a.accion)], ['Sobre', (a) => `${a.entidad} ${a.entidadId ?? ''}`], ['Detalle', (a) => a.detalle || '—'],
        ]} />}
      </Cargando>
    </Pagina>
  )
}
