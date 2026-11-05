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
import pe.upeu.resi.ResiAPI.enums.EstadoReserva;
import pe.upeu.resi.ResiAPI.enums.TipoReserva;

/** Reserva de una comida. {@code fecha} es el día en que se sirve. Nunca se borra: se cancela con quién, cuándo y por qué. */
@Entity
@Table(name = "reserva_alimentacion")
@Getter
@Setter
@NoArgsConstructor
public class ReservaAlimentacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoReserva tipo;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "curso")
    private String curso;

    @Column(name = "observacion")
    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoReserva estado;

    @Column(name = "creada_por")
    private Long creadaPor;

    @Column(name = "creada_en")
    private Instant creadaEn;

    @Column(name = "cancelada_por")
    private Long canceladaPor;

    @Column(name = "cancelada_en")
    private Instant canceladaEn;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;
}
