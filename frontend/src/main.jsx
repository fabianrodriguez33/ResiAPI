import { Component, StrictMode, useEffect, useState, useSyncExternalStore } from 'react'
import { createRoot } from 'react-dom/client'
import { guardarSesion, leerSesion } from './api'
import { Boton, ProveedorAvisos, nombre, useDatos } from './ui'
import { Avisos, CambiarClave, Login, Perfil } from './pages/comun'
import * as residente from './pages/residente'
import * as apoderado from './pages/apoderado'
import * as pastor from './pages/pastor'
import * as garita from './pages/garita'
import * as admin from './pages/admin'
import logo from './assets/logo.png'
import './styles.css'

// Menú de cada rol: [ruta, título, componente]. El servidor vuelve a validar el rol en cada petición.
const MENUS = {
  RESIDENTE: [
    ['inicio', 'Inicio', residente.Inicio], ['permisos', 'Permisos', residente.Permisos], ['asistencias', 'Asistencias', residente.Asistencias],
    ['limpieza', 'Limpieza', residente.Limpieza], ['reservas', 'Reservas', residente.Reservas],
  ],
  APODERADO: [['permisos', 'Permisos', apoderado.Permisos], ['hijos', 'Mis hijos', apoderado.Hijos]],
  PRECEPTOR: [
    ['permisos', 'Permisos', pastor.Permisos], ['cultos', 'Cultos', pastor.Cultos], ['limpieza', 'Limpieza', pastor.Limpieza],
    ['reservas', 'Reservas', pastor.Reservas], ['faltas', 'Faltas', pastor.Faltas], ['reporte', 'Reporte', pastor.Reporte],
  ],
  GARITA: [['verificar', 'Verificar', garita.Verificar], ['aprobados', 'Aprobados', garita.Aprobados], ['historial', 'Historial', garita.Historial]],
  ADMINISTRADOR: [
    ['residentes', 'Residentes', admin.Residentes], ['habitaciones', 'Habitaciones', admin.Habitaciones], ['usuarios', 'Usuarios', admin.Usuarios],
    ['ajustes', 'Semestre y reglas', admin.Ajustes], ['auditoria', 'Auditoría', admin.Auditoria],
  ],
}

const alCambiarRuta = (f) => { window.addEventListener('hashchange', f); return () => window.removeEventListener('hashchange', f) }
const useRuta = () => useSyncExternalStore(alCambiarRuta, () => window.location.hash.slice(2))

/** Si una pantalla falla, el menú sigue en pie en vez de quedar todo en blanco. */
class Resguardo extends Component {
  state = { fallo: false }

  static getDerivedStateFromError() { return { fallo: true } }

  render() {
    return this.state.fallo
      ? <p className="error">Esta pantalla no se pudo mostrar. Recarga la página.</p>
      : this.props.children
  }
}

function Panel({ sesion, salir }) {
  const ruta = useRuta()
  const noLeidas = useDatos('/notificaciones/no-leidas', { cada: 20000 })
  const rol = sesion.roles[0]
  const menu = [...(MENUS[rol] ?? []), ['avisos', 'Avisos', Avisos], ['cuenta', 'Mi cuenta', Perfil]]
  const actual = menu.find(([r]) => r === ruta) ?? menu[0]
  const Vista = actual[2]
  const cantidad = noLeidas.datos?.cantidad

  return (
    <div className="panel">
      <nav className="lateral" aria-label="Principal">
        <div className="marca"><img src={logo} alt="" /><span>ResiUP</span></div>
        <ul>
          {menu.map(([r, titulo]) => (
            <li key={r}>
              <a href={`#/${r}`} aria-current={r === actual[0] ? 'page' : undefined}>
                {titulo}
                {r === 'avisos' && cantidad > 0 && <span className="contador">{cantidad}</span>}
              </a>
            </li>
          ))}
        </ul>
        <div className="quien">
          <strong>{sesion.nombreCompleto}</strong>
          <span>{nombre(rol)}</span>
          <Boton variante="enlace" onClick={salir}>Cerrar sesión</Boton>
        </div>
      </nav>
      <main className="contenido">
        <Resguardo key={actual[0]}><Vista alLeer={noLeidas.recargar} /></Resguardo>
      </main>
    </div>
  )
}

function App() {
  const [sesion, setSesion] = useState(leerSesion)
  const [mensaje, setMensaje] = useState(null)

  const poner = (s) => { guardarSesion(s); setSesion(s) }
  const salir = () => { window.location.hash = ''; setMensaje(null); poner(null) }

  useEffect(() => {
    const expirada = () => { setMensaje('Tu sesión terminó. Ingresa otra vez.'); guardarSesion(null); setSesion(null) }
    window.addEventListener('resiup:expirada', expirada)
    return () => window.removeEventListener('resiup:expirada', expirada)
  }, [])

  if (!sesion) return <Login mensaje={mensaje} onEntrar={(s) => { window.location.hash = ''; setMensaje(null); poner(s) }} />
  if (sesion.debeCambiarClave) {
    return (
      <main className="centro">
        <h1>Elige una clave nueva</h1>
        <p>Entraste con una clave provisional.</p>
        <CambiarClave alTerminar={() => poner({ ...sesion, debeCambiarClave: false })} />
        <Boton variante="enlace" onClick={salir}>Salir</Boton>
      </main>
    )
  }
  return <Panel sesion={sesion} salir={salir} />
}

createRoot(document.getElementById('root')).render(
  <StrictMode><ProveedorAvisos><App /></ProveedorAvisos></StrictMode>,
)
