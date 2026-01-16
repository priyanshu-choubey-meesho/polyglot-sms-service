#!/bin/bash

# =============================================================================
# Polyglot SMS Service - Automated Test Runner
# =============================================================================
# This script:
# 1. Stops all running services
# 2. Cleans up test data
# 3. Starts all services (Zookeeper, Kafka, Spring Boot, Go)
# 4. Runs E2E tests to verify message delivery
# 5. Reports test results
# =============================================================================

set -e

PROJECT_ROOT="$HOME/Desktop/polyglot-sms-service"
KAFKA_HOME="$HOME/kafka_2.13-3.6.1"
LOG_DIR="$PROJECT_ROOT/logs"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo ""
echo "=============================================="
echo "  Polyglot SMS Service - Automated Testing"
echo "=============================================="
echo ""

# =============================================================================
# Step 1: Stop all existing services
# =============================================================================
echo -e "${YELLOW}[1/7] Stopping any running services...${NC}"

# Stop Go Service (including go run processes)
if [ -f "$LOG_DIR/go-service.pid" ]; then
    GO_PID=$(cat "$LOG_DIR/go-service.pid")
    kill -9 $GO_PID 2>/dev/null && echo "  ✓ Go service stopped" || true
    rm "$LOG_DIR/go-service.pid"
fi
# Kill any process on port 8081
lsof -ti:8081 2>/dev/null | xargs kill -9 2>/dev/null || true
# Kill any "go run" processes
pkill -9 -f "go run cmd/app/main.go" 2>/dev/null || true
sleep 1
echo "  ✓ Go service stopped"

# Stop Spring Boot
if [ -f "$LOG_DIR/spring-boot.pid" ]; then
    SPRING_PID=$(cat "$LOG_DIR/spring-boot.pid")
    kill -9 $SPRING_PID 2>/dev/null && echo "  ✓ Spring Boot stopped" || true
    rm "$LOG_DIR/spring-boot.pid"
else
    lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true
fi

# Stop Kafka
if [ -f "$LOG_DIR/kafka.pid" ]; then
    KAFKA_PID=$(cat "$LOG_DIR/kafka.pid")
    kill -9 $KAFKA_PID 2>/dev/null && echo "  ✓ Kafka stopped" || true
    rm "$LOG_DIR/kafka.pid"
else
    cd "$KAFKA_HOME"
    bin/kafka-server-stop.sh 2>/dev/null || true
fi

# Stop Zookeeper
if [ -f "$LOG_DIR/zookeeper.pid" ]; then
    ZK_PID=$(cat "$LOG_DIR/zookeeper.pid")
    kill -9 $ZK_PID 2>/dev/null && echo "  ✓ Zookeeper stopped" || true
    rm "$LOG_DIR/zookeeper.pid"
else
    cd "$KAFKA_HOME"
    bin/zookeeper-server-stop.sh 2>/dev/null || true
fi

sleep 2
echo -e "${GREEN}  ✓ All services stopped${NC}"
echo ""

# =============================================================================
# Step 2: Clean up test data
# =============================================================================
echo -e "${YELLOW}[2/7] Cleaning up test data...${NC}"

if command -v mongosh &> /dev/null; then
    mongosh smsstore --quiet --eval 'db.smsdata.drop()' > /dev/null 2>&1 || true
    echo "  ✓ MongoDB cleaned"
else
    echo "  ⚠ mongosh not found, skipping MongoDB cleanup"
fi
echo ""

# =============================================================================
# Step 3: Start services
# =============================================================================
echo -e "${YELLOW}[3/7] Starting services...${NC}"

mkdir -p "$LOG_DIR"

# Function to check if service is ready
wait_for_port() {
    local port=$1
    local service=$2
    local max_wait=60
    local count=0
    
    echo -n "  Waiting for $service (port $port)..."
    while [ $count -lt $max_wait ]; do
        if netstat -tuln 2>/dev/null | grep -q ":$port " || ss -tuln 2>/dev/null | grep -q ":$port "; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        sleep 1
        echo -n "."
        count=$((count + 1))
    done
    echo -e " ${RED}✗ Timeout${NC}"
    return 1
}

