package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evaluacion_limpieza")
@Getter
@Setter
@NoArgsConstructor
public class EvaluacionLimpieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "evaluador_id")
    private Long evaluadorId;

    @Column(name = "total")
    private short total;

    @Column(name = "observacion")
    private String observacion;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
