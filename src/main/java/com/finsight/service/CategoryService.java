package com.finsight.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finsight.dto.CategoryRequest;
import com.finsight.dto.CategoryResponse;
import com.finsight.entity.Category;
import com.finsight.entity.User;
import com.finsight.exception.ResourceNotFoundException;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.UserRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.BudgetRepository;
import com.finsight.exception.ConflictException;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository,
                           TransactionRepository transactionRepository, BudgetRepository budgetRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(String email) {
        return categoryRepository.findByUserIdOrderByName(findUser(email).getId()).stream()
                .map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(String email, CategoryRequest request) {
        User user = findUser(email);
        String name = request.name().trim();
        validateType(request);
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(user.getId(), name, request.type())) {
            throw new ConflictException("Category already exists");
        }
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setType(request.type());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(String email, Long id, CategoryRequest request) {
        User user = findUser(email);
        validateType(request);
        Category category = ownedCategory(email, id);
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndType(user.getId(), name, request.type())
                && (!category.getName().equalsIgnoreCase(name) || category.getType() != request.type())) {
            throw new ConflictException("Category already exists");
        }
        boolean inUse = transactionRepository.existsByUserIdAndCategoryIgnoreCase(user.getId(), category.getName())
                || budgetRepository.existsByUserIdAndCategoryIgnoreCase(user.getId(), category.getName());
        if (inUse && (!category.getName().equals(name) || category.getType() != request.type())) {
            throw new ConflictException("This category is already used by financial records and cannot be changed");
        }
        category.setName(name);
        category.setType(request.type());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(String email, Long id) {
        Category category = ownedCategory(email, id);
        Long userId = category.getUser().getId();
        if (transactionRepository.existsByUserIdAndCategoryIgnoreCase(userId, category.getName())
                || budgetRepository.existsByUserIdAndCategoryIgnoreCase(userId, category.getName())) {
            throw new ConflictException("Cannot delete a category that is used by transactions or budgets");
        }
        categoryRepository.delete(category);
    }

    private Category ownedCategory(String email, Long id) {
        return categoryRepository.findById(id).filter(value -> value.getUser().getEmail().equals(email))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void validateType(CategoryRequest request) {
        if (request.type() == com.finsight.entity.TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Categories must be income or expense");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