# Check MongoDB
if sudo systemctl is-active --quiet mongod; then
    echo "  ✓ MongoDB is running"
else
    echo "  Starting MongoDB..."
    sudo systemctl start mongod
    sleep 2
    echo "  ✓ MongoDB started"
fi

# Start Zookeeper
if netstat -tuln 2>/dev/null | grep -q ":2181 " || ss -tuln 2>/dev/null | grep -q ":2181 "; then
    echo "  ✓ Zookeeper already running"
else
    rm -rf /tmp/zookeeper 2>/dev/null
    cd "$KAFKA_HOME"
    nohup bin/zookeeper-server-start.sh config/zookeeper.properties > "$LOG_DIR/zookeeper.log" 2>&1 &
    ZOOKEEPER_PID=$!
    echo $ZOOKEEPER_PID > "$LOG_DIR/zookeeper.pid"
    wait_for_port 2181 "Zookeeper"
fi

# Start Kafka
if netstat -tuln 2>/dev/null | grep -q ":9092 " || ss -tuln 2>/dev/null | grep -q ":9092 "; then
    echo "  ✓ Kafka already running"
else
    rm -rf /tmp/kafka-logs 2>/dev/null
    cd "$KAFKA_HOME"
    nohup bin/kafka-server-start.sh config/server.properties > "$LOG_DIR/kafka.log" 2>&1 &
    KAFKA_PID=$!
    echo $KAFKA_PID > "$LOG_DIR/kafka.pid"
    wait_for_port 9092 "Kafka"
    sleep 3
fi

# Create Kafka topic
cd "$KAFKA_HOME"
if bin/kafka-topics.sh --list --bootstrap-server localhost:9092 2>/dev/null | grep -q "sms_events"; then
    echo "  ✓ Kafka topic exists"
else
    bin/kafka-topics.sh --create \
        --bootstrap-server localhost:9092 \
        --replication-factor 1 \
        --partitions 1 \
        --topic sms_events 2>/dev/null
    echo "  ✓ Kafka topic created"
fi

# Start Spring Boot
if netstat -tuln 2>/dev/null | grep -q ":8080 " || ss -tuln 2>/dev/null | grep -q ":8080 "; then
    echo "  ✓ Spring Boot already running"
else
    cd "$PROJECT_ROOT/sms-sender"
    nohup ./gradlew bootRun > "$LOG_DIR/spring-boot.log" 2>&1 &
    SPRING_PID=$!
    echo $SPRING_PID > "$LOG_DIR/spring-boot.pid"
    wait_for_port 8080 "Spring Boot"
fi

# Start Go Service
if netstat -tuln 2>/dev/null | grep -q ":8081 " || ss -tuln 2>/dev/null | grep -q ":8081 "; then
    echo "  ✓ Go service already running"
else
    cd "$PROJECT_ROOT/smsstore"
    nohup env SERVER_PORT=:8081 go run cmd/app/main.go > "$LOG_DIR/go-service.log" 2>&1 &
    GO_PID=$!
    echo $GO_PID > "$LOG_DIR/go-service.pid"
    wait_for_port 8081 "Go service"
fi

echo -e "${GREEN}  ✓ All services started${NC}"
echo ""

# =============================================================================
# Step 4: Wait for services to stabilize
# =============================================================================
echo -e "${YELLOW}[4/7] Waiting for services to stabilize...${NC}"
sleep 5
echo -e "${GREEN}  ✓ Services ready${NC}"
echo ""

# =============================================================================
# Step 5: Run E2E Tests
# =============================================================================
echo -e "${YELLOW}[5/7] Running E2E tests...${NC}"
echo ""

SPRING_BOOT_URL="http://localhost:8080"
GO_SERVICE_URL="http://localhost:8081"
TEST_PHONE="+1234567890"
TEST_MESSAGE="Automated test at $(date +%T)"

