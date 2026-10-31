package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evidencia")
@Getter
@Setter
@NoArgsConstructor
public class Evidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_original")
    private String nombreOriginal;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "ruta")
    private String ruta;

    @Column(name = "tamanio")
    private long tamanio;

    @Column(name = "subida_por")
    private Long subidaPor;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
