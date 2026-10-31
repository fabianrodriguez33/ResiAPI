package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "asignacion_habitacion")
@Getter
@Setter
@NoArgsConstructor
public class AsignacionHabitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "residente_id")
    private Long residenteId;

    @Column(name = "habitacion_id")
    private Long habitacionId;

    @Column(name = "desde")
    private Instant desde;

    @Column(name = "hasta")
    private Instant hasta;
}
