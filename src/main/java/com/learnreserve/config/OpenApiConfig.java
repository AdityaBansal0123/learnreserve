package com.learnreserve.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info().title("LearnReserve API").version("1.0.0")
                        .description("Learning resources, availability and transaction-safe bookings. All timestamps require an offset (UTC recommended), at whole-second precision. Intervals are [start, end). Use Authorize with a registered email and password. Admin writes require ADMIN. Errors use application/problem+json."))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    @Bean
    OpenApiCustomizer errorContract() {
        return api -> {
            var fieldError = new ObjectSchema().addProperty("field", new StringSchema())
                    .addProperty("message", new StringSchema());
            var error = new ObjectSchema()
                    .addProperty("type", new StringSchema().example("urn:learnreserve:error:booking-conflict"))
                    .addProperty("title", new StringSchema().example("Conflict"))
                    .addProperty("status", new IntegerSchema().example(409))
                    .addProperty("detail", new StringSchema())
                    .addProperty("instance", new StringSchema().example("/api/v1/bookings"))
                    .addProperty("code", new StringSchema().example("BOOKING_CONFLICT"))
                    .addProperty("errors", new ArraySchema().items(fieldError));
            api.getComponents().addSchemas("ApiProblem", error);
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                operation.getResponses().addApiResponse("400", errorResponse("Invalid request or validation failure"));
                if (path.contains("{")) {
                    operation.getResponses().addApiResponse("404", errorResponse("Resource not found, or booking not owned by caller"));
                }
                if (operation.getSecurity() == null || !operation.getSecurity().isEmpty()) {
                    operation.getResponses().addApiResponse("401", errorResponse("Missing or invalid credentials"));
                    operation.getResponses().addApiResponse("403", errorResponse("Insufficient role permissions"));
                }
                if (method != PathItem.HttpMethod.GET) {
                    operation.getResponses().addApiResponse("409", errorResponse("Booking, availability, state, or data conflict; inspect code"));
                }
                operation.getResponses().addApiResponse("500", errorResponse("Unexpected server error"));
            }));
        };
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem"))));
    }
}
