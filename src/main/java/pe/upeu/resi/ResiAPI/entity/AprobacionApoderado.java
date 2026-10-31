package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.DecisionApoderado;

@Entity
@Table(name = "aprobacion_apoderado")
@Getter
@Setter
@NoArgsConstructor
public class AprobacionApoderado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Column(name = "apoderado_id")
    private Long apoderadoId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expira_en")
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private DecisionApoderado decision;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
