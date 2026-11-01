package pe.upeu.resi.ResiAPI.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "apoderado")
@Getter
@Setter
@NoArgsConstructor
public class Apoderado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "telefono")
    private String telefono;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "email")
    private String email;
}
