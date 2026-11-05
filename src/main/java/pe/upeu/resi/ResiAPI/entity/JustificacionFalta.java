package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "justificacion_falta")
@Getter
@Setter
@NoArgsConstructor
public class JustificacionFalta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "registrada_por")
    private Long registradaPor;

    @Column(name = "registrada_en")
    private Instant registradaEn;
}
