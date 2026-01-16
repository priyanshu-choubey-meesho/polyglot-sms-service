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
KAFKA_HOME="$HOME/kafka_2.13-3.6.1"
LOG_DIR="$PROJECT_ROOT/logs"

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
        if netstat -tuln 2>/dev/null | grep -q ":$port " || ss -tuln 2>/dev/null | grep -q ":$port "; then
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

# =============================================================================
# Step 1: Check Redis
# =============================================================================
echo -e "${YELLOW}[1/7] Checking Redis...${NC}"
if sudo systemctl is-active --quiet redis-server; then
    echo -e "  ${GREEN}✓ Redis is running${NC}"
else
    echo "  Starting Redis..."
    sudo systemctl start redis-server
    sleep 2
    echo -e "  ${GREEN}✓ Redis started${NC}"
fi
echo ""

# =============================================================================
# Step 2: Check MongoDB
# =============================================================================
echo -e "${YELLOW}[2/7] Checking MongoDB...${NC}"
if sudo systemctl is-active --quiet mongod; then
    echo -e "  ${GREEN}✓ MongoDB is running${NC}"
else
    echo "  Starting MongoDB..."
    sudo systemctl start mongod
    sleep 2
    echo -e "  ${GREEN}✓ MongoDB started${NC}"
fi
echo ""

# =============================================================================
# Step 3: Start Zookeeper
# =============================================================================
echo -e "${YELLOW}[3/7] Starting Zookeeper...${NC}"
if netstat -tuln 2>/dev/null | grep -q ":2181 " || ss -tuln 2>/dev/null | grep -q ":2181 "; then
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
if netstat -tuln 2>/dev/null | grep -q ":9092 " || ss -tuln 2>/dev/null | grep -q ":9092 "; then
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
if netstat -tuln 2>/dev/null | grep -q ":8080 " || ss -tuln 2>/dev/null | grep -q ":8080 "; then
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
if netstat -tuln 2>/dev/null | grep -q ":8081 " || ss -tuln 2>/dev/null | grep -q ":8081 "; then
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
