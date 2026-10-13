package com.learnreserve.resource;

import com.learnreserve.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources")
public class ResourceController {
    private final ResourceService service;
    public ResourceController(ResourceService service) { this.service = service; }

    public record ResourceRequest(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 2000) String description, @NotNull LearningResource.Type type,
            @NotBlank @Size(max = 200) String location) {}
    public record ActiveRequest(@NotNull Boolean active) {}
    public record ResourceView(Long id, String name, String description, LearningResource.Type type, String location, boolean active) {
        public static ResourceView from(LearningResource resource) {
            return new ResourceView(resource.getId(), resource.getName(), resource.getDescription(), resource.getType(),
                    resource.getLocation(), resource.isActive());
        }
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Discover active resources", description = "Optional type and case-insensitive name/description search. Page is zero-based; size is 1–100.")
    public PageResponse<ResourceView> list(@RequestParam(required = false) LearningResource.Type type,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.discover(type, q, page, size);
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Get resource details, including inactive resources")
    public ResourceView get(@PathVariable @Positive Long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a resource (admin)")
    public ResponseEntity<ResourceView> create(@Valid @RequestBody ResourceRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/resources/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update resource metadata (admin)")
    public ResourceView update(@PathVariable @Positive Long id, @Valid @RequestBody ResourceRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a resource (admin)", description = "Deactivation blocks new bookings but preserves existing reservations and history.")
    public ResourceView active(@PathVariable @Positive Long id, @Valid @RequestBody ActiveRequest request) {
        return service.setActive(id, request.active());
    }
}
