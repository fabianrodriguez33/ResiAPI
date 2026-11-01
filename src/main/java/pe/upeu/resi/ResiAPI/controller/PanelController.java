package pe.upeu.resi.ResiAPI.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** El panel web de administración es una página estática (static/panel); esta ruta corta redirige a ella. */
@Controller
public class PanelController {

    @GetMapping("/panel")
    public String panel() {
        return "redirect:/panel/index.html";
    }
}
