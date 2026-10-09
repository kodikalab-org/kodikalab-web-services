package com.kodika.kodikalab.problems.material;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link MaterialService} con su repositorio inyectado, sin lógica. */
@Service
public class MaterialServiceImpl implements MaterialService {
    private final MaterialRepository materialRepository;

    public MaterialServiceImpl(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }
}
