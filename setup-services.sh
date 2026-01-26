#!/bin/bash

# =============================================================================
# Polyglot SMS Service - Service Setup for Manual Testing
# =============================================================================
# This script starts all services for manual testing with Postman or demos.
# Services will remain running until you manually stop them.
# 
# Usage:
#   ./setup-services.sh        - Start all services
#   ./stop-services.sh         - Stop all services
# =============================================================================

set -e

PROJECT_ROOT="$HOME/Desktop/polyglot-sms-service"
LOG_DIR="$PROJECT_ROOT/logs"

# Find Kafka installation (check common locations)
if [ -d "/usr/local/kafka" ] && [ -f "/usr/local/kafka/bin/kafka-server-start.sh" ]; then
    KAFKA_HOME="/usr/local/kafka"
elif [ -d "$HOME/kafka_2.13-3.6.2" ] && [ -f "$HOME/kafka_2.13-3.6.2/bin/kafka-server-start.sh" ]; then
    KAFKA_HOME="$HOME/kafka_2.13-3.6.2"
else
    echo "Error: Kafka not found. Please install Kafka 3.6.2 at /usr/local/kafka or ~/kafka_2.13-3.6.2"
    exit 1
fi

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

clear
echo ""
echo "================================================"
echo "  Polyglot SMS Service - Setup for Testing"
echo "================================================"
echo ""

mkdir -p "$LOG_DIR"

# =============================================================================
# Helper Functions
# =============================================================================
wait_for_port() {
    local port=$1
    local service=$2
    local max_wait=60
    local count=0
    
    echo -n "  Waiting for $service on port $port..."
    while [ $count -lt $max_wait ]; do
        # Use lsof on macOS (more reliable than netstat)
        if lsof -ti:$port >/dev/null 2>&1 || netstat -an 2>/dev/null | grep -q "\.$port "; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        sleep 1
        echo -n "."
        count=$((count + 1))
    done
    echo -e " ${YELLOW}✗ Timeout${NC}"
    return 1
}

# Check if a service is running on a port (macOS compatible)
is_port_in_use() {
    local port=$1
    lsof -ti:$port >/dev/null 2>&1 || netstat -an 2>/dev/null | grep -q "\.$port "
}

# =============================================================================
# Step 1: Check Redis
# =============================================================================
echo -e "${YELLOW}[1/7] Checking Redis...${NC}"
if is_port_in_use 6379; then
    echo -e "  ${GREEN}✓ Redis is running${NC}"
elif command -v brew &> /dev/null && brew services list 2>/dev/null | grep -q "redis.*started"; then
    echo -e "  ${GREEN}✓ Redis is running (via Homebrew)${NC}"
elif command -v brew &> /dev/null; then
    echo "  Starting Redis via Homebrew..."
    brew services start redis 2>/dev/null || redis-server --daemonize yes 2>/dev/null
    sleep 2
    if is_port_in_use 6379; then
        echo -e "  ${GREEN}✓ Redis started${NC}"
    else
        echo -e "  ${YELLOW}⚠ Redis may not be running. Please start it manually:${NC}"
        echo "     brew services start redis"
        echo "     or: redis-server"
    fi
else
    # Try to start redis-server directly
    if command -v redis-server &> /dev/null; then
        redis-server --daemonize yes 2>/dev/null && sleep 2
        if is_port_in_use 6379; then
            echo -e "  ${GREEN}✓ Redis started${NC}"
        else
            echo -e "  ${YELLOW}⚠ Redis not running. Please start it manually${NC}"
        fi
    else
        echo -e "  ${YELLOW}⚠ Redis not found. Please install: brew install redis${NC}"
    fi
fi
echo ""

# =============================================================================
# Step 2: Check MongoDB
# =============================================================================
echo -e "${YELLOW}[2/7] Checking MongoDB...${NC}"
if is_port_in_use 27017; then
    echo -e "  ${GREEN}✓ MongoDB is running${NC}"
elif command -v brew &> /dev/null && brew services list 2>/dev/null | grep -q "mongodb-community.*started"; then
    echo -e "  ${GREEN}✓ MongoDB is running (via Homebrew)${NC}"
elif command -v brew &> /dev/null; then
    echo "  Starting MongoDB via Homebrew..."
    brew services start mongodb-community 2>/dev/null || mongod --fork --logpath /tmp/mongod.log 2>/dev/null
    sleep 2
    if is_port_in_use 27017; then
        echo -e "  ${GREEN}✓ MongoDB started${NC}"
    else
        echo -e "  ${YELLOW}⚠ MongoDB may not be running. Please start it manually:${NC}"
        echo "     brew services start mongodb-community"
        echo "     or: mongod --fork --logpath /tmp/mongod.log"
    fi
