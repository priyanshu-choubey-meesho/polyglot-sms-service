package config

import (
	"os"
	"strings"
)

type Config struct {
	MongoURI     string
	DBName       string
	KafkaBrokers []string
	KafkaTopic   string
	KafkaGroupID string
	ServerPort   string
}

func getenv(key string, fallback string) string {
	val, exists := os.LookupEnv(key)
	if !exists {
		return fallback
	}
	return val
}

func LoadConfig() *Config {
	brokersStr := getenv("KAFKA_BROKERS", "localhost:9092")
	brokers := strings.Split(brokersStr, ",")

	return &Config{
		MongoURI:     getenv("MONGO_URI", "mongodb://localhost:27017"),
		DBName:       getenv("DB_NAME", "sms_db"),
		KafkaBrokers: brokers,
		KafkaTopic:   getenv("KAFKA_TOPIC", "sms_events"),
		KafkaGroupID: getenv("KAFKA_GROUP_ID", "sms-storage-group"),
		ServerPort:   getenv("SERVER_PORT", ":8080"),
	}
}
