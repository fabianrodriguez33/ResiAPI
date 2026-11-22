package pe.upeu.resi.ResiAPI.service;

/** Celdas de CSV: se encierran entre comillas y se evita que una celda empiece como fórmula en Excel. */
final class Csv {

    private Csv() {
    }

    static String celda(String valor) {
        String v = valor == null ? "" : valor.replace("\"", "\"\"");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }
}
