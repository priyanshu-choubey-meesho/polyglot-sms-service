# Polyglot SMS Service

A microservices-based SMS processing system with Spring Boot (Java) and Go services communicating via Kafka.

## Architecture

```
┌─────────────────────┐
│  Spring Boot (Java) │
│   Port: 8080        │
│   POST /v1/sms/send │
└──────────┬──────────┘
           │ Produces SMS events
           ▼
    ┌──────────────┐
    │    Kafka     │
    │ sms_events   │
    └──────┬───────┘
           │ Consumes events
           ▼
┌─────────────────────┐
│   Go Service        │
│   Port: 8081        │
│   GET /v1/user/     │
│   {phone}/messages  │
└──────────┬──────────┘
           │
           ▼
    ┌──────────────┐
    │   MongoDB    │
    │   Port:27017 │
    └──────────────┘
```

## Prerequisites

- **Java 21** - LTS version
- **Go 1.25+** - For Go service
- **MongoDB 8.0+** - Document storage
- **Kafka 3.6.1** - Event streaming (with Zookeeper)

## Quick Start

### 1. Install Dependencies

**MongoDB:**

```bash
curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc | \
   sudo gpg --dearmor -o /usr/share/keyrings/mongodb-server-8.0.gpg
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" | \
   sudo tee /etc/apt/sources.list.d/mongodb-org-8.0.list
sudo apt-get update && sudo apt-get install -y mongodb-org
sudo systemctl start mongod && sudo systemctl enable mongod
```

**Kafka:**

```bash
cd ~ && wget https://archive.apache.org/dist/kafka/3.6.1/kafka_2.13-3.6.1.tgz
tar -xzf kafka_2.13-3.6.1.tgz
```

### 2. Option A: Setup Services for Manual Testing/Demo

```bash
cd ~/Desktop/polyglot-sms-service
./setup-services.sh
```

This starts all services and provides cURL examples for manual testing with Postman.

### 3. Option B: Run Automated Tests

```bash
cd ~/Desktop/polyglot-sms-service
./run-tests.sh
```

This automatically:

- Stops any running services
- Cleans test data
- Starts all services
- Runs E2E tests
- Reports results

### 4. Stop All Services

```bash
./stop-services.sh
```

## Available Scripts

- **`./setup-services.sh`** - Start services for manual testing/demos
- **`./run-tests.sh`** - Run complete automated test suite
- **`./stop-services.sh`** - Stop all running services

## Testing with Postman

After running `./setup-services.sh`, use these endpoints:

**Send SMS:**

```
POST http://localhost:8080/v1/sms/send
Content-Type: application/json

{
  "phoneNumber": "+1234567890",
  "message": "Hello World"
}
```

**Retrieve Messages:**

```
GET http://localhost:8081/v1/user/+1234567890/messages
```

## Stop Services

```bash
./stop-services.sh
```

## Available Scripts

- **start-all-services.sh** - Start all services
- **stop-services.sh** - Stop all services
- **test-e2e.sh** - End-to-end integration test

## API Usage

**Send SMS:**

```bash
curl -X POST http://localhost:8080/v1/sms/send \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+1234567890", "message": "Hello"}'
```

**Retrieve Messages:**

```bash
curl http://localhost:8081/v1/user/+1234567890/messages
```

## View Logs

```bash
tail -f logs/spring-boot.log   # Spring Boot
tail -f logs/go-service.log    # Go service
tail -f logs/kafka.log         # Kafka
tail -f logs/zookeeper.log     # Zookeeper
```

## Technologies

- Spring Boot 3.4.1 (Java 21)
- Go 1.25
- Apache Kafka 3.6.1
- MongoDB 8.0
- Gradle 9.2.1

## Troubleshooting

**Check services:**

```bash
netstat -tuln | grep -E ":(8080|8081|9092|2181|27017)"
```

**Restart MongoDB:**

```bash
sudo systemctl restart mongod
```

**Kill process on port:**

```bash
lsof -i :8080
kill -9 <PID>
```
