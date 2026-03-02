# Legal Agent - Spring Boot Application

A Spring Boot application for a legal agent service.

## Prerequisites

- Java 17 or higher
- Maven 3.6.0 or higher

## Getting Started

### 1. Build the project
```bash
mvn clean package
```

### 2. Run the application
```bash
mvn spring-boot:run
```

Or run the JAR file:
```bash
java -jar target/legal-agent-1.0.0.jar
```

The application will start on `http://localhost:8080`

## API Endpoints

- **GET `/`** - Returns "Hello, World!"
- **GET `/api/greeting`** - Returns a JSON greeting message

## Testing

Run the tests with:
```bash
mvn test
```

## Project Structure

```
legal-agent/
├── src/
│   ├── main/
│   │   ├── java/com/agent/
│   │   │   ├── AgentApplication.java     (Main application class)
│   │   │   └── AgentController.java      (REST controller)
│   │   └── resources/
│   │       └── application.properties    (Application configuration)
│   └── test/
│       └── java/com/agent/
│           └── AgentControllerTest.java (Unit tests)
├── pom.xml
├── README.md
└── .gitignore
```

## License

This project is open source and available under the MIT License.
