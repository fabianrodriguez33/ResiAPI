package pe.upeu.resi.ResiAPI.dto.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas, boolean ultima) {

    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapeo) {
        return new PaginaResponse<>(page.getContent().stream().map(mapeo).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