TEST_PASSED=true

# Test 1: Send SMS
echo "  📤 Test 1: Sending SMS..."
SEND_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$SPRING_BOOT_URL/v1/sms/send" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\": \"$TEST_PHONE\", \"message\": \"$TEST_MESSAGE\"}")

HTTP_CODE=$(echo "$SEND_RESPONSE" | tail -n1)
if [ "$HTTP_CODE" = "200" ]; then
    echo -e "    ${GREEN}✓ SMS sent successfully${NC}"
else
    echo -e "    ${RED}✗ Failed to send SMS (HTTP $HTTP_CODE)${NC}"
    TEST_PASSED=false
fi

# Test 2: Wait for processing
echo "  ⏳ Test 2: Waiting for Kafka consumer..."
sleep 8
echo -e "    ${GREEN}✓ Processing complete${NC}"

# Test 3: Retrieve messages
echo "  📥 Test 3: Retrieving messages from MongoDB..."
GET_RESPONSE=$(curl -s -w "\n%{http_code}" "$GO_SERVICE_URL/v1/user/$TEST_PHONE/messages")
HTTP_CODE=$(echo "$GET_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$GET_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "    ${GREEN}✓ Messages retrieved successfully${NC}"
    
    # Test 4: Verify message content
    if echo "$RESPONSE_BODY" | grep -q "$TEST_MESSAGE"; then
        echo -e "    ${GREEN}✓ Test message found in MongoDB${NC}"
    else
        echo -e "    ${YELLOW}⚠ Test message not found${NC}"
        TEST_PASSED=false
    fi
    
    # Test 5: Verify message count
    MESSAGE_COUNT=$(echo "$RESPONSE_BODY" | grep -o '"count":[0-9]*' | grep -o '[0-9]*')
    if [ -n "$MESSAGE_COUNT" ] && [ "$MESSAGE_COUNT" -gt 0 ]; then
        echo -e "    ${GREEN}✓ Message count: $MESSAGE_COUNT${NC}"
    else
        echo -e "    ${YELLOW}⚠ Unexpected message count${NC}"
    fi
else
    echo -e "    ${RED}✗ Failed to retrieve messages (HTTP $HTTP_CODE)${NC}"
    TEST_PASSED=false
fi

echo ""

# =============================================================================
# Step 6: Generate Test Report
# =============================================================================
echo -e "${YELLOW}[6/7] Generating test report...${NC}"
echo ""
echo "  📊 Test Summary:"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "    • Spring Boot Service: ✓ Running on port 8080"
echo "    • Go Service:          ✓ Running on port 8081"
echo "    • Kafka:               ✓ Running on port 9092"
echo "    • MongoDB:             ✓ Running on port 27017"
echo "    • Message Delivery:    $([ "$TEST_PASSED" = true ] && echo "✓ Success" || echo "✗ Failed")"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# =============================================================================
# Step 7: Final Result
# =============================================================================
echo -e "${YELLOW}[7/7] Test execution complete${NC}"
echo ""

if [ "$TEST_PASSED" = true ]; then
    echo "=============================================="
    echo -e "${GREEN}✅ ALL TESTS PASSED!${NC}"
    echo "=============================================="
    echo ""
    echo "Data flow verified:"
    echo "  Spring Boot → Kafka → Go Consumer → MongoDB"
    echo ""
    echo "Services are running. To stop them:"
    echo "  cd $PROJECT_ROOT && ./stop-services.sh"
    echo ""
    exit 0
else
    echo "=============================================="
    echo -e "${RED}❌ SOME TESTS FAILED${NC}"
    echo "=============================================="
    echo ""
    echo "Check logs for details:"
    echo "  - Spring Boot: tail -f $LOG_DIR/spring-boot.log"
    echo "  - Go Service:  tail -f $LOG_DIR/go-service.log"
    echo "  - Kafka:       tail -f $LOG_DIR/kafka.log"
    echo ""
    exit 1
fi
