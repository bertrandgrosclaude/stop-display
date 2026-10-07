package com.stopdisplay.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/networks")
public class NetworkController {
    private static final List<Network> NETWORKS = List.of(
            new Network("tam", "TaM, Métropole de Montpellier", "TaM", "https://www.tam-voyages.com/"));

    @GetMapping
    public List<Network> networks() {
        return NETWORKS;
    }
}
