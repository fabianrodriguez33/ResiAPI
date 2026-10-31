package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.EstadoRevision;

@Entity
@Table(name = "justificacion_posterior")
@Getter
@Setter
@NoArgsConstructor
public class JustificacionPosterior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "evidencia_id")
    private Long evidenciaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoRevision estado;

    @Column(name = "revisada_por")
    private Long revisadaPor;

    @Column(name = "revisada_en")
    private Instant revisadaEn;

    @Column(name = "comentario")
    private String comentario;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
