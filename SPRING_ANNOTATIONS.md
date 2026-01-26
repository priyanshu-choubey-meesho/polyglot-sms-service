# Core Spring Annotations Reference

This document provides a comprehensive guide to all Spring Framework annotations used in the polyglot-sms-service project, including their general purpose, specific usage in this project, and other common applications.

---

## Table of Contents

1. [Application Configuration Annotations](#application-configuration-annotations)
2. [Component Stereotype Annotations](#component-stereotype-annotations)
3. [Dependency Injection Annotations](#dependency-injection-annotations)
4. [Web Layer Annotations](#web-layer-annotations)
5. [Configuration Annotations](#configuration-annotations)
6. [Testing Annotations](#testing-annotations)

---

## Application Configuration Annotations

### @SpringBootApplication

**General Purpose:**
`@SpringBootApplication` is a convenience annotation that combines three annotations:
- `@Configuration`: Marks the class as a source of bean definitions
- `@EnableAutoConfiguration`: Enables Spring Boot's auto-configuration mechanism
- `@ComponentScan`: Enables component scanning to find and register beans

This annotation is typically placed on the main application class and serves as the entry point for Spring Boot applications.

**Usage in This Project:**
```java
@SpringBootApplication
public class SmsSenderApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmsSenderApplication.class, args);
    }
}
```

In this project, `@SpringBootApplication` is used on the main class `SmsSenderApplication` to:
- Enable auto-configuration for Spring Boot features (Redis, Kafka, etc.)
- Scan for components in the `com.example.demo` package and sub-packages
- Configure the application context

**Other Applications:**
- **Microservices**: Entry point for Spring Boot microservices
- **REST APIs**: Main class for RESTful web services
- **Batch Processing**: Starting point for Spring Batch applications
- **Web Applications**: Main class for Spring MVC web applications
- **Event-Driven Systems**: Base configuration for Spring Cloud Stream applications

---

## Component Stereotype Annotations

### @Service

**General Purpose:**
`@Service` is a specialization of `@Component` that indicates a class contains business logic. It's a stereotype annotation that makes the intent clear: this class provides a service to other components. Spring automatically detects and registers classes annotated with `@Service` during component scanning.

**Usage in This Project:**
The project uses `@Service` on multiple classes:

1. **SmsService** - Core business logic for SMS operations:
```java
@Service
public class SmsService {
    // Handles SMS sending logic, blacklist checking, event publishing
}
```

2. **SmsEventProducer** - Kafka event publishing service:
```java
@Service
public class SmsEventProducer {
    // Publishes SMS events to Kafka topic
}
```

3. **TwillioService** - External SMS provider integration:
```java
@Service
public class TwillioService {
    // Handles communication with Twilio API
}
```

4. **BlacklistCache** - Redis-based caching service:
```java
@Service
public class BlacklistCache {
    // Manages blacklisted phone numbers in Redis
}
```

**Other Applications:**
- **Business Logic Layer**: Service classes that implement business rules
- **Integration Services**: Classes that integrate with external systems (APIs, databases, message queues)
- **Data Processing Services**: Services that transform or process data
- **Validation Services**: Custom validation logic beyond Bean Validation
- **Notification Services**: Email, SMS, push notification services
- **Payment Processing**: Services handling payment gateway integrations
- **Authentication Services**: Custom authentication and authorization logic

---

### @RestController

**General Purpose:**
`@RestController` is a convenience annotation that combines `@Controller` and `@ResponseBody`. It indicates that the class handles HTTP requests and that the return value of methods should be bound to the web response body (typically JSON/XML). It's specifically designed for RESTful web services.

**Usage in This Project:**
```java
@RestController
@RequestMapping("v1/sms/send")
public class SmsControllerV1 {
    @PostMapping
    public ResponseEntity<SmsResponse> sendSmsRequest(@Valid @RequestBody SmsRequest request) {
        // Handles POST requests to send SMS
    }
}
```

In this project, `@RestController` is used to:
- Create a REST endpoint for SMS sending operations
- Automatically serialize response objects to JSON
- Handle HTTP requests without needing to return view names

**Other Applications:**
- **RESTful APIs**: Building REST endpoints for CRUD operations
- **Microservices**: Exposing service endpoints in microservice architectures
- **Mobile Backends**: Providing JSON APIs for mobile applications
- **SPA Backends**: Backend APIs for Single Page Applications
- **Webhook Endpoints**: Receiving webhooks from external services
- **GraphQL Endpoints**: REST endpoints that serve GraphQL queries

---

## Dependency Injection Annotations

### @Autowired

**General Purpose:**
`@Autowired` enables automatic dependency injection by type. Spring's dependency injection container automatically wires beans together. It can be used on:
- **Constructors**: Preferred method (constructor injection)
- **Fields**: Direct field injection (less preferred)
- **Setter methods**: Setter-based injection

Constructor injection is generally preferred as it makes dependencies explicit and enables immutability.

**Usage in This Project:**
```java
@Service
public class BlacklistCache {
    private final StringRedisTemplate redisTemplate;
    
    @Autowired
    public BlacklistCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
```

In this project, `@Autowired` is used for:
- Constructor injection in `BlacklistCache` to inject `StringRedisTemplate`
- Constructor injection in `SmsControllerV1` to inject `SmsService`

**Other Applications:**
- **Repository Injection**: Injecting JPA repositories or data access objects
- **Service Injection**: Injecting business service dependencies
- **Configuration Injection**: Injecting configuration properties or beans
- **External Client Injection**: Injecting HTTP clients, database connections
- **Event Publisher Injection**: Injecting application event publishers
- **Cache Manager Injection**: Injecting cache managers for caching operations

**Note:** In modern Spring (4.3+), `@Autowired` is optional on constructors when there's only one constructor, but it's often kept for clarity.

---

## Web Layer Annotations

### @RequestMapping

**General Purpose:**
`@RequestMapping` maps HTTP requests to handler methods. It can be used at both class and method levels to define URL patterns, HTTP methods, request parameters, headers, and content types. It's the most flexible mapping annotation but has more specific alternatives like `@GetMapping`, `@PostMapping`, etc.

**Usage in This Project:**
```java
@RestController
@RequestMapping("v1/sms/send")
public class SmsControllerV1 {
    // All methods in this controller will have the base path "v1/sms/send"
}
```

In this project, `@RequestMapping` is used at the class level to:
- Define the base URL path for all endpoints in the controller
- Establish API versioning (v1) in the URL structure
- Group related endpoints under a common path

**Other Applications:**
- **API Versioning**: Different versions of APIs (v1, v2, etc.)
- **Resource Grouping**: Grouping related endpoints (e.g., `/api/users`, `/api/products`)
- **Path Prefixes**: Adding common prefixes to all controller methods
- **Content Type Mapping**: Specifying `produces` and `consumes` attributes
- **Header-Based Routing**: Routing based on request headers
- **Multi-Method Endpoints**: Endpoints that handle multiple HTTP methods

---

### @PostMapping

**General Purpose:**
`@PostMapping` is a composed annotation that acts as a shortcut for `@RequestMapping(method = RequestMethod.POST)`. It's specifically for handling HTTP POST requests, which are typically used for creating resources or submitting data.

**Usage in This Project:**
```java
@PostMapping
public ResponseEntity<SmsResponse> sendSmsRequest(@Valid @RequestBody SmsRequest request) {
    String result = service.sendSms(request);
    return ResponseEntity.ok(new SmsResponse(result));
}
```

In this project, `@PostMapping` is used to:
- Handle POST requests for sending SMS messages
- Accept JSON request body containing phone number and message
- Return a response indicating success or failure

**Other Applications:**
- **Resource Creation**: Creating new resources (POST /users, POST /orders)
- **Form Submissions**: Handling form data submissions
- **File Uploads**: Receiving file uploads via POST
- **Search Operations**: POST requests for complex search queries
- **Action Endpoints**: Triggering actions (e.g., POST /users/{id}/activate)
- **Webhook Receivers**: Receiving webhook payloads from external services

---

### @RequestBody

**General Purpose:**
`@RequestBody` binds the HTTP request body to a method parameter. It uses HTTP message converters to deserialize the request body (typically JSON or XML) into a Java object. Spring automatically handles the conversion based on the `Content-Type` header.

**Usage in This Project:**
```java
@PostMapping
public ResponseEntity<SmsResponse> sendSmsRequest(@Valid @RequestBody SmsRequest request) {
    // request is automatically deserialized from JSON to SmsRequest object
}
```

In this project, `@RequestBody` is used to:
- Deserialize JSON request body into `SmsRequest` object
- Extract phone number and message from the incoming JSON
- Work in conjunction with `@Valid` for request validation

**Other Applications:**
- **JSON APIs**: Receiving JSON payloads in REST APIs
- **XML APIs**: Receiving XML payloads (with appropriate converter)
- **Complex Objects**: Deserializing nested objects and collections
- **DTOs**: Using Data Transfer Objects for request/response
- **Command Objects**: Receiving command objects in CQRS patterns
- **Bulk Operations**: Receiving arrays or lists of objects

---

## Configuration Annotations

### @Configuration

**General Purpose:**
`@Configuration` indicates that a class declares one or more `@Bean` methods and may be processed by the Spring container to generate bean definitions. It's used for Java-based configuration instead of XML configuration. Classes annotated with `@Configuration` are themselves beans and can be injected.

**Usage in This Project:**
```java
@Configuration
public class BlacklistInitializer {
    @Bean
    CommandLineRunner initBlacklist(BlacklistCache blacklistCache) {
        return args -> {
            blacklistCache.addToBlacklist("+1111111111");
        };
    }
}
```

In this project, `@Configuration` is used to:
- Define configuration beans for application initialization
- Create a `CommandLineRunner` bean that runs on application startup
- Pre-seed the blacklist with test data

**Other Applications:**
- **Bean Definitions**: Replacing XML configuration with Java configuration
- **Conditional Configuration**: Using `@ConditionalOnProperty` for feature flags
- **Profile-Specific Config**: Different configurations for dev, test, prod
- **Security Configuration**: Configuring Spring Security
- **Database Configuration**: Setting up data sources and JPA
- **Message Queue Configuration**: Configuring Kafka, RabbitMQ, etc.
- **Cache Configuration**: Setting up Redis, Caffeine, etc.
- **Scheduling Configuration**: Configuring `@Scheduled` tasks

---

### @Bean

**General Purpose:**
`@Bean` indicates that a method produces a bean to be managed by the Spring container. The method's return value becomes a bean in the Spring application context. It's used within `@Configuration` classes to define beans programmatically.

**Usage in This Project:**
```java
@Configuration
public class BlacklistInitializer {
    @Bean
    CommandLineRunner initBlacklist(BlacklistCache blacklistCache) {
        return args -> {
            blacklistCache.addToBlacklist("+1111111111");
            System.out.println("✓ Blacklist initialized - Blocked number: +1111111111");
        };
    }
}
```

In this project, `@Bean` is used to:
- Create a `CommandLineRunner` bean that executes on application startup
- Initialize the blacklist cache with predefined phone numbers
- Perform one-time setup operations

**Other Applications:**
- **Custom Bean Creation**: Creating beans with custom initialization logic
- **Third-Party Integration**: Wrapping third-party objects as Spring beans
- **Factory Methods**: Creating beans using factory patterns
- **Conditional Beans**: Creating beans based on conditions
- **Bean Customization**: Customizing existing beans (e.g., `RestTemplate`, `ObjectMapper`)
- **Initialization Logic**: Running setup code on application startup
- **Resource Configuration**: Configuring data sources, connection pools, etc.

---

## Testing Annotations

### @WebMvcTest

**General Purpose:**
`@WebMvcTest` is a Spring Boot test annotation that focuses on testing the web layer (controllers) without loading the full application context. It auto-configures MockMvc and only loads web-related components, making tests faster and more focused. It's part of Spring Boot's test slice annotations.

**Usage in This Project:**
```java
@WebMvcTest(SmsControllerV1.class)
class SmsSenderApplicationTests {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private SmsService smsService;
    
    @Test
    void testSendSms_Success() {
        // Test controller endpoints
    }
}
```

In this project, `@WebMvcTest` is used to:
- Test the `SmsControllerV1` REST controller in isolation
- Verify HTTP request/response handling
- Test request validation and error handling
- Ensure proper JSON serialization/deserialization

**Other Applications:**
- **Controller Unit Tests**: Testing REST controllers without full context
- **Request Validation Tests**: Testing `@Valid` annotations and error responses
- **HTTP Status Code Tests**: Verifying correct HTTP status codes
- **Content Negotiation**: Testing different content types (JSON, XML)
- **Security Tests**: Testing security configurations on endpoints
- **Exception Handling**: Testing global exception handlers
- **Filter Testing**: Testing servlet filters and interceptors

---

### @MockBean

**General Purpose:**
`@MockBean` is a Spring Boot test annotation that adds a mock object to the Spring application context. It replaces any existing bean of the same type in the application context with a Mockito mock. This is useful for testing components in isolation by mocking their dependencies.

**Usage in This Project:**
```java
@WebMvcTest(SmsControllerV1.class)
class SmsSenderApplicationTests {
    @MockBean
    private SmsService smsService;
    
    @Test
    void testSendSms_Success() {
        when(smsService.sendSms(any(SmsRequest.class)))
            .thenReturn("SMS sent to +1234567890");
        // Test controller with mocked service
    }
}
```

In this project, `@MockBean` is used to:
- Mock the `SmsService` dependency in controller tests
- Isolate controller testing from business logic
- Control service behavior for different test scenarios
- Verify service method calls from the controller

**Other Applications:**
- **Integration Test Isolation**: Mocking external dependencies (databases, APIs)
- **Service Layer Testing**: Mocking repository or external service dependencies
- **Configuration Testing**: Mocking configuration beans
- **Event Testing**: Mocking event publishers or listeners
- **Cache Testing**: Mocking cache managers
- **Message Queue Testing**: Mocking Kafka, RabbitMQ templates

---

## Summary

### Annotation Usage Summary

| Annotation | Count in Project | Primary Purpose |
|------------|------------------|-----------------|
| `@SpringBootApplication` | 1 | Application entry point |
| `@Service` | 4 | Business logic components |
| `@RestController` | 1 | REST API controller |
| `@Autowired` | 2 | Dependency injection |
| `@RequestMapping` | 1 | URL mapping |
| `@PostMapping` | 1 | POST endpoint |
| `@RequestBody` | 1 | Request deserialization |
| `@Configuration` | 1 | Configuration class |
| `@Bean` | 1 | Bean definition |
| `@WebMvcTest` | 1 | Web layer testing |
| `@MockBean` | 1 | Mock in Spring context |

### Best Practices Demonstrated

1. **Constructor Injection**: Using `@Autowired` on constructors (preferred over field injection)
2. **Service Layer Separation**: Clear separation with `@Service` for business logic
3. **RESTful Design**: Using `@RestController` for clean REST APIs
4. **Configuration Management**: Using `@Configuration` and `@Bean` for setup
5. **Test Isolation**: Using `@WebMvcTest` and `@MockBean` for focused testing

---

## Additional Resources

- [Spring Framework Documentation](https://docs.spring.io/spring-framework/reference/)
- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/reference/)
- [Spring Testing Documentation](https://docs.spring.io/spring-framework/reference/testing.html)

---

**Last Updated:** January 2026  
**Project:** polyglot-sms-service
