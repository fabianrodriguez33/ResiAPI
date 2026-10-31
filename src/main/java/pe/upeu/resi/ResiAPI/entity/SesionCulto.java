package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.EstadoSesion;
import pe.upeu.resi.ResiAPI.enums.TipoCulto;

@Entity
@Table(name = "sesion_culto")
@Getter
@Setter
@NoArgsConstructor
public class SesionCulto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoCulto tipo;

    @Column(name = "inicio")
    private Instant inicio;

    @Column(name = "corte")
    private Instant corte;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoSesion estado;

    @Column(name = "abierta_por")
    private Long abiertaPor;

    @Column(name = "creado_en")
    private Instant creadoEn;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;
}
