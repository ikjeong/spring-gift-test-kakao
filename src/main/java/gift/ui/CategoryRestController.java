package gift.ui;

import gift.application.CategoryService;
import gift.application.CreateCategoryRequest;
import gift.model.Category;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/categories")
@RestController
public class CategoryRestController {
    private final CategoryService categoryService;

    public CategoryRestController(final CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public Category create(@RequestBody final CreateCategoryRequest request) {
        return categoryService.create(request);
    }

    @GetMapping
    public List<Category> retrieve() {
        return categoryService.retrieve();
    }
}
