package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.EstadoSolicitud;
import pe.upeu.resi.ResiAPI.enums.TipoPermiso;

@Entity
@Table(name = "solicitud_permiso")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudPermiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoPermiso tipo;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "destino")
    private String destino;

    @Column(name = "salida_solicitada")
    private Instant salidaSolicitada;

    @Column(name = "retorno_solicitado")
    private Instant retornoSolicitado;

    @Column(name = "retorno_autorizado")
    private Instant retornoAutorizado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoSolicitud estado;

    @Column(name = "requiere_apoderado")
    private boolean requiereApoderado;

    @Column(name = "decidido_por")
    private Long decididoPor;

    @Column(name = "decidido_en")
    private Instant decididoEn;

    @Column(name = "comentario_decision")
    private String comentarioDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen")
    private pe.upeu.resi.ResiAPI.enums.OrigenSolicitud origen;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "responsable_nombre")
    private String responsableNombre;

    @Column(name = "responsable_celular")
    private String responsableCelular;

    @Column(name = "acompanante")
    private String acompanante;

    @Column(name = "clave_cliente")
    private String claveCliente;

    @Column(name = "creado_en")
    private Instant creadoEn;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;
}
