import { useState } from 'react'
import { Check, CheckCheck, KeyRound, LogIn, Mail, Pencil } from 'lucide-react'
import { api, pagina } from '../api'
import { Boton, Bloque, Cargando, Formulario, Pagina, useAccion, useDatos, ConDialogo, nombre } from '../ui'
import { fecha, fechaHora } from '../formatos'
import logo from '../assets/logo.png'
import residencia from '../assets/residencia.png'

export function Login({ onEntrar, mensaje }) {
  const [olvide, setOlvide] = useState(false)
  return (
    <main className="portada">
      <div className="portada-panel">
        <img src={logo} alt="" className="portada-logo" />
        <h1>ResiUP</h1>
        <p>Residencia Universitaria Fernando Stahl · Universidad Peruana Unión</p>
        {mensaje && <p className="nota">{mensaje}</p>}
        {olvide ? (
          <Formulario
            key="olvide"
            campos={[{ n: 'username', t: 'Usuario o código', requerido: true }]}
            boton="Pedir una clave provisional"
            icono={Mail}
            enviar={(v) => api.post('/auth/olvide-clave', v)}
            exito="Si el usuario tiene un correo verificado, recibirá una clave provisional."
            alTerminar={() => setOlvide(false)}
          />
        ) : (
          <Formulario
            key="login"
            campos={[
              { n: 'username', t: 'Usuario o código', requerido: true },
              { n: 'password', t: 'Contraseña', tipo: 'password', requerido: true },
            ]}
            boton="Ingresar"
            icono={LogIn}
            enviar={(v) => api.post('/auth/login', { username: v.username.trim(), password: v.password })}
            alTerminar={onEntrar}
          />
        )}
        <Boton variante="enlace" onClick={() => setOlvide(!olvide)}>
          {olvide ? 'Volver al inicio de sesión' : 'Olvidé mi clave'}
        </Boton>
      </div>
      <img src={residencia} alt="Ilustración de la residencia" className="portada-foto" />
    </main>
  )
}

export function CambiarClave({ alTerminar }) {
  return (
    <Formulario
      campos={[
        { n: 'claveActual', t: 'Clave actual', tipo: 'password', requerido: true },
        { n: 'claveNueva', t: 'Clave nueva', tipo: 'password', requerido: true, ayuda: 'Al menos 8 caracteres' },
      ]}
      boton="Cambiar clave"
      icono={KeyRound}
      enviar={(v) => api.post('/auth/cambiar-clave', v)}
      exito="Clave cambiada"
      alTerminar={alTerminar}
    />
  )
}

export function Perfil() {
  const perfil = useDatos('/perfil')
  const [ejecutar, ocupado] = useAccion()
  return (
    <Pagina titulo="Mi cuenta">
      <Cargando datos={perfil}>
        {(p) => (
          <div className="columnas">
            <Bloque titulo={p.nombreCompleto}>
              <dl className="ficha">
                <dt>Usuario</dt><dd>{p.username}</dd>
                <dt>Rol</dt><dd>{nombre(p.rol)}</dd>
                <dt>DNI</dt><dd>{p.dni || '—'}</dd>
                <dt>Nacimiento</dt><dd>{fecha(p.fechaNacimiento)}</dd>
                <dt>Correo</dt><dd>{p.email || '—'} {p.email && (p.emailVerificado ? '(verificado)' : '(sin verificar)')}</dd>
                <dt>Celular</dt><dd>{p.telefono || '—'}</dd>
              </dl>
              <div className="acciones">
                <ConDialogo texto="Cambiar datos" icono={Pencil}>
                  {(cerrar) => (
                    <Formulario
                      campos={[
                        { n: 'email', t: 'Correo', tipo: 'email' },
                        { n: 'telefono', t: 'Celular', ayuda: '9 dígitos, empieza en 9' },
                      ]}
                      inicial={p}
                      enviar={(v) => api.patch('/perfil', v)}
                      exito="Datos guardados"
                      alTerminar={() => { cerrar(); perfil.recargar() }}
                    />
                  )}
                </ConDialogo>
                {p.email && !p.emailVerificado && (
                  <>
                    <Boton variante="secundario" icono={Mail} disabled={ocupado} onClick={() => ejecutar(() => api.post('/perfil/correo/codigo'), 'Te enviamos un código. Revisa también el spam.')}>
                      Enviar código al correo
                    </Boton>
                    <ConDialogo texto="Escribir código" icono={KeyRound}>
                      {(cerrar) => (
                        <Formulario
                          campos={[{ n: 'codigo', t: 'Código recibido', requerido: true }]}
                          boton="Verificar"
                          icono={Check}
                          enviar={(v) => api.post('/perfil/correo/verificar', v)}
                          exito="Correo verificado"
                          alTerminar={() => { cerrar(); perfil.recargar() }}
                        />
                      )}
                    </ConDialogo>
                  </>
                )}
              </div>
            </Bloque>
            <Bloque titulo="Cambiar clave"><CambiarClave /></Bloque>
          </div>
        )}
      </Cargando>
    </Pagina>
  )
}

export function Avisos({ alLeer }) {
  const avisos = useDatos(pagina('/notificaciones', 50), { cada: 15000 })
  const [ejecutar] = useAccion()
  const despues = () => { avisos.recargar(); alLeer?.() }
  return (
    <Pagina titulo="Avisos" acciones={<Boton variante="secundario" icono={CheckCheck} onClick={() => ejecutar(() => api.post('/notificaciones/leidas'), null, despues)}>Marcar todo como leído</Boton>}>
      <Cargando datos={avisos} vacio="No tienes avisos. Aquí llegarán las novedades de tus permisos y reservas.">
        {(lista) => (
          <ul className="lista-avisos">
            {lista.map((a) => (
              <li key={a.id} className={a.leida ? '' : 'sin-leer'}>
                <div>
                  <strong>{a.titulo}</strong>
                  <p>{a.mensaje}</p>
                  <small>{fechaHora(a.creadaEn)}</small>
                </div>
                {!a.leida && <Boton variante="enlace" icono={Check} onClick={() => ejecutar(() => api.post(`/notificaciones/${a.id}/leida`), null, despues)}>Leído</Boton>}
              </li>
            ))}
          </ul>
        )}
      </Cargando>
    </Pagina>
  )
}