else
    # Try to start mongod directly
    if command -v mongod &> /dev/null; then
        mongod --fork --logpath /tmp/mongod.log 2>/dev/null && sleep 2
        if is_port_in_use 27017; then
            echo -e "  ${GREEN}✓ MongoDB started${NC}"
        else
            echo -e "  ${YELLOW}⚠ MongoDB not running. Please start it manually${NC}"
        fi
    else
        echo -e "  ${YELLOW}⚠ MongoDB not found. Please install: brew install mongodb-community${NC}"
    fi
fi
echo ""

# =============================================================================
# Step 3: Start Zookeeper
# =============================================================================
echo -e "${YELLOW}[3/7] Starting Zookeeper...${NC}"
if is_port_in_use 2181; then
    echo -e "  ${GREEN}✓ Zookeeper already running${NC}"
else
    rm -rf /tmp/zookeeper 2>/dev/null
    cd "$KAFKA_HOME"
    nohup bin/zookeeper-server-start.sh config/zookeeper.properties > "$LOG_DIR/zookeeper.log" 2>&1 &
    ZOOKEEPER_PID=$!
    echo $ZOOKEEPER_PID > "$LOG_DIR/zookeeper.pid"
    wait_for_port 2181 "Zookeeper"
fi
echo ""

# =============================================================================
# Step 4: Start Kafka
# =============================================================================
echo -e "${YELLOW}[4/7] Starting Kafka...${NC}"
if is_port_in_use 9092; then
    echo -e "  ${GREEN}✓ Kafka already running${NC}"
else
    rm -rf /tmp/kafka-logs 2>/dev/null
    cd "$KAFKA_HOME"
    nohup bin/kafka-server-start.sh config/server.properties > "$LOG_DIR/kafka.log" 2>&1 &
    KAFKA_PID=$!
    echo $KAFKA_PID > "$LOG_DIR/kafka.pid"
    wait_for_port 9092 "Kafka"
    sleep 5
fi

# Create Kafka topic
cd "$KAFKA_HOME"
if bin/kafka-topics.sh --list --bootstrap-server localhost:9092 2>/dev/null | grep -q "sms_events"; then
    echo -e "  ${GREEN}✓ Topic 'sms_events' exists${NC}"
else
    bin/kafka-topics.sh --create \
        --bootstrap-server localhost:9092 \
        --replication-factor 1 \
        --partitions 1 \
        --topic sms_events 2>/dev/null
    echo -e "  ${GREEN}✓ Topic 'sms_events' created (1 partition)${NC}"
fi
echo ""

# =============================================================================
# Step 5: Start Spring Boot Service
# =============================================================================
echo -e "${YELLOW}[5/7] Starting Spring Boot service...${NC}"
if is_port_in_use 8080; then
    echo -e "  ${GREEN}✓ Spring Boot already running on port 8080${NC}"
else
    cd "$PROJECT_ROOT/sms-sender"
    nohup ./gradlew bootRun > "$LOG_DIR/spring-boot.log" 2>&1 &
    SPRING_PID=$!
    echo $SPRING_PID > "$LOG_DIR/spring-boot.pid"
    wait_for_port 8080 "Spring Boot"
fi
echo ""

# =============================================================================
# Step 6: Start Go Service
# =============================================================================
echo -e "${YELLOW}[6/7] Starting Go service...${NC}"
if is_port_in_use 8081; then
    echo -e "  ${GREEN}✓ Go service already running on port 8081${NC}"
else
    cd "$PROJECT_ROOT/smsstore"
    nohup env SERVER_PORT=:8081 go run cmd/app/main.go > "$LOG_DIR/go-service.log" 2>&1 &
    GO_PID=$!
    echo $GO_PID > "$LOG_DIR/go-service.pid"
    wait_for_port 8081 "Go service"
fi
echo ""

# =============================================================================
# Step 7: Display Service Information
# =============================================================================
echo -e "${YELLOW}[7/7] Services ready!${NC}"
echo ""
echo "================================================"
echo -e "${GREEN}✅ ALL SERVICES RUNNING${NC}"
echo "================================================"
echo ""
echo "📋 Service Endpoints:"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  • Spring Boot (SMS Sender)"
echo "    http://localhost:8080"
echo "    POST /v1/sms/send"
echo ""
echo "  • Go Service (SMS Storage)"
echo "    http://localhost:8081"
echo "    GET /v1/user/{phone}/messages"
echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "🧪 Test with cURL:"
echo "  # Send SMS"
echo "  curl -X POST http://localhost:8080/v1/sms/send \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"phoneNumber\":\"+1234567890\",\"message\":\"Hello World\"}'"
echo ""
echo "  # Retrieve messages"
echo "  curl http://localhost:8081/v1/user/+1234567890/messages"
echo ""
echo "📝 Logs available at:"
echo "  • Spring Boot: $LOG_DIR/spring-boot.log"
echo "  • Go Service:  $LOG_DIR/go-service.log"
echo "  • Kafka:       $LOG_DIR/kafka.log"
echo ""
echo "🛑 To stop all services:"
echo "  ./stop-services.sh"
echo ""
echo "📊 To run automated tests:"
echo "  ./run-tests.sh"
echo ""
