package com.kodika.kodikalab.competitions.category;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link CategoryService} con su repositorio inyectado, sin lógica. */
@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }
}
