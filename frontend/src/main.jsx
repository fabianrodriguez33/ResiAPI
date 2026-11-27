import { Component, StrictMode, useEffect, useState, useSyncExternalStore } from 'react'
import { createRoot } from 'react-dom/client'
import {
  BedDouble, Bell, BookOpen, CalendarCheck, CircleCheck, CircleUser, ClipboardList, FileSpreadsheet, History, House, LogOut, ScanLine, ScrollText,
  Settings, Sparkles, Ticket, UserCog, UserX, Users, UtensilsCrossed,
} from 'lucide-react'
import { guardarSesion, leerSesion } from './api'
import { Boton, ProveedorAvisos, nombre, useDatos } from './ui'
import { Avisos, CambiarClave, Login, Perfil } from './pages/comun'
import * as residente from './pages/residente'
import * as apoderado from './pages/apoderado'
import * as pastor from './pages/pastor'
import * as garita from './pages/garita'
import * as admin from './pages/admin'
import logo from './assets/logo-letras.png'
import './styles.css'

// Menú de cada rol: [ruta, título, componente, icono]. El servidor vuelve a validar el rol en cada petición.
const MENUS = {
  RESIDENTE: [
    ['inicio', 'Inicio', residente.Inicio, House], ['permisos', 'Permisos', residente.Permisos, Ticket], ['asistencias', 'Asistencias', residente.Asistencias, CalendarCheck],
    ['limpieza', 'Limpieza', residente.Limpieza, Sparkles], ['reservas', 'Reservas', residente.Reservas, UtensilsCrossed],
  ],
  APODERADO: [['permisos', 'Permisos', apoderado.Permisos, Ticket], ['hijos', 'Mis hijos', apoderado.Hijos, Users]],
  PRECEPTOR: [
    ['inicio', 'Inicio', pastor.Inicio, House], ['permisos', 'Permisos', pastor.Permisos, Ticket], ['cultos', 'Cultos', pastor.Cultos, BookOpen],
    ['limpieza', 'Limpieza', pastor.Limpieza, Sparkles], ['reservas', 'Reservas', pastor.Reservas, UtensilsCrossed], ['faltas', 'Faltas', pastor.Faltas, UserX],
    ['reporte', 'Reporte', pastor.Reporte, FileSpreadsheet],
  ],
  GARITA: [['verificar', 'Verificar', garita.Verificar, ScanLine], ['aprobados', 'Aprobados', garita.Aprobados, CircleCheck], ['historial', 'Historial', garita.Historial, History]],
  ADMINISTRADOR: [
    ['residentes', 'Residentes', admin.Residentes, Users], ['habitaciones', 'Habitaciones', admin.Habitaciones, BedDouble], ['usuarios', 'Usuarios', admin.Usuarios, UserCog],
    ['ajustes', 'Semestre y reglas', admin.Ajustes, Settings], ['auditoria', 'Auditoría', admin.Auditoria, ScrollText],
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
  const [ruta, apartado] = useRuta().split('/')
  const noLeidas = useDatos('/notificaciones/no-leidas', { cada: 20000 })
  const rol = sesion.roles[0]
  const menu = [...(MENUS[rol] ?? []), ['avisos', 'Avisos', Avisos, Bell], ['cuenta', 'Mi cuenta', Perfil, CircleUser]]
  const actual = menu.find(([r]) => r === ruta) ?? menu[0]
  const Vista = actual[2]
  const cantidad = noLeidas.datos?.cantidad

  return (
    <div className="panel">
      <nav className="lateral" aria-label="Principal">
        <div className="marca"><img src={logo} alt="ResiUP" /></div>
        <ul>
          {menu.map(([r, titulo, , Icono]) => (
            <li key={r}>
              <a href={`#/${r}`} aria-current={r === actual[0] ? 'page' : undefined}>
                <Icono size={19} />
                <span>{titulo}</span>
                {r === 'avisos' && cantidad > 0 && <span className="contador">{cantidad}</span>}
              </a>
            </li>
          ))}
        </ul>
        <div className="quien">
          <strong>{sesion.nombreCompleto}</strong>
          <span>{nombre(rol)}</span>
          <Boton variante="enlace" icono={LogOut} onClick={salir}>Cerrar sesión</Boton>
        </div>
      </nav>
      <main className="contenido">
        <Resguardo key={`${actual[0]}/${apartado}`}><Vista alLeer={noLeidas.recargar} apartado={apartado} /></Resguardo>
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
        <Boton variante="enlace" icono={LogOut} onClick={salir}>Salir</Boton>
      </main>
    )
  }
  return <Panel sesion={sesion} salir={salir} />
}

createRoot(document.getElementById('root')).render(
  <StrictMode><ProveedorAvisos><App /></ProveedorAvisos></StrictMode>,
)
