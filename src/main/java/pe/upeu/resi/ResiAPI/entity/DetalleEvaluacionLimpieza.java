package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.CriterioLimpieza;

@Entity
@Table(name = "detalle_evaluacion_limpieza")
@Getter
@Setter
@NoArgsConstructor
public class DetalleEvaluacionLimpieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluacion_id")
    private Long evaluacionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterio")
    private CriterioLimpieza criterio;

    @Column(name = "puntaje")
    private short puntaje;
}
