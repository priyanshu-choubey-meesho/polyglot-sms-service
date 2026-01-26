#!/bin/bash

# Stop all services

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

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "=================================="
echo "Stopping Polyglot SMS Service"
echo "=================================="
echo ""

# Stop Go Service
echo -e "${YELLOW}Stopping Go service...${NC}"
if [ -f "$LOG_DIR/go-service.pid" ]; then
    GO_PID=$(cat "$LOG_DIR/go-service.pid")
    kill -9 $GO_PID 2>/dev/null && echo -e "${GREEN}✓ Go service stopped${NC}" || echo "Go service not running"
    rm "$LOG_DIR/go-service.pid"
else
    # Find by port
    GO_PID=$(lsof -ti:8081 2>/dev/null)
    if [ -n "$GO_PID" ]; then
        kill -9 $GO_PID && echo -e "${GREEN}✓ Go service stopped${NC}"
    else
        echo "Go service not running"
    fi
fi

# Stop Spring Boot
echo -e "${YELLOW}Stopping Spring Boot...${NC}"
if [ -f "$LOG_DIR/spring-boot.pid" ]; then
    SPRING_PID=$(cat "$LOG_DIR/spring-boot.pid")
    kill -9 $SPRING_PID 2>/dev/null && echo -e "${GREEN}✓ Spring Boot stopped${NC}" || echo "Spring Boot not running"
    rm "$LOG_DIR/spring-boot.pid"
else
    # Find by port
    SPRING_PID=$(lsof -ti:8080 2>/dev/null)
    if [ -n "$SPRING_PID" ]; then
        kill -9 $SPRING_PID && echo -e "${GREEN}✓ Spring Boot stopped${NC}"
    else
        echo "Spring Boot not running"
    fi
fi

# Stop Kafka
echo -e "${YELLOW}Stopping Kafka...${NC}"
if [ -f "$LOG_DIR/kafka.pid" ]; then
    KAFKA_PID=$(cat "$LOG_DIR/kafka.pid")
    kill -9 $KAFKA_PID 2>/dev/null && echo -e "${GREEN}✓ Kafka stopped${NC}" || echo "Kafka not running"
    rm "$LOG_DIR/kafka.pid"
else
    cd "$KAFKA_HOME"
    bin/kafka-server-stop.sh 2>/dev/null || echo "Kafka not running"
fi

# Stop Zookeeper
echo -e "${YELLOW}Stopping Zookeeper...${NC}"
if [ -f "$LOG_DIR/zookeeper.pid" ]; then
    ZK_PID=$(cat "$LOG_DIR/zookeeper.pid")
    kill -9 $ZK_PID 2>/dev/null && echo -e "${GREEN}✓ Zookeeper stopped${NC}" || echo "Zookeeper not running"
    rm "$LOG_DIR/zookeeper.pid"
else
    cd "$KAFKA_HOME"
    bin/zookeeper-server-stop.sh 2>/dev/null || echo "Zookeeper not running"
fi

# Clean MongoDB test data
echo ""
echo -e "${YELLOW}Cleaning up MongoDB test data...${NC}"
if command -v mongosh &> /dev/null; then
    mongosh smsstore --quiet --eval 'db.smsdata.drop()' > /dev/null 2>&1 || true
    echo -e "${GREEN}✓ MongoDB test data cleaned${NC}"
else
    echo -e "${YELLOW}⚠ mongosh not found, skipping MongoDB cleanup${NC}"
fi

# Clean Redis blacklist cache
echo -e "${YELLOW}Cleaning up Redis blacklist cache...${NC}"
if command -v redis-cli &> /dev/null; then
    # macOS xargs doesn't support -r, so we check if there are keys first
    KEYS=$(redis-cli --scan --pattern "blacklist:*" 2>/dev/null)
    if [ -n "$KEYS" ]; then
        echo "$KEYS" | xargs redis-cli DEL > /dev/null 2>&1 || true
    fi
    echo -e "${GREEN}✓ Redis blacklist cache cleaned${NC}"
else
    echo -e "${YELLOW}⚠ redis-cli not found, skipping Redis cleanup${NC}"
fi

echo ""
echo -e "${GREEN}✅ All services stopped${NC}"
echo ""
