package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.upeu.resi.ResiAPI.enums.TipoMovimiento;

@Entity
@Table(name = "movimiento_garita")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoGarita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoMovimiento tipo;

    @Column(name = "registrado_en")
    private Instant registradoEn;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(name = "minutos_tarde")
    private Integer minutosTarde;

    @Column(name = "anulado")
    private boolean anulado;

    @Column(name = "anulado_por")
    private Long anuladoPor;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;
}
