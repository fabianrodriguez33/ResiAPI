package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "residente_apoderado")
@IdClass(ResidenteApoderado.Clave.class)
@Getter
@Setter
@NoArgsConstructor
public class ResidenteApoderado {

    @Id
    @Column(name = "residente_id")
    private Long residenteId;

    @Id
    @Column(name = "apoderado_id")
    private Long apoderadoId;

    private String parentesco;

    private boolean principal;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long residenteId;
        private Long apoderadoId;
    }
}
