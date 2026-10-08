package com.finsight.dto;

import com.finsight.entity.Category;
import com.finsight.entity.TransactionType;

public record CategoryResponse(Long id, String name, TransactionType type) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType());
    }
}