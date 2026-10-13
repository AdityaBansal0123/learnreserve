# Submission email template

Replace the placeholders and confirm the repository is accessible before sending.

**Subject:** Learning Resource Booking System — Assignment Submission — [Your Name]

Hi [Hiring Team / Recruiter Name],

Please find my Learning Resource Booking and Availability System submission:

**GitHub:** [Repository URL]

The backend uses Java 21, Spring Boot, Spring Data JPA, MySQL, and Maven. It includes resource and availability management, authenticated booking/cancellation, booking history, input validation, and Swagger/OpenAPI documentation.

Concurrent reservations are protected with database row locks and transactions. The automated suite passes on both H2 and MySQL, including an eight-request race that verifies exactly one successful booking.

For a quick local demonstration, run:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Then open `http://localhost:8080/swagger-ui.html`. The README includes demo credentials, MySQL/Docker setup, architecture, database design, and testing instructions.

Thank you for the opportunity. I would be happy to walk through the implementation and design decisions.

Best regards,
[Your Name]
