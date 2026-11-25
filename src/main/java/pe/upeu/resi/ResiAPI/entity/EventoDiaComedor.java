package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.AccionComedor;

/** Historial de cierres y reaperturas de un día del comedor. Solo se agregan filas. */
@Entity
@Table(name = "evento_dia_comedor")
@Getter
@Setter
@NoArgsConstructor
public class EventoDiaComedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion")
    private AccionComedor accion;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
