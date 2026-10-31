package pe.upeu.resi.ResiAPI.mapper;

import pe.upeu.resi.ResiAPI.dto.response.UsuarioResponse;
import pe.upeu.resi.ResiAPI.entity.Usuario;

public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse aResponse(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getUsername(), u.getNombreCompleto(), u.getRol(), u.isActivo());
    }
}
