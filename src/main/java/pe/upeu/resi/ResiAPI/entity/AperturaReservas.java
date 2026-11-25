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

/** Apertura, por el pastor o monitor, de las reservas de una comida en una fecha. Nunca se borra: queda quién abrió y cuándo. */
@Entity
@Table(name = "apertura_reservas")
@Getter
@Setter
@NoArgsConstructor
public class AperturaReservas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "grupo")
    private String grupo;

    @Column(name = "abierto_por")
    private Long abiertoPor;

    @Column(name = "abierto_en")
    private Instant abiertoEn;
}
