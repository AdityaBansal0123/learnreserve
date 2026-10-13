package com.learnreserve.resource;

import com.learnreserve.common.*;
import java.time.Clock;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class ResourceService {
    private final ResourceRepository resources;
    private final Clock clock;

    public ResourceService(ResourceRepository resources, Clock clock) {
        this.resources = resources;
        this.clock = clock;
    }

    public PageResponse<ResourceController.ResourceView> discover(LearningResource.Type type, String q, int page, int size) {
        return PageResponse.from(resources.discover(type, q.strip(), PageRequest.of(page, size, Sort.by("id")))
                .map(ResourceController.ResourceView::from));
    }

    public ResourceController.ResourceView get(Long id) {
        return ResourceController.ResourceView.from(resources.findById(id).orElseThrow(() -> ApiException.notFound("Resource")));
    }

    @Transactional
    public ResourceController.ResourceView create(ResourceController.ResourceRequest request) {
        return ResourceController.ResourceView.from(resources.save(new LearningResource(request.name(), request.description(),
                request.type(), request.location(), clock.instant())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResourceController.ResourceView update(Long id, ResourceController.ResourceRequest request) {
        var resource = lock(id);
        resource.update(request.name(), request.description(), request.type(), request.location());
        return ResourceController.ResourceView.from(resource);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResourceController.ResourceView setActive(Long id, boolean active) {
        var resource = lock(id);
        resource.setActive(active);
        return ResourceController.ResourceView.from(resource);
    }

    private LearningResource lock(Long id) {
        return resources.lockById(id).orElseThrow(() -> ApiException.notFound("Resource"));
    }
}
