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

/** Estado actual de un día del comedor. Solo existe la fila de las fechas que alguna vez se cerraron; no se borra al reabrir. */
@Entity
@Table(name = "dia_comedor")
@Getter
@Setter
@NoArgsConstructor
public class DiaComedor {

    @Id
    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "cerrado")
    private boolean cerrado;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "actualizado_por")
    private Long actualizadoPor;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;
}
