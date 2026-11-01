package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Día de lunes a viernes en el que no hay cultos (feriado, vacaciones). */
@Entity
@Table(name = "dia_sin_culto")
@Getter
@Setter
@NoArgsConstructor
public class DiaSinCulto {

    @Id
    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "creado_por")
    private Long creadoPor;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
