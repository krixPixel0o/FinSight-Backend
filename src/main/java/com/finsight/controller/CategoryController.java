package com.finsight.controller;

import com.finsight.dto.CategoryRequest;
import com.finsight.dto.CategoryResponse;
import com.finsight.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/API/categories")
public class CategoryController {
    private final CategoryService categoryService;
    public CategoryController(CategoryService categoryService) { this.categoryService = categoryService; }
    @GetMapping public List<CategoryResponse> findAll(Authentication authentication) { return categoryService.findAll(authentication.getName()); }
    @PostMapping public ResponseEntity<CategoryResponse> create(Authentication authentication, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(authentication.getName(), request));
    }
    @PutMapping("/{id}") public CategoryResponse update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(authentication.getName(), id, request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        categoryService.delete(authentication.getName(), id); return ResponseEntity.noContent().build();
    }
}
